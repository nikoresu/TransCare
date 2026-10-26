package org.nikoresu.transcare

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.domain.model.DiaryEntry
import org.nikoresu.transcare.domain.model.DiaryEntryType

class DiaryEntryTest {

    private val day = LocalDate.of(2026, 10, 24)

    @Test
    fun date_isDerivedFromDayStartMillis() {
        val entry = DiaryEntry(dayStartMillis = DiaryEntry.dayStartMillisOf(day))

        assertEquals(day, entry.date)
    }

    @Test
    fun blankEntriesHaveNoTitleOrText() {
        assertTrue(DiaryEntry().isBlank)
        assertTrue(DiaryEntry(title = "   ", text = "\n").isBlank)
    }

    @Test
    fun entryWithOnlyTitleOrOnlyTextIsNotBlank() {
        assertFalse(DiaryEntry(title = "Headache").isBlank)
        assertFalse(DiaryEntry(text = "Felt better after lunch.").isBlank)
    }

    @Test
    fun dayStartMillisRoundTripsThroughDate() {
        val millis = DiaryEntry.dayStartMillisOf(day)

        assertEquals(day, DiaryEntry.dateOf(millis))
    }

    @Test
    fun entryDefaultsToNotesType() {
        assertEquals(DiaryEntryType.NOTES, DiaryEntry().type)
    }

    @Test
    fun storageValueFallsBackToNotes() {
        assertEquals(DiaryEntryType.THOUGHTS, DiaryEntryType.fromStorageValue("THOUGHTS"))
        assertEquals(DiaryEntryType.NOTES, DiaryEntryType.fromStorageValue("DREAMS"))
        assertEquals(DiaryEntryType.NOTES, DiaryEntryType.fromStorageValue(null))
    }
}
