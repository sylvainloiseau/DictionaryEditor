
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
import javafx.geometry.Insets;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

/**
 * Editor for a single {@link LiftPronunciation}.
 *
 * Displays: pronunciation MultiText + ExtensibleWithFieldEditor
 * (since LiftPronunciation extends AbstractExtensibleWithField).
 */
public final class PronunciationEditor extends VBox {

    private final MultiTextEditor pronTextEditor;
    private final ExtensibleWithFieldEditor extensibleEditor;
    private final LiftDictionary dictionary;

    public PronunciationEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.pronTextEditor = new MultiTextEditor(dictionary);
        this.extensibleEditor = new ExtensibleWithFieldEditor(dictionary);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #ccb; -fx-border-radius: 4; -fx-background-color: #fafaf0; -fx-background-radius: 4;");

        TitledPane textPane = new TitledPane("Prononciation (MultiText)", pronTextEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés (dates, traits, annotations, champs)", extensibleEditor);
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
        //pronTextEditor.setAvailableLanguages(langs);
        pronTextEditor.setMultiText(p.getPronunciation());
        extensibleEditor.setModel(p);
    }
}
