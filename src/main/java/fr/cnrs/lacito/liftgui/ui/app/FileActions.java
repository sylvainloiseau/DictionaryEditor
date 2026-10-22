package fr.cnrs.lacito.liftgui.ui.app;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftgui.core.DictionaryService;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** The File menu: new, open, recent files, save, save as, and the dictionary shown at startup. */
public final class FileActions {

    private static final Logger LOGGER = Logger.getLogger(FileActions.class.getName());

    private final DictionaryDocument document;
    private final DictionaryService service = new DictionaryService();
    private final AppPreferences prefs;
    private final Supplier<Window> owner;
    private RecentFiles recentFiles;

    public FileActions(DictionaryDocument document, AppPreferences prefs, Supplier<Window> owner) {
        this.document = document;
        this.prefs = prefs;
        this.owner = owner;
    }

    public void setRecentFiles(RecentFiles recentFiles) {
        this.recentFiles = recentFiles;
    }

    /**
     * Picks the dictionary to show at startup: the one handed over by a scene reload
     * (language change), else the last file opened, else — only if no file was ever
     * opened — the demo dictionary.
     */
    public void loadInitial() {
        DictionaryDocument.Snapshot handoff = DictionaryDocument.takeHandoff();
        if (handoff != null) {
            document.restore(handoff);
            return;
        }
        List<String> recents = recentFiles == null ? List.of() : recentFiles.paths();
        if (recents.isEmpty()) {
            document.load(service.loadDemo(), null, true);
            return;
        }
        File lastFile = new File(recents.get(0));
        try {
            document.load(service.loadFromFile(lastFile), lastFile, false);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ouverture du dernier fichier LIFT", e);
            document.load(null, null, false);
            Platform.runLater(() -> Dialogs.error(I18n.get(Keys.ERROR_OPEN), I18n.formatErrorMessage("error.open.detail", e)));
        }
    }

    /** Asks what to do with unsaved changes. Returns true when the caller may go on. */
    public boolean confirmDiscardChanges() {
        if (!document.isDirty()) return true;
        ButtonType save = new ButtonType(I18n.get("btn.save"), ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType(I18n.get("btn.discard"), ButtonBar.ButtonData.NO);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, I18n.get("confirm.unsavedChanges"), save, discard, ButtonType.CANCEL);
        a.setTitle(I18n.get("confirm.unsavedChangesTitle"));
        a.setHeaderText(null);
        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty() || r.get() == ButtonType.CANCEL) return false;
        if (r.get() == save) {
            save();
            return !document.isDirty();
        }
        return true;
    }

    /** File → Open. */
    public void open() {
        if (!confirmDiscardChanges()) return;
        FileChooser ch = new FileChooser();
        ch.setTitle(I18n.get("dialog.openLift"));
        File dir = prefs.defaultDirectory();
        if (dir != null) ch.setInitialDirectory(dir);
        ch.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(I18n.get("dialog.liftFilter"), "*.lift"),
            new FileChooser.ExtensionFilter(I18n.get("dialog.allFilter"), "*.*")
        );
        File f = ch.showOpenDialog(owner.get());
        if (f != null) openFile(f);
    }

    /** Opens a recent file, after asking what to do with unsaved changes. */
    public void openRecent(File f) {
        if (confirmDiscardChanges()) openFile(f);
    }

    /** Opens a LIFT file. The caller has already asked what to do with unsaved changes. */
    private void openFile(File f) {
        try {
            document.load(service.loadFromFile(f), f, false);
            if (recentFiles != null) recentFiles.add(f);
            // What the file uses but does not declare: kept, read-only, not saved.
            java.util.List<String> warnings = document.dictionary().getLoadWarnings();
            if (!warnings.isEmpty()) {
                Dialogs.warnings(I18n.get("loadWarnings.title"), I18n.get("loadWarnings.header", warnings.size()), warnings);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ouverture du fichier LIFT", e);
            Dialogs.error(I18n.get(Keys.ERROR_OPEN), I18n.formatErrorMessage("error.open.detail", e));
        }
    }

    /** Saves to the current file; a new or demo dictionary goes through "Save as". */
    public void save() {
        if (document.dictionary() == null) {
            Dialogs.error(I18n.get("error.save"), I18n.get("error.noDictionary"));
            return;
        }
        if (document.file() == null || document.isDemo()) {
            saveAs();
            return;
        }
        try {
            document.dictionary().save(document.file());
            document.markSaved(document.file());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Sauvegarde du dictionnaire", e);
            Dialogs.error(I18n.get("error.save"), I18n.formatErrorMessage("error.save.detail", e));
        }
    }

    public void saveAs() {
        if (document.dictionary() == null) {
            Dialogs.error(I18n.get("error.saveAs"), I18n.get("error.noDictionaryShort"));
            return;
        }
        FileChooser ch = new FileChooser();
        ch.setTitle(I18n.get("dialog.saveLift"));
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.get("dialog.liftFilter"), "*.lift"));
        File current = document.file();
        if (current != null && !document.isDemo()) {
            ch.setInitialDirectory(current.getParentFile());
            ch.setInitialFileName(current.getName());
        }
        File f = ch.showSaveDialog(owner.get());
        if (f == null) return;
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".lift")) {
            f = new File(f.getParentFile(), f.getName() + ".lift");
        }
        try {
            document.dictionary().save(f);
            document.markSaved(f);
            if (recentFiles != null) recentFiles.add(f);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Sauvegarde du dictionnaire sous un autre fichier", e);
            Dialogs.error(I18n.get("error.saveAs"), I18n.formatErrorMessage("error.saveAs.detail", e));
        }
    }

    /** Creates an empty dictionary, asking for its object and meta languages. */
    public void newDictionary() {
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
        grid.addRow(0, new Label(I18n.get("nav.objectLangs")), objField);
        grid.addRow(1, new Label(I18n.get("nav.metaLangs")), metaField);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(bt -> bt == ButtonType.OK ? new String[] { objField.getText(), metaField.getText() } : null);
        dlg.showAndWait().ifPresent(langs -> {
            String[] obj = splitLanguageCodes(langs[0]);
            String[] meta = splitLanguageCodes(langs[1]);
            if (obj.length == 0 || meta.length == 0) {
                Dialogs.error(I18n.get("dialog.newDictionary"), I18n.get("error.newDictionaryLanguages"));
                return;
            }
            LiftDictionary d = LiftDictionary.makeBuilder().withObjectLanguages(obj).withMetaLanguages(meta).build();
            document.load(d, null, false);
        });
    }

    private static String[] splitLanguageCodes(String text) {
        if (text == null) return new String[0];
        return Arrays.stream(text.split("[,\\s]+"))
            .map(String::trim)
            .filter(t -> !t.isEmpty())
            .distinct()
            .toArray(String[]::new);
    }
}
