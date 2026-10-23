package fr.cnrs.lacito.liftgui.ui.fx;

import fr.cnrs.lacito.liftgui.testsupport.InMemoryPreferences;
import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.app.DictionaryDocument;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.testfx.api.FxToolkit;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Base class of the UI tests: opens the main window on the demo dictionary (the in-memory
 * preferences have no recent file), in English, and offers lookups of its parts.
 */
public abstract class MainWindowTest extends ApplicationTest {

    protected Stage stage;
    protected Object controller;
    /** Exceptions thrown on the JavaFX thread (e.g. in a button handler) during the test. */
    private final List<Throwable> fxErrors = new CopyOnWriteArrayList<>();

    @Override
    public void init() {
        InMemoryPreferences.clearAll();
        I18n.setLocale(Locale.ENGLISH);
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> fxErrors.add(e));
        FXMLLoader loader = new FXMLLoader(
            MainWindowTest.class.getResource("/fr/cnrs/lacito/liftgui/ui/MainView.fxml"), I18n.getBundle());
        Parent root = loader.load();
        controller = loader.getController();
        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add(MainWindowTest.class.getResource("/fr/cnrs/lacito/liftgui/ui/app.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    @AfterEach
    void closeWindows() throws TimeoutException {
        List<Throwable> errors = List.copyOf(fxErrors);
        // Dialogs left open by a failed test must not block the next one.
        interact(() -> new ArrayList<>(Window.getWindows()).forEach(w -> {
            if (w != stage && w instanceof Stage s) s.close();
        }));
        FxToolkit.hideStage();
        FxToolkit.cleanupStages();
        if (!errors.isEmpty()) {
            AssertionError failure = new AssertionError("exception on the JavaFX thread: " + errors.get(0));
            errors.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    /* ─── Menus ─── */

    /**
     * Fires a menu item of the menu bar, given the path of labels (i18n keys). The item is
     * fired later on the JavaFX thread, so that an action opening a dialog does not block
     * the test.
     */
    protected void fireMenu(String... i18nKeys) {
        MenuBar bar = lookup(".menu-bar").queryAs(MenuBar.class);
        List<MenuItem> items = new ArrayList<>(bar.getMenus());
        MenuItem found = null;
        for (String key : i18nKeys) {
            String label = I18n.get(key);
            found = items.stream().filter(i -> label.equals(i.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("no menu item " + label));
            items = found instanceof Menu m ? new ArrayList<>(m.getItems()) : List.of();
        }
        MenuItem item = found;
        Platform.runLater(item::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Fires the menu item with this id (e.g. undoMenuItem), whatever its current text. */
    protected void fireMenuItem(String id) {
        MenuBar bar = lookup(".menu-bar").queryAs(MenuBar.class);
        MenuItem item = bar.getMenus().stream().flatMap(m -> m.getItems().stream())
            .filter(i -> id.equals(i.getId())).findFirst()
            .orElseThrow(() -> new AssertionError("no menu item " + id));
        Platform.runLater(item::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /* ─── Model ─── */

    /** The dictionary being edited. */
    protected LiftDictionary dictionary() {
        return document().dictionary();
    }

    protected DictionaryDocument document() {
        try {
            Field f = controller.getClass().getDeclaredField("document");
            f.setAccessible(true);
            return (DictionaryDocument) f.get(controller);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    protected boolean isDirty() {
        return document().isDirty();
    }

    /* ─── Parts of the main window ─── */

    @SuppressWarnings("unchecked")
    protected TreeView<String> navTree() {
        return lookup("#navTree").queryAs(TreeView.class);
    }

    protected String viewTitle() {
        return lookup("#viewTitle").queryLabeled().getText();
    }

    protected String countLabel() {
        return lookup("#tableCountLabel").queryLabeled().getText();
    }

    protected String editorTitle() {
        return lookup("#editEntryTitle").queryLabeled().getText();
    }

    protected TextField searchField() {
        return lookup("#searchField").queryAs(TextField.class);
    }

    protected Button addButton() {
        return lookup("#addButton").queryButton();
    }

    protected String windowTitle() {
        return stage.getTitle();
    }

    /** The table of the current view. */
    @SuppressWarnings("unchecked")
    protected <T> TableView<T> table() {
        return lookup("#tableContainer .table-view").queryAs(TableView.class);
    }

    /** Shows the view whose navigation item has the label of {@code i18nKey}. */
    protected void openView(String i18nKey) {
        openNavItem(I18n.get(i18nKey));
    }

    protected void openNavItem(String label) {
        TreeItem<String> item = findItem(navTree().getRoot(), label);
        if (item == null) throw new AssertionError("no navigation item " + label);
        interact(() -> navTree().getSelectionModel().select(item));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static TreeItem<String> findItem(TreeItem<String> item, String label) {
        for (TreeItem<String> child : item.getChildren()) {
            if (child.isLeaf() && label.equals(child.getValue())) return child;
            TreeItem<String> found = findItem(child, label);
            if (found != null) return found;
        }
        return null;
    }

    /** The leaf items of the navigation tree. */
    protected List<TreeItem<String>> navLeaves() {
        List<TreeItem<String>> leaves = new ArrayList<>();
        collect(navTree().getRoot(), leaves);
        return leaves;
    }

    private static void collect(TreeItem<String> item, List<TreeItem<String>> acc) {
        for (TreeItem<String> c : item.getChildren()) {
            if (c.isLeaf()) acc.add(c);
            else collect(c, acc);
        }
    }

    /** Selects a row of the current table (and waits for the editor panel). */
    protected void selectRow(int index) {
        interact(() -> {
            TableView<?> t = table();
            t.getSelectionModel().clearAndSelect(index);
            t.scrollTo(index);
        });
    }

    /** The red "Delete" button at the top of the editor panel. */
    protected Button deleteButton() {
        return lookup("#editorContainer .delete-btn").queryButton();
    }

    /** The editable text fields of the editor panel, in order. */
    protected List<TextField> editorTextFields() {
        return lookup("#editorContainer .text-field").queryAllAs(TextField.class).stream()
            .filter(tf -> tf.isEditable() && tf.isVisible())
            .toList();
    }

    /** A button of the editor panel, by its text. */
    protected Button editorButton(String text) {
        return lookup("#editorContainer .button").match((Node n) -> n instanceof Button b && text.equals(b.getText()))
            .queryButton();
    }

    /** Buttons of the editor panel whose text starts with {@code prefix}. */
    protected Button editorButtonStartingWith(String prefix) {
        return lookup("#editorContainer .button")
            .match((Node n) -> n instanceof Button b && b.getText() != null && b.getText().startsWith(prefix))
            .queryButton();
    }

    /* ─── Dialogs ─── */

    /** The dialog pane of the open dialog, if any. */
    protected DialogPane dialog() {
        WaitForAsyncUtils.waitForFxEvents();
        return lookup(".dialog-pane").queryAs(DialogPane.class);
    }

    protected boolean dialogOpen() {
        WaitForAsyncUtils.waitForFxEvents();
        return lookup(".dialog-pane").tryQuery().isPresent();
    }

    /** Presses a button of the open dialog, by its button type. */
    protected void pressDialogButton(ButtonType type) {
        DialogPane pane = dialog();
        Button b = (Button) pane.lookupButton(type);
        clickOn(b);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Types {@code text} in the text field of the open dialog and presses OK. */
    protected void answerTextDialog(String text) {
        DialogPane pane = dialog();
        TextField field = from(pane).lookup(".text-field").queryAs(TextField.class);
        interact(() -> field.setText(text));
        pressDialogButton(ButtonType.OK);
    }

    /**
     * Clicks a button and waits for the events it triggers (dialogs open asynchronously).
     * A button scrolled out of view (e.g. at the bottom of the editor panel) is fired directly.
     */
    protected void fire(ButtonBase b) {
        if (isShownOnScreen(b)) clickOn(b);
        else Platform.runLater(b::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private boolean isShownOnScreen(Node n) {
        javafx.geometry.Bounds b = n.localToScene(n.getBoundsInLocal());
        for (Node p = n.getParent(); p != null; p = p.getParent()) {
            if (p instanceof ScrollPane sp) {
                javafx.geometry.Bounds view = sp.localToScene(sp.getBoundsInLocal());
                if (!view.contains(b.getCenterX(), b.getCenterY())) return false;
            }
        }
        return true;
    }

    /**
     * Sends shortcut+key (Cmd on macOS, Ctrl elsewhere) to the focused node. The headless
     * robot does not send modifier keys, so the event is built directly.
     */
    protected void pressShortcut(javafx.scene.input.KeyCode key) {
        interact(() -> {
            boolean mac = System.getProperty("os.name").startsWith("Mac");
            javafx.scene.input.KeyEvent ev = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", key, false, !mac, false, mac);
            Node target = stage.getScene().getFocusOwner() != null ? stage.getScene().getFocusOwner() : stage.getScene().getRoot();
            javafx.event.Event.fireEvent(target, ev);
        });
    }

    /** The digits of a text (counts are formatted with grouping separators). */
    protected static String digits(String s) {
        return s.replaceAll("\\D", "");
    }
}
