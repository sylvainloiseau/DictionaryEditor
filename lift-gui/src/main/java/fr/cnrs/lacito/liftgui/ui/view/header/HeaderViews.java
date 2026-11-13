package fr.cnrs.lacito.liftgui.ui.view.header;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.LiftHeader;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.controls.MultiTextEditor;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Small layout helpers shared by the header configuration views. */
final class HeaderViews {

    private HeaderViews() {}

    /** A bold title for a configuration page. */
    static Label pageTitle(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("page-title");
        return title;
    }

    /** A label and a multitext editor, added to {@code box}. */
    static void addMultiTextRow(VBox box, LiftDictionary d, String label, MultiText mt) {
        Label l = new Label(label);
        l.getStyleClass().add("field-title");
        box.getChildren().addAll(l, multiTextEditor(d, mt));
    }

    static MultiTextEditor multiTextEditor(LiftDictionary d, MultiText mt) {
        MultiTextEditor ed = new MultiTextEditor(d);
        ed.setMultiText(mt);
        return ed;
    }

    /** A label and a multitext editor, read-only if asked, added to {@code box}. */
    static void addMultiTextRow(VBox box, LiftDictionary d, String label, MultiText mt, boolean readOnly) {
        Label l = new Label(label);
        l.getStyleClass().add("field-title");
        box.getChildren().addAll(l, multiTextEditor(d, mt, readOnly));
    }

    static MultiTextEditor multiTextEditor(LiftDictionary d, MultiText mt, boolean readOnly) {
        MultiTextEditor ed = multiTextEditor(d, mt);
        ed.setReadOnly(readOnly);
        return ed;
    }

    /**
     * Whether a feature set is read-only: defined in a shared {@code .lift-ranges} file, or
     * a built-in set the file does not declare.
     */
    static boolean isReadOnly(FeatureSet set) {
        return set.isExternallyDefined() || !set.isDeclared();
    }

    /**
     * For a read-only feature set, why it is read-only and a button that makes it
     * editable; the view is shown again once it is done. {@code null} for a set that can
     * be edited.
     *
     * <ul>
     * <li>defined in a shared {@code .lift-ranges} file: the button makes it editable in
     * this dictionary (it is then saved in the {@code .lift} file, and no longer follows the
     * shared file), after a confirmation;</li>
     * <li>a built-in set the file does not declare: the button declares it (it is then
     * saved, with the values declared in it).</li>
     * </ul>
     */
    static Node readOnlyRangeBanner(EditorContext ctx, FeatureSet set) {
        LiftHeader header = ctx.dictionary().getHeader();
        Label why;
        Button makeEditable;
        if (set.isExternallyDefined()) {
            String href = set.getHref().orElse("");
            why = new Label(I18n.get(
                set.isExternalDefinitionMissing() ? "cfg.externalRangeMissing" : "cfg.externalRange", href));
            makeEditable = new Button(I18n.get("cfg.makeInternal"));
            makeEditable.setOnAction(e -> {
                if (Dialogs.confirm(I18n.get("cfg.makeInternal"), I18n.get("cfg.makeInternalConfirm", set.getId(), href))) {
                    ctx.perform(I18n.get("undo.makeRangeInternal", set.getId()), set,
                        () -> header.makeFeatureSetInternal(set));
                    ctx.navigator().refresh();
                }
            });
        } else if (!set.isDeclared()) {
            why = new Label(I18n.get("cfg.undeclaredRange"));
            makeEditable = new Button(I18n.get("cfg.declareRange"));
            makeEditable.setOnAction(e -> {
                ctx.perform(I18n.get("undo.declareRange", set.getId()), set, () -> header.declareFeatureSet(set));
                ctx.navigator().refresh();
            });
        } else {
            return null;
        }
        why.setWrapText(true);
        why.getStyleClass().add("muted-label");
        HBox banner = new HBox(8, why, makeEditable);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(4, 0, 4, 0));
        HBox.setHgrow(why, Priority.ALWAYS);
        return banner;
    }

    /**
     * Declares a value its range does not declare, so that it can be edited and is saved.
     * In a set defined in a shared file, the set is made editable in this dictionary first,
     * after a confirmation; in a set the file does not declare, the set is declared with
     * it. The view is shown again once it is done.
     */
    static void declareValue(EditorContext ctx, Feature value) {
        LiftHeader header = ctx.dictionary().getHeader();
        FeatureSet set = value.getParentFeatureSet();
        boolean external = set.isExternallyDefined();
        if (external && !Dialogs.confirm(I18n.get("cfg.declareValue"),
            I18n.get("cfg.declareValueExternalConfirm", value.getId(), set.getId(), set.getHref().orElse("")))) {
            return;
        }
        ctx.perform(I18n.get("undo.declareValue", value.getId()), value, () -> {
            if (external) header.makeFeatureSetInternal(set);
            header.declareFeature(value);
        });
        ctx.navigator().refresh();
    }

    /** The style of what its range or the file does not declare: grey. */
    static final String UNDECLARED_STYLE = "undeclared";

    /**
     * Deletes a feature of a set, as one undo step. lift-api refuses a feature in use; the
     * user is told how many uses it has, parent links included (they do not show in the
     * entries).
     *
     * @return whether the feature was deleted
     */
    static boolean deleteFeature(EditorContext ctx, FeatureSet set, Feature f) {
        try {
            ctx.perform(I18n.get("undo.delete", I18n.get("undo.kind.Feature") + " " + f.getId()), set,
                () -> set.removeFeature(f.getId()));
            return true;
        } catch (IllegalStateException e) {
            long uses = ctx.dictionary().getHeader().countUses(f);
            Dialogs.error(I18n.get("btn.delete"), uses > 0
                ? I18n.get("cfg.featureInUse", uses)
                : e.getMessage());
            return false;
        }
    }

    /** The first meta language (sorted), used for descriptions edited in tables. */
    static String firstMetaLanguage(LiftDictionary d) {
        return d.getMetaLanguageManager().getLanguages().stream().sorted().findFirst().orElse("en");
    }
}
