package fr.cnrs.lacito.liftgui.ui.fx;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftfx.ComponentKind;
import fr.cnrs.lacito.liftfx.internal.ComponentSection;
import fr.cnrs.lacito.liftfx.internal.ItemCard;
import fr.cnrs.lacito.liftfx.internal.SectionPane;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.util.List;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The sections of the editors: their count, their [+] in the title bar, the cards with
 * their delete button, the keyboard and the context menus, and the hidden empty sections.
 */
class ComponentSectionsTest extends MainWindowTest {

    private LiftEntry entry;
    private LiftSense sense;
    private LiftNote note;
    private LiftTrait trait;

    @BeforeEach
    void prepare() {
        interact(() -> {
            LiftDictionary d = dictionary();
            entry = d.getLiftDictionaryRegistry().getEntries().stream()
                .filter(e -> e.getSenses().size() >= 2).findFirst().orElseThrow();
            sense = entry.getSenses().getFirst();
            String noteType = d.getHeader().getNoteTypeManager().getFeatures().keySet().stream().sorted()
                .findFirst().orElse("general");
            note = sense.getNotes().values().stream().findFirst()
                .orElseGet(() -> d.getComponentBuilder().note(sense, noteType).build());
            trait = d.getComponentBuilder().trait(entry, "test-trait", "a value").build();
            document().history().markSaved();
        });
        showEntry(entry);
    }

    private void showEntry(LiftEntry e) {
        openView("nav.entries");
        interact(() -> table().getSelectionModel().select(e));
        assertSame(e, table().getSelectionModel().getSelectedItem());
    }

    private void showSense(LiftSense s) {
        openView("nav.senses");
        interact(() -> table().getSelectionModel().select(s));
        assertSame(s, table().getSelectionModel().getSelectedItem());
    }

    private List<ComponentSection> sections() {
        WaitForAsyncUtils.waitForFxEvents();
        return lookup("#editorContainer .component-section").queryAllAs(ComponentSection.class).stream().toList();
    }

    @Test
    void everySectionShowsItsCountIncludingZero() {
        List<ComponentSection> all = sections();
        assertFalse(all.isEmpty());
        for (ComponentSection s : all) {
            assertEquals(s.title() + " (" + s.cards().size() + ")", s.getText(), s.getId());
        }
        assertTrue(all.stream().anyMatch(s -> s.getCount() == 0), "a section at 0 shows (0)");
        assertTrue(lookup("#editorContainer .section-count").queryAll().size() >= all.size());
        assertTrue(lookup("#editorContainer").queryAs(Node.class).lookupAll(".label").stream()
            .noneMatch(n -> n instanceof javafx.scene.control.Label l && I18n.get("editor.none").equals(l.getText())),
            "no more \"None\"");
    }

    @Test
    void aPropertiesPaneShowsTheTotalOfItsComponents() {
        SectionPane props = lookup("#editorContainer .properties-pane").queryAllAs(SectionPane.class).stream()
            .filter(p -> ItemCard.of(p) == null).findFirst().orElseThrow();
        int total = entry.getTraits().size() + entry.getAnnotations().size() + entry.getFields().size()
            + entry.getNotes().size();
        assertEquals(total, props.getCount());
        assertTrue(props.getText().endsWith("(" + total + ")"), props.getText());
    }

    @Test
    void thePlusOfAClosedPaneDoesNotOpenItThenTheAdditionDoes() {
        ComponentSection relations = section(entry, ComponentKind.RELATION);
        interact(() -> {
            relations.setExpanded(false);
            fr.cnrs.lacito.liftfx.internal.Reveal.scrollTo(relations);
        });
        MenuButton plus = (MenuButton) relations.addButton();
        clickOn(plus);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(plus.isShowing(), "the [+] opens the menu of the types");
        assertFalse(relations.isExpanded(), "the click on the [+] does not open the pane");
        interact(plus::hide);

        int before = entry.getRelations().size();
        List<MenuItem> types = typeMenu(entry, ComponentKind.RELATION);
        if (types.stream().anyMatch(i -> !(i instanceof SeparatorMenuItem) && !I18n.get("menu.otherType").equals(i.getText()))) {
            addTyped(entry, ComponentKind.RELATION, null);
        } else {
            addTyped(entry, ComponentKind.RELATION, I18n.get("menu.otherType"));
            answerTextDialog("test-relation");
        }
        assertEquals(before + 1, entry.getRelations().size());
        assertTrue(section(entry, ComponentKind.RELATION).isExpanded(), "the pane opens after the addition");
    }

    @Test
    void aTypeAlreadyUsedCannotBeChosenAgain() {
        showSense(sense);
        String used = note.getType().getId();
        MenuItem item = typeMenu(sense, ComponentKind.NOTE).stream()
            .filter(i -> used.equals(i.getText())).findFirst().orElseThrow();
        assertTrue(item.isDisable(), "one note per type");
    }

    @Test
    void aFieldInsideANoteCanBeDeleted() {
        LiftField field = interactAndGet(() -> dictionary().getComponentBuilder().field(note, "test-field").build());
        showSense(sense);
        ItemCard card = card(field);
        assertNotNull(card.deleteButton(), "a field in a note has its delete button");
        interact(card.deleteButton()::fire);
        assertFalse(dialogOpen(), "a field is deleted without asking");
        assertFalse(note.getFields().containsValue(field));
    }

    @Test
    void aTraitGetsAnAnnotation() {
        int before = trait.getAnnotations().size();
        boolean anyType = typeMenu(trait, ComponentKind.ANNOTATION).stream()
            .anyMatch(i -> !(i instanceof SeparatorMenuItem) && !I18n.get("menu.otherType").equals(i.getText()));
        if (anyType) {
            addTyped(trait, ComponentKind.ANNOTATION, null);
        } else {
            addTyped(trait, ComponentKind.ANNOTATION, I18n.get("menu.otherType"));
            answerTextDialog("checked");
        }
        assertEquals(before + 1, trait.getAnnotations().size());
        assertEquals(before + 1, section(trait, ComponentKind.ANNOTATION).getCount());
    }

    @Test
    void aNewSenseIsEmptyTitledSoFocusedAndOneUndoRemovesIt() {
        int senses = entry.getSenses().size();
        add(entry, ComponentKind.SENSE);
        LiftSense created = entry.getSenses().getLast();
        ItemCard card = card(created);
        assertTrue(card.title().endsWith(I18n.get("sense.empty")), card.title());
        assertTrue(card.getStyleClass().contains("item-empty"));
        assertTrue(card.isExpanded());
        Node focus = focusOwner();
        assertSame(card, ItemCard.of(focus), "the focus is in the new sense");
        fireMenuItem("undoMenuItem");
        assertEquals(senses, entry.getSenses().size());
    }

    @Test
    void illustrationsAndMediaHaveTheirSectionsAndCanBeDeleted() {
        LiftIllustration ill = interactAndGet(() -> dictionary().getComponentBuilder().illustration(sense, "pictures/a.png").build());
        showSense(sense);
        assertNotNull(section(sense, ComponentKind.ILLUSTRATION).addButton(), "the [+] chooses a file");
        interact(card(ill).deleteButton()::fire);
        assertFalse(dialogOpen());
        assertFalse(sense.getIllustrations().contains(ill));

        LiftPronunciation pron = interactAndGet(() -> dictionary().getComponentBuilder().pronunciation(entry).build());
        LiftMedia media = interactAndGet(() -> dictionary().getComponentBuilder().media(pron, "audio/a.wav").build());
        showEntry(entry);
        assertEquals(1, section(pron, ComponentKind.MEDIA).getCount());
        interact(card(media).deleteButton()::fire);
        assertFalse(pron.getMedias().contains(media));
    }

    @Test
    void deletingAnEmptySenseAsksNothingAndANonEmptyOneAsks() {
        add(entry, ComponentKind.SENSE);
        LiftSense created = entry.getSenses().getLast();
        interact(card(created).deleteButton()::fire);
        assertFalse(dialogOpen(), "an empty sense goes without asking");
        assertFalse(entry.getSenses().contains(created));

        fireLater(card(sense).deleteButton());
        assertTrue(dialogOpen(), "a sense with content asks first");
        pressDialogButton(ButtonType.CANCEL);
        assertTrue(entry.getSenses().contains(sense));
    }

    @Test
    void theInSectionCommandAddsToTheInnermostSection() {
        ComponentSection annotations = section(trait, ComponentKind.ANNOTATION);
        interact(() -> {
            fr.cnrs.lacito.liftfx.internal.Reveal.expandAncestors(annotations);
            annotations.requestFocus();
        });
        assertEquals(new javafx.scene.input.KeyCodeCombination(KeyCode.N, javafx.scene.input.KeyCombination.SHORTCUT_DOWN,
            javafx.scene.input.KeyCombination.SHIFT_DOWN), menuItem("add.inSection").getAccelerator());
        int before = trait.getAnnotations().size();
        fireMenuItem("add.inSection");
        MenuButton plus = (MenuButton) section(trait, ComponentKind.ANNOTATION).addButton();
        assertTrue(plus.isShowing(), "the menu of the annotation types opens under the [+]");
        MenuItem first = plus.getItems().stream().filter(i -> !(i instanceof SeparatorMenuItem)).findFirst().orElseThrow();
        boolean other = I18n.get("menu.otherType").equals(first.getText());
        runLater(() -> {
            plus.hide();
            first.fire();
        });
        if (other) answerTextDialog("checked");
        assertEquals(before + 1, trait.getAnnotations().size(), "an annotation of the trait, not a trait");
    }

    @Test
    void theContextMenuOfACardAddsAndDeletes() {
        ItemCard card = card(trait);
        ContextMenu menu = openContextMenu(card.header());
        List<String> texts = menu.getItems().stream().map(MenuItem::getText).toList();
        assertTrue(texts.contains(ComponentKind.ANNOTATION.addTooltip()), texts.toString());
        MenuItem delete = menu.getItems().stream().filter(i -> I18n.get("btn.delete").equals(i.getText()))
            .findFirst().orElseThrow();
        runLater(() -> {
            menu.hide();
            delete.fire();
        });
        assertFalse(entry.getTraits().contains(trait));
    }

    @Test
    void deleteOnAFocusedCardHeaderDeletesTheComponent() {
        ItemCard card = card(trait);
        interact(() -> {
            fr.cnrs.lacito.liftfx.internal.Reveal.expandAncestors(card);
            card.header().requestFocus();
        });
        type(KeyCode.DELETE);
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(entry.getTraits().contains(trait));
        fireMenuItem("undoMenuItem");
        assertTrue(entry.getTraits().contains(trait), "the deletion is undone");
    }

    @Test
    void hidingTheEmptySectionsAndAddingThroughTheAddMenu() {
        ComponentSection etymologies = section(entry, ComponentKind.ETYMOLOGY);
        boolean emptyEtymologies = etymologies.getCount() == 0;
        javafx.scene.control.CheckMenuItem hide = (javafx.scene.control.CheckMenuItem) menuItem("view.hideEmptySections");
        interact(() -> hide.setSelected(true));
        try {
            for (ComponentSection s : sections()) {
                assertEquals(s.getCount() > 0, s.isVisible(), s.getId());
            }
            MenuButton addHidden = lookup("#addHiddenSections").queryAs(MenuButton.class);
            assertTrue(addHidden.isVisible());
            interact(addHidden::show);
            List<String> texts = addHidden.getItems().stream().map(MenuItem::getText).toList();
            interact(addHidden::hide);
            if (emptyEtymologies) assertTrue(texts.contains(ComponentKind.ETYMOLOGY.addTooltip()), texts.toString());
            assertTrue(new fr.cnrs.lacito.liftgui.ui.app.AppPreferences().hideEmptySections(), "remembered");
        } finally {
            interact(() -> hide.setSelected(false));
        }
        assertTrue(sections().stream().allMatch(Node::isVisible));
    }

    /* ─── Helpers ─── */

    private <T> T interactAndGet(java.util.function.Supplier<T> s) {
        java.util.concurrent.atomic.AtomicReference<T> ref = new java.util.concurrent.atomic.AtomicReference<>();
        interact(() -> ref.set(s.get()));
        return ref.get();
    }

    private MenuItem menuItem(String id) {
        return lookup(".menu-bar").queryAs(javafx.scene.control.MenuBar.class).getMenus().stream()
            .flatMap(m -> m.getItems().stream()).filter(i -> id.equals(i.getId())).findFirst().orElseThrow();
    }

    /** Fires a button later, so that a dialog it opens does not block the test. */
    private void fireLater(javafx.scene.control.ButtonBase b) {
        runLater(b::fire);
    }

    private void runLater(Runnable r) {
        javafx.application.Platform.runLater(r);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Opens the context menu of a node as the context-menu key would, and returns it. */
    private ContextMenu openContextMenu(Node node) {
        interact(() -> {
            Point2D p = node.localToScreen(2, 2);
            Point2D s = node.localToScene(2, 2);
            Event.fireEvent(node, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                2, 2, p == null ? 0 : p.getX(), p == null ? 0 : p.getY(), true, null));
        });
        WaitForAsyncUtils.waitForFxEvents();
        return Window.getWindows().stream().filter(w -> w instanceof ContextMenu && w.isShowing())
            .map(ContextMenu.class::cast).findFirst().orElseThrow(() -> new AssertionError("no context menu"));
    }
}
