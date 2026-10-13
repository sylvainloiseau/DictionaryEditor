package fr.cnrs.lacito.liftgui.ui.table;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ColumnLayoutTest {

    private final List<ColumnSpec<String>> catalog = List.of(
        Columns.<String>text("a", "A", s -> s),
        Columns.<String>text("b", "B", String::toUpperCase).hidden(),
        Columns.<String>perLanguage("c", "C", List.of("en", "fr"), (s, lang) -> lang + ":" + s)
    );

    @Test
    void defaultsKeepTheVisibleColumnsInCatalogOrder() {
        assertEquals(List.of("a", "c"), ColumnLayout.defaults(catalog).columnIds());
    }

    @Test
    void applyFollowsTheLayoutOrderAndIgnoresUnknownIds() {
        List<ColumnSpec<String>> shown = new ColumnLayout(List.of("c", "gone", "b")).apply(catalog);
        assertEquals(List.of("c", "b"), shown.stream().map(ColumnSpec::id).toList());
    }

    @Test
    void specsGiveCellTextsAndLeafHeaders() {
        ColumnSpec<String> c = catalog.get(2);
        assertTrue(c.multilingual());
        assertEquals("fr:x", c.text("x", "fr"));
        assertEquals("", c.text(null, "fr"));
        assertEquals("C [en]", new Leaf<>(c, "en").header());
        assertEquals("A", new Leaf<>(catalog.get(0), null).header());
    }

    @Test
    void withersReturnModifiedCopies() {
        ColumnSpec<String> a = catalog.get(0);
        ColumnSpec<String> narrow = a.width(50).textFilter();
        assertEquals(ColumnSpec.DEFAULT_WIDTH, a.prefWidth());
        assertEquals(50, narrow.prefWidth());
        assertEquals(ColumnSpec.FilterMode.TEXT, narrow.filter());
        assertEquals(ColumnSpec.FilterMode.AUTO, a.filter());
    }
}
