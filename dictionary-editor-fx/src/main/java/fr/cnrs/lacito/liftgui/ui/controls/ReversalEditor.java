/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftHeaderRangeElement;
import fr.cnrs.lacito.liftapi.model.LiftReversal;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * Editor for a single {@link LiftReversal}.
 *
 * Displays: type, forms MultiText, and recursive main (if present).
 */
public final class ReversalEditor extends VBox {

    private final ComboBox<LiftHeaderRangeElement> typeCombo = new ComboBox<>();
    //private final TextField typeField = new TextField();
    private final MultiTextEditor formsEditor = new MultiTextEditor();
    private final VBox mainBox = new VBox(6);
    private LiftDictionary dictionary;

    public ReversalEditor(LiftDictionary dictionary) {
        this.dictionary = dictionary;
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #b9c; -fx-border-radius: 4; -fx-background-color: #f8f4fa; -fx-background-radius: 4;");

        typeCombo.setEditable(false);
        typeCombo.setPromptText("type");
        typeCombo.setItems(FXCollections.observableArrayList(dictionary.getHeader().getEtymologyTypeManager().typesProperty().get()));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Type"), 0, 0);
        grid.add(typeCombo, 1, 0);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);

        TitledPane formsPane = new TitledPane("Formes (MultiText)", formsEditor);
        formsPane.setExpanded(true);
        formsPane.setAnimated(false);

        TitledPane mainPane = new TitledPane("Main (récursif)", mainBox);
        mainPane.setExpanded(false);
        mainPane.setAnimated(false);

        getChildren().addAll(grid, formsPane, mainPane);
    }

    public void setReversal(LiftReversal rev, Collection<String> langs) {
        mainBox.getChildren().clear();

        if (rev == null) {
            typeCombo.setValue(null);
            typeCombo.setDisable(true);
            formsEditor.setMultiText(null);
            return;
        }
        typeCombo.setValue(rev.getType());
        typeCombo.getSelectionModel().select(rev.getType());
        typeCombo.setDisable(false);
        formsEditor.setAvailableLanguages(langs);
        formsEditor.setMultiText(rev.getForms());

        if (rev.getMain() != null) {
            ReversalEditor mainEditor = new ReversalEditor(dictionary);
            mainEditor.setReversal(rev.getMain(), langs);
            mainBox.getChildren().add(mainEditor);
        }
    }
}
