package fr.cnrs.lacito.liftgui.ui.table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which columns of a catalog are shown, in which order, with which languages.
 * <p>
 * {@link #defaults(List)} gives the columns visible by default. A layout chosen by the user
 * is applied with {@link #apply(List)}; ids that do not exist in the catalog are ignored (and
 * kept: a stored layout survives changes of the catalog and of the dictionary).
 * <p>
 * Text form ({@link #format()}, {@link #parse(String)}), as stored in the preferences:
 * <pre>entry.form|entry.morphType|sense.gloss@fr,en|count:senses~TEXT</pre>
 * one column per {@code |}-separated item: its id, then optionally {@code @} and its
 * languages, then optionally {@code ~} and the filter chosen for it. The characters
 * {@code % | @ , ~} of ids and languages are written {@code %xx}.
 */
public record ColumnLayout(List<ColumnRef> columns) {

    /**
     * A shown column.
     *
     * @param id        the id of the column
     * @param languages the languages shown, in that order; empty for all the languages of
     *                  the column (including languages added later)
     * @param filterAs  the filter chosen by the user; null for the default of the column
     */
    public record ColumnRef(String id, List<String> languages, FilterKind filterAs) {

        public ColumnRef {
            Objects.requireNonNull(id);
            languages = languages == null ? List.of() : List.copyOf(languages);
        }

        public static ColumnRef of(String id) {
            return new ColumnRef(id, List.of(), null);
        }

        public ColumnRef withLanguages(List<String> langs) {
            return new ColumnRef(id, langs, filterAs);
        }

        public ColumnRef withFilterAs(FilterKind kind) {
            return new ColumnRef(id, languages, kind);
        }

        /** Whether every language of the column is shown. */
        public boolean allLanguages() {
            return languages.isEmpty();
        }
    }

    public ColumnLayout {
        columns = List.copyOf(columns);
    }

    /** A layout of the given ids, all languages. */
    public static ColumnLayout ofIds(List<String> ids) {
        return new ColumnLayout(ids.stream().map(ColumnRef::of).toList());
    }

    /** The columns visible by default, in catalog order. */
    public static <T> ColumnLayout defaults(List<ColumnSpec<T>> catalog) {
        return ofIds(catalog.stream().filter(ColumnSpec::visible).map(ColumnSpec::id).toList());
    }

    /** The ids of the columns, in order. */
    public List<String> columnIds() {
        return columns.stream().map(ColumnRef::id).toList();
    }

    public Optional<ColumnRef> ref(String id) {
        return columns.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public boolean contains(String id) {
        return ref(id).isPresent();
    }

    /**
     * The specs of the catalog listed in this layout, in the layout's order, each restricted
     * to the chosen languages (in the chosen order) and with the chosen filter. A
     * multilingual column none of whose chosen languages exists is left out.
     */
    public <T> List<ColumnSpec<T>> apply(List<ColumnSpec<T>> catalog) {
        Map<String, ColumnSpec<T>> byId = catalog.stream()
            .collect(Collectors.toMap(ColumnSpec::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<ColumnSpec<T>> shown = new ArrayList<>();
        for (ColumnRef ref : columns) {
            ColumnSpec<T> spec = byId.remove(ref.id());
            if (spec == null) continue;
            if (spec.multilingual() && !ref.allLanguages()) {
                List<String> langs = ref.languages().stream().filter(spec.languages()::contains).toList();
                if (langs.isEmpty()) continue;
                spec = spec.withLanguages(langs);
            }
            if (ref.filterAs() != null) spec = spec.filterAs(ref.filterAs());
            shown.add(spec.shown(true));
        }
        return shown;
    }

    /* ─── Modified copies ─── */

    /** This layout with {@code ref} in place of the column of the same id, or appended. */
    public ColumnLayout with(ColumnRef ref) {
        List<ColumnRef> list = new ArrayList<>(columns);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(ref.id())) {
                list.set(i, ref);
                return new ColumnLayout(list);
            }
        }
        list.add(ref);
        return new ColumnLayout(list);
    }

    public ColumnLayout without(String id) {
        return new ColumnLayout(columns.stream().filter(c -> !c.id().equals(id)).toList());
    }

    /** This layout with the column {@code id} moved to {@code index} (among the others). */
    public ColumnLayout moved(String id, int index) {
        Optional<ColumnRef> ref = ref(id);
        if (ref.isEmpty()) return this;
        List<ColumnRef> list = new ArrayList<>(without(id).columns);
        list.add(Math.max(0, Math.min(index, list.size())), ref.get());
        return new ColumnLayout(list);
    }

    /* ─── Text form ─── */

    public String format() {
        StringBuilder sb = new StringBuilder();
        for (ColumnRef c : columns) {
            if (sb.length() > 0) sb.append('|');
            sb.append(escape(c.id()));
            if (!c.languages().isEmpty()) {
                sb.append('@').append(c.languages().stream().map(ColumnLayout::escape).collect(Collectors.joining(",")));
            }
            if (c.filterAs() != null) sb.append('~').append(c.filterAs().name());
        }
        return sb.toString();
    }

    /** Reads {@link #format()}; malformed items are skipped. */
    public static ColumnLayout parse(String text) {
        List<ColumnRef> refs = new ArrayList<>();
        if (text == null || text.isEmpty()) return new ColumnLayout(refs);
        for (String item : text.split("\\|", -1)) {
            if (item.isEmpty()) continue;
            FilterKind kind = null;
            int tilde = item.indexOf('~');
            if (tilde >= 0) {
                try {
                    kind = FilterKind.valueOf(item.substring(tilde + 1));
                } catch (IllegalArgumentException e) {
                    kind = null;
                }
                item = item.substring(0, tilde);
            }
            List<String> langs = List.of();
            int at = item.indexOf('@');
            if (at >= 0) {
                langs = java.util.Arrays.stream(item.substring(at + 1).split(","))
                    .filter(s -> !s.isEmpty()).map(ColumnLayout::unescape).toList();
                item = item.substring(0, at);
            }
            if (item.isEmpty()) continue;
            refs.add(new ColumnRef(unescape(item), langs, kind));
        }
        return new ColumnLayout(refs);
    }

    private static final String SPECIAL = "%|@,~";

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (SPECIAL.indexOf(ch) >= 0) sb.append('%').append(String.format("%02x", (int) ch));
            else sb.append(ch);
        }
        return sb.toString();
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '%' && i + 2 < s.length()) {
                try {
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException e) {
                    // kept as is
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }
}
