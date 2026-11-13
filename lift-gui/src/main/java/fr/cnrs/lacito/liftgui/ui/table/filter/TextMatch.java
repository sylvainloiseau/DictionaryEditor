package fr.cnrs.lacito.liftgui.ui.table.filter;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Text normalisation shared by the filters and the search: NFC, so that a precomposed
 * letter and the same letter with a combining diacritic match (IPA, orthographies typed
 * with different input methods).
 */
public final class TextMatch {

    private TextMatch() {}

    public static String nfc(String s) {
        if (s == null) return "";
        return Normalizer.isNormalized(s, Normalizer.Form.NFC) ? s : Normalizer.normalize(s, Normalizer.Form.NFC);
    }

    /** NFC and lower case, for case-insensitive comparisons. */
    public static String fold(String s) {
        return nfc(s).toLowerCase(Locale.ROOT);
    }
}
