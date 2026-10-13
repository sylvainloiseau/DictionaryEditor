package fr.cnrs.lacito.liftgui.ui.view.header;

import fr.cnrs.lacito.liftapi.LiftDictionaryLanguagesManager;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.view.DictionaryView;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** The object and meta languages of the dictionary: usage counts, adding and removing languages. */
public final class LanguagesView implements DictionaryView {

    private final EditorContext ctx;
    private final VBox box = new VBox(10);

    public LanguagesView(EditorContext ctx) {
        this.ctx = ctx;
        var d = ctx.dictionary();
        box.setPadding(new Insets(12));
        TitledPane objPane = pane(I18n.get("nav.objectLangs"),
            languagePanel(d.getObjectLanguageManager(), d.getLiftDictionaryRegistry().getObjectText()));
        TitledPane metaPane = pane(I18n.get("nav.metaLangs"),
            languagePanel(d.getMetaLanguageManager(), d.getLiftDictionaryRegistry().getMetaText()));
        box.getChildren().addAll(HeaderViews.pageTitle(I18n.get("nav.cfgManageLangs")), objPane, metaPane);
        ctx.panel().show(I18n.get("nav.cfgManageLangs"), "", null);
    }

    private static TitledPane pane(String title, Node content) {
        TitledPane p = new TitledPane(title, content);
        p.setExpanded(true);
        p.setAnimated(false);
        return p;
    }

    /** A table of the languages with their usage, and controls to add and delete languages. */
    private VBox languagePanel(LiftDictionaryLanguagesManager manager, ObservableList<MultiText> multiTexts) {
        TableView<String> table = new TableView<>();
        Runnable reload = () -> table.setItems(FXCollections.observableArrayList(manager.getLanguages().stream().sorted().toList()));
        reload.run();
        table.setPrefHeight(140);
        TableColumn<String, String> langCol = new TableColumn<>(I18n.get("col.code"));
        langCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue()));
        langCol.setPrefWidth(120);
        TableColumn<String, String> usageCol = new TableColumn<>(I18n.get("cfg.usageCount"));
        usageCol.setCellValueFactory(cd ->
            new ReadOnlyStringWrapper(String.valueOf(DictionaryQueries.countLanguageUsage(cd.getValue(), multiTexts))));
        usageCol.setPrefWidth(80);
        table.getColumns().addAll(List.of(langCol, usageCol));

        TextField addField = new TextField();
        addField.setPromptText(I18n.get("config.addAbbr"));
        Button addBtn = new Button(I18n.get("btn.add"));
        addBtn.setOnAction(e -> {
            String code = addField.getText().trim();
            if (code.isEmpty() || manager.hasLanguage(code)) return;
            manager.addLanguage(code);
            ctx.markDirty();
            addField.clear();
            reload.run();
        });
        Button removeBtn = new Button(I18n.get("btn.delete"));
        removeBtn.setOnAction(e -> {
            String sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            long usage = DictionaryQueries.countLanguageUsage(sel, multiTexts);
            if (usage > 0) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle(I18n.get("btn.delete"));
                confirm.setHeaderText(I18n.get("config.deleteWarning", sel, usage));
                confirm.setContentText(I18n.get("config.deleteConfirm"));
                if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isEmpty()) return;
            }
            // Removing the forms drops the language occurrences; then the language itself.
            for (MultiText mt : List.copyOf(multiTexts)) {
                if (mt.getForm(sel).isPresent()) mt.removeForm(sel);
            }
            ctx.markDirty();
            try {
                if (manager.hasLanguage(sel)) manager.removeLanguage(sel);
            } catch (IllegalArgumentException ex) {
                // Still used somewhere outside the listed texts (e.g. the header).
                Dialogs.error(I18n.get("btn.delete"), I18n.get("error.languageStillUsed", sel));
            }
            reload.run();
        });
        HBox controls = new HBox(8, addField, addBtn, removeBtn);
        controls.setPadding(new Insets(6, 0, 0, 0));
        HBox.setHgrow(addField, Priority.ALWAYS);
        return new VBox(6, table, controls);
    }

    @Override
    public Node node() {
        return box;
    }
}
