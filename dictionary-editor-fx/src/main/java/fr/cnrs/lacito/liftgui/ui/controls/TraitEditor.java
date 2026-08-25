/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Editor for a single {@link LiftTrait}.
 * <p>
 * The value widget adapts to the {@link LiftFieldAndTraitDefinitionDataModel} of the associated
 * {@link LiftFieldAndTraitDefinition}:
 * <ul>
 *   <li>{@code datetime} → {@link DatePicker}</li>
 *   <li>{@code integer} → integer-only {@link TextField}</li>
 *   <li>{@code option / option-collection / option-sequence} → tree/list picker
 *       from the resolved {@link LiftHeaderRange}</li>
 *   <li>otherwise → editable {@link ComboBox} (default)</li>
 * </ul>
 */
public final class TraitEditor extends VBox {

    private final ComboBox<String> nameCombo = new ComboBox<>();
    private final VBox valueBox = new VBox(4);
    private final VBox annotationsBox = new VBox(6);

    private LiftTrait trait;
    private final LiftDictionary dictionary;

    public TraitEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        setPadding(new Insets(4));
        setStyle(
            "-fx-border-color: #cde; -fx-border-radius: 4; -fx-background-color: #f5f8fc; -fx-background-radius: 4;"
        );

        nameCombo.setEditable(false);
        nameCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            validateTraitName(newVal);
        });
        nameCombo.setMaxWidth(Double.MAX_VALUE);
        nameCombo.setPromptText("nom du trait");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(60);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setMinWidth(180);
        valueCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, valueCol);
        grid.add(new Label("Nom"), 0, 0);
        grid.add(nameCombo, 1, 0);
        grid.add(new Label("Valeur"), 0, 1);
        grid.add(valueBox, 1, 1);
        GridPane.setHgrow(nameCombo, Priority.ALWAYS);
        GridPane.setHgrow(valueBox, Priority.ALWAYS);

        TitledPane annoPane = new TitledPane("Annotations", annotationsBox);
        annoPane.setExpanded(false);
        annoPane.setAnimated(false);

        getChildren().addAll(grid, annoPane);
    }

    private List<String> knownTraitNames = new ArrayList<>();

    private void validateTraitName(String name) {
        if (name == null || name.isBlank()) {
            nameCombo.setStyle("-fx-border-color: #cde; -fx-border-radius: 4;");
            nameCombo.setTooltip(null);
            return;
        }
        if (!knownTraitNames.contains(name)) {
            nameCombo.setStyle(
                "-fx-border-color: orange; -fx-border-width: 2; -fx-border-radius: 4;"
            );
            Tooltip tip = new Tooltip(
                "⚠ Valeur non documentée dans la configuration du dictionnaire"
            );
            tip.setStyle(
                "-fx-background-color: #fff3cd; -fx-text-fill: #856404;"
            );
            nameCombo.setTooltip(tip);
        } else {
            nameCombo.setStyle("-fx-border-color: #cde; -fx-border-radius: 4;");
            nameCombo.setTooltip(null);
        }
    }

    /**
     * @param t               the trait to edit
     * @param availableLangs  languages for annotation sub-editors
     * @param traitNames      all known trait names in the dictionary
     * @param valuesForName   known values for each trait name (fallback when no definition)
     * @param definition      optional field/trait definition carrying @type and resolved range
     */
    public void setTrait(
        LiftTrait t,
        LiftFieldAndTraitDefinitionTarget target
        //Collection<String> traitNames,
        // Map<String, Set<String>> valuesForName
        // Optional<LiftFieldAndTraitDefinition> definition
    ) {
        this.trait = t;
        Collection<String> availableLangs = dictionary.getObjectLanguageManager().getLanguages();
        Collection<String> traitNames = dictionary.getHeader().getFieldsAndTraitsDefinitionsFor(target).stream().map(Object::toString).toList();
        LiftFieldAndTraitDefinition definition = t.getDefinition();

        if (trait == null) {
            nameCombo.getItems().clear();
            valueBox.getChildren().clear();
            annotationsBox.getChildren().clear();
            return;
        }

        nameCombo.setItems(
            FXCollections.observableArrayList(
                traitNames instanceof List
                    ? (List<String>) traitNames
                    : new ArrayList<>(traitNames)
            )
        );
        nameCombo.setValue(t.getDefinition().getName());
        this.knownTraitNames = new ArrayList<>(traitNames);
        validateTraitName(t.getDefinition().getName());
        valueBox
            .getChildren()
            .setAll(buildValueWidget(t));

        annotationsBox.getChildren().clear();
        List<LiftAnnotation> annos = t.getAnnotations();
        if (annos != null) {
            for (LiftAnnotation a : annos) {
                AnnotationEditor ae = new AnnotationEditor(dictionary);
                ae.setAnnotation(a, availableLangs, Set.of());
                annotationsBox.getChildren().add(ae);
            }
        }
    }

    private Node buildValueWidget(
        LiftTrait t
    ) {
            Optional<LiftFieldAndTraitDefinitionDataModel> typeOpt =
                t.getDefinition().getDataModel();
            if (typeOpt.isPresent()) {
                return switch (typeOpt.get()) {
                    case DATETIME -> buildDatePicker(t);
                    case INTEGER -> buildIntegerField(t);
                    case OPTION, OPTION_COLLECTION, OPTION_SEQUENCE -> {
                        Optional<LiftHeaderRange> rangeOpt =
                            t.getDefinition().getResolvedRange();
                        yield rangeOpt.isPresent()
                            ? buildRangePicker(t, rangeOpt.get(), typeOpt.get())
                            : buildDefaultCombo(t);
                    }
                    default -> buildDefaultCombo(t);
                };
            }
        return buildDefaultCombo(t);
    }

    private DatePicker buildDatePicker(LiftTrait t) {
        DatePicker dp = new DatePicker();
        dp.setMaxWidth(Double.MAX_VALUE);
        try {
            if (t.getValue() != null && !t.getValue().isBlank()) dp.setValue(
                LocalDate.parse(t.getValue().substring(0, 10))
            );
        } catch (DateTimeParseException ignored) {}
        dp.valueProperty().addListener((obs, o, n) -> {
            if (n != null) t.valueProperty().set(n.toString());
        });
        return dp;
    }

    private TextField buildIntegerField(LiftTrait t) {
        TextField tf = new TextField(t.getValue());
        tf.setMaxWidth(Double.MAX_VALUE);
        tf.textProperty().addListener((obs, o, n) -> {
            if (n.matches("-?\\d*")) t.valueProperty().set(n);
            else tf.setText(o);
        });
        return tf;
    }

    /**
     * For option/option-collection/option-sequence: show a tree popup where
     * range-elements are organised by their @parent attribute.
     * The selected abbreviation(s) are shown in a read-only label; clicking opens the picker.
     */
    private Node buildRangePicker(
        LiftTrait t,
        LiftHeaderRange range,
        LiftFieldAndTraitDefinitionDataModel type
    ) {
        boolean multiSelect =
            type == LiftFieldAndTraitDefinitionDataModel.OPTION_COLLECTION ||
            type == LiftFieldAndTraitDefinitionDataModel.OPTION_SEQUENCE;

        Label displayLabel = new Label(t.getValue());
        displayLabel.setMaxWidth(Double.MAX_VALUE);
        displayLabel.setStyle(
            "-fx-border-color: #aab; -fx-padding: 3 6 3 6; -fx-background-color: white; -fx-background-radius: 3;"
        );

        Button pickBtn = new Button("…");
        pickBtn.setOnAction(e -> {
            String chosen = showRangePickerDialog(
                range,
                t.getValue(),
                multiSelect
            );
            if (chosen != null) {
                t.valueProperty().set(chosen);
                displayLabel.setText(chosen);
            }
        });

        VBox box = new VBox(4, displayLabel, pickBtn);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private ComboBox<String> buildDefaultCombo(
        LiftTrait t
    ) {
        ComboBox<String> combo = new ComboBox<>();
        combo.setEditable(true);
        combo.setMaxWidth(Double.MAX_VALUE);
        Set<String> knownValues = t.getDefinition().getResolvedRange().get().getRangeElements().keySet();
        combo.setItems(
            FXCollections.observableArrayList(new TreeSet<>(knownValues))
        );
        combo.setValue(t.getValue());
        combo.getEditor().textProperty().bindBidirectional(t.valueProperty());

        // ✅ Validation de la valeur saisie
        combo
            .getEditor()
            .textProperty()
            .addListener((obs, oldVal, newVal) -> {
                if (
                    newVal == null ||
                    newVal.isBlank() ||
                    knownValues.contains(newVal)
                ) {
                    combo.setStyle(null);
                    combo.setTooltip(null);
                } else {
                    combo.setStyle(
                        "-fx-border-color: orange; -fx-border-width: 2; -fx-border-radius: 4;"
                    );
                    Tooltip tip = new Tooltip(
                        "⚠ Valeur non documentée dans la configuration du dictionnaire"
                    );
                    tip.setStyle(
                        "-fx-background-color: #fff3cd; -fx-text-fill: #856404;"
                    );
                    combo.setTooltip(tip);
                }
            });

        // Validation initiale
        if (
            !knownValues.isEmpty() &&
            t.getValue() != null &&
            !knownValues.contains(t.getValue())
        ) {
            combo.setStyle(
                "-fx-border-color: orange; -fx-border-width: 2; -fx-border-radius: 4;"
            );
            Tooltip tip = new Tooltip(
                "⚠ Valeur non documentée dans la configuration du dictionnaire"
            );
            tip.setStyle(
                "-fx-background-color: #fff3cd; -fx-text-fill: #856404;"
            );
            combo.setTooltip(tip);
        }

        return combo;
    }

    /**
     * Shows a Dialog with a TreeView of range-elements (using @parent hierarchy).
     * Returns the selected element's abbreviation, or null if cancelled.
     */
    private String showRangePickerDialog(
        LiftHeaderRange range,
        String currentValue,
        boolean multiSelect
    ) {
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle(range.getId());
        dlg.setHeaderText("Sélectionner une valeur");
        dlg.getDialogPane()
            .getButtonTypes()
            .addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefSize(360, 400);

        TreeItem<LiftHeaderRangeElement> root = new TreeItem<>(null);
        root.setExpanded(true);
        Map<String, TreeItem<LiftHeaderRangeElement>> itemMap =
            new LinkedHashMap<>();

        for (LiftHeaderRangeElement re : range.getRangeElements().values()) {
            TreeItem<LiftHeaderRangeElement> item = new TreeItem<>(re);
            item.setExpanded(true);
            itemMap.put(re.getId(), item);
        }
        for (LiftHeaderRangeElement re : range.getRangeElements().values()) {
            TreeItem<LiftHeaderRangeElement> item = itemMap.get(re.getId());
            LiftHeaderRangeElement pid = re.getParentElement().orElse(null);
            if (pid != null && itemMap.containsKey(pid.getId())) itemMap
                .get(pid.getId())
                .getChildren()
                .add(item);
            else root.getChildren().add(item);
        }

        TreeView<LiftHeaderRangeElement> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.getSelectionModel().setSelectionMode(
            multiSelect ? SelectionMode.MULTIPLE : SelectionMode.SINGLE
        );
        tree.setCellFactory(tv ->
            new TreeCell<>() {
                @Override
                protected void updateItem(
                    LiftHeaderRangeElement item,
                    boolean empty
                ) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        return;
                    }
                    String abbrev = item
                        .getAbbrev()
                        .getForms()
                        .stream()
                        .findFirst()
                        .map(f -> f.toPlainText())
                        .orElse("");
                    String label = item
                        .getLabel()
                        .getForms()
                        .stream()
                        .findFirst()
                        .map(f -> f.toPlainText())
                        .orElse(item.getId());
                    setText(abbrev.isBlank() ? label : abbrev + " – " + label);
                }
            }
        );

        // Pre-select current value
        String[] current =
            currentValue == null ? new String[0] : currentValue.split("\\s+");
        Set<String> currentSet = new HashSet<>(Arrays.asList(current));
        for (TreeItem<LiftHeaderRangeElement> ti : itemMap.values()) {
            if (ti.getValue() != null) {
                String abbrev = ti
                    .getValue()
                    .getAbbrev()
                    .getForms()
                    .stream()
                    .findFirst()
                    .map(f -> f.toPlainText())
                    .orElse(ti.getValue().getId());
                if (
                    currentSet.contains(abbrev) ||
                    currentSet.contains(ti.getValue().getId())
                ) tree.getSelectionModel().select(ti);
            }
        }

        dlg.getDialogPane().setContent(
            new ScrollPane(tree) {
                {
                    setFitToWidth(true);
                    setFitToHeight(true);
                }
            }
        );

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            var selected = tree.getSelectionModel().getSelectedItems();
            if (selected.isEmpty()) return null;
            StringJoiner sj = new StringJoiner(" ");
            for (var ti : selected) {
                if (ti.getValue() != null) {
                    String abbrev = ti
                        .getValue()
                        .getAbbrev()
                        .getForms()
                        .stream()
                        .findFirst()
                        .map(f -> f.toPlainText())
                        .orElse(ti.getValue().getId());
                    sj.add(abbrev.isBlank() ? ti.getValue().getId() : abbrev);
                }
            }
            return sj.toString();
        });

        return dlg.showAndWait().orElse(null);
    }
}
