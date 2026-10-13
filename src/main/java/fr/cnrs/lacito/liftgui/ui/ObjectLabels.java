package fr.cnrs.lacito.liftgui.ui;

import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;

/** Labels shown to the user for dictionary components. */
public final class ObjectLabels {

    private ObjectLabels() {}

    /** First non-empty form of an entry, or "?". */
    public static String entryLabel(LiftEntry entry) {
        if (entry == null) return "?";
        return entry.getForms().getForms().stream()
            .map(Form::toPlainText)
            .filter(t -> t != null && !t.isBlank())
            .findFirst()
            .orElse("?");
    }

    /** Short technical reference of a parent component (e.g. "entry:id"). */
    public static String describeParent(Object parent) {
        if (parent == null) return "";
        if (parent instanceof MultiText mt) return describeParent(mt.getParent());
        if (parent instanceof LiftEntry e) return "entry:" + e.getId().orElse("?");
        if (parent instanceof LiftSense s) return "sense:" + s.getId().orElse("?");
        if (parent instanceof GrammaticalInfo) return "gram-info";
        return parent.getClass().getSimpleName();
    }

    /** The kind of a parent component, as shown in the "Parent type" columns. */
    public static String describeParentType(Object parent) {
        if (parent == null) return "";
        if (parent instanceof MultiText mt) return describeParentType(mt.getParent());
        if (parent instanceof LiftEntry) return I18n.get("nav.entries");
        if (parent instanceof LiftSense) return I18n.get("nav.senses");
        if (parent instanceof LiftExample) return I18n.get("nav.examples");
        if (parent instanceof LiftNote) return I18n.get("nav.notes");
        if (parent instanceof LiftVariant) return I18n.get("nav.variants");
        if (parent instanceof LiftEtymology) return I18n.get("nav.etymologies");
        if (parent instanceof GrammaticalInfo) return I18n.get("nav.gramInfo");
        if (parent instanceof LiftRelation) return I18n.get("nav.relations");
        if (parent instanceof LiftField) return I18n.get("nav.fields");
        if (parent instanceof LiftTrait) return I18n.get("nav.traits");
        if (parent instanceof LiftAnnotation) return I18n.get("nav.annotations");
        if (parent instanceof LiftPronunciation) return I18n.get("mtkind.pronunciation");
        if (parent instanceof LiftReversal) return I18n.get("mtkind.reversal");
        if (parent instanceof LiftIllustration) return I18n.get("mtkind.illustration");
        if (parent instanceof LiftTranslation) return I18n.get("mtkind.translation");
        if (parent instanceof LiftHeader || parent instanceof FeatureSet || parent instanceof Feature) {
            return I18n.get("nav.headerConfig");
        }
        return parent.getClass().getSimpleName();
    }

    /**
     * What a multitext is for its component: the forms of an entry, the gloss or the
     * definition of a sense, the usage of a relation…
     */
    public static String multiTextKind(MultiText mt) {
        AbstractLiftRoot p = mt.getParent();
        if (p instanceof LiftEntry e) return mt == e.getCitations() ? I18n.get("mtkind.citation") : I18n.get("nav.entries");
        if (p instanceof LiftSense s) return mt == s.getGlosses() ? I18n.get("mtkind.gloss") : I18n.get("mtkind.definition");
        if (p instanceof LiftExample) return I18n.get("mtkind.example");
        if (p instanceof LiftTranslation) return I18n.get("mtkind.translation");
        if (p instanceof LiftVariant) return I18n.get("mtkind.variant");
        if (p instanceof LiftPronunciation) return I18n.get("mtkind.pronunciation");
        if (p instanceof LiftEtymology et) return mt == et.getGlosses() ? I18n.get("mtkind.gloss") : I18n.get("mtkind.etymology");
        if (p instanceof LiftNote) return I18n.get("mtkind.note");
        if (p instanceof LiftRelation) return I18n.get("mtkind.relationUsage");
        if (p instanceof LiftField) return I18n.get("mtkind.field");
        if (p instanceof LiftAnnotation) return I18n.get("mtkind.annotation");
        if (p instanceof LiftReversal r) return r.getParent() instanceof LiftReversal ? I18n.get("mtkind.reversalMain") : I18n.get("mtkind.reversal");
        if (p instanceof LiftIllustration) return I18n.get("mtkind.illustration");
        return describeParentType(p);
    }

    /** The id of the sense or entry a component belongs to, or "?". */
    public static String ownerId(Object o) {
        AbstractIdentifiable owner = DictionaryQueries.owningSenseOrEntry(o);
        return owner == null ? "?" : owner.getId().orElse("?");
    }
}
