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
import fr.cnrs.lacito.liftgui.ui.I18n;
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
 * The trait name is read-only (a trait cannot be renamed). The value widget adapts to the
 * {@link LiftFieldAndTraitDefinitionDataModel} of the trait's {@link LiftFieldAndTraitDefinition}:
 * <ul>
 *   <li>{@code datetime} → {@link DatePicker}</li>
 *   <li>{@code integer} → integer-only {@link TextField}</li>
 *   <li>{@code option / option-collection / option-sequence} → tree/list picker
 *       from the resolved {@link FeatureSet}; the value stored is the feature id</li>
 *   <li>otherwise → editable {@link ComboBox} offering the values already used for this trait name</li>
 * </ul>
 */
public final class TraitEditor extends VBox {

    private final TextField nameField = EditorSupport.readOnlyField("");
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

        nameField.setMaxWidth(Double.MAX_VALUE);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(60);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setMinWidth(180);
        valueCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, valueCol);

        grid.add(new Label(I18n.get("col.name")), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label(I18n.get("col.value")), 0, 1);
        grid.add(valueBox, 1, 1);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(valueBox, Priority.ALWAYS);

        TitledPane annoPane = new TitledPane(I18n.get("editor.annotations"), annotationsBox);
        annoPane.setExpanded(false);
        annoPane.setAnimated(false);

        getChildren().addAll(grid, annoPane);
    }

    /**
     * @param t      the trait to edit
     * @param target the kind of object owning the trait, used to check that the trait name
     *               is documented in the header for that kind of object; null to accept any
     *               trait definition
     */
    public void setTrait(LiftTrait t, LiftFieldAndTraitDefinitionTarget target) {
        this.trait = t;
        annotationsBox.getChildren().clear();
        if (t == null) {
            nameField.setText("");
            valueBox.getChildren().clear();
            return;
        }

        String name = t.getSpecification().getName();
        Set<String> documentedNames = new HashSet<>();
        (target == null
            ? dictionary.getHeader().getFieldsAndTraitsDefinitions()
            : dictionary.getHeader().getFieldsAndTraitsDefinitionsFor(target))
            .forEach(d -> documentedNames.add(d.getName()));
        nameField.setText(name);
        EditorSupport.flagUndocumented(nameField, documentedNames.contains(name), "tooltip.undocumentedValue");

        valueBox.getChildren().setAll(buildValueWidget(t));

        for (LiftAnnotation a : t.getAnnotations()) {
            AnnotationEditor ae = new AnnotationEditor(dictionary);
            ae.setAnnotation(a);
            annotationsBox.getChildren().add(ae);
        }
    }

    private Node buildValueWidget(LiftTrait t) {
        Optional<LiftFieldAndTraitDefinitionDataModel> typeOpt = t.getSpecification().getDataModel();
        if (typeOpt.isPresent()) {
            return switch (typeOpt.get()) {
                case DATETIME -> buildDatePicker(t);
                case INTEGER -> buildIntegerField(t);
                case FEATURE, FEATURE_SET, FEATURE_LIST -> {
                    Optional<FeatureSet> rangeOpt = t.getSpecification().getResolvedFeatureSet();
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
            if (t.getValue() != null && t.getValue().length() >= 10) dp.setValue(
                LocalDate.parse(t.getValue().substring(0, 10))
            );
        } catch (DateTimeParseException ignored) {}
        dp.valueProperty().addListener((obs, o, n) -> t.setValue(n == null ? "" : n.toString()));
        return dp;
    }

    private TextField buildIntegerField(LiftTrait t) {
        TextField tf = new TextField(t.getValue());
        tf.setMaxWidth(Double.MAX_VALUE);
        tf.textProperty().addListener((obs, o, n) -> {
            if (n.matches("-?\\d*")) t.setValue(n);
            else tf.setText(o);
        });
        return tf;
    }

    /**
     * For option/option-collection/option-sequence: show a tree popup where
     * range-elements are organised by their @parent attribute.
     * The selected feature id(s) are shown in a read-only label; clicking opens the picker.
     */
    private Node buildRangePicker(
        LiftTrait t,
        FeatureSet range,
        LiftFieldAndTraitDefinitionDataModel type
    ) {
        boolean multiSelect =
            type == LiftFieldAndTraitDefinitionDataModel.FEATURE_SET ||
            type == LiftFieldAndTraitDefinitionDataModel.FEATURE_LIST;
        Label displayLabel = new Label(t.getValue());
        displayLabel.setMaxWidth(Double.MAX_VALUE);
        displayLabel.setStyle(
            "-fx-border-color: #aab; -fx-padding: 3 6 3 6; -fx-background-color: white; -fx-background-radius: 3;"
        );
        Button pickBtn = new Button("…");
        pickBtn.setOnAction(e -> {
            String chosen = showRangePickerDialog(range, t.getValue(), multiSelect);
            if (chosen != null) {
                t.setValue(chosen);
                displayLabel.setText(chosen);
            }
        });
        VBox box = new VBox(4, displayLabel, pickBtn);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** Editable combo offering the values already used in the dictionary for this trait name. */
    private ComboBox<String> buildDefaultCombo(LiftTrait t) {
        ComboBox<String> combo = new ComboBox<>();
        combo.setEditable(true);
        combo.setMaxWidth(Double.MAX_VALUE);
        Map<String, Long> known = dictionary.getValueCounterForTraitName(t.getSpecification().getName());
        if (known != null) {
            combo.setItems(FXCollections.observableArrayList(new TreeSet<>(known.keySet())));
        }
        combo.setValue(t.getValue());
        combo.getEditor().textProperty().addListener((obs, o, n) -> t.setValue(n == null ? "" : n));
        return combo;
    }

    private String showRangePickerDialog(
        FeatureSet range,
        String currentValue,
        boolean multiSelect
    ) {
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle(range.getId());
        dlg.setHeaderText(I18n.get("dialog.selectValue"));
        dlg.getDialogPane()
            .getButtonTypes()
            .addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefSize(360, 400);

        TreeItem<Feature> root = new TreeItem<>(null);
        root.setExpanded(true);
        Map<String, TreeItem<Feature>> itemMap =
            new LinkedHashMap<>();

        for (Feature re : range.getFeatures().values()) {
            TreeItem<Feature> item = new TreeItem<>(re);
            item.setExpanded(true);
            itemMap.put(re.getId(), item);
        }
        for (Feature re : range.getFeatures().values()) {
            TreeItem<Feature> item = itemMap.get(re.getId());
            Feature pid = re.getSuperOrdinateFeature().orElse(null);
            if (pid != null && itemMap.containsKey(pid.getId())) itemMap
                .get(pid.getId())
                .getChildren()
                .add(item);
            else root.getChildren().add(item);
        }

        TreeView<Feature> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.getSelectionModel().setSelectionMode(
            multiSelect ? SelectionMode.MULTIPLE : SelectionMode.SINGLE
        );
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
        for (TreeItem<Feature> ti : itemMap.values()) {
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
            // The trait value refers to range elements by their id.
            StringJoiner sj = new StringJoiner(" ");
            for (var ti : selected) {
                if (ti.getValue() != null) sj.add(ti.getValue().getId());
            }
            return sj.toString();
        });

        return dlg.showAndWait().orElse(null);
    }
}
