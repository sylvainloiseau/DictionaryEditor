package fr.cnrs.lacito.liftgui.ui.table.catalogs;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithField;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithoutField;
import fr.cnrs.lacito.liftapi.model.AbstractNotable;
import fr.cnrs.lacito.liftapi.model.LiftField;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.ui.I18n;

/**
 * Editing the text of a field or a note in a table cell, where the field or note may not
 * exist yet:
 * <ul>
 *   <li>typing in an empty cell creates the field (or note) with that text;</li>
 *   <li>clearing a text removes its form; when it was the last form and the field (or note)
 *       holds nothing else (no trait, no annotation, and for a note no field), the field is
 *       removed too, so that no data is lost silently;</li>
 *   <li>each of these is one undo step ({@link Gesture}).</li>
 * </ul>
 */
public final class ExtensionCells {

    /** Runs a change as one undo step (EditorContext::perform). */
    @FunctionalInterface
    public interface Gesture {
        void perform(String label, Object subject, Runnable change);

        /** Runs the change directly (no history). */
        Gesture DIRECT = (label, subject, change) -> change.run();
    }

    private ExtensionCells() {}

    /** Stores the text typed in the cell of field {@code name}, language {@code lang}, of {@code row}. */
    public static void commitField(LiftDictionary d, Gesture gesture, AbstractExtensibleWithField row, String name,
                                   String lang, String text) {
        String t = text == null ? "" : text;
        LiftField field = field(row, name);
        if (field == null) {
            if (t.isEmpty()) return;
            gesture.perform(I18n.get("undo.addField", name), row,
                () -> d.getComponentBuilder().field(row, name).addText(lang, t).build());
            return;
        }
        if (!t.isEmpty()) {
            field.getText().formTextProperty(lang).set(t);
            return;
        }
        gesture.perform(I18n.get("undo.clearField", name), row, () -> {
            field.getText().formTextProperty(lang).set("");
            if (isEmpty(field.getText()) && holdsNothingElse(field)) row.deleteField(field);
        });
    }

    /** Stores the text typed in the cell of the note of type {@code type}, language {@code lang}, of {@code row}. */
    public static void commitNote(LiftDictionary d, Gesture gesture, AbstractNotable row, String type, String lang,
                                  String text) {
        String t = text == null ? "" : text;
        LiftNote note = note(row, type);
        if (note == null) {
            if (t.isEmpty()) return;
            gesture.perform(I18n.get("undo.addNote", type), row, () -> {
                var b = d.getComponentBuilder().note(row, type).addText(lang, t);
                b.build();
            });
            return;
        }
        if (!t.isEmpty()) {
            note.getText().formTextProperty(lang).set(t);
            return;
        }
        gesture.perform(I18n.get("undo.clearNote", type), row, () -> {
            note.getText().formTextProperty(lang).set("");
            if (isEmpty(note.getText()) && holdsNothingElse(note) && note.getFields().isEmpty()) row.deleteNote(note);
        });
    }

    /** The field of that name, or null (lift-api's getField throws when there is none). */
    public static LiftField field(AbstractExtensibleWithField row, String name) {
        return row.getFields().get(name);
    }

    /** The note of that type ("" for none), or null. */
    public static LiftNote note(AbstractNotable row, String type) {
        return row.getNotes().values().stream()
            .filter(n -> type.equals(fr.cnrs.lacito.liftgui.core.DictionaryQueries.featureId(n.getType())))
            .findFirst().orElse(null);
    }

    private static boolean isEmpty(MultiText mt) {
        return mt.getForms().stream().allMatch(f -> f.toPlainText().isEmpty());
    }

    private static boolean holdsNothingElse(AbstractExtensibleWithoutField x) {
        return x.getTraits().isEmpty() && x.getAnnotations().isEmpty();
    }
}
