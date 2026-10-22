package fr.cnrs.lacito.liftgui.ui.view.header;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftHeader;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.controls.EditorSupport;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.view.DictionaryView;
import javafx.geometry.Insets;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * A range of the header: its label, abbreviation and description, and the tree of its
 * elements, which can be added, renamed (trait values follow) and deleted when unused.
 *
 * A range defined in a shared {@code .lift-ranges} file is shown read-only, with a button
 * that makes it editable in this dictionary; the elements the {@code .lift} file itself
 * declares (overriding the shared file) are marked.
 */
public final class RangeView implements DictionaryView {

    private final EditorContext ctx;
    private final String rangeId;
    private final Node node;

    public RangeView(EditorContext ctx, String rangeId) {
        this.ctx = ctx;
        this.rangeId = rangeId;
        LiftDictionary d = ctx.dictionary();
        LiftHeader header = d.getHeader();
        if (!header.hasFeatureSet(rangeId)) {
            node = new Label(I18n.get("cfg.noHeader"));
            return;
        }
        FeatureSet range = header.getFeatureSet(rangeId);
        boolean readOnly = range.isExternallyDefined();

        VBox props = new VBox(8);
        props.setPadding(new Insets(6));
        HeaderViews.addMultiTextRow(props, d, I18n.get("cfg.description"), range.getDescription(), readOnly);
        HeaderViews.addMultiTextRow(props, d, I18n.get("cfg.label"), range.getLabel(), readOnly);
        HeaderViews.addMultiTextRow(props, d, I18n.get("cfg.abbrev"), range.getAbbrev(), readOnly);
        TitledPane propsPane = new TitledPane(I18n.get("cfg.rangeProperties"), props);
        propsPane.setExpanded(true);
        propsPane.setAnimated(false);

        TreeView<Feature> tree = elementTree(range);
        tree.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null && n.getValue() != null) showElement(range, n.getValue());
        });

        TextField newIdField = new TextField();
        newIdField.setPromptText(I18n.get("cfg.newElementLabel"));
        ComboBox<String> parentCombo = new ComboBox<>();
        parentCombo.setPromptText(I18n.get("cfg.parentElement"));
        parentCombo.getItems().add("");
        parentCombo.getItems().addAll(range.getFeatures().keySet());
        Button addBtn = new Button(I18n.get("cfg.addElement"));
        addBtn.setOnAction(e -> {
            String newId = newIdField.getText().trim();
            if (newId.isEmpty() || range.hasFeature(newId)) return;
            Feature newElem = range.addFeature(newId);
            String parentSel = parentCombo.getValue();
            if (parentSel != null && !parentSel.isBlank()) newElem.setSuperordinateFeature(range.getFeature(parentSel));
            d.getMetaLanguageManager().getLanguages().stream().findFirst()
                .ifPresent(lang -> newElem.getDescription().add(new Form(lang, I18n.get("cfg.autoAdded"))));
            reload();
        });

        Button deleteBtn = new Button(I18n.get(Keys.BTN_DELETE));
        deleteBtn.setOnAction(e -> {
            TreeItem<Feature> sel = tree.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getValue() == null) return;
            if (HeaderViews.deleteFeature(ctx, range, sel.getValue())) reload();
        });

        Button renameBtn = new Button(I18n.get("cfg.rename"));
        renameBtn.setOnAction(e -> {
            TreeItem<Feature> sel = tree.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getValue() == null) return;
            String oldId = sel.getValue().getId();
            TextInputDialog dlg = new TextInputDialog(oldId);
            dlg.setTitle(I18n.get("cfg.rename"));
            dlg.setHeaderText(I18n.get("cfg.renamePrompt", oldId));
            dlg.showAndWait().map(String::trim).filter(n -> !n.isEmpty()).ifPresent(newId -> {
                if (range.hasFeature(newId)) {
                    Dialogs.error(I18n.get("cfg.rename"), I18n.get("cfg.idExists", newId));
                    return;
                }
                range.changeFeatureId(sel.getValue(), newId);
                reload();
            });
        });

        Label countLbl = new Label(range.getFeatures().size() + " " + I18n.get("cfg.elements"));
        countLbl.getStyleClass().add("muted-label");
        HBox addRow = new HBox(8, newIdField, parentCombo, addBtn);
        HBox.setHgrow(newIdField, Priority.ALWAYS);
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionRow = new HBox(8, deleteBtn, renameBtn, spacer, countLbl);
        for (Node control : List.of(newIdField, parentCombo, addBtn, deleteBtn, renameBtn)) {
            control.setDisable(readOnly);
        }

        Label elemTitle = new Label(I18n.get("cfg.rangeElements2"));
        elemTitle.getStyleClass().add("page-subtitle");
        VBox box = new VBox(8);
        Node banner = HeaderViews.externalRangeBanner(ctx, range);
        if (banner != null) box.getChildren().add(banner);
        box.getChildren().addAll(propsPane, elemTitle, tree, addRow, actionRow);
        box.setPadding(new Insets(8));
        VBox.setVgrow(tree, Priority.ALWAYS);
        node = box;
        ctx.showCount(range.getFeatures().size(), range.getFeatures().size());
    }

    /** Shows the view again after a change of the elements. */
    private void reload() {
        ctx.navigator().refresh();
    }

    /** The elements of the range, by superordinate element, shown as "id [abbrev] – label". */
    private static TreeView<Feature> elementTree(FeatureSet range) {
        TreeView<Feature> tree = new TreeView<>(EditorSupport.featureTree(range));
        tree.setShowRoot(false);
        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Feature item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String abbrev = EditorSupport.firstForm(item.getAbbrev());
                String label = EditorSupport.firstForm(item.getLabel());
                String display = item.getId();
                if (!abbrev.isBlank()) display += "  [" + abbrev + "]";
                if (!label.isBlank()) display += "  – " + label;
                if (range.isExternallyDefined() && item.isDeclaredInline()) {
                    display += "  (" + I18n.get("cfg.declaredInline") + ")";
                }
                setText(display);
            }
        });
        return tree;
    }

    /** The label, abbreviation and description of an element (its id is not shown). */
    private void showElement(FeatureSet range, Feature elem) {
        String label = EditorSupport.firstForm(elem.getLabel());
        String abbrev = EditorSupport.firstForm(elem.getAbbrev());
        String title = !label.isBlank() ? label : !abbrev.isBlank() ? abbrev : I18n.get("cfg.rangeElement");
        LiftDictionary d = ctx.dictionary();
        boolean readOnly = range.isExternallyDefined();
        ctx.panel().show(title, range.getId(), null)
            .section(I18n.get("cfg.label"), HeaderViews.multiTextEditor(d, elem.getLabel(), readOnly), true)
            .section(I18n.get("cfg.abbrev"), HeaderViews.multiTextEditor(d, elem.getAbbrev(), readOnly), true)
            .section(I18n.get("cfg.description"), HeaderViews.multiTextEditor(d, elem.getDescription(), readOnly), false);
    }

    @Override
    public Node node() {
        return node;
    }

    @Override
    public String title() {
        return rangeId;
    }
}
