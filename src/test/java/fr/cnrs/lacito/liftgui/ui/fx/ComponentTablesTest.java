package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.view.ComponentKinds;
import java.util.List;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The tables of the "Objects" subtree for the components that had none (translations,
 * pronunciations, media, reversals, illustrations, traits, annotations): rows, editor,
 * parent link, editing, deletion.
 */
class ComponentTablesTest extends MainWindowTest {

    private LiftEntry entry;
    private LiftSense sense;
    private LiftExample example;
    private LiftPronunciation pronunciation;
    private LiftMedia media;
    private LiftIllustration illustration;
    private LiftReversal reversal;
    private LiftTranslation translation;
    private LiftTrait trait;
    private LiftAnnotation annotation;
    private LiftAnnotation glossAnnotation;

    /** One component of each new kind, on an entry of the demo dictionary. */
    @BeforeEach
    void addComponents() {
        interact(() -> {
            LiftDictionary d = dictionary();
            LiftHeader h = d.getHeader();
            String objLang = d.getObjectLanguageManager().getLanguages().iterator().next();
            String metaLang = d.getMetaLanguageManager().getLanguages().iterator().next();
            entry = d.getLiftDictionaryRegistry().getEntries().stream()
                .filter(e -> !e.getSenses().isEmpty()).findFirst().orElseThrow();
            sense = entry.getSenses().getFirst();
            if (sense.getExamples().isEmpty()) {
                LiftExample ex = new LiftExample();
                ex.getExample().add(new Form(objLang, "an example"));
                sense.addExample(ex);
            }
            example = sense.getExamples().getFirst();

            pronunciation = new LiftPronunciation();
            pronunciation.getPronunciation().add(new Form(objLang, "prɔnʌns"));
            entry.addPronunciation(pronunciation);
            media = new LiftMedia("audio/word.wav");
            pronunciation.addMedia(media);
            illustration = new LiftIllustration("pictures/word.png");
            sense.addIllustration(illustration);
            reversal = new LiftReversal(h.getInverseTypeManager().getOrCreateFeature("test-reversal"));
            reversal.getForms().add(new Form(objLang, "reversed"));
            sense.addReversal(reversal);
            Feature free = h.getTranslationTypeManager().getOrCreateFeature("test-free");
            example.createTranslation(free).add(new Form(metaLang, "a translation"));
            translation = example.getTranslationComponents().stream()
                .filter(t -> t.getType() == free).findFirst().orElseThrow();
            trait = new LiftTrait(h.getOrCreateTraitsDefinitions("test-trait"), "a value");
            entry.addTrait(trait);
            annotation = new LiftAnnotation(h.getAnnotationTypeManager().getOrCreateFeature("test-annotation"));
            annotation.setValue("checked");
            entry.addAnnotation(annotation);
            glossAnnotation = new LiftAnnotation(h.getAnnotationTypeManager().getOrCreateFeature("test-annotation"));
            sense.getGlosses().addAnnotation(glossAnnotation);
        });
    }

    private void show(String viewKey, Object row) {
        openView(viewKey);
        interact(() -> table().getSelectionModel().select(row));
        assertSame(row, table().getSelectionModel().getSelectedItem(), viewKey + " lists the component");
    }

    /** Follows the parent link, and checks the table and the row it lands on. */
    private void assertParentLinkOpens(String parentView, Object parent) {
        fire(lookup("#editorContainer .back-btn").queryButton());
        assertEquals(I18n.get(parentView), viewTitle());
        assertSame(parent, table().getSelectionModel().getSelectedItem(), "the parent's row is selected");
    }

    @Test
    void theObjectsSubtreeListsEveryComponentTable() {
        TreeItem<String> objects = navTree().getRoot().getChildren().stream()
            .filter(i -> I18n.get("nav.objects").equals(i.getValue())).findFirst().orElseThrow();
        List<String> expected = ComponentKinds.all().stream().map(k -> I18n.get(k.key().i18nKey())).toList();
        assertEquals(expected, objects.getChildren().stream().map(TreeItem::getValue).toList());
        for (String kind : List.of("nav.translations", "nav.pronunciations", "nav.media", "nav.reversals",
                "nav.illustrations", "nav.traits", "nav.annotations")) {
            assertTrue(expected.contains(I18n.get(kind)), kind);
        }
    }

    @Test
    void aTranslationLinksToItsExample() {
        show("nav.translations", translation);
        assertNotNull(deleteButton());
        assertParentLinkOpens("nav.examples", example);
    }

    @Test
    void aPronunciationLinksToItsEntry() {
        show("nav.pronunciations", pronunciation);
        assertParentLinkOpens("nav.entries", entry);
    }

    @Test
    void aMediaLinksToItsPronunciation() {
        show("nav.media", media);
        assertParentLinkOpens("nav.pronunciations", pronunciation);
    }

    @Test
    void anIllustrationLinksToItsSense() {
        show("nav.illustrations", illustration);
        assertParentLinkOpens("nav.senses", sense);
    }

    @Test
    void aReversalLinksToItsSense() {
        show("nav.reversals", reversal);
        assertParentLinkOpens("nav.senses", sense);
    }

    @Test
    void aTraitLinksToItsEntry() {
        show("nav.traits", trait);
        assertParentLinkOpens("nav.entries", entry);
    }

    @Test
    void anAnnotationLinksToItsEntry() {
        show("nav.annotations", annotation);
        assertParentLinkOpens("nav.entries", entry);
    }

    @Test
    void anAnnotationOnAGlossLinksToItsSense() {
        show("nav.annotations", glossAnnotation);
        assertParentLinkOpens("nav.senses", sense);
    }

    @Test
    void theFileOfAMediaIsEditedInItsEditor() {
        show("nav.media", media);
        TextField href = lookup("#editorContainer .text-field")
            .match(n -> n instanceof TextField t && "audio/word.wav".equals(t.getText())).queryAs(TextField.class);
        clickOn(href).write("x");
        assertEquals("audio/word.wavx", media.getHref());
        assertTrue(isDirty());
    }

    @Test
    void theTypeOfATranslationIsChangedInItsEditor() {
        Feature other = dictionary().getHeader().getTranslationTypeManager().getOrCreateFeature("test-literal");
        show("nav.translations", translation);
        @SuppressWarnings("unchecked")
        ComboBox<Feature> type = lookup("#editorContainer .combo-box").queryAs(ComboBox.class);
        interact(() -> type.setValue(other));
        assertSame(other, translation.getType());
    }

    @Test
    void deletingAMediaFromItsTableCanBeUndone() {
        show("nav.media", media);
        fire(deleteButton());
        pressDialogButton(ButtonType.OK);
        assertFalse(pronunciation.getMedias().contains(media));
        assertFalse(table().getItems().contains(media));
        fire(lookup("#undoButton").queryButton());
        assertTrue(pronunciation.getMedias().contains(media));
        assertTrue(table().getItems().contains(media), "the row is back");
    }

    @Test
    void theEntryEditorOpensTheTableOfItsPronunciations() {
        // The Entries view is open from the start: rebuild it, with the pronunciation added above.
        openView("nav.senses");
        openView("nav.entries");
        interact(() -> table().getSelectionModel().select(entry));
        String text = I18n.get("editor.openChildren", I18n.get("nav.pronunciations"), entry.getPronunciations().size());
        fire(lookup("#editorContainer .hyperlink")
            .match(n -> n instanceof Hyperlink h && text.equals(h.getText())).queryAs(Hyperlink.class));
        assertTrue(viewTitle().startsWith(I18n.get("nav.pronunciations")), viewTitle());
        assertEquals(entry.getPronunciations().size(), table().getItems().size(), "only the entry's pronunciations");
    }
}
