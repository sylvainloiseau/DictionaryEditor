package fr.cnrs.lacito.liftgui.ui.table.filter;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A text filter: the cells containing, starting with or equal to a text (or a regular
 * expression), or the empty / non-empty cells. Case is ignored unless {@code matchCase};
 * texts are compared in NFC ({@link TextMatch}).
 *
 * @param pattern   the text or regular expression
 * @param op        the comparison
 * @param matchCase whether case matters
 * @param regex     whether {@code pattern} is a regular expression: <i>contains</i> = find,
 *                  <i>starts with</i> = lookingAt, <i>is exactly</i> = matches
 * @param negate    keeps the cells that do <b>not</b> match
 */
public record TextFilter(String pattern, TextOp op, boolean matchCase, boolean regex, boolean negate)
    implements ColumnFilter {

    public TextFilter {
        pattern = pattern == null ? "" : pattern;
        op = op == null ? TextOp.CONTAINS : op;
    }

    /** "contains {@code text}", ignoring case. */
    public static TextFilter contains(String text) {
        return new TextFilter(text, TextOp.CONTAINS, false, false, false);
    }

    public TextFilter withPattern(String p) { return new TextFilter(p, op, matchCase, regex, negate); }
    public TextFilter withOp(TextOp o) { return new TextFilter(pattern, o, matchCase, regex, negate); }
    public TextFilter withMatchCase(boolean b) { return new TextFilter(pattern, op, b, regex, negate); }
    public TextFilter withRegex(boolean b) { return new TextFilter(pattern, op, matchCase, b, negate); }
    public TextFilter withNegate(boolean b) { return new TextFilter(pattern, op, matchCase, regex, b); }

    @Override
    public boolean active() {
        if (!op.usesText()) return true;
        if (pattern.isBlank()) return false;
        return !regex || error().isEmpty();
    }

    @Override
    public Optional<String> error() {
        if (!regex || !op.usesText() || pattern.isEmpty()) return Optional.empty();
        try {
            compilePattern();
            return Optional.empty();
        } catch (PatternSyntaxException e) {
            return Optional.of(e.getDescription() + (e.getIndex() >= 0 ? " (" + e.getIndex() + ")" : ""));
        }
    }

    private Pattern compilePattern() {
        int flags = Pattern.UNICODE_CASE;
        if (!matchCase) flags |= Pattern.CASE_INSENSITIVE;
        return Pattern.compile(TextMatch.nfc(pattern), flags);
    }

    @Override
    public CellMatcher compile() {
        if (op == TextOp.EMPTY) return (t, v) -> t.isEmpty() != negate;
        if (op == TextOp.NOT_EMPTY) return (t, v) -> !t.isEmpty() != negate;
        if (!active()) return CellMatcher.ALL;
        Predicate<String> m;
        if (regex) {
            Pattern p = compilePattern();
            m = switch (op) {
                case STARTS_WITH -> s -> p.matcher(s).lookingAt();
                case EQUALS -> s -> p.matcher(s).matches();
                default -> s -> p.matcher(s).find();
            };
            return (t, v) -> m.test(TextMatch.nfc(t)) != negate;
        }
        String q = matchCase ? TextMatch.nfc(pattern.trim()) : TextMatch.fold(pattern.trim());
        m = switch (op) {
            case STARTS_WITH -> s -> s.startsWith(q);
            case EQUALS -> s -> s.equals(q);
            default -> s -> s.contains(q);
        };
        return (t, v) -> m.test(matchCase ? TextMatch.nfc(t) : TextMatch.fold(t)) != negate;
    }
}
