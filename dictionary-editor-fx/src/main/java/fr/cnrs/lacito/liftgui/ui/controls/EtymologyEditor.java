/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftEtymology;
import fr.cnrs.lacito.liftapi.model.LiftHeaderRangeElement;

import java.util.Collection;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Editor for a single {@link LiftEtymology}.
 *
 * Displays: type, source, forms MultiText, glosses MultiText + ExtensibleWithFieldEditor.
 */
public final class EtymologyEditor extends VBox {

    private final ComboBox<LiftHeaderRangeElement> typeCombo = new ComboBox<>();
    private final TextField sourceField = new TextField();
    private final MultiTextEditor formsEditor = new MultiTextEditor();
    private final MultiTextEditor glossesEditor = new MultiTextEditor();
    private final ExtensibleWithFieldEditor extensibleEditor =
        new ExtensibleWithFieldEditor();

    public EtymologyEditor(LiftDictionary dictionary) {
        super(6);
        setPadding(new Insets(4));
        setStyle(
            "-fx-border-color: #abc; -fx-border-radius: 4; -fx-background-color: #f4f8fa; -fx-background-radius: 4;"
        );

        typeCombo.setEditable(false);
        typeCombo.setPromptText("type");
        typeCombo.setItems(FXCollections.observableArrayList(dictionary.getHeader().getEtymologyTypeManager().typesProperty().get()));
        sourceField.setEditable(false);
        sourceField.setPromptText("source");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Type"), 0, 0);
        grid.add(typeCombo, 1, 0);
        grid.add(new Label("Source"), 0, 1);
        grid.add(sourceField, 1, 1);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);
        GridPane.setHgrow(sourceField, Priority.ALWAYS);

        TitledPane formsPane = new TitledPane(
            "Formes (MultiText)",
            formsEditor
        );
        formsPane.setExpanded(true);
        formsPane.setAnimated(false);

        TitledPane glossesPane = new TitledPane(
            "Glosess (MultiText)",
            glossesEditor
        );
        glossesPane.setExpanded(false);
        glossesPane.setAnimated(false);

        TitledPane extPane = new TitledPane(
            "Propriétés (dates, traits, annotations, champs)",
            extensibleEditor
        );
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, formsPane, glossesPane, extPane);
    }

    /**
     * @param ety        the etymology
     * @param objLangs   object-languages for etymology forms
     * @param metaLangs  meta-languages for glosses and inherited properties
     */
    public void setEtymology(
        LiftEtymology ety,
        Collection<String> objLangs,
        Collection<String> metaLangs
    ) {
        if (ety == null) {
            typeCombo.setDisable(true);
            sourceField.setText("");
            formsEditor.setMultiText(null);
            glossesEditor.setMultiText(null);
            extensibleEditor.setModel(null, metaLangs);
            return;
        }
        typeCombo.setDisable(false);
        typeCombo.getSelectionModel().select(ety.getType());
        sourceField.setText(ety.getSource() != null ? ety.getSource() : "");
        formsEditor.setAvailableLanguages(objLangs);
        formsEditor.setMultiText(ety.getForms());
        glossesEditor.setAvailableLanguages(metaLangs);
        glossesEditor.setMultiText(ety.getGloss());
        extensibleEditor.setModel(ety, metaLangs);
    }
}
