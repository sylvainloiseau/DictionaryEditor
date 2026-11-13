package fr.cnrs.lacito.liftgui.ui.table.filter;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftgui.ui.table.ValueType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class FilterEngineTest {

    record Row(String name, String pos, int senses, String date, List<String> tags) {}

    record Col(String id, ValueType type, Function<Row, String> text, Function<Row, List<String>> vals)
        implements FilterColumn<Row> {
        @Override public String text(Row row) { return text.apply(row); }
        @Override public List<String> values(Row row) {
            if (vals != null) return vals.apply(row);
            String t = text(row);
            return t.isEmpty() ? List.of() : List.of(t);
        }
    }

    private static final List<Col> COLUMNS = List.of(
        new Col("name", ValueType.TEXT, Row::name, null),
        new Col("pos", ValueType.OPTION, Row::pos, null),
        new Col("senses", ValueType.NUMBER, r -> String.valueOf(r.senses()), null),
        new Col("date", ValueType.DATE, Row::date, null),
        new Col("tags", ValueType.OPTION, r -> String.join(", ", r.tags()), Row::tags));

    private final List<Row> rows = List.of(
        new Row("walk", "verb", 2, "2023-01-05", List.of("a", "b")),
        new Row("talk", "verb", 1, "2023-03-01", List.of("b")),
        new Row("house", "noun", 0, "2024-02-01", List.of()),
        new Row("horse", "noun", 3, "", List.of("a")),
        new Row("big", "", 1, "2023-01-20", List.of("c")));

    private final FilterEngine<Row> engine = new FilterEngine<>(COLUMNS);

    {
        engine.setSearchText(Row::name);
    }

    private List<String> names(Map<String, ColumnFilter> filters, String search) {
        return rows.stream().filter(engine.predicate(filters, search)).map(Row::name).toList();
    }

    @Test
    void filtersCombineWithAndBetweenColumns() {
        assertEquals(List.of("walk", "talk"), names(Map.of("pos", OptionFilter.of("verb")), ""));
        assertEquals(List.of("walk"), names(Map.of("pos", OptionFilter.of("verb"),
            "senses", new RangeFilter(ValueType.NUMBER, "2", "", false)), ""));
        assertEquals(List.of("talk"), names(Map.of("pos", OptionFilter.of("verb")), "TALK"));
        assertEquals(List.of("walk", "horse"), names(Map.of("tags", OptionFilter.of("a")), null));
    }

    @Test
    void filtersOfUnknownColumnsAreIgnored() {
        assertEquals(5, names(Map.of("gone", TextFilter.contains("zzz")), "").size());
    }

    @Test
    void optionsAreCountedOverTheRowsOfTheOtherFilters() {
        Map<String, ColumnFilter> f = Map.of("senses", new RangeFilter(ValueType.NUMBER, "1", "", false),
            "pos", OptionFilter.of("noun"));
        List<FilterEngine.OptionCount> pos = engine.options(rows, "pos", f, "");
        // senses >= 1: walk, talk (verb), horse (noun), big (empty); house is hidden: noun counts 1.
        assertEquals(new FilterEngine.OptionCount("verb", 2), pos.get(0));
        assertEquals(new FilterEngine.OptionCount("noun", 1), pos.get(1));
        assertEquals(new FilterEngine.OptionCount("", 1), pos.get(pos.size() - 1));
        List<FilterEngine.OptionCount> tags = engine.options(rows, "tags", Map.of("pos", OptionFilter.of("noun")), "");
        assertTrue(tags.contains(new FilterEngine.OptionCount("a", 1)));
        assertTrue(tags.contains(new FilterEngine.OptionCount("c", 0)), "values of hidden rows come with 0");
        assertEquals(new FilterEngine.OptionCount("", 1), tags.get(tags.size() - 1));
    }

    @Test
    void boundsAndHistograms() {
        assertEquals(new FilterEngine.Bounds("0", "3"), engine.bounds(rows, "senses", Map.of(), "").orElseThrow());
        assertEquals(new FilterEngine.Bounds("2023-01-05", "2024-02-01"), engine.bounds(rows, "date", Map.of(), "").orElseThrow());
        List<FilterEngine.Bin> senses = engine.histogram(rows, "senses", Map.of(), "");
        assertEquals(List.of("0", "1", "2", "3"), senses.stream().map(FilterEngine.Bin::label).toList());
        assertEquals(List.of(1, 2, 1, 1), senses.stream().map(FilterEngine.Bin::count).toList());
        List<FilterEngine.Bin> dates = engine.histogram(rows, "date", Map.of(), "");
        assertEquals("2023-01", dates.get(0).label());
        assertEquals(2, dates.get(0).count());
        assertEquals(14, dates.size(), "every month from 2023-01 to 2024-02");
    }

    @Test
    void manyValuesGiveTwentyBars() {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 1000; i++) texts.add(String.valueOf(i));
        List<FilterEngine.Bin> bins = FilterEngine.numberBins(texts);
        assertEquals(20, bins.size());
        assertEquals(1000, bins.stream().mapToInt(FilterEngine.Bin::count).sum());
        assertEquals("0", bins.get(0).from());
        assertEquals("999", bins.get(19).to());
    }

    @Test
    void hidingNamesTheFiltersThatHideARow() {
        Map<String, ColumnFilter> f = Map.of("pos", OptionFilter.of("verb"), "name", TextFilter.contains("al"));
        FilterEngine.Hiding h = engine.hiding(rows.get(2), f, "hou");
        assertEquals(java.util.Set.of("pos", "name"), h.filters());
        assertFalse(h.search());
        assertTrue(engine.hiding(rows.get(2), Map.of(), "walk").search());
        assertFalse(engine.hiding(rows.get(0), f, "").hidden());
    }

    @Test
    void theCacheIsClearedOnDemand() {
        String[] value = {"one"};
        FilterEngine<Row> e = new FilterEngine<>(List.of(new Col("v", ValueType.TEXT, r -> value[0], null)));
        assertEquals("one", e.text(rows.get(0), 0));
        value[0] = "two";
        assertEquals("one", e.text(rows.get(0), 0), "cached");
        e.invalidate();
        assertEquals("two", e.text(rows.get(0), 0));
    }

    @Test
    void filteringALargeTableIsFast() {
        List<Row> many = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            many.add(new Row("w" + i, i % 3 == 0 ? "noun" : "verb", i % 7, "2023-0" + (1 + i % 9) + "-01", List.of("t" + i % 5)));
        }
        Map<String, ColumnFilter> f = Map.of("name", TextFilter.contains("1"), "pos", OptionFilter.of("verb"),
            "senses", new RangeFilter(ValueType.NUMBER, "1", "5", false));
        many.stream().filter(engine.predicate(f, "w")).count(); // fills the cache
        long t0 = System.nanoTime();
        long n = many.stream().filter(engine.predicate(Map.of("name", TextFilter.contains("12"), "pos", OptionFilter.of("verb"),
            "senses", new RangeFilter(ValueType.NUMBER, "1", "5", false)), "w")).count();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(n > 0);
        assertTrue(ms < 100, "refilter took " + ms + " ms");
    }
}
