/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftDictionaryLanguagesManager;
import fr.cnrs.lacito.liftapi.LiftVersion;
import fr.cnrs.lacito.liftapi.builder.EntryBuilder;
import fr.cnrs.lacito.liftapi.builder.SenseBuilder;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.core.DictionaryService;
import fr.cnrs.lacito.liftgui.ui.controls.*;
import fr.cnrs.lacito.liftgui.undo.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

/**
 * Main controller.
 *
 * General display:
 *  - Left:  TreeView for navigation (Objects, Languages, Categories, Configuration)
 *  - Center: dynamic TableView switching per navigation selection
 *  - Right:  detail editor form for selected table row
 */
public final class MainController {

    private static final Logger LOGGER = Logger.getLogger(
        MainController.class.getName()
    );
    private static final String FILTER_MODE_TEXT = "text";
    private static final int MAX_RECENT_FILES = 5;
    private static final String PREF_RECENT_PREFIX = "recent.file.";
    private static final Preferences PREFS = Preferences.userNodeForPackage(
        MainController.class
    );

    private void saveRecentFile(File f) {
        // De-rank existing recent files and put the new file on top.
        List<String> recents = loadRecentFiles();
        recents.remove(f.getAbsolutePath());
        recents.add(0, f.getAbsolutePath());
        if (recents.size() > MAX_RECENT_FILES) recents = recents.subList(
            0,
            MAX_RECENT_FILES
        );
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            if (i < recents.size()) PREFS.put(
                PREF_RECENT_PREFIX + i,
                recents.get(i)
            );
            else PREFS.remove(PREF_RECENT_PREFIX + i);
        }
        refreshRecentMenu();
    }

    private List<String> loadRecentFiles() {
        List<String> recents = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            String path = PREFS.get(PREF_RECENT_PREFIX + i, null);
            if (path != null) recents.add(path);
        }
        return recents;
    }

    private void refreshRecentMenu() {
        if (recentMenu == null) return;
        recentMenu.getItems().clear();
        List<String> recents = loadRecentFiles();
        if (recents.isEmpty()) {
            MenuItem empty = new MenuItem(I18n.get(Keys.MENU_FILE_NO_RECENT));
            empty.setDisable(true);
            recentMenu.getItems().add(empty);
            return;
        }
        for (String path : recents) {
            File f = new File(path);
            MenuItem item = new MenuItem(
                f.getName() + "  (" + f.getParent() + ")"
            );
            item.setOnAction(e -> {
                if (confirmDiscardChanges()) openDictionaryFile(f);
            });
            item.setDisable(!f.exists());
            recentMenu.getItems().add(item);
        }
    }

    /* ─── Nav view identifiers (i18n keys) ─── */
    private static final String NAV_ENTRIES = "nav.entries";
    private static final String NAV_SENSES = "nav.senses";
    private static final String NAV_EXAMPLES = "nav.examples";
    private static final String NAV_NOTES = "nav.notes";
    private static final String NAV_VARIANTS = "nav.variants";
    private static final String NAV_ETYMOLOGIES = "nav.etymologies";
    private static final String NAV_RELATIONS = "nav.relations";
    private static final String NAV_OBJ_LANGS = "nav.objectLangs";
    private static final String NAV_META_LANGS = "nav.metaLangs";
    private static final String NAV_TRAITS = "nav.traits";
    private static final String NAV_ANNOTATIONS = "nav.annotations";
    private static final String NAV_FIELDS = "nav.fields";
    private static final String NAV_GRAM_INFO = "nav.gramInfo";
    private static final String NAV_TRANS_TYPES = "nav.transTypes";
    private static final String NAV_NOTE_TYPES = "nav.noteTypes";
    private static final String NAV_RELATION_TYPES = "nav.relationTypes";
    private static final String NAV_FIELD_TYPES = "nav.fieldTypes";
    private static final String NAV_QUICK_ENTRY = "nav.quickEntry";

    /* ─── Header configuration nav keys ─── */
    private static final String NAV_CFG_DESC = "nav.cfgDesc";
    private static final String NAV_CFG_FIELD_DEFS = "nav.cfgFieldDefs";
    private static final String NAV_CFG_MANAGE_LANGS = "nav.cfgManageLangs";
    private static final String NAV_CFG_MANAGE_NOTE_TYPES =
        "nav.cfgManageNoteTypes";
    private static final String NAV_CFG_MANAGE_TRANS_TYPES =
        "nav.cfgManageTransTypes";
    private static final String NAV_CFG_MANAGE_ANNOTATION_TYPES =
        "nav.cfgManageAnnotationTypes";
    private static final String NAV_CFG_MANAGE_RELATION_TYPES =
        "nav.cfgManageRelationTypes";
    private static final String NAV_CFG_RANGE_PREFIX = "cfg.range."; // + rangeId for dynamic ranges
    private TreeItem<String> headerCfgNode; // kept for dynamic rebuild

    /* ─── State ─── */
    private final DictionaryService dictionaryService = new DictionaryService();
    private LiftDictionary currentDictionary;
    private String currentView = NAV_ENTRIES;
    private boolean ignoreNavSelectionEvents = false;

    /* ─── FXML nodes ─── */
    @FXML
    private TreeView<String> navTree;

    @FXML
    private StackPane tableContainer;

    @FXML
    private TextField searchField;

    @FXML
    private Label viewTitle;

    @FXML
    private Label tableCountLabel;

    @FXML
    private Label editEntryTitle;

    @FXML
    private Label editEntryCode;

    @FXML
    private VBox rightContent;

    @FXML
    private VBox editorContainer;

    @FXML
    private Menu recentMenu;

    @FXML
    private MenuBar menuBar;

    @FXML
    private SplitPane mainSplit;

    @FXML
    private Button addButton;

    @FXML
    private Button undoButton;

    @FXML
    private Button redoButton;

    @FXML
    private MenuItem undoMenuItem;

    @FXML
    private MenuItem redoMenuItem;

    /* ─── Undo/Redo ─── */
    private final UndoManager undoManager = new UndoManager();

    /* ─── Entry table (main view) ─── */
    private final TableView<LiftEntry> entryTable = new TableView<>();
    private final ObservableList<LiftEntry> baseEntries =
        FXCollections.observableArrayList();
    private final FilteredList<LiftEntry> filteredEntries = new FilteredList<>(
        baseEntries,
        e -> true
    );

    /* ─── Generic object tables ─── */
    private final TableView<LiftSense> senseTable = new TableView<>();
    private final TableView<LiftExample> exampleTable = new TableView<>();
    private final TableView<LiftVariant> variantTable = new TableView<>();
    private final TableView<LiftRelation> relationTable = new TableView<>();
    private final TableView<TraitRow> traitTable = new TableView<>();

    private record TraitRow(
        String parentType,
        String name,
        String value,
        long frequency
    ) {}

    private final TableView<LiftAnnotation> annotationTable = new TableView<>();
    private final TableView<LiftField> fieldTable = new TableView<>();
    private final TableView<MultiTextField> langFieldTable = new TableView<>();
    private final TableView<QuickEntryRow> quickEntryTable = new TableView<>();
    private List<LiftEntry> entrySubsetOverride = null;
    private String entrySubsetTitle = null;
    private boolean keepEntrySubsetOnNextEntryView = false;
    private List<LiftSense> senseSubsetOverride = null;
    private boolean keepSenseSubsetOnNextSenseView = false;

    /* ─── Wrapper for language field view ─── */
    public record MultiTextField(
        String parentType,
        String parentId,
        String lang,
        String text,
        Object parentObject,
        MultiText multiText
    ) {
        public MultiTextField(
            String parentType,
            String parentId,
            String lang,
            String text
        ) {
            this(parentType, parentId, lang, text, null, null);
        }
    }

    public static class QuickEntryRow {

        private final Map<String, javafx.beans.property.StringProperty> forms =
            new HashMap<>();
        private final Map<
            String,
            javafx.beans.property.StringProperty
        > glosses = new HashMap<>();
        private final javafx.beans.property.StringProperty gramInfo =
            new javafx.beans.property.SimpleStringProperty("");
        private final javafx.beans.property.BooleanProperty created =
            new javafx.beans.property.SimpleBooleanProperty(false);

        public javafx.beans.property.StringProperty formProperty(String lang) {
            return forms.computeIfAbsent(lang, k ->
                new javafx.beans.property.SimpleStringProperty("")
            );
        }

        public javafx.beans.property.StringProperty glossProperty(String lang) {
            return glosses.computeIfAbsent(lang, k ->
                new javafx.beans.property.SimpleStringProperty("")
            );
        }

        public javafx.beans.property.StringProperty gramInfoProperty() {
            return gramInfo;
        }

        public javafx.beans.property.BooleanProperty createdProperty() {
            return created;
        }
    }

    /**
     * TableCell that shows a TextField permanently and commits its value on
     * focus loss, Tab, and Enter (not only Enter like the default TextFieldTableCell).
     */
    private static class CommitOnFocusLossCell<S> extends TableCell<S, String> {

        private final TextField textField = new TextField();
        private final java.util.function.Function<
            S,
            javafx.beans.property.StringProperty
        > propertyAccessor;
        private javafx.beans.property.StringProperty boundProperty;

        CommitOnFocusLossCell(
            java.util.function.Function<
                S,
                javafx.beans.property.StringProperty
            > propertyAccessor
        ) {
            this.propertyAccessor = propertyAccessor;
            textField.setOnAction(e -> commitValue());
            textField
                .focusedProperty()
                .addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused) commitValue();
                });
        }

        private void commitValue() {
            if (boundProperty != null) boundProperty.set(
                textField.getText() != null ? textField.getText() : ""
            );
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (
                empty ||
                getTableRow() == null ||
                getTableRow().getItem() == null
            ) {
                setGraphic(null);
                boundProperty = null;
                return;
            }
            S rowItem = getTableRow().getItem();
            boundProperty = propertyAccessor.apply(rowItem);
            if (!textField.isFocused()) {
                textField.setText(boundProperty.get());
            }
            setGraphic(textField);
        }
    }

    /* ─────────────────────── INITIALIZATION ─────────────────────── */

    @FXML
    private void initialize() {
        buildNavTree();
        setupEntryTable();
        setupGenericTables();
        searchField
            .textProperty()
            .addListener((obs, o, n) -> applyCurrentFilter());
        ComponentDeletion.setUndoSink(cmd -> {
            undoManager.execute(cmd);
            markDirty();
        });
        installDirtyTracking();
        loadInitialDictionary();
        ensureRightPanelVisible();
        setupMenuHover();
        switchView(NAV_ENTRIES);
        refreshRecentMenu();
        setupUndoRedo();
        setupWindow();
    }

    /**
     * Picks the dictionary to show at startup: the one handed over by a scene reload
     * (language change), else the last file opened, else — only if no file was ever
     * opened — the demo dictionary.
     */
    private void loadInitialDictionary() {
        if (handoff != null) {
            Handoff h = handoff;
            handoff = null;
            setDictionary(h.dictionary());
            currentFile = h.file();
            demo = h.demo();
            dirty = h.dirty();
            return;
        }
        List<String> recents = loadRecentFiles();
        if (recents.isEmpty()) {
            setDictionary(loadDemoDictionary());
            demo = currentDictionary != null;
            return;
        }
        File lastFile = new File(recents.get(0));
        try {
            setDictionary(dictionaryService.loadFromFile(lastFile));
            currentFile = lastFile;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ouverture du dernier fichier LIFT", e);
            setDictionary(null);
            Platform.runLater(() -> showError(
                I18n.get(Keys.ERROR_OPEN),
                I18n.formatErrorMessage("error.open.detail", e)
            ));
        }
    }

    /* ─── Current file, unsaved changes, window title ─── */

    /** State handed over to the controller of a reloaded scene (language change). */
    private record Handoff(LiftDictionary dictionary, File file, boolean demo, boolean dirty) {}

    private static Handoff handoff;

    /** The file the dictionary was loaded from or last saved to; null for a new or demo dictionary. */
    private File currentFile;
    /** True while the bundled demo dictionary is shown. */
    private boolean demo;
    /** True when the dictionary has changes that are not saved. */
    private boolean dirty;
    /** The entry owning the object shown in the editor panel; its modification date is updated on edit. */
    private LiftEntry editedEntry;

    private void markDirty() {
        if (currentDictionary == null) return;
        if (!dirty) {
            dirty = true;
            updateWindowTitle();
        }
    }

    /** The entry an object belongs to (itself for an entry), or null (e.g. header objects). */
    private static LiftEntry owningEntry(Object o) {
        Object n = o;
        while (n instanceof AbstractLiftRoot r) {
            if (r instanceof LiftEntry e) return e;
            n = r.getParentNode();
        }
        return null;
    }

    private void updateWindowTitle() {
        if (menuBar == null || menuBar.getScene() == null) return;
        if (!(menuBar.getScene().getWindow() instanceof javafx.stage.Stage stage)) return;
        String name;
        if (currentDictionary == null) name = null;
        else if (demo) name = I18n.get("title.demo");
        else if (currentFile == null) name = I18n.get("title.untitled");
        else name = currentFile.getName();
        stage.setTitle(
            I18n.get("app.title") +
            (name == null ? "" : " — " + name) +
            (dirty ? " *" : "")
        );
    }

    /**
     * Marks the dictionary as modified when the user types or acts in the editor panel or
     * in a table view. Navigation controls (hyperlinks, nodes with the {@code no-dirty} style
     * class, such as filters and "back" buttons) are ignored. The modification date of the
     * entry being edited is updated at the same time.
     */
    private void installDirtyTracking() {
        javafx.event.EventHandler<javafx.event.Event> onEdit = e -> {
            if (!isEditEvent(e)) return;
            markDirty();
            if (editedEntry != null) {
                editedEntry.setDateModified(
                    java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
                );
            }
        };
        for (javafx.scene.layout.Pane container : List.<javafx.scene.layout.Pane>of(editorContainer, tableContainer)) {
            container.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, onEdit::handle);
            container.addEventFilter(javafx.event.ActionEvent.ACTION, onEdit::handle);
        }
    }

    private static boolean isEditEvent(javafx.event.Event e) {
        if (!(e.getTarget() instanceof javafx.scene.Node node)) return false;
        if (e instanceof javafx.scene.input.KeyEvent ke) {
            if (!(node instanceof javafx.scene.control.TextInputControl tic) || !tic.isEditable()) return false;
            String c = ke.getCharacter();
            // Tab, Enter and Escape move around; they do not change the text.
            if (c.isEmpty() || ke.isShortcutDown() || "\t\r\n\u001b".contains(c)) return false;
        } else if (!(node instanceof ButtonBase) && !node.isFocused()) {
            // Combo boxes and date pickers also fire actions when their value is set by code
            // (e.g. while an editor is filled); only the control the user operates has the focus.
            return false;
        }
        if (node instanceof Hyperlink) return false;
        for (javafx.scene.Node n = node; n != null; n = n.getParent()) {
            if (n.getStyleClass().contains("no-dirty")) return false;
        }
        return true;
    }

    /** Asks what to do with unsaved changes. Returns true when the caller may go on. */
    private boolean confirmDiscardChanges() {
        if (!dirty) return true;
        ButtonType save = new ButtonType(I18n.get("btn.save"), ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType(I18n.get("btn.discard"), ButtonBar.ButtonData.NO);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, I18n.get("confirm.unsavedChanges"), save, discard, ButtonType.CANCEL);
        a.setTitle(I18n.get("confirm.unsavedChangesTitle"));
        a.setHeaderText(null);
        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty() || r.get() == ButtonType.CANCEL) return false;
        if (r.get() == save) {
            onSave();
            return !dirty;
        }
        return true;
    }

    private void setupWindow() {
        if (menuBar == null) return;
        menuBar.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            applyFontSize(Integer.parseInt(PREFS.get("ui.fontSize", "13")));
            applyTheme(PREFS.get("ui.theme", "light"));
            newScene.windowProperty().addListener((o2, oldW, newW) -> {
                if (newW == null) return;
                newW.setOnCloseRequest(ev -> {
                    if (!confirmDiscardChanges()) ev.consume();
                });
                updateWindowTitle();
            });
            if (newScene.getWindow() != null) {
                newScene.getWindow().setOnCloseRequest(ev -> {
                    if (!confirmDiscardChanges()) ev.consume();
                });
                updateWindowTitle();
            }
        });
    }

    /** Opens a LIFT file. The caller has already asked what to do with unsaved changes. */
    private void openDictionaryFile(File f) {
        try {
            setDictionary(dictionaryService.loadFromFile(f));
            currentFile = f;
            demo = false;
            dirty = false;
            updateWindowTitle();
            switchView(NAV_ENTRIES);
            saveRecentFile(f);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ouverture du fichier LIFT", e);
            showError(
                I18n.get(Keys.ERROR_OPEN),
                I18n.formatErrorMessage("error.open.detail", e)
            );
        }
    }

    private void setupUndoRedo() {
        if (undoButton != null) {
            undoButton.setGraphic(Icons.undoIcon());
            undoButton.setTooltip(
                new Tooltip(I18n.get(Keys.MENU_EDIT_UNDO) + " (" +
                    new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN).getDisplayText() + ")")
            );
        }
        if (redoButton != null) {
            redoButton.setGraphic(Icons.redoIcon());
            redoButton.setTooltip(
                new Tooltip(I18n.get(Keys.MENU_EDIT_REDO) + " (" +
                    new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN).getDisplayText() + ")")
            );
        }
        if (undoButton != null) undoButton
            .disableProperty()
            .bind(undoManager.canUndoProperty().not());
        if (redoButton != null) redoButton
            .disableProperty()
            .bind(undoManager.canRedoProperty().not());
        if (undoMenuItem != null) undoMenuItem
            .disableProperty()
            .bind(undoManager.canUndoProperty().not());
        if (redoMenuItem != null) redoMenuItem
            .disableProperty()
            .bind(undoManager.canRedoProperty().not());
        if (menuBar != null) {
            menuBar.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) installUndoRedoAccelerators(newScene);
            });
            if (menuBar.getScene() != null) installUndoRedoAccelerators(
                menuBar.getScene()
            );
        }
    }

    private void installUndoRedoAccelerators(javafx.scene.Scene scene) {
        scene
            .getAccelerators()
            .put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN),
                this::onUndo
            );
        scene
            .getAccelerators()
            .put(
                new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN),
                this::onRedo
            );
    }

    private void setupMenuHover() {
        if (menuBar == null) return;
        // Nodes are not yet in the scene graph at initialize() time; install after layout.
        menuBar.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            Platform.runLater(() -> {
                for (Menu menu : menuBar.getMenus()) {
                    javafx.scene.Node btn = menu.getStyleableNode();
                    if (btn == null) continue;
                    btn.setOnMouseEntered(e -> {
                        boolean anyOpen = menuBar
                            .getMenus()
                            .stream()
                            .anyMatch(Menu::isShowing);
                        if (anyOpen) {
                            menuBar.getMenus().forEach(Menu::hide);
                            menu.show();
                        }
                    });
                }
            });
        });
    }

    /* ─── Navigation tree (5.7.2) ─── */

    private void buildNavTree() {
        navKeyMap.clear();

        TreeItem<String> root = new TreeItem<>(I18n.get(Keys.NAV_DICTIONARY));
        root.setExpanded(true);

        TreeItem<String> objects = new TreeItem<>(I18n.get(Keys.NAV_OBJECTS));
        objects.setExpanded(true);
        objects
            .getChildren()
            .addAll(
                navItem(NAV_ENTRIES),
                navItem(NAV_SENSES),
                navItem(NAV_EXAMPLES),
                navItem(NAV_NOTES),
                navItem(NAV_VARIANTS),
                navItem(NAV_ETYMOLOGIES),
                navItem(NAV_RELATIONS),
                navItem(NAV_FIELDS)
            );

        TreeItem<String> langs = new TreeItem<>(I18n.get(Keys.NAV_LANGUAGES));
        langs.setExpanded(true);
        langs
            .getChildren()
            .addAll(navItem(NAV_OBJ_LANGS), navItem(NAV_META_LANGS));

        TreeItem<String> cats = new TreeItem<>(I18n.get(Keys.NAV_CATEGORIES));
        cats.setExpanded(true);
        cats.getChildren().addAll(
            navItem(NAV_GRAM_INFO),
            navItem(NAV_TRAITS),
            navItem(NAV_ANNOTATIONS),
            navItem(NAV_TRANS_TYPES),
            navItem(NAV_NOTE_TYPES),
            navItem(NAV_RELATION_TYPES),
            navItem(NAV_FIELD_TYPES)
        );

        headerCfgNode = new TreeItem<>(I18n.get(Keys.NAV_HEADER_CONFIG));
        headerCfgNode.setExpanded(true);
        rebuildHeaderCfgChildren();

        TreeItem<String> quick = navItem(NAV_QUICK_ENTRY);

        root.getChildren().addAll(objects, langs, cats, headerCfgNode, quick);
        navTree.setRoot(root);
        navTree.setShowRoot(false);

        navTree
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, oldV, newV) -> {
                if (ignoreNavSelectionEvents) return;
                if (newV != null && newV.isLeaf()) {
                    String key = navKeyMap.get(newV);
                    if (key != null) switchView(key);
                }
            });

        navTree.setCellFactory(tv -> {
            TreeCell<String> cell = new TreeCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            ContextMenu ctx = new ContextMenu();
            MenuItem createItem = new MenuItem(
                I18n.get(Keys.BTN_CREATE_OBJECT)
            );
            createItem.setOnAction(e -> onCreateNewObject());
            ctx.getItems().add(createItem);
            cell.setContextMenu(ctx);
            return cell;
        });
    }

    private final Map<TreeItem<String>, String> navKeyMap = new HashMap<>();

    private TreeItem<String> navItem(String i18nKey) {
        TreeItem<String> item = new TreeItem<>(I18n.get(i18nKey));
        navKeyMap.put(item, i18nKey);
        return item;
    }

    /** Dynamic nav item with explicit label (for ranges whose id is the label). */
    private TreeItem<String> navItemDynamic(String key, String label) {
        TreeItem<String> item = new TreeItem<>(label);
        navKeyMap.put(item, key);
        return item;
    }

    /**
     * (Re-)builds the children of the "Configuration du dictionnaire" nav node.
     * Structure: Dictionary description, Field and traits definitions (separated),
     * then a "Ranges" section grouping all dynamic ranges.
     */
    private void rebuildHeaderCfgChildren() {
        if (headerCfgNode == null) return;
        // Remove old items from navKeyMap (including nested range children)
        headerCfgNode.getChildren().forEach(this::removeFromNavKeyMapRecursive);
        headerCfgNode.getChildren().clear();

        // First group: Dictionary description and Field definitions (separated)
        headerCfgNode.getChildren().add(navItem(NAV_CFG_DESC));
        headerCfgNode.getChildren().add(navItem(NAV_CFG_FIELD_DEFS));

        // Manage X entries (open config dialogs)
        headerCfgNode.getChildren().add(navItem(NAV_CFG_MANAGE_LANGS));
        headerCfgNode.getChildren().add(navItem(NAV_CFG_MANAGE_NOTE_TYPES));
        headerCfgNode.getChildren().add(navItem(NAV_CFG_MANAGE_TRANS_TYPES));
        headerCfgNode
            .getChildren()
            .add(navItem(NAV_CFG_MANAGE_ANNOTATION_TYPES));
        headerCfgNode.getChildren().add(navItem(NAV_CFG_MANAGE_RELATION_TYPES));

        // Second group: Ranges (Taxinomies) - parent node with dynamic range entries as children
        TreeItem<String> rangesNode = new TreeItem<>(
            I18n.get(Keys.NAV_CFG_RANGES)
        );
        rangesNode.setExpanded(false);
        if (currentDictionary != null) {
            LiftHeader header = currentDictionary
                .getHeader();
            if (header != null) {
                for (FeatureSet range : header.getFeatureSets()) {
                    String key = NAV_CFG_RANGE_PREFIX + range.getId();
                    String label = range
                        .getLabel()
                        .getForms()
                        .stream()
                        .findFirst()
                        .map(Form::toPlainText)
                        .orElse(range.getId());
                    rangesNode.getChildren().add(navItemDynamic(key, label));
                }
            }
        }
        headerCfgNode.getChildren().add(rangesNode);
        headerCfgNode.setExpanded(true);
    }

    private void removeFromNavKeyMapRecursive(TreeItem<String> item) {
        navKeyMap.remove(item);
        item.getChildren().forEach(this::removeFromNavKeyMapRecursive);
    }

    /* ─── View switching ─── */

    private void switchView(String viewName) {
        if (NAV_ENTRIES.equals(viewName)) {
            if (!keepEntrySubsetOnNextEntryView) {
                entrySubsetOverride = null;
                entrySubsetTitle = null;
            }
            keepEntrySubsetOnNextEntryView = false;
        }
        if (NAV_SENSES.equals(viewName)) {
            if (!keepSenseSubsetOnNextSenseView) {
                senseSubsetOverride = null;
            }
            keepSenseSubsetOnNextSenseView = false;
        }
        currentView = viewName;
        editedEntry = null;
        detachSearchListener();
        ensureRightPanelVisible();
        String title = viewName.startsWith(NAV_CFG_RANGE_PREFIX)
            ? viewName.substring(NAV_CFG_RANGE_PREFIX.length())
            : I18n.get(viewName);
        viewTitle.setText(title);
        editorContainer.getChildren().clear();
        editEntryTitle.setText(I18n.get("panel.selectElement"));
        editEntryCode.setText("");
        tableContainer.getChildren().clear();
        addButton.setText(I18n.get(Keys.BTN_NEW));
        boolean showAddButton =
            NAV_ENTRIES.equals(viewName) || NAV_QUICK_ENTRY.equals(viewName);
        addButton.setVisible(showAddButton);
        addButton.setManaged(showAddButton);

        if (viewName.startsWith(NAV_CFG_RANGE_PREFIX)) {
            setRightPanelVisible(true);
            showHeaderRangeView(
                viewName.substring(NAV_CFG_RANGE_PREFIX.length())
            );
            selectNavItem(viewName);
            return;
        }
        switch (viewName) {
            case NAV_ENTRIES -> showEntryView();
            case NAV_SENSES -> showSenseView();
            case NAV_EXAMPLES -> showExampleView();
            case NAV_NOTES -> showNoteView();
            case NAV_VARIANTS -> showVariantView();
            case NAV_ETYMOLOGIES -> showEtymologyView();
            case NAV_RELATIONS -> showRelationView();
            case NAV_OBJ_LANGS -> showLangFieldView(true);
            case NAV_META_LANGS -> showLangFieldView(false);
            case NAV_TRAITS -> showTraitView();
            case NAV_ANNOTATIONS -> showAnnotationView();
            case NAV_FIELDS -> showFieldView();
            case NAV_GRAM_INFO -> showGramInfoView();
            case NAV_TRANS_TYPES -> showTranslationTypesView();
            case NAV_NOTE_TYPES -> showNoteTypesView();
            case NAV_RELATION_TYPES -> showRelationTypesView();
            case NAV_FIELD_TYPES -> showFieldTypesView();
            case NAV_QUICK_ENTRY -> showQuickEntryView();
            case NAV_CFG_DESC -> {
                showHeaderDescView();
                setRightPanelVisible(false);
            }
            case NAV_CFG_FIELD_DEFS -> showHeaderFieldDefsView();
            case NAV_CFG_MANAGE_LANGS -> {
                showManageLanguagesView();
                setRightPanelVisible(false);
            }
            case NAV_CFG_MANAGE_NOTE_TYPES -> {
                showConfigNoteTypesView();
                setRightPanelVisible(false);
            }
            case NAV_CFG_MANAGE_TRANS_TYPES -> {
                showConfigTranslationTypesView();
                setRightPanelVisible(false);
            }
            case NAV_CFG_MANAGE_ANNOTATION_TYPES -> {
                showConfigAnnotationTypesView();
                setRightPanelVisible(false);
            }
            case NAV_CFG_MANAGE_RELATION_TYPES -> {
                showConfigRelationTypesView();
                setRightPanelVisible(false);
            }
            default -> showEntryView();
        }
        boolean hideRightPanel =
            NAV_CFG_DESC.equals(viewName) ||
            NAV_CFG_MANAGE_LANGS.equals(viewName) ||
            NAV_CFG_MANAGE_NOTE_TYPES.equals(viewName) ||
            NAV_CFG_MANAGE_TRANS_TYPES.equals(viewName) ||
            NAV_CFG_MANAGE_ANNOTATION_TYPES.equals(viewName) ||
            NAV_CFG_MANAGE_RELATION_TYPES.equals(viewName);
        if (!hideRightPanel) setRightPanelVisible(true);
        // Hide search field in quick entry view (not needed there)
        if (searchField != null) {
            boolean showSearch = !NAV_QUICK_ENTRY.equals(viewName);
            searchField.setVisible(showSearch);
            searchField.setManaged(showSearch);
        }
        selectNavItem(viewName);
    }

    /**
     * Runs a deletion and records it for undo. lift-api refuses to delete an object that
     * other objects refer to: the user is told, and nothing is recorded.
     */
    private void executeDeletion(fr.cnrs.lacito.liftgui.undo.UndoableCommand cmd) {
        try {
            cmd.redo();
        } catch (IllegalStateException e) {
            showError(I18n.get("btn.delete"), I18n.get("error.deleteReferenced", e.getMessage()));
            return;
        }
        undoManager.execute(cmd);
        markDirty();
    }

    /** Rebuilds the current view in place (e.g. after an undo), keeping its subset of objects. */
    private void refreshCurrentView() {
        keepEntrySubsetOnNextEntryView = true;
        keepSenseSubsetOnNextSenseView = true;
        switchView(currentView);
    }

    @FXML
    private void onUndo() {
        if (!undoManager.canUndo()) return;
        undoManager.undo();
        markDirty();
    }

    @FXML
    private void onRedo() {
        if (!undoManager.canRedo()) return;
        undoManager.redo();
        markDirty();
    }

    private boolean splitConstraintInstalled = false;

    private void setRightPanelVisible(boolean visible) {
        if (mainSplit != null && mainSplit.getItems().size() >= 2) {
            javafx.scene.Node rightPane = mainSplit.getItems().get(1);
            rightPane.setManaged(visible);
            rightPane.setVisible(visible);
        }
    }

    private void ensureRightPanelVisible() {
        if (rightContent != null) {
            rightContent.setManaged(true);
            rightContent.setVisible(true);
        }
        if (
            mainSplit != null &&
            mainSplit.getItems().size() >= 2 &&
            !splitConstraintInstalled
        ) {
            splitConstraintInstalled = true;
            // Enforce min widths so neither pane can completely disappear.
            javafx.scene.Node leftPane = mainSplit.getItems().get(0);
            javafx.scene.Node rightPane = mainSplit.getItems().get(1);
            if (leftPane instanceof Region r) r.setMinWidth(250);
            if (rightPane instanceof Region r) r.setMinWidth(300);

            // Clamp the divider so the right panel always stays visible.
            SplitPane.Divider divider = mainSplit.getDividers().get(0);
            divider.positionProperty().addListener((obs, oldPos, newPos) -> {
                double total = mainSplit.getWidth();
                if (total <= 0) return;
                double minRight = 300.0;
                double maxPosition = 1.0 - minRight / total;
                double minLeft = 250.0;
                double minPosition = minLeft / total;
                double clamped = Math.max(
                    minPosition,
                    Math.min(maxPosition, newPos.doubleValue())
                );
                if (Math.abs(clamped - newPos.doubleValue()) > 0.001) {
                    Platform.runLater(() -> divider.setPosition(clamped));
                }
            });
        }
    }

    /* ════════════════════ ENTRY VIEW ════════════════════ */

    private void setupEntryTable() {
        SortedList<LiftEntry> sorted = new SortedList<>(filteredEntries);
        sorted.comparatorProperty().bind(entryTable.comparatorProperty());
        entryTable.setItems(sorted);
        entryTable
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (
                    n != null && currentView.equals(NAV_ENTRIES)
                ) populateEntryEditor(n);
            });
    }

    private void showEntryView() {
        addButton.setText(I18n.get(Keys.BTN_NEW_ENTRY));
        GridPane filterRow = buildEntryFilterRow();
        String clearOption = I18n.get("filter.clear");

        Button clearBtn = new Button(I18n.get("filter.resetAll"));
        clearBtn.setOnAction(e -> {
            entryFilterInternalUpdate = true;
            try {
                entryColumnFilters.forEach(cb -> cb.setValue(clearOption));
            } finally {
                entryFilterInternalUpdate = false;
            }
            applyCurrentFilter();
            entryTable.getSelectionModel().clearSelection();
        });
        HBox header = new HBox();
        header.getStyleClass().add("no-dirty");
        header.setPadding(new Insets(0, 6, 4, 6));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, clearBtn);

        VBox wrapper = new VBox(header, filterRow, entryTable);
        wrapper.setMinWidth(0);
        filterRow.setMinWidth(0);
        entryTable.setMinWidth(0);
        VBox.setVgrow(entryTable, Priority.ALWAYS);
        tableContainer.getChildren().setAll(wrapper);
        applyCurrentFilter();
        if (entrySubsetTitle != null) viewTitle.setText(entrySubsetTitle);
        if (!filteredEntries.isEmpty()) {
            entryTable.getSelectionModel().selectFirst();
            LiftEntry selected = entryTable
                .getSelectionModel()
                .getSelectedItem();
            if (selected != null) populateEntryEditor(selected);
        }
        int total =
            entrySubsetOverride != null
                ? entrySubsetOverride.size()
                : baseEntries.size();
        updateCountLabel(filteredEntries.size(), total);
    }

    private final List<ComboBox<String>> entryColumnFilters = new ArrayList<>();
    private boolean entryFilterInternalUpdate = false;

    private GridPane buildEntryFilterRow() {
        entryColumnFilters.clear();
        GridPane row = new GridPane();
        row.getStyleClass().add("no-dirty");
        row.setHgap(0);
        row.setPadding(new Insets(2, 0, 2, 0));
        row.setStyle("-fx-background-color: #eef2f3;");
        row.setMinWidth(0);
        String clearOption = I18n.get(Keys.FILTER_CLEAR);
        List<TableColumn<LiftEntry, ?>> leaves = collectLeafColumns(entryTable);

        for (int i = 0; i < leaves.size(); i++) {
            TableColumn<LiftEntry, ?> col = leaves.get(i);
            ComboBox<String> cb = new ComboBox<>();
            cb.getStyleClass().add("filter-combo");
            cb.setPromptText(I18n.get("filter.prompt"));
            cb.setEditable(false);
            cb.setMaxWidth(Double.MAX_VALUE);
            cb.setMinWidth(0);
            cb.setMinHeight(26);
            cb.setPrefHeight(26);
            cb.setStyle("-fx-font-size: 11px;");
            cb.setCellFactory(list ->
                new ListCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setDisable(false);
                            setStyle("");
                            return;
                        }
                        setText(item);
                        boolean isClearItem = clearOption.equals(item);
                        boolean activeFilter = isActiveFilter(
                            cb.getValue(),
                            clearOption
                        );
                        boolean disableClear = isClearItem && !activeFilter;
                        setDisable(disableClear);
                        setStyle(disableClear ? "-fx-opacity: 0.45;" : "");
                    }
                }
            );
            cb.setButtonCell(
                new ListCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item);
                    }
                }
            );
            cb.valueProperty().addListener((obs, o, n) -> {
                if (clearOption.equals(n) && !isActiveFilter(o, clearOption)) {
                    cb.setValue(o);
                    return;
                }
                if (entryFilterInternalUpdate) return;
                applyCurrentFilter();
            });
            entryColumnFilters.add(cb);

            ColumnConstraints cc = new ColumnConstraints();
            cc.prefWidthProperty().bind(col.widthProperty());
            row.getColumnConstraints().add(cc);
            GridPane.setHgrow(cb, Priority.ALWAYS);
            row.add(cb, i, 0);
        }

        entryFilterInternalUpdate = true;
        try {
            entryColumnFilters.forEach(cb -> cb.setValue(clearOption));
        } finally {
            entryFilterInternalUpdate = false;
        }

        // Aligner la largeur des filtres sur la zone des colonnes (prend en compte la scrollbar verticale)
        row.maxWidthProperty().bind(
            Bindings.createDoubleBinding(
                () ->
                    leaves
                        .stream()
                        .mapToDouble(c -> c.getWidth())
                        .sum(),
                leaves
                    .stream()
                    .<javafx.beans.Observable>map(col -> col.widthProperty())
                    .toArray(javafx.beans.Observable[]::new)
            )
        );
        return row;
    }

    private void configureEntryTableColumns() {
        entryTable.getColumns().clear();
        entryTable.setEditable(true); // ← active l'édition sur la table
        if (currentDictionary == null) return;

        // ── Colonnes Formes par langue ──
        var formLangs = java.util.stream.Stream.concat(
                currentDictionary.getObjectLanguageManager().getLanguages().stream(),
                currentDictionary
                    .getLiftDictionaryRegistry()
                    .getEntries()
                    .stream()
                    .flatMap(e -> e.getForms().getLangs().stream())
            )
            .filter(s -> s != null && !s.isBlank())
            .distinct()
            .sorted()
            .toList();

        TableColumn<LiftEntry, String> formGroup = new TableColumn<>(
            I18n.get(Keys.COL_FORM)
        );
        for (String lang : formLangs) {
            TableColumn<LiftEntry, String> c = new TableColumn<>(lang);
            c.setMinWidth(85);
            c.setPrefWidth(140);
            c.setCellValueFactory(cd ->
                cd.getValue() == null
                    ? new ReadOnlyStringWrapper("")
                    : cd.getValue().getForms().formTextProperty(lang)
            );
            c.setCellFactory(TextFieldTableCell.forTableColumn());
            c.setOnEditCommit(ev -> {
                LiftEntry e = ev.getRowValue();
                if (e == null) return;
                // An empty text removes the form, as in the editors.
                e.getForms().formTextProperty(lang).set(ev.getNewValue() == null ? "" : ev.getNewValue());
            });
            formGroup.getColumns().add(c);
        }

        // ── Colonne morph-type (trait "morph-type") ──
        TableColumn<LiftEntry, String> morphCol = new TableColumn<>(
            I18n.get(Keys.COL_MORPH_TYPE)
        );
        morphCol.setMinWidth(85);
        morphCol.setPrefWidth(110);
        morphCol.setCellValueFactory(cd -> {
            LiftEntry e = cd.getValue();
            return e == null
                ? new ReadOnlyStringWrapper("")
                : Bindings.createStringBinding(
                      () -> getTraitValue(e, Keys.MORPH_TYPE),
                      e.traitsProperty()
                  );
        });
        morphCol.setCellFactory(TextFieldTableCell.forTableColumn());
        morphCol.setOnEditCommit(ev -> {
            LiftEntry e = ev.getRowValue();
            if (e == null) return;
            String value = ev.getNewValue() == null ? "" : ev.getNewValue().trim();
            Optional<LiftTrait> existing = e.getTraits()
                .stream()
                .filter(t -> Keys.MORPH_TYPE.equals(t.getSpecification().getName()))
                .findFirst();
            if (value.isEmpty()) {
                existing.ifPresent(e::deleteTrait);
            } else if (existing.isPresent()) {
                existing.get().setValue(value);
            } else {
                currentDictionary.getComponentBuilder().trait(e, Keys.MORPH_TYPE, value).build();
            }
        });

        // ── Colonne Date (lecture seule) ──
        TableColumn<LiftEntry, String> dateCol = new TableColumn<>(
            I18n.get(Keys.COL_DATE_CREATED)
        );
        dateCol.setMinWidth(85);
        dateCol.setPrefWidth(130);
        dateCol.setCellValueFactory(cd ->
            new ReadOnlyStringWrapper(
                cd.getValue() == null
                    ? ""
                    : cd.getValue().getDateCreated().orElse("")
            )
        );

        entryTable.getColumns().addAll(formGroup, morphCol, dateCol);
    }

    /* ════════════════════ SENSE VIEW ════════════════════ */

    private void showSenseView() {
        senseTable.setItems(FXCollections.observableArrayList());
        senseTable.getColumns().clear();
        if (currentDictionary == null) {
            tableContainer.getChildren().setAll(senseTable);
            return;
        }

        Set<String> objLangs = currentDictionary.getObjectLanguageManager().getLanguages();
        Set<String> metaLangs = currentDictionary.getMetaLanguageManager().getLanguages();

        // Colonne "Entrée parente" : forme(s) de l'entrée dont c'est le sens
        TableColumn<LiftSense, String> parentEntryGroup = new TableColumn<>(
            I18n.get(Keys.COL_PARENT_ENTRY)
        );
        for (String l : objLangs) {
            final String lang = l;
            TableColumn<LiftSense, String> c = col(l, s -> {
                LiftEntry parent = s.getParentEntry();
                return parent != null
                    ? parent
                          .getForms()
                          .getForm(lang)
                          .map(Form::toPlainText)
                          .orElse("")
                    : "";
            });
            c.setPrefWidth(120);
            parentEntryGroup.getColumns().add(c);
        }
        TableColumn<LiftSense, String> giCol = col(
            I18n.get(Keys.COL_GRAM_INFO),
            s ->
                s.getGrammaticalInfo().map(x -> x.getGramInfoValue().getId()).orElse("")
        );
        TableColumn<LiftSense, String> glossGroup = new TableColumn<>(
            I18n.get(Keys.COL_GLOSS)
        );
        for (String l : metaLangs) {
            glossGroup
                .getColumns()
                .add(
                    col(l, s ->
                        s
                            .getGlosses()
                            .getForm(l)
                            .map(Form::toPlainText)
                            .orElse("")
                    )
                );
        }
        TableColumn<LiftSense, String> defGroup = new TableColumn<>(
            I18n.get(Keys.COL_DEFINITION)
        );
        for (String l : metaLangs) {
            defGroup
                .getColumns()
                .add(
                    col(l, s ->
                        s
                            .getDefinition()
                            .getForm(l)
                            .map(Form::toPlainText)
                            .orElse("")
                    )
                );
        }
        senseTable
            .getColumns()
            .addAll(parentEntryGroup, giCol, glossGroup, defGroup);
        List<LiftSense> sensesToShow =
            senseSubsetOverride != null
                ? senseSubsetOverride
                : currentDictionary
                      .getLiftDictionaryRegistry()
                      .getSenses();
        senseTable.getItems().addAll(sensesToShow);
        tableContainer
            .getChildren()
            .setAll(
                wrapTableWithFilters(
                    senseTable,
                    (f, t) -> updateCountLabel(f, t),
                    searchField != null ? searchField.textProperty() : null
                )
            );
        updateCountLabel(
            senseTable.getItems().size(),
            senseTable.getItems().size()
        );
    }

    /* ════════════════════ EXAMPLE VIEW ════════════════════ */

    private void showExampleView() {
        exampleTable.setItems(FXCollections.observableArrayList());
        exampleTable.getColumns().clear();
        if (currentDictionary == null) {
            tableContainer.getChildren().setAll(exampleTable);
            return;
        }

        Set<String> objLangs = currentDictionary.getObjectLanguageManager().getLanguages();
        Set<String> metaLangs = currentDictionary.getMetaLanguageManager().getLanguages();
        Set<Feature> transTypes = currentDictionary.getHeader().getTranslationTypeManager().getFeatures().values().stream().collect(Collectors.toSet());

        // 1. Sens parent (multitexte : glose du sens parent)
        TableColumn<LiftExample, String> parentSenseGroup = new TableColumn<>(
            I18n.get(Keys.COL_PARENT_SENSE)
        );
        for (String l : metaLangs) {
            final String lang = l;
            parentSenseGroup.getColumns().add(
                col(l, ex -> {
                    LiftSense parent = ex.getParent();
                    if (parent == null) return "";
                    // Gloss = forme principale du sens ; sinon première forme disponible
                    MultiText gloss = parent.getGlosses();
                    return gloss
                        .getForm(lang)
                        .map(Form::toPlainText)
                        .or(() ->
                            gloss
                                .getForms()
                                .stream()
                                .findFirst()
                                .map(Form::toPlainText)
                        )
                        .orElse("");
                })
            );
        }
        exampleTable.getColumns().add(parentSenseGroup);

        // 2. Source
        TableColumn<LiftExample, String> srcCol = col(
            I18n.get(Keys.COL_SOURCE),
            ex -> ex.getSource().orElse("")
        );
        exampleTable.getColumns().add(srcCol);

        // 3. Exemple (langues objet)
        TableColumn<LiftExample, String> exGroup = new TableColumn<>(
            I18n.get(Keys.COL_EXAMPLE)
        );
        for (String l : objLangs) {
            exGroup
                .getColumns()
                .add(
                    col(l, ex ->
                        ex
                            .getExample()
                            .getForm(l)
                            .map(Form::toPlainText)
                            .orElse("")
                    )
                );
        }
        exampleTable.getColumns().add(exGroup);

        // 4. Traductions : un groupe par type de traduction, sous-colonnes par langue méta
        for (String transType : transTypes.stream().map(Feature::getId).sorted().toList()) {
            TableColumn<LiftExample, String> transGroup = new TableColumn<>(
                transType.isEmpty() ? I18n.get(Keys.COL_TRANSLATION) : transType
            );
            for (String l : metaLangs) {
                final String lang = l;
                final String type = transType;
                transGroup.getColumns().add(
                    col(l, ex -> {
                        MultiText mt = translationOfType(ex, type);
                        return mt != null
                            ? mt.getForm(lang).map(Form::toPlainText).orElse("")
                            : "";
                    })
                );
            }
            exampleTable.getColumns().add(transGroup);
        }

        exampleTable
            .getItems()
            .addAll(
                currentDictionary.getLiftDictionaryRegistry().getExamples()
            );
        tableContainer
            .getChildren()
            .setAll(
                wrapTableWithFilters(
                    exampleTable,
                    (f, t2) -> updateCountLabel(f, t2),
                    searchField != null ? searchField.textProperty() : null
                )
            );
        updateCountLabel(
            exampleTable.getItems().size(),
            exampleTable.getItems().size()
        );
    }

    /* ════════════════════ NOTE VIEW ════════════════════ */

    private void showNoteView() {
        TableView<LiftNote> noteTable = new TableView<>();
        if (currentDictionary == null) {
            tableContainer.getChildren().setAll(noteTable);
            return;
        }
        List<String> metaLangs = new ArrayList<>(currentDictionary.getMetaLanguageManager().getLanguages());
        TableColumn<LiftNote, String> parentTypeCol = col(
            I18n.get(Keys.COL_PARENT_TYPE),
            n -> describeParentType(n.getParent())
        );
        TableColumn<LiftNote, String> typeCol = col(
            I18n.get(Keys.COL_TYPE),
            x -> x.getType().getId()
        );
        TableColumn<LiftNote, String> textGroup = new TableColumn<>(
            I18n.get(Keys.COL_TEXT)
        );
        for (String l : metaLangs) {
            TableColumn<LiftNote, String> c = col(l, n ->
                n.getText().getForm(l).map(Form::toPlainText).orElse("")
            );
            c.getProperties().put("filterMode", FILTER_MODE_TEXT);
            textGroup.getColumns().add(c);
        }
        noteTable.getColumns().addAll(parentTypeCol, typeCol, textGroup);
        noteTable
            .getItems()
            .addAll(
                currentDictionary.getLiftDictionaryRegistry().getNotes()
            );
        noteTable
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (n != null) populateNoteEditor(n);
            });
        tableContainer
            .getChildren()
            .setAll(
                wrapTableWithFilters(
                    noteTable,
                    (f, t2) -> updateCountLabel(f, t2),
                    searchField != null ? searchField.textProperty() : null
                )
            );
        updateCountLabel(
            noteTable.getItems().size(),
            noteTable.getItems().size()
        );
    }

    /* ════════════════════ VARIANT VIEW ════════════════════ */

    private void showVariantView() {
        variantTable.setItems(FXCollections.observableArrayList());
        variantTable.getColumns().clear();
        if (currentDictionary == null) {
            tableContainer.getChildren().setAll(variantTable);
            return;
        }
        Set<String> objLangs = currentDictionary.getObjectLanguageManager().getLanguages();
        TableColumn<LiftVariant, String> parentFormGroup = new TableColumn<>(
            I18n.get(Keys.COL_PARENT_ENTRY)
        );
        for (String l : objLangs) {
            parentFormGroup
                .getColumns()
                .add(
                    col(l, v ->
                        v.getParent() != null
                            ? v
                                  .getParent()
                                  .getForms()
                                  .getForm(l)
                                  .map(Form::toPlainText)
                                  .orElse("")
                            : ""
                    )
                );
        }
        TableColumn<LiftVariant, String> variantTypeCol = col(
            "variant-type",
            v -> getTraitValueFor(v, "variant-type")
        );
        TableColumn<LiftVariant, String> isPrimaryCol = col("is-primary", v ->
            getTraitValueFor(v, "is-primary")
        );
        TableColumn<LiftVariant, String> refCol = col(
            I18n.get(Keys.COL_REF),
            v -> v.getRefId().orElse("")
        );
        TableColumn<LiftVariant, String> formGroup = new TableColumn<>(
            I18n.get(Keys.COL_FORMS)
        );
        for (String l : objLangs)
            formGroup
                .getColumns()
                .add(
                    col(l, v ->
                        v
                            .getForms()
                            .getForm(l)
                            .map(Form::toPlainText)
                            .orElse("")
                    )
                );
        variantTable
            .getColumns()
            .addAll(
                parentFormGroup,
                variantTypeCol,
                isPrimaryCol,
                refCol,
                formGroup
            );
        variantTable
            .getItems()
            .addAll(
                currentDictionary.getLiftDictionaryRegistry().getVariants()
            );
        tableContainer
            .getChildren()
            .setAll(
                wrapTableWithFilters(
                    variantTable,
                    (f, t2) -> updateCountLabel(f, t2),
                    searchField != null ? searchField.textProperty() : null
                )
            );
        updateCountLabel(
            variantTable.getItems().size(),
            variantTable.getItems().size()
        );
    }

    /* ════════════════════ RELATION VIEW ════════════════════ */

    private void showRelationView() {
        relationTable.setItems(FXCollections.observableArrayList());
        relationTable.getColumns().clear();
        if (currentDictionary == null) {
            tableContainer.getChildren().setAll(relationTable);
            return;
        }
        Set<String> objLangs = currentDictionary.getObjectLanguageManager().getLanguages();
        Set<String> metaLangs = currentDictionary.getMetaLanguageManager().getLanguages();
        var entryById = currentDictionary
            .getLiftDictionaryRegistry()
            .getEntriesById();
        TableColumn<LiftRelation, String> parentFormGroup = new TableColumn<>(
            I18n.get(Keys.COL_PARENT_ENTRY)
        );
        for (String l : objLangs) {
            parentFormGroup.getColumns().add(
                col(l, r -> {
                    MultiText forms = getParentEntryForms(r);
                    return forms != null
                        ? forms.getForm(l).map(Form::toPlainText).orElse("")
                        : "";
                })
            );
        }
        TableColumn<LiftRelation, String> typeCol = col(
            I18n.get(Keys.COL_TYPE),
            (LiftRelation r) -> r.getType().getId()
        );
        TableColumn<LiftRelation, String> refFormGroup = new TableColumn<>(
            I18n.get("col.refForm")
        );
        for (String l : objLangs) {
            refFormGroup.getColumns().add(
                col(l, r -> {
                    String refId = r.getRefID().orElse("");
                    if (refId.isBlank()) return "";
                    LiftEntry pointed =
                        entryById != null ? entryById.get(refId) : null;
                    return pointed != null
                        ? pointed
                              .getForms()
                              .getForm(l)
                              .map(Form::toPlainText)
                              .orElse("")
                        : "";
                })
            );
        }
        TableColumn<LiftRelation, String> usageCol = new TableColumn<>(
            I18n.get(Keys.COL_USAGE)
        );
        for (String l : metaLangs) {
            usageCol
                .getColumns()
                .add(
                    col(l, r ->
                        r
                            .getUsage()
                            .getForm(l)
                            .map(Form::toPlainText)
                            .orElse("")
                    )
                );
        }
        relationTable
            .getColumns()
            .addAll(parentFormGroup, typeCol, refFormGroup, usageCol);
        relationTable
            .getItems()
            .addAll(
                currentDictionary
                    .getLiftDictionaryRegistry()
                    .getRelations()
            );
        tableContainer
            .getChildren()
            .setAll(
                wrapTableWithFilters(
                    relationTable,
                    (f, t2) -> updateCountLabel(f, t2),
                    searchField != null ? searchField.textProperty() : null
                )
            );
        updateCountLabel(
            relationTable.getItems().size(),
            relationTable.getItems().size()
        );
    }

    private void populateRelationEditor(LiftRelation relation) {
        editedEntry = owningEntry(relation);
        editEntryTitle.setText(
            I18n.get("nav.relations") +
                " : " +
                (relation.getType() != null ? relation.getType().getId() : "?")
        );
        editEntryCode.setText(relation.getRefID().orElse(""));
        editorContainer.getChildren().clear();
        HasRelations parent = relation.getParent();
        if (parent != null) {
            addComponentDeleteButton(() -> parent.deleteRelation(relation), () -> parent.addRelation(relation));
        }
        addBackToParentButton(parent);
        RelationEditor re = new RelationEditor(currentDictionary);
        re.setRelation(relation);
        editorContainer.getChildren().add(re);
    }

    /* ════════════════════ ETYMOLOGY VIEW ════════════════════ */

    private void showEtymologyView() {
        TableView<LiftEtymology> etyTable = new TableView<>();
        if (currentDictionary == null) {
            tableContainer.getChildren().setAll(etyTable);
            return;
        }
        Set<String> objLangs = currentDictionary.getObjectLanguageManager().getLanguages();
        TableColumn<LiftEtymology, String> typeCol = col(
            I18n.get(Keys.COL_TYPE),
            (LiftEtymology e) -> e.getType().getId()
        );
        TableColumn<LiftEtymology, String> sourceCol = col(
            I18n.get(Keys.COL_SOURCE),
            (LiftEtymology e) -> e.getSource() != null ? e.getSource() : ""
        );
        TableColumn<LiftEtymology, String> formGroup = new TableColumn<>(
            I18n.get(Keys.COL_FORMS)
        );
        for (String l : objLangs)
            formGroup
                .getColumns()
                .add(
                    col(l, e ->
                        e
                            .getForms()
                            .getForm(l)
                            .map(Form::toPlainText)
                            .orElse("")
                    )
                );
        etyTable.getColumns().addAll(typeCol, sourceCol, formGroup);
        currentDictionary
            .getLiftDictionaryRegistry()
            .getEntries()
            .stream()
            .flatMap(e -> e.getEtymologies().stream())
            .forEach(etyTable.getItems()::add);
        etyTable
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (n != null) {
                    EtymologyEditor ee = new EtymologyEditor(currentDictionary);
                    ee.setEtymology(n);
                    editedEntry = owningEntry(n);
                    editEntryTitle.setText(
                        // TODO null for type?
                        n.getType() == null ? I18n.get("nav.etymologies") : n.getType().getId()
                    );
                    editEntryCode.setText(
                        n.getSource() != null ? n.getSource() : ""
                    );
                    editorContainer.getChildren().clear();
                    LiftEntry etyParent = n.getParent();
                    if (etyParent != null) {
                        addComponentDeleteButton(() -> etyParent.deleteEtymology(n), () -> etyParent.addEtymology(n));
                    }
                    editorContainer.getChildren().add(ee);
                }
            });
        tableContainer
            .getChildren()
            .setAll(
                wrapTableWithFilters(etyTable, this::updateCountLabel, searchField.textProperty())
            );
        updateCountLabel(
            etyTable.getItems().size(),
            etyTable.getItems().size()
        );
    }

    /* ════════════════════ LANGUAGE FIELD VIEW (5.9) ════════════════════ */

    private void showLangFieldView(boolean objectLangs) {
        langFieldTable.setItems(FXCollections.observableArrayList());
        langFieldTable.getColumns().clear();
        if (currentDictionary == null) {
            tableContainer.getChildren().setAll(langFieldTable);
            return;
        }

        Set<String> langs = objectLangs
            ? currentDictionary.getObjectLanguageManager().getLanguages()
            : currentDictionary.getMetaLanguageManager().getLanguages();

        // Collect all multitext entries with parent info
        List<MultiTextField> rows = new ArrayList<>();


        for (LiftEntry entry : currentDictionary
            .getLiftDictionaryRegistry()
            .getEntries()) {
            if (objectLangs) {
                String eid = entry.getId().orElse("?");
                collectMtRows(
                    rows,
                    I18n.get("nav.entries"),
                    eid,
                    entry,
                    entry.getForms(),
                    langs
                );
                for (LiftVariant v : entry.getVariants()) {
                    collectMtRows(
                        rows,
                        I18n.get("mtkind.variant"),
                        v.getRefId().orElse("?"),
                        v,
                        v.getForms(),
                        langs
                    );
                    for (LiftPronunciation vp : v.getPronunciations())
                        collectMtRows(
                            rows,
                            I18n.get("mtkind.pronunciation"),
                            v.getRefId().orElse("?"),
                            vp,
                            vp.getPronunciation(),
                            langs
                        );
                }
                for (LiftPronunciation p : entry.getPronunciations())
                    collectMtRows(
                        rows,
                        I18n.get("mtkind.pronunciation"),
                        eid,
                        p,
                        p.getPronunciation(),
                        langs
                    );
                for (LiftSense s : entry.getSenses())
                    collectObjLangRowsForSense(rows, s, langs);
                for (LiftEtymology et : entry.getEtymologies())
                    collectMtRows(
                        rows,
                        I18n.get("mtkind.etymology"),
                        eid,
                        et,
                        et.getForms(),
                        langs
                    );
            } else {
                String eid = entry.getId().orElse("?");
                collectMtRows(
                    rows,
                    I18n.get("mtkind.citation"),
                    eid,
                    entry,
                    entry.getCitations(),
                    langs
                );
                for (LiftNote n : entry.getNotes().values())
                    collectMtRows(rows, I18n.get("mtkind.note"), eid, n, n.getText(), langs);
                for (LiftRelation r : entry.getRelations())
                    collectMtRows(
                        rows,
                        I18n.get("mtkind.relationUsage"),
                        eid,
                        r,
                        r.getUsage(),
                        langs
                    );
                for (LiftField f : entry.getFields().values())
                    collectMtRows(rows, I18n.get("mtkind.field"), eid, f, f.getText(), langs);
                for (LiftAnnotation a : entry.getAnnotations())
                    collectMtRows(
                        rows,
                        I18n.get("mtkind.annotation"),
                        eid,
                        a,
                        a.getText(),
                        langs
                    );
                for (LiftSense s : entry.getSenses())
                    collectMetaLangRowsForSense(rows, s, langs);
                for (LiftVariant v : entry.getVariants()) {
                    String vid = v.getRefId().orElse("?");
                    for (LiftRelation r : v.getRelations())
                        collectMtRows(
                            rows,
                            I18n.get("mtkind.relationUsage"),
                            vid,
                            r,
                            r.getUsage(),
                            langs
                        );
                    for (LiftField f : v.getFields().values())
                        collectMtRows(
                            rows,
                            I18n.get("mtkind.field"),
                            vid,
                            f,
                            f.getText(),
                            langs
                        );
                    for (LiftAnnotation a : v.getAnnotations())
                        collectMtRows(
                            rows,
                            I18n.get("mtkind.annotation"),
                            vid,
                            a,
                            a.getText(),
                            langs
                        );
                }
            }
        }

        TableColumn<MultiTextField, String> parentTypeCol = new TableColumn<>(
            I18n.get(Keys.COL_PARENT_TYPE)
        );
        parentTypeCol.setCellValueFactory(cd ->
            new ReadOnlyStringWrapper(cd.getValue().parentType())
        );
        parentTypeCol.setPrefWidth(100);

        TableColumn<MultiTextField, String> langGroup = new TableColumn<>(
            I18n.get(Keys.NAV_LANGUAGES)
        );
        for (String l : langs) {
            TableColumn<MultiTextField, String> c = new TableColumn<>(l);
            c.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(
                    l.equals(cd.getValue().lang()) ? cd.getValue().text() : ""
                )
            );
            c.setPrefWidth(160);
            c.getProperties().put("filterMode", FILTER_MODE_TEXT);
            langGroup.getColumns().add(c);
        }

        langFieldTable.getColumns().addAll(parentTypeCol, langGroup);
        langFieldTable.getItems().addAll(rows);
        tableContainer
            .getChildren()
            .setAll(
                wrapTableWithFilters(
                    langFieldTable,
                    (f, t2) -> updateCountLabel(f, t2),
                    searchField != null ? searchField.textProperty() : null
                )
            );
        updateCountLabel(rows.size(), rows.size());
    }

    private static void collectMtRows(
        List<MultiTextField> rows,
        String parentType,
        String parentId,
        Object parentObject,
        MultiText mt,
        Set<String> langs
    ) {
        if (mt == null) return;
        for (Form f : mt.getForms()) {
            if (langs.contains(f.getLang())) {
                rows.add(
                    new MultiTextField(
                        parentType,
                        parentId,
                        f.getLang(),
                        f.toPlainText(),
                        parentObject,
                        mt
                    )
                );
            }
        }
    }

    private static void collectObjLangRowsForSense(
        List<MultiTextField> rows,
        LiftSense s,
        Set<String> langs
    ) {
        String sid = s.getId().orElse("?");
        for (LiftExample ex : s.getExamples())
            collectMtRows(rows, I18n.get("mtkind.example"), sid, ex, ex.getExample(), langs);
        for (LiftSense sub : s.getSenses())
            collectObjLangRowsForSense(rows, sub, langs);
    }

    private static void collectMetaLangRowsForSense(
        List<MultiTextField> rows,
        LiftSense s,
        Set<String> langs
    ) {
        String sid = s.getId().orElse("?");
        collectMtRows(rows, I18n.get("mtkind.definition"), sid, s, s.getDefinition(), langs);
        collectMtRows(rows, I18n.get("mtkind.gloss"), sid, s, s.getGlosses(), langs);
        for (LiftExample ex : s.getExamples()) {
            for (MultiText tr : ex.getTranslations().values())
                collectMtRows(rows, I18n.get("mtkind.translation"), sid, ex, tr, langs);
            for (LiftNote n : ex.getNotes().values())
                collectMtRows(rows, I18n.get("mtkind.note"), sid, n, n.getText(), langs);
            for (LiftField f : ex.getFields().values())
                collectMtRows(rows, I18n.get("mtkind.field"), sid, f, f.getText(), langs);
            for (LiftAnnotation a : ex.getAnnotations())
                collectMtRows(rows, I18n.get("mtkind.annotation"), sid, a, a.getText(), langs);
        }
        for (LiftReversal rev : s.getReversals()) {
            collectMtRows(rows, I18n.get("mtkind.reversal"), sid, rev, rev.getForms(), langs);
            if (rev.getMain() != null) collectMtRows(
                rows,
                I18n.get("mtkind.reversalMain"),
                sid,
                rev.getMain(),
                rev.getMain().getForms(),
                langs
            );
        }
        for (LiftRelation r : s.getRelations())
            collectMtRows(
                rows,
                I18n.get("mtkind.relationUsage"),
                sid,
                r,
                r.getUsage(),
                langs
            );
        for (LiftNote n : s.getNotes().values())
            collectMtRows(rows, I18n.get("mtkind.note"), sid, n, n.getText(), langs);
        for (LiftField f : s.getFields().values())
            collectMtRows(rows, I18n.get("mtkind.field"), sid, f, f.getText(), langs);
        for (LiftAnnotation a : s.getAnnotations())
            collectMtRows(rows, I18n.get("mtkind.annotation"), sid, a, a.getText(), langs);
        for (LiftIllustration ill : s.getIllustrations())
            collectMtRows(
                rows,
                I18n.get("mtkind.illustration"),
                sid,
                ill,
                ill.getLabel(),
                langs
            );
        for (LiftSense sub : s.getSenses())
            collectMetaLangRowsForSense(rows, sub, langs);
    }

    /* ════════════════════ TRAIT VIEW (5.10 – split: names top, values bottom) ════════════════════ */

    private void showTraitView() {
        traitTable.setItems(FXCollections.observableArrayList());
        traitTable.getColumns().clear();
        if (currentDictionary == null) {
            tableContainer
                .getChildren()
                .setAll(
                    wrapTableWithFilters(
                        traitTable,
                        (filtered, total) -> updateCountLabel(filtered, total),
                        searchField != null ? searchField.textProperty() : null
                    )
                );
            return;
        }

        Map<String, TraitRow> counts = new LinkedHashMap<>();
        for (LiftTrait t : currentDictionary
            .getLiftDictionaryRegistry()
            .getTraits()) {
            String key = t.getSpecification().getName() + "|" + t.getValue();
            counts.compute(key, (k, row) -> {
                String parentType = describeParentType(t.getParent());
                if (row == null) return new TraitRow(
                    parentType,
                    t.getSpecification().getName(),
                    t.getValue(),
                    1
                );
                return new TraitRow(
                    row.parentType,
                    row.name,
                    row.value,
                    row.frequency + 1
                );
            });
        }

        TableColumn<TraitRow, String> traitFreqCol = col(
            I18n.get(Keys.COL_FREQUENCY),
            r -> String.valueOf(r.frequency())
        );
        traitFreqCol.getProperties().put("filterMode", FILTER_MODE_TEXT);
        traitTable
            .getColumns()
            .addAll(
                col(I18n.get(Keys.COL_PARENT_TYPE), (TraitRow r) ->
                    r.parentType()
                ),
                col(I18n.get(Keys.COL_NAME), (TraitRow r) -> r.name()),
                col(I18n.get(Keys.COL_VALUE), (TraitRow r) -> r.value()),
                traitFreqCol
            );
        traitTable
            .getItems()
            .addAll(
                counts
                    .values()
                    .stream()
                    .sorted(
                        Comparator.comparingLong(TraitRow::frequency).reversed()
                    )
                    .toList()
            );

        tableContainer
            .getChildren()
            .setAll(
                wrapTableWithFilters(
                    traitTable,
                    (f, t2) -> updateCountLabel(f, t2),
                    searchField != null ? searchField.textProperty() : null
                )
            );
        updateCountLabel(
            traitTable.getItems().size(),
            traitTable.getItems().size()
        );
    }

    private void showAnnotationView() {
        annotationTable.setItems(FXCollections.observableArrayList());
        annotationTable.getColumns().clear();
        if (currentDictionary == null) {
            tableContainer.getChildren().setAll(annotationTable);
            return;
        }

        List<LiftAnnotation> all = currentDictionary
            .getLiftDictionaryRegistry()
            .getAnnotations();

        TableColumn<LiftAnnotation, String> annotFreqCol = col(
            I18n.get(Keys.COL_FREQUENCY),
            a -> {
                long c = all
                    .stream()
                    .filter(x -> x.getType() == a.getType() && Objects.equals(x.getValue(), a.getValue()))
                    .count();
                return String.valueOf(c);
            }
        );

        annotationTable
            .getColumns()
            .addAll(
                col(I18n.get(Keys.COL_PARENT_TYPE), (LiftAnnotation a) ->
                    describeParentType(a.getParent())
                ),
                col(I18n.get(Keys.COL_PARENT), (LiftAnnotation a) ->
                    describeParent(a.getParent())
                ),
                col(I18n.get(Keys.COL_NAME), (LiftAnnotation a) -> EditorSupport.featureId(a.getType())),
                col(I18n.get(Keys.COL_VALUE), LiftAnnotation::getValue),
                col(I18n.get(Keys.COL_WHO), LiftAnnotation::getWho),
                col(I18n.get(Keys.COL_WHEN), LiftAnnotation::getWhen),
                annotFreqCol
            );
        annotationTable.getItems().addAll(all);

        tableContainer
            .getChildren()
            .setAll(
                wrapTableWithFilters(
                    annotationTable,
                    (f, t2) -> updateCountLabel(f, t2),
                    searchField != null ? searchField.textProperty() : null
                )
            );
        updateCountLabel(all.size(), all.size());
    }

    /* ════════════════════ FIELD VIEW (5.10) ════════════════════ */

    private void showFieldView() {
        fieldTable.setItems(FXCollections.observableArrayList());
        fieldTable.getColumns().clear();
        if (currentDictionary == null) {
            tableContainer.getChildren().setAll(fieldTable);
            return;
        }
        fieldTable
            .getColumns()
            .addAll(
                col(I18n.get(Keys.COL_PARENT_TYPE), f ->
                    describeParentType(f.getParent())
                ),
                col(I18n.get(Keys.COL_TYPE), x -> x.getSpecification().getName()),
                col(I18n.get(Keys.COL_TEXT), f ->
                    f
                        .getText()
                        .getForms()
                        .stream()
                        .findFirst()
                        .map(Form::toPlainText)
                        .orElse("")
                )
            );
        fieldTable
            .getItems()
            .addAll(
                currentDictionary.getLiftDictionaryRegistry().getFields()
            );
        tableContainer
            .getChildren()
            .setAll(
                wrapTableWithFilters(
                    fieldTable,
                    (f, t2) -> updateCountLabel(f, t2),
                    searchField != null ? searchField.textProperty() : null
                )
            );
        updateCountLabel(
            fieldTable.getItems().size(),
            fieldTable.getItems().size()
        );
    }

    /* ════════════════════ QUICK ENTRY VIEW (5.12) ════════════════════ */

    private void showQuickEntryView() {
        quickEntryTable.setItems(FXCollections.observableArrayList());
        quickEntryTable.getColumns().clear();
        if (currentDictionary == null) {
            tableContainer.getChildren().setAll(quickEntryTable);
            return;
        }
        quickEntryTable.setEditable(true);
        Set<String> objLangs = currentDictionary.getObjectLanguageManager().getLanguages();
        Set<String> metaLangs = currentDictionary.getMetaLanguageManager().getLanguages();

        for (String l : objLangs) {
            TableColumn<QuickEntryRow, String> c = new TableColumn<>(
                I18n.get("quick.formColumn", l)
            );
            c.setCellValueFactory(cd -> cd.getValue().formProperty(l));
            c.setCellFactory(tc ->
                new CommitOnFocusLossCell<>(row -> row.formProperty(l))
            );
            c.setPrefWidth(130);
            c.setEditable(true);
            quickEntryTable.getColumns().add(c);
        }
        for (String l : metaLangs) {
            TableColumn<QuickEntryRow, String> c = new TableColumn<>(
                I18n.get("quick.senseColumn", l)
            );
            c.setCellValueFactory(cd -> cd.getValue().glossProperty(l));
            c.setCellFactory(tc ->
                new CommitOnFocusLossCell<>(row -> row.glossProperty(l))
            );
            c.setPrefWidth(130);
            c.setEditable(true);
            quickEntryTable.getColumns().add(c);
        }
        List<String> knownGramCodes = getKnownGramInfoValues();
        ObservableList<String> gramItems = FXCollections.observableArrayList(
            knownGramCodes
        );

        TableColumn<QuickEntryRow, String> giCol = new TableColumn<>(
            I18n.get(Keys.COL_GRAM_CODE)
        );
        giCol.setCellValueFactory(cd -> cd.getValue().gramInfoProperty());
        giCol.setCellFactory(tc -> {
            ComboBox<String> combo = new ComboBox<>(gramItems);
            combo.setEditable(false);
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setPromptText("—");
            TableCell<QuickEntryRow, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        combo.setValue(item);
                        setGraphic(combo);
                    }
                }
            };
            combo.valueProperty().addListener((obs, o, n) -> {
                if (
                    cell.getTableRow() != null &&
                    cell.getTableRow().getItem() != null
                ) {
                    cell.getTableRow()
                        .getItem()
                        .gramInfoProperty()
                        .set(n != null ? n : "");
                }
            });
            return cell;
        });
        giCol.setPrefWidth(140);
        giCol.setEditable(true);
        quickEntryTable.getColumns().add(giCol);

        // Seed with empty rows
        for (int i = 0; i < 5; i++) quickEntryTable
            .getItems()
            .add(new QuickEntryRow());

        // Auto-create entry when user leaves a filled row (selection changes away from it)
        quickEntryTable
            .getSelectionModel()
            .selectedIndexProperty()
            .addListener((obs, oldIdx, newIdx) -> {
                int prev = oldIdx.intValue();
                if (
                    prev < 0 || prev >= quickEntryTable.getItems().size()
                ) return;
                QuickEntryRow row = quickEntryTable.getItems().get(prev);

                LiftEntry e = createEntriesFromQuickTableRow(row);
                if (e == null) return;

                // + add grammatical info if present
                updateCountLabel(baseEntries.size(), baseEntries.size());

                // Ensure there's always an empty row at the end
                int lastIdx = quickEntryTable.getItems().size() - 1;
                QuickEntryRow lastRow = quickEntryTable.getItems().get(lastIdx);
                boolean lastHasContent =
                    objLangs
                        .stream()
                        .anyMatch(
                            l -> !lastRow.formProperty(l).get().isBlank()
                        ) ||
                    metaLangs
                        .stream()
                        .anyMatch(
                            l -> !lastRow.glossProperty(l).get().isBlank()
                        );
                if (lastHasContent) quickEntryTable
                    .getItems()
                    .add(new QuickEntryRow());
            });

        // Also append a new row when user clicks on the last row
        quickEntryTable.setOnMouseClicked(e -> {
            int lastIdx = quickEntryTable.getItems().size() - 1;
            if (
                lastIdx >= 0 &&
                quickEntryTable.getSelectionModel().getSelectedIndex() ==
                    lastIdx
            ) {
                QuickEntryRow lastRow = quickEntryTable.getItems().get(lastIdx);
                boolean lastHasContent =
                    objLangs
                        .stream()
                        .anyMatch(
                            l -> !lastRow.formProperty(l).get().isBlank()
                        ) ||
                    metaLangs
                        .stream()
                        .anyMatch(
                            l -> !lastRow.glossProperty(l).get().isBlank()
                        );
                if (lastHasContent) quickEntryTable
                    .getItems()
                    .add(new QuickEntryRow());
            }
        });

        tableContainer.getChildren().setAll(quickEntryTable);
        updateCountLabel(0, 0);
        addButton.setText(I18n.get(Keys.BTN_CREATE_ENTRIES));
    }

    /* ─── Create new object (5.8 context menu) ─── */

    @FXML
    private void onCreateNewObject() {
        if (currentDictionary == null) {
            showError(
                I18n.get(Keys.ERROR_CREATION),
                I18n.get(Keys.ERROR_NO_DICTIONARY)
            );
            return;
        }
        switch (currentView) {
            case NAV_QUICK_ENTRY -> createEntriesFromQuickTable();
            case NAV_ENTRIES -> createNewEntry();
            default -> {
            }
        }
    }

    private void createNewEntry() {
        LiftEntry entry = currentDictionary.getComponentBuilder().entry().build();
        // The builder registers the entry in the dictionary, and baseEntries follows the
        // dictionary's entry list.
        markDirty();
        applyCurrentFilter();
        entryTable.getSelectionModel().select(entry);
        entryTable.scrollTo(entry);
    }

    private LiftEntry createEntriesFromQuickTableRow(QuickEntryRow row) {
        Set<String> objLangs = currentDictionary.getObjectLanguageManager().getLanguages();
        Set<String> metaLangs = currentDictionary.getMetaLanguageManager().getLanguages();
        List<String> filedObjLangs = objLangs
            .stream()
            .filter(l -> !row.formProperty(l).get().isBlank())
            .collect(Collectors.toList());

        if (filedObjLangs.isEmpty() || currentDictionary == null) return null;

        List<String> filedMetaLangs = metaLangs
            .stream()
            .filter(l -> !row.glossProperty(l).get().isBlank())
            .collect(Collectors.toList());

        // Check if entry was already auto-created for this row
        if (Boolean.TRUE.equals(row.createdProperty().get())) return null;
        row.createdProperty().set(true);

        EntryBuilder eb = currentDictionary.getComponentBuilder().entry();
        for (String filedObjLang : filedObjLangs) {
            String v = row.formProperty(filedObjLang).get();
            if (!v.isBlank()) eb.withForm(filedObjLang, v);
        }
        LiftEntry e = eb.build();

        // One sense carrying the glosses of every meta-language filled in the row.
        String gi = row.gramInfoProperty().get();
        if (!filedMetaLangs.isEmpty() || !gi.isBlank()) {
            SenseBuilder sb = currentDictionary.getComponentBuilder().sense(e);
            for (String filedMetaLang : filedMetaLangs) {
                sb.withGloss(filedMetaLang, row.glossProperty(filedMetaLang).get());
            }
            if (!gi.isBlank()) sb.withPartOfSpeech(gi);
            sb.build();
        }
        return e;
    }

    private void createEntriesFromQuickTable() {
        int created = 0;
        for (QuickEntryRow row : quickEntryTable.getItems()) {
            LiftEntry e = createEntriesFromQuickTableRow(row);
            if (e == null) continue;
            created++;
        }
        if (created > 0) {
            markDirty();
            showInfo(
                I18n.get(Keys.NAV_QUICK_ENTRY),
                I18n.get(Keys.INFO_QUICK_ENTRY_CREATED, created)
            );
            quickEntryTable.getItems().clear();
            for (int i = 0; i < 5; i++) quickEntryTable
                .getItems()
                .add(new QuickEntryRow());
        }
    }

    /* ─── Editor population helpers ─── */

    private void populateEntryEditor(LiftEntry entry) {
        editedEntry = entry;
        try {
            Form preferred = entry
                .getForms()
                .getForms()
                .stream()
                .findFirst()
                .orElse(Form.EMPTY_FORM);
            editEntryTitle.setText(
                preferred == Form.EMPTY_FORM
                    ? I18n.get("editor.noForm")
                    : preferred.toPlainText()
            );
            editEntryCode.setText(getTraitValue(entry, "code"));
            editorContainer.getChildren().clear();

            Button deleteBtn = newDeleteButton();
            deleteBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle(I18n.get(Keys.CONFIRM_DELETE_TITLE));
                confirm.setHeaderText(null);
                confirm.setContentText(
                    I18n.get(
                        Keys.CONFIRM_DELETE_ENTRY,
                        entry
                            .getForms()
                            .getForms()
                            .stream()
                            .findFirst()
                            .map(Form::toPlainText)
                            .filter(t -> t != null && !t.isBlank())
                            .orElse(I18n.get("editor.noForm"))
                    )
                );
                confirm
                    .showAndWait()
                    .filter(r -> r == ButtonType.OK)
                    .ifPresent(r -> {
                        int idx = currentDictionary.getLiftDictionaryRegistry().getEntries().indexOf(entry);
                        Runnable refresh = () -> {
                            editorContainer.getChildren().clear();
                            editEntryTitle.setText(
                                I18n.get("panel.selectElement")
                            );
                            editEntryCode.setText("");
                            applyCurrentFilter();
                        };
                        DeleteEntryCommand cmd = new DeleteEntryCommand(
                            entry,
                            idx,
                            () -> currentDictionary,
                            refresh,
                            refresh
                        );
                        executeDeletion(cmd);
                    });
            });
            editorContainer.getChildren().add(deleteBtn);

            LiftEntryEditor entryEditor = new LiftEntryEditor(currentDictionary);
            entryEditor.setOnOpenSense(this::navigateToSenseKeepingEntriesFocus);
            entryEditor.setOnChanged(() -> {
                entryTable.refresh();
                senseTable.refresh();
            });
            entryEditor.setEntry(entry);
            editorContainer.getChildren().add(entryEditor);
        } catch (Exception ex) {
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            values.put(
                I18n.get("field.id"),
                entry == null ? "" : entry.getId().orElse("")
            );
            values.put(
                I18n.get("editor.error"),
                Optional.ofNullable(ex.getMessage()).orElse(
                    ex.getClass().getSimpleName()
                )
            );
            populateSummaryEditor(I18n.get("editor.entryDisplayError"), "", values);
        }
    }

    private void populateSenseEditor(LiftSense sense) {
        editedEntry = owningEntry(sense);
        editEntryTitle.setText(LiftEntryEditor.senseDisplayText(sense));
        editEntryCode.setText(
            sense.getGrammaticalInfo().map(x -> x.getGramInfoValue().getId()).orElse("")
        );
        editorContainer.getChildren().clear();

        Button deleteBtn = newDeleteButton();
        deleteBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle(I18n.get(Keys.CONFIRM_DELETE_TITLE));
            confirm.setHeaderText(null);
            confirm.setContentText(
                I18n.get(Keys.CONFIRM_DELETE_SENSE, LiftEntryEditor.senseDisplayText(sense))
            );
            confirm
                .showAndWait()
                .filter(r -> r == ButtonType.OK)
                .ifPresent(r -> {
                        Runnable refresh = () -> {
                            editorContainer.getChildren().clear();
                            editEntryTitle.setText(
                                I18n.get("panel.selectElement")
                            );
                            editEntryCode.setText("");
                            refreshCurrentView();
                        };
                        DeleteSenseCommand cmd = new DeleteSenseCommand(
                            sense,
                            ((HasSense) sense.getParent()),
                            sense.getParent().getSenses().indexOf(sense),
                            refresh,
                            refresh
                        );
                        executeDeletion(cmd);
                });
        });
        editorContainer.getChildren().add(deleteBtn);
        addBackToParentButton(sense.getParentEntry());

        // Links to examples (one per example, showing example number)
        List<LiftExample> examples = sense.getExamples();
        for (int i = 0; i < examples.size(); i++) {
            final int idx = i + 1;
            final LiftExample ex = examples.get(i);
            Hyperlink exLink = new Hyperlink(I18n.get("sense.exampleN", idx));
            exLink.setOnAction(e -> navigateToExampleKeepingEntriesFocus(ex));
            editorContainer.getChildren().add(exLink);
        }

        SenseEditor se = new SenseEditor(currentDictionary);
        se.setGrammaticalInfoValues(getKnownGramInfoValues());
        se.setOnGramInfoChanged(() -> senseTable.refresh());
        BiConsumer<String, MultiText> onAddAnnotation =
            (name, mt) -> currentDictionary.getComponentBuilder().annotation(mt, name).build();
        se.setSense(
            sense,
            onAddAnnotation,
            EditorSupport.sortedFeatureIds(currentDictionary.getHeader().getAnnotationTypeManager()),
            this::createSenseAddActions,
            this::createExampleAddActions
        );
        editorContainer.getChildren().add(se);
            FlowPane addButtons = new FlowPane(8, 6);
            addButtons.setPadding(new Insets(8, 0, 0, 0));

            Button addExBtn = new Button(I18n.get("btn.addExample"));
            addExBtn.setOnAction(e -> {
                currentDictionary.getComponentBuilder().example(sense).build();
                populateSenseEditor(sense);
            });

            Button addNoteBtn = new Button(I18n.get("btn.addNote"));
            addNoteBtn.setOnAction(e -> {
                List<String> types = currentDictionary.getHeader().getNoteTypeManager().getFeatures().values().stream().map(x -> x.getId()).toList();
                ChoiceDialog<String> dlg = new ChoiceDialog<>(
                    types.isEmpty() ? null : types.get(0),
                    types
                );
                dlg.setTitle(I18n.get("btn.addNote"));
                dlg.setHeaderText(I18n.get("col.type"));
                dlg.showAndWait().ifPresent(type -> {
                    currentDictionary.getComponentBuilder().note(sense, type).build();
                    populateSenseEditor(sense);
                });
            });

            Button addRelationBtn = new Button(I18n.get("btn.addRelation"));
            addRelationBtn.setOnAction(e -> {
                List<String> types = currentDictionary.getHeader().getRelationTypeManager().getFeatures().values().stream().map(x -> x.getId()).toList();
                Optional<String> typeOpt;
                if (types.isEmpty()) {
                    TextInputDialog tid = new TextInputDialog();
                    tid.setTitle(I18n.get("btn.addRelation"));
                    tid.setHeaderText(I18n.get("col.type"));
                    typeOpt = tid.showAndWait();
                } else {
                    ChoiceDialog<String> dlg = new ChoiceDialog<>(
                        types.get(0),
                        types
                    );
                    dlg.setTitle(I18n.get("btn.addRelation"));
                    dlg.setHeaderText(I18n.get("col.type"));
                    typeOpt = dlg.showAndWait();
                }
                typeOpt
                    .filter(t -> t != null && !t.isBlank())
                    .ifPresent(type -> {
                        currentDictionary.getComponentBuilder().relation(type.trim(), sense).build();
                        populateSenseEditor(sense);
                    });
            });

            Button addReversalBtn = new Button(I18n.get("btn.addReversal"));
            addReversalBtn.setOnAction(e -> {
                currentDictionary.getComponentBuilder().reversal(sense).build();
                populateSenseEditor(sense);
            });

            Button addSubSenseBtn = new Button(I18n.get("btn.addSubSense"));
            addSubSenseBtn.setOnAction(e -> {
                currentDictionary.getComponentBuilder().sense(sense).build();
                populateSenseEditor(sense);
                refreshSenseTableAndFilters();
            });

            addButtons
                .getChildren()
                .addAll(
                    addExBtn,
                    addNoteBtn,
                    addRelationBtn,
                    addReversalBtn,
                    addSubSenseBtn
                );
            editorContainer.getChildren().add(addButtons);
    }

    /** Rebuilds the sense table and filter dropdowns (e.g. after adding a sub-sense). */
    private void refreshSenseTableAndFilters() {
        if (NAV_SENSES.equals(currentView) && currentDictionary != null) {
            showSenseView();
        }
    }




    private void populateExampleEditor(LiftSense parentSense, LiftExample ex) {
        editedEntry = owningEntry(ex);
        editEntryTitle.setText(I18n.get("nav.examples"));
        editEntryCode.setText(ex.getSource().orElse(""));
        editorContainer.getChildren().clear();
        Button deleteBtn = newDeleteButton();
        deleteBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle(I18n.get(Keys.CONFIRM_DELETE_TITLE));
            confirm.setHeaderText(null);
            confirm.setContentText(I18n.get("confirm.delete.example"));
            confirm
                .showAndWait()
                .filter(r -> r == ButtonType.OK)
                .ifPresent(r -> {
                        Runnable refresh = () -> {
                            editorContainer.getChildren().clear();
                            editEntryTitle.setText(
                                I18n.get("panel.selectElement")
                            );
                            editEntryCode.setText("");
                            refreshCurrentView();
                        };
                        DeleteExampleCommand cmd = new DeleteExampleCommand(
                            ex,
                            ex.getParent(),
                            ex.getParent().getExamples().indexOf(ex),
                            refresh,
                            refresh
                        );
                        executeDeletion(cmd);
                });
        });
        editorContainer.getChildren().add(deleteBtn);

        addBackToParentButton(parentSense);

        ExampleEditor ee = new ExampleEditor(currentDictionary);
        BiConsumer<String, MultiText> onAddAnnotation =
            (name, mt) -> currentDictionary.getComponentBuilder().annotation(mt, name).build();
        ee.setExample(ex, onAddAnnotation, createExampleAddActions(ex));
        editorContainer.getChildren().add(ee);
    }

    private void populateExampleEditor(LiftExample ex) {
        populateExampleEditor(ex.getParent(), ex);
    }


    private void navigateToSenseKeepingEntriesFocus(LiftSense sense) {
        if (sense == null) return;
        switchView(NAV_SENSES);
        applySenseTableFilterByEntry(sense.getParentEntry());
        if (senseTable.getItems().contains(sense)) {
            senseTable.getSelectionModel().select(sense);
            senseTable.scrollTo(sense);
        }
        selectNavItem(NAV_SENSES);
    }

    private void navigateToEntryFromSense(LiftEntry entry) {
        if (entry == null) return;
        switchView(NAV_ENTRIES);
        clearSearchAndVisibleColumnFilters();
        applyCurrentFilter();
        if (filteredEntries.contains(entry)) {
            entryTable.getSelectionModel().select(entry);
            entryTable.scrollTo(entry);
            populateEntryEditor(entry);
        } else if (!filteredEntries.isEmpty()) {
            LiftEntry first = filteredEntries.getFirst();
            entryTable.getSelectionModel().select(first);
            entryTable.scrollTo(first);
            populateEntryEditor(first);
        }
    }

    /** Navigue vers la fiche du sens (depuis une note ou autre objet enfant). */
    private void navigateToSenseFromParent(LiftSense sense) {
        if (sense == null) return;
        switchView(NAV_SENSES);
        if (senseTable.getItems().contains(sense)) {
            senseTable.getSelectionModel().select(sense);
            senseTable.scrollTo(sense);
            populateSenseEditor(sense);
        }
    }

    private void navigateToExampleKeepingEntriesFocus(LiftExample example) {
        if (example == null) return;
        switchView(NAV_EXAMPLES);
        clearSearchAndVisibleColumnFilters();
        if (exampleTable.getItems().contains(example)) {
            exampleTable.getSelectionModel().select(example);
            exampleTable.scrollTo(example);
        }
        selectNavItem(NAV_EXAMPLES);
    }

    private void selectNavItem(String navKey) {
        if (navTree == null || navTree.getSelectionModel() == null) return;
        TreeItem<String> item = findNavItemByKey(navKey);
        if (item == null) return;
        ignoreNavSelectionEvents = true;
        try {
            navTree.getSelectionModel().select(item);
        } finally {
            ignoreNavSelectionEvents = false;
        }
    }

    private void clearSearchAndVisibleColumnFilters() {
        if (searchField != null && !searchField.getText().isBlank()) {
            searchField.clear();
        }
        if (
            tableContainer == null || tableContainer.getChildren().isEmpty()
        ) return;
        javafx.scene.Node root = tableContainer.getChildren().get(0);
        if (
            !(root instanceof VBox wrapper) || wrapper.getChildren().size() < 2
        ) return;
        javafx.scene.Node filterNode = wrapper.getChildren().get(1);
        if (!(filterNode instanceof Pane filterPane)) return;

        String clearOption = I18n.get("filter.clear");
        for (javafx.scene.Node child : filterPane.getChildren()) {
            if (child instanceof ComboBox<?> rawCombo) {
                @SuppressWarnings("unchecked")
                ComboBox<String> combo = (ComboBox<String>) rawCombo;
                combo.setValue(clearOption);
            } else if (child instanceof TextField textField) {
                textField.clear();
            }
        }
    }

    /**
     * Applique un filtre sur le tableau des sens pour n'afficher que les sens de l'entrée donnée.
     * Utilisé lors de la navigation depuis le formulaire d'une entrée vers le tableau des sens.
     */
    private void applySenseTableFilterByEntry(LiftEntry entry) {
        if (
            entry == null ||
            tableContainer == null ||
            tableContainer.getChildren().isEmpty()
        ) return;
        if (
            searchField != null && !searchField.getText().isBlank()
        ) searchField.clear();
        javafx.scene.Node root = tableContainer.getChildren().get(0);
        if (
            !(root instanceof VBox wrapper) || wrapper.getChildren().size() < 2
        ) return;
        javafx.scene.Node filterNode = wrapper.getChildren().get(1);
        if (
            !(filterNode instanceof Pane filterPane) ||
            filterPane.getChildren().isEmpty()
        ) return;

        Set<String> objLangs = currentDictionary.getObjectLanguageManager().getLanguages();
        String filterValue = null;
        for (String lang : objLangs) {
            String form = entry
                .getForms()
                .getForm(lang)
                .map(Form::toPlainText)
                .orElse("")
                .trim();
            if (!form.isEmpty()) {
                filterValue = form;
                break;
            }
        }
        if (filterValue == null) {
            filterValue = entry
                .getForms()
                .getForms()
                .stream()
                .findFirst()
                .map(Form::toPlainText)
                .orElse("")
                .trim();
        }
        if (filterValue.isEmpty()) return;

        javafx.scene.Node firstFilter = filterPane.getChildren().get(0);
        if (firstFilter instanceof ComboBox<?> rawCombo) {
            @SuppressWarnings("unchecked")
            ComboBox<String> combo = (ComboBox<String>) rawCombo;
            if (combo.getItems().contains(filterValue)) {
                combo.setValue(filterValue);
            }
        } else if (firstFilter instanceof TextField tf) {
            tf.setText(filterValue);
        }
    }

    private TreeItem<String> findNavItemByKey(String navKey) {
        for (Map.Entry<TreeItem<String>, String> entry : navKeyMap.entrySet()) {
            if (Objects.equals(entry.getValue(), navKey)) return entry.getKey();
        }
        return null;
    }

    private void populateLangFieldEditor(MultiTextField row) {
        editedEntry = owningEntry(row.parentObject());
        editEntryTitle.setText(I18n.get(currentView));
        editEntryCode.setText("");
        editorContainer.getChildren().clear();

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        int r = 0;
        addReadOnlyRow(g, r++, I18n.get("col.parentType"), row.parentType());
        g.add(new Label(I18n.get("col.parent")), 0, r);
        if (row.parentObject() != null) {
            Button parentLink = new Button(row.parentId());
            parentLink.getStyleClass().add("hyperlink");
            parentLink.setOnAction(e -> navigateToObject(row.parentObject()));
            GridPane.setHgrow(parentLink, Priority.ALWAYS);
            g.add(parentLink, 1, r);
        } else {
            TextField tf = new TextField(row.parentId());
            styleReadOnlyTextField(tf);
            GridPane.setHgrow(tf, Priority.ALWAYS);
            g.add(tf, 1, r);
        }
        r++;
        addReadOnlyRow(g, r++, I18n.get("nav.languages"), row.lang());

        if (row.multiText() != null) {
            editorContainer.getChildren().add(g);
            MultiTextEditor mte = new MultiTextEditor(currentDictionary);
            if (NAV_OBJ_LANGS.equals(currentView)) mte.useObjectLanguages();
            mte.setMultiText(row.multiText());
            VBox textSection = new VBox(6);
            textSection.getChildren().add(new Label(I18n.get("col.text")));
            textSection.getChildren().add(mte);
            VBox.setVgrow(mte, Priority.ALWAYS);
            editorContainer.getChildren().add(textSection);
        } else {
            addReadOnlyRow(g, r, I18n.get("col.text"), row.text());
            editorContainer.getChildren().add(g);
        }
    }

    private void populateTraitSummaryEditor(TraitRow row) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.name"), row.name());
        values.put(I18n.get("col.value"), row.value());
        values.put(I18n.get("col.frequency"), String.valueOf(row.frequency()));
        populateSummaryEditor(I18n.get("nav.traits"), "", values);
        Button accessBtn = new Button(I18n.get("btn.accessObjects"));
        accessBtn.getStyleClass().addAll("example-add-button", "no-dirty");
        accessBtn.setMaxWidth(Double.MAX_VALUE);
        accessBtn.setOnAction(e ->
            showObjectsWithTrait(row.name(), row.value())
        );
        editorContainer.getChildren().add(accessBtn);
    }

    private void populateNoteTypeSummaryEditor(NoteTypeRow row) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.type"), row.type());
        values.put(I18n.get("col.parentType"), row.parentType());
        values.put(I18n.get("col.frequency"), String.valueOf(row.frequency()));
        populateSummaryEditor(I18n.get("nav.noteTypes"), "", values);
        Button accessBtn = new Button(I18n.get("btn.accessObjects"));
        accessBtn.getStyleClass().addAll("example-add-button", "no-dirty");
        accessBtn.setMaxWidth(Double.MAX_VALUE);
        accessBtn.setOnAction(e -> showObjectsWithNoteType(row.type()));
        editorContainer.getChildren().add(accessBtn);
    }

    private void showObjectsWithTrait(String traitName, String traitValue) {
        if (currentDictionary == null) return;
        List<LiftEntry> matches = new ArrayList<>();
        var comps = currentDictionary.getLiftDictionaryRegistry();
        for (LiftEntry e : comps.getEntries()) {
            if (
                e
                    .getTraits()
                    .stream()
                    .anyMatch(
                        t ->
                            traitName.equals(t.getSpecification().getName()) &&
                            traitValue.equals(t.getValue())
                    )
            ) matches.add(e);
        }
        for (LiftSense s : comps.getSenses()) {
            if (
                s
                    .getTraits()
                    .stream()
                    .anyMatch(
                        t ->
                            traitName.equals(t.getSpecification().getName()) &&
                            traitValue.equals(t.getValue())
                    )
            ) matches.add(s.getParentEntry());
        }
        for (LiftExample ex : comps.getExamples()) {
            if (
                ex
                    .getTraits()
                    .stream()
                    .anyMatch(
                        t ->
                            traitName.equals(t.getSpecification().getName()) &&
                            traitValue.equals(t.getValue())
                    )
            ) // findParentSense(ex)
            matches.add(ex.getParent().getParentEntry());
        }
        for (LiftVariant v : comps.getVariants()) {
            if (
                v.getTraits() != null &&
                v
                    .getTraits()
                    .stream()
                    .anyMatch(
                        t ->
                            traitName.equals(t.getSpecification().getName()) &&
                            traitValue.equals(t.getValue())
                    )
            ) matches.add(v.getParent()); //Optional.ofNullable(v.getParent()).ifPresent(matches::add);
        }
        for (LiftEtymology et : comps
            .getEntries()
            .stream()
            .flatMap(e -> e.getEtymologies().stream())
            .toList()) {
            if (
                et
                    .getTraits()
                    .stream()
                    .anyMatch(
                        t ->
                            traitName.equals(t.getSpecification().getName()) &&
                            traitValue.equals(t.getValue())
                    )
            ) matches.add(et.getParent()); // Optional.ofNullable(et.getParent()).ifPresent(matches::add);
        }
        showMatchingEntries(
            matches,
            I18n.get("nav.traits") + ": " + traitName + " = " + traitValue
        );
    }

    private void showObjectsWithNoteType(String noteType) {
        if (currentDictionary == null) return;
        List<LiftEntry> matches = new ArrayList<>();
        for (LiftNote n : currentDictionary
            .getLiftDictionaryRegistry()
            .getNotes()) {
            if (!noteType.equals(n.getType().getId())) continue;
            AbstractNotable parent = n.getParent();
            if (parent instanceof LiftEntry e) matches.add(e);
            else if (parent instanceof LiftSense s)
                matches.add(s.getParentEntry());
        }
        showMatchingEntries(
            matches,
            I18n.get("nav.noteTypes") + ": " + noteType
        );
    }

    private void showMatchingEntries(List<LiftEntry> matches, String title) {
        entrySubsetOverride = matches.stream().distinct().toList();
        entrySubsetTitle = title;
        keepEntrySubsetOnNextEntryView = true;
        switchView(NAV_ENTRIES);
    }

    private void navigateToObject(Object obj) {
        if (obj instanceof LiftEntry e) {
            switchView(NAV_ENTRIES);
            selectEntryInTable(e);
            populateEntryEditor(e);
        } else if (obj instanceof LiftSense s) {
            LiftEntry entry = s.getParentEntry();
                switchView(NAV_ENTRIES);
                selectEntryInTable(entry);
                populateEntryEditor(entry);
        } else if (obj instanceof LiftExample ex) {
            LiftEntry entry = ex.getParent().getParentEntry();
                    switchView(NAV_ENTRIES);
                    selectEntryInTable(entry);
                    populateEntryEditor(entry);
        } else if (obj instanceof LiftNote n) {
            AbstractNotable p = n.getParent();
            if (p instanceof LiftEntry e) {
                switchView(NAV_ENTRIES);
                selectEntryInTable(e);
                populateEntryEditor(e);
            } else if (p instanceof LiftSense s) {
                LiftEntry entry = s.getParentEntry();
                    switchView(NAV_ENTRIES);
                    selectEntryInTable(entry);
                    populateEntryEditor(entry);
            }
        } else if (obj instanceof LiftVariant v) {
            if (v.getParent() != null) {
                switchView(NAV_ENTRIES);
                selectEntryInTable(v.getParent());
                populateEntryEditor(v.getParent());
            }
        } else if (obj instanceof LiftEtymology et) {
            LiftEntry entry = et.getParent();
            if (entry != null) {
                switchView(NAV_ENTRIES);
                selectEntryInTable(entry);
                populateEntryEditor(entry);
            }
        } else if (obj instanceof LiftRelation r) {
            HasRelations p = r.getParent();
            if (p instanceof LiftEntry e) {
                switchView(NAV_ENTRIES);
                selectEntryInTable(e);
                populateEntryEditor(e);
            } else if (p instanceof LiftSense s) {
                LiftEntry entry = s.getParentEntry();
                    switchView(NAV_ENTRIES);
                    selectEntryInTable(entry);
                    populateEntryEditor(entry);
            } else if (p instanceof LiftVariant v) {
                if (v.getParent() != null) {
                    switchView(NAV_ENTRIES);
                    selectEntryInTable(v.getParent());
                    populateEntryEditor(v.getParent());
                }
            }
        } else if (obj instanceof LiftPronunciation p) {
            if (p.getParent() instanceof LiftEntry e) {
                switchView(NAV_ENTRIES);
                selectEntryInTable(e);
                populateEntryEditor(e);
            }
        }
    }

    private void selectEntryInTable(LiftEntry entry) {
        entryTable.getSelectionModel().select(entry);
    }

    private void populateAnnotationSummaryEditor(LiftAnnotation annotation) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(
            I18n.get("col.parentType"),
            describeParentType(annotation.getParent())
        );
        values.put(I18n.get("col.name"), annotation.getType().getId());
        values.put(I18n.get("col.value"), annotation.getValue());
        values.put(I18n.get("col.who"), annotation.getWho());
        values.put(I18n.get("col.when"), annotation.getWhen());
        populateSummaryEditor(I18n.get("nav.annotations"), "", values);
        addGoToParentButton(annotation.getParent());
    }

    private void populateFieldSummaryEditor(LiftField field) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(
            I18n.get("col.parentType"),
            describeParentType(field.getParent())
        );
        values.put(I18n.get("col.type"), field.getSpecification().getName());
        values.put(
            I18n.get("col.text"),
            field
                .getText()
                .getForms()
                .stream()
                .findFirst()
                .map(Form::toPlainText)
                .orElse("")
        );
        populateSummaryEditor(I18n.get("nav.fields"), "", values);
        addGoToParentButton(field.getParent());
    }

    private void showObjectsWithFieldType(String fieldType) {
        if (currentDictionary == null) return;
        List<LiftEntry> matches = currentDictionary
            .getLiftDictionaryRegistry()
            .getFields()
            .stream()
            .filter(f -> fieldType.equals(f.getSpecification().getName()))
            .map(LiftField::getParent)
            .map(parent -> {
                if (parent instanceof LiftEntry e) return Optional.of(e);
                if (parent instanceof LiftSense s) return Optional.of(s.getParentEntry()); //findParentEntry(s);
                return Optional.<LiftEntry>empty();
            })
            .flatMap(Optional::stream)
            .collect(Collectors.toList());
        showMatchingEntries(matches, I18n.get("nav.fields") + ": " + fieldType);
    }

    private void addGoToParentButton(Object parent) {
        if (parent == null) return;
        Button goBtn = new Button(I18n.get("btn.goToParent"));
        goBtn.getStyleClass().addAll("example-add-button", "no-dirty");
        goBtn.setMaxWidth(Double.MAX_VALUE);
        goBtn.setOnAction(e -> navigateToObject(parent));
        editorContainer.getChildren().add(goBtn);
    }

    private void populateCategorySummaryEditor(String title, CategoryRow row) {
        populateCategorySummaryEditor(title, row, null);
    }

    private void populateCategorySummaryEditor(
        String title,
        CategoryRow row,
        String categoryKind
    ) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.value"), row.value());
        values.put(I18n.get("col.frequency"), String.valueOf(row.frequency()));
        populateSummaryEditor(title, "", values);
        if (categoryKind != null) {
            Button accessBtn = new Button(I18n.get("btn.accessObjects"));
            accessBtn.getStyleClass().addAll("example-add-button", "no-dirty");
            accessBtn.setMaxWidth(Double.MAX_VALUE);
            final String val = row.value();
            accessBtn.setOnAction(e -> {
                if (
                    "grammatical-info".equals(categoryKind)
                ) showObjectsWithGramInfo(val);
                else if (
                    "translation-type".equals(categoryKind)
                ) showObjectsWithTranslationType(val);
                else if (
                    "relation-type".equals(categoryKind)
                ) showObjectsWithRelationType(val);
            });
            editorContainer.getChildren().add(accessBtn);
        }
    }

    private void showObjectsWithGramInfo(String gramInfoValue) {
        if (currentDictionary == null) return;
        List<LiftSense> matches = currentDictionary
            .getLiftDictionaryRegistry()
            .getSenses()
            .stream()
            .filter(s ->
                s
                    .getGrammaticalInfo()
                    .map(g -> gramInfoValue.equals(g.getGramInfoValue().getId()))
                    .orElse(false)
            )
            .distinct()
            .collect(Collectors.toList());
        showMatchingSenses(matches);
    }

    private void showMatchingSenses(List<LiftSense> matches) {
        senseSubsetOverride = matches;
        keepSenseSubsetOnNextSenseView = true;
        switchView(NAV_SENSES);
    }

    private void showObjectsWithTranslationType(String transType) {
        if (currentDictionary == null) return;
        List<LiftEntry> matches = currentDictionary
            .getLiftDictionaryRegistry()
            .getExamples()
            .stream()
            .filter(ex -> translationOfType(ex, transType) != null)
            .map(x -> x.getParent().getParentEntry())
            .collect(Collectors.toList());
        showMatchingEntries(
            matches,
            I18n.get("nav.transTypes") + ": " + transType
        );
    }

    private void showObjectsWithRelationType(String relationType) {
        if (currentDictionary == null) return;
        List<LiftEntry> matches = currentDictionary
            .getLiftDictionaryRegistry()
            .getRelations()
            .stream()
            .filter(r -> relationType.equals(r.getType().getId()))
            .map(LiftRelation::getParent)
            .map(parent -> {
                if (parent instanceof LiftEntry e) return Optional.of(e);
                if (parent instanceof LiftSense s) return Optional.of(s.getParentEntry()); //findParentEntry(s);
                if (parent instanceof LiftVariant v) return Optional.ofNullable(
                    v.getParent()
                );
                return Optional.<LiftEntry>empty();
            })
            .flatMap(Optional::stream)
            .collect(Collectors.toList());
        showMatchingEntries(
            matches,
            I18n.get("nav.relationTypes") + ": " + relationType
        );
    }

    private void populateSummaryEditor(
        String title,
        String code,
        LinkedHashMap<String, String> values
    ) {
        editedEntry = null;
        editEntryTitle.setText(title);
        editEntryCode.setText(code == null ? "" : code);
        editorContainer.getChildren().clear();

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        int row = 0;
        for (Map.Entry<String, String> e : values.entrySet()) {
            addReadOnlyRow(
                g,
                row++,
                e.getKey(),
                e.getValue() == null ? "" : e.getValue()
            );
        }
        editorContainer.getChildren().add(g);
    }

    /**
     * Adds a "Back to …" button leading to the parent of the object shown: the entry (or the
     * entry of a variant), or the sense. Does nothing for other parents.
     */
    private void addBackToParentButton(Object parent) {
        String label;
        Runnable action;
        if (parent instanceof LiftVariant v) parent = v.getParent();
        if (parent instanceof LiftEntry entry) {
            label = I18n.get("sense.backToEntry", entryLabel(entry));
            action = () -> navigateToEntryFromSense(entry);
        } else if (parent instanceof LiftSense sense) {
            label = I18n.get("sense.backToSense", LiftEntryEditor.senseDisplayText(sense));
            action = () -> navigateToSenseFromParent(sense);
        } else {
            return;
        }
        Button backBtn = new Button(label);
        backBtn.getStyleClass().addAll("example-add-button", "back-btn", "no-dirty");
        backBtn.setOnAction(e -> action.run());
        editorContainer.getChildren().add(backBtn);
    }

    /** First non-empty form of an entry, or "?". */
    private static String entryLabel(LiftEntry entry) {
        return entry.getForms().getForms().stream()
            .map(Form::toPlainText)
            .filter(t -> t != null && !t.isBlank())
            .findFirst()
            .orElse("?");
    }

    /** The red "Delete" button shown at the top of an object's editor. */
    private static Button newDeleteButton() {
        Button deleteBtn = new Button(I18n.get(Keys.BTN_DELETE));
        deleteBtn.getStyleClass().add("delete-btn");
        deleteBtn.setStyle(
            "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 7 12 7 12;"
        );
        return deleteBtn;
    }

    /**
     * Adds a "Delete" button to the editor panel for a component shown in its own view
     * (note, variant, relation, etymology). The deletion asks for confirmation and can be undone.
     */
    private void addComponentDeleteButton(Runnable remove, ComponentDeletion.CheckedRunnable restore) {
        Button deleteBtn = newDeleteButton();
        deleteBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle(I18n.get(Keys.CONFIRM_DELETE_TITLE));
            confirm.setHeaderText(null);
            confirm.setContentText(I18n.get("confirm.delete.component"));
            confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r ->
                ComponentDeletion.deleteChecked(remove, restore, () -> {
                    editorContainer.getChildren().clear();
                    editEntryTitle.setText(I18n.get("panel.selectElement"));
                    editEntryCode.setText("");
                    refreshCurrentView();
                })
            );
        });
        editorContainer.getChildren().add(deleteBtn);
    }

    private void populateNoteEditor(LiftNote note) {
        editedEntry = owningEntry(note);
        editEntryTitle.setText(
            I18n.get("nav.notes") + " : " + EditorSupport.featureId(note.getType())
        );
        editEntryCode.setText("");
        editorContainer.getChildren().clear();
        AbstractNotable parent = note.getParent();
        if (parent != null) {
            addComponentDeleteButton(() -> parent.deleteNote(note), () -> parent.addNote(note));
        }
        addBackToParentButton(parent);
        NoteEditor ne = new NoteEditor(currentDictionary);
        ne.setNote(note);
        editorContainer.getChildren().add(ne);
    }

    private void populateVariantEditor(LiftVariant v) {
        editedEntry = owningEntry(v);
        editEntryTitle.setText(
            I18n.get("nav.variants") + " : " + v.getRefId().orElse("?")
        );
        editEntryCode.setText("");
        editorContainer.getChildren().clear();
        LiftEntry parentEntry = v.getParent();
        if (parentEntry != null) {
            addComponentDeleteButton(() -> parentEntry.deleteVariant(v), () -> parentEntry.addVariant(v));
        }
        addBackToParentButton(parentEntry);
        VariantEditor ve = new VariantEditor(currentDictionary);
        ve.setVariant(
            v,
            createVariantAddActions(v)
        );
        editorContainer.getChildren().add(ve);
    }

    private ExtensibleAddActions createSenseAddActions(LiftSense s) {
        return new ExtensibleAddActions() {
            @Override
            public void addTrait(String name, String value) {
                currentDictionary.getComponentBuilder().trait(s, name, value).build();
            }

            @Override
            public void addAnnotation(String name) {
                currentDictionary.getComponentBuilder().annotation(s, name).build();
            }

            @Override
            public void addField(String type) {
                currentDictionary.getComponentBuilder().field(s, type).build();
            }

            @Override
            public void addNote(String type) {
                currentDictionary.getComponentBuilder().note(s, type).build();
            }

            @Override
            public void refresh() {
                if (NAV_SENSES.equals(currentView)) populateSenseEditor(s);
            }




        };
    }

    private ExtensibleAddActions createExampleAddActions(LiftExample ex) {
        return new ExtensibleAddActions() {
            @Override
            public void addTrait(String name, String value) {
                currentDictionary.getComponentBuilder().trait(ex, name, value).build();
            }

            @Override
            public void addAnnotation(String name) {
                currentDictionary.getComponentBuilder().annotation(ex, name).build();
            }

            @Override
            public void addField(String type) {
                currentDictionary.getComponentBuilder().field(ex, type).build();
            }

            @Override
            public void addNote(String type) {
                currentDictionary.getComponentBuilder().note(ex, type).build();
            }

            @Override
            public void refresh() {
                if (NAV_EXAMPLES.equals(currentView)) populateExampleEditor(ex);
            }




        };
    }

    private ExtensibleAddActions createVariantAddActions(LiftVariant v) {
        return new ExtensibleAddActions() {
            @Override
            public void addTrait(String name, String value) {
                currentDictionary.getComponentBuilder().trait(v, name, value).build();
            }

            @Override
            public void addAnnotation(String name) {
                currentDictionary.getComponentBuilder().annotation(v, name).build();
            }

            @Override
            public void addField(String type) {
                currentDictionary.getComponentBuilder().field(v, type).build();
            }

            @Override
            public void addPronunciation() {
                currentDictionary.getComponentBuilder().pronunciation(v).build();
            }

            @Override
            public void addRelation(String type) {
                currentDictionary.getComponentBuilder().relation(type, v).build();
            }

            @Override
            public void refresh() {
                if (NAV_VARIANTS.equals(currentView)) populateVariantEditor(v);
            }




        };
    }

    /* ─── Setup generic tables ─── */

    private void setupGenericTables() {
        entryTable.setPlaceholder(new Label(I18n.get("placeholder.noData")));
        senseTable.setPlaceholder(new Label(I18n.get("placeholder.noSense")));
        exampleTable.setPlaceholder(
            new Label(I18n.get("placeholder.noExample"))
        );
        variantTable.setPlaceholder(
            new Label(I18n.get("placeholder.noVariant"))
        );
        relationTable.setPlaceholder(
            new Label(I18n.get("placeholder.noRelation"))
        );
        traitTable.setPlaceholder(new Label(I18n.get("placeholder.noTrait")));
        annotationTable.setPlaceholder(
            new Label(I18n.get("placeholder.noAnnotation"))
        );
        fieldTable.setPlaceholder(new Label(I18n.get("placeholder.noField")));
        langFieldTable.setPlaceholder(
            new Label(I18n.get("placeholder.noMultiField"))
        );
        quickEntryTable.setPlaceholder(
            new Label(I18n.get("placeholder.quickEntryHint"))
        );
        // Selection listeners are installed once: the tables are reused by every visit of their view.
        senseTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateSenseEditor(n);
        });
        exampleTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateExampleEditor(n);
        });
        variantTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateVariantEditor(n);
        });
        relationTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateRelationEditor(n);
        });
        traitTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateTraitSummaryEditor(n);
        });
        annotationTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateAnnotationSummaryEditor(n);
        });
        fieldTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateFieldSummaryEditor(n);
        });
        langFieldTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateLangFieldEditor(n);
        });

    }

    /* ────────────────── FILTER / SEARCH ────────────────── */

    private void applyCurrentFilter() {
        String q = Optional.ofNullable(searchField.getText())
            .orElse("")
            .trim()
            .toLowerCase(Locale.ROOT);
        if (currentView.equals(NAV_ENTRIES)) {
            List<LiftEntry> entrySource =
                entrySubsetOverride != null ? entrySubsetOverride : baseEntries;
            Set<LiftEntry> sourceSet = Collections.newSetFromMap(new IdentityHashMap<>());
            sourceSet.addAll(entrySource);
            // Search text computed once per entry for this filtering pass.
            Map<LiftEntry, String> searchText = new IdentityHashMap<>();
            java.util.function.Function<LiftEntry, String> searchTextOf = e ->
                searchText.computeIfAbsent(e, x -> buildSearchText(x).toLowerCase(Locale.ROOT));
            List<TableColumn<LiftEntry, ?>> leaves = collectLeafColumns(
                entryTable
            );
            String clearOption = I18n.get("filter.clear");
            filteredEntries.setPredicate(entry -> {
                if (entry == null || !sourceSet.contains(entry)) return false;
                if (!q.isEmpty() && !searchTextOf.apply(entry).contains(q)) return false;
                for (
                    int i = 0;
                    i < entryColumnFilters.size() && i < leaves.size();
                    i++
                ) {
                    String selected = entryColumnFilters.get(i).getValue();
                    if (!isActiveFilter(selected, clearOption)) continue;
                    Object val =
                        leaves.get(i).getCellObservableValue(entry) != null
                            ? leaves
                                  .get(i)
                                  .getCellObservableValue(entry)
                                  .getValue()
                            : null;
                    String cellText = val != null ? val.toString() : "";
                    if (!selected.equals(cellText)) return false;
                }
                return true;
            });
            refreshEntryFacetChoices(q, leaves, clearOption, entrySource, searchTextOf);
            updateCountLabel(filteredEntries.size(), entrySource.size());
        }
    }

    private void refreshEntryFacetChoices(
        String q,
        List<TableColumn<LiftEntry, ?>> leaves,
        String clearOption,
        List<LiftEntry> entrySource,
        java.util.function.Function<LiftEntry, String> searchTextOf
    ) {
        if (entryColumnFilters.isEmpty()) return;
        entryFilterInternalUpdate = true;
        try {
            for (
                int i = 0;
                i < entryColumnFilters.size() && i < leaves.size();
                i++
            ) {
                ComboBox<String> combo = entryColumnFilters.get(i);
                String currentValue = combo.getValue();
                final int colIndex = i;

                List<String> values = entrySource
                    .stream()
                    .filter(
                        e ->
                            e != null &&
                            (q.isEmpty() || searchTextOf.apply(e).contains(q))
                    )
                    .filter(e ->
                        rowMatchesEntryFiltersExcluding(
                            e,
                            leaves,
                            clearOption,
                            colIndex
                        )
                    )
                    .map(e -> {
                        Object v =
                            leaves.get(colIndex).getCellObservableValue(e) !=
                            null
                                ? leaves
                                      .get(colIndex)
                                      .getCellObservableValue(e)
                                      .getValue()
                                : null;
                        return v == null ? "" : v.toString();
                    })
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

                ObservableList<String> items =
                    FXCollections.observableArrayList();
                items.add(clearOption);
                items.addAll(values);
                combo.setItems(items);

                if (
                    currentValue != null && items.contains(currentValue)
                ) combo.setValue(currentValue);
                else combo.setValue(clearOption);
            }
        } finally {
            entryFilterInternalUpdate = false;
        }
    }

    private boolean rowMatchesEntryFiltersExcluding(
        LiftEntry entry,
        List<TableColumn<LiftEntry, ?>> leaves,
        String clearOption,
        int ignoredColumn
    ) {
        for (
            int i = 0;
            i < entryColumnFilters.size() && i < leaves.size();
            i++
        ) {
            if (i == ignoredColumn) continue;
            String selected = entryColumnFilters.get(i).getValue();
            if (!isActiveFilter(selected, clearOption)) continue;
            Object val =
                leaves.get(i).getCellObservableValue(entry) != null
                    ? leaves.get(i).getCellObservableValue(entry).getValue()
                    : null;
            String cellText = val != null ? val.toString() : "";
            if (!selected.equals(cellText)) return false;
        }
        return true;
    }

    /* ────────────────── DICTIONARY MANAGEMENT ────────────────── */

    private void setDictionary(LiftDictionary dictionary) {
        if (currentDictionary != null) {
            Bindings.unbindContent(baseEntries, currentDictionary.getLiftDictionaryRegistry().getEntries());
        }
        this.currentDictionary = dictionary;
        undoManager.clear();
        baseEntries.clear();
        if (dictionary == null) {
            updateCountLabel(0, 0);
            return;
        }
        rebuildHeaderCfgChildren();
        // baseEntries mirrors the dictionary's entry list, so entries created, deleted or
        // restored (undo) anywhere show up in the entry table.
        Bindings.bindContent(baseEntries, dictionary.getLiftDictionaryRegistry().getEntries());
        configureEntryTableColumns();
        if (currentView.equals(NAV_ENTRIES)) {
            applyCurrentFilter();
            if (!filteredEntries.isEmpty()) entryTable
                .getSelectionModel()
                .selectFirst();
        }
    }

    /* ────────────────── MENU HANDLERS ────────────────── */

    @FXML
    private void onImportLift() {
        if (!confirmDiscardChanges()) return;
        FileChooser ch = new FileChooser();
        ch.setTitle(I18n.get("dialog.openLift"));
        File defaultDir = new File(PREFS.get("ui.defaultPath", System.getProperty("user.home")));
        if (defaultDir.exists()) ch.setInitialDirectory(defaultDir);
        ch.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(I18n.get("dialog.liftFilter"), "*.lift"),
            new FileChooser.ExtensionFilter(I18n.get("dialog.allFilter"), "*.*")
        );
        File f = ch.showOpenDialog(navTree.getScene().getWindow());
        if (f == null) return;
        openDictionaryFile(f);
    }

    /** Saves to the current file; a new or demo dictionary goes through "Save as". */
    @FXML
    private void onSave() {
        if (currentDictionary == null) {
            showError(I18n.get("error.save"), I18n.get("error.noDictionary"));
            return;
        }
        if (currentFile == null || demo) {
            onSaveAs();
            return;
        }
        try {
            currentDictionary.save(currentFile);
            dirty = false;
            updateWindowTitle();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Sauvegarde du dictionnaire", e);
            showError(
                I18n.get("error.save"),
                I18n.formatErrorMessage("error.save.detail", e)
            );
        }
    }

    /** Creates an empty dictionary, asking for its object and meta languages. */
    @FXML
    private void onNewDictionary() {
        if (!confirmDiscardChanges()) return;
        Dialog<String[]> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("dialog.newDictionary"));
        dlg.setHeaderText(I18n.get("dialog.newDictionaryHeader"));
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField objField = new TextField();
        objField.setPromptText("xyz");
        TextField metaField = new TextField();
        metaField.setPromptText("en, fr");
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label(I18n.get("nav.objectLangs")), 0, 0);
        grid.add(objField, 1, 0);
        grid.add(new Label(I18n.get("nav.metaLangs")), 0, 1);
        grid.add(metaField, 1, 1);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt == ButtonType.OK
            ? new String[] { objField.getText(), metaField.getText() }
            : null);
        dlg.showAndWait().ifPresent(langs -> {
            String[] obj = splitLanguageCodes(langs[0]);
            String[] meta = splitLanguageCodes(langs[1]);
            if (obj.length == 0 || meta.length == 0) {
                showError(I18n.get("dialog.newDictionary"), I18n.get("error.newDictionaryLanguages"));
                return;
            }
            setDictionary(
                LiftDictionary.makeBuilder()
                    .withObjectLanguages(obj)
                    .withMetaLanguages(meta)
                    .build()
            );
            currentFile = null;
            demo = false;
            dirty = false;
            updateWindowTitle();
            switchView(NAV_ENTRIES);
        });
    }

    private static String[] splitLanguageCodes(String text) {
        if (text == null) return new String[0];
        return java.util.Arrays.stream(text.split("[,\\s]+"))
            .map(String::trim)
            .filter(t -> !t.isEmpty())
            .distinct()
            .toArray(String[]::new);
    }

    @FXML
    private void onSaveAs() {
        if (currentDictionary == null) {
            showError(
                I18n.get("error.saveAs"),
                I18n.get("error.noDictionaryShort")
            );
            return;
        }
        FileChooser ch = new FileChooser();
        ch.setTitle(I18n.get("dialog.saveLift"));
        ch.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(I18n.get("dialog.liftFilter"), "*.lift")
        );
        if (currentFile != null && !demo) {
            ch.setInitialDirectory(currentFile.getParentFile());
            ch.setInitialFileName(currentFile.getName());
        }
        File f = ch.showSaveDialog(navTree.getScene().getWindow());
        if (f == null) return;
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".lift")) {
            f = new File(f.getParentFile(), f.getName() + ".lift");
        }
        try {
            currentDictionary.save(f);
            currentFile = f;
            demo = false;
            dirty = false;
            updateWindowTitle();
            saveRecentFile(f);
        } catch (Exception e) {
            LOGGER.log(
                Level.SEVERE,
                "Sauvegarde du dictionnaire sous un autre fichier",
                e
            );
            showError(
                I18n.get("error.saveAs"),
                I18n.formatErrorMessage("error.saveAs.detail", e)
            );
        }
    }

    @FXML
    private void onPreferences() {
        showPreferencesDialog();
    }

    @FXML
    private void onQuit() {
        if (confirmDiscardChanges()) Platform.exit();
    }

    /** Copies the selected text, or in the Entries view the selected entry (id and forms). */
    @FXML
    private void onCopy() {
        javafx.scene.Node focused = menuBar.getScene().getFocusOwner();
        if (focused instanceof TextInputControl tic) {
            tic.copy();
        } else if (NAV_ENTRIES.equals(currentView)) {
            copySelectedToClipboard();
        }
    }

    /**
     * Pastes into the focused text field; in the Entries view, outside a text field, creates a
     * new entry whose form (first object language) is the clipboard text.
     */
    @FXML
    private void onPaste() {
        javafx.scene.Node focused = menuBar.getScene().getFocusOwner();
        if (focused instanceof TextInputControl tic) {
            tic.paste();
            return;
        }
        if (!NAV_ENTRIES.equals(currentView) || currentDictionary == null) return;
        String text = Clipboard.getSystemClipboard().getString();
        if (text == null || text.isBlank()) return;
        Optional<String> lang = currentDictionary.getObjectLanguageManager().getLanguages().stream().sorted().findFirst();
        if (lang.isEmpty()) return;
        LiftEntry entry = currentDictionary.getComponentBuilder().entry().withForm(lang.get(), text.trim()).build();
        markDirty();
        applyCurrentFilter();
        entryTable.getSelectionModel().select(entry);
        entryTable.scrollTo(entry);
    }

    /** Cuts the selected text of the focused text field. */
    @FXML
    private void onCut() {
        javafx.scene.Node focused = menuBar.getScene().getFocusOwner();
        if (focused instanceof TextInputControl tic) tic.cut();
    }

    private void copySelectedToClipboard() {
        LiftEntry e = entryTable.getSelectionModel().getSelectedItem();
        if (e == null) return;
        ClipboardContent cc = new ClipboardContent();
        StringBuilder sb = new StringBuilder();
        sb.append(e.getId().orElse(""));
        for (Form f : e.getForms().getForms())
            sb.append("\t").append(f.toPlainText());
        cc.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(cc);
    }

    /* ─── Vue menu items: delegate to switchView ─── */
    @FXML
    private void onViewEntries() {
        switchView(NAV_ENTRIES);
    }

    @FXML
    private void onViewSenses() {
        switchView(NAV_SENSES);
    }

    @FXML
    private void onViewExamples() {
        switchView(NAV_EXAMPLES);
    }

    @FXML
    private void onViewNotes() {
        switchView(NAV_NOTES);
    }

    @FXML
    private void onViewVariants() {
        switchView(NAV_VARIANTS);
    }

    @FXML
    private void onViewEtymologies() {
        switchView(NAV_ETYMOLOGIES);
    }

    @FXML
    private void onViewObjectLangs() {
        switchView(NAV_OBJ_LANGS);
    }

    @FXML
    private void onViewMetaLangs() {
        switchView(NAV_META_LANGS);
    }

    @FXML
    private void onViewTraits() {
        switchView(NAV_TRAITS);
    }

    @FXML
    private void onViewAnnotations() {
        switchView(NAV_ANNOTATIONS);
    }

    @FXML
    private void onViewFields() {
        switchView(NAV_FIELDS);
    }

    @FXML
    private void onViewGramInfo() {
        switchView(NAV_GRAM_INFO);
    }

    @FXML
    private void onViewTransTypes() {
        switchView(NAV_TRANS_TYPES);
    }

    @FXML
    private void onViewNoteTypes() {
        switchView(NAV_NOTE_TYPES);
    }

    @FXML
    private void onViewRelationTypes() {
        switchView(NAV_RELATION_TYPES);
    }

    /* ─── Header type views (note, translation, annotation, relation types) ─── */

    private void showConfigNoteTypesView() {
        showFeatureSetView(I18n.get("nav.cfgManageNoteTypes"), headerOrNull(LiftHeader::getNoteTypeManager));
    }

    private void showConfigTranslationTypesView() {
        showFeatureSetView(I18n.get("nav.cfgManageTransTypes"), headerOrNull(LiftHeader::getTranslationTypeManager));
    }

    private void showConfigAnnotationTypesView() {
        showFeatureSetView(I18n.get("nav.cfgManageAnnotationTypes"), headerOrNull(LiftHeader::getAnnotationTypeManager));
    }

    private void showConfigRelationTypesView() {
        showFeatureSetView(I18n.get("nav.cfgManageRelationTypes"), headerOrNull(LiftHeader::getRelationTypeManager));
    }

    private FeatureSet headerOrNull(java.util.function.Function<LiftHeader, FeatureSet> manager) {
        return currentDictionary == null ? null : manager.apply(currentDictionary.getHeader());
    }

    private void showManageLanguagesView() {
        if (currentDictionary == null) {
            tableContainer
                .getChildren()
                .setAll(new Label(I18n.get("cfg.noHeader")));
            return;
        }
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        Label title = new Label(I18n.get("nav.cfgManageLangs"));
        title.setStyle(
            "-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#4c6f76;"
        );

        TitledPane objPane = new TitledPane(
            I18n.get("nav.objectLangs"),
            buildEditableLanguagePanel(
                currentDictionary.getObjectLanguageManager(),
                currentDictionary.getLiftDictionaryRegistry().getObjectText()
            )
        );
        objPane.setExpanded(true);
        objPane.setAnimated(false);
        TitledPane metaPane = new TitledPane(
            I18n.get("nav.metaLangs"),
            buildEditableLanguagePanel(
                currentDictionary.getMetaLanguageManager(),
                currentDictionary.getLiftDictionaryRegistry().getMetaText()
            )
        );
        metaPane.setExpanded(true);
        metaPane.setAnimated(false);

        box.getChildren().addAll(title, objPane, metaPane);
        tableContainer.getChildren().setAll(box);
        editorContainer.getChildren().clear();
        editEntryTitle.setText(I18n.get("nav.cfgManageLangs"));
        editEntryCode.setText("");
    }

    /** Builds an editable panel for a language list (object or meta) with add/delete. */
    private VBox buildEditableLanguagePanel(
        LiftDictionaryLanguagesManager manager,
        ObservableList<MultiText> multiTexts
    ) {
        TableView<String> table = new TableView<>();
        Runnable reload = () -> table.setItems(
            FXCollections.observableArrayList(manager.getLanguages().stream().sorted().toList())
        );
        reload.run();
        table.setPrefHeight(140);
        TableColumn<String, String> langCol = new TableColumn<>(I18n.get("col.code"));
        langCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue()));
        langCol.setPrefWidth(120);
        TableColumn<String, String> usageCol = new TableColumn<>(I18n.get("cfg.usageCount"));
        usageCol.setCellValueFactory(cd ->
            new ReadOnlyStringWrapper(String.valueOf(countLanguageUsage(cd.getValue(), multiTexts)))
        );
        usageCol.setPrefWidth(80);
        table.getColumns().addAll(List.of(langCol, usageCol));

        TextField addField = new TextField();
        addField.setPromptText(I18n.get("config.addAbbr"));
        Button addBtn = new Button(I18n.get("btn.add"));
        addBtn.setOnAction(e -> {
            String code = addField.getText().trim();
            if (code.isEmpty() || manager.hasLanguage(code)) return;
            manager.addLanguage(code);
            markDirty();
            addField.clear();
            reload.run();
        });
        Button removeBtn = new Button(I18n.get("btn.delete"));
        removeBtn.setOnAction(e -> {
            String sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            long usage = countLanguageUsage(sel, multiTexts);
            if (usage > 0) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle(I18n.get("btn.delete"));
                confirm.setHeaderText(I18n.get("config.deleteWarning", sel, usage));
                confirm.setContentText(I18n.get("config.deleteConfirm"));
                if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isEmpty()) return;
            }
            // Removing the forms drops the language occurrences; then the language itself.
            for (MultiText mt : List.copyOf(multiTexts)) {
                if (mt.getForm(sel).isPresent()) mt.removeForm(sel);
            }
            markDirty();
            try {
                if (manager.hasLanguage(sel)) manager.removeLanguage(sel);
            } catch (IllegalArgumentException ex) {
                // Still used somewhere outside the listed texts (e.g. the header).
                showError(I18n.get("btn.delete"), I18n.get("error.languageStillUsed", sel));
            }
            reload.run();
        });

        HBox controls = new HBox(8, addField, addBtn, removeBtn);
        controls.setPadding(new Insets(6, 0, 0, 0));
        HBox.setHgrow(addField, Priority.ALWAYS);
        return new VBox(6, table, controls);
    }

    private long countLanguageUsage(String lang, List<MultiText> multiTexts) {
        if (lang == null || lang.isBlank() || multiTexts == null) return 0;
        return multiTexts
            .stream()
            .filter(mt -> mt.getForm(lang).isPresent())
            .count();
    }

    /* ─── Outil menu ─── */
    /**
     * Checks the dictionary for common problems and shows a report: entries without form,
     * senses without gloss or definition, references to unknown entries or senses, fields and
     * traits whose name is not declared in the header, trait values outside their range,
     * types not declared in the header, and empty forms.
     */
    @FXML
    private void onValidateDictionary() {
        if (currentDictionary == null) {
            showError(
                I18n.get("error.validation"),
                I18n.get("error.noDictionaryShort")
            );
            return;
        }
        var reg = currentDictionary.getLiftDictionaryRegistry();
        Map<String, List<String>> issues = new LinkedHashMap<>();
        java.util.function.BiConsumer<String, String> issue = (key, where) ->
            issues.computeIfAbsent(I18n.get(key), k -> new ArrayList<>()).add(where);

        for (LiftEntry e : reg.getEntries()) {
            if (e.getForms().isEmpty()) issue.accept("validate.entryWithoutForm", describeForReport(e));
        }
        for (LiftSense sn : reg.getSenses()) {
            if (sn.getGlosses().isEmpty() && sn.getDefinition().isEmpty()) {
                issue.accept("validate.senseWithoutGloss", describeForReport(sn));
            }
        }
        for (LiftRelation r : reg.getRelations()) {
            if (r.getRefID().filter(id -> !id.isBlank()).isPresent() && r.getRefObject() == null) {
                issue.accept("validate.danglingReference", describeForReport(r) + " → " + r.getRefID().get());
            }
        }
        for (LiftVariant v : reg.getVariants()) {
            if (v.getRefId().filter(id -> !id.isBlank()).isPresent() && v.getRefObject() == null) {
                issue.accept("validate.danglingReference", describeForReport(v) + " → " + v.getRefId().get());
            }
        }
        for (LiftField f : reg.getFields()) {
            if (f.getSpecification().getKind() == LiftFieldAndTraitDefinitionKind.UNKNOWN) {
                issue.accept("validate.undeclaredField", describeForReport(f) + " : " + f.getSpecification().getName());
            }
        }
        for (LiftTrait t : reg.getTraits()) {
            LiftFieldAndTraitDefinition spec = t.getSpecification();
            if (spec.getKind() == LiftFieldAndTraitDefinitionKind.UNKNOWN) {
                issue.accept("validate.undeclaredTrait", describeForReport(t) + " : " + spec.getName());
            }
            spec.getResolvedFeatureSet().ifPresent(set -> {
                for (String token : traitValueTokens(t)) {
                    if (!set.hasFeature(token)) {
                        issue.accept("validate.traitValueOutOfRange",
                            describeForReport(t) + " : " + spec.getName() + " = " + token);
                    }
                }
            });
        }
        List<HasType> typed = new ArrayList<>();
        typed.addAll(reg.getNotes());
        typed.addAll(reg.getTranslations());
        typed.addAll(reg.getAnnotations());
        typed.addAll(reg.getRelations());
        typed.addAll(reg.getEtymologies());
        typed.addAll(reg.getReversals());
        for (HasType x : typed) {
            Feature type = x.getType();
            if (type == null) continue;
            FeatureSet set = type.getParent();
            if (set == null || set.getFeature(type.getId()) != type) {
                issue.accept("validate.undeclaredType", describeForReport(x) + " : " + type.getId());
            }
        }
        List<MultiText> texts = new ArrayList<>(reg.getObjectText());
        texts.addAll(reg.getMetaText());
        for (MultiText mt : texts) {
            for (Form f : mt.getForms()) {
                if (f.toPlainText() == null || f.toPlainText().isBlank()) {
                    issue.accept("validate.emptyForm", describeForReport(mt.getParent()) + " [" + f.getLang() + "]");
                }
            }
        }

        StringBuilder report = new StringBuilder();
        int total = 0;
        for (var e : issues.entrySet()) {
            total += e.getValue().size();
            report.append(e.getKey()).append(" (").append(e.getValue().size()).append(")\n");
            e.getValue().stream().limit(200).forEach(w -> report.append("    ").append(w).append("\n"));
            if (e.getValue().size() > 200) report.append("    …\n");
            report.append("\n");
        }
        Alert a = new Alert(total == 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        a.setTitle(I18n.get("error.validation"));
        a.setHeaderText(
            I18n.get(
                "info.validationResult",
                reg.getEntries().size(),
                reg.getSenses().size(),
                reg.getExamples().size(),
                String.join(", ", currentDictionary.getObjectLanguageManager().getLanguages()),
                String.join(", ", currentDictionary.getMetaLanguageManager().getLanguages())
            ) + "\n" + (total == 0 ? I18n.get("validate.noIssue") : I18n.get("validate.issues", total))
        );
        if (total > 0) {
            TextArea area = new TextArea(report.toString());
            area.setEditable(false);
            area.setWrapText(false);
            area.setPrefSize(720, 420);
            a.getDialogPane().setContent(area);
            a.setResizable(true);
        }
        a.showAndWait();
    }

    /** Short description of a component for the validation report: its type and its entry. */
    private static String describeForReport(Object o) {
        String kind = o == null ? "" : o.getClass().getSimpleName().replaceFirst("^Lift", "");
        LiftEntry e = owningEntry(o);
        if (e == null) return kind;
        String form = e.getForms().getForms().stream()
            .map(Form::toPlainText)
            .filter(t -> t != null && !t.isBlank())
            .findFirst()
            .orElse(e.getId().orElse("?"));
        return kind + " — " + form;
    }

    @FXML
    private void onExportCsv() {
        if (currentDictionary == null) {
            showError(
                I18n.get("error.export"),
                I18n.get("error.noDictionaryShort")
            );
            return;
        }
        TableView<?> table = getCurrentTableView();
        if (table == null) {
            showError(
                I18n.get("error.export"),
                I18n.get("error.exportNoTable")
            );
            return;
        }
        FileChooser ch = new FileChooser();
        ch.setTitle(I18n.get("dialog.exportCsv"));
        ch.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                I18n.get("dialog.csvFilter"),
                "*.csv"
            )
        );
        File f = ch.showSaveDialog(navTree.getScene().getWindow());
        if (f == null) return;
        try {
            exportTableToCsv(table, f);
            showInfo(
                I18n.get("error.export"),
                I18n.get("info.exportSuccess", f.getAbsolutePath())
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Export CSV", e);
            showError(
                I18n.get("error.export"),
                I18n.formatErrorMessage("error.export.detail", e)
            );
        }
    }

    /** Returns the TableView currently displayed in tableContainer, or null. */
    private TableView<?> getCurrentTableView() {
        if (tableContainer.getChildren().isEmpty()) return null;
        javafx.scene.Node first = tableContainer.getChildren().get(0);
        if (first instanceof TableView<?> tv) return tv;
        if (first instanceof VBox vbox && vbox.getChildren().size() >= 3) {
            javafx.scene.Node third = vbox.getChildren().get(2);
            if (third instanceof TableView<?> tv) return tv;
        }
        return null;
    }

    /** Export table to CSV: column headers match table, comma-separated, values escaped per RFC 4180. */
    private static <T> void exportTableToCsv(TableView<T> table, File file)
        throws IOException {
        List<TableColumn<T, ?>> leaves = collectLeafColumns(table);
        if (leaves.isEmpty()) return;
        try (
            PrintWriter pw = new PrintWriter(
                file,
                java.nio.charset.StandardCharsets.UTF_8
            )
        ) {
            pw.print('\uFEFF'); // BOM for Excel UTF-8 recognition
            pw.print(escapeCsv(getColumnHeader(leaves.get(0))));
            for (int i = 1; i < leaves.size(); i++) {
                pw.print(",");
                pw.print(escapeCsv(getColumnHeader(leaves.get(i))));
            }
            pw.println();
            for (T row : table.getItems()) {
                pw.print(escapeCsv(cellText(row, leaves.get(0))));
                for (int i = 1; i < leaves.size(); i++) {
                    pw.print(",");
                    pw.print(escapeCsv(cellText(row, leaves.get(i))));
                }
                pw.println();
            }
        }
    }

    private static String getColumnHeader(TableColumn<?, ?> col) {
        String t = col.getText();
        return t != null ? t : "";
    }

    /** RFC 4180: wrap in quotes if contains comma, quote, or newline; double internal quotes. */
    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (
            value.contains(",") ||
            value.contains("\"") ||
            value.contains("\n") ||
            value.contains("\r")
        ) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /* ════════════════════ GRAMMATICAL INFO VIEW ════════════════════ */

    private void showGramInfoView() {
        if (currentDictionary == null) {
            tableContainer
                .getChildren()
                .setAll(new Label(I18n.get("placeholder.noDictionary")));
            return;
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LiftSense s : currentDictionary
            .getLiftDictionaryRegistry()
            .getSenses()) {
            s.getGrammaticalInfo().ifPresent(gi ->
                counts.merge(gi.getGramInfoValue().getId(), 1L, Long::sum)
            );
        }
        showCategoryTable(
            I18n.get("nav.gramInfo"),
            I18n.get("col.value"),
            counts,
            "grammatical-info"
        );
    }

    /* ════════════════════ TRANSLATION TYPES VIEW ════════════════════ */

    private void showTranslationTypesView() {
        if (currentDictionary == null) {
            tableContainer
                .getChildren()
                .setAll(new Label(I18n.get("placeholder.noDictionary")));
            return;
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LiftExample ex : currentDictionary
            .getLiftDictionaryRegistry()
            .getExamples()) {
            for (String type : ex.getTranslations().keySet().stream().map(x -> x.getId()).toList())
                counts.merge(type, 1L, Long::sum);
        }
        showCategoryTable(
            I18n.get("nav.transTypes"),
            I18n.get("col.type"),
            counts,
            "translation-type"
        );
    }

    /* ════════════════════ NOTE TYPES VIEW ════════════════════ */

    private record NoteTypeRow(
        String type,
        String parentType,
        long frequency
    ) {}

    private void showNoteTypesView() {
        if (currentDictionary == null) {
            tableContainer
                .getChildren()
                .setAll(new Label(I18n.get("placeholder.noDictionary")));
            return;
        }
        Map<String, NoteTypeRow> counts = new LinkedHashMap<>();
        for (LiftNote n : currentDictionary
            .getLiftDictionaryRegistry()
            .getNotes()) {
            String type = n.getType().getId();
            String pt = describeParentType(n.getParent());
            String key = type + "|" + pt;
            counts.compute(key, (k, row) -> {
                if (row == null) return new NoteTypeRow(type, pt, 1);
                return new NoteTypeRow(type, pt, row.frequency + 1);
            });
        }
        TableView<NoteTypeRow> table = new TableView<>();
        TableColumn<NoteTypeRow, String> noteFreqCol = col(
            I18n.get("col.frequency"),
            r -> String.valueOf(r.frequency())
        );
        noteFreqCol.getProperties().put("filterMode", FILTER_MODE_TEXT);
        table
            .getColumns()
            .addAll(
                col(I18n.get("col.type"), NoteTypeRow::type),
                col(I18n.get("col.parentType"), NoteTypeRow::parentType),
                noteFreqCol
            );
        counts
            .values()
            .stream()
            .sorted(Comparator.comparingLong(NoteTypeRow::frequency).reversed())
            .forEach(table.getItems()::add);
        table
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (n != null) populateNoteTypeSummaryEditor(n);
            });
        VBox wrapper = wrapTableWithFilters(
            table,
            (f, t) -> updateCountLabel(f, t),
            searchField != null ? searchField.textProperty() : null
        );
        tableContainer.getChildren().setAll(wrapper);
        updateCountLabel(table.getItems().size(), table.getItems().size());
    }

    /* ════════════════════ RELATION TYPES VIEW ════════════════════ */

    private void showRelationTypesView() {
        if (currentDictionary == null) {
            tableContainer
                .getChildren()
                .setAll(new Label(I18n.get("placeholder.noDictionary")));
            return;
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LiftRelation r : currentDictionary
            .getLiftDictionaryRegistry()
            .getRelations()) {
            counts.merge(
                r.getType().getId().isEmpty() ? I18n.get("placeholder.noType") : r.getType().getId(),
                1L,
                Long::sum
            );
        }
        showCategoryTable(
            I18n.get("nav.relationTypes"),
            I18n.get("col.type"),
            counts,
            "relation-type"
        );
    }

    /* ════════════════════ FIELD TYPES VIEW ════════════════════ */

    private record FieldTypeRow(String fieldType, long frequency) {}

    private void showFieldTypesView() {
        if (currentDictionary == null) {
            tableContainer
                .getChildren()
                .setAll(new Label(I18n.get("placeholder.noDictionary")));
            return;
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LiftField f : currentDictionary
            .getLiftDictionaryRegistry()
            .getFields()) {
            counts.merge(f.getSpecification().getName(), 1L, Long::sum);
        }
        TableView<FieldTypeRow> table = new TableView<>();
        TableColumn<FieldTypeRow, String> typeCol = new TableColumn<>(
            I18n.get("col.type")
        );
        typeCol.setCellValueFactory(cd ->
            new ReadOnlyStringWrapper(cd.getValue().fieldType())
        );
        typeCol.setPrefWidth(250);
        TableColumn<FieldTypeRow, String> freqCol = new TableColumn<>(
            I18n.get("col.frequency")
        );
        freqCol.setCellValueFactory(cd ->
            new ReadOnlyStringWrapper(String.valueOf(cd.getValue().frequency()))
        );
        freqCol.setPrefWidth(100);
        freqCol.getProperties().put("filterMode", FILTER_MODE_TEXT);
        table.getColumns().addAll(typeCol, freqCol);
        counts
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(e ->
                table.getItems().add(new FieldTypeRow(e.getKey(), e.getValue()))
            );
        table
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (n != null) populateFieldTypeSummaryEditor(n);
            });
        VBox wrapper = wrapTableWithFilters(table, this::updateCountLabel, searchField.textProperty());
        tableContainer.getChildren().setAll(wrapper);
        updateCountLabel(table.getItems().size(), table.getItems().size());
    }

    private void populateFieldTypeSummaryEditor(FieldTypeRow row) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.type"), row.fieldType());
        values.put(I18n.get("col.frequency"), String.valueOf(row.frequency()));
        populateSummaryEditor(I18n.get("nav.fieldTypes"), "", values);
        Button accessBtn = new Button(I18n.get("btn.accessObjects"));
        accessBtn.getStyleClass().addAll("example-add-button", "no-dirty");
        accessBtn.setMaxWidth(Double.MAX_VALUE);
        accessBtn.setOnAction(e -> showObjectsWithFieldType(row.fieldType()));
        editorContainer.getChildren().add(accessBtn);
    }

    /** Shared helper: show a simple value + frequency table for category views. */
    private record CategoryRow(String value, long frequency) {}

    private void showCategoryTable(
        String title,
        String colLabel,
        Map<String, Long> counts
    ) {
        showCategoryTable(title, colLabel, counts, null);
    }

    private void showCategoryTable(
        String title,
        String colLabel,
        Map<String, Long> counts,
        String categoryKind
    ) {
        TableView<CategoryRow> table = new TableView<>();
        TableColumn<CategoryRow, String> valCol = new TableColumn<>(colLabel);
        valCol.setCellValueFactory(cd ->
            new ReadOnlyStringWrapper(cd.getValue().value())
        );
        valCol.setPrefWidth(250);
        TableColumn<CategoryRow, String> freqCol = new TableColumn<>(
            I18n.get("col.frequency")
        );
        freqCol.setCellValueFactory(cd ->
            new ReadOnlyStringWrapper(String.valueOf(cd.getValue().frequency()))
        );
        freqCol.setPrefWidth(100);
        freqCol.getProperties().put("filterMode", FILTER_MODE_TEXT);
        table.getColumns().addAll(valCol, freqCol);
        counts
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(e ->
                table.getItems().add(new CategoryRow(e.getKey(), e.getValue()))
            );
        table
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (n != null) populateCategorySummaryEditor(
                    title,
                    n,
                    categoryKind
                );
            });
        VBox wrapper = wrapTableWithFilters(table, this::updateCountLabel, searchField.textProperty());
        tableContainer.getChildren().setAll(wrapper);
        updateCountLabel(table.getItems().size(), table.getItems().size());
    }

    /* ════════════════════ COLUMN FILTERS (5.8) ════════════════════ */

    /**
     * Wraps a TableView in a VBox with a row of TextFields below the headers.
     * Each TextField filters its column; only rows matching ALL column filters are shown.
     * If searchTextProperty is non-null, the global search bar also filters rows (any visible column).
     */
    /** The search-field listener of the table view currently shown, if any. */
    private javafx.beans.value.ChangeListener<String> activeSearchListener;

    private void detachSearchListener() {
        if (activeSearchListener != null && searchField != null) {
            searchField.textProperty().removeListener(activeSearchListener);
        }
        activeSearchListener = null;
    }

    @SuppressWarnings("unchecked")
    private <T> VBox wrapTableWithFilters(
        TableView<T> table,
        java.util.function.BiConsumer<Integer, Integer> onCountChanged,
        javafx.beans.property.StringProperty searchTextProperty
    ) {
        ObservableList<T> sourceItems = FXCollections.observableArrayList(
            table.getItems()
        );
        FilteredList<T> filtered = new FilteredList<>(sourceItems, t -> true);
        javafx.collections.transformation.SortedList<T> sorted =
            new javafx.collections.transformation.SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        List<TableColumn<T, ?>> leaves = collectLeafColumns(table);
        List<javafx.scene.Node> filterInputs = new ArrayList<>();
        List<Boolean> textFilterColumns = new ArrayList<>();
        String clearOption = I18n.get("filter.clear");
        AtomicBoolean internalUpdate = new AtomicBoolean(false);

        GridPane filterRow = new GridPane();
        filterRow.getStyleClass().add("no-dirty");
        filterRow.setHgap(0);
        filterRow.setPadding(new Insets(4, 0, 4, 0));
        filterRow.setStyle("-fx-background-color: #eef2f3;");
        filterRow.setMinWidth(0);

        java.util.function.Supplier<String> searchTextSupplier = () ->
            searchTextProperty != null
                ? Optional.ofNullable(searchTextProperty.get())
                      .orElse("")
                      .trim()
                      .toLowerCase(Locale.ROOT)
                : "";

        Runnable refreshPredicate = () -> {
            String q = searchTextSupplier.get();
            filtered.setPredicate(
                row ->
                    rowMatchesAllFilters(
                        row,
                        leaves,
                        filterInputs,
                        textFilterColumns,
                        clearOption,
                        -1
                    ) && rowMatchesSearch(row, leaves, q)
            );
            if (onCountChanged != null) javafx.application.Platform.runLater(
                () -> onCountChanged.accept(filtered.size(), sourceItems.size())
            );
        };

        Runnable refreshFacetChoices = () -> {
            if (internalUpdate.get()) return;
            internalUpdate.set(true);
            try {
                String q = searchTextSupplier.get();
                for (int i = 0; i < leaves.size(); i++) {
                    if (textFilterColumns.get(i)) continue;
                    ComboBox<String> combo =
                        (ComboBox<String>) filterInputs.get(i);
                    String currentValue = combo.getValue();
                    final int colIndex = i;

                    List<String> values = sourceItems
                        .stream()
                        .filter(
                            row ->
                                rowMatchesAllFilters(
                                    row,
                                    leaves,
                                    filterInputs,
                                    textFilterColumns,
                                    clearOption,
                                    colIndex
                                ) && rowMatchesSearch(row, leaves, q)
                        )
                        .map(row -> cellText(row, leaves.get(colIndex)))
                        .filter(s -> s != null && !s.isBlank())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();

                    ObservableList<String> items =
                        FXCollections.observableArrayList();
                    items.add(clearOption);
                    items.addAll(values);
                    combo.setItems(items);

                    if (
                        currentValue != null && items.contains(currentValue)
                    ) combo.setValue(currentValue);
                    else combo.setValue(clearOption);
                }
            } finally {
                internalUpdate.set(false);
            }
        };

        if (searchTextProperty != null) {
            // Only the table shown listens to the search field.
            detachSearchListener();
            activeSearchListener = (obs, o, n) -> {
                refreshPredicate.run();
                refreshFacetChoices.run();
            };
            searchTextProperty.addListener(activeSearchListener);
        }

        Button clearBtn = new Button(I18n.get("filter.resetAll"));
        clearBtn.setOnAction(e -> {
            if (
                searchTextProperty != null &&
                !searchTextProperty.get().isBlank()
            ) {
                searchTextProperty.set("");
            }
            internalUpdate.set(true);
            try {
                for (int i = 0; i < filterInputs.size(); i++) {
                    if (textFilterColumns.get(i)) (
                        (TextField) filterInputs.get(i)
                    ).clear();
                    else ((ComboBox<String>) filterInputs.get(i)).setValue(
                        clearOption
                    );
                }
            } finally {
                internalUpdate.set(false);
            }
            refreshPredicate.run();
            refreshFacetChoices.run();
            table.getSelectionModel().clearSelection();
        });

        HBox header = new HBox();
        header.getStyleClass().add("no-dirty");
        header.setPadding(new Insets(0, 6, 4, 6));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, clearBtn);

        for (int i = 0; i < leaves.size(); i++) {
            TableColumn<T, ?> column = leaves.get(i);
            column.setMinWidth(85);
            boolean forceText = FILTER_MODE_TEXT.equals(
                column.getProperties().get("filterMode")
            );
            final int colIdx = i;
            long distinct = sourceItems
                .stream()
                .map(row -> cellText(row, leaves.get(colIdx)))
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .count();
            boolean hasRepeatedValues =
                distinct < sourceItems.size() && distinct > 0;
            boolean textFilter = forceText || !hasRepeatedValues;
            textFilterColumns.add(textFilter);

            ColumnConstraints cc = new ColumnConstraints();
            cc.prefWidthProperty().bind(column.widthProperty());
            filterRow.getColumnConstraints().add(cc);

            if (textFilter) {
                TextField tf = new TextField();
                tf.setPromptText(I18n.get("filter.prompt"));
                tf.setMaxWidth(Double.MAX_VALUE);
                tf.setMinWidth(0);
                tf.setMinHeight(26);
                tf.setPrefHeight(26);
                tf.setStyle("-fx-font-size: 11px; -fx-padding: 4 6 4 6;");
                filterInputs.add(tf);
                GridPane.setHgrow(tf, Priority.ALWAYS);
                filterRow.add(tf, i, 0);
                tf.textProperty().addListener((obs, o, n) -> {
                    if (internalUpdate.get()) return;
                    refreshPredicate.run();
                    refreshFacetChoices.run();
                });
                continue;
            }

            ComboBox<String> cb = new ComboBox<>();
            cb.getStyleClass().add("filter-combo");
            cb.setPromptText(I18n.get("filter.prompt"));
            cb.setEditable(false);
            cb.setMaxWidth(Double.MAX_VALUE);
            cb.setMinWidth(0);
            cb.setMinHeight(26);
            cb.setPrefHeight(26);
            cb.setStyle("-fx-font-size: 11px;");
            cb.setCellFactory(list ->
                new ListCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setDisable(false);
                            setStyle("");
                            return;
                        }
                        setText(item);
                        boolean isClearItem = clearOption.equals(item);
                        boolean activeFilter = isActiveFilter(
                            cb.getValue(),
                            clearOption
                        );
                        boolean disableClear = isClearItem && !activeFilter;
                        setDisable(disableClear);
                        setStyle(disableClear ? "-fx-opacity: 0.45;" : "");
                    }
                }
            );
            cb.setButtonCell(
                new ListCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item);
                    }
                }
            );
            filterInputs.add(cb);
            GridPane.setHgrow(cb, Priority.ALWAYS);
            filterRow.add(cb, i, 0);

            cb.valueProperty().addListener((obs, o, n) -> {
                if (clearOption.equals(n) && !isActiveFilter(o, clearOption)) {
                    cb.setValue(o);
                    return;
                }
                if (internalUpdate.get()) return;
                refreshPredicate.run();
                refreshFacetChoices.run();
            });
        }

        internalUpdate.set(true);
        try {
            for (int i = 0; i < filterInputs.size(); i++) {
                if (textFilterColumns.get(i)) (
                    (TextField) filterInputs.get(i)
                ).clear();
                else ((ComboBox<String>) filterInputs.get(i)).setValue(
                    clearOption
                );
            }
        } finally {
            internalUpdate.set(false);
        }
        refreshPredicate.run();
        refreshFacetChoices.run();

        // Aligner la largeur des filtres sur la zone des colonnes (prend en compte la scrollbar verticale)
        filterRow.maxWidthProperty().bind(
            Bindings.createDoubleBinding(
                () ->
                    leaves
                        .stream()
                        .mapToDouble(c -> c.getWidth())
                        .sum(),
                leaves
                    .stream()
                    .<javafx.beans.Observable>map(TableColumn::widthProperty)
                    .toArray(javafx.beans.Observable[]::new)
            )
        );

        VBox wrapper = new VBox(header, filterRow, table);
        wrapper.setMinWidth(0);
        filterRow.setMinWidth(0);
        table.setMinWidth(0);
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrapper;
    }

    private static <T> boolean rowMatchesSearch(
        T row,
        List<TableColumn<T, ?>> leaves,
        String searchText
    ) {
        if (searchText == null || searchText.isEmpty()) return true;
        StringBuilder sb = new StringBuilder();
        for (TableColumn<T, ?> col : leaves) {
            String ct = cellText(row, col);
            if (ct != null) sb.append(" ").append(ct);
        }
        return sb.toString().toLowerCase(Locale.ROOT).contains(searchText);
    }

    private static <T> boolean rowMatchesAllFilters(
        T row,
        List<TableColumn<T, ?>> leaves,
        List<javafx.scene.Node> filterInputs,
        List<Boolean> textFilterColumns,
        String clearOption,
        int ignoredColumn
    ) {
        for (int i = 0; i < filterInputs.size(); i++) {
            if (i == ignoredColumn) continue;
            String cellValue = cellText(row, leaves.get(i));
            if (textFilterColumns.get(i)) {
                String query = ((TextField) filterInputs.get(i)).getText();
                if (query == null || query.isBlank()) continue;
                if (
                    !cellValue
                        .toLowerCase(Locale.ROOT)
                        .contains(query.trim().toLowerCase(Locale.ROOT))
                ) return false;
                continue;
            }
            String selected = (
                (ComboBox<String>) filterInputs.get(i)
            ).getValue();
            if (
                selected == null ||
                selected.isBlank() ||
                clearOption.equals(selected)
            ) continue;
            if (!selected.equals(cellValue)) return false;
        }
        return true;
    }

    private static boolean isActiveFilter(String selected, String clearOption) {
        return (
            selected != null &&
            !selected.isBlank() &&
            !clearOption.equals(selected)
        );
    }

    private static <T> String cellText(T row, TableColumn<T, ?> col) {
        Object cellVal =
            col.getCellObservableValue(row) != null
                ? col.getCellObservableValue(row).getValue()
                : null;
        return cellVal != null ? cellVal.toString() : "";
    }

    /** Collect leaf (non-grouped) columns in display order. */
    private static <T> List<TableColumn<T, ?>> collectLeafColumns(
        TableView<T> table
    ) {
        List<TableColumn<T, ?>> leaves = new ArrayList<>();
        for (TableColumn<T, ?> c : table.getColumns()) collectLeaves(c, leaves);
        return leaves;
    }

    private static <T> void collectLeaves(
        TableColumn<T, ?> col,
        List<TableColumn<T, ?>> leaves
    ) {
        if (col.getColumns().isEmpty()) {
            leaves.add(col);
        } else {
            for (TableColumn<T, ?> child : col.getColumns())
                collectLeaves(child, leaves);
        }
    }

    /* ════════════════════ HEADER CONFIGURATION VIEWS ════════════════════ */

    private void showHeaderRangeView(String rangeId) {
        if (currentDictionary == null) {
            tableContainer
                .getChildren()
                .setAll(new Label(I18n.get("cfg.noHeader")));
            return;
        }
        LiftHeader header = currentDictionary.getHeader();

        FeatureSet range = header.hasFeatureSet(rangeId)
            ? header.getFeatureSet(rangeId)
            : currentDictionary
                  .getComponentBuilder()
                  .range(rangeId)
                  .build();

        List<String> metaLangs = new ArrayList<>(currentDictionary.getMetaLanguageManager().getLanguages());

        // ── Top: Range properties (description, label, abbrev) ─────────────
        TitledPane rangePropsPane = new TitledPane(
            I18n.get("cfg.rangeProperties"),
            buildRangePropertiesEditor(range)
        );
        rangePropsPane.setExpanded(true);
        rangePropsPane.setAnimated(false);

        // ── Middle: editable tree of range-elements ─────────────────────────
        TreeView<Feature> tree = buildRangeElementTree(range);
        tree.setShowRoot(false);
        tree.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (
                    n != null && n.getValue() != null
                ) populateRangeElementEditor(range, n.getValue());
            });

        // Add controls
        TextField newIdField = new TextField();
        newIdField.setPromptText(I18n.get("cfg.newElementLabel"));

        ComboBox<String> parentCombo = new ComboBox<>();
        parentCombo.setPromptText(I18n.get("cfg.parentElement"));
        parentCombo.getItems().add("");
        range
            .getFeatures()
            .keySet()
            .stream()
            .forEach(parentCombo.getItems()::add);

        Button addBtn = new Button(I18n.get("cfg.addElement"));
        addBtn.setOnAction(e -> {
            String newId = newIdField.getText().trim();
            if (!newId.isEmpty() && !range.hasFeature(newId)) {
                Feature newElem = range.addFeature(
                    newId
                );
                String parentSel = parentCombo.getValue();
                if (
                    parentSel != null && !parentSel.isBlank()
                ) {
                    // There is a hierarchy between LiftHeaderRangeElement
                    Feature parentRangeElement = range.getFeature(parentSel);
                    newElem.setSuperordinateFeature(parentRangeElement);
                }
                if (!metaLangs.isEmpty()) newElem
                    .getDescription()
                    .add(new Form(metaLangs.get(0), I18n.get("cfg.autoAdded")));
                newIdField.clear();
                showHeaderRangeView(rangeId);
            }
        });

        Button deleteBtn = new Button(I18n.get(Keys.BTN_DELETE));
        deleteBtn.setOnAction(e -> {
            TreeItem<Feature> selItem = tree
                .getSelectionModel()
                .getSelectedItem();
            if (selItem == null) return;
            if (selItem == null || selItem.getValue() == null) return;
            Feature sel = selItem.getValue();
            long usage = countFeatureUsage(sel);
            if (usage > 0) showError(
                I18n.get("btn.delete"),
                I18n.get("cfg.deleteNotAllowed", usage)
            );
            else {
                range.removeFeature(sel.getId());
                showHeaderRangeView(rangeId);
            }
        });

        Button renameBtn = new Button(I18n.get("cfg.rename"));
        renameBtn.setOnAction(e -> {
            TreeItem<Feature> selItem = tree
                .getSelectionModel()
                .getSelectedItem();
            if (selItem == null || selItem.getValue() == null) return;
            TextInputDialog dlg = new TextInputDialog(
                selItem.getValue().getId()
            );
            dlg.setTitle(I18n.get("cfg.rename"));
            dlg.setHeaderText(
                I18n.get("cfg.renamePrompt", selItem.getValue().getId())
            );
            dlg.showAndWait().ifPresent(newName -> {
                if (!newName.isBlank()) {
                    if (range.hasFeature(newName.trim())) {
                        showError(I18n.get("cfg.rename"), I18n.get("cfg.idExists", newName.trim()));
                        return;
                    }
                    renameFeature(range, selItem.getValue().getId(), newName.trim());
                    showHeaderRangeView(rangeId);
                }
            });
        });

        Label countLbl = new Label(
            range.getFeatures().size() + " " + I18n.get("cfg.elements")
        );
        countLbl.setStyle("-fx-text-fill: #66767a; -fx-font-size: 12px;");

        HBox addRow = new HBox(8, newIdField, parentCombo, addBtn);
        HBox.setHgrow(newIdField, Priority.ALWAYS);
        HBox actionRow = new HBox(
            8,
            deleteBtn,
            renameBtn,
            new HBox(),
            countLbl
        );
        HBox.setHgrow(actionRow.getChildren().get(2), Priority.ALWAYS);

        Label elemTitle = new Label(I18n.get("cfg.rangeElements2"));
        elemTitle.setStyle(
            "-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#4c6f76;"
        );

        VBox.setVgrow(tree, Priority.ALWAYS);
        VBox centerBox = new VBox(
            8,
            rangePropsPane,
            elemTitle,
            tree,
            addRow,
            actionRow
        );
        centerBox.setPadding(new Insets(8));
        VBox.setVgrow(centerBox.getChildren().get(2), Priority.ALWAYS);
        tableContainer.getChildren().setAll(centerBox);
        updateCountLabel(
            range.getFeatures().size(),
            range.getFeatures().size()
        );
    }

    /** Build a small GridPane of MultiTextEditors for description / label / abbrev of a range. */
    private VBox buildRangePropertiesEditor(FeatureSet range) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(6));
        addMultiTextRow(
            box,
            I18n.get("cfg.description"),
            range.getDescription());
        addMultiTextRow(
            box,
            I18n.get("cfg.label"),
            range.getLabel());
        addMultiTextRow(
            box,
            I18n.get("cfg.abbrev"),
            range.getAbbrev());
        return box;
    }

    private void addMultiTextRow(VBox box, String lbl, MultiText mt) {
        Label l = new Label(lbl);
        l.setStyle("-fx-font-weight:bold; -fx-font-size:12px;");
        MultiTextEditor ed = new MultiTextEditor(currentDictionary);
        ed.setMultiText(mt);
        box.getChildren().addAll(l, ed);
    }

    /** Show description editor for the LiftHeader itself. */
    private void showHeaderDescView() {
        if (currentDictionary == null) {
            tableContainer
                .getChildren()
                .setAll(new Label(I18n.get("cfg.noHeader")));
            return;
        }
        LiftHeader header = currentDictionary
            .getHeader();
        if (header == null) {
            tableContainer
                .getChildren()
                .setAll(new Label(I18n.get("cfg.noHeader")));
            return;
        }
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        Label title = new Label(I18n.get("nav.cfgDesc"));
        title.setStyle(
            "-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#4c6f76;"
        );
        box.getChildren().add(title);

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(12);
        infoGrid.setVgap(6);
        int row = 0;
        LiftVersion version = currentDictionary.getLiftVersion();
        if (version != null) {
            Label vLabel = new Label(I18n.get("cfg.liftVersion"));
            vLabel.setStyle("-fx-font-weight:bold;");
            TextField vField = new TextField(version.toString());
            vField.setEditable(false);
            vField.setPrefWidth(200);
            vField.setStyle("-fx-background-color: #eee;");
            infoGrid.add(vLabel, 0, row);
            infoGrid.add(vField, 1, row);
            row++;
        }
        String producer = currentDictionary.getLiftProducer();
        if (producer != null && !producer.isBlank()) {
            Label pLabel = new Label(I18n.get("cfg.producer"));
            pLabel.setStyle("-fx-font-weight:bold;");
            TextField pField = new TextField(producer);
            pField.setEditable(false);
            pField.setPrefWidth(200);
            pField.setStyle("-fx-background-color: #eee;");
            infoGrid.add(pLabel, 0, row);
            infoGrid.add(pField, 1, row);
        }
        box.getChildren().add(infoGrid);

        addMultiTextRow(
            box,
            I18n.get("cfg.description"),
            header.getDescription());
        tableContainer.getChildren().setAll(box);
        editorContainer.getChildren().clear();
        editEntryTitle.setText(I18n.get("nav.cfgDesc"));
        editEntryCode.setText("");
    }

    /** Build a TreeView of LiftHeaderRangeElements respecting the @parent hierarchy. */
    private TreeView<Feature> buildRangeElementTree(
        FeatureSet range
    ) {
        TreeItem<Feature> root = new TreeItem<>(null);
        root.setExpanded(true);
        Map<String, TreeItem<Feature>> itemMap =
            new java.util.LinkedHashMap<>();

        // First pass: create all items
        for (Feature re : range.getFeatures().values()) {
            TreeItem<Feature> item = new TreeItem<>(re);
            item.setExpanded(true);
            itemMap.put(re.getId(), item);
        }
        // Second pass: wire parent-child relationships
        for (Feature re : range.getFeatures().values()) {
            TreeItem<Feature> item = itemMap.get(re.getId());
            Feature pid = re.getSuperOrdinateFeature().orElse(null);
            if (pid != null && itemMap.containsKey(pid.getId())) {
                itemMap.get(pid.getId()).getChildren().add(item);
            } else {
                root.getChildren().add(item);
            }
        }

        TreeView<Feature> tree = new TreeView<>(root);
        tree.setCellFactory(tv ->
            new TreeCell<>() {
                @Override
                protected void updateItem(
                    Feature item,
                    boolean empty
                ) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        String abbrev = item
                            .getAbbrev()
                            .getForms()
                            .stream()
                            .findFirst()
                            .map(Form::toPlainText)
                            .orElse("");
                        String label = item
                            .getLabel()
                            .getForms()
                            .stream()
                            .findFirst()
                            .map(Form::toPlainText)
                            .orElse("");
                        String display = item.getId();
                        if (!abbrev.isBlank()) display += "  [" + abbrev + "]";
                        if (!label.isBlank()) display += "  – " + label;
                        setText(display);
                    }
                }
            }
        );
        return tree;
    }

    private void showHeaderFieldDefsView() {
        if (currentDictionary == null) {
            tableContainer
                .getChildren()
                .setAll(new Label(I18n.get("cfg.noHeader")));
            return;
        }
        LiftHeader header = currentDictionary
            .getHeader();

        TableView<LiftFieldAndTraitDefinition> table = new TableView<>();
        table
            .getColumns()
            .addAll(
                col(
                    I18n.get("cfg.fieldDefName"),
                    LiftFieldAndTraitDefinition::getName
                ),
                col(I18n.get("cfg.kind"), fd -> fieldDefKindLabel(fd)),
                col(I18n.get("cfg.fieldDefType"), fd ->
                    fd.getTypeStr().orElse("")
                ),
                col(I18n.get("cfg.targets"), fd -> fd.getTargetAsString()),
                col(I18n.get("cfg.description"), fd ->
                    fd
                        .getDescription()
                        .getForms()
                        .stream()
                        .findFirst()
                        .map(Form::toPlainText)
                        .orElse("")
                ),
                col(I18n.get("cfg.usageCount"), fd ->
                    String.valueOf(countFieldOrTraitUsage(fd))
                )
            );
        table.getItems().addAll(header.getFieldsAndTraitsDefinitions());

        table
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (n != null) populateFieldDefEditor(n);
            });

        Button addBtn = new Button(I18n.get("cfg.newFieldOrTrait"));
        addBtn.setOnAction(e -> showNewFieldDefDialog(table));

        Button deleteBtn = new Button(I18n.get(Keys.BTN_DELETE));
        deleteBtn.setOnAction(e -> {
            LiftFieldAndTraitDefinition sel = table
                .getSelectionModel()
                .getSelectedItem();
            if (sel == null) return;
            long usage = countFieldOrTraitUsage(sel);
            if (usage > 0) {
                showError(
                    I18n.get("btn.delete"),
                    I18n.get("cfg.deleteNotAllowed", usage)
                );
            } else {
                header.getFieldsAndTraitsDefinitions().remove(sel);
                table.getItems().remove(sel);
            }
        });

        HBox controls = new HBox(8, addBtn, deleteBtn);
        controls.setPadding(new Insets(6, 0, 0, 0));

        VBox wrapper = new VBox(6, wrapTableWithFilters(table, this::updateCountLabel, searchField.textProperty()), controls);
        VBox.setVgrow(wrapper.getChildren().get(0), Priority.ALWAYS);
        tableContainer.getChildren().setAll(wrapper);
        updateCountLabel(table.getItems().size(), table.getItems().size());
    }

    private void showNewFieldDefDialog(
        TableView<LiftFieldAndTraitDefinition> table
    ) {
        LiftHeader header = currentDictionary.getHeader();
        Dialog<LiftFieldAndTraitDefinition> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("cfg.newFieldOrTrait"));
        dlg.setHeaderText(I18n.get("cfg.chooseKind"));
        dlg.getDialogPane()
            .getButtonTypes()
            .addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefWidth(450);

        TextField nameField = new TextField();
        nameField.setPromptText(I18n.get("cfg.fieldDefName"));

        ToggleGroup kindGroup = new ToggleGroup();
        RadioButton rbField = new RadioButton(I18n.get("cfg.kindField"));
        rbField.setToggleGroup(kindGroup);
        rbField.setSelected(true);
        RadioButton rbTrait = new RadioButton(I18n.get("cfg.kindTrait"));
        rbTrait.setToggleGroup(kindGroup);

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.setEditable(true);
        typeCombo.setPromptText(I18n.get("cfg.fieldDefType"));
        Runnable updateTypes = () -> {
            typeCombo.getItems().clear();
            if (rbField.isSelected()) {
                typeCombo.getItems().addAll("multistring", "multitext");
                typeCombo.setValue("multitext");
            } else {
                typeCombo
                    .getItems()
                    .addAll(
                        "datetime",
                        "integer",
                        "option",
                        "option-collection",
                        "option-sequence"
                    );
                typeCombo.setValue("option");
            }
        };
        updateTypes.run();
        rbField.setOnAction(e -> updateTypes.run());
        rbTrait.setOnAction(e -> updateTypes.run());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.add(new Label(I18n.get("cfg.fieldDefName")), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label(I18n.get("cfg.kind")), 0, 1);
        grid.add(new HBox(12, rbField, rbTrait), 1, 1);
        grid.add(new Label(I18n.get("cfg.fieldDefType")), 0, 2);
        grid.add(typeCombo, 1, 2);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);
        dlg.getDialogPane().setContent(grid);

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            String name = nameField.getText().trim();
            if (
                name.isEmpty() ||
                header
                    .getFieldsAndTraitsDefinitions()
                    .stream()
                    .anyMatch(fd -> fd.getName().equals(name))
            ) return null;
            LiftFieldAndTraitDefinition fd = rbTrait.isSelected()
                ? header.createTraitDefinition(name)
                : header.createFieldDefinition(name);
            String typeVal = typeCombo.getValue();
            if (typeVal != null && !typeVal.isBlank()) fd.setDataModel(
                Optional.of(typeVal)
            );
            List<String> metaLangs = new ArrayList<>(currentDictionary.getMetaLanguageManager().getLanguages());
            if (!metaLangs.isEmpty()) fd.getDescription().add(
                new Form(metaLangs.get(0), I18n.get("cfg.autoAdded"))
            );
            return fd;
        });

        dlg.showAndWait().ifPresent(fd -> table.getItems().add(fd));
    }

    private void populateRangeElementEditor(
        FeatureSet range,
        Feature elem
    ) {
        // No id/guid shown to the user — only human-readable MultiText fields
        String firstLabel = elem
            .getLabel()
            .getForms()
            .stream()
            .findFirst()
            .map(Form::toPlainText)
            .orElse("");
        String firstAbbrev = elem
            .getAbbrev()
            .getForms()
            .stream()
            .findFirst()
            .map(Form::toPlainText)
            .orElse("");
        editEntryTitle.setText(
            firstLabel.isBlank()
                ? firstAbbrev.isBlank()
                    ? I18n.get("cfg.rangeElement")
                    : firstAbbrev
                : firstLabel
        );
        editEntryCode.setText(range.getId());
        editorContainer.getChildren().clear();

        addSection(
            editorContainer,
            I18n.get("cfg.label"),
            () -> {
                MultiTextEditor m = new MultiTextEditor(currentDictionary);
                m.setMultiText(elem.getLabel());
                return m;
            },
            true
        );
        addSection(
            editorContainer,
            I18n.get("cfg.abbrev"),
            () -> {
                MultiTextEditor m = new MultiTextEditor(currentDictionary);
                m.setMultiText(elem.getAbbrev());
                return m;
            },
            true
        );
        addSection(
            editorContainer,
            I18n.get("cfg.description"),
            () -> {
                MultiTextEditor m = new MultiTextEditor(currentDictionary);
                m.setMultiText(elem.getDescription());
                return m;
            },
            false
        );
    }

    private void populateFieldDefEditor(LiftFieldAndTraitDefinition fd) {
        editEntryTitle.setText(fd.getName());
        String kindLabel = fieldDefKindLabel(fd);
        editEntryCode.setText(kindLabel + " – " + fd.getTypeStr().orElse(""));
        editorContainer.getChildren().clear();

        addSection(
            editorContainer,
            I18n.get("editor.identity"),
            () -> {
                GridPane g = new GridPane();
                g.setHgap(8);
                g.setVgap(6);
                addReadOnlyRow(
                    g,
                    0,
                    I18n.get("cfg.fieldDefName"),
                    fd.getName()
                );
                addReadOnlyRow(g, 1, I18n.get("cfg.kind"), kindLabel);

                g.add(new Label(I18n.get("cfg.fieldDefType")), 0, 2);
                ComboBox<String> typeCombo = new ComboBox<>();
                typeCombo.setEditable(true);
                typeCombo
                    .getItems()
                    .addAll(
                        "multistring",
                        "multitext",
                        "datetime",
                        "integer",
                        "option",
                        "option-collection",
                        "option-sequence"
                    );
                typeCombo.setValue(fd.getTypeStr().orElse(""));
                typeCombo.valueProperty().addListener((obs, o, n) -> {
                    try {
                        fd.setDataModel(
                            n == null || n.isBlank()
                                ? Optional.empty()
                                : Optional.of(n.trim())
                        );
                        markDirty();
                    } catch (IllegalArgumentException ex) {
                        showError(I18n.get("cfg.fieldDefType"), I18n.get("error.invalidValue", n));
                    }
                });
                GridPane.setHgrow(typeCombo, Priority.ALWAYS);
                g.add(typeCombo, 1, 2);

                g.add(new Label(I18n.get("cfg.targets")), 0, 3);
                TextField classTf = new TextField(fd.getTargetAsString());
                classTf.setPromptText("entry sense variant ...");
                // Applied when the field is validated (Enter or focus lost), and only if every
                // target is a known LIFT element name.
                Runnable applyTargets = () -> {
                    String n = classTf.getText().trim();
                    if (n.isEmpty() || n.equals(fd.getTargetAsString())) {
                        classTf.setStyle("");
                        return;
                    }
                    try {
                        LiftFieldAndTraitDefinitionTarget.parseTargetString(n);
                        fd.setTargets(n);
                        classTf.setStyle("");
                        markDirty();
                    } catch (RuntimeException ex) {
                        classTf.setStyle("-fx-border-color: #c0392b; -fx-border-width: 2;");
                    }
                };
                classTf.setOnAction(e -> applyTargets.run());
                classTf.focusedProperty().addListener((obs, was, is) -> {
                    if (!is) applyTargets.run();
                });
                GridPane.setHgrow(classTf, Priority.ALWAYS);
                g.add(classTf, 1, 3);

                g.add(new Label(I18n.get("cfg.optionRange")), 0, 4);
                ComboBox<FeatureSet> orCb = new ComboBox<>();
                orCb.setItems(currentDictionary.getHeader().getFeatureSets());
                orCb.setConverter(new javafx.util.StringConverter<>() {
                    @Override
                    public String toString(FeatureSet f) {
                        return f == null ? "" : f.getId();
                    }

                    @Override
                    public FeatureSet fromString(String str) {
                        return null;
                    }
                });
                orCb.setCellFactory(lv -> new ListCell<>() {
                    @Override
                    protected void updateItem(FeatureSet item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item.getId());
                    }
                });
                orCb.setValue(fd.getResolvedFeatureSet().orElse(null));
                orCb.valueProperty().addListener((obs, o, n) -> {
                    fd.setResolvedRange(Optional.ofNullable(n));
                    markDirty();
                });

                GridPane.setHgrow(orCb, Priority.ALWAYS);
                g.add(orCb, 1, 4);
                return g;
            },
            true
        );

        addSection(
            editorContainer,
            I18n.get("cfg.label"),
            () -> {
                MultiTextEditor m = new MultiTextEditor(currentDictionary);
                m.setMultiText(fd.getLabel());
                return m;
            },
            true
        );
        addSection(
            editorContainer,
            I18n.get("cfg.description"),
            () -> {
                MultiTextEditor m = new MultiTextEditor(currentDictionary);
                m.setMultiText(fd.getDescription());
                return m;
            },
            true
        );
    }

    private long countFieldOrTraitUsage(LiftFieldAndTraitDefinition fd) {
        if (currentDictionary == null) return 0;
        var comps = currentDictionary.getLiftDictionaryRegistry();
        long fieldCount = comps
            .getFields()
            .stream()
            .filter(f -> fd.getName().equals(f.getSpecification().getName()))
            .count();
        long traitCount = comps
            .getTraits()
            .stream()
            .filter(t -> fd.getName().equals(t.getSpecification().getName()))
            .count();
        return fieldCount + traitCount;
    }

    /* ─── Header usage counting & renaming ─── */

    /**
     * Number of components that use a feature of the header: as their type (notes,
     * translations, annotations, relations, etymologies, reversals, variants), as their
     * grammatical info, or in the value of a trait whose definition points at the feature's set.
     */
    private long countFeatureUsage(Feature f) {
        if (currentDictionary == null || f == null) return 0;
        var reg = currentDictionary.getLiftDictionaryRegistry();
        long n = 0;
        n += reg.getNotes().stream().filter(x -> x.getType() == f).count();
        n += reg.getTranslations().stream().filter(x -> x.getType() == f).count();
        n += reg.getAnnotations().stream().filter(x -> x.getType() == f).count();
        n += reg.getRelations().stream().filter(x -> x.getType() == f).count();
        n += reg.getEtymologies().stream().filter(x -> x.getType() == f).count();
        n += reg.getReversals().stream().filter(x -> x.getType() == f).count();
        n += reg.getVariants().stream().filter(x -> x.getType() == f).count();
        n += reg.getGrammaticalInfos().stream().filter(g -> g.getGramInfoValue() == f).count();
        FeatureSet set = f.getParent();
        n += reg.getTraits().stream()
            .filter(t -> traitUsesFeatureSet(t, set) && traitValueTokens(t).contains(f.getId()))
            .count();
        return n;
    }

    /** Whether the values of a trait are ids of the features of {@code set}. */
    private static boolean traitUsesFeatureSet(LiftTrait t, FeatureSet set) {
        if (set == null) return false;
        LiftFieldAndTraitDefinition spec = t.getSpecification();
        return spec.getResolvedFeatureSet()
            .map(r -> r == set)
            .orElse(spec.getName().equals(set.getId()));
    }

    private static List<String> traitValueTokens(LiftTrait t) {
        String v = t.getValue();
        if (v == null || v.isBlank()) return List.of();
        return List.of(v.trim().split("\\s+"));
    }

    /**
     * Renames a feature of the header. Components referring to the feature object follow
     * automatically; trait values, which hold the feature id as text, are updated here.
     */
    private void renameFeature(FeatureSet set, String oldId, String newId) {
        Feature element = set.getFeature(oldId);
        if (element == null) return;
        set.changeFeatureId(element, newId);
        for (LiftTrait t : currentDictionary.getLiftDictionaryRegistry().getTraits()) {
            if (!traitUsesFeatureSet(t, set)) continue;
            List<String> tokens = traitValueTokens(t);
            if (!tokens.contains(oldId)) continue;
            t.setValue(String.join(" ", tokens.stream().map(x -> x.equals(oldId) ? newId : x).toList()));
        }
    }

    /* ════════════════════ AUTO-POPULATE HEADER ════════════════════ */

    private static String fieldDefKindLabel(LiftFieldAndTraitDefinition fd) {
        if (fd == null) return "";
        if (fd.isFieldDefinition()) return I18n.get("cfg.kindField");
        if (fd.isTraitDefinition()) return I18n.get("cfg.kindTrait");
        return I18n.get("cfg.kindUnknown");
    }



    /* ────────────────── UTILITIES ────────────────── */

    @FunctionalInterface
    private interface NodeFactory {
        javafx.scene.Node create();
    }

    private static <T> TableColumn<T, String> col(
        String title,
        java.util.function.Function<T, String> extractor
    ) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd ->
            new ReadOnlyStringWrapper(
                cd.getValue() == null ? "" : extractor.apply(cd.getValue())
            )
        );
        c.setPrefWidth(140);
        return c;
    }

    private static void addSection(
        VBox container,
        String title,
        NodeFactory factory,
        boolean expanded
    ) {
        TitledPane tp = new TitledPane(title, factory.create());
        tp.setExpanded(expanded);
        tp.setAnimated(false);
        container.getChildren().add(tp);
    }

    private static void addReadOnlyRow(
        GridPane grid,
        int row,
        String label,
        String value
    ) {
        grid.add(new Label(label), 0, row);
        TextField tf = new TextField(value);
        styleReadOnlyTextField(tf);
        GridPane.setHgrow(tf, Priority.ALWAYS);
        grid.add(tf, 1, row);
    }

    private static void styleReadOnlyTextField(TextField tf) {
        tf.setEditable(false);
        tf.setFocusTraversable(false);
        tf.getStyleClass().add("read-only-meta-field");
    }

    private void updateCountLabel(int shown, int total) {
        if (tableCountLabel == null) return;
        if (shown == total) {
            tableCountLabel.setText(I18n.get("table.count.total", shown));
            return;
        }
        tableCountLabel.setText(I18n.get("table.count.filtered", shown, total));
    }

    private static String getTraitValue(LiftEntry e, String name) {
        return e == null
            ? ""
            : e
                  .getTraits()
                  .stream()
                  .filter(t -> name.equals(t.getSpecification().getName()))
                  .findFirst()
                  .map(LiftTrait::getValue)
                  .orElse("");
    }

    /** The translation of an example with the given type id, or null. */
    private static MultiText translationOfType(LiftExample ex, String typeId) {
        return ex.getTranslations().entrySet().stream()
            .filter(e -> e.getKey() != null && typeId.equals(e.getKey().getId()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private static String getTraitValueFor(LiftVariant v, String name) {
        return v == null || v.getTraits() == null
            ? ""
            : v
                  .getTraits()
                  .stream()
                  .filter(t -> name.equals(t.getSpecification().getName()))
                  .findFirst()
                  .map(LiftTrait::getValue)
                  .orElse("");
    }

    private MultiText getParentEntryForms(LiftRelation r) {
        if (r == null || r.getParent() == null) return null;
        HasRelations p = r.getParent();
        if (p instanceof LiftEntry e) return e.getForms();
        if (p instanceof LiftSense s) return s.getParentEntry().getForms(); //findParentEntry(s)
        if (p instanceof LiftVariant v) return v.getParent() != null
            ? v.getParent().getForms()
            : null;
        return null;
    }

    private static String buildSearchText(LiftEntry entry) {
        if (entry == null) return "";
        StringBuilder sb = new StringBuilder();
        appendSep(sb, getTraitValue(entry, "code"));
        for (Form f : entry.getForms().getForms())
            appendSep(sb, f.toPlainText());
        for (LiftPronunciation p : entry.getPronunciations())
            for (Form f : p.getPronunciation().getForms())
                appendSep(sb, f.toPlainText());
        for (LiftSense s : entry.getSenses()) {
            for (Form f : s.getDefinition().getForms())
                appendSep(sb, f.toPlainText());
            for (Form f : s.getGlosses().getForms())
                appendSep(sb, f.toPlainText());
        }
        return sb.toString();
    }

    private static String describeParent(Object parent) {
        if (parent == null) return "";
        if (parent instanceof MultiText mt) return describeParent(mt.getParent());
        if (parent instanceof LiftEntry e) return (
            "entry:" + e.getId().orElse("?")
        );
        if (parent instanceof LiftSense s) return (
            "sense:" + s.getId().orElse("?")
        );
        if (parent instanceof GrammaticalInfo) return "gram-info";
        return parent.getClass().getSimpleName();
    }

    private static String describeParentType(Object parent) {
        if (parent == null) return "";
        if (parent instanceof MultiText mt) return describeParentType(mt.getParent());
        if (parent instanceof LiftEntry) return I18n.get("nav.entries");
        if (parent instanceof LiftSense) return I18n.get("nav.senses");
        if (parent instanceof LiftExample) return I18n.get("nav.examples");
        if (parent instanceof LiftNote) return I18n.get("nav.notes");
        if (parent instanceof LiftVariant) return I18n.get("nav.variants");
        if (parent instanceof LiftEtymology) return I18n.get("nav.etymologies");
        if (parent instanceof GrammaticalInfo) return I18n.get("nav.gramInfo");
        return parent.getClass().getSimpleName();
    }

    /* ─── Known dropdown values from header ranges ─── */








    private List<String> getKnownGramInfoValues() {
        if (currentDictionary == null) return List.of();
        return EditorSupport.sortedFeatureIds(currentDictionary.getHeader().getGrammaticalInfoManager());
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void showPreferencesDialog() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("prefs.title"));
        dlg.setHeaderText(I18n.get("prefs.title"));
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefSize(480, 380);
        dlg.getDialogPane()
            .getButtonTypes()
            .addAll(ButtonType.OK, ButtonType.CANCEL);

        // ── Langue ──
        Label langLabel = new Label(I18n.get("prefs.language"));
        ComboBox<String> langCombo = new ComboBox<>(
            FXCollections.observableArrayList("Français", "English")
        );
        langCombo.setValue(
            "fr".equals(I18n.getLocale().getLanguage()) ? "Français" : "English"
        );

        // ── Taille de police ──
        Label fontLabel = new Label(I18n.get("prefs.fontSize"));
        Slider fontSlider = new Slider(
            10,
            20,
            Double.parseDouble(PREFS.get("ui.fontSize", "13"))
        );
        fontSlider.setShowTickLabels(true);
        fontSlider.setShowTickMarks(true);
        fontSlider.setMajorTickUnit(2);
        fontSlider.setBlockIncrement(1);
        fontSlider.setSnapToTicks(true);
        Label fontValueLabel = new Label(
            String.valueOf((int) fontSlider.getValue()) + "px"
        );
        fontSlider
            .valueProperty()
            .addListener((obs, o, n) ->
                fontValueLabel.setText((int) n.doubleValue() + "px")
            );

        // ── Thème ──
        Label themeLabel = new Label(I18n.get("prefs.theme"));
        ToggleGroup themeGroup = new ToggleGroup();
        RadioButton lightBtn = new RadioButton(I18n.get("prefs.themeLight"));
        lightBtn.setToggleGroup(themeGroup);
        RadioButton darkBtn = new RadioButton(I18n.get("prefs.themeDark"));
        darkBtn.setToggleGroup(themeGroup);
        String savedTheme = PREFS.get("ui.theme", "light");
        if ("dark".equals(savedTheme)) darkBtn.setSelected(true);
        else lightBtn.setSelected(true);

        // ── Chemin par défaut ──
        Label pathLabel = new Label(I18n.get("prefs.defaultPath"));
        TextField pathField = new TextField(
            PREFS.get("ui.defaultPath", System.getProperty("user.home"))
        );
        pathField.setPromptText(System.getProperty("user.home"));
        Button browseBtn = new Button(I18n.get("prefs.browse"));
        browseBtn.setOnAction(e -> {
            javafx.stage.DirectoryChooser dc =
                new javafx.stage.DirectoryChooser();
            dc.setTitle(I18n.get("prefs.defaultPath"));
            File dir = dc.showDialog(
                dlg.getDialogPane().getScene().getWindow()
            );
            if (dir != null) pathField.setText(dir.getAbsolutePath());
        });
        HBox pathBox = new HBox(8, pathField, browseBtn);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        // ── Layout ──
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(16));

        grid.add(langLabel, 0, 0);
        grid.add(langCombo, 1, 0);

        grid.add(fontLabel, 0, 1);
        HBox fontBox = new HBox(8, fontSlider, fontValueLabel);
        HBox.setHgrow(fontSlider, Priority.ALWAYS);
        grid.add(fontBox, 1, 1);

        grid.add(themeLabel, 0, 2);
        grid.add(new HBox(12, lightBtn, darkBtn), 1, 2);

        grid.add(pathLabel, 0, 3);
        grid.add(pathBox, 1, 3);

        GridPane.setHgrow(langCombo, Priority.ALWAYS);
        GridPane.setHgrow(fontBox, Priority.ALWAYS);
        GridPane.setHgrow(pathBox, Priority.ALWAYS);

        dlg.getDialogPane().setContent(grid);

        dlg.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                // Sauvegarde taille de police
                int fontSize = (int) fontSlider.getValue();
                PREFS.put("ui.fontSize", String.valueOf(fontSize));
                applyFontSize(fontSize);

                // Sauvegarde thème
                String theme = darkBtn.isSelected() ? "dark" : "light";
                PREFS.put("ui.theme", theme);
                applyTheme(theme);

                // Sauvegarde chemin par défaut
                PREFS.put("ui.defaultPath", pathField.getText().trim());

                // Applique langue si changée
                String sel = langCombo.getValue();
                Locale newLocale = "Français".equals(sel)
                    ? Locale.FRENCH
                    : Locale.ENGLISH;
                if (
                    !newLocale
                        .getLanguage()
                        .equals(I18n.getLocale().getLanguage())
                ) {
                    I18n.setLocale(newLocale);
                    // Hand the open dictionary over to the controller of the reloaded scene.
                    handoff = new Handoff(currentDictionary, currentFile, demo, dirty);
                    Platform.runLater(
                        fr.cnrs.lacito.liftgui.MainApp::reloadScene
                    );
                }
            }
            return null;
        });

        dlg.showAndWait();
    }

    private void applyFontSize(int size) {
        if (menuBar == null || menuBar.getScene() == null) return;
        menuBar
            .getScene()
            .getRoot()
            .setStyle("-fx-font-size: " + size + "px;");
    }

    private void applyTheme(String theme) {
        if (menuBar == null || menuBar.getScene() == null) return;
        javafx.collections.ObservableList<String> sheets = menuBar
            .getScene()
            .getStylesheets();
        sheets.removeIf(s -> s.contains("dark") || s.contains("light"));
        if ("dark".equals(theme)) {
            String darkCss =
                fr.cnrs.lacito.liftgui.MainApp.class.getResource(
                    "/fr/cnrs/lacito/liftgui/ui/dark.css"
                ) != null
                    ? fr.cnrs.lacito.liftgui.MainApp.class
                          .getResource("/fr/cnrs/lacito/liftgui/ui/dark.css")
                          .toExternalForm()
                    : null;
            if (darkCss != null) sheets.add(darkCss);
            else menuBar
                .getScene()
                .getRoot()
                .setStyle(
                    menuBar.getScene().getRoot().getStyle() +
                        "; -fx-base: #2b2b2b; -fx-background: #3c3f41; -fx-control-inner-background: #45494a;"
                );
        }
    }

    /** A row of a header type view: one feature of a feature set. */
    private record FeatureRow(Feature feature, String id, String description, long usage) {}

    /**
     * Edits a header feature set (note types, translation types…) in place: ids can be
     * renamed, descriptions edited, features added, and features that nothing uses deleted.
     * Every change is applied to the header immediately.
     */
    private void showFeatureSetView(String title, FeatureSet set) {
        editorContainer.getChildren().clear();
        editEntryTitle.setText(title);
        editEntryCode.setText("");
        if (set == null) {
            tableContainer.getChildren().setAll(new Label(I18n.get("cfg.noHeader")));
            return;
        }
        String descLang = firstMetaLanguage();

        TableView<FeatureRow> table = new TableView<>();
        table.setEditable(true);
        Runnable reload = () -> {
            table.getItems().setAll(
                EditorSupport.sortedFeatures(set).stream()
                    .map(f -> new FeatureRow(
                        f,
                        f.getId(),
                        f.getDescription().getForm(descLang).map(Form::toPlainText).orElse(""),
                        countFeatureUsage(f)))
                    .toList()
            );
            updateCountLabel(table.getItems().size(), table.getItems().size());
        };

        TableColumn<FeatureRow, String> idCol = new TableColumn<>(I18n.get("col.abbreviation"));
        idCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().id()));
        idCol.setCellFactory(TextFieldTableCell.forTableColumn());
        idCol.setOnEditCommit(e -> {
            String newId = e.getNewValue() == null ? "" : e.getNewValue().trim();
            String oldId = e.getRowValue().id();
            if (!newId.isEmpty() && !newId.equals(oldId)) {
                if (set.hasFeature(newId)) {
                    showError(title, I18n.get("cfg.idExists", newId));
                } else {
                    renameFeature(set, oldId, newId);
                    markDirty();
                }
            }
            reload.run();
        });
        idCol.setPrefWidth(180);

        TableColumn<FeatureRow, String> usageCol = new TableColumn<>(I18n.get("cfg.usageCount"));
        usageCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(String.valueOf(cd.getValue().usage())));
        usageCol.setEditable(false);
        usageCol.setPrefWidth(80);

        TableColumn<FeatureRow, String> descCol = new TableColumn<>(I18n.get("col.description") + " [" + descLang + "]");
        descCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().description()));
        descCol.setCellFactory(TextFieldTableCell.forTableColumn());
        descCol.setOnEditCommit(e -> {
            e.getRowValue().feature().getDescription().formTextProperty(descLang)
                .set(e.getNewValue() == null ? "" : e.getNewValue());
            markDirty();
            reload.run();
        });
        descCol.setPrefWidth(320);

        table.getColumns().addAll(List.of(idCol, usageCol, descCol));
        reload.run();

        TextField addIdField = new TextField();
        addIdField.setPromptText(I18n.get("config.addAbbr"));
        TextField addDescField = new TextField();
        addDescField.setPromptText(I18n.get("config.addDesc"));
        Button addBtn = new Button(I18n.get("btn.add"));
        addBtn.setOnAction(e -> {
            String id = addIdField.getText().trim();
            if (id.isEmpty()) return;
            if (set.hasFeature(id)) {
                showError(title, I18n.get("cfg.idExists", id));
                return;
            }
            Feature f = set.addFeature(id);
            String desc = addDescField.getText().trim();
            if (!desc.isEmpty()) f.getDescription().formTextProperty(descLang).set(desc);
            addIdField.clear();
            addDescField.clear();
            reload.run();
        });

        Button removeBtn = new Button(I18n.get("btn.delete"));
        removeBtn.setOnAction(e -> {
            FeatureRow sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            long usage = countFeatureUsage(sel.feature());
            if (usage > 0) {
                showError(I18n.get("btn.delete"), I18n.get("cfg.deleteNotAllowed", usage));
                return;
            }
            set.removeFeature(sel.id());
            reload.run();
        });

        HBox controls = new HBox(8, addIdField, addDescField, addBtn, removeBtn);
        controls.setPadding(new Insets(6, 0, 0, 0));
        HBox.setHgrow(addIdField, Priority.ALWAYS);
        HBox.setHgrow(addDescField, Priority.ALWAYS);

        Label heading = new Label(title);
        heading.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#4c6f76;");
        VBox box = new VBox(10, heading, table, controls);
        box.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        tableContainer.getChildren().setAll(box);
    }

    private String firstMetaLanguage() {
        if (currentDictionary == null) return "en";
        return currentDictionary.getMetaLanguageManager().getLanguages().stream()
            .sorted().findFirst().orElse("en");
    }

    private LiftDictionary loadDemoDictionary() {
        try {
            java.nio.file.Path tempDir = Files.createTempDirectory(
                "dict-default-"
            );
            tempDir.toFile().deleteOnExit();
            File liftFile = tempDir.resolve("20260302.lift").toFile();
            File rangesFile = tempDir.resolve("20260302.lift-ranges").toFile();
            try (
                InputStream inLift = MainController.class.getResourceAsStream(
                    "/lift/20260302.lift"
                );
                InputStream inRanges = MainController.class.getResourceAsStream(
                    "/lift/20260302.lift-ranges"
                )
            ) {
                if (inLift == null) return loadFallbackDemo();
                try (
                    FileOutputStream outLift = new FileOutputStream(liftFile)
                ) {
                    inLift.transferTo(outLift);
                }
                if (inRanges != null) {
                    try (
                        FileOutputStream outRanges = new FileOutputStream(
                            rangesFile
                        )
                    ) {
                        inRanges.transferTo(outRanges);
                    }
                }
            }
            return LiftDictionary.loadDictionaryFromFile(liftFile);
        } catch (Exception e) {
            return loadFallbackDemo();
        }
    }

    private LiftDictionary loadFallbackDemo() {
        try (
            InputStream in = MainController.class.getResourceAsStream(
                "/lift/demo.lift"
            )
        ) {
            if (in == null) return null;
            File tmp = Files.createTempFile("dict-demo-", ".lift").toFile();
            tmp.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                in.transferTo(out);
            }
            return LiftDictionary.loadDictionaryFromFile(tmp);
        } catch (Exception e) {
            LOGGER.log(
                Level.WARNING,
                "Impossible de charger le dictionnaire de démonstration",
                e
            );
            return null;
        }
    }


    private static void appendSep(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(part);
        }
    }
}
