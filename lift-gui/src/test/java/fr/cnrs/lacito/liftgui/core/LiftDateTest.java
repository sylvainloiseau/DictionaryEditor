package fr.cnrs.lacito.liftgui.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LiftDateTest {

    private static LiftDate d(String s) {
        return LiftDate.parse(s).orElseThrow(() -> new AssertionError("unreadable: " + s));
    }

    @Test
    void partialDatesAreIntervals() {
        LiftDate year = d("2023");
        LiftDate month = d("2023-05");
        LiftDate day = d("2023-05-01");
        LiftDate stamp = d("2023-05-01T12:00:00Z");
        assertTrue(year.start() <= month.start() && month.end() <= year.end());
        assertTrue(month.start() <= day.start() && day.end() <= month.end());
        assertEquals(1, stamp.end() - stamp.start());
        assertEquals(day.start() + 12 * 3600, stamp.start());
    }

    @Test
    void readsTheFormsFoundInLiftFiles() {
        assertTrue(LiftDate.parse("2023-05-01T12:00:00+02:00").isPresent());
        assertTrue(LiftDate.parse("2023-05-01T12:00").isPresent());
        assertTrue(LiftDate.parse("2023-05-01 12:00:00").isPresent());
        assertTrue(LiftDate.parse("").isEmpty());
        assertTrue(LiftDate.parse("yesterday").isEmpty());
        assertTrue(LiftDate.parse("2023-13").isEmpty());
    }

    @Test
    void overlapsUsesPartialBoundsAsWholeIntervals() {
        LiftDate may = d("2023-05-10");
        assertTrue(may.overlaps(d("2023"), d("2023")));
        assertTrue(may.overlaps(null, d("2023-05")));
        assertFalse(may.overlaps(d("2023-06"), null));
        assertTrue(d("2023").overlaps(d("2023-12-31"), null), "a year overlaps its last day");
    }

    @Test
    void sortsByStart() {
        assertTrue(d("2022").compareTo(d("2023-01-01")) < 0);
        assertTrue(d("2023-01-01").compareTo(d("2023")) < 0, "same start: the shorter first");
    }
}
