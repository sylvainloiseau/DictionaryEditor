/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftfx.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftTranslation;
import fr.cnrs.lacito.liftfx.ComponentKind;
import fr.cnrs.lacito.liftfx.EditActions;
import fr.cnrs.lacito.liftfx.internal.ComponentSection;
import fr.cnrs.lacito.liftfx.internal.EditorSupport;
import fr.cnrs.lacito.liftfx.internal.Messages;
import fr.cnrs.lacito.liftfx.internal.Reveal;
import fr.cnrs.lacito.liftfx.internal.SectionPane;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Editor for a single {@link LiftExample}.
 *
 * Displays: source, example MultiText (object languages), translations (one MultiTextEditor per
 * translation type, meta-languages) + NotableEditor (notes, fields, traits, annotations, dates).
 * With {@link EditActions}, translations and properties can be added and deleted.
 */
public final class ExampleEditor extends VBox {

    private final TextField sourceField = new TextField();
    private ChangeListener<String> sourceListener;
    private final MultiTextEditor exampleTextEditor;
    private final ComponentSection translationsSection;
    private final NotableEditor notableEditor;
    private final LiftDictionary dictionary;

    public ExampleEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.exampleTextEditor = new MultiTextEditor(dictionary);
        this.notableEditor = new NotableEditor(this.dictionary);
        setPadding(new Insets(4));
        sourceField.setPromptText(Messages.get("col.source"));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label(Messages.get("col.source")), 0, 0);
        grid.add(sourceField, 1, 0);
        GridPane.setHgrow(sourceField, Priority.ALWAYS);

        exampleTextEditor.useObjectLanguages();
        exampleTextEditor.setFixedLanguageRows(true);
        exampleTextEditor.getStyleClass().add(Reveal.FIRST_INPUT);

        TitledPane exPane = new TitledPane(Messages.get("col.example"), exampleTextEditor);
        exPane.setExpanded(true);
        exPane.setAnimated(false);

        translationsSection = new ComponentSection(ComponentKind.TRANSLATION, Messages.get("editor.translations"), true);

        SectionPane extPane = SectionPane.properties(Messages.get("editor.properties.inherited"), notableEditor);

        getChildren().addAll(grid, exPane, translationsSection, extPane);
    }

    public void setExample(LiftExample ex) {
        setExample(ex, null);
    }

    /** With {@code actions}, translations, annotations and properties can be added and deleted. */
    public void setExample(LiftExample ex, EditActions actions) {
        translationsSection.setOwner(ex, actions);
        if (sourceListener != null) sourceField.textProperty().removeListener(sourceListener);
        sourceListener = null;

        if (ex == null) {
            sourceField.setText("");
            exampleTextEditor.setMultiText(null);
            exampleTextEditor.setEditActions(null);
            notableEditor.setModel(null, null);
            return;
        }

        sourceField.setText(ex.getSource().orElse(""));
        sourceListener = (obs, o, n) -> ex.setSource(n != null ? n : "");
        sourceField.textProperty().addListener(sourceListener);

        exampleTextEditor.setMultiText(ex.getExample());
        exampleTextEditor.setEditActions(actions);

        // Translations: one MultiTextEditor per translation type — meta-languages
        List<LiftTranslation> translations = ex.getTranslationComponents().stream()
            .sorted(Comparator.comparing(t -> EditorSupport.featureId(t.getType())))
            .toList();
        List<Node> cards = new ArrayList<>();
        for (LiftTranslation tr : translations) {
            String type = EditorSupport.featureId(tr.getType());
            String label = type.isBlank()
                ? Messages.get("editor.translationDefault")
                : Messages.get("editor.translationTyped", type);
            MultiTextEditor mte = new MultiTextEditor(dictionary);
            mte.setFixedLanguageRows(true);
            mte.setMultiText(tr.getTranslation());
            mte.setEditActions(actions);
            cards.add(translationsSection.card(tr, label, mte, () -> ex.deleteTranslation(tr)));
        }
        translationsSection.setItems(cards);

        notableEditor.setModel(ex, actions);
    }
}
