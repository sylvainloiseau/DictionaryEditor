package fr.cnrs.lacito.liftgui.ui.view.header;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
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
     * For a feature set defined in a shared {@code .lift-ranges} file, which is read-only:
     * where it is defined, and a button that makes it editable in this dictionary (it is
     * then saved in the {@code .lift} file, and no longer follows the shared file). The view
     * is shown again once it is done. {@code null} for a set defined in the header.
     */
    static Node externalRangeBanner(EditorContext ctx, FeatureSet set) {
        if (!set.isExternallyDefined()) {
            return null;
        }
        String href = set.getHref().orElse("");
        Label where = new Label(I18n.get(
            set.isExternalDefinitionMissing() ? "cfg.externalRangeMissing" : "cfg.externalRange", href));
        where.setWrapText(true);
        where.getStyleClass().add("muted-label");
        Button makeInternal = new Button(I18n.get("cfg.makeInternal"));
        makeInternal.setOnAction(e -> {
            if (Dialogs.confirm(I18n.get("cfg.makeInternal"), I18n.get("cfg.makeInternalConfirm", set.getId(), href))) {
                ctx.dictionary().getHeader().makeFeatureSetInternal(set);
                ctx.navigator().refresh();
            }
        });
        HBox banner = new HBox(8, where, makeInternal);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(4, 0, 4, 0));
        HBox.setHgrow(where, Priority.ALWAYS);
        return banner;
    }

    /** The first meta language (sorted), used for descriptions edited in tables. */
    static String firstMetaLanguage(LiftDictionary d) {
        return d.getMetaLanguageManager().getLanguages().stream().sorted().findFirst().orElse("en");
    }
}
