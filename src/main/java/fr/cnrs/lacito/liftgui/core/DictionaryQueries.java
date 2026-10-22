package fr.cnrs.lacito.liftgui.core;

import fr.cnrs.lacito.liftapi.LiftDictionaryRegistry;
import fr.cnrs.lacito.liftapi.model.*;
import java.util.Collection;
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

    /* ─── Usage of header definitions ─── */

    /**
     * Number of components that use a feature of the header: as their type (notes,
     * translations, annotations, relations, etymologies, reversals), as their
     * grammatical info, or in the value of a trait whose definition points at the feature's set.
     */
    public static long countFeatureUsage(LiftDictionaryRegistry reg, Feature f) {
        if (reg == null || f == null) return 0;
        long n = 0;
        n += reg.getNotes().stream().filter(x -> x.getType() == f).count();
        n += reg.getTranslations().stream().filter(x -> x.getType() == f).count();
        n += reg.getAnnotations().stream().filter(x -> x.getType() == f).count();
        n += reg.getRelations().stream().filter(x -> x.getType() == f).count();
        n += reg.getEtymologies().stream().filter(x -> x.getType() == f).count();
        n += reg.getReversals().stream().filter(x -> x.getType() == f).count();
        n += reg.getGrammaticalInfos().stream().filter(g -> g.getGramInfoValue() == f).count();
        FeatureSet set = f.getParent();
        n += reg.getTraits().stream()
            .filter(t -> traitUsesFeatureSet(t, set) && traitValueTokens(t).contains(f.getId()))
            .count();
        return n;
    }

    /**
     * Number of fields and traits whose name refers to a field/trait definition. A trait
     * of that name that the definition does not fit (a definition for fields) refers to a
     * range or to nothing, and is not counted.
     */
    public static long countFieldOrTraitUsage(LiftDictionaryRegistry reg, LiftFieldAndTraitDefinition fd) {
        if (reg == null || fd == null) return 0;
        long fields = reg.getFields().stream().filter(f -> f.getSpecification() == fd).count();
        long traits = reg.getTraits().stream().filter(t -> t.getSpecification() == fd).count();
        return fields + traits;
    }

    /** Number of multitexts having a form in {@code lang}. */
    public static long countLanguageUsage(String lang, List<MultiText> multiTexts) {
        if (lang == null || lang.isBlank() || multiTexts == null) return 0;
        return multiTexts.stream().filter(mt -> mt.getForm(lang).isPresent()).count();
    }

    /**
     * Whether the values of a trait are features of {@code set}: the range of its
     * definition, or the range it is named after.
     */
    public static boolean traitUsesFeatureSet(LiftTrait t, FeatureSet set) {
        if (set == null) return false;
        return t.getSpecification().getResolvedFeatureSet().map(r -> r == set).orElse(false);
    }

    /**
     * The values of a trait: the ids of its features for an option trait (several for a
     * collection or a sequence; an id may contain spaces, as FLEx semantic domains
     * {@code "1.5 Plant"} do), the space-separated tokens of its value otherwise.
     */
    public static List<String> traitValueTokens(LiftTrait t) {
        return switch (t.getSpecification().getDataModel()) {
            case FEATURE -> t.getTypedValue() == null ? List.of() : List.of(((Feature) t.getTypedValue()).getId());
            case FEATURE_SET, FEATURE_LIST -> ((Collection<?>) t.getTypedValue()).stream()
                .map(f -> ((Feature) f).getId())
                .toList();
            default -> {
                String v = t.getValue();
                yield v == null || v.isBlank() ? List.of() : List.of(v.trim().split("\\s+"));
            }
        };
    }

    /**
     * Renames a feature of the header. Everything referring to it holds the feature object
     * and follows: the typed components, and the traits whose values are features of the
     * set (those of a definition with that option-range, and those named after the set).
     */
    public static void renameFeature(FeatureSet set, String oldId, String newId) {
        Feature element = set.getFeature(oldId);
        if (element == null) return;
        set.changeFeatureId(element, newId);
    }
}
