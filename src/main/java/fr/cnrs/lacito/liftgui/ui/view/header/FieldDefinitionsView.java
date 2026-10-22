package fr.cnrs.lacito.liftgui.ui.view.header;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftDictionaryRegistry;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.controls.EditorSupport;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.editor.EditorPanel;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.table.Columns;
import fr.cnrs.lacito.liftgui.ui.view.ObjectTableView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

/** The field and trait definitions of the header: list, creation, deletion of unused ones, editing. */
public final class FieldDefinitionsView extends ObjectTableView<LiftFieldAndTraitDefinition> {

    private static final List<String> FIELD_TYPES = List.of("multistring", "multitext");
    private static final List<String> TRAIT_TYPES = List.of("datetime", "integer", "option", "option-collection", "option-sequence");

    public FieldDefinitionsView(EditorContext ctx) {
        super(ctx, LiftFieldAndTraitDefinition.class, columns(ctx.dictionary()),
            FXCollections.observableArrayList(ctx.dictionary().getHeader().getFieldsAndTraitsDefinitions()),
            null, null, fd -> showDefinition(ctx, fd));
        LiftHeader header = ctx.dictionary().getHeader();
        Button addBtn = new Button(I18n.get("cfg.newFieldOrTrait"));
        addBtn.setOnAction(e -> newDefinition(ctx));
        Button deleteBtn = new Button(I18n.get(Keys.BTN_DELETE));
        deleteBtn.setOnAction(e -> {
            LiftFieldAndTraitDefinition sel = table.table().getSelectionModel().getSelectedItem();
            if (sel == null) return;
            long usage = DictionaryQueries.countFieldOrTraitUsage(ctx.dictionary().getLiftDictionaryRegistry(), sel);
            if (usage > 0) {
                Dialogs.error(I18n.get("btn.delete"), I18n.get("cfg.deleteNotAllowed", usage));
                return;
            }
            header.removeFieldOrTraitDefinition(sel);
            table.source().remove(sel);
        });
        HBox controls = new HBox(8, addBtn, deleteBtn);
        controls.setPadding(new Insets(6, 0, 0, 0));
        addBelow(controls);
    }

    private static List<ColumnSpec<LiftFieldAndTraitDefinition>> columns(LiftDictionary d) {
        // Usage counts, computed once.
        LiftDictionaryRegistry reg = d.getLiftDictionaryRegistry();
        Map<LiftFieldAndTraitDefinition, Long> usage = new HashMap<>();
        for (LiftFieldAndTraitDefinition fd : d.getHeader().getFieldsAndTraitsDefinitions()) {
            usage.put(fd, DictionaryQueries.countFieldOrTraitUsage(reg, fd));
        }
        return List.of(
            Columns.text("fieldDef.name", I18n.get("cfg.fieldDefName"), LiftFieldAndTraitDefinition::getName),
            Columns.text("fieldDef.kind", I18n.get("cfg.kind"), FieldDefinitionsView::kindLabel),
            Columns.text("fieldDef.type", I18n.get("cfg.fieldDefType"), LiftFieldAndTraitDefinition::getTypeStr),
            Columns.text("fieldDef.targets", I18n.get("cfg.targets"), LiftFieldAndTraitDefinition::getTargetAsString),
            Columns.text("fieldDef.description", I18n.get("cfg.description"), fd -> EditorSupport.firstForm(fd.getDescription())),
            Columns.text("fieldDef.usage", I18n.get("cfg.usageCount"),
                fd -> String.valueOf(usage.computeIfAbsent(fd, x -> DictionaryQueries.countFieldOrTraitUsage(reg, x))))
        );
    }

    private static String kindLabel(LiftFieldAndTraitDefinition fd) {
        if (fd.isFieldDefinition()) return I18n.get("cfg.kindField");
        if (fd.isTraitDefinition()) return I18n.get("cfg.kindTrait");
        return I18n.get("cfg.kindUnknown");
    }

    /** Asks for the name, kind and type of a new definition, and adds it to the header. */
    private void newDefinition(EditorContext ctx) {
        LiftHeader header = ctx.dictionary().getHeader();
        Dialog<LiftFieldAndTraitDefinition> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("cfg.newFieldOrTrait"));
        dlg.setHeaderText(I18n.get("cfg.chooseKind"));
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefWidth(450);

        TextField nameField = new TextField();
        nameField.setPromptText(I18n.get("cfg.fieldDefName"));
        ToggleGroup kindGroup = new ToggleGroup();
        RadioButton rbField = new RadioButton(I18n.get("cfg.kindField"));
        rbField.setToggleGroup(kindGroup);
        rbField.setSelected(true);
        RadioButton rbTrait = new RadioButton(I18n.get("cfg.kindTrait"));
        rbTrait.setToggleGroup(kindGroup);
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.setEditable(true);
        typeCombo.setPromptText(I18n.get("cfg.fieldDefType"));
        Runnable updateTypes = () -> {
            List<String> types = rbField.isSelected() ? FIELD_TYPES : TRAIT_TYPES;
            typeCombo.getItems().setAll(types);
            typeCombo.setValue(rbField.isSelected() ? "multitext" : "option");
        };
        updateTypes.run();
        rbField.setOnAction(e -> updateTypes.run());
        rbTrait.setOnAction(e -> updateTypes.run());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label(I18n.get("cfg.fieldDefName")), nameField);
        grid.addRow(1, new Label(I18n.get("cfg.kind")), new HBox(12, rbField, rbTrait));
        grid.addRow(2, new Label(I18n.get("cfg.fieldDefType")), typeCombo);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);
        dlg.getDialogPane().setContent(grid);

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            String name = nameField.getText().trim();
            if (name.isEmpty()) return null;
            if (header.getFieldsAndTraitsDefinitions().stream().anyMatch(fd -> fd.getName().equals(name))) {
                Dialogs.error(I18n.get("cfg.newFieldOrTrait"), I18n.get("cfg.idExists", name));
                return null;
            }
            LiftFieldAndTraitDefinition fd = rbTrait.isSelected() ? header.createTraitDefinition(name) : header.createFieldDefinition(name);
            String typeVal = typeCombo.getValue();
            if (typeVal != null && !typeVal.isBlank()) fd.setDataModel(Optional.of(typeVal));
            ctx.dictionary().getMetaLanguageManager().getLanguages().stream().findFirst()
                .ifPresent(lang -> fd.getDescription().add(new Form(lang, I18n.get("cfg.autoAdded"))));
            return fd;
        });
        dlg.showAndWait().ifPresent(fd -> {
            table.source().add(fd);
            table.select(fd);
        });
    }

    /** The editor of a definition: data type, targets, option range, label and description. */
    private static void showDefinition(EditorContext ctx, LiftFieldAndTraitDefinition fd) {
        String kindLabel = kindLabel(fd);
        EditorPanel panel = ctx.panel().show(fd.getName(), kindLabel + " – " + fd.getTypeStr(), null);

        GridPane g = EditorPanel.grid();
        EditorPanel.addReadOnlyRow(g, 0, I18n.get("cfg.fieldDefName"), fd.getName());
        EditorPanel.addReadOnlyRow(g, 1, I18n.get("cfg.kind"), kindLabel);

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.setEditable(true);
        typeCombo.getItems().addAll(FIELD_TYPES);
        typeCombo.getItems().addAll(TRAIT_TYPES);
        typeCombo.setValue(fd.getTypeStr());
        boolean[] restoring = {false};
        typeCombo.valueProperty().addListener((obs, o, n) -> {
            if (restoring[0]) return;
            try {
                fd.setDataModel(n == null || n.isBlank() ? Optional.empty() : Optional.of(n.trim()));
            } catch (IllegalArgumentException ex) {
                Dialogs.error(I18n.get("cfg.fieldDefType"), I18n.get("error.invalidValue", n));
            } catch (IllegalStateException ex) {
                // The values of the fields or traits that use it depend on the data model.
                long usage = DictionaryQueries.countFieldOrTraitUsage(ctx.dictionary().getLiftDictionaryRegistry(), fd);
                Dialogs.error(I18n.get("cfg.fieldDefType"), I18n.get("cfg.typeChangeNotAllowed", usage));
                restoring[0] = true;
                try {
                    typeCombo.setValue(o);
                } finally {
                    restoring[0] = false;
                }
            }
        });
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);
        g.addRow(2, new Label(I18n.get("cfg.fieldDefType")), typeCombo);

        TextField targetsField = new TextField(fd.getTargetAsString());
        targetsField.setPromptText("entry sense variant ...");
        // Applied when the field is validated (Enter or focus lost), and only if every target
        // is a known LIFT element name.
        Runnable applyTargets = () -> {
            String n = targetsField.getText().trim();
            if (n.isEmpty() || n.equals(fd.getTargetAsString())) {
                targetsField.setStyle("");
                return;
            }
            try {
                LiftFieldAndTraitDefinitionTarget.parseTargetString(n);
                fd.setTargets(n);
                targetsField.setStyle("");
            } catch (RuntimeException ex) {
                targetsField.setStyle("-fx-border-color: #c0392b; -fx-border-width: 2;");
            }
        };
        targetsField.setOnAction(e -> applyTargets.run());
        targetsField.focusedProperty().addListener((obs, was, is) -> {
            if (!is) applyTargets.run();
        });
        GridPane.setHgrow(targetsField, Priority.ALWAYS);
        g.addRow(3, new Label(I18n.get("cfg.targets")), targetsField);

        ComboBox<FeatureSet> rangeCombo = new ComboBox<>(ctx.dictionary().getHeader().getFeatureSets());
        StringConverter<FeatureSet> byId = new StringConverter<>() {
            @Override
            public String toString(FeatureSet f) {
                return f == null ? "" : f.getId();
            }

            @Override
            public FeatureSet fromString(String str) {
                return null;
            }
        };
        rangeCombo.setConverter(byId);
        rangeCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(FeatureSet item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getId());
            }
        });
        rangeCombo.setValue(fd.getResolvedFeatureSet().orElse(null));
        boolean[] restoringRange = {false};
        rangeCombo.valueProperty().addListener((obs, o, n) -> {
            if (restoringRange[0]) return;
            try {
                fd.setResolvedRange(Optional.ofNullable(n));
            } catch (IllegalStateException ex) {
                // The traits that use it hold features of the current range.
                long usage = DictionaryQueries.countFieldOrTraitUsage(ctx.dictionary().getLiftDictionaryRegistry(), fd);
                Dialogs.error(I18n.get("cfg.optionRange"), I18n.get("cfg.rangeChangeNotAllowed", usage));
                restoringRange[0] = true;
                try {
                    rangeCombo.setValue(o);
                } finally {
                    restoringRange[0] = false;
                }
            }
        });
        GridPane.setHgrow(rangeCombo, Priority.ALWAYS);
        g.addRow(4, new Label(I18n.get("cfg.optionRange")), rangeCombo);

        panel.section(I18n.get("editor.identity"), g, true);
        panel.section(I18n.get("cfg.label"), HeaderViews.multiTextEditor(ctx.dictionary(), fd.getLabel()), true);
        panel.section(I18n.get("cfg.description"), HeaderViews.multiTextEditor(ctx.dictionary(), fd.getDescription()), true);
    }
}
