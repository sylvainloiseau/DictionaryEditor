/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftTranslation;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Editor for a single {@link LiftExample}.
 *
 * Displays: source, example MultiText (object languages), translations (one MultiTextEditor per
 * translation type, meta-languages) + NotableEditor (notes, fields, traits, annotations, dates).
 * When add actions are given, translations can be added and deleted.
 */
public final class ExampleEditor extends VBox {

    private final TextField sourceField = new TextField();
    private ChangeListener<String> sourceListener;
    private final MultiTextEditor exampleTextEditor;
    private final VBox translationsBox = new VBox(6);
    private final NotableEditor notableEditor;
    private final LiftDictionary dictionary;

    public ExampleEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.exampleTextEditor = new MultiTextEditor(dictionary);
        this.notableEditor = new NotableEditor(this.dictionary);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #cba; -fx-border-radius: 4; -fx-background-color: #faf6f2; -fx-background-radius: 4;");

        sourceField.setPromptText(I18n.get("col.source"));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label(I18n.get("col.source")), 0, 0);
        grid.add(sourceField, 1, 0);
        GridPane.setHgrow(sourceField, Priority.ALWAYS);

        exampleTextEditor.useObjectLanguages();
        exampleTextEditor.setFixedLanguageRows(true);

        TitledPane exPane = new TitledPane(I18n.get("col.example"), exampleTextEditor);
        exPane.setExpanded(true);
        exPane.setAnimated(false);

        TitledPane trPane = new TitledPane(I18n.get("editor.translations"), translationsBox);
        trPane.setExpanded(true);
        trPane.setAnimated(false);

        TitledPane extPane = new TitledPane(I18n.get("editor.properties.inherited"), notableEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, exPane, trPane, extPane);
    }

    public void setExample(LiftExample ex) {
        setExample(ex, null);
    }

    /**
     * Same with optional callback to create annotations on MultiText (example, translations).
     */
    public void setExample(LiftExample ex, BiConsumer<String, MultiText> onAddAnnotation) {
        setExample(ex, onAddAnnotation, null);
    }

    /**
     * Same with optional add actions for translations and trait/annotation/field/note at
     * example level. When {@code addActions} is null, nothing can be added or deleted.
     */
    public void setExample(LiftExample ex, BiConsumer<String, MultiText> onAddAnnotation,
            ExtensibleAddActions addActions) {
        translationsBox.getChildren().clear();
        if (sourceListener != null) sourceField.textProperty().removeListener(sourceListener);
        sourceListener = null;
        List<String> knownAnnotationNames =
            EditorSupport.sortedFeatureIds(dictionary.getHeader().getAnnotationTypeManager());

        if (ex == null) {
            sourceField.setText("");
            exampleTextEditor.setMultiText(null);
            exampleTextEditor.setOnAddAnnotation(null, null);
            notableEditor.setModel(null, null);
            return;
        }

        sourceField.setText(ex.getSource().orElse(""));
        sourceListener = (obs, o, n) -> ex.setSource(n != null ? n : "");
        sourceField.textProperty().addListener(sourceListener);

        exampleTextEditor.setMultiText(ex.getExample());
        if (onAddAnnotation != null) {
            exampleTextEditor.setOnAddAnnotation(onAddAnnotation, knownAnnotationNames);
        }

        if (addActions != null) {
            Button addTrBtn = new Button(I18n.get("btn.addTranslation"));
            addTrBtn.getStyleClass().add("example-add-button");
            addTrBtn.setOnAction(e -> addTranslation(ex, addActions));
            translationsBox.getChildren().add(new FlowPane(6, 4, addTrBtn));
        }

        // Translations: one MultiTextEditor per translation type — meta-languages
        List<LiftTranslation> translations = ex.getTranslationComponents().stream()
            .sorted(Comparator.comparing(t -> EditorSupport.featureId(t.getType())))
            .toList();
        for (LiftTranslation tr : translations) {
            String type = EditorSupport.featureId(tr.getType());
            String label = type.isBlank()
                ? I18n.get("editor.translationDefault")
                : I18n.get("editor.translationTyped", type);
            MultiTextEditor mte = new MultiTextEditor(dictionary);
            mte.setFixedLanguageRows(true);
            mte.setMultiText(tr.getTranslation());
            if (onAddAnnotation != null) {
                mte.setOnAddAnnotation(onAddAnnotation, knownAnnotationNames);
            }
            Runnable onDelete = addActions == null ? null : () -> ComponentDeletion.deleteChecked(
                () -> ex.deleteTranslation(tr), () -> ex.addTranslation(tr), addActions::refresh);
            TitledPane tp = new TitledPane(label, EditorSupport.withDeleteButton(mte, onDelete));
            tp.setExpanded(true);
            tp.setAnimated(false);
            translationsBox.getChildren().add(tp);
        }

        notableEditor.setModel(ex, addActions);
    }

    private void addTranslation(LiftExample ex, ExtensibleAddActions addActions) {
        Set<String> used = ex.getTranslationComponents().stream()
            .map(t -> EditorSupport.featureId(t.getType()))
            .collect(Collectors.toSet());
        List<String> types = EditorSupport.sortedFeatureIds(dictionary.getHeader().getTranslationTypeManager())
            .stream().filter(t -> !used.contains(t)).toList();
        if (types.isEmpty()) {
            EditorSupport.showError(I18n.get("btn.addTranslation"), I18n.get("error.noTranslationType"));
            return;
        }
        ChoiceDialog<String> dlg = new ChoiceDialog<>(types.get(0), types);
        dlg.setTitle(I18n.get("btn.addTranslation"));
        dlg.setHeaderText(I18n.get("col.type"));
        dlg.showAndWait().ifPresent(type -> {
            dictionary.getComponentBuilder().translation(ex, type).build();
            addActions.refresh();
        });
    }
}
