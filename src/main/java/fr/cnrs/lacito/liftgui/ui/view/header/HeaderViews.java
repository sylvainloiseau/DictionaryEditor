package fr.cnrs.lacito.liftgui.ui.view.header;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.ui.controls.MultiTextEditor;
import javafx.scene.control.Label;
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

    /** The first meta language (sorted), used for descriptions edited in tables. */
    static String firstMetaLanguage(LiftDictionary d) {
        return d.getMetaLanguageManager().getLanguages().stream().sorted().findFirst().orElse("en");
    }
}
