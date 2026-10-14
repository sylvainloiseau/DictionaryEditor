package fr.cnrs.lacito.liftgui.ui.view;

import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.table.catalogs.EntryColumns;
import java.util.List;
import java.util.Optional;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/**
 * The entries of the dictionary (or a subset of them). Forms and morph-type can be edited
 * in the table; the search covers forms, pronunciations, glosses and definitions.
 */
public final class EntryView extends ObjectTableView<LiftEntry> {

    public EntryView(EditorContext ctx, List<?> subset, String title) {
        super(ctx, LiftEntry.class, EntryColumns.catalog(ctx.dictionary()),
            rows(subset, LiftEntry.class, ctx.dictionary().getLiftDictionaryRegistry().getEntries()),
            title, "placeholder.noData", ctx.editors()::showEntry);
        table.searchText(DictionaryQueries::searchText);
    }

    /** Selects the first entry, as the view opens. */
    public void selectFirst() {
        if (!table.table().getItems().isEmpty()) table.table().getSelectionModel().selectFirst();
    }

    @Override
    public String addButtonText() {
        return I18n.get(Keys.BTN_NEW_ENTRY);
    }

    /** Asks for the form of a new entry (an entry needs one), creates and selects it. */
    @Override
    public void onAdd() {
        Optional<String> lang = Dialogs.firstLanguage(ctx.dictionary().getObjectLanguageManager().getLanguages());
        if (lang.isEmpty()) return;
        Dialogs.askText(I18n.get(Keys.BTN_NEW_ENTRY), I18n.get(Keys.COL_FORM) + " (" + lang.get() + ")").ifPresent(form ->
            showNew(create(I18n.get("undo.newEntry", form), lang.get(), form)));
    }

    /** Copies the selected entry (id and forms, tab-separated) to the clipboard. */
    public void copySelected() {
        LiftEntry e = table.table().getSelectionModel().getSelectedItem();
        if (e == null) return;
        StringBuilder sb = new StringBuilder(e.getId().orElse(""));
        for (Form f : e.getForms().getForms()) sb.append("\t").append(f.toPlainText());
        ClipboardContent cc = new ClipboardContent();
        cc.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(cc);
    }

    /** Creates an entry whose form (in the first object language) is the clipboard text. */
    public void pasteAsNewEntry() {
        String text = Clipboard.getSystemClipboard().getString();
        if (text == null || text.isBlank()) return;
        Optional<String> lang = Dialogs.firstLanguage(ctx.dictionary().getObjectLanguageManager().getLanguages());
        if (lang.isEmpty()) return;
        showNew(create(I18n.get("undo.pasteEntry", text.trim()), lang.get(), text.trim()));
    }

    /** Creates an entry with one form, as one undo step. */
    private LiftEntry create(String label, String lang, String form) {
        LiftEntry[] created = new LiftEntry[1];
        ctx.perform(label, null, () ->
            created[0] = ctx.dictionary().getComponentBuilder().entry().withForm(lang, form).build());
        return created[0];
    }

    /**
     * Selects a new entry and shows it in the editor panel. When the filters hide it, they
     * are reset first, so that the user never edits another entry by mistake.
     */
    private void showNew(LiftEntry entry) {
        table.refresh();
        if (!table.select(entry)) {
            table.resetFilters();
            table.select(entry);
        }
        ctx.editors().showEntry(entry);
    }
}
