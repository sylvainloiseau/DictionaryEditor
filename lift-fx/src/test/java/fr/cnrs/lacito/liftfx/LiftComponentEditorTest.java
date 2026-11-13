package fr.cnrs.lacito.liftfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftapi.model.LiftSense;
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
import fr.cnrs.lacito.liftfx.internal.ComponentSection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The editor in a window of its own, as another application would show it: no stylesheet
 * nor anything else from DictionaryEditor.
 */
class LiftComponentEditorTest extends ApplicationTest {

    private LiftDictionary dictionary;
    private LiftEntry entry;
    private LiftSense sense;
    private LiftExample example;
    private LiftComponentEditor editor;

    @Override
    public void init() {
        LiftFx.setLocale(Locale.ENGLISH);
        LiftFx.hideEmptySectionsProperty().set(false);
    }

    @Override
    public void start(Stage stage) {
        dictionary = LiftDictionary.makeBuilder()
            .withObjectLanguages("xx")
            .withMetaLanguages("en", "fr")
            .withNoteType("comment")
            .build();
        entry = dictionary.getComponentBuilder().entry().withForm("xx", "kato").build();
        sense = dictionary.getComponentBuilder().sense(entry).withGloss("en", "cat").build();
        example = dictionary.getComponentBuilder().example(sense).withExample("xx", "kato miaŭas").build();
        editor = new LiftComponentEditor(dictionary);
        stage.setScene(new Scene(new ScrollPane(editor), 800, 900));
        stage.show();
    }

    @AfterEach
    void closeWindow() throws TimeoutException {
        FxToolkit.hideStage();
        FxToolkit.cleanupStages();
    }

    private void show(Object component) {
        interact(() -> editor.setComponent(component));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Node shownEditor() {
        assertEquals(1, editor.getChildren().size(), "the editor of the component");
        return editor.getChildren().getFirst();
    }

    private ComponentSection section(Object owner, ComponentKind kind) {
        return lookup(".component-section")
            .match((Node n) -> n instanceof ComponentSection s && s.owner() == owner && s.kind() == kind)
            .queryAs(ComponentSection.class);
    }

    private void add(Object owner, ComponentKind kind) {
        interact(() -> section(owner, kind).addButton().fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    /* ─── Which components ─── */

    @Test
    void supportsTheComponentsAndNothingElse() {
        assertTrue(LiftComponentEditor.supports(entry));
        assertTrue(LiftComponentEditor.supports(sense));
        assertTrue(LiftComponentEditor.supports(example));
        assertFalse(LiftComponentEditor.supports(null));
        assertFalse(LiftComponentEditor.supports("kato"));
        assertFalse(LiftComponentEditor.supports(entry.getForms()), "a multitext is not a component");
    }

    @Test
    void refusesWhatItCannotShow() {
        assertThrows(IllegalArgumentException.class, () -> editor.setComponent("kato"));
    }

    @Test
    void eachComponentGetsItsEditor() {
        var b = dictionary.getComponentBuilder();
        LiftPronunciation pronunciation = interactAndGet(() -> b.pronunciation(entry).build());
        Map<Object, Class<?>> expected = interactAndGet(() -> Map.ofEntries(
            Map.entry(entry, LiftEntryEditor.class),
            Map.entry(sense, SenseEditor.class),
            Map.entry(example, ExampleEditor.class),
            Map.entry(b.translation(example, "free").build(), TranslationEditor.class),
            Map.entry(pronunciation, PronunciationEditor.class),
            Map.entry(b.media(pronunciation, "sound/kato.wav").build(), ExternalDocumentEditor.class),
            Map.entry(b.illustration(sense, "pictures/kato.png").build(), ExternalDocumentEditor.class),
            Map.entry(b.variant(entry).build(), VariantEditor.class),
            Map.entry(b.relation("synonym", sense).build(), RelationEditor.class),
            Map.entry(b.etymology(entry, "borrowed", "").build(), EtymologyEditor.class),
            Map.entry(b.reversal(sense).withType("en").build(), ReversalEditor.class),
            Map.entry(b.note(sense, "comment").build(), NoteEditor.class),
            Map.entry(b.field(entry, "literal-meaning").build(), FieldEditor.class),
            Map.entry(b.trait(entry, "status", "draft").build(), TraitEditor.class),
            Map.entry(b.annotation(sense, "checked").build(), AnnotationEditor.class)));
        expected.forEach((component, editorClass) -> {
            assertTrue(LiftComponentEditor.supports(component), component.toString());
            show(component);
            assertInstanceOf(editorClass, shownEditor(), component.getClass().getSimpleName());
            assertSame(component, editor.getComponent());
        });
    }

    @Test
    void nullEmptiesTheEditor() {
        show(entry);
        show(null);
        assertTrue(editor.getChildren().isEmpty());
    }

    /* ─── Languages and editing ─── */

    @Test
    void theEntryFormsAreInTheObjectLanguagesAndTypingChangesTheModel() {
        show(entry);
        TextField form = lookup(".text-field").match((TextField t) -> "kato".equals(t.getText())).queryAs(TextField.class);
        interact(() -> form.setText("katoj"));
        assertEquals("katoj", entry.getForms().getForm("xx").orElseThrow().toPlainText());
    }

    @Test
    void theGlossesOfferTheMetalanguages() {
        show(sense);
        List<String> glosses = lookup(".text-field").queryAllAs(TextField.class).stream()
            .map(TextField::getText).filter(t -> t != null && !t.isEmpty()).toList();
        assertTrue(glosses.contains("cat"), glosses.toString());
    }

    /* ─── Adding, deleting ─── */

    @Test
    void addingASenseShowsTheEntryAgainAndTellsTheApplication() {
        AtomicInteger changes = new AtomicInteger();
        editor.setOnChanged(changes::incrementAndGet);
        show(entry);
        Node before = shownEditor();
        add(entry, ComponentKind.SENSE);
        assertEquals(2, entry.getSenses().size());
        assertEquals(1, changes.get());
        assertNotSame(before, shownEditor(), "the editor was built again");
        assertEquals(2, section(entry, ComponentKind.SENSE).getCount());
    }

    @Test
    void theApplicationCanShowTheChangeItself() {
        AtomicInteger structureChanges = new AtomicInteger();
        editor.setOnStructureChanged(structureChanges::incrementAndGet);
        show(entry);
        Node before = shownEditor();
        add(entry, ComponentKind.SENSE);
        assertEquals(1, structureChanges.get());
        assertSame(before, shownEditor(), "the editor is left as it is");
    }

    @Test
    void withoutAddAndDeleteTheSectionsOfferNoAddition() {
        editor.setAddDeleteEnabled(false);
        show(entry);
        assertFalse(section(entry, ComponentKind.SENSE).canAdd());
        assertTrue(lookup(".item-delete").queryAll().isEmpty(), "no delete button");
    }

    /* ─── Settings ─── */

    @Test
    void theEditorBringsItsStylesheet() {
        assertTrue(editor.getStylesheets().contains(LiftFx.stylesheet()));
    }

    @Test
    void theTextsFollowTheLocaleOfTheLibrary() {
        LiftFx.setLocale(Locale.FRENCH);
        try {
            show(entry);
            assertEquals("Sens", section(entry, ComponentKind.SENSE).title());
        } finally {
            LiftFx.setLocale(Locale.ENGLISH);
        }
    }

    @Test
    void emptySectionsCanBeHidden() {
        show(entry);
        ComponentSection variants = section(entry, ComponentKind.VARIANT);
        assertTrue(variants.isVisible());
        interact(() -> LiftFx.hideEmptySectionsProperty().set(true));
        try {
            assertFalse(variants.isVisible(), "no variant: hidden");
            assertTrue(section(entry, ComponentKind.SENSE).isVisible(), "one sense: shown");
        } finally {
            interact(() -> LiftFx.hideEmptySectionsProperty().set(false));
        }
    }

    private <T> T interactAndGet(java.util.concurrent.Callable<T> c) {
        Object[] out = new Object[1];
        interact(() -> {
            try {
                out[0] = c.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        @SuppressWarnings("unchecked") T t = (T) out[0];
        return t;
    }
}
