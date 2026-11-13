package fr.cnrs.lacito.liftfx.internal;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * The texts of the editors, in the locale set with {@link fr.cnrs.lacito.liftfx.LiftFx#setLocale}
 * (the default locale until then). English when the locale has no translation.
 *
 * Unlike an application, the library never reads or writes the user's preferences: the
 * application chooses the locale.
 */
public final class Messages {

    private static final String BUNDLE_BASE = "fr.cnrs.lacito.liftfx.internal.messages";

    /** The languages that have a translation; the others get messages.properties (English). */
    private static final Set<String> TRANSLATED = Set.of("fr");

    private static ResourceBundle load(Locale l) {
        // Asking for the root locale when there is no translation: getBundle would otherwise
        // fall back on the system's locale. (A ResourceBundle.Control, which would also do it,
        // is refused in a named module.)
        Locale lookup = TRANSLATED.contains(l.getLanguage()) ? Locale.of(l.getLanguage()) : Locale.ROOT;
        return ResourceBundle.getBundle(BUNDLE_BASE, lookup);
    }

    private static Locale locale = Locale.getDefault();
    private static ResourceBundle bundle = load(locale);

    private Messages() {}

    public static Locale getLocale() {
        return locale;
    }

    public static void setLocale(Locale l) {
        locale = l == null ? Locale.getDefault() : l;
        bundle = load(locale);
    }

    /** The text of {@code key}; "!key!" when there is none. */
    public static String get(String key) {
        String text = find(key);
        return text == null ? "!" + key + "!" : text;
    }

    /** The text of {@code key} with its {0}, {1}… replaced, numbers formatted in the locale. */
    public static String get(String key, Object... args) {
        return new MessageFormat(get(key), locale).format(args);
    }

    /** The text of {@code key}, or null when the library has none. */
    public static String find(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return null;
        }
    }
}
