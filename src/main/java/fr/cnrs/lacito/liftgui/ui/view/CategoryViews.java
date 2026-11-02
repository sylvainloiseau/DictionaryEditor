package fr.cnrs.lacito.liftgui.ui.view;

import fr.cnrs.lacito.liftapi.LiftDictionaryRegistry;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.ObjectLabels;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.nav.ViewKey;
import fr.cnrs.lacito.liftgui.ui.nav.ViewRequest;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.table.Columns;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import javafx.collections.FXCollections;

/**
 * The category views: how often each grammatical info, trait, translation type, note type,
 * relation type or field type is used. Selecting a row shows its counts and a button
 * opening the objects concerned.
 */
public final class CategoryViews {

    /** A counted value, with the kind of object it was found on when that matters. */
    public record CategoryRow(String parentType, String name, String value, long frequency) {}

    private CategoryViews() {}

    private static LiftDictionaryRegistry reg(EditorContext ctx) {
        return ctx.dictionary().getLiftDictionaryRegistry();
    }

    /** Counts the objects of {@code objects} by the row returned by {@code row} (with frequency 0). */
    private static <O> List<CategoryRow> count(Stream<O> objects, Function<O, CategoryRow> row) {
        Map<CategoryRow, Long> counts = new LinkedHashMap<>();
        objects.forEach(o -> counts.merge(row.apply(o), 1L, Long::sum));
        return counts.entrySet().stream()
            .map(e -> new CategoryRow(e.getKey().parentType(), e.getKey().name(), e.getKey().value(), e.getValue()))
            .sorted(Comparator.comparingLong(CategoryRow::frequency).reversed())
            .toList();
    }

    private static ColumnSpec<CategoryRow> frequencyColumn() {
        return Columns.<CategoryRow>text("category.frequency", I18n.get(Keys.COL_FREQUENCY), r -> String.valueOf(r.frequency()))
            .number()
            .width(100);
    }

    /** A value (shown as "(no type)" when empty) and its frequency. */
    private static List<ColumnSpec<CategoryRow>> valueColumns(String valueTitle) {
        return List.of(
            Columns.<CategoryRow>text("category.value", valueTitle,
                r -> r.value().isEmpty() ? I18n.get("placeholder.noType") : r.value()).width(250),
            frequencyColumn()
        );
    }

    private static ObjectTableView<CategoryRow> view(EditorContext ctx, List<CategoryRow> rows,
                                                     List<ColumnSpec<CategoryRow>> columns, String placeholder,
                                                     java.util.function.Consumer<CategoryRow> onSelect) {
        return new ObjectTableView<>(ctx, CategoryRow.class, columns, FXCollections.observableArrayList(rows),
            null, placeholder, onSelect);
    }

    /** Shows the counts of a row, with a button opening the objects concerned. */
    private static void summary(EditorContext ctx, String title, CategoryRow r, Map<String, String> values, ViewRequest access) {
        values.put(I18n.get("col.frequency"), String.valueOf(r.frequency()));
        ctx.editors().showSummary(title, values, I18n.get("btn.accessObjects"), () -> ctx.navigator().go(access));
    }

    private static Map<String, String> values(String k1, String v1) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k1, v1);
        return m;
    }

    /* ─── Rows ─── */

    private static List<CategoryRow> gramInfoRows(LiftDictionaryRegistry reg) {
        return count(reg.getSenses().stream().filter(s -> s.getGrammaticalInfo().isPresent()),
            s -> new CategoryRow("", "", s.getGrammaticalInfo().get().getGramInfoValue().getId(), 0));
    }

    private static List<CategoryRow> translationTypeRows(LiftDictionaryRegistry reg) {
        return count(reg.getExamples().stream().flatMap(ex -> ex.getTranslations().keySet().stream()),
            f -> new CategoryRow("", "", DictionaryQueries.featureId(f), 0));
    }

    private static List<CategoryRow> relationTypeRows(LiftDictionaryRegistry reg) {
        return count(reg.getRelations().stream(),
            rel -> new CategoryRow("", "", DictionaryQueries.featureId(rel.getType()), 0));
    }

    private static List<CategoryRow> fieldTypeRows(LiftDictionaryRegistry reg) {
        return count(reg.getFields().stream(), f -> new CategoryRow("", "", f.getName(), 0));
    }

    private static List<CategoryRow> noteTypeRows(LiftDictionaryRegistry reg) {
        return count(reg.getNotes().stream(),
            n -> new CategoryRow(ObjectLabels.describeParentType(n.getParent()), "", DictionaryQueries.featureId(n.getType()), 0));
    }

    private static List<CategoryRow> traitRows(LiftDictionaryRegistry reg) {
        return count(reg.getTraits().stream(),
            t -> new CategoryRow(ObjectLabels.describeParentType(t.getParent()), t.getName(),
                Objects.toString(t.getValue(), ""), 0));
    }

    /** The number of rows of a category view (as the navigation shows it); -1 for another view. */
    public static int rowCount(ViewKey key, LiftDictionaryRegistry reg) {
        return switch (key) {
            case GRAM_INFO -> gramInfoRows(reg).size();
            case TRAIT_VALUES -> traitRows(reg).size();
            case TRANSLATION_TYPES -> translationTypeRows(reg).size();
            case NOTE_TYPES -> noteTypeRows(reg).size();
            case RELATION_TYPES -> relationTypeRows(reg).size();
            case FIELD_TYPES -> fieldTypeRows(reg).size();
            default -> -1;
        };
    }

    /* ─── Views ─── */

    public static DictionaryView gramInfo(EditorContext ctx) {
        String title = I18n.get("nav.gramInfo");
        List<CategoryRow> rows = gramInfoRows(reg(ctx));
        return view(ctx, rows, valueColumns(I18n.get("col.value")), "placeholder.noData", r -> {
            List<LiftSense> senses = reg(ctx).getSenses().stream()
                .filter(s -> s.getGrammaticalInfo().map(g -> r.value().equals(g.getGramInfoValue().getId())).orElse(false))
                .toList();
            summary(ctx, title, r, values(I18n.get("col.value"), r.value()),
                ViewRequest.subset(ViewKey.SENSES, senses, title + ": " + r.value()));
        });
    }

    public static DictionaryView translationTypes(EditorContext ctx) {
        String title = I18n.get("nav.transTypes");
        List<CategoryRow> rows = translationTypeRows(reg(ctx));
        return view(ctx, rows, valueColumns(I18n.get("col.type")), "placeholder.noData", r -> {
            List<LiftExample> examples = reg(ctx).getExamples().stream()
                .filter(ex -> DictionaryQueries.translationOfType(ex, r.value()) != null)
                .toList();
            summary(ctx, title, r, values(I18n.get("col.value"), r.value()),
                ViewRequest.subset(ViewKey.EXAMPLES, examples, title + ": " + r.value()));
        });
    }

    public static DictionaryView relationTypes(EditorContext ctx) {
        String title = I18n.get("nav.relationTypes");
        List<CategoryRow> rows = relationTypeRows(reg(ctx));
        return view(ctx, rows, valueColumns(I18n.get("col.type")), "placeholder.noData", r -> {
            List<LiftRelation> relations = reg(ctx).getRelations().stream()
                .filter(rel -> r.value().equals(DictionaryQueries.featureId(rel.getType())))
                .toList();
            summary(ctx, title, r, values(I18n.get("col.value"), r.value()),
                ViewRequest.subset(ViewKey.RELATIONS, relations, title + ": " + r.value()));
        });
    }

    public static DictionaryView fieldTypes(EditorContext ctx) {
        String title = I18n.get("nav.fieldTypes");
        List<CategoryRow> rows = fieldTypeRows(reg(ctx));
        return view(ctx, rows, valueColumns(I18n.get("col.type")), "placeholder.noField", r -> {
            List<LiftField> fields = reg(ctx).getFields().stream()
                .filter(f -> r.value().equals(f.getName()))
                .toList();
            summary(ctx, title, r, values(I18n.get("col.type"), r.value()),
                ViewRequest.subset(ViewKey.FIELDS, fields, title + ": " + r.value()));
        });
    }

    public static DictionaryView noteTypes(EditorContext ctx) {
        String title = I18n.get("nav.noteTypes");
        List<CategoryRow> rows = noteTypeRows(reg(ctx));
        List<ColumnSpec<CategoryRow>> columns = List.of(
            Columns.<CategoryRow>text("category.value", I18n.get("col.type"), CategoryRow::value).option(),
            Columns.<CategoryRow>text("category.parentType", I18n.get("col.parentType"), CategoryRow::parentType).option(),
            frequencyColumn()
        );
        return view(ctx, rows, columns, "placeholder.noData", r -> {
            List<LiftNote> notes = reg(ctx).getNotes().stream()
                .filter(n -> r.value().equals(DictionaryQueries.featureId(n.getType()))
                    && r.parentType().equals(ObjectLabels.describeParentType(n.getParent())))
                .toList();
            Map<String, String> values = values(I18n.get("col.type"), r.value());
            values.put(I18n.get("col.parentType"), r.parentType());
            summary(ctx, title, r, values, ViewRequest.subset(ViewKey.NOTES, notes, title + ": " + r.value()));
        });
    }

    /** Traits, counted by kind of object, name and value; the objects are opened through their entries. */
    public static DictionaryView traits(EditorContext ctx) {
        String title = I18n.get("nav.traitValues");
        List<CategoryRow> rows = traitRows(reg(ctx));
        List<ColumnSpec<CategoryRow>> columns = List.of(
            Columns.<CategoryRow>text("trait.parentType", I18n.get(Keys.COL_PARENT_TYPE), CategoryRow::parentType).option(),
            Columns.<CategoryRow>text("trait.name", I18n.get(Keys.COL_NAME), CategoryRow::name).option(),
            Columns.<CategoryRow>text("trait.value", I18n.get(Keys.COL_VALUE), CategoryRow::value).option(),
            frequencyColumn()
        );
        return view(ctx, rows, columns, "placeholder.noTrait", r -> {
            List<LiftEntry> entries = reg(ctx).getTraits().stream()
                .filter(t -> r.name().equals(t.getName())
                    && r.value().equals(Objects.toString(t.getValue(), ""))
                    && r.parentType().equals(ObjectLabels.describeParentType(t.getParent())))
                .map(t -> DictionaryQueries.owningEntry(t))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
            Map<String, String> values = values(I18n.get("col.name"), r.name());
            values.put(I18n.get("col.value"), r.value());
            summary(ctx, title, r, values,
                ViewRequest.subset(ViewKey.ENTRIES, entries, title + ": " + r.name() + " = " + r.value()));
        });
    }
}
