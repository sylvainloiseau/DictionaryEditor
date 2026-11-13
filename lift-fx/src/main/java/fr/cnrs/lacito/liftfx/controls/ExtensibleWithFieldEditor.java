/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftfx.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithField;
import fr.cnrs.lacito.liftapi.model.LiftField;
import fr.cnrs.lacito.liftfx.ComponentKind;
import fr.cnrs.lacito.liftfx.EditActions;
import fr.cnrs.lacito.liftfx.internal.ComponentSection;
import fr.cnrs.lacito.liftfx.internal.Messages;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;

/**
 * Programmatic editor for {@link AbstractExtensibleWithField}.
 *
 * Extends {@link ExtensibleWithoutFieldEditor} by also displaying
 * the list of {@link LiftField}s (each via a {@link FieldEditor}).
 */
public class ExtensibleWithFieldEditor extends ExtensibleWithoutFieldEditor {

    private final ComponentSection fieldsSection;
    private final LiftDictionary dictionary;

    public ExtensibleWithFieldEditor(LiftDictionary dictionary) {
        super(dictionary);
        this.dictionary = dictionary;
        fieldsSection = new ComponentSection(ComponentKind.FIELD, Messages.get("editor.fields"), false);
        countIn(fieldsSection);
        getChildren().add(fieldsSection);
    }

    public void setModel(AbstractExtensibleWithField model) {
        setModel(model, null);
    }

    public void setModel(AbstractExtensibleWithField model, EditActions actions) {
        super.setModel(model, actions);
        fieldsSection.setOwner(model, actions);
        if (model == null) return;

        List<Node> cards = new ArrayList<>();
        for (LiftField f : model.getFields().values()) {
            FieldEditor fe = new FieldEditor(dictionary);
            fe.setField(f, actions);
            cards.add(fieldsSection.card(f, f.getName(), fe, () -> model.deleteField(f)));
        }
        fieldsSection.setItems(cards);
    }
}
