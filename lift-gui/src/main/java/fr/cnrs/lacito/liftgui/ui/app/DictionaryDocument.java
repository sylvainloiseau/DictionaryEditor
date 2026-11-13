package fr.cnrs.lacito.liftgui.ui.app;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.undo.History;
import java.io.File;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;

/**
 * The dictionary being edited: the dictionary itself, the file it comes from, whether it is
 * the bundled demo, and its undo {@link History}. The window title follows these through
 * {@link #titleBinding()}.
 *
 * The document has unsaved changes exactly when its history is not at the save point:
 * undoing back to the last save removes the {@code *}.
 */
public final class DictionaryDocument {

    /** What a reloaded main scene takes over (see {@link #handOver} and {@link #takeHandoff}). */
    public record Snapshot(LiftDictionary dictionary, File file, boolean demo, History history) {}

    private static Snapshot handoff;

    private final ObjectProperty<LiftDictionary> dictionary = new SimpleObjectProperty<>();
    private final ObjectProperty<File> file = new SimpleObjectProperty<>();
    private final BooleanProperty demo = new SimpleBooleanProperty();
    private final History history;
    private final BooleanBinding dirty;
    private final StringBinding title;

    public DictionaryDocument() {
        this(new History());
    }

    public DictionaryDocument(History history) {
        this.history = history;
        this.dirty = Bindings.createBooleanBinding(
            () -> dictionary.get() != null && !history.isAtSavePoint(),
            dictionary, history.atSavePointProperty());
        this.title = Bindings.createStringBinding(this::computeTitle, dictionary, file, demo, dirty);
    }

    /**
     * Replaces the dictionary, with a fresh history.
     *
     * @param file the file it was loaded from; null for a new or demo dictionary
     */
    public void load(LiftDictionary d, File file, boolean demo) {
        // File, flags and history first: listeners of the dictionary see a consistent document.
        this.file.set(file);
        this.demo.set(demo && d != null);
        history.attach(d);
        this.dictionary.set(d);
    }

    /** Records that the dictionary was saved to {@code f}. */
    public void markSaved(File f) {
        file.set(f);
        demo.set(false);
        history.markSaved();
    }

    public History history() {
        return history;
    }

    public LiftDictionary dictionary() {
        return dictionary.get();
    }

    public ReadOnlyObjectProperty<LiftDictionary> dictionaryProperty() {
        return dictionary;
    }

    public File file() {
        return file.get();
    }

    public boolean isDemo() {
        return demo.get();
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public ObservableBooleanValue dirtyProperty() {
        return dirty;
    }

    /** "Dictionary Editor — name *": the window title. */
    public StringBinding titleBinding() {
        return title;
    }

    private String computeTitle() {
        String name;
        if (dictionary.get() == null) name = null;
        else if (demo.get()) name = I18n.get("title.demo");
        else if (file.get() == null) name = I18n.get("title.untitled");
        else name = file.get().getName();
        return I18n.get("app.title") + (name == null ? "" : " — " + name) + (dirty.get() ? " *" : "");
    }

    /* ─── Hand-over to a reloaded scene (change of interface language) ─── */

    public void handOver() {
        handoff = new Snapshot(dictionary.get(), file.get(), demo.get(), history);
    }

    /** The document handed over by the previous scene, if any (consumed). */
    public static Snapshot takeHandoff() {
        Snapshot s = handoff;
        handoff = null;
        return s;
    }

    /** Restores a handed-over document with its history: undo and the {@code *} carry on. */
    public void restore(Snapshot s) {
        this.file.set(s.file());
        this.demo.set(s.demo() && s.dictionary() != null);
        history.takeOver(s.history());
        this.dictionary.set(s.dictionary());
    }
}
