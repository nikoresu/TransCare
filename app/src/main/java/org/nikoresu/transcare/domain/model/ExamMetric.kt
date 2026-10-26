package org.nikoresu.transcare.domain.model

import androidx.annotation.StringRes
import java.math.BigDecimal
import java.math.RoundingMode
import org.nikoresu.transcare.R

/**
 * Lab metrics that are commonly monitored during feminizing hormone therapy,
 * plus a few general health markers.
 *
 * Reference values are general guides and can differ between laboratories.
 */
enum class ExamMetric(
    @param:StringRes val displayNameRes: Int,
    @param:StringRes val shortLabelRes: Int,
    val unit: String,
    val referenceLow: Double? = null,
    val referenceHigh: Double? = null,
    @param:StringRes val referenceNoteRes: Int? = null,
) {
    ESTRADIOL(
        displayNameRes = R.string.exam_metric_estradiol,
        shortLabelRes = R.string.exam_metric_estradiol_short,
        unit = "pg/mL",
        referenceLow = 100.0,
        referenceHigh = 200.0,
        referenceNoteRes = R.string.exam_metric_estradiol_note,
    ),
    TESTOSTERONE_TOTAL(
        displayNameRes = R.string.exam_metric_testosterone_total,
        shortLabelRes = R.string.exam_metric_testosterone_total_short,
        unit = "ng/dL",
        referenceHigh = 50.0,
        referenceNoteRes = R.string.exam_metric_testosterone_total_note,
    ),
    LH(
        displayNameRes = R.string.exam_metric_lh,
        shortLabelRes = R.string.exam_metric_lh_short,
        unit = "mIU/mL",
        referenceLow = 0.0,
        referenceHigh = 5.0,
        referenceNoteRes = R.string.exam_metric_lh_note,
    ),
    FSH(
        displayNameRes = R.string.exam_metric_fsh,
        shortLabelRes = R.string.exam_metric_fsh_short,
        unit = "mIU/mL",
        referenceLow = 0.0,
        referenceHigh = 5.0,
        referenceNoteRes = R.string.exam_metric_fsh_note,
    ),
    PROLACTIN(
        displayNameRes = R.string.exam_metric_prolactin,
        shortLabelRes = R.string.exam_metric_prolactin_short,
        unit = "ng/mL",
        referenceLow = 4.0,
        referenceHigh = 24.0,
        referenceNoteRes = R.string.exam_metric_prolactin_note,
    ),
    SHBG(
        displayNameRes = R.string.exam_metric_shbg,
        shortLabelRes = R.string.exam_metric_shbg_short,
        unit = "nmol/L",
        referenceLow = 20.0,
        referenceHigh = 130.0,
        referenceNoteRes = R.string.exam_metric_shbg_note,
    ),
    FREE_TESTOSTERONE(
        displayNameRes = R.string.exam_metric_free_testosterone,
        shortLabelRes = R.string.exam_metric_free_testosterone_short,
        unit = "pg/mL",
        referenceNoteRes = R.string.exam_metric_free_testosterone_note,
    ),
    DHT(
        displayNameRes = R.string.exam_metric_dht,
        shortLabelRes = R.string.exam_metric_dht_short,
        unit = "ng/dL",
        referenceNoteRes = R.string.exam_metric_dht_note,
    ),
    PROGESTERONE(
        displayNameRes = R.string.exam_metric_progesterone,
        shortLabelRes = R.string.exam_metric_progesterone_short,
        unit = "ng/mL",
        referenceNoteRes = R.string.exam_metric_progesterone_note,
    ),
    HEMATOCRIT(
        displayNameRes = R.string.exam_metric_hematocrit,
        shortLabelRes = R.string.exam_metric_hematocrit_short,
        unit = "%",
        referenceLow = 36.0,
        referenceHigh = 48.0,
        referenceNoteRes = R.string.exam_metric_hematocrit_note,
    ),
    HEMOGLOBIN(
        displayNameRes = R.string.exam_metric_hemoglobin,
        shortLabelRes = R.string.exam_metric_hemoglobin_short,
        unit = "g/dL",
        referenceLow = 12.0,
        referenceHigh = 16.0,
        referenceNoteRes = R.string.exam_metric_hemoglobin_note,
    ),
    POTASSIUM(
        displayNameRes = R.string.exam_metric_potassium,
        shortLabelRes = R.string.exam_metric_potassium_short,
        unit = "mmol/L",
        referenceLow = 3.5,
        referenceHigh = 5.1,
        referenceNoteRes = R.string.exam_metric_potassium_note,
    ),
    CREATININE(
        displayNameRes = R.string.exam_metric_creatinine,
        shortLabelRes = R.string.exam_metric_creatinine_short,
        unit = "mg/dL",
        referenceLow = 0.5,
        referenceHigh = 1.1,
        referenceNoteRes = R.string.exam_metric_creatinine_note,
    ),
    ALT(
        displayNameRes = R.string.exam_metric_alt,
        shortLabelRes = R.string.exam_metric_alt_short,
        unit = "U/L",
        referenceLow = 7.0,
        referenceHigh = 35.0,
        referenceNoteRes = R.string.exam_metric_alt_note,
    ),
    AST(
        displayNameRes = R.string.exam_metric_ast,
        shortLabelRes = R.string.exam_metric_ast_short,
        unit = "U/L",
        referenceLow = 8.0,
        referenceHigh = 33.0,
        referenceNoteRes = R.string.exam_metric_ast_note,
    ),
    VITAMIN_D(
        displayNameRes = R.string.exam_metric_vitamin_d,
        shortLabelRes = R.string.exam_metric_vitamin_d_short,
        unit = "ng/mL",
        referenceLow = 30.0,
        referenceHigh = 100.0,
        referenceNoteRes = R.string.exam_metric_vitamin_d_note,
    ),
    FERRITIN(
        displayNameRes = R.string.exam_metric_ferritin,
        shortLabelRes = R.string.exam_metric_ferritin_short,
        unit = "ng/mL",
        referenceLow = 15.0,
        referenceHigh = 150.0,
        referenceNoteRes = R.string.exam_metric_ferritin_note,
    ),
    HBA1C(
        displayNameRes = R.string.exam_metric_hba1c,
        shortLabelRes = R.string.exam_metric_hba1c_short,
        unit = "%",
        referenceLow = 4.0,
        referenceHigh = 5.6,
        referenceNoteRes = R.string.exam_metric_hba1c_note,
    ),
    ;

    /**
     * Human readable reference range, e.g. "100–200 pg/mL" or "under 50 ng/dL".
     * The [underLabel], [overLabel] and [labSpecificLabel] arguments come from
     * string resources so the result follows the app language.
     */
    fun referenceLabel(
        underLabel: String,
        overLabel: String,
        labSpecificLabel: String,
    ): String = when {
        referenceLow != null && referenceHigh != null ->
            "${formatExamNumber(referenceLow)}–${formatExamNumber(referenceHigh)} $unit"

        referenceHigh != null -> "$underLabel ${formatExamNumber(referenceHigh)} $unit"
        referenceLow != null -> "$overLabel ${formatExamNumber(referenceLow)} $unit"
        else -> labSpecificLabel
    }
}

/** Formats a lab value without trailing zeros, e.g. 150.0 -> "150", 0.5 -> "0.5". */
fun formatExamNumber(value: Double): String {
    if (!value.isFinite()) return "—"
    if (value == 0.0) return "0"
    return BigDecimal.valueOf(value)
        .setScale(3, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
