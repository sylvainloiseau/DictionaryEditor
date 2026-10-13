package fr.cnrs.lacito.liftgui.ui.app;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.io.File;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * The dictionary being edited: the dictionary itself, the file it comes from, whether it is
 * the bundled demo, and whether it has unsaved changes. The window title follows these
 * through {@link #titleBinding()}.
 */
public final class DictionaryDocument {

    /** What a reloaded main scene takes over (see {@link #handOver} and {@link #takeHandoff}). */
    public record Snapshot(LiftDictionary dictionary, File file, boolean demo, boolean dirty) {}

    private static Snapshot handoff;

    private final ObjectProperty<LiftDictionary> dictionary = new SimpleObjectProperty<>();
    private final ObjectProperty<File> file = new SimpleObjectProperty<>();
    private final BooleanProperty demo = new SimpleBooleanProperty();
    private final BooleanProperty dirty = new SimpleBooleanProperty();
    private final StringBinding title = Bindings.createStringBinding(this::computeTitle, dictionary, file, demo, dirty);

    /**
     * Replaces the dictionary.
     *
     * @param file the file it was loaded from; null for a new or demo dictionary
     */
    public void load(LiftDictionary d, File file, boolean demo) {
        // File and flags first: listeners of the dictionary see a consistent document.
        this.file.set(file);
        this.demo.set(demo && d != null);
        this.dirty.set(false);
        this.dictionary.set(d);
    }

    /** Records that the dictionary was saved to {@code f}. */
    public void markSaved(File f) {
        file.set(f);
        demo.set(false);
        dirty.set(false);
    }

    public void markDirty() {
        if (dictionary.get() != null) dirty.set(true);
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

    public ReadOnlyBooleanProperty dirtyProperty() {
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
        handoff = new Snapshot(dictionary.get(), file.get(), demo.get(), dirty.get());
    }

    /** The document handed over by the previous scene, if any (consumed). */
    public static Snapshot takeHandoff() {
        Snapshot s = handoff;
        handoff = null;
        return s;
    }

    /** Restores a handed-over document, keeping its unsaved-changes flag. */
    public void restore(Snapshot s) {
        load(s.dictionary(), s.file(), s.demo());
        dirty.set(s.dirty() && s.dictionary() != null);
    }
}
