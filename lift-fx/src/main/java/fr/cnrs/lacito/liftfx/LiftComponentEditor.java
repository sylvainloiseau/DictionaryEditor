package fr.cnrs.lacito.liftfx;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftEtymology;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftField;
import fr.cnrs.lacito.liftapi.model.LiftIllustration;
import fr.cnrs.lacito.liftapi.model.LiftMedia;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftapi.model.LiftRelation;
import fr.cnrs.lacito.liftapi.model.LiftReversal;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftapi.model.LiftTranslation;
import fr.cnrs.lacito.liftapi.model.LiftVariant;
import fr.cnrs.lacito.liftfx.controls.AnnotationEditor;
import fr.cnrs.lacito.liftfx.controls.EtymologyEditor;
import fr.cnrs.lacito.liftfx.controls.ExampleEditor;
import fr.cnrs.lacito.liftfx.controls.ExternalDocumentEditor;
import fr.cnrs.lacito.liftfx.controls.FieldEditor;
import fr.cnrs.lacito.liftfx.controls.LiftEntryEditor;
import fr.cnrs.lacito.liftfx.controls.NoteEditor;
import fr.cnrs.lacito.liftfx.controls.PronunciationEditor;
import fr.cnrs.lacito.liftfx.controls.RelationEditor;
import fr.cnrs.lacito.liftfx.controls.ReversalEditor;
import fr.cnrs.lacito.liftfx.controls.SenseEditor;
import fr.cnrs.lacito.liftfx.controls.TraitEditor;
import fr.cnrs.lacito.liftfx.controls.TranslationEditor;
import fr.cnrs.lacito.liftfx.controls.VariantEditor;
import fr.cnrs.lacito.liftfx.internal.EditorSupport;
import java.io.File;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.layout.VBox;

/**
 * Shows a component of a LIFT dictionary with its editor: an entry, a sense, an example, a
 * note… (see {@link #supports}). The dictionary gives the languages offered (metalanguages,
 * object languages), the types and ranges, and creates the components added.
 *
 * <pre>{@code
 * LiftComponentEditor editor = new LiftComponentEditor(dictionary);
 * editor.setComponent(entry);
 * pane.getChildren().add(editor);
 * }</pre>
 *
 * The editor changes the component as the user types; the [+] of its sections add
 * components, and the delete buttons delete them. Each change goes through lift-api, which
 * reports it to the dictionary's listeners (an undo history, for instance).
 *
 * <h2>Threads</h2>
 *
 * Like every JavaFX node, the editor is used on the JavaFX Application Thread only. Since it
 * observes the dictionary, the dictionary must also be changed on that thread only while the
 * editor shows one of its components.
 *
 * <h2>Styles</h2>
 *
 * The editor adds {@link LiftFx#stylesheet()} to its own stylesheets. Its colours derive
 * from the theme's {@code -fx-base}, so that it follows a light or dark theme.
 */
public class LiftComponentEditor extends VBox {

    private final LiftDictionary dictionary;
    private Object component;
    private Supplier<File> dictionaryFile = () -> null;
    private boolean addDeleteEnabled = true;
    private Consumer<LiftSense> onOpenSense;
    private Runnable onChanged;
    private Runnable onStructureChanged;

    /**
     * @param dictionary the dictionary of the components to be shown
     */
    public LiftComponentEditor(LiftDictionary dictionary) {
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
        getStyleClass().add("lift-component-editor");
        getStylesheets().add(LiftFx.stylesheet());
    }

    /** Whether {@link #setComponent} accepts this object: a component that has an editor. */
    public static boolean supports(Object component) {
        return switch (component) {
            case LiftEntry e -> true;
            case LiftSense s -> true;
            case LiftExample e -> true;
            case LiftTranslation t -> true;
            case LiftPronunciation p -> true;
            case LiftMedia m -> true;
            case LiftVariant v -> true;
            case LiftRelation r -> true;
            case LiftEtymology e -> true;
            case LiftReversal r -> true;
            case LiftIllustration i -> true;
            case LiftNote n -> true;
            case LiftField f -> true;
            case LiftTrait t -> true;
            case LiftAnnotation a -> true;
            case null, default -> false;
        };
    }

    public LiftDictionary getDictionary() {
        return dictionary;
    }

    /** The component shown, or null. */
    public Object getComponent() {
        return component;
    }

    /**
     * Shows a component of the dictionary; null empties the editor.
     *
     * @throws IllegalArgumentException when the component has no editor (see {@link #supports})
     */
    public void setComponent(Object component) {
        if (component != null && !supports(component)) {
            throw new IllegalArgumentException("No editor for " + component.getClass().getName());
        }
        this.component = component;
        refresh();
    }

    /** Builds the editor of the component again, e.g. after a change made elsewhere. */
    public void refresh() {
        if (component == null) getChildren().clear();
        else getChildren().setAll(editorOf(component));
    }

    /**
     * The file of the dictionary, if it has one: the paths of the illustrations and media
     * chosen under its folder are stored relative to it. Takes effect at the next
     * {@link #setComponent} or {@link #refresh}.
     *
     * @param dictionaryFile gives the file, or null when the dictionary has none (yet)
     */
    public void setDictionaryFile(Supplier<File> dictionaryFile) {
        this.dictionaryFile = dictionaryFile == null ? () -> null : dictionaryFile;
    }

    /**
     * Whether the sections offer to add components ([+]) and to delete them; true by default.
     * The texts stay editable either way. Takes effect at the next {@link #setComponent} or
     * {@link #refresh}.
     */
    public void setAddDeleteEnabled(boolean enabled) {
        this.addDeleteEnabled = enabled;
    }

    public boolean isAddDeleteEnabled() {
        return addDeleteEnabled;
    }

    /**
     * When set, each sense of an entry shows a link calling {@code action} with the sense
     * (e.g. to open it elsewhere in the application). No link by default. Takes effect at the
     * next {@link #setComponent} or {@link #refresh}.
     */
    public void setOnOpenSense(Consumer<LiftSense> action) {
        this.onOpenSense = action;
    }

    /**
     * Called after each change the editor makes other than typing: a component added or
     * deleted, a grammatical info chosen.
     */
    public void setOnChanged(Runnable action) {
        this.onChanged = action;
    }

    /**
     * What to do after a component was added or deleted. By default the editor is built
     * again ({@link #refresh}); an application that shows more than the editor (a header
     * counting the senses, for instance) can build its whole view again instead. Null
     * restores the default.
     */
    public void setOnStructureChanged(Runnable action) {
        this.onStructureChanged = action;
    }

    private void structureChanged() {
        if (onStructureChanged != null) onStructureChanged.run();
        else refresh();
        changed();
    }

    private void changed() {
        if (onChanged != null) onChanged.run();
    }

    /** What the editors need to add and delete components; null when it is not allowed. */
    private EditActions actions() {
        return addDeleteEnabled ? EditActions.of(dictionary, dictionaryFile, this::structureChanged) : null;
    }

    private Node editorOf(Object component) {
        EditActions actions = actions();
        return switch (component) {
            case LiftEntry entry -> {
                LiftEntryEditor e = new LiftEntryEditor(dictionary);
                e.setOnOpenSense(onOpenSense);
                e.setOnChanged(this::changed);
                e.setEntry(entry, actions);
                yield e;
            }
            case LiftSense sense -> {
                SenseEditor e = new SenseEditor(dictionary);
                e.setGrammaticalInfoValues(EditorSupport.sortedFeatureIds(dictionary.getHeader().getGrammaticalInfoManager()));
                e.setOnGramInfoChanged(this::changed);
                e.setSense(sense, actions);
                yield e;
            }
            case LiftExample example -> {
                ExampleEditor e = new ExampleEditor(dictionary);
                e.setExample(example, actions);
                yield e;
            }
            case LiftTranslation translation -> {
                TranslationEditor e = new TranslationEditor(dictionary);
                e.setTranslation(translation, actions);
                yield e;
            }
            case LiftPronunciation pronunciation -> {
                PronunciationEditor e = new PronunciationEditor(dictionary);
                e.setPronunciation(pronunciation, actions);
                yield e;
            }
            case LiftMedia media -> {
                ExternalDocumentEditor e = new ExternalDocumentEditor(dictionary, dictionaryFile);
                e.setDocument(media, actions);
                yield e;
            }
            case LiftIllustration illustration -> {
                ExternalDocumentEditor e = new ExternalDocumentEditor(dictionary, dictionaryFile);
                e.setDocument(illustration, actions);
                yield e;
            }
            case LiftVariant variant -> {
                VariantEditor e = new VariantEditor(dictionary);
                e.setVariant(variant, actions);
                yield e;
            }
            case LiftRelation relation -> {
                RelationEditor e = new RelationEditor(dictionary);
                e.setRelation(relation, actions);
                yield e;
            }
            case LiftEtymology etymology -> {
                EtymologyEditor e = new EtymologyEditor(dictionary);
                e.setEtymology(etymology, actions);
                yield e;
            }
            case LiftReversal reversal -> {
                ReversalEditor e = new ReversalEditor(dictionary);
                e.setReversal(reversal, actions);
                yield e;
            }
            case LiftNote note -> {
                NoteEditor e = new NoteEditor(dictionary);
                e.setNote(note, actions);
                yield e;
            }
            case LiftField field -> {
                FieldEditor e = new FieldEditor(dictionary);
                e.setField(field, actions);
                yield e;
            }
            case LiftTrait trait -> {
                TraitEditor e = new TraitEditor(dictionary);
                e.setTrait(trait, EditorSupport.targetOf(trait.getParent()), actions);
                yield e;
            }
            case LiftAnnotation annotation -> {
                AnnotationEditor e = new AnnotationEditor(dictionary);
                e.setAnnotation(annotation);
                yield e;
            }
            default -> throw new IllegalArgumentException("No editor for " + component.getClass().getName());
        };
    }
}
