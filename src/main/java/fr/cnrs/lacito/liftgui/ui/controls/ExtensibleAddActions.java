package fr.cnrs.lacito.liftgui.ui.controls;

/**
 * Callbacks for adding trait, annotation, field, and note to extensible objects.
 * When null, add and delete buttons are hidden.
 */
public interface ExtensibleAddActions {
    void addTrait(String name, String value);
    void addAnnotation(String name);
    void addField(String type);
    default void addNote(String type) {}
    default void addPronunciation() {}
    default void addRelation(String type) {}
    void refresh();
}
