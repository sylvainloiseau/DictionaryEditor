package fr.cnrs.lacito.liftgui.ui.app;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.export.EntrySelection;
import fr.cnrs.lacito.liftapi.export.LatexCompilation;
import fr.cnrs.lacito.liftapi.export.LatexEngine;
import fr.cnrs.lacito.liftapi.export.LatexEngineKind;
import fr.cnrs.lacito.liftapi.export.LatexEngineRequest;
import fr.cnrs.lacito.liftapi.export.LatexExport;
import fr.cnrs.lacito.liftapi.export.LatexExportException;
import fr.cnrs.lacito.liftapi.export.LatexExportOptions;
import fr.cnrs.lacito.liftapi.export.LatexExportWarning;
import fr.cnrs.lacito.liftapi.export.Polyglossia;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Tools → Export as LaTeX / PDF: the settings of {@link LatexExport}, the checks before
 * exporting, a preview on a sample, and the compilation to PDF.
 *
 * <p>The settings of the last export of a dictionary come back when the dialog is opened
 * again; "Load settings…" and "Save settings…" read and write the JSON file of
 * {@code lift-api latex --config}.
 */
public final class LatexExportDialog {

    private static final Logger LOGGER = Logger.getLogger(LatexExportDialog.class.getName());

    /** The key, in the dialog pane's properties, of the dialog object (for tests). */
    public static final String PANE_PROPERTY = "latexExportDialog";

    private static final String INSTALL_URL = "https://www.latex-project.org/get/";
    private static final int PREVIEW_ENTRIES = 50;
    private static final java.time.Duration RUN_TIMEOUT = java.time.Duration.ofMinutes(10);

    /** The file and directory choosers; replaced in tests. */
    public interface Choosers {
        File saveTex(Window owner, File initial);
        File chooseDirectory(Window owner, File initial);
        File chooseProgram(Window owner);
        File saveSettings(Window owner, File initial);
        File openSettings(Window owner, File initial);
        File openStylesheet(Window owner, File initial);
        File saveStylesheet(Window owner, File initial);
    }

    /** The engines found on the machine; replaced in tests. */
    private static Supplier<List<LatexEngine>> engineFinder = LatexEngine::findAll;

    /** For tests: the engines the dialog finds, instead of searching the machine. */
    public static void setEngineFinderForTests(Supplier<List<LatexEngine>> finder) {
        engineFinder = finder == null ? LatexEngine::findAll : finder;
    }

    private final LiftDictionary dictionary;
    private final File dictionaryFile;
    private final AppPreferences prefs;
    /** Opens a URI (a file, a folder, a web page) in the platform's default application. */
    private final Consumer<String> opener;
    private Choosers choosers = new FxChoosers();

    private final Dialog<Void> dialog = new Dialog<>();
    private final TextField titleField = new TextField();
    private final TextField authorField = new TextField();
    private final CheckBox thematicBox = new CheckBox(I18n.get("latex.thematic"));
    private final CheckBox listOfFiguresBox = new CheckBox(I18n.get("latex.listOfFigures"));
    private final CheckBox figuresBox = new CheckBox(I18n.get("latex.figures"));
    private final TextField illustrationsField = new TextField();
    private final Button illustrationsButton = new Button(I18n.get("latex.browse"));
    private final ComboBox<String> mainFontBox = fontBox(I18n.get("latex.mainFontDefault"));
    private final ComboBox<String> sortBox = new ComboBox<>();
    private final TextField outputField = new TextField();
    private final TextField stylesheetField = new TextField();
    /** The messages of the stylesheets during the last export. */
    private volatile List<String> stylesheetMessages = List.of();
    private final CheckBox pdfBox = new CheckBox(I18n.get("latex.makePdf"));
    private final ComboBox<EngineChoice> engineBox = new ComboBox<>();
    private final CheckBox openPdfBox = new CheckBox(I18n.get("latex.openPdf"));
    private final VBox warningsBox = new VBox(4);
    private final ProgressBar progress = new ProgressBar();
    private final Label status = new Label();
    private final Button cancelButton = new Button(I18n.get("latex.cancel"));
    private final Button previewButton = new Button(I18n.get("latex.preview"));
    private final Button exportButton = new Button(I18n.get("latex.export"));
    private final List<LanguageRow> rows = new ArrayList<>();
    private final Map<LatexEngineKind, LatexEngine> engines = new LinkedHashMap<>();
    private final PauseTransition recheck = new PauseTransition(javafx.util.Duration.millis(300));
    private VBox form;
    private Task<?> running;

    /** A language of the dictionary: its code, its polyglossia name, its font. */
    record LanguageRow(String code, ComboBox<String> name, ComboBox<String> font) {}

    /** An item of the engine list: auto, or one kind, found or not. */
    record EngineChoice(LatexEngineKind kind, LatexEngine found, String label) {
        boolean available() {
            return kind == null || found != null;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private LatexExportDialog(LiftDictionary dictionary, File dictionaryFile, AppPreferences prefs, Consumer<String> opener) {
        this.dictionary = dictionary;
        this.dictionaryFile = dictionaryFile;
        this.prefs = prefs;
        this.opener = opener;
    }

    /**
     * Opens the dialog for a dictionary.
     *
     * @param dictionaryFile the dictionary's file, or null (a new or demo dictionary)
     * @param opener opens a URI (a file, a folder, a web page) in the platform's default application
     */
    public static void show(LiftDictionary dictionary, File dictionaryFile, AppPreferences prefs,
                            Window owner, Consumer<String> opener) {
        LatexExportDialog d = new LatexExportDialog(dictionary, dictionaryFile, prefs, opener);
        d.build(owner);
        d.dialog.show();
    }

    /** For tests: replaces the file choosers. */
    public void setChoosers(Choosers choosers) {
        this.choosers = choosers;
    }

    /* ─── Building ─── */

    private void build(Window owner) {
        dialog.initOwner(owner);
        dialog.setTitle(I18n.get("latex.title"));
        dialog.setHeaderText(null);
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getProperties().put(PANE_PROPERTY, this);
        dialog.setOnCloseRequest(e -> {
            if (running != null) running.cancel();
        });

        titleField.setId("latexTitle");
        authorField.setId("latexAuthor");
        figuresBox.setId("latexFigures");
        illustrationsField.setId("latexIllustrations");
        mainFontBox.setId("latexMainFont");
        sortBox.setId("latexSort");
        outputField.setId("latexOutput");
        stylesheetField.setId("latexStylesheet");
        pdfBox.setId("latexPdf");
        engineBox.setId("latexEngine");
        openPdfBox.setId("latexOpenPdf");
        previewButton.setId("latexPreview");
        exportButton.setId("latexExport");
        warningsBox.setId("latexWarnings");
        titleField.setPromptText(I18n.get("latex.titleField"));
        authorField.setPromptText(I18n.get("latex.author"));
        illustrationsButton.setOnAction(e -> {
            File f = choosers.chooseDirectory(window(), existingDirectory(illustrationsField.getText()));
            if (f != null) illustrationsField.setText(f.getAbsolutePath());
        });
        illustrationsField.disableProperty().bind(figuresBox.selectedProperty().not());
        illustrationsButton.disableProperty().bind(figuresBox.selectedProperty().not());
        HBox illustrations = new HBox(8, illustrationsField, illustrationsButton);
        HBox.setHgrow(illustrationsField, Priority.ALWAYS);

        Button outputButton = new Button(I18n.get("latex.browse"));
        outputButton.setOnAction(e -> {
            File f = choosers.saveTex(window(), outputField.getText().isBlank() ? null : new File(outputField.getText()));
            if (f != null) outputField.setText(f.getAbsolutePath());
        });
        HBox output = new HBox(8, outputField, outputButton);
        HBox.setHgrow(outputField, Priority.ALWAYS);

        Button stylesheetButton = new Button(I18n.get("latex.browse"));
        stylesheetButton.setOnAction(e -> {
            File f = choosers.openStylesheet(window(), baseDirectory() == null ? null : baseDirectory().toFile());
            if (f != null) stylesheetField.setText(f.getAbsolutePath());
        });
        Button clearStylesheet = new Button(I18n.get("latex.stylesheetClear"));
        clearStylesheet.setId("latexStylesheetClear");
        clearStylesheet.setOnAction(e -> stylesheetField.clear());
        Button createStylesheet = new Button(I18n.get("latex.stylesheetCreate"));
        createStylesheet.setId("latexStylesheetCreate");
        createStylesheet.setOnAction(e -> createStylesheet());
        HBox stylesheet = new HBox(8, stylesheetField, stylesheetButton, clearStylesheet, createStylesheet);
        HBox.setHgrow(stylesheetField, Priority.ALWAYS);

        GridPane general = new GridPane();
        general.setHgap(10);
        general.setVgap(8);
        int r = 0;
        general.addRow(r++, new Label(I18n.get("latex.titleField")), titleField);
        general.addRow(r++, new Label(I18n.get("latex.author")), authorField);
        general.add(new VBox(4, thematicBox, listOfFiguresBox, figuresBox), 1, r++);
        general.addRow(r++, new Label(I18n.get("latex.illustrations")), illustrations);
        general.addRow(r++, new Label(I18n.get("latex.mainFont")), mainFontBox);
        general.addRow(r++, new Label(I18n.get("latex.sortLanguage")), sortBox);
        general.addRow(r++, new Label(I18n.get("latex.output")), output);
        general.addRow(r++, new Label(I18n.get("latex.stylesheet")), stylesheet);
        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(illustrations, Priority.ALWAYS);
        GridPane.setHgrow(output, Priority.ALWAYS);
        GridPane.setHgrow(stylesheet, Priority.ALWAYS);

        sortBox.getItems().add("");
        sortBox.getItems().addAll(new TreeSet<>(dictionary.getMetaLanguageManager().getLanguages()));
        sortBox.setCellFactory(l -> sortCell());
        sortBox.setButtonCell(sortCell());

        form = new VBox(12, general, new Separator(), languagesPane(), new Separator(), pdfPane(),
            new Separator(), new Label(I18n.get("latex.warnings")), warningsBox);
        form.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(560);

        Button load = new Button(I18n.get("latex.loadSettings"));
        load.setId("latexLoad");
        load.setOnAction(e -> loadSettings());
        Button save = new Button(I18n.get("latex.saveSettings"));
        save.setId("latexSave");
        save.setOnAction(e -> saveSettings());
        previewButton.setOnAction(e -> preview());
        exportButton.setOnAction(e -> export());
        exportButton.setDefaultButton(true);
        HBox actions = new HBox(8, load, save, spacer(), previewButton, exportButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        progress.setMaxWidth(Double.MAX_VALUE);
        cancelButton.setOnAction(e -> {
            if (running != null) running.cancel();
        });
        HBox progressBox = new HBox(8, progress, status, cancelButton);
        progressBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progress, Priority.ALWAYS);
        progressBox.visibleProperty().bind(cancelButton.visibleProperty());
        progressBox.managedProperty().bind(cancelButton.visibleProperty());
        cancelButton.setVisible(false);

        VBox content = new VBox(10, scroll, actions, progressBox);
        content.setPrefWidth(760);
        dialog.getDialogPane().setContent(content);

        findEngines();
        fill(initialSettings(), initialOutput());
        pdfBox.setSelected(!engines.isEmpty());
        recheck.setOnFinished(e -> refreshWarnings());
        for (TextField t : List.of(illustrationsField, outputField, stylesheetField)) t.textProperty().addListener((o, a, b) -> scheduleCheck());
        // The custom stylesheet may be edited in another application: check again on coming back.
        dialog.setOnShown(e -> window().focusedProperty().addListener((o, was, focused) -> {
            if (focused && running == null) scheduleCheck();
        }));
        for (CheckBox c : List.of(thematicBox, figuresBox)) c.selectedProperty().addListener((o, a, b) -> scheduleCheck());
        refreshWarnings();
    }

    private static ListCell<String> sortCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.isEmpty() ? I18n.get("latex.sortDefault") : item);
            }
        };
    }

    private static Node spacer() {
        HBox s = new HBox();
        HBox.setHgrow(s, Priority.ALWAYS);
        return s;
    }

    private Window window() {
        return dialog.getDialogPane().getScene() == null ? null : dialog.getDialogPane().getScene().getWindow();
    }

    /** One row per language: object languages first, then the meta languages that are not object languages. */
    private Node languagesPane() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.addRow(0, bold(I18n.get("latex.code")), bold(I18n.get("latex.polyglossia")), bold(I18n.get("latex.font")));
        int r = 1;
        TreeSet<String> object = new TreeSet<>(dictionary.getObjectLanguageManager().getLanguages());
        TreeSet<String> meta = new TreeSet<>(dictionary.getMetaLanguageManager().getLanguages());
        meta.removeAll(object);
        for (var section : List.of(Map.entry("latex.objectLanguages", object), Map.entry("latex.metaLanguages", meta))) {
            if (section.getValue().isEmpty()) continue;
            Label header = new Label(I18n.get(section.getKey()));
            header.getStyleClass().add("latex-section");
            grid.add(header, 0, r++, 3, 1);
            for (String code : section.getValue()) {
                ComboBox<String> name = new ComboBox<>(FXCollections.observableArrayList(Polyglossia.names()));
                name.setEditable(true);
                name.setId("polyglossia-" + code);
                ComboBox<String> font = fontBox(I18n.get("latex.fontDefault"));
                font.setId("font-" + code);
                LanguageRow row = new LanguageRow(code, name, font);
                rows.add(row);
                name.valueProperty().addListener((o, a, b) -> {
                    // A name already given a font in another row brings that font.
                    rows.stream().filter(x -> x != row && same(x.name().getValue(), b) && !text(x.font()).isEmpty())
                        .findFirst().ifPresent(x -> row.font().setValue(text(x.font())));
                    scheduleCheck();
                });
                font.valueProperty().addListener((o, a, b) -> {
                    // Two codes with the same polyglossia name share one font.
                    for (LanguageRow x : rows) {
                        if (x != row && same(x.name().getValue(), row.name().getValue()) && !same(text(x.font()), b)) {
                            x.font().setValue(b);
                        }
                    }
                    scheduleCheck();
                });
                grid.addRow(r++, new Label(code), name, font);
                GridPane.setHgrow(name, Priority.ALWAYS);
                GridPane.setHgrow(font, Priority.ALWAYS);
            }
        }
        return new VBox(6, new Label(I18n.get("latex.languages")), grid);
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && !a.isBlank() && a.trim().equals(b.trim());
    }

    private static Label bold(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    private static ComboBox<String> fontBox(String prompt) {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(Font.getFamilies()));
        box.setEditable(true);
        box.setPromptText(prompt);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private Node pdfPane() {
        engineBox.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(EngineChoice item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
                setDisable(item != null && !item.available());
            }
        });
        engineBox.valueProperty().addListener((o, a, b) -> {
            if (b != null && !b.available()) Platform.runLater(() -> engineBox.setValue(a != null && a.available() ? a : engineBox.getItems().get(0)));
        });
        engineBox.disableProperty().bind(pdfBox.selectedProperty().not());
        openPdfBox.disableProperty().bind(pdfBox.selectedProperty().not());
        openPdfBox.setSelected(prefs.latexOpenPdf());
        Button program = new Button(I18n.get("latex.chooseProgram"));
        program.setOnAction(e -> chooseProgram());
        VBox box = new VBox(6, new HBox(8, pdfBox, engineBox, program), openPdfBox);
        if (engines.isEmpty()) {
            pdfBox.setSelected(false);
            pdfBox.setDisable(true);
            Hyperlink install = new Hyperlink(I18n.get("latex.installTex"));
            install.setOnAction(e -> opener.accept(INSTALL_URL));
            box.getChildren().add(new HBox(8, new Label(I18n.get("latex.noEngine")), install));
        }
        return box;
    }

    private void open(Path file) {
        opener.accept(file.toAbsolutePath().toUri().toString());
    }

    /** The engines, each kind once; a program chosen in the preferences replaces the one found. */
    private void findEngines() {
        engines.clear();
        for (LatexEngine e : engineFinder.get()) engines.putIfAbsent(e.kind(), e);
        for (LatexEngineKind k : LatexEngineKind.values()) {
            String chosen = prefs.latexProgram(k.command());
            if (chosen != null) {
                try {
                    engines.put(k, LatexEngine.at(Path.of(chosen), k));
                } catch (LatexExportException ex) {
                    LOGGER.log(Level.INFO, "The chosen " + k.displayName() + " is gone: " + chosen, ex);
                }
            }
        }
        EngineChoice selected = engineBox.getValue();
        engineBox.getItems().setAll(engineChoices());
        engineBox.setValue(selected == null ? engineBox.getItems().get(0)
            : engineBox.getItems().stream().filter(c -> c.kind() == selected.kind()).findFirst().orElse(engineBox.getItems().get(0)));
    }

    private List<EngineChoice> engineChoices() {
        List<EngineChoice> list = new ArrayList<>();
        list.add(new EngineChoice(null, null, I18n.get("latex.engineAuto")));
        for (LatexEngineKind k : LatexEngineKind.values()) {
            LatexEngine e = engines.get(k);
            list.add(new EngineChoice(k, e, e == null
                ? I18n.get("latex.engineNotFound", k.displayName())
                : I18n.get("latex.engineFound", k.displayName(), e.version().isEmpty() ? e.path().toString() : e.version() + ", " + e.path())));
        }
        return list;
    }

    private void chooseProgram() {
        File f = choosers.chooseProgram(window());
        if (f == null) return;
        EngineChoice c = engineBox.getValue();
        LatexEngineKind kind = c != null && c.kind() != null ? c.kind()
            : LatexEngineKind.parse(f.getName().replaceFirst("\\..*$", "")).orElse(LatexEngineKind.XELATEX);
        prefs.setLatexProgram(kind.command(), f.getAbsolutePath());
        findEngines();
        if (!engines.isEmpty() && pdfBox.isDisabled()) pdfBox.setDisable(false);
    }

    /* ─── Settings ─── */

    private LatexExportOptions initialSettings() {
        LatexExportOptions suggested = LatexExportOptions.suggested(dictionary,
            dictionaryFile == null ? null : dictionaryFile.toPath());
        String json = prefs.latexSettings(dictionaryFile);
        if (json != null) {
            try {
                return suggested.merge(LatexExportOptions.fromJson(json, baseDirectory()));
            } catch (LatexExportException e) {
                LOGGER.log(Level.INFO, "Ignoring the saved LaTeX settings", e);
            }
        }
        return suggested;
    }

    private Path initialOutput() {
        String saved = prefs.latexOutput(dictionaryFile);
        if (saved != null) return Path.of(saved);
        if (dictionaryFile != null) {
            return dictionaryFile.toPath().resolveSibling(dictionaryFile.getName().replaceFirst("\\.[^.]*$", "") + ".tex");
        }
        File dir = prefs.defaultDirectory();
        return (dir != null ? dir.toPath() : Path.of(System.getProperty("user.home"))).resolve("dictionary.tex");
    }

    private Path baseDirectory() {
        return dictionaryFile == null ? null : dictionaryFile.getAbsoluteFile().getParentFile().toPath();
    }

    /** Puts settings into the fields. */
    void fill(LatexExportOptions o, Path output) {
        titleField.setText(o.title() == null ? "" : o.title());
        authorField.setText(o.author() == null ? "" : o.author());
        thematicBox.setSelected(o.thematicDictionaryOrDefault());
        listOfFiguresBox.setSelected(o.listOfFiguresOrDefault());
        figuresBox.setSelected(o.figuresOrDefault());
        illustrationsField.setText(o.illustrationDirectory() == null ? "" : o.illustrationDirectory().toString());
        mainFontBox.setValue(o.mainFont() == null ? "" : o.mainFont());
        String sort = o.sortLanguage() == null ? "" : o.sortLanguage();
        if (!sortBox.getItems().contains(sort)) sortBox.getItems().add(sort);
        sortBox.setValue(sort);
        Map<String, String> languages = o.polyglossiaLanguages() == null ? Map.of() : o.polyglossiaLanguages();
        Map<String, String> fonts = o.fonts() == null ? Map.of() : o.fonts();
        for (LanguageRow row : rows) {
            String name = languages.getOrDefault(row.code(), "");
            row.name().setValue(name);
            row.font().setValue(fonts.getOrDefault(name, ""));
        }
        if (output != null) outputField.setText(output.toString());
        stylesheetField.setText(o.customStylesheet() == null ? "" : o.customStylesheet().toString());
        LatexEngineRequest engine = o.latexEngineOrDefault();
        engineBox.getItems().stream()
            .filter(c -> engine.kind().map(k -> k == c.kind()).orElse(c.kind() == null))
            .findFirst().ifPresent(engineBox::setValue);
    }

    /** The settings in the fields. */
    LatexExportOptions read() {
        Map<String, String> languages = new TreeMap<>();
        Map<String, String> fonts = new TreeMap<>();
        for (LanguageRow row : rows) {
            String name = text(row.name());
            if (name.isEmpty()) continue;
            languages.put(row.code(), name);
            String font = text(row.font());
            if (!font.isEmpty()) fonts.put(name, font);
        }
        String dir = illustrationsField.getText().trim();
        String custom = stylesheetField.getText().trim();
        EngineChoice engine = engineBox.getValue();
        return new LatexExportOptions(
            titleField.getText(), authorField.getText(),
            thematicBox.isSelected(), listOfFiguresBox.isSelected(), figuresBox.isSelected(),
            dir.isEmpty() ? null : Path.of(dir).toAbsolutePath(),
            languages, emptyToNull(sortBox.getValue()), emptyToNull(text(mainFontBox)), fonts,
            engine == null || engine.kind() == null ? LatexEngineRequest.auto() : LatexEngineRequest.of(engine.kind()),
            custom.isEmpty() ? null : Path.of(custom).toAbsolutePath());
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** The value of an editable combo box, including what is typed but not committed. */
    private static String text(ComboBox<String> box) {
        String v = box.isEditable() ? box.getEditor().getText() : box.getValue();
        return v == null ? "" : v.trim();
    }

    private Path output() {
        String s = outputField.getText().trim();
        return s.isEmpty() ? null : Path.of(s).toAbsolutePath();
    }

    private void remember(LatexExportOptions o, Path output) {
        try {
            prefs.setLatexSettings(dictionaryFile, o.toJson(baseDirectory()));
            if (output != null) prefs.setLatexOutput(dictionaryFile, output.toString());
            prefs.setLatexOpenPdf(openPdfBox.isSelected());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot remember the LaTeX settings", e);
        }
    }

    /** Writes a starting custom stylesheet (a no-op until edited), and uses it. */
    private void createStylesheet() {
        File initial = baseDirectory() == null ? null : baseDirectory().resolve("custom.xsl").toFile();
        File f = choosers.saveStylesheet(window(), initial);
        if (f == null) return;
        try {
            LatexExport.writeStylesheetTemplate(f.toPath());
        } catch (IOException e) {
            Dialogs.error(I18n.get("latex.title"), e.getMessage());
            return;
        }
        stylesheetField.setText(f.getAbsolutePath());
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(window());
        a.setTitle(I18n.get("latex.title"));
        a.setHeaderText(null);
        a.setContentText(I18n.get("latex.stylesheetCreated", f.getAbsolutePath()));
        ButtonType open = new ButtonType(I18n.get("latex.stylesheetOpen"));
        a.getButtonTypes().setAll(open, ButtonType.CLOSE);
        a.showAndWait().filter(b -> b == open).ifPresent(b -> open(f.toPath()));
    }

    private void loadSettings() {
        File f = choosers.openSettings(window(), baseDirectory() == null ? null : baseDirectory().toFile());
        if (f == null) return;
        try {
            fill(LatexExportOptions.defaults().merge(LatexExportOptions.fromJson(f.toPath())), null);
            refreshWarnings();
        } catch (LatexExportException e) {
            Dialogs.error(I18n.get("latex.title"), e.getMessage());
        }
    }

    private void saveSettings() {
        File f = choosers.saveSettings(window(), baseDirectory() == null ? null : baseDirectory().toFile());
        if (f == null) return;
        try {
            read().writeJson(f.toPath());
        } catch (IOException e) {
            Dialogs.error(I18n.get("latex.title"), e.getMessage());
        }
    }

    /* ─── Checks ─── */

    private void scheduleCheck() {
        recheck.playFromStart();
    }

    /** The warnings of {@link LatexExport#check}, each expandable to its examples. */
    public void refreshWarnings() {
        warningsBox.getChildren().clear();
        List<LatexExportWarning> warnings = LatexExport.check(dictionary, read(), output());
        if (warnings.isEmpty()) {
            warningsBox.getChildren().add(new Label(I18n.get("latex.noWarning")));
            return;
        }
        for (LatexExportWarning w : warnings) {
            VBox examples = new VBox(2);
            for (String example : w.examples()) {
                if (w.kind() == LatexExportWarning.Kind.LANGUAGE_NOT_MAPPED) {
                    Hyperlink link = new Hyperlink(example);
                    link.setOnAction(e -> selectLanguage(example));
                    examples.getChildren().add(link);
                } else {
                    examples.getChildren().add(new Label(example));
                }
            }
            if (w.count() > w.examples().size()) examples.getChildren().add(new Label("…"));
            TitledPane pane = new TitledPane("⚠ " + I18n.get("latex.warn." + w.kind().name(), w.count()), examples);
            pane.setExpanded(false);
            pane.setAnimated(false);
            pane.getStyleClass().add("latex-warning");
            pane.setUserData(w.kind());
            if (w.examples().isEmpty()) pane.setCollapsible(false);
            warningsBox.getChildren().add(pane);
        }
    }

    /** Selects the polyglossia field of a language. */
    void selectLanguage(String code) {
        rows.stream().filter(r -> r.code().equals(code)).findFirst().ifPresent(r -> {
            r.name().requestFocus();
            r.name().getEditor().selectAll();
        });
    }

    /* ─── Running ─── */

    private void preview() {
        LatexExportOptions o = read();
        remember(o, output());
        Optional<LatexEngine> engine = pdfBox.isSelected() ? engine(o) : Optional.empty();
        run(new Task<Path>() {
            @Override
            protected Path call() throws Exception {
                Path dir = Files.createTempDirectory("dictionary-preview");
                Path tex = dir.resolve("preview.tex");
                updateMessage(I18n.get("latex.writing"));
                stylesheetMessages = LatexExport.export(dictionary, tex, o, EntrySelection.sample(PREVIEW_ENTRIES));
                if (engine.isEmpty()) return tex;
                updateMessage(I18n.get("latex.compiling", engine.get().kind().displayName()));
                LatexCompilation c = LatexExport.compile(tex, engine.get(), RUN_TIMEOUT);
                if (!c.success()) throw new CompilationFailed(c);
                return c.pdf();
            }
        }, this::open);
    }

    private void export() {
        Path tex = output();
        if (tex == null) {
            Dialogs.error(I18n.get("latex.title"), I18n.get("latex.noOutput"));
            return;
        }
        LatexExportOptions o = read();
        remember(o, tex);
        Optional<LatexEngine> engine = pdfBox.isSelected() ? engine(o) : Optional.empty();
        boolean openWhenDone = pdfBox.isSelected() && openPdfBox.isSelected();
        run(new Task<Path>() {
            @Override
            protected Path call() throws Exception {
                updateMessage(I18n.get("latex.writing"));
                stylesheetMessages = LatexExport.export(dictionary, tex, o, EntrySelection.all());
                if (engine.isEmpty()) return tex;
                updateMessage(I18n.get("latex.compiling", engine.get().kind().displayName()));
                LatexCompilation c = LatexExport.compile(tex, engine.get(), RUN_TIMEOUT);
                if (!c.success()) throw new CompilationFailed(c);
                return c.pdf();
            }
        }, file -> {
            if (openWhenDone) {
                open(file);
                dialog.close();
            } else {
                done(tex, engine.isPresent() ? file : null);
            }
        });
    }

    /** The engine for the settings: the chosen kind, else XeLaTeX, else LuaLaTeX. */
    private Optional<LatexEngine> engine(LatexExportOptions o) {
        Optional<LatexEngineKind> kind = o.latexEngineOrDefault().kind();
        if (kind.isPresent()) return Optional.ofNullable(engines.get(kind.get()));
        return Optional.ofNullable(engines.getOrDefault(LatexEngineKind.XELATEX, engines.get(LatexEngineKind.LUALATEX)));
    }

    /** A failed compilation, carried out of the task. */
    static final class CompilationFailed extends Exception {
        final LatexCompilation compilation;

        CompilationFailed(LatexCompilation compilation) {
            super(compilation.firstError().orElse(""));
            this.compilation = compilation;
        }
    }

    private void run(Task<Path> task, Consumer<Path> onSuccess) {
        running = task;
        form.setDisable(true);
        previewButton.setDisable(true);
        exportButton.setDisable(true);
        cancelButton.setVisible(true);
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> {
            finished();
            onSuccess.accept(task.getValue());
        });
        task.setOnCancelled(e -> {
            finished();
            status.setText(I18n.get("latex.cancelled"));
        });
        task.setOnFailed(e -> {
            finished();
            Throwable ex = task.getException();
            if (ex instanceof CompilationFailed f) {
                compilationFailed(f.compilation);
            } else {
                LOGGER.log(Level.WARNING, "LaTeX export", ex);
                Dialogs.error(I18n.get("latex.title"), ex.getMessage());
            }
        });
        Thread t = new Thread(task, "latex-export");
        t.setDaemon(true);
        t.start();
    }

    private void finished() {
        running = null;
        status.textProperty().unbind();
        status.setText("");
        form.setDisable(false);
        previewButton.setDisable(false);
        exportButton.setDisable(false);
        cancelButton.setVisible(false);
        progress.setProgress(0);
    }

    private void done(Path tex, Path pdf) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(window());
        a.setTitle(I18n.get("latex.title"));
        a.setHeaderText(I18n.get("latex.done"));
        String written = I18n.get("latex.doneMessage", pdf == null ? tex : tex + "\n" + pdf);
        a.setContentText(stylesheetMessages.isEmpty() ? written
            : written + "\n\n" + I18n.get("latex.messages", String.join("\n", stylesheetMessages)));
        ButtonType openPdf = new ButtonType(I18n.get("latex.openPdfButton"));
        ButtonType openTex = new ButtonType(I18n.get("latex.openTex"));
        ButtonType folder = new ButtonType(I18n.get("latex.showFolder"));
        a.getButtonTypes().setAll(ButtonType.CLOSE);
        if (pdf != null) a.getButtonTypes().add(0, openPdf);
        a.getButtonTypes().addAll(openTex, folder);
        a.showAndWait().ifPresent(b -> {
            if (b == openPdf) open(pdf);
            else if (b == openTex) open(tex);
            else if (b == folder) open(tex.getParent());
        });
    }

    private void compilationFailed(LatexCompilation c) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.initOwner(window());
        a.setTitle(I18n.get("latex.title"));
        String engine = c.engine().kind().displayName();
        a.setHeaderText(c.firstErrorLine()
            .map(line -> I18n.get("latex.compileFailed", engine, line))
            .orElse(I18n.get("latex.compileFailedNoLine", engine)));
        TextArea error = new TextArea(c.firstError().orElse(""));
        error.setEditable(false);
        error.setPrefRowCount(4);
        a.getDialogPane().setContent(error);
        ButtonType showLog = new ButtonType(I18n.get("latex.showLog"), ButtonBar.ButtonData.LEFT);
        a.getButtonTypes().setAll(showLog, ButtonType.CLOSE);
        a.showAndWait().filter(b -> b == showLog).ifPresent(b -> showLog(c));
    }

    private void showLog(LatexCompilation c) {
        String log;
        try {
            log = new String(Files.readAllBytes(c.log()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Dialogs.error(I18n.get("latex.title"), e.getMessage());
            return;
        }
        TextArea area = new TextArea(log);
        area.setEditable(false);
        area.setPrefSize(800, 500);
        String first = c.firstError().map(s -> s.lines().findFirst().orElse("")).orElse("");
        int at = first.isEmpty() ? -1 : log.indexOf(first);
        Alert a = new Alert(Alert.AlertType.NONE, "", ButtonType.CLOSE);
        a.initOwner(window());
        a.setTitle(I18n.get("latex.log"));
        a.setResizable(true);
        a.getDialogPane().setContent(area);
        if (at >= 0) Platform.runLater(() -> area.selectRange(at, at + first.length()));
        a.showAndWait();
    }

    private static File existingDirectory(String path) {
        if (path == null || path.isBlank()) return null;
        File f = new File(path.trim());
        return f.isDirectory() ? f : null;
    }

    /* ─── Choosers ─── */

    private static final class FxChoosers implements Choosers {
        @Override
        public File saveTex(Window owner, File initial) {
            FileChooser ch = new FileChooser();
            ch.setTitle(I18n.get("latex.output"));
            ch.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.get("latex.texFilter"), "*.tex"));
            if (initial != null) {
                if (initial.getParentFile() != null && initial.getParentFile().isDirectory()) ch.setInitialDirectory(initial.getParentFile());
                ch.setInitialFileName(initial.getName());
            }
            return ch.showSaveDialog(owner);
        }

        @Override
        public File chooseDirectory(Window owner, File initial) {
            DirectoryChooser ch = new DirectoryChooser();
            ch.setTitle(I18n.get("latex.illustrations"));
            if (initial != null) ch.setInitialDirectory(initial);
            return ch.showDialog(owner);
        }

        @Override
        public File chooseProgram(Window owner) {
            FileChooser ch = new FileChooser();
            ch.setTitle(I18n.get("latex.chooseProgram"));
            return ch.showOpenDialog(owner);
        }

        @Override
        public File saveSettings(Window owner, File initial) {
            FileChooser ch = settingsChooser(initial);
            return ch.showSaveDialog(owner);
        }

        @Override
        public File openSettings(Window owner, File initial) {
            return settingsChooser(initial).showOpenDialog(owner);
        }

        @Override
        public File openStylesheet(Window owner, File initial) {
            return stylesheetChooser(initial).showOpenDialog(owner);
        }

        @Override
        public File saveStylesheet(Window owner, File initial) {
            FileChooser ch = stylesheetChooser(initial == null ? null : initial.getParentFile());
            if (initial != null) ch.setInitialFileName(initial.getName());
            return ch.showSaveDialog(owner);
        }

        private static FileChooser stylesheetChooser(File initial) {
            FileChooser ch = new FileChooser();
            ch.setTitle(I18n.get("latex.stylesheet"));
            ch.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.get("latex.stylesheetFilter"), "*.xsl", "*.xslt"));
            if (initial != null && initial.isDirectory()) ch.setInitialDirectory(initial);
            return ch;
        }

        private static FileChooser settingsChooser(File initial) {
            FileChooser ch = new FileChooser();
            ch.setTitle(I18n.get("latex.title"));
            ch.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.get("latex.settingsFilter"), "*.json"));
            if (initial != null && initial.isDirectory()) ch.setInitialDirectory(initial);
            return ch;
        }
    }
}
