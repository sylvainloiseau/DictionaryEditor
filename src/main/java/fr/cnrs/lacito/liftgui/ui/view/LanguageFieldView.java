package fr.cnrs.lacito.liftgui.ui.view;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.ObjectLabels;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.table.Columns;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Every form of the dictionary in the object languages (or in the meta languages): one row
 * per form, with the kind of text and the sense or entry it belongs to.
 */
public final class LanguageFieldView extends ObjectTableView<MultiTextRow> {

    public LanguageFieldView(EditorContext ctx, boolean objectLanguages) {
        super(ctx, MultiTextRow.class, columns(ctx.dictionary(), objectLanguages), rows(ctx.dictionary(), objectLanguages),
            null, "placeholder.noMultiField",
            row -> ctx.editors().showMultiText(row.multiText(), row.lang(),
                I18n.get(objectLanguages ? "nav.objectLangs" : "nav.metaLangs"), objectLanguages));
    }

    private static Set<String> languages(LiftDictionary d, boolean objectLanguages) {
        return objectLanguages ? d.getObjectLanguageManager().getLanguages() : d.getMetaLanguageManager().getLanguages();
    }

    /**
     * The forms of the texts of the view's role (object or meta texts, as filed by lift-api)
     * in the view's languages, and the forms of the other texts in a language of this view
     * only (e.g. a citation, an object text, with a form in a meta language). So every form
     * is listed in one of the two views.
     */
    private static ObservableList<MultiTextRow> rows(LiftDictionary d, boolean objectLanguages) {
        Set<String> langs = languages(d, objectLanguages);
        Set<String> otherLangs = languages(d, !objectLanguages);
        var reg = d.getLiftDictionaryRegistry();
        List<MultiText> own = objectLanguages ? reg.getObjectText() : reg.getMetaText();
        List<MultiText> other = objectLanguages ? reg.getMetaText() : reg.getObjectText();
        Set<MultiText> ownSet = Collections.newSetFromMap(new IdentityHashMap<>());
        ownSet.addAll(own);
        List<MultiTextRow> rows = new ArrayList<>();
        for (MultiText mt : own) {
            for (Form f : mt.getForms()) {
                if (langs.contains(f.getLang())) rows.add(new MultiTextRow(mt, f));
            }
        }
        for (MultiText mt : other) {
            if (ownSet.contains(mt)) continue;
            for (Form f : mt.getForms()) {
                if (langs.contains(f.getLang()) && !otherLangs.contains(f.getLang())) rows.add(new MultiTextRow(mt, f));
            }
        }
        return FXCollections.observableArrayList(rows);
    }

    private static List<ColumnSpec<MultiTextRow>> columns(LiftDictionary d, boolean objectLanguages) {
        return List.of(
            Columns.<MultiTextRow>text("text.kind", I18n.get(Keys.COL_PARENT_TYPE), r -> ObjectLabels.multiTextKind(r.multiText()))
                .width(100),
            Columns.<MultiTextRow>text("text.owner", I18n.get(Keys.COL_PARENT), r -> ObjectLabels.ownerId(r.multiText())),
            Columns.<MultiTextRow>perLanguage("text.text", I18n.get(Keys.NAV_LANGUAGES), languages(d, objectLanguages),
                    (r, lang) -> lang.equals(r.lang()) ? r.text() : "")
                .textFilter()
                .width(160)
        );
    }
}
