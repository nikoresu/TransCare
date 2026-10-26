package org.nikoresu.transcare

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.domain.model.Appointment

class AppointmentTest {

    private val day = LocalDate.of(2026, 10, 24)

    @Test
    fun date_isDerivedFromDayStartMillis() {
        val appointment = Appointment(dayStartMillis = Appointment.dayStartMillisOf(day))

        assertEquals(day, appointment.date)
    }

    @Test
    fun startMillis_sortsAppointmentsByTime() {
        val morning = Appointment(dayStartMillis = Appointment.dayStartMillisOf(day), hour = 9, minute = 0)
        val afternoon = Appointment(dayStartMillis = Appointment.dayStartMillisOf(day), hour = 15, minute = 30)

        assertTrue(morning.startMillis < afternoon.startMillis)
    }

    @Test
    fun monthRange_coversTheWholeMonth() {
        val (startMillis, endMillis) = Appointment.monthRange(YearMonth.of(2026, 10))

        assertEquals(Appointment.dayStartMillisOf(LocalDate.of(2026, 10, 1)), startMillis)
        assertEquals(Appointment.dayStartMillisOf(LocalDate.of(2026, 11, 1)), endMillis)
    }

    @Test
    fun formattedTime_usesTwoDigitFormat() {
        assertEquals("09:05", Appointment(hour = 9, minute = 5).formattedTime)
        assertEquals("15:30", Appointment(hour = 15, minute = 30).formattedTime)
    }
}
