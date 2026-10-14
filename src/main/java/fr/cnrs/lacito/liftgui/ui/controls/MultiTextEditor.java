/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Reusable editor for a {@link MultiText} (section 5.6 of the specification).
 *
 * Layout per language row:
 * <pre>
 *   lg : |_text_field_________| [-] [edit]
 * </pre>
 *
 * The languages offered are the dictionary's meta-languages by default; call
 * {@link #useObjectLanguages()} for a multitext written in the object language (entry forms,
 * example text, variant/etymology forms, pronunciations).
 *
 * Below all rows, a ComboBox dropdown lets the user pick an unused language to add a new row.
 * The dropdown is disabled when the field is populated for every available language.
 */
public final class MultiTextEditor extends VBox {

    private final VBox rowsBox = new VBox(6);
    private final VBox annotationsBox = new VBox(6);
    private final ComboBox<String> addLangCombo = new ComboBox<>();
    private final TitledPane annotationsPane = new TitledPane(I18n.get("editor.annotations"), annotationsBox);
    private final ObservableList<String> allLanguages = FXCollections.observableArrayList();
    private MultiText multiText;
    private boolean readOnly;
    /** When true: one {@link TextField} row per configured language (no language picker ComboBox). */
    private boolean fixedLanguageRows;
    private BiConsumer<String, MultiText> onAddAnnotation;
    private List<String> knownAnnotationNames = List.of();
    private final LiftDictionary dictionary;

    public MultiTextEditor(LiftDictionary dictionary) {
        super(8);
        this.dictionary = dictionary;
        setPadding(new Insets(4, 0, 4, 0));

        addLangCombo.setPromptText(I18n.get("multitext.addLanguage"));
        addLangCombo.setPrefWidth(200);
        addLangCombo.setEditable(false);
        addLangCombo.setOnAction(e -> {
            String sel = safeTrim(addLangCombo.getValue());
            if (!sel.isBlank()) {
                addRowForLang(sel);
                addLangCombo.setValue(null);
                refreshAddLangChoices();
            }
        });

        annotationsPane.setExpanded(false);
        annotationsPane.setAnimated(false);
        annotationsBox.setPadding(new Insets(8, 16, 8, 16));

        getChildren().addAll(rowsBox, addLangCombo, annotationsPane);
        setAvailableLanguages(dictionary.getMetaLanguageManager().getLanguages());
    }

    /** Offers the dictionary's object languages instead of its meta-languages. */
    public void useObjectLanguages() {
        setAvailableLanguages(dictionary.getObjectLanguageManager().getLanguages());
    }

    /** Optional: when set, enables "add annotation" button in the Annotations pane. Callback receives (name, multiText). */
    public void setOnAddAnnotation(BiConsumer<String, MultiText> callback, Collection<String> annotationNames) {
        this.onAddAnnotation = callback;
        this.knownAnnotationNames = annotationNames == null ? List.of() : new ArrayList<>(annotationNames);
        rebuildAnnotations();
    }

    public void setAvailableLanguages(Collection<String> langs) {
        allLanguages.setAll(normalizeLangs(langs));
        if (multiText != null) {
            for (String l : sorted(multiText.getLangs())) {
                if (!allLanguages.contains(l)) allLanguages.add(l);
            }
        }
        allLanguages.sort(Comparator.naturalOrder());
        if (fixedLanguageRows && multiText != null) {
            rebuild();
        } else {
            refreshAddLangChoices();
            rebuildAnnotations();
        }
    }

    public void setMultiText(MultiText mt) {
        this.multiText = mt;
        rebuild();
    }

    /**
     * When true, shows a text field for every available language
     * instead of only languages that already have content plus a dropdown to add a language.
     * Use for entry forms, sense gloss/definition, example text, etc.
     */
    public void setFixedLanguageRows(boolean fixed) {
        this.fixedLanguageRows = fixed;
        addLangCombo.setVisible(!fixed && !readOnly);
        addLangCombo.setManaged(!fixed && !readOnly);
        rebuild();
    }

    /** When true, displays content as read-only (grayed, non-editable). */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        addLangCombo.setVisible(!fixedLanguageRows && !readOnly);
        addLangCombo.setManaged(!fixedLanguageRows && !readOnly);
        if (readOnly) setStyle("-fx-background-color: #e8e8e8; -fx-opacity: 0.9;");
        else setStyle("");
        for (var node : rowsBox.getChildren()) {
            if (node instanceof Row r) r.applyReadOnly(readOnly);
        }
        rebuildAnnotations();
    }

    private void rebuild() {
        rowsBox.getChildren().clear();
        if (multiText == null) { refreshAddLangChoices(); rebuildAnnotations(); return; }

        for (String l : sorted(multiText.getLangs())) {
            if (!allLanguages.contains(l)) allLanguages.add(l);
        }
        allLanguages.sort(Comparator.naturalOrder());

        List<String> langs = fixedLanguageRows ? new ArrayList<>(allLanguages) : sorted(multiText.getLangs());
        for (String lang : langs) {
            Row row = new Row(lang, fixedLanguageRows);
            rowsBox.getChildren().add(row);
            if (readOnly) row.applyReadOnly(true);
        }
        refreshAddLangChoices();
        rebuildAnnotations();
    }

    private void addRowForLang(String lang) {
        if (multiText == null) return;
        String l = safeTrim(lang);
        if (l.isBlank()) return;
        if (!allLanguages.contains(l)) {
            allLanguages.add(l);
            allLanguages.sort(Comparator.naturalOrder());
        }
        rowsBox.getChildren().add(new Row(l, fixedLanguageRows));
    }

    private void refreshAddLangChoices() {
        if (fixedLanguageRows) {
            addLangCombo.setItems(FXCollections.observableArrayList());
            addLangCombo.setDisable(true);
            return;
        }
        Set<String> used = getUsedLanguages();
        List<String> available = new ArrayList<>();
        for (String l : allLanguages) {
            if (!used.contains(l)) available.add(l);
        }
        addLangCombo.setItems(FXCollections.observableArrayList(available));
        addLangCombo.setDisable(available.isEmpty());
    }

    private void rebuildAnnotations() {
        annotationsBox.getChildren().clear();
        List<LiftAnnotation> annotations = getAnnotationsSafe();
        boolean editable = onAddAnnotation != null && multiText != null && !readOnly;

        if (editable) {
            Button addBtn = new Button(I18n.get("btn.addAnnotation"));
            addBtn.getStyleClass().add("example-add-button");
            addBtn.setOnAction(e -> {
                String name;
                if (knownAnnotationNames.isEmpty()) {
                    TextInputDialog dlg = new TextInputDialog("");
                    dlg.setTitle(I18n.get("btn.addAnnotation"));
                    dlg.setHeaderText(I18n.get("col.type"));
                    name = dlg.showAndWait().orElse(null);
                } else {
                    ChoiceDialog<String> dlg = new ChoiceDialog<>(
                        knownAnnotationNames.get(0), knownAnnotationNames);
                    dlg.setTitle(I18n.get("btn.addAnnotation"));
                    dlg.setHeaderText(I18n.get("col.type"));
                    name = dlg.showAndWait().orElse(null);
                }
                if (name != null && !name.trim().isBlank()) {
                    onAddAnnotation.accept(name.trim(), multiText);
                    rebuildAnnotations();
                }
            });
            annotationsBox.getChildren().add(addBtn);
        }

        if (annotations.isEmpty()) {
            if (!editable) annotationsBox.getChildren().add(new Label(I18n.get("editor.none")));
            return;
        }

        MultiText owner = multiText;
        int i = 1;
        for (LiftAnnotation a : annotations) {
            AnnotationEditor ae = new AnnotationEditor(dictionary);
            ae.setAnnotation(a);
            String type = EditorSupport.featureId(a.getType());
            String title = type.isBlank() ? "#" + i : "#" + i + " - " + type;
            Runnable onDelete = editable
                ? () -> ComponentDeletion.delete(
                      () -> owner.deleteAnnotation(a),
                      this::rebuildAnnotations)
                : null;
            TitledPane tp = new TitledPane(title, EditorSupport.withDeleteButton(ae, onDelete));
            tp.setExpanded(false);
            tp.setAnimated(false);
            annotationsBox.getChildren().add(tp);
            i++;
        }
    }

    private List<LiftAnnotation> getAnnotationsSafe() {
        if (multiText == null) return List.of();
        List<LiftAnnotation> annotations = multiText.getAnnotations();
        if (annotations == null || annotations.isEmpty()) return List.of();
        return annotations.stream().filter(Objects::nonNull).toList();
    }

    private Set<String> getUsedLanguages() {
        Set<String> used = new HashSet<>();
        for (var node : rowsBox.getChildren()) {
            if (node instanceof Row r && !r.boundLang.isBlank()) {
                used.add(r.boundLang);
            }
        }
        return used;
    }

    /* ─── Row: one language ─── */

    private final class Row extends HBox {
        private final Label langLabel = new Label();
        private final TextField textField = new TextField();
        private final Button removeButton = new Button("-");
        private final Button editButton = new Button(I18n.get("multitext.edit"));
        private String boundLang = "";
        private final boolean fixedSlot;

        private Row(String lang, boolean fixedSlot) {
            super(6);
            this.fixedSlot = fixedSlot;
            setPadding(new Insets(2, 0, 2, 0));
            setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            langLabel.setPrefWidth(60);
            langLabel.setMinWidth(60);
            langLabel.setStyle("-fx-font-weight: bold;");
            langLabel.setText(safeTrim(lang));

            textField.setPromptText(I18n.get("multitext.textPrompt"));
            HBox.setHgrow(textField, Priority.ALWAYS);

            removeButton.setPrefWidth(28);
            removeButton.setTooltip(new Tooltip(I18n.get("multitext.removeLanguage")));
            removeButton.getStyleClass().add("page-button");
            removeButton.setOnAction(e -> removeRow());

            editButton.setTooltip(new Tooltip(I18n.get("multitext.editTooltip")));
            editButton.getStyleClass().add("page-button");
            editButton.setOnAction(e -> openEditDialog());

            if (fixedSlot) {
                removeButton.setVisible(false);
                removeButton.setManaged(false);
            }

            getChildren().addAll(langLabel, textField, removeButton, editButton);
            bindToLang(safeTrim(lang));
        }

        void applyReadOnly(boolean ro) {
            textField.setEditable(!ro);
            textField.setStyle(ro ? "-fx-background-color: #e0e0e0; -fx-text-fill: #555;" : "");
            removeButton.setVisible(!ro && !fixedSlot);
            removeButton.setManaged(!ro && !fixedSlot);
            editButton.setVisible(!ro);
            editButton.setManaged(!ro);
        }

        private void removeRow() {
            if (fixedSlot) return;
            if (multiText != null && !boundLang.isBlank()) {
                // An empty text removes the form from the multitext.
                multiText.formTextProperty(boundLang).set("");
            }
            unbind();
            rowsBox.getChildren().remove(this);
            refreshAddLangChoices();
        }

        private void openEditDialog() {
            TextInputDialog dlg = new TextInputDialog(textField.getText());
            dlg.setTitle(I18n.get("multitext.editTitle", boundLang));
            dlg.setHeaderText(I18n.get("multitext.editHeader", boundLang));
            dlg.getEditor().setPrefColumnCount(40);
            dlg.showAndWait()
                .filter(text -> !text.equals(textField.getText()))
                .ifPresent(text -> {
                    textField.setText(text);
                });
        }

        private void bindToLang(String lang) {
            if (multiText == null) return;
            String l = safeTrim(lang);
            boundLang = l;
            if (l.isBlank()) return;
            textField.textProperty().bindBidirectional(multiText.formTextProperty(l));
        }

        private void unbind() {
            if (multiText == null) return;
            if (!boundLang.isBlank()) {
                textField.textProperty().unbindBidirectional(multiText.formTextProperty(boundLang));
            }
            boundLang = "";
        }
    }

    /* ─── Utilities ─── */

    private static List<String> normalizeLangs(Collection<String> langs) {
        if (langs == null) return List.of();
        Set<String> set = new LinkedHashSet<>();
        for (String l : langs) {
            String t = safeTrim(l);
            if (!t.isBlank()) set.add(t);
        }
        List<String> out = new ArrayList<>(set);
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static List<String> sorted(Collection<String> langs) {
        if (langs == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : langs) {
            String t = safeTrim(s);
            if (!t.isBlank()) out.add(t);
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
