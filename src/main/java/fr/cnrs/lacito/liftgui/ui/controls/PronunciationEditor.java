/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.geometry.Insets;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

/**
 * Editor for a single {@link LiftPronunciation}: pronunciation text (object languages)
 * and the extensible properties.
 */
public final class PronunciationEditor extends VBox {

    private final MultiTextEditor pronTextEditor;
    private final ExtensibleWithFieldEditor extensibleEditor;

    public PronunciationEditor(LiftDictionary dictionary) {
        super(6);
        this.pronTextEditor = new MultiTextEditor(dictionary);
        this.extensibleEditor = new ExtensibleWithFieldEditor(dictionary);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #ccb; -fx-border-radius: 4; -fx-background-color: #fafaf0; -fx-background-radius: 4;");

        pronTextEditor.useObjectLanguages();

        TitledPane textPane = new TitledPane(I18n.get("editor.pronunciation"), pronTextEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        TitledPane extPane = new TitledPane(I18n.get("editor.properties.withFields"), extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(textPane, extPane);
    }

    public void setPronunciation(LiftPronunciation p) {
        if (p == null) {
            pronTextEditor.setMultiText(null);
            extensibleEditor.setModel(null);
            return;
        }
        pronTextEditor.setMultiText(p.getPronunciation());
        extensibleEditor.setModel(p);
    }
}
