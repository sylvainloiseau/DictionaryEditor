package fr.cnrs.lacito.liftgui.ui.table.widgets;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.table.filter.ColumnFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.OptionFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.RangeFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.TextFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.TextOp;
import java.util.List;

/** The texts that describe filters: button summaries and chips. */
public final class FilterTexts {

    private FilterTexts() {}

    public static String opLabel(TextOp op) {
        return I18n.get(switch (op) {
            case CONTAINS -> "filter.op.contains";
            case STARTS_WITH -> "filter.op.startsWith";
            case EQUALS -> "filter.op.equals";
            case EMPTY -> "filter.op.empty";
            case NOT_EMPTY -> "filter.op.notEmpty";
        });
    }

    /** The value shown for the empty value of an options filter. */
    public static String value(String v) {
        return v == null || v.isEmpty() ? I18n.get("filter.empty") : v;
    }

    /** "All", "noun", "noun, verb", "3 selected", "not noun". */
    public static String optionSummary(OptionFilter f) {
        if (f == null || !f.active()) return I18n.get("filter.all");
        List<String> values = new java.util.ArrayList<>(f.selected());
        if (f.includeEmpty()) values.add("");
        String s = values.size() <= 2
            ? String.join(", ", values.stream().map(FilterTexts::value).toList())
            : I18n.get("filter.nSelected", values.size());
        return f.negate() ? I18n.get("filter.not") + " " + s : s;
    }

    /** "Any", "2 – 5", "= 0", "≥ 2", "≤ 2024-03". */
    public static String rangeSummary(RangeFilter f) {
        if (f == null || !f.active()) return I18n.get("filter.any");
        String s;
        if (!f.min().isEmpty() && !f.max().isEmpty()) s = f.min().equals(f.max()) ? "= " + f.min() : f.min() + " – " + f.max();
        else if (!f.min().isEmpty()) s = "≥ " + f.min();
        else s = "≤ " + f.max();
        return f.includeEmpty() ? s + " +" + I18n.get("filter.empty") : s;
    }

    /** The text of a chip, after the column name: « contains "to" », « : noun, verb », « ≥ 2 ». */
    public static String describe(ColumnFilter f) {
        return switch (f) {
            case TextFilter t -> {
                String op = t.regex() && t.op().usesText() ? I18n.get("filter.op.matches") : opLabel(t.op());
                String s = t.op().usesText() ? op + " " + (t.regex() ? "/" + t.pattern() + "/" : "\"" + t.pattern() + "\"") : op;
                if (t.matchCase() && t.op().usesText()) s += " (Aa)";
                yield t.negate() ? I18n.get("filter.not") + " " + s : s;
            }
            case OptionFilter o -> ": " + optionSummary(o);
            case RangeFilter r -> rangeSummary(r);
        };
    }
}
