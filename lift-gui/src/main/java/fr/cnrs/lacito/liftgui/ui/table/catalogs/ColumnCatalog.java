package fr.cnrs.lacito.liftgui.ui.table.catalogs;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithField;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithoutField;
import fr.cnrs.lacito.liftapi.model.AbstractNotable;
import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftEtymology;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftField;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinition;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinitionTarget;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftapi.model.LiftRelation;
import fr.cnrs.lacito.liftapi.model.LiftReversal;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftapi.model.LiftVariant;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSection;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.table.Columns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;

/**
 * All the columns available for a kind of component, in sections:
 * <ol>
 *   <li>its own columns: the catalog of the kind (whose visible columns are the default
 *       layout), then its other properties (citation form, order, dates…), hidden;</li>
 *   <li>its fields, traits, notes and annotations, by name or type, as found in the
 *       dictionary (and declared in its header);</li>
 *   <li>the number of each kind of subcomponent, direct and nested;</li>
 *   <li>for each kind of ancestor it can have (nearest first), the columns of the nearest
 *       ancestor of that kind: its own columns, extensions and counts (not its ancestors,
 *       which have sections of their own).</li>
 * </ol>
 * Multitext columns (own texts, fields, notes, ancestors' texts) are editable; typing in an
 * empty field or note cell creates it ({@link ExtensionCells}).
 */
public final class ColumnCatalog {

    /** The plain catalog of a kind, by class (its visible columns are the default layout). */
    @FunctionalInterface
    public interface BaseCatalogs {
        Optional<List<? extends ColumnSpec<?>>> of(Class<?> type, LiftDictionary d);
    }

    private ColumnCatalog() {}

    public static <T> List<ColumnSection<T>> sections(Class<T> type, LiftDictionary d, BaseCatalogs base,
                                                      ExtensionCells.Gesture gesture) {
        Index index = new Index(d);
        List<ColumnSection<T>> out = new ArrayList<>();
        out.add(new ColumnSection<>("own", kindLabel(type), own(type, d, base)));
        List<ColumnSpec<T>> ext = extensions(type, d, index, gesture);
        if (!ext.isEmpty()) out.add(new ColumnSection<>("extensions", I18n.get("colsec.extensions"), ext));
        List<ColumnSpec<T>> counts = counts(type);
        if (!counts.isEmpty()) out.add(new ColumnSection<>("counts", I18n.get("colsec.counts"), counts));
        for (Class<?> a : ModelTree.ancestorKinds(type)) {
            List<ColumnSpec<T>> cols = ancestorColumns(a, d, base, index, gesture);
            if (!cols.isEmpty()) {
                out.add(new ColumnSection<>("up:" + ModelTree.kindId(a), I18n.get("colsec.ancestor", kindLabel(a)), cols));
            }
        }
        return out;
    }

    /** "Sense", "Entry"… */
    public static String kindLabel(Class<?> c) {
        String key = "kind." + c.getSimpleName();
        return I18n.getBundle().containsKey(key) ? I18n.get(key) : c.getSimpleName();
    }

    /* ─── Own columns ─── */

    @SuppressWarnings("unchecked")
    private static <T> List<ColumnSpec<T>> own(Class<T> type, LiftDictionary d, BaseCatalogs base) {
        List<ColumnSpec<T>> cols = new ArrayList<>();
        base.of(type, d).ifPresent(list -> list.forEach(c -> cols.add((ColumnSpec<T>) c)));
        Set<String> ids = new HashSet<>();
        cols.forEach(c -> ids.add(c.id()));
        for (ColumnSpec<T> extra : extras(type, d)) if (ids.add(extra.id())) cols.add(extra.hidden());
        return cols;
    }

    /** The properties of a kind that its catalog does not show. */
    @SuppressWarnings("unchecked")
    private static <T> List<ColumnSpec<T>> extras(Class<T> type, LiftDictionary d) {
        String k = ModelTree.kindId(type);
        List<ColumnSpec<?>> out = new ArrayList<>();
        Set<String> metaLangs = d.getMetaLanguageManager().getLanguages();
        if (type == LiftEntry.class) {
            out.add(Columns.<LiftEntry>multiText("entry.citation", I18n.get("col.citation"),
                languages(d, d.getObjectLanguageManager().getLanguages(), e -> ((LiftEntry) e).getCitations(),
                    d.getLiftDictionaryRegistry().getEntries()), LiftEntry::getCitations));
            out.add(Columns.<LiftEntry>number("entry.order", I18n.get("col.order"),
                e -> e.getOrder().map(ColumnCatalog::integer).orElse(null)));
            out.add(Columns.<LiftEntry>text("entry.dateDeleted", I18n.get("col.dateDeleted"), e -> e.getDateDeleted().orElse(""))
                .date());
        }
        if (type == LiftSense.class) {
            out.add(Columns.<LiftSense>number("sense.order", I18n.get("col.order"), s -> s.getOrder().orElse(null)));
            out.add(Columns.<LiftSense>text("sense.id", I18n.get("col.id"), s -> s.getId().orElse("")));
        }
        if (type == LiftRelation.class) {
            out.add(Columns.<LiftRelation>number("relation.order", I18n.get("col.order"), r -> r.getOrder().orElse(null)));
            out.add(Columns.<LiftRelation>text("relation.ref", I18n.get("col.ref"), r -> r.getRefId().orElse("")));
        }
        if (type == LiftExample.class) {
            out.add(Columns.<LiftExample>text("example.source", I18n.get("col.source"), x -> x.getSource().orElse("")));
        }
        if (type == LiftVariant.class) {
            out.add(Columns.<LiftVariant>text("variant.ref", I18n.get("col.ref"), v -> v.getRefId().orElse("")));
        }
        if (type == LiftEtymology.class) {
            out.add(Columns.<LiftEtymology>multiText("etymology.gloss", I18n.get("col.gloss"), metaLangs, LiftEtymology::getGlosses));
        }
        if (AbstractExtensibleWithoutField.class.isAssignableFrom(type)) {
            out.add(Columns.<AbstractExtensibleWithoutField>text(k + ".dateCreated", I18n.get("col.dateCreated"),
                x -> x.getDateCreated().orElse("")).date());
            out.add(Columns.<AbstractExtensibleWithoutField>text(k + ".dateModified", I18n.get("field.dateModified"),
                x -> x.getDateModified().orElse("")).date());
        }
        List<ColumnSpec<T>> typed = new ArrayList<>();
        out.forEach(c -> typed.add((ColumnSpec<T>) c));
        return typed;
    }

    private static Integer integer(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The given languages and those used by {@code text} in {@code rows}, sorted. */
    private static List<String> languages(LiftDictionary d, Set<String> declared, Function<Object, MultiText> text,
                                          List<?> rows) {
        Set<String> langs = new TreeSet<>(declared);
        for (Object r : rows) {
            MultiText mt = text.apply(r);
            if (mt != null) mt.getLangs().stream().filter(l -> l != null && !l.isBlank()).forEach(langs::add);
        }
        return List.copyOf(langs);
    }

    /* ─── Fields, traits, notes, annotations ─── */

    /** What the dictionary holds, by class of the holder: field names, trait names… One pass over the registry. */
    static final class Index {
        final Map<Class<?>, Set<String>> fields = new HashMap<>();
        final Map<String, Set<String>> fieldLangs = new HashMap<>();
        final Map<Class<?>, Set<String>> traits = new HashMap<>();
        final Map<Class<?>, Set<String>> notes = new HashMap<>();
        final Map<String, Set<String>> noteLangs = new HashMap<>();
        final Map<Class<?>, Set<String>> annotations = new HashMap<>();

        Index(LiftDictionary d) {
            var reg = d.getLiftDictionaryRegistry();
            for (LiftField f : reg.getFields()) {
                if (f.getParent() == null) continue;
                fields.computeIfAbsent(f.getParent().getClass(), k -> new TreeSet<>()).add(f.getName());
                fieldLangs.computeIfAbsent(f.getName(), k -> new TreeSet<>()).addAll(f.getText().getLangs());
            }
            for (LiftTrait t : reg.getTraits()) {
                if (t.getParent() != null) traits.computeIfAbsent(t.getParent().getClass(), k -> new TreeSet<>()).add(t.getName());
            }
            for (LiftNote n : reg.getNotes()) {
                if (n.getParent() == null) continue;
                String type = DictionaryQueries.featureId(n.getType());
                notes.computeIfAbsent(n.getParent().getClass(), k -> new TreeSet<>()).add(type);
                noteLangs.computeIfAbsent(type, k -> new TreeSet<>()).addAll(n.getText().getLangs());
            }
            for (LiftAnnotation a : reg.getAnnotations()) {
                if (a.getParent() != null) {
                    annotations.computeIfAbsent(a.getParent().getClass(), k -> new TreeSet<>())
                        .add(DictionaryQueries.featureId(a.getType()));
                }
            }
            // Fields and traits declared in the header for a kind, even unused.
            var header = d.getHeader();
            for (Class<?> c : ModelTree.KINDS) {
                target(c).ifPresent(t -> {
                    for (LiftFieldAndTraitDefinition def : header.getFieldsDefinitionsFor(t)) {
                        if (AbstractExtensibleWithField.class.isAssignableFrom(c)) {
                            fields.computeIfAbsent(c, k -> new TreeSet<>()).add(def.getName());
                        }
                    }
                    for (LiftFieldAndTraitDefinition def : header.getTraitsDefinitionsFor(t)) {
                        if (AbstractExtensibleWithoutField.class.isAssignableFrom(c)) {
                            traits.computeIfAbsent(c, k -> new TreeSet<>()).add(def.getName());
                        }
                    }
                });
            }
        }

        static Optional<LiftFieldAndTraitDefinitionTarget> target(Class<?> c) {
            Map<Class<?>, LiftFieldAndTraitDefinitionTarget> m = Map.of(
                LiftEntry.class, LiftFieldAndTraitDefinitionTarget.ENTRY,
                LiftSense.class, LiftFieldAndTraitDefinitionTarget.SENSE,
                LiftExample.class, LiftFieldAndTraitDefinitionTarget.EXAMPLE,
                LiftVariant.class, LiftFieldAndTraitDefinitionTarget.VARIANT,
                LiftPronunciation.class, LiftFieldAndTraitDefinitionTarget.PRONUNCIATION,
                LiftNote.class, LiftFieldAndTraitDefinitionTarget.NOTE,
                LiftEtymology.class, LiftFieldAndTraitDefinitionTarget.ETYMOLOGY,
                LiftRelation.class, LiftFieldAndTraitDefinitionTarget.RELATION,
                LiftReversal.class, LiftFieldAndTraitDefinitionTarget.REVERSAL);
            return Optional.ofNullable(m.get(c));
        }

        static Set<String> get(Map<Class<?>, Set<String>> m, Class<?> c) {
            return m.getOrDefault(c, Set.of());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<ColumnSpec<T>> extensions(Class<T> type, LiftDictionary d, Index index,
                                                      ExtensionCells.Gesture gesture) {
        List<ColumnSpec<?>> out = new ArrayList<>();
        Set<String> metaLangs = d.getMetaLanguageManager().getLanguages();
        if (AbstractExtensibleWithField.class.isAssignableFrom(type)) {
            for (String name : Index.get(index.fields, type)) {
                Set<String> langs = new TreeSet<>(metaLangs);
                langs.addAll(index.fieldLangs.getOrDefault(name, Set.of()));
                langs.removeIf(l -> l == null || l.isBlank());
                out.add(textOf("field:" + name, I18n.get("col.fieldOf", name), List.copyOf(langs),
                    (AbstractExtensibleWithField row) -> {
                        LiftField f = ExtensionCells.field(row, name);
                        return f == null ? null : f.getText();
                    },
                    (row, lang, text) -> ExtensionCells.commitField(d, gesture, row, name, lang, text)));
            }
        }
        if (AbstractNotable.class.isAssignableFrom(type)) {
            for (String noteType : Index.get(index.notes, type)) {
                Set<String> langs = new TreeSet<>(metaLangs);
                langs.addAll(index.noteLangs.getOrDefault(noteType, Set.of()));
                langs.removeIf(l -> l == null || l.isBlank());
                String title = I18n.get("col.noteOf", noteType.isEmpty() ? I18n.get("placeholder.noType") : noteType);
                out.add(textOf("note:" + noteType, title, List.copyOf(langs),
                    (AbstractNotable row) -> {
                        LiftNote n = ExtensionCells.note(row, noteType);
                        return n == null ? null : n.getText();
                    },
                    (row, lang, text) -> ExtensionCells.commitNote(d, gesture, row, noteType, lang, text)));
            }
        }
        if (AbstractExtensibleWithoutField.class.isAssignableFrom(type)) {
            for (String name : Index.get(index.traits, type)) {
                out.add(Columns.<AbstractExtensibleWithoutField>text("trait:" + name, I18n.get("col.traitOf", name),
                        x -> String.join(", ", traitValues(x, name)))
                    .values((x, lang) -> traitValues(x, name))
                    .option());
            }
            for (String name : Index.get(index.annotations, type)) {
                out.add(Columns.<AbstractExtensibleWithoutField>text("annotation:" + name, I18n.get("col.annotationOf", name),
                        x -> String.join(", ", annotationValues(x, name)))
                    .values((x, lang) -> annotationValues(x, name))
                    .option());
            }
        }
        List<ColumnSpec<T>> typed = new ArrayList<>();
        out.forEach(c -> typed.add(((ColumnSpec<T>) c).hidden()));
        return typed;
    }

    /** An editable multitext column on a text that may not exist yet (field, note). */
    private static <R> ColumnSpec<R> textOf(String id, String title, List<String> langs, Function<R, MultiText> text,
                                            ColumnSpec.CellCommit<R> commit) {
        return ColumnSpec.<R>of(id, title, langs, (row, lang) -> Columns.formText(text.apply(row), lang))
            .live((row, lang) -> {
                MultiText mt = text.apply(row);
                return mt == null ? new ReadOnlyStringWrapper("") : mt.formTextProperty(lang);
            })
            .editable(commit)
            .markEmpty(true);
    }

    private static List<String> traitValues(AbstractExtensibleWithoutField x, String name) {
        return x.getTraits().stream().filter(t -> name.equals(t.getName()))
            .map(t -> Objects.toString(t.getValue(), "")).filter(s -> !s.isEmpty()).toList();
    }

    private static List<String> annotationValues(AbstractExtensibleWithoutField x, String name) {
        return x.getAnnotations().stream().filter(a -> name.equals(DictionaryQueries.featureId(a.getType())))
            .map(a -> Objects.toString(a.getValue(), "")).filter(s -> !s.isEmpty()).toList();
    }

    /* ─── Counts ─── */

    private static <T> List<ColumnSpec<T>> counts(Class<T> type) {
        List<ColumnSpec<T>> out = new ArrayList<>();
        for (ModelTree.Child c : ModelTree.children(type)) {
            out.add(Columns.<T>number("count:" + c.id(), I18n.get("col.count", I18n.get(c.i18nKey())),
                row -> c.list().apply(row).size()).hidden());
        }
        for (ModelTree.Child c : ModelTree.nested(type)) {
            out.add(Columns.<T>number("count:" + c.id(), I18n.get("col.countAll", I18n.get(c.i18nKey())),
                row -> c.list().apply(row).size()).hidden());
        }
        return out;
    }

    /* ─── Ancestors ─── */

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> List<ColumnSpec<T>> ancestorColumns(Class<?> a, LiftDictionary d, BaseCatalogs base, Index index,
                                                           ExtensionCells.Gesture gesture) {
        List<ColumnSpec<Object>> cols = new ArrayList<>();
        Class<Object> ac = (Class<Object>) a;
        cols.addAll(own(ac, d, base));
        cols.addAll(extensions(ac, d, index, gesture));
        cols.addAll(counts(ac));
        String prefix = "up:" + ModelTree.kindId(a) + "/";
        String label = kindLabel(a);
        List<ColumnSpec<T>> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ColumnSpec<Object> c : cols) {
            if (!ids.add(c.id())) continue;
            ColumnSpec<T> through = c.<T>through(prefix + c.id(), label + " › " + c.title(), row -> ModelTree.nearest(row, ac));
            list.add(through.hidden());
        }
        return list;
    }
}
