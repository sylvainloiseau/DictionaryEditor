/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftgui.MainApp;
import fr.cnrs.lacito.liftgui.core.CsvExporter;
import fr.cnrs.lacito.liftgui.ui.app.*;
import fr.cnrs.lacito.liftgui.ui.controls.ComponentDeletion;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.editor.EditorPanel;
import fr.cnrs.lacito.liftgui.ui.nav.NavigationTree;
import fr.cnrs.lacito.liftgui.ui.nav.Navigator;
import fr.cnrs.lacito.liftgui.ui.nav.ViewKey;
import fr.cnrs.lacito.liftgui.ui.nav.ViewRequest;
import fr.cnrs.lacito.liftgui.ui.table.FilterableTable;
import fr.cnrs.lacito.liftgui.ui.view.DictionaryView;
import fr.cnrs.lacito.liftgui.ui.view.EntryView;
import fr.cnrs.lacito.liftgui.ui.view.Views;
import fr.cnrs.lacito.liftgui.undo.UndoManager;
import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Main controller: wires the main window together and handles the menus.
 * <p>
 * General display:
 * <ul>
 *   <li>Left: the navigation tree ({@link NavigationTree})</li>
 *   <li>Center: the current view ({@link DictionaryView}, created by {@link Views})</li>
 *   <li>Right: the editor panel ({@link EditorPanel}) showing the selected object</li>
 * </ul>
 * The document (dictionary, file, unsaved changes) is a {@link DictionaryDocument}; the
 * File menu is handled by {@link FileActions}.
 */
public final class MainController {

    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());
    private static final double MIN_LEFT_WIDTH = 250;
    private static final double MIN_RIGHT_WIDTH = 300;

    /* ─── FXML nodes ─── */
    @FXML private TreeView<String> navTree;
    @FXML private StackPane tableContainer;
    @FXML private TextField searchField;
    @FXML private Label viewTitle;
    @FXML private Label tableCountLabel;
    @FXML private Label editEntryTitle;
    @FXML private Label editEntryCode;
    @FXML private VBox rightContent;
    @FXML private VBox editorContainer;
    @FXML private Menu recentMenu;
    @FXML private MenuBar menuBar;
    @FXML private SplitPane mainSplit;
    @FXML private Button addButton;
    @FXML private Button undoButton;
    @FXML private Button redoButton;
    @FXML private MenuItem undoMenuItem;
    @FXML private MenuItem redoMenuItem;

    /* ─── State ─── */
    private final AppPreferences prefs = new AppPreferences();
    private final DictionaryDocument document = new DictionaryDocument();
    private final UndoManager undoManager = new UndoManager();
    private final FileActions fileActions = new FileActions(document, prefs, this::window);
    private Navigator navigator;
    private NavigationTree navigation;
    private EditorContext ctx;
    private DictionaryView currentView;

    /* ─────────────────────── INITIALIZATION ─────────────────────── */

    @FXML
    private void initialize() {
        RecentFiles recentFiles = new RecentFiles(prefs.node(), recentMenu, fileActions::openRecent);
        fileActions.setRecentFiles(recentFiles);
        recentFiles.refreshMenu();

        EditorPanel panel = new EditorPanel(editorContainer, editEntryTitle, editEntryCode);
        navigator = new Navigator(this::show, () -> document.dictionary() == null
            ? List.of()
            : document.dictionary().getLiftDictionaryRegistry().getSenses());
        ctx = new EditorContext(document, panel, navigator, undoManager, searchField.textProperty(), this::updateCountLabel);
        navigation = new NavigationTree(navTree, navigator::go, this::onCreateNewObject);

        ComponentDeletion.setUndoSink(ctx::recordExecuted);
        DirtyTracker.install(() -> {
            document.markDirty();
            panel.touchEditedEntry();
        }, editorContainer, tableContainer);

        // A new dictionary (opened, created, or handed over): fresh undo history, its ranges
        // in the navigation tree, and the entries view.
        document.dictionaryProperty().addListener((obs, o, d) -> {
            undoManager.clear();
            navigation.setRanges(d == null ? null : d.getHeader());
            navigator.go(ViewKey.ENTRIES);
        });

        setupSplitPane();
        setupMenuHover();
        setupUndoRedo();
        setupWindow();
        fileActions.loadInitial();
        if (currentView == null) navigator.go(ViewKey.ENTRIES);
    }

    private Window window() {
        return menuBar.getScene() == null ? null : menuBar.getScene().getWindow();
    }

    /** Font, theme, window title and close confirmation, once the scene and window exist. */
    private void setupWindow() {
        menuBar.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) return;
            prefs.applyAppearance(scene);
            installUndoRedoAccelerators(scene);
            scene.windowProperty().addListener((o2, oldW, w) -> attachWindow(w));
            attachWindow(scene.getWindow());
        });
    }

    private void attachWindow(Window w) {
        if (!(w instanceof Stage stage)) return;
        stage.setOnCloseRequest(ev -> {
            if (!fileActions.confirmDiscardChanges()) ev.consume();
        });
        stage.titleProperty().bind(document.titleBinding());
    }

    private void setupUndoRedo() {
        undoButton.setGraphic(Icons.undoIcon());
        undoButton.setTooltip(new Tooltip(I18n.get(Keys.MENU_EDIT_UNDO) + " ("
            + new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN).getDisplayText() + ")"));
        redoButton.setGraphic(Icons.redoIcon());
        redoButton.setTooltip(new Tooltip(I18n.get(Keys.MENU_EDIT_REDO) + " ("
            + new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN).getDisplayText() + ")"));
        undoButton.disableProperty().bind(undoManager.canUndoProperty().not());
        redoButton.disableProperty().bind(undoManager.canRedoProperty().not());
        undoMenuItem.disableProperty().bind(undoManager.canUndoProperty().not());
        redoMenuItem.disableProperty().bind(undoManager.canRedoProperty().not());
    }

    private void installUndoRedoAccelerators(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), this::onUndo);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), this::onRedo);
    }

    /** Hovering a menu title while another menu is open opens it (as in native menu bars). */
    private void setupMenuHover() {
        // The menu titles are not yet in the scene graph at initialize() time.
        menuBar.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            Platform.runLater(() -> {
                for (Menu menu : menuBar.getMenus()) {
                    Node btn = menu.getStyleableNode();
                    if (btn == null) continue;
                    btn.setOnMouseEntered(e -> {
                        if (menuBar.getMenus().stream().anyMatch(Menu::isShowing)) {
                            menuBar.getMenus().forEach(Menu::hide);
                            menu.show();
                        }
                    });
                }
            });
        });
    }

    /** Minimum widths, and a divider that never hides the navigation tree or the editor panel. */
    private void setupSplitPane() {
        if (mainSplit.getItems().size() < 2) return;
        if (mainSplit.getItems().get(0) instanceof Region left) left.setMinWidth(MIN_LEFT_WIDTH);
        if (mainSplit.getItems().get(1) instanceof Region right) right.setMinWidth(MIN_RIGHT_WIDTH);
        SplitPane.Divider divider = mainSplit.getDividers().get(0);
        divider.positionProperty().addListener((obs, oldPos, newPos) -> {
            double total = mainSplit.getWidth();
            if (total <= 0) return;
            double clamped = Math.max(MIN_LEFT_WIDTH / total, Math.min(1.0 - MIN_RIGHT_WIDTH / total, newPos.doubleValue()));
            if (Math.abs(clamped - newPos.doubleValue()) > 0.001) Platform.runLater(() -> divider.setPosition(clamped));
        });
    }

    private void setEditorPanelVisible(boolean visible) {
        if (mainSplit.getItems().size() < 2) return;
        Node rightPane = mainSplit.getItems().get(1);
        rightPane.setManaged(visible);
        rightPane.setVisible(visible);
    }

    /* ─────────────────────── VIEWS ─────────────────────── */

    /** Shows a view (called by the navigator). */
    private void show(ViewRequest request) {
        if (currentView != null) currentView.dispose();
        ViewKey key = request.key();
        // An object to reach must not be hidden by the search.
        if (request.select() != null && !searchField.getText().isBlank()) searchField.clear();
        tableCountLabel.setText("");
        ctx.panel().clear();

        currentView = Views.create(request, ctx);
        tableContainer.getChildren().setAll(currentView.node());
        String title = currentView.title() != null ? currentView.title() : I18n.get(key.i18nKey());
        viewTitle.setText(title);
        ctx.setRefreshTable(currentView::refreshCells);

        String addText = currentView.addButtonText();
        addButton.setText(addText == null ? "" : addText);
        addButton.setVisible(addText != null);
        addButton.setManaged(addText != null);
        setEditorPanelVisible(key.hasEditorPanel());
        rightContent.setManaged(true);
        rightContent.setVisible(true);
        searchField.setVisible(key.hasSearch());
        searchField.setManaged(key.hasSearch());

        Object target = request.select();
        if (target != null) {
            if (!currentView.select(target)) ctx.editors().show(target);
        } else if (currentView instanceof EntryView entries) {
            entries.selectFirst();
        }
        navigation.select(request);
    }

    private void updateCountLabel(int shown, int total) {
        tableCountLabel.setText(shown == total
            ? I18n.get("table.count.total", shown)
            : I18n.get("table.count.filtered", shown, total));
    }

    /* ─────────────────────── MENU HANDLERS ─────────────────────── */

    @FXML
    private void onCreateNewObject() {
        if (document.dictionary() == null) {
            Dialogs.error(I18n.get(Keys.ERROR_CREATION), I18n.get(Keys.ERROR_NO_DICTIONARY));
            return;
        }
        currentView.onAdd();
    }

    @FXML
    private void onImportLift() {
        fileActions.open();
    }

    @FXML
    private void onSave() {
        fileActions.save();
    }

    @FXML
    private void onSaveAs() {
        fileActions.saveAs();
    }

    @FXML
    private void onNewDictionary() {
        fileActions.newDictionary();
    }

    @FXML
    private void onQuit() {
        if (fileActions.confirmDiscardChanges()) Platform.exit();
    }

    @FXML
    private void onPreferences() {
        PreferencesDialog.show(prefs, menuBar.getScene(), () -> {
            // The reloaded scene takes over the open dictionary.
            document.handOver();
            Platform.runLater(MainApp::reloadScene);
        });
    }

    @FXML
    private void onUndo() {
        if (!undoManager.canUndo()) return;
        undoManager.undo();
        document.markDirty();
    }

    @FXML
    private void onRedo() {
        if (!undoManager.canRedo()) return;
        undoManager.redo();
        document.markDirty();
    }

    /** Copies the selected text, or in the Entries view the selected entry (id and forms). */
    @FXML
    private void onCopy() {
        if (focusOwner() instanceof TextInputControl tic) tic.copy();
        else if (currentView instanceof EntryView entries) entries.copySelected();
    }

    /**
     * Pastes into the focused text field; in the Entries view, outside a text field, creates a
     * new entry whose form (first object language) is the clipboard text.
     */
    @FXML
    private void onPaste() {
        if (focusOwner() instanceof TextInputControl tic) tic.paste();
        else if (currentView instanceof EntryView entries) entries.pasteAsNewEntry();
    }

    /** Cuts the selected text of the focused text field. */
    @FXML
    private void onCut() {
        if (focusOwner() instanceof TextInputControl tic) tic.cut();
    }

    private Node focusOwner() {
        return menuBar.getScene() == null ? null : menuBar.getScene().getFocusOwner();
    }

    @FXML
    private void onValidateDictionary() {
        LiftDictionary d = document.dictionary();
        if (d == null) {
            Dialogs.error(I18n.get("error.validation"), I18n.get("error.noDictionaryShort"));
            return;
        }
        ValidationDialog.show(d);
    }

    /** Exports the rows shown in the current table. */
    @FXML
    private void onExportCsv() {
        if (document.dictionary() == null) {
            Dialogs.error(I18n.get("error.export"), I18n.get("error.noDictionaryShort"));
            return;
        }
        FilterableTable<?> table = currentView.table().orElse(null);
        if (table == null) {
            Dialogs.error(I18n.get("error.export"), I18n.get("error.exportNoTable"));
            return;
        }
        FileChooser ch = new FileChooser();
        ch.setTitle(I18n.get("dialog.exportCsv"));
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.get("dialog.csvFilter"), "*.csv"));
        File f = ch.showSaveDialog(window());
        if (f == null) return;
        try {
            CsvExporter.write(f, table.headers(), table.rowTexts());
            Dialogs.info(I18n.get("error.export"), I18n.get("info.exportSuccess", f.getAbsolutePath()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Export CSV", e);
            Dialogs.error(I18n.get("error.export"), I18n.formatErrorMessage("error.export.detail", e));
        }
    }

    /* ─── View menu ─── */

    @FXML private void onViewEntries() { navigator.go(ViewKey.ENTRIES); }
    @FXML private void onViewSenses() { navigator.go(ViewKey.SENSES); }
    @FXML private void onViewExamples() { navigator.go(ViewKey.EXAMPLES); }
    @FXML private void onViewNotes() { navigator.go(ViewKey.NOTES); }
    @FXML private void onViewVariants() { navigator.go(ViewKey.VARIANTS); }
    @FXML private void onViewEtymologies() { navigator.go(ViewKey.ETYMOLOGIES); }
    @FXML private void onViewObjectLangs() { navigator.go(ViewKey.OBJECT_LANGUAGES); }
    @FXML private void onViewMetaLangs() { navigator.go(ViewKey.META_LANGUAGES); }
    @FXML private void onViewTraits() { navigator.go(ViewKey.TRAITS); }
    @FXML private void onViewAnnotations() { navigator.go(ViewKey.ANNOTATIONS); }
    @FXML private void onViewFields() { navigator.go(ViewKey.FIELDS); }
    @FXML private void onViewGramInfo() { navigator.go(ViewKey.GRAM_INFO); }
    @FXML private void onViewTransTypes() { navigator.go(ViewKey.TRANSLATION_TYPES); }
    @FXML private void onViewNoteTypes() { navigator.go(ViewKey.NOTE_TYPES); }
    @FXML private void onViewRelationTypes() { navigator.go(ViewKey.RELATION_TYPES); }
}
