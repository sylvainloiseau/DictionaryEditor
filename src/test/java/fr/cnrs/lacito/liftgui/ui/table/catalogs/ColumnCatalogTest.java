package fr.cnrs.lacito.liftgui.ui.table.catalogs;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.nav.ViewKey;
import fr.cnrs.lacito.liftgui.ui.table.ColumnLayout;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSection;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.view.ComponentKinds;
import fr.cnrs.lacito.liftgui.undo.History;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ColumnCatalogTest {

    private LiftDictionary d;
    private LiftEntry entry;
    private LiftSense sense;
    private LiftSense subsense;
    private LiftExample example;

    @BeforeAll
    static void english() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @BeforeEach
    void setUp() {
        d = LiftDictionary.makeBuilder()
            .withObjectLanguages("xx")
            .withMetaLanguages("en")
            .withNoteType("comment")
            .build();
        entry = d.getComponentBuilder().entry().withForm("xx", "kato").build();
        sense = d.getComponentBuilder().sense(entry).withGloss("en", "cat").build();
        d.getComponentBuilder().sense(entry).withGloss("en", "feline").build();
        subsense = d.getComponentBuilder().sense(sense).withGloss("en", "kitten").build();
        example = d.getComponentBuilder().example(subsense).withExample("xx", "kato miaŭas").build();
        d.getComponentBuilder().field(entry, "literal-meaning").addText("en", "the cat").addText("fr", "le chat").build();
    }

    private static <T> List<ColumnSection<T>> sections(ViewKey key, LiftDictionary d, ExtensionCells.Gesture g) {
        return ComponentKinds.sectionsOf(ComponentKinds.ofKey(key).orElseThrow(), d, g);
    }

    private static <T> ColumnSpec<T> column(List<ColumnSection<T>> sections, String id) {
        return ColumnSection.flatten(sections).stream().filter(c -> c.id().equals(id)).findFirst()
            .orElseThrow(() -> new AssertionError("no column " + id + " in " + ColumnSection.flatten(sections).stream()
                .map(ColumnSpec::id).toList()));
    }

    @Test
    void aFieldGivesOneSubColumnPerLanguage() {
        List<ColumnSection<LiftEntry>> s = sections(ViewKey.ENTRIES, d, ExtensionCells.Gesture.DIRECT);
        ColumnSpec<LiftEntry> field = column(s, "field:literal-meaning");
        assertEquals(List.of("en", "fr"), field.languages());
        assertEquals("le chat", field.text(entry, "fr"));
        assertFalse(field.visible(), "generated columns are not shown by default");
        assertTrue(s.stream().anyMatch(x -> x.id().equals("extensions") && x.columns().contains(field)));
    }

    @Test
    void countsOfDirectAndNestedSubcomponents() {
        List<ColumnSection<LiftEntry>> s = sections(ViewKey.ENTRIES, d, ExtensionCells.Gesture.DIRECT);
        assertEquals("2", column(s, "count:senses").text(entry, null));
        assertEquals("3", column(s, "count:senses.all").text(entry, null));
        assertEquals("1", column(s, "count:examples.all").text(entry, null));
        assertEquals("1", column(s, "count:fields").text(entry, null));
        List<ColumnSection<LiftSense>> senses = sections(ViewKey.SENSES, d, ExtensionCells.Gesture.DIRECT);
        assertEquals("1", column(senses, "count:subsenses").text(sense, null));
        assertEquals("0", column(senses, "count:examples").text(sense, null));
        assertEquals("1", column(senses, "count:examples.all").text(sense, null));
    }

    @Test
    void ancestorColumnsReadTheNearestAncestorOfTheirKind() {
        List<ColumnSection<LiftExample>> s = sections(ViewKey.EXAMPLES, d, ExtensionCells.Gesture.DIRECT);
        assertEquals("kitten", column(s, "up:sense/sense.gloss").text(example, "en"), "the nearest sense: the subsense");
        assertEquals("kato", column(s, "up:entry/entry.form").text(example, "xx"));
        assertEquals("the cat", column(s, "up:entry/field:literal-meaning").text(example, "en"));
        ColumnSpec<LiftExample> form = column(s, "up:entry/entry.form");
        assertTrue(form.title().startsWith(ColumnCatalog.kindLabel(LiftEntry.class) + " › "), form.title());
        List<String> sectionIds = s.stream().map(ColumnSection::id).toList();
        assertTrue(sectionIds.indexOf("up:sense") < sectionIds.indexOf("up:entry"), "nearest first: " + sectionIds);
    }

    @Test
    void aNoteOnAnExampleReachesItsEntry() {
        LiftNote note = d.getComponentBuilder().note(example, "comment").addText("en", "said of cats").build();
        List<ColumnSection<LiftNote>> s = sections(ViewKey.NOTES, d, ExtensionCells.Gesture.DIRECT);
        assertEquals("kato", column(s, "up:entry/entry.form").text(note, "xx"));
        assertEquals("kato miaŭas", column(s, "up:example/example.text").text(note, "xx"));
        LiftNote onEntry = d.getComponentBuilder().note(entry, "comment").addText("en", "an entry note").build();
        assertEquals("", column(s, "up:sense/sense.gloss").text(onEntry, "en"), "no such ancestor");
        assertFalse(column(s, "up:sense/sense.gloss").applies(onEntry, "en"));
        assertTrue(column(s, "up:sense/sense.gloss").applies(note, "en"));
    }

    @Test
    void ancestorTextsAreEditable() {
        List<ColumnSection<LiftExample>> s = sections(ViewKey.EXAMPLES, d, ExtensionCells.Gesture.DIRECT);
        ColumnSpec<LiftExample> form = column(s, "up:entry/entry.form");
        assertTrue(form.isEditable(example, "xx"));
        form.commit().commit(example, "xx", "gato");
        assertEquals("gato", entry.getForms().getForm("xx").orElseThrow().toPlainText());
    }

    @Test
    void typingInAnEmptyFieldCellCreatesTheFieldInOneUndoStep() {
        LiftSense other = entry.getSenses().get(1);
        d.getComponentBuilder().field(sense, "scientific-name").addText("en", "Felis catus").build();
        History history = new History(Runnable::run);
        history.attach(d);
        List<ColumnSection<LiftSense>> s = sections(ViewKey.SENSES, d, history::perform);
        ColumnSpec<LiftSense> col = column(s, "field:scientific-name");
        assertNull(ExtensionCells.field(other, "scientific-name"));
        assertTrue(col.isEditable(other, "en"));
        col.commit().commit(other, "en", "Felis silvestris");
        assertEquals("Felis silvestris", col.text(other, "en"));
        int steps = history.cursor();
        history.undo();
        assertNull(ExtensionCells.field(other, "scientific-name"), "one undo removes the created field");
        assertEquals(steps - 1, history.cursor());
    }

    @Test
    void clearingTheLastTextRemovesTheFieldUnlessItHoldsMore() {
        List<ColumnSection<LiftEntry>> s = sections(ViewKey.ENTRIES, d, ExtensionCells.Gesture.DIRECT);
        ColumnSpec<LiftEntry> col = column(s, "field:literal-meaning");
        col.commit().commit(entry, "fr", "");
        assertNotNull(ExtensionCells.field(entry, "literal-meaning"), "another language remains");
        col.commit().commit(entry, "en", "");
        assertNull(ExtensionCells.field(entry, "literal-meaning"), "no text left: removed");

        LiftField kept = d.getComponentBuilder().field(entry, "literal-meaning").addText("en", "x").build();
        d.getComponentBuilder().trait(kept, "status", "checked").build();
        col.commit().commit(entry, "en", "");
        assertNotNull(ExtensionCells.field(entry, "literal-meaning"), "a field holding a trait is kept, empty");
    }

    @Test
    void typingInAnEmptyNoteCellCreatesTheNote() {
        List<ColumnSection<LiftExample>> s = sections(ViewKey.EXAMPLES, d, ExtensionCells.Gesture.DIRECT);
        d.getComponentBuilder().note(sense, "comment").addText("en", "x").build();
        ColumnSpec<LiftExample> col = column(sections(ViewKey.EXAMPLES, d, ExtensionCells.Gesture.DIRECT), "up:sense/note:comment");
        assertNotNull(col);
        d.getComponentBuilder().note(example, "comment").addText("en", "y").build();
        ColumnSpec<LiftExample> own = column(sections(ViewKey.EXAMPLES, d, ExtensionCells.Gesture.DIRECT), "note:comment");
        own.commit().commit(example, "en", "");
        assertNull(ExtensionCells.note(example, "comment"));
        own.commit().commit(example, "en", "z");
        assertEquals("z", own.text(example, "en"));
        assertFalse(s.isEmpty());
    }

    @Test
    void traitsGiveOptionColumnsWithEveryValue() {
        d.getComponentBuilder().trait(entry, "dialect", "north").build();
        d.getComponentBuilder().trait(entry, "dialect", "south").build();
        ColumnSpec<LiftEntry> col = column(sections(ViewKey.ENTRIES, d, ExtensionCells.Gesture.DIRECT), "trait:dialect");
        assertEquals(List.of("north", "south"), col.cellValues(entry, null));
        assertEquals("north, south", col.text(entry, null));
    }

    @Test
    void defaultLayoutsAreTheCurrentColumns() {
        for (ComponentKinds.ComponentKind<?> kind : ComponentKinds.all()) {
            List<ColumnSection<Object>> s = ComponentKinds.sectionsOf(kind, d, ExtensionCells.Gesture.DIRECT);
            @SuppressWarnings("unchecked")
            List<ColumnSpec<Object>> base = new ArrayList<>((List<ColumnSpec<Object>>) (List<?>) kind.columns().apply(d));
            assertEquals(ColumnLayout.defaults(base).columnIds(), ColumnLayout.defaults(ColumnSection.flatten(s)).columnIds(),
                kind.key().name());
        }
        List<ColumnSpec<Object>> entries = ColumnSection.flatten(ComponentKinds.sectionsOf(
            ComponentKinds.ofKey(ViewKey.ENTRIES).orElseThrow(), d, ExtensionCells.Gesture.DIRECT));
        assertEquals(List.of("entry.form", "entry.morphType", "entry.dateCreated"), ColumnLayout.defaults(entries).columnIds());
        List<ColumnSpec<Object>> senses = ColumnSection.flatten(ComponentKinds.sectionsOf(
            ComponentKinds.ofKey(ViewKey.SENSES).orElseThrow(), d, ExtensionCells.Gesture.DIRECT));
        assertEquals(List.of("sense.parentEntry", "sense.gramInfo", "sense.gloss", "sense.definition"),
            ColumnLayout.defaults(senses).columnIds());
    }

    @Test
    void ancestorKindsAreNearestFirst() {
        assertEquals(List.of(LiftSense.class, LiftEntry.class), ModelTree.ancestorKinds(LiftExample.class));
        assertEquals(List.of(LiftPronunciation.class, LiftEntry.class, LiftVariant.class),
            ModelTree.ancestorKinds(LiftMedia.class));
        assertTrue(ModelTree.ancestorKinds(LiftTrait.class).contains(LiftSense.class));
        assertTrue(ModelTree.ancestorKinds(LiftEntry.class).isEmpty());
    }
}
