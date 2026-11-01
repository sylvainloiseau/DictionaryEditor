package fr.cnrs.lacito.liftgui.core;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A LIFT date, which may be partial: {@code 2023}, {@code 2023-05}, {@code 2023-05-01},
 * {@code 2023-05-01T12:00:00Z}. A date stands for an interval {@code [start, end)} in
 * seconds since the epoch (UTC): {@code 2023} is the whole year, a full timestamp lasts one
 * second. Dates are compared by their start, then by their end.
 *
 * @param start first second of the interval (inclusive)
 * @param end   end of the interval (exclusive)
 * @param text  the text the date was parsed from
 */
public record LiftDate(long start, long end, String text) implements Comparable<LiftDate> {

    private static final Pattern YEAR = Pattern.compile("(\\d{4})");
    private static final Pattern MONTH = Pattern.compile("(\\d{4})-(\\d{1,2})");
    private static final Pattern DAY = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");

    /** Parses a (possibly partial) date; empty for an empty or unreadable text. */
    public static Optional<LiftDate> parse(String text) {
        if (text == null) return Optional.empty();
        String t = text.trim();
        if (t.isEmpty()) return Optional.empty();
        try {
            Matcher m;
            if ((m = YEAR.matcher(t)).matches()) {
                int y = Integer.parseInt(m.group(1));
                return Optional.of(new LiftDate(epoch(LocalDate.of(y, 1, 1)), epoch(LocalDate.of(y + 1, 1, 1)), t));
            }
            if ((m = MONTH.matcher(t)).matches()) {
                YearMonth ym = YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                return Optional.of(new LiftDate(epoch(ym.atDay(1)), epoch(ym.plusMonths(1).atDay(1)), t));
            }
            if ((m = DAY.matcher(t)).matches()) {
                LocalDate d = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)));
                return Optional.of(new LiftDate(epoch(d), epoch(d.plusDays(1)), t));
            }
            return Optional.of(timestamp(t));
        } catch (java.time.DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static LiftDate timestamp(String t) {
        String s = t.replace(' ', 'T');
        long sec;
        try {
            sec = OffsetDateTime.parse(s).toEpochSecond();
        } catch (DateTimeParseException e) {
            // No offset: UTC; seconds optional.
            String local = s.endsWith("Z") ? s.substring(0, s.length() - 1) : s;
            if (local.length() == 16) local += ":00";
            sec = java.time.LocalDateTime.parse(local).toEpochSecond(ZoneOffset.UTC);
        }
        return new LiftDate(sec, sec + 1, t);
    }

    private static long epoch(LocalDate d) {
        return d.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    }

    /** Whether this interval and {@code [from.start, to.end)} overlap; a null bound is open. */
    public boolean overlaps(LiftDate from, LiftDate to) {
        if (from != null && end <= from.start) return false;
        return to == null || start < to.end;
    }

    /** The day of the start of the interval (UTC). */
    public LocalDate startDay() {
        return java.time.Instant.ofEpochSecond(start).atZone(ZoneOffset.UTC).toLocalDate();
    }

    @Override
    public int compareTo(LiftDate o) {
        int c = Long.compare(start, o.start);
        return c != 0 ? c : Long.compare(end, o.end);
    }
}
