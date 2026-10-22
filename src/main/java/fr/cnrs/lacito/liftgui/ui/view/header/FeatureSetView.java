package fr.cnrs.lacito.liftgui.ui.view.header;

import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.controls.EditorSupport;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.view.DictionaryView;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Edits a header feature set (note types, translation types…) in place: ids can be renamed,
 * descriptions edited, features added, and features that nothing uses deleted. Every change
 * is applied to the header immediately.
 *
 * A set defined in a shared {@code .lift-ranges} file is shown read-only, with a button
 * that makes it editable in this dictionary.
 */
public final class FeatureSetView implements DictionaryView {

    /** A row: one feature of the set. */
    private record FeatureRow(Feature feature, String id, String description, long usage) {}

    private final Node node;

    public FeatureSetView(EditorContext ctx, String title, FeatureSet set) {
        ctx.panel().show(title, "", null);
        if (set == null) {
            node = new Label(I18n.get("cfg.noHeader"));
            return;
        }
        String descLang = HeaderViews.firstMetaLanguage(ctx.dictionary());
        boolean readOnly = HeaderViews.isReadOnly(set);

        TableView<FeatureRow> table = new TableView<>();
        table.setEditable(!readOnly);
        // The values the range does not declare are in grey (and cannot be edited).
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(FeatureRow row, boolean empty) {
                super.updateItem(row, empty);
                boolean undeclared = !empty && row != null && !row.feature().isDeclared();
                getStyleClass().remove(HeaderViews.UNDECLARED_STYLE);
                if (undeclared) getStyleClass().add(HeaderViews.UNDECLARED_STYLE);
                setTooltip(undeclared ? new Tooltip(I18n.get("cfg.undeclaredValue")) : null);
            }
        });
        Runnable reload = () -> {
            table.getItems().setAll(EditorSupport.sortedFeatures(set).stream()
                .map(f -> new FeatureRow(f, f.getId(),
                    f.getDescription().getForm(descLang).map(Form::toPlainText).orElse(""),
                    ctx.dictionary().getHeader().countUses(f)))
                .toList());
            ctx.showCount(table.getItems().size(), table.getItems().size());
        };

        TableColumn<FeatureRow, String> idCol = new TableColumn<>(I18n.get("col.abbreviation"));
        idCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().id()));
        idCol.setCellFactory(TextFieldTableCell.forTableColumn());
        idCol.setOnEditCommit(e -> {
            String newId = e.getNewValue() == null ? "" : e.getNewValue().trim();
            String oldId = e.getRowValue().id();
            if (!newId.isEmpty() && !newId.equals(oldId)) {
                if (set.hasFeature(newId)) {
                    Dialogs.error(title, I18n.get("cfg.idExists", newId));
                } else {
                    editValue(title, () -> set.changeFeatureId(e.getRowValue().feature(), newId));
                }
            }
            reload.run();
        });
        idCol.setPrefWidth(180);

        TableColumn<FeatureRow, String> usageCol = new TableColumn<>(I18n.get("cfg.usageCount"));
        usageCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(String.valueOf(cd.getValue().usage())));
        usageCol.setEditable(false);
        usageCol.setPrefWidth(80);

        TableColumn<FeatureRow, String> descCol = new TableColumn<>(I18n.get("col.description") + " [" + descLang + "]");
        descCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().description()));
        descCol.setCellFactory(TextFieldTableCell.forTableColumn());
        descCol.setOnEditCommit(e -> {
            // Through the model rather than formTextProperty, whose listener would swallow a refusal.
            editValue(title, () -> setText(e.getRowValue().feature().getDescription(), descLang, e.getNewValue()));
            reload.run();
        });
        descCol.setPrefWidth(320);

        table.getColumns().addAll(List.of(idCol, usageCol, descCol));
        if (readOnly) {
            // Which values the .lift file itself declares, overriding the shared file.
            TableColumn<FeatureRow, String> inlineCol = new TableColumn<>();
            inlineCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().feature().isDeclaredInline() ? I18n.get("cfg.declaredInline") : ""));
            inlineCol.setPrefWidth(140);
            table.getColumns().add(inlineCol);
        }
        reload.run();

        TextField addIdField = new TextField();
        addIdField.setPromptText(I18n.get("config.addAbbr"));
        TextField addDescField = new TextField();
        addDescField.setPromptText(I18n.get("config.addDesc"));
        Button addBtn = new Button(I18n.get("btn.add"));
        addBtn.setOnAction(e -> {
            String id = addIdField.getText().trim();
            if (id.isEmpty()) return;
            if (set.hasFeature(id)) {
                Dialogs.error(title, I18n.get("cfg.idExists", id));
                return;
            }
            Feature f = set.addFeature(id);
            String desc = addDescField.getText().trim();
            if (!desc.isEmpty()) f.getDescription().formTextProperty(descLang).set(desc);
            addIdField.clear();
            addDescField.clear();
            reload.run();
        });
        Button removeBtn = new Button(I18n.get("btn.delete"));
        removeBtn.setOnAction(e -> {
            FeatureRow sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (HeaderViews.deleteFeature(ctx, set, sel.feature())) reload.run();
        });

        for (Node control : List.of(addIdField, addDescField, addBtn, removeBtn)) {
            control.setDisable(readOnly);
        }
        Button declareBtn = new Button(I18n.get("cfg.declareValue"));
        declareBtn.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) ->
            declareBtn.setDisable(n == null || n.feature().isDeclared()));
        declareBtn.setOnAction(e -> {
            FeatureRow sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) HeaderViews.declareValue(ctx, sel.feature());
        });
        HBox controls = new HBox(8, addIdField, addDescField, addBtn, removeBtn, declareBtn);
        controls.setPadding(new Insets(6, 0, 0, 0));
        HBox.setHgrow(addIdField, Priority.ALWAYS);
        HBox.setHgrow(addDescField, Priority.ALWAYS);
        VBox box = new VBox(10, HeaderViews.pageTitle(title));
        Node banner = HeaderViews.readOnlyRangeBanner(ctx, set);
        if (banner != null) box.getChildren().add(banner);
        box.getChildren().addAll(table, controls);
        box.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        node = box;
    }

    /** Sets the text of a language, removing the form when the text is blank. */
    private static void setText(MultiText text, String lang, String value) {
        String v = value == null ? "" : value;
        var form = text.getForm(lang);
        if (v.isBlank()) {
            if (form.isPresent()) text.removeForm(lang);
        } else if (form.isPresent()) {
            form.get().changeText(v);
        } else {
            text.add(new Form(lang, v));
        }
    }

    /**
     * An edit of a value: refused by lift-api for a value its range does not declare - the
     * user is told to declare it first.
     */
    private static void editValue(String title, Runnable edit) {
        try {
            edit.run();
        } catch (IllegalStateException e) {
            Dialogs.error(title, I18n.get("cfg.valueReadOnly"));
        }
    }

    @Override
    public Node node() {
        return node;
    }
}
