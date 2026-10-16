package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.HasExternalDocument;
import fr.cnrs.lacito.liftapi.model.LiftIllustration;
import fr.cnrs.lacito.liftapi.model.LiftMedia;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.io.File;
import java.nio.file.Path;
import java.util.function.Supplier;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Editor for the file of a {@link LiftMedia} (a sound recording of a pronunciation) or a
 * {@link LiftIllustration} (a picture of a sense): its path ({@code href}), with a "Choose…"
 * button, and its label (meta-languages).
 *
 * A chosen file under the dictionary's folder is stored relative to it, as FieldWorks
 * does; any other file with its absolute path. The file itself is not copied.
 */
public final class ExternalDocumentEditor extends VBox {

    private final TextField hrefField = new TextField();
    private final Button chooseBtn = new Button(I18n.get("btn.chooseFile"));
    private final MultiTextEditor labelEditor;
    private final Supplier<File> dictionaryFile;
    private ObjectProperty<String> bound;

    /**
     * @param dictionaryFile the file of the dictionary (relative paths are resolved against
     *                       its folder); may return null for an unsaved dictionary
     */
    public ExternalDocumentEditor(LiftDictionary dictionary, Supplier<File> dictionaryFile) {
        super(6);
        this.dictionaryFile = dictionaryFile;
        this.labelEditor = new MultiTextEditor(dictionary);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #bcb; -fx-border-radius: 4; -fx-background-color: #f6faf4; -fx-background-radius: 4;");

        hrefField.setPromptText(I18n.get("col.file"));
        HBox fileBox = new HBox(6, hrefField, chooseBtn);
        HBox.setHgrow(hrefField, Priority.ALWAYS);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label(I18n.get("col.file")), 0, 0);
        grid.add(fileBox, 1, 0);
        GridPane.setHgrow(fileBox, Priority.ALWAYS);

        TitledPane labelPane = new TitledPane(I18n.get("col.label"), labelEditor);
        labelPane.setExpanded(true);
        labelPane.setAnimated(false);

        getChildren().addAll(grid, labelPane);
    }

    public void setDocument(HasExternalDocument doc) {
        if (bound != null) hrefField.textProperty().unbindBidirectional(bound);
        bound = switch (doc) {
            case LiftMedia m -> m.hrefProperty();
            case LiftIllustration i -> i.hrefProperty();
            case null -> null;
        };
        if (bound == null) {
            hrefField.setText("");
            labelEditor.setMultiText(null);
            chooseBtn.setOnAction(null);
            return;
        }
        // Typing in the field is reported like any other text (one undo step per session).
        hrefField.textProperty().bindBidirectional(bound);
        ObjectProperty<String> href = bound;
        chooseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.get("btn.chooseFile"));
            File base = baseFolder();
            if (base != null) chooser.setInitialDirectory(base);
            File chosen = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (chosen != null) href.set(pathFor(chosen, base));
        });
        labelEditor.setMultiText(doc.getLabel());
    }

    private File baseFolder() {
        File f = dictionaryFile.get();
        File dir = f == null ? null : f.getAbsoluteFile().getParentFile();
        return dir != null && dir.isDirectory() ? dir : null;
    }

    /** The path to store: relative to the dictionary's folder when the file is under it. */
    static String pathFor(File chosen, File base) {
        Path file = chosen.getAbsoluteFile().toPath().normalize();
        if (base != null) {
            Path dir = base.getAbsoluteFile().toPath().normalize();
            if (file.startsWith(dir)) return dir.relativize(file).toString().replace(File.separatorChar, '/');
        }
        return file.toString();
    }
}
