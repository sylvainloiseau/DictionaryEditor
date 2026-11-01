package fr.cnrs.lacito.liftgui.ui.table;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * The columns chosen for each table, shared by the open views and the Preferences dialog,
 * and stored (globally, not per dictionary) through the functions given at construction.
 * A table without a chosen layout shows the default columns of its catalog.
 */
public final class ColumnLayoutStore {

    private final Function<String, String> readLayout;
    private final BiConsumer<String, String> writeLayout;
    private final Function<String, Map<String, Double>> readWidths;
    private final BiConsumer<String, Map<String, Double>> writeWidths;
    private final Map<String, ObjectProperty<ColumnLayout>> layouts = new HashMap<>();

    public ColumnLayoutStore(Function<String, String> readLayout, BiConsumer<String, String> writeLayout,
                             Function<String, Map<String, Double>> readWidths,
                             BiConsumer<String, Map<String, Double>> writeWidths) {
        this.readLayout = readLayout;
        this.writeLayout = writeLayout;
        this.readWidths = readWidths;
        this.writeWidths = writeWidths;
    }

    /** A store kept in memory only (tests, no preferences). */
    public static ColumnLayoutStore inMemory() {
        Map<String, String> l = new HashMap<>();
        Map<String, Map<String, Double>> w = new HashMap<>();
        return new ColumnLayoutStore(l::get, (k, v) -> { if (v == null) l.remove(k); else l.put(k, v); },
            k -> w.getOrDefault(k, Map.of()), w::put);
    }

    /** The layout chosen for a table, followed by the views; its value is null for the default one. */
    public ObjectProperty<ColumnLayout> property(String table) {
        return layouts.computeIfAbsent(table, k -> {
            String s = readLayout.apply(k);
            return new SimpleObjectProperty<>(s == null ? null : ColumnLayout.parse(s));
        });
    }

    public Optional<ColumnLayout> layout(String table) {
        return Optional.ofNullable(property(table).get());
    }

    /** Chooses the columns of a table (stored at once); null goes back to the default columns. */
    public void set(String table, ColumnLayout layout) {
        writeLayout.accept(table, layout == null ? null : layout.format());
        property(table).set(layout);
    }

    /** The layout of a table, or the default one of {@code catalog}. */
    public <T> ColumnLayout effective(String table, java.util.List<ColumnSpec<T>> catalog) {
        return layout(table).orElseGet(() -> ColumnLayout.defaults(catalog));
    }

    public Map<String, Double> widths(String table) {
        return readWidths.apply(table);
    }

    public void setWidths(String table, Map<String, Double> widths) {
        writeWidths.accept(table, widths);
    }
}
