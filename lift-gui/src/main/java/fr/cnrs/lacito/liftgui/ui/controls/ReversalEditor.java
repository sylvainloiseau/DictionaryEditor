/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.HasReversal;
import fr.cnrs.lacito.liftapi.model.LiftReversal;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.util.List;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Editor for a {@link LiftReversal}: type (from the header reversal types), forms, and the
 * recursive "main" reversal.
 */
public final class ReversalEditor extends VBox {

    private final ComboBox<Feature> typeCombo = new ComboBox<>();
    private final MultiTextEditor formsEditor;
    private final ComponentSection mainSection;
    private ChangeListener<Feature> typeListener;
    private final LiftDictionary dictionary;

    public ReversalEditor(LiftDictionary dictionary) {
        this.dictionary = dictionary;
        this.formsEditor = new MultiTextEditor(dictionary);
        super(6);
        setPadding(new Insets(4));

        typeCombo.setEditable(false);
        typeCombo.setPromptText(I18n.get("col.type"));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        EditorSupport.showFeatureIds(typeCombo);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label(I18n.get("col.type")), 0, 0);
        grid.add(typeCombo, 1, 0);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);

        TitledPane formsPane = new TitledPane(I18n.get("col.forms"), formsEditor);
        formsPane.setExpanded(true);
        formsPane.setAnimated(false);

        formsEditor.getStyleClass().add(Reveal.FIRST_INPUT);
        mainSection = new ComponentSection(ComponentKind.REVERSAL, I18n.get("editor.reversalMain"), false);
        mainSection.setMaxCount(1);

        getChildren().addAll(grid, formsPane, mainSection);
    }

    public void setReversal(LiftReversal rev) {
        setReversal(rev, null);
    }

    /** With {@code actions}, the main reversal can be added and deleted. */
    public void setReversal(LiftReversal rev, EditActions actions) {
        if (typeListener != null) typeCombo.valueProperty().removeListener(typeListener);
        typeListener = null;
        mainSection.setOwner(rev, actions);
        if (rev == null) {
            typeCombo.getItems().clear();
            typeCombo.setDisable(true);
            formsEditor.setMultiText(null);
            return;
        }
        typeCombo.setItems(FXCollections.observableArrayList(
            EditorSupport.sortedFeatures(dictionary.getHeader().getInverseTypeManager())));
        if (rev.getType() != null && !typeCombo.getItems().contains(rev.getType())) {
            typeCombo.getItems().add(0, rev.getType());
        }
        typeCombo.setValue(rev.getType());
        HasReversal parent = rev.getParent();
        typeCombo.setDisable(parent == null);
        typeListener = (obs, o, n) -> {
            if (n != null && n != rev.getType() && parent != null) parent.retypeReversal(rev, n);
        };
        typeCombo.valueProperty().addListener(typeListener);

        formsEditor.setMultiText(rev.getForms());
        formsEditor.setEditActions(actions);
        LiftReversal main = rev.getMain();
        if (main != null) {
            ReversalEditor mainEditor = new ReversalEditor(dictionary);
            mainEditor.setReversal(main, actions);
            String title = EditorSupport.featureId(main.getType()) + "  " + EditorSupport.firstForm(main.getForms());
            mainSection.setItems(List.of(mainSection.card(main, title.strip(), mainEditor, rev::deleteMain)));
        } else {
            mainSection.setItems(List.of());
        }
    }
}
