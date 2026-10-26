package org.nikoresu.transcare.pdf

import java.io.Serializable
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale
import org.nikoresu.transcare.domain.model.ExamMetric

/** One metric/value pair recognized in a lab PDF. */
data class ParsedExamResult(
    val metric: ExamMetric,
    val value: Double,
    val matchedLabel: String,
) : Serializable

/** Everything recognized in a lab PDF. */
data class ParsedPdfExams(
    val date: LocalDate?,
    val results: List<ParsedExamResult>,
) : Serializable

/**
 * Heuristic parser for text extracted from laboratory-result PDFs.
 *
 * It recognizes common English and Spanish metric names and short lab codes,
 * decimal commas and a few date formats. Reference ranges are deliberately
 * ignored: only the first plausible result value is returned. The parser is
 * pure Kotlin so it can be unit-tested without Android.
 */
object ExamPdfParser {

    fun parse(text: String): ParsedPdfExams {
        val normalizedText = text
            .replace('\u00A0', ' ')
            .replace('\u2007', ' ')
            .replace('\u202F', ' ')
        val lines = normalizedText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val spanish = isSpanish(normalizedText)
        return ParsedPdfExams(
            date = extractDate(lines, spanish),
            results = extractResults(lines),
        )
    }

    // ---------------------------------------------------------------------
    // Dates
    // ---------------------------------------------------------------------

    private fun extractDate(lines: List<String>, spanish: Boolean): LocalDate? {
        // 1. Prefer a line that explicitly labels the sample/exam date.
        for ((index, line) in lines.withIndex()) {
            val normalized = normalizeForMatch(line)
            if (COLLECTION_DATE_KEYWORDS.none { normalized.contains(it) }) continue
            parseDateFromLine(line, spanish)?.let { return it }
            lines.getOrNull(index + 1)?.let { next ->
                parseDateFromLine(next, spanish)?.let { return it }
            }
        }

        // 2. Any line with a date label, except print/report/birth dates.
        for (line in lines) {
            val normalized = normalizeForMatch(line)
            if (DATE_LABEL_KEYWORDS.none { normalized.contains(it) }) continue
            if (IGNORED_DATE_KEYWORDS.any { normalized.contains(it) }) continue
            parseDateFromLine(line, spanish)?.let { return it }
        }

        // 3. Last resort: the first date that is not on an ignored line.
        for (line in lines) {
            val normalized = normalizeForMatch(line)
            if (IGNORED_DATE_KEYWORDS.any { normalized.contains(it) }) continue
            parseDateFromLine(line, spanish)?.let { return it }
        }
        return null
    }

    private fun parseDateFromLine(line: String, spanish: Boolean): LocalDate? {
        ISO_DATE_REGEX.find(line)?.let { match ->
            safeDate(
                year = match.groupValues[1].toInt(),
                month = match.groupValues[2].toInt(),
                day = match.groupValues[3].toInt(),
            )?.let { return it }
        }
        NUMERIC_DATE_REGEX.find(line)?.let { match ->
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[2].toInt()
            val year = normalizeYear(match.groupValues[3].toInt())
            numericDate(first, second, year, spanish)?.let { return it }
        }
        SPANISH_LONG_DATE_REGEX.find(line)?.let { match ->
            val day = match.groupValues[1].toInt()
            val month = monthNumber(match.groupValues[2]) ?: return@let
            safeDate(year = match.groupValues[3].toInt(), month = month, day = day)
                ?.let { return it }
        }
        ENGLISH_LONG_DATE_REGEX.find(line)?.let { match ->
            val month = monthNumber(match.groupValues[1]) ?: return@let
            val day = match.groupValues[2].toInt()
            safeDate(year = match.groupValues[3].toInt(), month = month, day = day)
                ?.let { return it }
        }
        DAY_MONTH_YEAR_REGEX.find(line)?.let { match ->
            val day = match.groupValues[1].toInt()
            val month = monthNumber(match.groupValues[2]) ?: return@let
            safeDate(year = match.groupValues[3].toInt(), month = month, day = day)
                ?.let { return it }
        }
        MONTH_DAY_YEAR_REGEX.find(line)?.let { match ->
            val month = monthNumber(match.groupValues[1]) ?: return@let
            val day = match.groupValues[2].toInt()
            safeDate(year = match.groupValues[3].toInt(), month = month, day = day)
                ?.let { return it }
        }
        return null
    }

    private fun numericDate(first: Int, second: Int, year: Int, spanish: Boolean): LocalDate? {
        val dayFirst = when {
            first > 12 -> true
            second > 12 -> false
            else -> spanish
        }
        return if (dayFirst) {
            safeDate(year = year, month = second, day = first)
        } else {
            safeDate(year = year, month = first, day = second)
        }
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? {
        if (year !in 1900..2100) return null
        return try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun normalizeYear(year: Int): Int = when {
        year >= 100 -> year
        year >= 70 -> 1900 + year
        else -> 2000 + year
    }

    private fun monthNumber(raw: String): Int? = MONTHS[normalizeForMatch(raw)]

    // ---------------------------------------------------------------------
    // Metrics
    // ---------------------------------------------------------------------

    private fun extractResults(lines: List<String>): List<ParsedExamResult> {
        val results = LinkedHashMap<ExamMetric, ParsedExamResult>()
        for ((index, line) in lines.withIndex()) {
            val matches = findAllAliases(line)
            if (matches.isEmpty()) continue
            val allowBeforeValue = matches.size == 1
            for ((matchIndex, aliasMatch) in matches.withIndex()) {
                val metric = aliasMatch.alias.metric
                if (metric in results) continue

                val nextStart = matches.getOrNull(matchIndex + 1)?.range?.first ?: line.length
                val after = line.substring(aliasMatch.range.last + 1, nextStart)
                val before = line.substring(0, aliasMatch.range.first)
                var value = extractValue(after, before, metric, allowBeforeValue)
                if (value == null && findCandidates(stripNoise(after)).isEmpty()) {
                    value = lookAheadForValue(lines, index + 1, metric)
                }
                if (value == null) continue

                results[metric] = ParsedExamResult(
                    metric = metric,
                    value = value,
                    matchedLabel = aliasMatch.alias.text,
                )
            }
        }
        return results.values.toList()
    }

    /**
     * Finds the non-overlapping metric labels on a line, left to right. When
     * two aliases start at the same place the longer one wins, and only the
     * first alias for each metric is kept so "25-OH Vitamin D" is not split.
     */
    private fun findAllAliases(line: String): List<AliasMatch> {
        val candidates = mutableListOf<AliasMatch>()
        for (alias in ALIASES) {
            var match = alias.regex.find(line)
            while (match != null) {
                candidates += AliasMatch(alias, match.range)
                match = alias.regex.find(line, match.range.last + 1)
            }
        }
        if (candidates.isEmpty()) return emptyList()

        val sorted = candidates.sortedWith(
            compareBy({ it.range.first }, { -it.alias.text.length }),
        )
        val accepted = mutableListOf<AliasMatch>()
        val seenMetrics = mutableSetOf<ExamMetric>()
        for (candidate in sorted) {
            val overlaps = accepted.any { existing ->
                existing.range.first <= candidate.range.last &&
                    candidate.range.first <= existing.range.last
            }
            if (overlaps) continue
            if (!seenMetrics.add(candidate.alias.metric)) continue
            accepted += candidate
        }
        return accepted.sortedBy { it.range.first }
    }

    private fun extractValue(
        afterAlias: String,
        beforeAlias: String,
        metric: ExamMetric,
        allowBeforeValue: Boolean,
    ): Double? {
        val cleaned = stripNoise(afterAlias)
        val candidates = findCandidates(cleaned)
        if (candidates.isNotEmpty()) {
            return selectCandidate(cleaned, candidates, metric)
        }

        // Rare layout: "23.8 Estradiol". Only accept a lone number directly
        // before the metric name so a neighbouring column is never used.
        if (!allowBeforeValue) return null
        val before = stripNoise(beforeAlias).trim()
        if (!NUMBER_ONLY_REGEX.matches(before)) return null
        return findCandidates(before).firstOrNull()?.takeIf { !isYearLike(it) }?.value
    }

    private fun selectCandidate(
        cleaned: String,
        candidates: List<Candidate>,
        metric: ExamMetric,
    ): Double? {
        val meaningful = candidates.filterNot { isYearLike(it) }
        if (meaningful.isEmpty()) return null

        val first = meaningful[0]
        if (meaningful.size >= 2 && matchesReferenceRange(first, meaningful[1], metric)) {
            return meaningful.getOrNull(2)?.value
        }
        if (isComparatorReference(first, metric)) {
            return meaningful.getOrNull(1)?.value
        }
        if (meaningful.size == 2 && hasRangeSeparatorBetween(cleaned, first, meaningful[1])) {
            return null
        }
        return first.value
    }

    private fun lookAheadForValue(
        lines: List<String>,
        startIndex: Int,
        metric: ExamMetric,
    ): Double? {
        val end = minOf(lines.size, startIndex + 3)
        for (index in startIndex until end) {
            val line = lines[index].trim()
            if (line.isEmpty()) continue
            if (findAllAliases(line).isNotEmpty()) return null
            if (parseDateFromLine(line, spanish = true) != null) return null

            val cleaned = stripNoise(line)
            if (cleaned.isBlank()) continue

            val candidates = findCandidates(cleaned)
            if (candidates.isEmpty()) {
                if (isHeaderLine(cleaned)) continue
                // A unit on its own line, e.g. "pg/mL", does not end the row.
                if (unitRegex(metric).containsMatchIn(cleaned) && cleaned.length <= 16) continue
                return null
            }
            if (isLikelyRangeLine(cleaned, metric)) return null

            // A bare value line, or one with the metric's unit, is safe. A
            // line with several numbers and no unit may be a phone number or
            // an unrelated row, so do not guess.
            val hasUnit = unitRegex(metric).containsMatchIn(cleaned)
            val isPlainNumber = NUMBER_ONLY_REGEX.matches(cleaned.trim())
            if (!hasUnit && !isPlainNumber && candidates.size != 1) return null

            return selectCandidate(cleaned, candidates, metric)
        }
        return null
    }

    private fun isLikelyRangeLine(cleaned: String, metric: ExamMetric): Boolean {
        val normalized = normalizeForMatch(cleaned)
        if (REFERENCE_KEYWORDS.any { normalized.contains(it) }) return true

        val candidates = findCandidates(cleaned)
        if (candidates.size >= 2 && hasRangeSeparatorBetween(cleaned, candidates[0], candidates[1])) {
            return true
        }
        if (candidates.size == 1 && isComparatorReference(candidates[0], metric)) {
            return true
        }
        return false
    }

    private fun matchesReferenceRange(
        first: Candidate,
        second: Candidate,
        metric: ExamMetric,
    ): Boolean {
        val low = metric.referenceLow ?: return false
        val high = metric.referenceHigh ?: return false
        return closeEnough(first.value, low) && closeEnough(second.value, high)
    }

    private fun isComparatorReference(candidate: Candidate, metric: ExamMetric): Boolean {
        val raw = candidate.raw
        if (!raw.contains('<') && !raw.contains('>') && !raw.contains('≤') && !raw.contains('≥')) {
            return false
        }
        val low = metric.referenceLow
        val high = metric.referenceHigh
        return (low != null && closeEnough(candidate.value, low)) ||
            (high != null && closeEnough(candidate.value, high))
    }

    private fun closeEnough(value: Double, reference: Double): Boolean {
        val tolerance = maxOf(0.01, kotlin.math.abs(reference) * 0.05)
        return kotlin.math.abs(value - reference) <= tolerance
    }

    private fun hasRangeSeparatorBetween(
        text: String,
        first: Candidate,
        second: Candidate,
    ): Boolean {
        if (second.start <= first.end) return false
        val between = text.substring(first.end + 1, second.start).trim()
        return between == "-" ||
            between == "–" ||
            between == "—" ||
            between.equals("a", ignoreCase = true) ||
            between.equals("to", ignoreCase = true)
    }

    private fun findCandidates(text: String): List<Candidate> =
        NUMBER_REGEX.findAll(text).mapNotNull { match ->
            val raw = match.groupValues[2]
            val value = parseNumber(raw) ?: return@mapNotNull null
            Candidate(
                value = value,
                start = match.range.first,
                end = match.range.last,
                raw = match.value,
            )
        }.toList()

    private fun parseNumber(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val lastDot = trimmed.lastIndexOf('.')
        val lastComma = trimmed.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 -> {
                if (lastDot > lastComma) {
                    trimmed.replace(",", "")
                } else {
                    trimmed.replace(".", "").replace(',', '.')
                }
            }

            lastComma >= 0 -> {
                val digitsAfter = trimmed.length - lastComma - 1
                val digitsBefore = lastComma
                if (digitsAfter == 3 && digitsBefore in 1..3 && !trimmed.startsWith("0")) {
                    trimmed.replace(",", "")
                } else {
                    trimmed.replace(',', '.')
                }
            }

            else -> trimmed
        }
        return normalized.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 && it < 1_000_000.0 }
    }

    private fun isYearLike(candidate: Candidate): Boolean =
        candidate.raw.trim().matches(FOUR_DIGITS_REGEX) && candidate.value in 1900.0..2100.0

    private fun isHeaderLine(cleaned: String): Boolean {
        val normalized = normalizeForMatch(cleaned)
        return HEADER_WORDS.any { normalized.contains(it) }
    }

    private fun stripNoise(text: String): String {
        var cleaned = PARENTHESES_REGEX.replace(text, " ")
        DATE_NOISE_REGEXES.forEach { regex -> cleaned = regex.replace(cleaned, " ") }
        cleaned = TIME_REGEX.replace(cleaned, " ")
        cleaned = METHOD_TOKEN_REGEX.replace(cleaned, " ")
        return cleaned
    }

    // ---------------------------------------------------------------------
    // Language detection and normalization
    // ---------------------------------------------------------------------

    private fun isSpanish(text: String): Boolean {
        val normalized = normalizeForMatch(text)
        val spanishHits = SPANISH_HINTS.count { word ->
            wordRegex(word).containsMatchIn(normalized)
        }
        val englishHits = ENGLISH_HINTS.count { word ->
            wordRegex(word).containsMatchIn(normalized)
        }
        return spanishHits >= englishHits
    }

    private fun normalizeForMatch(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS_REGEX, "")

    private fun wordRegex(word: String): Regex =
        Regex("(?<![\\p{L}])${Regex.escape(word)}(?![\\p{L}])")

    // ---------------------------------------------------------------------
    // Static data
    // ---------------------------------------------------------------------

    private data class Alias(
        val metric: ExamMetric,
        val text: String,
        val caseSensitive: Boolean = false,
    ) {
        val regex: Regex by lazy {
            val flags = if (caseSensitive) {
                emptySet()
            } else {
                setOf(RegexOption.IGNORE_CASE)
            }
            Regex(
                "(?<![\\p{L}\\p{N}])${Regex.escape(text)}(?![\\p{L}\\p{N}])",
                flags,
            )
        }
    }

    private data class AliasMatch(val alias: Alias, val range: IntRange)

    private data class Candidate(
        val value: Double,
        val start: Int,
        val end: Int,
        val raw: String,
    )

    private val MARKS_REGEX = Regex("\\p{M}+")
    private val FOUR_DIGITS_REGEX = Regex("\\d{4}")
    private val PARENTHESES_REGEX = Regex("\\([^)]*\\)")
    private val TIME_REGEX = Regex("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b")
    private val NUMBER_ONLY_REGEX = Regex("[<>≤≥]?\\s*\\d+(?:[.,]\\d+)?\\s*")

    /**
     * A number that is not glued to a word, optionally prefixed by a
     * comparator. Supports both decimal dots and decimal commas.
     */
    private val NUMBER_REGEX = Regex(
        "(?<![\\p{L}\\p{N}])([<>≤≥]?\\s*)(\\d+(?:[.,]\\d+)*)(?![\\p{L}\\p{N}])",
    )

    private val ISO_DATE_REGEX = Regex("\\b(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b")
    private val NUMERIC_DATE_REGEX = Regex("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})\\b")
    private val SPANISH_LONG_DATE_REGEX = Regex(
        "\\b(\\d{1,2})\\s+de\\s+([\\p{L}]+)(?:\\s+de|\\s+del)?\\s+(\\d{4})\\b",
        RegexOption.IGNORE_CASE,
    )
    private val ENGLISH_LONG_DATE_REGEX = Regex(
        "\\b([\\p{L}]+)\\s+(\\d{1,2}),?\\s+(\\d{4})\\b",
        RegexOption.IGNORE_CASE,
    )
    private val DAY_MONTH_YEAR_REGEX = Regex(
        "\\b(\\d{1,2})[\\s./-]+([\\p{L}]+)[\\s./-]+(\\d{4})\\b",
        RegexOption.IGNORE_CASE,
    )
    private val MONTH_DAY_YEAR_REGEX = Regex(
        "\\b([\\p{L}]+)[\\s./-]+(\\d{1,2}),?[\\s./-]+(\\d{4})\\b",
        RegexOption.IGNORE_CASE,
    )

    private val DATE_NOISE_REGEXES = listOf(
        ISO_DATE_REGEX,
        NUMERIC_DATE_REGEX,
        SPANISH_LONG_DATE_REGEX,
        ENGLISH_LONG_DATE_REGEX,
        DAY_MONTH_YEAR_REGEX,
        MONTH_DAY_YEAR_REGEX,
    )

    /**
     * Tokens that belong to a metric name, method or unit rather than to the
     * value, e.g. the "A1c" in "HbA1c 5.3" or the "25-OH" in "Vitamin D 25-OH".
     */
    private val METHOD_TOKEN_REGEX = Regex(
        "(?<![\\p{L}\\p{N}])(?:" +
            "25\\s*[- ]?\\s*oh|" +
            "25\\s*\\(oh\\)\\s*d?|" +
            "1\\s*[,.]?\\s*25\\s*[- ]?\\s*oh|" +
            "17\\s*[- ]?(?:beta|β)|" +
            "hba1c|a1c|e2|p4|shbg|dht|prl|lh|fsh|hct|hgb|hb|alt|ast|gpt|tgp|got|tgo|cr|k|t" +
            ")(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE,
    )

    private val REFERENCE_KEYWORDS = listOf(
        "referencia",
        "reference",
        "rango",
        "range",
        "valor de referencia",
        "reference range",
        "vr",
        "ref.",
        "ref:",
    )

    private val HEADER_WORDS = listOf(
        "resultado",
        "resultados",
        "result",
        "results",
        "unidad",
        "unidades",
        "unit",
        "units",
        "rango",
        "range",
        "valor",
        "value",
        "referencia",
        "reference",
        "fecha",
        "date",
    )

    private val COLLECTION_DATE_KEYWORDS = listOf(
        "fecha de toma",
        "fecha de extraccion",
        "fecha de muestra",
        "fecha de recoleccion",
        "fecha de analisis",
        "tomado el",
        "muestra del",
        "collection date",
        "date collected",
        "sample date",
        "date of collection",
        "collected on",
        "specimen date",
        "test date",
    ).map { normalizeForMatch(it) }

    private val DATE_LABEL_KEYWORDS = listOf("fecha", "date")
        .map { normalizeForMatch(it) }

    private val IGNORED_DATE_KEYWORDS = listOf(
        "fecha de impresion",
        "fecha de informe",
        "fecha de reporte",
        "fecha de emision",
        "fecha de nacimiento",
        "fecha de ingreso",
        "print date",
        "report date",
        "date printed",
        "date reported",
        "date of report",
        "date of birth",
        "birth date",
        "issued",
        "nacimiento",
    ).map { normalizeForMatch(it) }

    private val SPANISH_HINTS = listOf(
        "fecha",
        "resultado",
        "resultados",
        "paciente",
        "medico",
        "laboratorio",
        "examen",
        "examenes",
        "muestra",
        "referencia",
        "valor",
        "unidades",
    )

    private val ENGLISH_HINTS = listOf(
        "date",
        "result",
        "results",
        "patient",
        "physician",
        "laboratory",
        "specimen",
        "reference",
        "value",
        "units",
    )

    private val MONTHS: Map<String, Int> = buildMap {
        fun add(month: Int, vararg names: String) {
            names.forEach { put(normalizeForMatch(it), month) }
        }
        add(1, "enero", "ene", "january", "jan")
        add(2, "febrero", "feb", "february")
        add(3, "marzo", "mar", "march")
        add(4, "abril", "abr", "april", "apr")
        add(5, "mayo", "may")
        add(6, "junio", "jun", "june")
        add(7, "julio", "jul", "july")
        add(8, "agosto", "ago", "august", "aug")
        add(9, "septiembre", "setiembre", "sep", "sept", "september")
        add(10, "octubre", "oct", "october")
        add(11, "noviembre", "nov", "november")
        add(12, "diciembre", "dic", "december", "dec")
    }

    private val ALIASES: List<Alias> = listOf(
        // Estradiol
        Alias(ExamMetric.ESTRADIOL, "estradiol (e2)"),
        Alias(ExamMetric.ESTRADIOL, "17-beta estradiol"),
        Alias(ExamMetric.ESTRADIOL, "17 beta estradiol"),
        Alias(ExamMetric.ESTRADIOL, "17β-estradiol"),
        Alias(ExamMetric.ESTRADIOL, "estradiol"),
        Alias(ExamMetric.ESTRADIOL, "e2"),
        // Testosterone, total
        Alias(ExamMetric.TESTOSTERONE_TOTAL, "testosterona total"),
        Alias(ExamMetric.TESTOSTERONE_TOTAL, "testosterona serica"),
        Alias(ExamMetric.TESTOSTERONE_TOTAL, "testosterona sérica"),
        Alias(ExamMetric.TESTOSTERONE_TOTAL, "testosterone, total"),
        Alias(ExamMetric.TESTOSTERONE_TOTAL, "testosterone total"),
        Alias(ExamMetric.TESTOSTERONE_TOTAL, "total testosterone"),
        Alias(ExamMetric.TESTOSTERONE_TOTAL, "testo total"),
        Alias(ExamMetric.TESTOSTERONE_TOTAL, "t total"),
        Alias(ExamMetric.TESTOSTERONE_TOTAL, "testosterona"),
        Alias(ExamMetric.TESTOSTERONE_TOTAL, "testosterone"),
        Alias(ExamMetric.TESTOSTERONE_TOTAL, "T", caseSensitive = true),
        // LH
        Alias(ExamMetric.LH, "hormona luteinizante"),
        Alias(ExamMetric.LH, "luteinizing hormone"),
        Alias(ExamMetric.LH, "luteinising hormone"),
        Alias(ExamMetric.LH, "lh"),
        // FSH
        Alias(ExamMetric.FSH, "hormona foliculoestimulante"),
        Alias(ExamMetric.FSH, "hormona foliculo estimulante"),
        Alias(ExamMetric.FSH, "hormona folículo estimulante"),
        Alias(ExamMetric.FSH, "follicle-stimulating hormone"),
        Alias(ExamMetric.FSH, "follicle stimulating hormone"),
        Alias(ExamMetric.FSH, "fsh"),
        // Prolactin
        Alias(ExamMetric.PROLACTIN, "prolactina"),
        Alias(ExamMetric.PROLACTIN, "prolactin"),
        Alias(ExamMetric.PROLACTIN, "prl"),
        // SHBG
        Alias(ExamMetric.SHBG, "globulina fijadora de hormonas sexuales"),
        Alias(ExamMetric.SHBG, "globulina transportadora de hormonas sexuales"),
        Alias(ExamMetric.SHBG, "sex hormone-binding globulin"),
        Alias(ExamMetric.SHBG, "sex hormone binding globulin"),
        Alias(ExamMetric.SHBG, "shbg"),
        // Free testosterone
        Alias(ExamMetric.FREE_TESTOSTERONE, "testosterona libre"),
        Alias(ExamMetric.FREE_TESTOSTERONE, "free testosterone"),
        Alias(ExamMetric.FREE_TESTOSTERONE, "testo libre"),
        Alias(ExamMetric.FREE_TESTOSTERONE, "free t"),
        Alias(ExamMetric.FREE_TESTOSTERONE, "t libre"),
        // DHT
        Alias(ExamMetric.DHT, "dihidrotestosterona"),
        Alias(ExamMetric.DHT, "dihydrotestosterone"),
        Alias(ExamMetric.DHT, "dht"),
        // Progesterone
        Alias(ExamMetric.PROGESTERONE, "progesterona"),
        Alias(ExamMetric.PROGESTERONE, "progesterone"),
        Alias(ExamMetric.PROGESTERONE, "p4"),
        // Hematocrit
        Alias(ExamMetric.HEMATOCRIT, "hematocrito"),
        Alias(ExamMetric.HEMATOCRIT, "hematocrit"),
        Alias(ExamMetric.HEMATOCRIT, "hct"),
        Alias(ExamMetric.HEMATOCRIT, "hto"),
        // Hemoglobin
        Alias(ExamMetric.HEMOGLOBIN, "hemoglobina"),
        Alias(ExamMetric.HEMOGLOBIN, "hemoglobin"),
        Alias(ExamMetric.HEMOGLOBIN, "hgb"),
        Alias(ExamMetric.HEMOGLOBIN, "hb"),
        // Potassium
        Alias(ExamMetric.POTASSIUM, "potasio"),
        Alias(ExamMetric.POTASSIUM, "potassium"),
        Alias(ExamMetric.POTASSIUM, "K", caseSensitive = true),
        // Creatinine
        Alias(ExamMetric.CREATININE, "creatinina"),
        Alias(ExamMetric.CREATININE, "creatinine"),
        Alias(ExamMetric.CREATININE, "cr"),
        // ALT
        Alias(ExamMetric.ALT, "alanina aminotransferasa"),
        Alias(ExamMetric.ALT, "alanine aminotransferase"),
        Alias(ExamMetric.ALT, "alt"),
        Alias(ExamMetric.ALT, "gpt"),
        Alias(ExamMetric.ALT, "tgp"),
        // AST
        Alias(ExamMetric.AST, "aspartato aminotransferasa"),
        Alias(ExamMetric.AST, "aspartate aminotransferase"),
        Alias(ExamMetric.AST, "ast"),
        Alias(ExamMetric.AST, "got"),
        Alias(ExamMetric.AST, "tgo"),
        // Vitamin D
        Alias(ExamMetric.VITAMIN_D, "vitamina d, 25-oh"),
        Alias(ExamMetric.VITAMIN_D, "vitamina d 25-oh"),
        Alias(ExamMetric.VITAMIN_D, "25-oh vitamina d"),
        Alias(ExamMetric.VITAMIN_D, "vitamina d"),
        Alias(ExamMetric.VITAMIN_D, "vitamin d, 25-oh"),
        Alias(ExamMetric.VITAMIN_D, "vitamin d 25-oh"),
        Alias(ExamMetric.VITAMIN_D, "25-oh vitamin d"),
        Alias(ExamMetric.VITAMIN_D, "vitamin d"),
        Alias(ExamMetric.VITAMIN_D, "25-oh"),
        Alias(ExamMetric.VITAMIN_D, "25 oh"),
        Alias(ExamMetric.VITAMIN_D, "25(oh)d"),
        Alias(ExamMetric.VITAMIN_D, "calcidiol"),
        // Ferritin
        Alias(ExamMetric.FERRITIN, "ferritina"),
        Alias(ExamMetric.FERRITIN, "ferritin"),
        // HbA1c
        Alias(ExamMetric.HBA1C, "hemoglobina glicosilada"),
        Alias(ExamMetric.HBA1C, "hemoglobina glucosilada"),
        Alias(ExamMetric.HBA1C, "hemoglobina a1c"),
        Alias(ExamMetric.HBA1C, "glycated hemoglobin"),
        Alias(ExamMetric.HBA1C, "glycosylated hemoglobin"),
        Alias(ExamMetric.HBA1C, "glycated haemoglobin"),
        Alias(ExamMetric.HBA1C, "hba1c"),
        Alias(ExamMetric.HBA1C, "a1c"),
    ).sortedByDescending { it.text.length }

    private val UNIT_ALIASES: Map<ExamMetric, List<String>> = mapOf(
        ExamMetric.ESTRADIOL to listOf("pg/ml", "pmol/l"),
        ExamMetric.TESTOSTERONE_TOTAL to listOf("ng/dl", "nmol/l"),
        ExamMetric.LH to listOf("miu/ml", "ui/l", "iu/l"),
        ExamMetric.FSH to listOf("miu/ml", "ui/l", "iu/l"),
        ExamMetric.PROLACTIN to listOf("ng/ml", "µg/l", "ug/l"),
        ExamMetric.SHBG to listOf("nmol/l"),
        ExamMetric.FREE_TESTOSTERONE to listOf("pg/ml", "ng/dl"),
        ExamMetric.DHT to listOf("ng/dl", "pg/ml"),
        ExamMetric.PROGESTERONE to listOf("ng/ml", "nmol/l"),
        ExamMetric.HEMATOCRIT to listOf("%"),
        ExamMetric.HEMOGLOBIN to listOf("g/dl"),
        ExamMetric.POTASSIUM to listOf("mmol/l", "meq/l"),
        ExamMetric.CREATININE to listOf("mg/dl", "µmol/l", "umol/l"),
        ExamMetric.ALT to listOf("u/l", "iu/l"),
        ExamMetric.AST to listOf("u/l", "iu/l"),
        ExamMetric.VITAMIN_D to listOf("ng/ml", "nmol/l"),
        ExamMetric.FERRITIN to listOf("ng/ml", "µg/l", "ug/l"),
        ExamMetric.HBA1C to listOf("%"),
    )

    private fun unitRegex(metric: ExamMetric): Regex {
        val units = UNIT_ALIASES[metric].orEmpty()
        if (units.isEmpty()) return Regex("a^")
        val alternatives = units.joinToString("|") { Regex.escape(it) }
        return Regex(
            "(?<![\\p{L}\\p{N}])($alternatives)(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE,
        )
    }
}
