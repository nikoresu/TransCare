package org.nikoresu.transcare

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.pdf.ExamPdfParser

class ExamPdfParserTest {

    private fun values(text: String): Map<ExamMetric, Double> =
        ExamPdfParser.parse(text).results.associate { it.metric to it.value }

    @Test
    fun spanishReport_extractsDateAndMetrics() {
        val parsed = ExamPdfParser.parse(
            """
            LABORATORIO CLÍNICO
            Paciente: Natalie
            Fecha de nacimiento: 12/05/1990
            Fecha de toma: 25/10/2026
            Médico: Dra. Pérez

            Resultado Unidad Referencia
            Estradiol (E2) 23.8 pg/mL 12.0 - 40.0
            Testosterona total 494 ng/dL 264 - 916
            LH 10.1 mIU/mL 0.5 - 5.0
            FSH 2.6 mIU/mL 0.5 - 5.0
            Prolactina 9.59 ng/mL 4.0 - 24.0
            Vitamina D 25-OH 13.8 ng/mL 30 - 100
            Potasio 4.04 mmol/L 3.5 - 5.1
            Hematocrito 49.9 % 36 - 48
            Hemoglobina 17.5 g/dL 12 - 16
            ALT 18.1 U/L 7 - 35
            AST 19.7 U/L 8 - 33
            Hemoglobina glicosilada 5.3 % 4.0 - 5.6
            """.trimIndent(),
        )

        assertEquals(LocalDate.of(2026, 10, 25), parsed.date)
        assertEquals(12, parsed.results.size)
        val byMetric = parsed.results.associate { it.metric to it.value }
        assertEquals(23.8, byMetric[ExamMetric.ESTRADIOL]!!, 0.0001)
        assertEquals(494.0, byMetric[ExamMetric.TESTOSTERONE_TOTAL]!!, 0.0001)
        assertEquals(10.1, byMetric[ExamMetric.LH]!!, 0.0001)
        assertEquals(2.6, byMetric[ExamMetric.FSH]!!, 0.0001)
        assertEquals(9.59, byMetric[ExamMetric.PROLACTIN]!!, 0.0001)
        assertEquals(13.8, byMetric[ExamMetric.VITAMIN_D]!!, 0.0001)
        assertEquals(4.04, byMetric[ExamMetric.POTASSIUM]!!, 0.0001)
        assertEquals(49.9, byMetric[ExamMetric.HEMATOCRIT]!!, 0.0001)
        assertEquals(17.5, byMetric[ExamMetric.HEMOGLOBIN]!!, 0.0001)
        assertEquals(18.1, byMetric[ExamMetric.ALT]!!, 0.0001)
        assertEquals(19.7, byMetric[ExamMetric.AST]!!, 0.0001)
        assertEquals(5.3, byMetric[ExamMetric.HBA1C]!!, 0.0001)
    }

    @Test
    fun englishReport_prefersCollectionDateOverPrintAndBirthDates() {
        val parsed = ExamPdfParser.parse(
            """
            Hormone Panel
            Patient: Alex
            Date of Birth: 05/12/1990
            Report Date: 10/27/2026
            Collection Date: 10/25/2026

            Test Name Result Units Reference Range
            Estradiol 150.0 pg/mL 100 - 200
            Testosterone, Total 45 ng/dL < 50
            Luteinizing hormone 4.2 mIU/mL 1.7 - 8.6
            Follicle-stimulating hormone 3.1 mIU/mL 1.5 - 12.4
            Sex hormone-binding globulin 45 nmol/L 20 - 130
            """.trimIndent(),
        )

        assertEquals(LocalDate.of(2026, 10, 25), parsed.date)
        val byMetric = parsed.results.associate { it.metric to it.value }
        assertEquals(150.0, byMetric[ExamMetric.ESTRADIOL]!!, 0.0001)
        assertEquals(45.0, byMetric[ExamMetric.TESTOSTERONE_TOTAL]!!, 0.0001)
        assertEquals(4.2, byMetric[ExamMetric.LH]!!, 0.0001)
        assertEquals(3.1, byMetric[ExamMetric.FSH]!!, 0.0001)
        assertEquals(45.0, byMetric[ExamMetric.SHBG]!!, 0.0001)
    }

    @Test
    fun shortLabCodes_areRecognized() {
        val byMetric = values(
            """
            Fecha: 25/10/2026
            E2 23.8
            T 494
            LH 10.1
            FSH 2.6
            PRL 9.59
            K 4.04
            Hct 49.9
            Hb 17.5
            ALT 18.1
            AST 19.7
            HbA1c 5.3
            """.trimIndent(),
        )

        assertEquals(23.8, byMetric[ExamMetric.ESTRADIOL]!!, 0.0001)
        assertEquals(494.0, byMetric[ExamMetric.TESTOSTERONE_TOTAL]!!, 0.0001)
        assertEquals(10.1, byMetric[ExamMetric.LH]!!, 0.0001)
        assertEquals(2.6, byMetric[ExamMetric.FSH]!!, 0.0001)
        assertEquals(9.59, byMetric[ExamMetric.PROLACTIN]!!, 0.0001)
        assertEquals(4.04, byMetric[ExamMetric.POTASSIUM]!!, 0.0001)
        assertEquals(49.9, byMetric[ExamMetric.HEMATOCRIT]!!, 0.0001)
        assertEquals(17.5, byMetric[ExamMetric.HEMOGLOBIN]!!, 0.0001)
        assertEquals(18.1, byMetric[ExamMetric.ALT]!!, 0.0001)
        assertEquals(19.7, byMetric[ExamMetric.AST]!!, 0.0001)
        assertEquals(5.3, byMetric[ExamMetric.HBA1C]!!, 0.0001)
    }

    @Test
    fun decimalCommasAndThousands_areParsed() {
        val byMetric = values(
            """
            Fecha de toma: 25/10/2026
            Estradiol 23,8 pg/mL
            Testosterona total 1.234,5 ng/dL
            Hemoglobina 17,5 g/dL
            """.trimIndent(),
        )

        assertEquals(23.8, byMetric[ExamMetric.ESTRADIOL]!!, 0.0001)
        assertEquals(1234.5, byMetric[ExamMetric.TESTOSTERONE_TOTAL]!!, 0.0001)
        assertEquals(17.5, byMetric[ExamMetric.HEMOGLOBIN]!!, 0.0001)
    }

    @Test
    fun referenceRanges_areIgnored() {
        val byMetric = values(
            """
            Fecha: 25/10/2026
            Estradiol 23.8 pg/mL 12.0 - 40.0
            Testosterona total 494 ng/dL 264 - 916
            Vitamina D 13.8 ng/mL 30 - 100
            """.trimIndent(),
        )

        assertEquals(23.8, byMetric[ExamMetric.ESTRADIOL]!!, 0.0001)
        assertEquals(494.0, byMetric[ExamMetric.TESTOSTERONE_TOTAL]!!, 0.0001)
        assertEquals(13.8, byMetric[ExamMetric.VITAMIN_D]!!, 0.0001)
    }

    @Test
    fun rangeOnlyLine_doesNotBorrowTheNextValue() {
        val byMetric = values(
            """
            Fecha: 25/10/2026
            Estradiol 12.0 - 40.0 pg/mL
            Testosterona total 494 ng/dL
            """.trimIndent(),
        )

        assertNull(byMetric[ExamMetric.ESTRADIOL])
        assertEquals(494.0, byMetric[ExamMetric.TESTOSTERONE_TOTAL]!!, 0.0001)
    }

    @Test
    fun valueOnNextLine_isRecognized() {
        val byMetric = values(
            """
            Fecha: 25/10/2026
            Estradiol (E2)
            Resultado: 23.8 pg/mL
            Testosterona total
            494 ng/dL
            """.trimIndent(),
        )

        assertEquals(23.8, byMetric[ExamMetric.ESTRADIOL]!!, 0.0001)
        assertEquals(494.0, byMetric[ExamMetric.TESTOSTERONE_TOTAL]!!, 0.0001)
    }

    @Test
    fun unitOnItsOwnLine_isSkipped() {
        val byMetric = values(
            """
            Fecha: 25/10/2026
            Estradiol (E2)
            pg/mL
            23.8
            """.trimIndent(),
        )

        assertEquals(23.8, byMetric[ExamMetric.ESTRADIOL]!!, 0.0001)
    }

    @Test
    fun headerBetweenLabelAndValue_isSkipped() {
        val byMetric = values(
            """
            Fecha: 25/10/2026
            Estradiol (E2)
            Resultado Unidad Referencia
            23.8 pg/mL 12.0 - 40.0
            """.trimIndent(),
        )

        assertEquals(23.8, byMetric[ExamMetric.ESTRADIOL]!!, 0.0001)
    }

    @Test
    fun commonDateFormats_areParsed() {
        val samples = mapOf(
            "Fecha: 2026-10-25" to LocalDate.of(2026, 10, 25),
            "Fecha: 25/10/2026" to LocalDate.of(2026, 10, 25),
            "Fecha: 25 de octubre de 2026" to LocalDate.of(2026, 10, 25),
            "Date: October 25, 2026" to LocalDate.of(2026, 10, 25),
            "Fecha: 25 OCT 2026" to LocalDate.of(2026, 10, 25),
            "Date: OCT 25, 2026" to LocalDate.of(2026, 10, 25),
        )

        samples.forEach { (line, expected) ->
            assertEquals(
                line,
                expected,
                ExamPdfParser.parse("$line\nEstradiol 23.8").date,
            )
        }
    }

    @Test
    fun ambiguousNumericDate_usesReportLanguage() {
        assertEquals(
            LocalDate.of(2026, 6, 5),
            ExamPdfParser.parse("Fecha de toma: 05/06/2026\nEstradiol 23.8").date,
        )
        assertEquals(
            LocalDate.of(2026, 5, 6),
            ExamPdfParser.parse("Collection Date: 05/06/2026\nEstradiol 23.8").date,
        )
    }

    @Test
    fun birthDate_isNotUsedAsExamDate() {
        val parsed = ExamPdfParser.parse(
            """
            Fecha de nacimiento: 12/05/1990
            Estradiol 23.8
            """.trimIndent(),
        )

        assertNull(parsed.date)
    }

    @Test
    fun firstOccurrenceOfAMetricWins() {
        val byMetric = values(
            """
            Estradiol 23.8
            Estradiol 30.0
            """.trimIndent(),
        )

        assertEquals(23.8, byMetric[ExamMetric.ESTRADIOL]!!, 0.0001)
    }

    @Test
    fun realPdfBoxExtraction_isParsed() {
        // Text produced by PDFBox 2.0.27's PDFTextStripper(sortByPosition = true)
        // from a generated laboratory report, i.e. the same pipeline the app uses.
        val text = checkNotNull(javaClass.getResourceAsStream("/lab_report_text.txt"))
            .bufferedReader()
            .use { it.readText() }
        val parsed = ExamPdfParser.parse(text)

        assertEquals(LocalDate.of(2026, 10, 25), parsed.date)
        assertEquals(12, parsed.results.size)
        val byMetric = parsed.results.associate { it.metric to it.value }
        assertEquals(23.8, byMetric[ExamMetric.ESTRADIOL]!!, 0.0001)
        assertEquals(494.0, byMetric[ExamMetric.TESTOSTERONE_TOTAL]!!, 0.0001)
        assertEquals(10.1, byMetric[ExamMetric.LH]!!, 0.0001)
        assertEquals(2.6, byMetric[ExamMetric.FSH]!!, 0.0001)
        assertEquals(9.59, byMetric[ExamMetric.PROLACTIN]!!, 0.0001)
        assertEquals(13.8, byMetric[ExamMetric.VITAMIN_D]!!, 0.0001)
        assertEquals(4.04, byMetric[ExamMetric.POTASSIUM]!!, 0.0001)
        assertEquals(49.9, byMetric[ExamMetric.HEMATOCRIT]!!, 0.0001)
        assertEquals(17.5, byMetric[ExamMetric.HEMOGLOBIN]!!, 0.0001)
        assertEquals(18.1, byMetric[ExamMetric.ALT]!!, 0.0001)
        assertEquals(19.7, byMetric[ExamMetric.AST]!!, 0.0001)
        assertEquals(5.3, byMetric[ExamMetric.HBA1C]!!, 0.0001)
    }

    @Test
    fun unrelatedText_isIgnored() {
        val parsed = ExamPdfParser.parse(
            """
            Laboratorio clínico
            Paciente: Testosterona
            Teléfono: 555 123 4567
            Página 1 de 2
            """.trimIndent(),
        )

        assertTrue(parsed.results.isEmpty())
    }
}
