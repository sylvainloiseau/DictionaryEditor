package fr.cnrs.lacito.liftgui.ui.table;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftgui.ui.table.ColumnLayout.ColumnRef;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ColumnLayoutTest {

    private final List<ColumnSpec<String>> catalog = List.of(
        Columns.<String>text("a", "A", s -> s),
        Columns.<String>text("b", "B", String::toUpperCase).hidden(),
        Columns.<String>perLanguage("c", "C", List.of("en", "fr", "tpi"), (s, lang) -> lang + ":" + s)
    );

    @Test
    void defaultsKeepTheVisibleColumnsInCatalogOrder() {
        assertEquals(List.of("a", "c"), ColumnLayout.defaults(catalog).columnIds());
    }

    @Test
    void applyFollowsTheLayoutOrderAndIgnoresUnknownIds() {
        List<ColumnSpec<String>> shown = ColumnLayout.ofIds(List.of("c", "gone", "b")).apply(catalog);
        assertEquals(List.of("c", "b"), shown.stream().map(ColumnSpec::id).toList());
        assertTrue(shown.get(1).visible(), "a hidden column chosen by the user is shown");
    }

    @Test
    void chosenLanguagesAreShownInTheirOrder() {
        ColumnLayout layout = new ColumnLayout(List.of(new ColumnRef("c", List.of("tpi", "en", "xx"), null)));
        ColumnSpec<String> c = layout.apply(catalog).get(0);
        assertEquals(List.of("tpi", "en"), c.languages());
        ColumnLayout none = new ColumnLayout(List.of(new ColumnRef("c", List.of("xx"), null)));
        assertTrue(none.apply(catalog).isEmpty(), "no chosen language exists: the column is left out");
        assertEquals(List.of("en", "fr", "tpi"), ColumnLayout.ofIds(List.of("c")).apply(catalog).get(0).languages(),
            "no languages = all of them");
    }

    @Test
    void textFormRoundTripsWithSpecialCharacters() {
        ColumnLayout layout = new ColumnLayout(List.of(
            ColumnRef.of("entry.form"),
            new ColumnRef("field:a|b@c,d~e%f", List.of("en", "fr"), null),
            new ColumnRef("count:senses", List.of(), FilterKind.TEXT)));
        String text = layout.format();
        assertEquals(layout, ColumnLayout.parse(text));
        assertEquals("entry.form|x@en,fr", ColumnLayout.parse("entry.form||x@en,fr~BAD").format());
        assertTrue(ColumnLayout.parse(null).columns().isEmpty());
    }

    @Test
    void layoutEditsKeepTheOtherColumns() {
        ColumnLayout l = ColumnLayout.ofIds(List.of("a", "b", "c"));
        assertEquals(List.of("b", "c"), l.without("a").columnIds());
        assertEquals(List.of("b", "c", "a"), l.moved("a", 5).columnIds());
        assertEquals(List.of("a", "c", "b"), l.moved("c", 1).columnIds());
        assertEquals(List.of("en"), l.with(new ColumnRef("c", List.of("en"), null)).ref("c").orElseThrow().languages());
        assertEquals(List.of("a", "b", "c", "d"), l.with(ColumnRef.of("d")).columnIds());
    }

    @Test
    void chosenFilterIsAppliedWhenTheTypeAcceptsIt() {
        List<ColumnSpec<String>> cat = List.of(Columns.<String>text("n", "N", s -> s).number());
        assertEquals(FilterKind.RANGE, cat.get(0).filterKind());
        assertEquals(FilterKind.TEXT,
            new ColumnLayout(List.of(new ColumnRef("n", List.of(), FilterKind.TEXT))).apply(cat).get(0).filterKind());
        assertEquals(FilterKind.RANGE,
            new ColumnLayout(List.of(new ColumnRef("n", List.of(), FilterKind.OPTIONS))).apply(cat).get(0).filterKind(),
            "a number column has no options filter");
    }

    @Test
    void specsGiveCellTextsAndLeafHeaders() {
        ColumnSpec<String> c = catalog.get(2);
        assertTrue(c.multilingual());
        assertEquals("fr:x", c.text("x", "fr"));
        assertEquals("", c.text(null, "fr"));
        assertEquals("C [en]", new Leaf<>(c, "en").header());
        assertEquals("c@en", new Leaf<>(c, "en").id());
        assertEquals("A", new Leaf<>(catalog.get(0), null).header());
    }

    @Test
    void withersReturnModifiedCopies() {
        ColumnSpec<String> a = catalog.get(0);
        ColumnSpec<String> narrow = a.width(50).option();
        assertEquals(ColumnSpec.DEFAULT_WIDTH, a.prefWidth());
        assertEquals(50, narrow.prefWidth());
        assertEquals(ValueType.OPTION, narrow.type());
        assertEquals(ValueType.TEXT, a.type());
        assertEquals(FilterKind.OPTIONS, narrow.filterKind());
    }

    @Test
    void throughReadsTheTargetAndIsEmptyWithoutOne() {
        ColumnSpec<String> upper = Columns.<String>text("u", "U", String::toUpperCase);
        ColumnSpec<Integer> via = upper.<Integer>through("up:u", "Up › U", i -> i > 0 ? "x" + i : null);
        assertEquals("X3", via.text(3, null));
        assertEquals("", via.text(-1, null));
        assertFalse(via.applies(-1, null));
        assertTrue(via.applies(1, null));
    }

    @Test
    void sortsByType() {
        List<String> numbers = new ArrayList<>(List.of("10", "", "9", "2"));
        numbers.sort(ValueType.NUMBER.comparator());
        assertEquals(List.of("2", "9", "10", ""), numbers);
        List<String> dates = new ArrayList<>(List.of("2024-01-02", "", "2023", "2023-06-01T10:00:00Z"));
        dates.sort(ValueType.DATE.comparator());
        assertEquals(List.of("2023", "2023-06-01T10:00:00Z", "2024-01-02", ""), dates);
    }
}
