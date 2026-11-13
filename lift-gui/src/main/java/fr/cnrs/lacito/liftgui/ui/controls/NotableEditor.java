/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.AbstractNotable;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;

/**
 * Editor for {@link AbstractNotable} objects.
 *
 * Extends {@link ExtensibleWithFieldEditor} by adding a "Notes" section.
 * Used by LiftEntry, LiftSense, and LiftExample which all inherit from AbstractNotable.
 */
public class NotableEditor extends ExtensibleWithFieldEditor {

    private final ComponentSection notesSection;
    private final LiftDictionary dictionary;

    public NotableEditor(LiftDictionary dictionary) {
        super(dictionary);
        this.dictionary = dictionary;
        notesSection = new ComponentSection(ComponentKind.NOTE, I18n.get("editor.notes"), false);
        countIn(notesSection);
        getChildren().add(notesSection);
    }

    public void setModel(AbstractNotable model) {
        setModel(model, null);
    }

    public void setModel(AbstractNotable model, EditActions actions) {
        super.setModel(model, actions);
        notesSection.setOwner(model, actions);
        if (model == null) return;

        List<Node> cards = new ArrayList<>();
        for (LiftNote note : model.getNotes().values()) {
            NoteEditor ne = new NoteEditor(dictionary);
            ne.setNote(note, actions);
            cards.add(notesSection.card(note, EditorSupport.featureId(note.getType()), ne, () -> model.deleteNote(note)));
        }
        notesSection.setItems(cards);
    }
}
