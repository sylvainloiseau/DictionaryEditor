package fr.cnrs.lacito.liftgui.ui.view.quickentry;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.builder.EntryBuilder;
import fr.cnrs.lacito.liftapi.builder.SenseBuilder;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.controls.EditorSupport;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.view.DictionaryView;
import java.util.List;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Quick entry: a table where each row (forms, glosses, grammatical info) becomes a new
 * entry with one sense. An entry is created when the user leaves a filled row, or for all
 * the filled rows with the "create entries" button.
 */
public final class QuickEntryView implements DictionaryView {

    private static final int INITIAL_ROWS = 5;

    private final EditorContext ctx;
    private final TableView<QuickEntryRow> table = new TableView<>();
    private final Set<String> objLangs;
    private final Set<String> metaLangs;

    public QuickEntryView(EditorContext ctx) {
        this.ctx = ctx;
        LiftDictionary d = ctx.dictionary();
        this.objLangs = d.getObjectLanguageManager().getLanguages();
        this.metaLangs = d.getMetaLanguageManager().getLanguages();
        table.setPlaceholder(new Label(I18n.get("placeholder.quickEntryHint")));
        table.setEditable(true);
        for (String l : objLangs) {
            table.getColumns().add(textColumn(I18n.get("quick.formColumn", l), row -> row.formProperty(l)));
        }
        for (String l : metaLangs) {
            table.getColumns().add(textColumn(I18n.get("quick.senseColumn", l), row -> row.glossProperty(l)));
        }
        table.getColumns().add(gramInfoColumn(EditorSupport.sortedFeatureIds(d.getHeader().getGrammaticalInfoManager())));
        resetRows();

        table.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            int prev = oldIdx.intValue();
            if (prev < 0 || prev >= table.getItems().size()) return;
            if (createEntry(table.getItems().get(prev)) == null) return;
            ctx.markDirty();
            int entries = d.getLiftDictionaryRegistry().getEntries().size();
            ctx.showCount(entries, entries);
            ensureEmptyLastRow();
        });
        table.setOnMouseClicked(e -> {
            int lastIdx = table.getItems().size() - 1;
            if (lastIdx >= 0 && table.getSelectionModel().getSelectedIndex() == lastIdx) ensureEmptyLastRow();
        });
        ctx.showCount(0, 0);
    }

    private TableColumn<QuickEntryRow, String> textColumn(String title, java.util.function.Function<QuickEntryRow, javafx.beans.property.StringProperty> property) {
        TableColumn<QuickEntryRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> property.apply(cd.getValue()));
        c.setCellFactory(tc -> new CommitOnFocusLossCell<>(property));
        c.setPrefWidth(130);
        c.setEditable(true);
        return c;
    }

    private TableColumn<QuickEntryRow, String> gramInfoColumn(List<String> gramCodes) {
        ObservableList<String> gramItems = FXCollections.observableArrayList(gramCodes);
        TableColumn<QuickEntryRow, String> giCol = new TableColumn<>(I18n.get(Keys.COL_GRAM_CODE));
        giCol.setCellValueFactory(cd -> cd.getValue().gramInfoProperty());
        giCol.setCellFactory(tc -> {
            ComboBox<String> combo = new ComboBox<>(gramItems);
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setPromptText("—");
            TableCell<QuickEntryRow, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        combo.setValue(item);
                        setGraphic(combo);
                    }
                }
            };
            combo.valueProperty().addListener((obs, o, n) -> {
                if (cell.getTableRow() != null && cell.getTableRow().getItem() != null) {
                    cell.getTableRow().getItem().gramInfoProperty().set(n != null ? n : "");
                }
            });
            return cell;
        });
        giCol.setPrefWidth(140);
        giCol.setEditable(true);
        return giCol;
    }

    private void resetRows() {
        table.getItems().clear();
        for (int i = 0; i < INITIAL_ROWS; i++) table.getItems().add(new QuickEntryRow());
    }

    private boolean hasContent(QuickEntryRow row) {
        return objLangs.stream().anyMatch(l -> !row.formProperty(l).get().isBlank())
            || metaLangs.stream().anyMatch(l -> !row.glossProperty(l).get().isBlank());
    }

    /** There is always an empty row at the end. */
    private void ensureEmptyLastRow() {
        if (table.getItems().isEmpty() || hasContent(table.getItems().getLast())) table.getItems().add(new QuickEntryRow());
    }

    /**
     * Creates the entry of a row (once): its forms, and one sense with the glosses and the
     * grammatical info. A sense needs a gloss: without one, the grammatical info is not used.
     * Returns null when the row has no form or was already created.
     */
    private LiftEntry createEntry(QuickEntryRow row) {
        LiftDictionary d = ctx.dictionary();
        List<String> filledObjLangs = objLangs.stream().filter(l -> !row.formProperty(l).get().isBlank()).toList();
        if (filledObjLangs.isEmpty() || row.createdProperty().get()) return null;
        row.createdProperty().set(true);

        EntryBuilder eb = d.getComponentBuilder().entry();
        for (String l : filledObjLangs) eb.withForm(l, row.formProperty(l).get());
        LiftEntry e = eb.build();

        List<String> filledMetaLangs = metaLangs.stream().filter(l -> !row.glossProperty(l).get().isBlank()).toList();
        String gi = row.gramInfoProperty().get();
        if (!filledMetaLangs.isEmpty()) {
            SenseBuilder sb = d.getComponentBuilder().sense(e);
            for (String l : filledMetaLangs) sb.withGloss(l, row.glossProperty(l).get());
            if (!gi.isBlank()) sb.withPartOfSpeech(gi);
            sb.build();
        }
        return e;
    }

    @Override
    public Node node() {
        return table;
    }

    @Override
    public String addButtonText() {
        return I18n.get(Keys.BTN_CREATE_ENTRIES);
    }

    /** Creates the entries of all the filled rows. */
    @Override
    public void onAdd() {
        int created = 0;
        for (QuickEntryRow row : table.getItems()) {
            if (createEntry(row) != null) created++;
        }
        if (created == 0) return;
        ctx.markDirty();
        Dialogs.info(I18n.get(Keys.NAV_QUICK_ENTRY), I18n.get(Keys.INFO_QUICK_ENTRY_CREATED, created));
        resetRows();
    }
}
