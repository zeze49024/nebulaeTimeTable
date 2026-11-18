package cn.nebulae.timetable

import org.junit.Assert.*
import org.junit.Test

class TimetableTest {
    private fun line(first: String = "23:50", last: String = "00:15", trips: List<Trip> = emptyList()) =
        RailLine("test", "测试", "", "", "", listOf("甲", "乙"), listOf("乙"),
            listOf(listOf(first, last), listOf("", "")), trips, "")

    @Test fun midnightBelongsToPreviousOperatingDay() {
        assertEquals(86400 + 240, serviceSeconds(0, 4))
        assertEquals(4 * 3600, serviceSeconds(4, 0))
        assertEquals("00:04", clockLabel(86400 + 240))
    }

    @Test fun dwellIncludesAlreadyArrivedTrain() {
        val trip = Trip("A", "乙", listOf(Stop(0, 86400, 86460)))
        assertEquals(listOf(Arrival("A", 86400, 86460)), nextArrivals(line(trips = listOf(trip)), "甲", "乙", 86430))
        assertTrue(nextArrivals(line(trips = listOf(trip)), "甲", "乙", 86461).isEmpty())
    }

    @Test fun ordersByArrivalAndKeepsTwo() {
        val trips = listOf(Trip("3", "乙", listOf(Stop(0, 86700, 86700))),
            Trip("1", "乙", listOf(Stop(0, 86500, 86500))),
            Trip("2", "乙", listOf(Stop(0, 86600, 86600))))
        assertEquals(listOf("1", "2"), nextArrivals(line(trips = trips), "甲", "乙", 86400).map { it.id })
    }

    @Test fun appliesFirstLastToleranceAndNinetyMinuteWindow() {
        val trips = listOf(Trip("early", "乙", listOf(Stop(0, 85714, 85714))),
            Trip("valid", "乙", listOf(Stop(0, 85715, 85715))),
            Trip("late", "乙", listOf(Stop(0, 87386, 87386))))
        assertEquals(listOf("valid"), nextArrivals(line(trips = trips), "甲", "乙", 85000).map { it.id })
        val distant = Trip("far", "乙", listOf(Stop(0, 34201, 34201)))
        assertTrue(nextArrivals(line("", "", listOf(distant)), "甲", "乙", 28800).isEmpty())
    }

    @Test fun noServiceToCurrentOrUnknownStation() {
        assertTrue(nextArrivals(line(), "乙", "乙", 86400).isEmpty())
        assertTrue(nextArrivals(line(), "不存在", "乙", 86400).isEmpty())
    }
}
