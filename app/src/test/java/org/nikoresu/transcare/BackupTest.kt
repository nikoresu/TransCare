package org.nikoresu.transcare

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.nikoresu.transcare.data.BackupManager

class BackupTest {

    @Test
    fun fileNameUsesIsoDate() {
        assertEquals(
            "TransCare-Data-2026-10-23.json",
            BackupManager.fileName(LocalDate.of(2026, 10, 23)),
        )
    }
}
