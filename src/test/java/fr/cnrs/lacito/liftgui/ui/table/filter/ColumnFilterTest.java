package fr.cnrs.lacito.liftgui.ui.table.filter;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftgui.ui.table.ValueType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ColumnFilterTest {

    private static boolean test(ColumnFilter f, String text) {
        return f.compile().test(text, () -> text.isEmpty() ? List.of() : List.of(text));
    }

    private static boolean test(ColumnFilter f, List<String> values) {
        return f.compile().test(String.join(", ", values), () -> values);
    }

    @Test
    void textIgnoresCaseUnlessAsked() {
        TextFilter f = TextFilter.contains("Walk");
        assertTrue(test(f, "to walk"));
        assertFalse(test(f.withMatchCase(true), "to walk"));
        assertTrue(test(f.withMatchCase(true), "to Walk"));
    }

    @Test
    void textOperations() {
        TextFilter f = TextFilter.contains("to");
        assertTrue(test(f.withOp(TextOp.STARTS_WITH), "to walk"));
        assertFalse(test(f.withOp(TextOp.STARTS_WITH), "into"));
        assertTrue(test(f.withOp(TextOp.EQUALS), "TO"));
        assertFalse(test(f.withOp(TextOp.EQUALS), "top"));
        assertTrue(test(f.withNegate(true), "walk"));
        assertFalse(test(f.withNegate(true), "to walk"));
    }

    @Test
    void emptyAndNotEmptyNeedNoText() {
        TextFilter empty = new TextFilter("", TextOp.EMPTY, false, false, false);
        assertTrue(empty.active());
        assertTrue(test(empty, ""));
        assertFalse(test(empty, "x"));
        TextFilter notEmpty = empty.withOp(TextOp.NOT_EMPTY);
        assertTrue(test(notEmpty, "x"));
        assertFalse(test(notEmpty, ""));
        assertFalse(TextFilter.contains("  ").active());
    }

    @Test
    void regularExpressions() {
        TextFilter f = new TextFilter("^to\\s", TextOp.CONTAINS, false, true, false);
        assertTrue(test(f, "To walk"));
        assertFalse(test(f, "into"));
        assertTrue(test(f.withPattern("wa.k").withOp(TextOp.EQUALS), "walk"));
        assertFalse(test(f.withPattern("wa.k").withOp(TextOp.EQUALS), "walks"));
        assertTrue(test(f.withPattern("wa").withOp(TextOp.STARTS_WITH), "walk"));
    }

    @Test
    void anInvalidRegularExpressionIsReportedAndKeepsEverything() {
        TextFilter f = new TextFilter("(oops", TextOp.CONTAINS, false, true, false);
        assertTrue(f.error().isPresent());
        assertFalse(f.active());
        assertTrue(test(f, "anything"));
    }

    @Test
    void combiningAndPrecomposedLettersMatch() {
        String precomposed = "café";
        String combining = "café";
        assertTrue(test(TextFilter.contains(precomposed), combining));
        assertTrue(test(TextFilter.contains(combining), precomposed));
        assertTrue(test(new TextFilter("fé$", TextOp.CONTAINS, false, true, false), combining));
    }

    @Test
    void optionsMatchAnyValueOfTheCell() {
        OptionFilter f = OptionFilter.of("noun", "verb");
        assertTrue(test(f, List.of("adj", "verb")));
        assertFalse(test(f, List.of("adj")));
        assertFalse(test(f, List.of()));
        assertTrue(test(f.withIncludeEmpty(true), List.of()));
        assertTrue(test(f.withNegate(true), List.of("adj")));
        assertFalse(new OptionFilter(Set.of(), false, false).active());
        assertTrue(new OptionFilter(Set.of(), true, false).active(), "only (empty)");
    }

    @Test
    void numberRanges() {
        RangeFilter f = new RangeFilter(ValueType.NUMBER, "2", "5", false);
        assertTrue(test(f, "2"));
        assertTrue(test(f, "5"));
        assertFalse(test(f, "6"));
        assertFalse(test(f, ""));
        assertTrue(test(f.withIncludeEmpty(true), ""));
        assertTrue(test(f.withMax(""), "100"));
        RangeFilter zero = new RangeFilter(ValueType.NUMBER, "0", "0", false);
        assertTrue(test(zero, "0"));
        assertFalse(test(zero, "1"));
        assertFalse(new RangeFilter(ValueType.NUMBER, "", "", true).active());
    }

    @Test
    void dateRangesOverlapPartialDates() {
        RangeFilter f = new RangeFilter(ValueType.DATE, "2023-05", "2023", false);
        assertTrue(test(f, "2023-05-01T10:00:00Z"));
        assertTrue(test(f, "2023-12-31"));
        assertFalse(test(f, "2023-04-30"));
        assertFalse(test(f, "2024-01-01"));
        assertTrue(test(f, "2023"), "a year overlapping the range");
    }

    @Test
    void unreadableBoundsAreErrors() {
        RangeFilter f = new RangeFilter(ValueType.NUMBER, "two", "", false);
        assertTrue(f.error().isPresent());
        assertFalse(f.active());
        assertTrue(new RangeFilter(ValueType.DATE, "", "soon", false).error().isPresent());
    }

    @Test
    void tokensRoundTrip() {
        List<ColumnFilter> filters = List.of(
            new TextFilter("a:b,c%d", TextOp.STARTS_WITH, true, true, true),
            TextFilter.contains("x"),
            new OptionFilter(Set.of("noun", "a,b"), true, true),
            new RangeFilter(ValueType.DATE, "2023", "2024-01", true),
            new RangeFilter(ValueType.NUMBER, "", "3", false));
        for (ColumnFilter f : filters) assertEquals(f, FilterTokens.parse(f.toToken()).orElseThrow(), f.toToken());
        assertTrue(FilterTokens.parse("text:BAD::x").isEmpty());
        assertTrue(FilterTokens.parse("nothing").isEmpty());
    }
}
