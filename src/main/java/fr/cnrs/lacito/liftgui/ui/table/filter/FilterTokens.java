package fr.cnrs.lacito.liftgui.ui.table.filter;

import fr.cnrs.lacito.liftgui.ui.table.ValueType;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * The text form of the filters, for saved views:
 * <pre>
 * text:CONTAINS:cr:pattern      (flags: c match case, r regex, n negate)
 * options:en:noun,verb          (flags: e include empty, n negate)
 * range:NUMBER:e:2:5            (flag: e include empty)
 * </pre>
 * The characters {@code % : ,} of the texts are written {@code %xx}.
 */
public final class FilterTokens {

    private FilterTokens() {}

    public static String format(ColumnFilter f) {
        return switch (f) {
            case TextFilter t -> "text:" + t.op().name() + ":" + flags(t.matchCase(), 'c', t.regex(), 'r', t.negate(), 'n')
                + ":" + esc(t.pattern());
            case OptionFilter o -> "options:" + flags(o.includeEmpty(), 'e', o.negate(), 'n', false, ' ') + ":"
                + String.join(",", o.selected().stream().map(FilterTokens::esc).toList());
            case RangeFilter r -> "range:" + r.type().name() + ":" + flags(r.includeEmpty(), 'e', false, ' ', false, ' ')
                + ":" + esc(r.min()) + ":" + esc(r.max());
        };
    }

    /** Reads {@link #format}; empty for a malformed token. */
    public static Optional<ColumnFilter> parse(String token) {
        if (token == null) return Optional.empty();
        String[] p = token.split(":", -1);
        try {
            return switch (p[0]) {
                case "text" -> p.length == 4
                    ? Optional.of(new TextFilter(unesc(p[3]), TextOp.valueOf(p[1]), p[2].contains("c"), p[2].contains("r"),
                        p[2].contains("n")))
                    : Optional.empty();
                case "options" -> {
                    if (p.length != 3) yield Optional.empty();
                    List<String> vals = p[2].isEmpty() ? List.of()
                        : Arrays.stream(p[2].split(",", -1)).map(FilterTokens::unesc).toList();
                    yield Optional.of(new OptionFilter(new LinkedHashSet<>(vals), p[1].contains("e"), p[1].contains("n")));
                }
                case "range" -> p.length == 5
                    ? Optional.of(new RangeFilter(ValueType.valueOf(p[1]), unesc(p[3]), unesc(p[4]), p[2].contains("e")))
                    : Optional.empty();
                default -> Optional.empty();
            };
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String flags(boolean a, char ca, boolean b, char cb, boolean c, char cc) {
        StringBuilder sb = new StringBuilder();
        if (a) sb.append(ca);
        if (b) sb.append(cb);
        if (c) sb.append(cc);
        return sb.toString();
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '%' || ch == ':' || ch == ',') sb.append('%').append(String.format("%02x", (int) ch));
            else sb.append(ch);
        }
        return sb.toString();
    }

    private static String unesc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '%' && i + 3 <= s.length()) {
                sb.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
