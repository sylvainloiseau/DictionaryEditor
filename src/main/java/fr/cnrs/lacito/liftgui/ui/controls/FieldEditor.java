/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftField;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinition;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Editor for a single {@link LiftField}: type (read-only — a field cannot be retyped),
 * text, and the extensible properties.
 */
public final class FieldEditor extends VBox {

    private final TextField typeField = EditorSupport.readOnlyField("");
    private final MultiTextEditor textEditor;
    private final ExtensibleWithoutFieldEditor extensibleEditor;
    private final LiftDictionary dictionary;

    public FieldEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.textEditor = new MultiTextEditor(dictionary);
        this.extensibleEditor = new ExtensibleWithoutFieldEditor(dictionary);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #bcd; -fx-border-radius: 4; -fx-background-color: #f0f4f8; -fx-background-radius: 4;");

        typeField.setMaxWidth(Double.MAX_VALUE);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label(I18n.get("col.type")), 0, 0);
        grid.add(typeField, 1, 0);
        GridPane.setHgrow(typeField, Priority.ALWAYS);

        TitledPane textPane = new TitledPane(I18n.get("editor.text"), textEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        TitledPane extPane = new TitledPane(I18n.get("editor.properties.withoutFields"), extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, textPane, extPane);
    }

    public void setField(LiftField f) {
        if (f == null) {
            typeField.setText("");
            textEditor.setMultiText(null);
            extensibleEditor.setModel(null);
            return;
        }
        String name = f.getName();
        Set<String> documented = EditorSupport
            .fieldDefinitions(dictionary.getHeader(), EditorSupport.targetOf(f.getParent()))
            .stream()
            .map(LiftFieldAndTraitDefinition::getName)
            .collect(Collectors.toSet());
        typeField.setText(name);
        EditorSupport.flagUndocumented(typeField, documented.contains(name), "tooltip.undocumentedType");
        textEditor.setMultiText(f.getText());
        extensibleEditor.setModel(f);
    }
}
