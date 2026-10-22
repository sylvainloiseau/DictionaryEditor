package fr.cnrs.lacito.liftgui.ui.table.catalogs;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.table.Columns;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.beans.binding.Bindings;

/** The columns available for entries. Forms and morph-type can be edited in the table. */
public final class EntryColumns {

    private EntryColumns() {}

    public static List<ColumnSpec<LiftEntry>> catalog(LiftDictionary d) {
        // The object languages, and any other language used in the entries' forms.
        List<String> formLangs = Stream.concat(
                d.getObjectLanguageManager().getLanguages().stream(),
                d.getLiftDictionaryRegistry().getEntries().stream().flatMap(e -> e.getForms().getLangs().stream())
            )
            .filter(s -> s != null && !s.isBlank())
            .distinct()
            .sorted()
            .toList();
        return List.of(
            Columns.<LiftEntry>multiText("entry.form", I18n.get(Keys.COL_FORM), formLangs, LiftEntry::getForms)
                .live((e, lang) -> e.getForms().formTextProperty(lang))
                // An empty text removes the form, as in the editors.
                .editable((e, lang, text) -> e.getForms().formTextProperty(lang).set(text))
                .facet(),
            Columns.<LiftEntry>text("entry.morphType", I18n.get(Keys.COL_MORPH_TYPE),
                    e -> DictionaryQueries.traitValue(e, Keys.MORPH_TYPE))
                .live((e, lang) -> Bindings.createStringBinding(
                    () -> DictionaryQueries.traitValue(e, Keys.MORPH_TYPE), e.getTraits()))
                .editable((e, lang, text) -> setMorphType(d, e, text.trim()))
                .facet()
                .width(110),
            Columns.<LiftEntry>text("entry.dateCreated", I18n.get(Keys.COL_DATE_CREATED), e -> e.getDateCreated().orElse(""))
                .facet()
                .width(130),
            Columns.<LiftEntry>text("entry.id", I18n.get("col.id"), e -> e.getId().orElse("")).hidden(),
            Columns.<LiftEntry>text("entry.dateModified", I18n.get("field.dateModified"), e -> e.getDateModified().orElse(""))
                .hidden()
        );
    }

    /** Sets, replaces or (for an empty value) removes the morph-type trait. */
    private static void setMorphType(LiftDictionary d, LiftEntry e, String value) {
        Optional<LiftTrait> existing = e.getTraits().stream()
            .filter(t -> Keys.MORPH_TYPE.equals(t.getName()))
            .findFirst();
        if (value.isEmpty()) existing.ifPresent(e::deleteTrait);
        else if (existing.isPresent()) existing.get().setValue(value);
        else d.getComponentBuilder().trait(e, Keys.MORPH_TYPE, value).build();
    }
}
