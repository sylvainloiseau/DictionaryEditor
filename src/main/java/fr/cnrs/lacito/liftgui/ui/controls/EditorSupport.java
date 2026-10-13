package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.model.AbstractLiftRoot;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinition;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinitionTarget;
import fr.cnrs.lacito.liftapi.model.LiftHeader;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Small helpers shared by the editors of this package.
 */
public final class EditorSupport {

    private static final String READ_ONLY_STYLE = "-fx-text-fill: #666; -fx-background-color: #f0f0f0;";
    private static final String WARNING_STYLE = "-fx-border-color: orange; -fx-border-width: 2; -fx-border-radius: 4;";

    private EditorSupport() {}

    /** The features of a feature set, sorted by id. */
    public static List<Feature> sortedFeatures(FeatureSet set) {
        if (set == null) return List.of();
        return set.getFeatures().values().stream()
            .sorted(Comparator.comparing(Feature::getId))
            .toList();
    }

    /** The ids of the features of a feature set, sorted. */
    public static List<String> sortedFeatureIds(FeatureSet set) {
        return sortedFeatures(set).stream().map(Feature::getId).toList();
    }

    /** Displays features by their id (instead of {@code Feature.toString()}). */
    public static void showFeatureIds(ComboBox<Feature> combo) {
        StringConverter<Feature> converter = new StringConverter<>() {
            @Override
            public String toString(Feature f) {
                return f == null ? "" : f.getId();
            }

            @Override
            public Feature fromString(String s) {
                return null;
            }
        };
        combo.setConverter(converter);
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Feature item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getId());
            }
        });
    }

    /**
     * The field/trait definition target matching a component, or null when the component
     * cannot carry fields or traits according to LIFT (e.g. a field carrying a trait).
     */
    public static LiftFieldAndTraitDefinitionTarget targetOf(Object model) {
        if (!(model instanceof AbstractLiftRoot root)) return null;
        try {
            return LiftFieldAndTraitDefinitionTarget.fromType(root);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Trait definitions applicable to {@code target}; all of them when the target is null. */
    public static List<LiftFieldAndTraitDefinition> traitDefinitions(LiftHeader header, LiftFieldAndTraitDefinitionTarget target) {
        return target == null ? header.getTraitsDefinitions() : header.getTraitsDefinitionsFor(target);
    }

    /** Field definitions applicable to {@code target}; all of them when the target is null. */
    public static List<LiftFieldAndTraitDefinition> fieldDefinitions(LiftHeader header, LiftFieldAndTraitDefinitionTarget target) {
        return target == null ? header.getFieldsDefinitions() : header.getFieldsDefinitionsFor(target);
    }

    /** The id of a feature, or "" when there is none. */
    public static String featureId(Feature f) {
        return f == null ? "" : f.getId();
    }

    /** The first form of a multitext, or "". */
    public static String firstForm(MultiText mt) {
        if (mt == null) return "";
        return mt.getForms().stream().findFirst().map(Form::toPlainText).orElse("");
    }

    /**
     * The features of a set as a tree following their superordinate feature; the root (with
     * a null value) holds the features without a superordinate. Every item is expanded.
     */
    public static TreeItem<Feature> featureTree(FeatureSet set) {
        TreeItem<Feature> root = new TreeItem<>(null);
        root.setExpanded(true);
        Map<String, TreeItem<Feature>> items = new LinkedHashMap<>();
        for (Feature f : set.getFeatures().values()) {
            TreeItem<Feature> item = new TreeItem<>(f);
            item.setExpanded(true);
            items.put(f.getId(), item);
        }
        for (Feature f : set.getFeatures().values()) {
            Feature sup = f.getSuperOrdinateFeature().orElse(null);
            TreeItem<Feature> parent = sup != null ? items.get(sup.getId()) : null;
            (parent != null ? parent : root).getChildren().add(items.get(f.getId()));
        }
        return root;
    }

    /** Plain text of all the forms of a multitext, joined by " / ". */
    public static String plainText(MultiText mt) {
        if (mt == null) return "";
        return mt.getForms().stream()
            .map(Form::toPlainText)
            .filter(t -> t != null && !t.isBlank())
            .collect(Collectors.joining(" / "));
    }

    public static TextField readOnlyField(String value) {
        TextField tf = new TextField(value == null ? "" : value);
        makeReadOnly(tf);
        return tf;
    }

    public static void makeReadOnly(TextField tf) {
        tf.setEditable(false);
        tf.setFocusTraversable(false);
        tf.setStyle(READ_ONLY_STYLE);
    }

    /** Orange border and tooltip when {@code documented} is false. */
    public static void flagUndocumented(TextField tf, boolean documented, String tooltipKey) {
        if (documented) {
            tf.setStyle(READ_ONLY_STYLE);
            tf.setTooltip(null);
        } else {
            tf.setStyle(READ_ONLY_STYLE + WARNING_STYLE);
            Tooltip tip = new Tooltip(I18n.get(tooltipKey));
            tip.setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #856404;");
            tf.setTooltip(tip);
        }
    }

    public static void showError(String title, String message) {
        Dialogs.error(title, message);
    }

    /**
     * Wraps an item editor with a small delete button above it. When {@code onDelete} is
     * null, the editor is returned unchanged.
     */
    public static Node withDeleteButton(Node editor, Runnable onDelete) {
        if (onDelete == null) return editor;
        Button del = new Button(I18n.get("btn.deleteItem"));
        del.getStyleClass().add("delete-item-button");
        del.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px; -fx-padding: 2 8 2 8;");
        del.setOnAction(e -> onDelete.run());
        HBox bar = new HBox(del);
        bar.setAlignment(Pos.CENTER_RIGHT);
        return new VBox(2, bar, editor);
    }
}
