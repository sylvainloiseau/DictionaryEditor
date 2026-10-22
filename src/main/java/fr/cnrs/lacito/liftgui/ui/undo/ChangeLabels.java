package fr.cnrs.lacito.liftgui.ui.undo;

import fr.cnrs.lacito.liftapi.change.ComponentAdded;
import fr.cnrs.lacito.liftapi.change.ComponentRemoved;
import fr.cnrs.lacito.liftapi.change.DefinitionAdded;
import fr.cnrs.lacito.liftapi.change.DefinitionRemoved;
import fr.cnrs.lacito.liftapi.change.FeatureAdded;
import fr.cnrs.lacito.liftapi.change.FeatureRemoved;
import fr.cnrs.lacito.liftapi.change.FeatureSetAdded;
import fr.cnrs.lacito.liftapi.change.FeatureSetRemoved;
import fr.cnrs.lacito.liftapi.change.FormAdded;
import fr.cnrs.lacito.liftapi.change.FormRemoved;
import fr.cnrs.lacito.liftapi.change.LanguageAdded;
import fr.cnrs.lacito.liftapi.change.LanguageRemoved;
import fr.cnrs.lacito.liftapi.change.ModelChange;
import fr.cnrs.lacito.liftapi.change.ModelProperty;
import fr.cnrs.lacito.liftapi.change.ValueChanged;
import fr.cnrs.lacito.liftapi.model.AbstractLiftRoot;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftEtymology;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.undo.HistoryEntry;

/**
 * The labels of the history's entries ("Typing in gloss (fr)", "Delete note"), for the
 * entries made without an action, from lift-api's change events.
 */
public final class ChangeLabels {

    private ChangeLabels() {}

    public static String label(HistoryEntry e) {
        if (e.actionLabel() != null) return e.actionLabel();
        ModelChange c = significant(e);
        if (c == null) return "";
        if (e.kind() == HistoryEntry.Kind.TYPING) {
            String text = textName(c);
            return text == null ? describe(c) : I18n.get("undo.typing", text);
        }
        return describe(c);
    }

    /**
     * The change the entry is about: the first that is not a consequence of another one (the
     * date, the id the dictionary gives a new sense, a language a component brings).
     */
    private static ModelChange significant(HistoryEntry e) {
        ModelChange fallback = null;
        for (ModelChange c : e.changes()) {
            boolean consequence = c instanceof LanguageAdded
                || c instanceof ValueChanged<?> v
                    && (v.property() == ModelProperty.DATE_MODIFIED || v.property() == ModelProperty.LIFT_ID);
            if (!consequence) return c;
            if (fallback == null) fallback = c;
        }
        return fallback;
    }

    static String describe(ModelChange c) {
        String text = textName(c);
        if (text != null) return I18n.get("undo.editText", text);
        return switch (c) {
            case ComponentAdded a -> I18n.get("undo.add", kind(a.component()));
            case ComponentRemoved r -> I18n.get(r.unregistered() ? "undo.delete" : "undo.move", kind(r.component()));
            case ValueChanged<?> v when v.property() == ModelProperty.HREF && v.owner() instanceof FeatureSet s ->
                I18n.get("undo.makeRangeInternal", s.getId());
            case ValueChanged<?> v when v.property() == ModelProperty.FEATURE_SET_DECLARED && v.owner() instanceof FeatureSet s ->
                I18n.get("undo.declareRange", s.getId());
            case ValueChanged<?> v when v.property() == ModelProperty.FEATURE_DECLARED && v.owner() instanceof Feature f ->
                I18n.get("undo.declareValue", f.getId());
            case ValueChanged<?> v -> I18n.get("undo.change", orElse("undo.prop." + v.property().name(), v.property().name()));
            case FeatureAdded f -> I18n.get("undo.add", I18n.get("undo.kind.Feature"));
            case FeatureRemoved f -> I18n.get("undo.delete", I18n.get("undo.kind.Feature"));
            case FeatureSetAdded f -> I18n.get("undo.add", I18n.get("undo.kind.FeatureSet"));
            case FeatureSetRemoved f -> I18n.get("undo.delete", I18n.get("undo.kind.FeatureSet"));
            case DefinitionAdded f -> I18n.get("undo.add", I18n.get("undo.kind.LiftFieldAndTraitDefinition"));
            case DefinitionRemoved f -> I18n.get("undo.delete", I18n.get("undo.kind.LiftFieldAndTraitDefinition"));
            case LanguageAdded l -> I18n.get("undo.add", I18n.get("undo.kind.language") + " " + l.language());
            case LanguageRemoved l -> I18n.get("undo.delete", I18n.get("undo.kind.language") + " " + l.language());
            default -> c.getClass().getSimpleName();
        };
    }

    /** "gloss (fr)" for a change of a text, or null for another change. */
    static String textName(ModelChange c) {
        return switch (c) {
            case FormAdded f -> textName(f.text(), f.form().getLang());
            case FormRemoved f -> textName(f.text(), f.form().getLang());
            case ValueChanged<?> v when v.property() == ModelProperty.FORM_TEXT && v.owner() instanceof Form f ->
                textName(f.getMultiText(), f.getLang());
            default -> null;
        };
    }

    private static String textName(MultiText m, String lang) {
        return role(m) + " (" + lang + ")";
    }

    /** What a multitext is to its component: a gloss, a definition, a form… */
    private static String role(MultiText m) {
        if (m == null) return I18n.get("undo.text.text");
        AbstractLiftRoot p = m.getParent();
        String role = switch (p) {
            case LiftEntry e -> m == e.getCitations() ? "citation" : "lexicalUnit";
            case LiftSense s -> m == s.getDefinition() ? "definition" : "gloss";
            case LiftEtymology y -> m == y.getGlosses() ? "gloss" : "form";
            case null -> "text";
            default -> null;
        };
        return role != null ? I18n.get("undo.text." + role) : kind(p);
    }

    /** The name of a kind of component ("sense", "note"…). */
    static String kind(Object component) {
        String name = component == null ? "" : component.getClass().getSimpleName();
        return orElse("undo.kind." + name, name);
    }

    private static String orElse(String key, String fallback) {
        return I18n.getBundle().containsKey(key) ? I18n.get(key) : fallback;
    }
}
