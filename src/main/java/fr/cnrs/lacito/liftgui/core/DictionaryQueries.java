package fr.cnrs.lacito.liftgui.core;

import fr.cnrs.lacito.liftapi.LiftDictionaryRegistry;
import fr.cnrs.lacito.liftapi.model.*;
import java.util.List;
import java.util.Map;

/**
 * Questions about a dictionary that do not involve the user interface: which entry owns a
 * component, how often a header feature is used, renaming a feature, etc.
 */
public final class DictionaryQueries {

    private DictionaryQueries() {}

    /**
     * The entry a component belongs to (itself for an entry), or null (e.g. header objects).
     * Multitexts are resolved through the component carrying them.
     */
    public static LiftEntry owningEntry(Object o) {
        Object n = o instanceof MultiText mt ? mt.getParent() : o;
        while (n instanceof AbstractLiftRoot r) {
            if (r instanceof LiftEntry e) return e;
            n = r.getParentNode();
        }
        return null;
    }

    /** The nearest sense or entry above a component (the component itself if it is one), or null. */
    public static AbstractIdentifiable owningSenseOrEntry(Object o) {
        Object n = o instanceof MultiText mt ? mt.getParent() : o;
        while (n instanceof AbstractLiftRoot r) {
            if (r instanceof LiftSense || r instanceof LiftEntry) return (AbstractIdentifiable) r;
            n = r.getParentNode();
        }
        return null;
    }

    /** The value of the first trait named {@code name}, or "". */
    public static String traitValue(HasTrait o, String name) {
        if (o == null || o.getTraits() == null) return "";
        return o.getTraits().stream()
            .filter(t -> name.equals(t.getName()))
            .findFirst()
            .map(LiftTrait::getValue)
            .orElse("");
    }

    /** The id of a feature, or "" when there is none. */
    public static String featureId(Feature f) {
        return f == null ? "" : f.getId();
    }

    /** The translation of an example with the given type id ("" for an untyped translation), or null. */
    public static MultiText translationOfType(LiftExample ex, String typeId) {
        return ex.getTranslations().entrySet().stream()
            .filter(e -> typeId.equals(featureId(e.getKey())))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    /** Text searched by the entry table: code, forms, pronunciations, definitions and glosses. */
    public static String searchText(LiftEntry entry) {
        if (entry == null) return "";
        StringBuilder sb = new StringBuilder();
        appendSep(sb, traitValue(entry, "code"));
        for (Form f : entry.getForms().getForms()) appendSep(sb, f.toPlainText());
        for (LiftPronunciation p : entry.getPronunciations()) {
            for (Form f : p.getPronunciation().getForms()) appendSep(sb, f.toPlainText());
        }
        for (LiftSense s : entry.getSenses()) {
            for (Form f : s.getDefinition().getForms()) appendSep(sb, f.toPlainText());
            for (Form f : s.getGlosses().getForms()) appendSep(sb, f.toPlainText());
        }
        return sb.toString();
    }

    private static void appendSep(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(part);
        }
    }

    /* ─── Usage ─── */

    /** Number of multitexts having a form in {@code lang}. */
    public static long countLanguageUsage(String lang, List<MultiText> multiTexts) {
        if (lang == null || lang.isBlank() || multiTexts == null) return 0;
        return multiTexts.stream().filter(mt -> mt.getForm(lang).isPresent()).count();
    }
}
