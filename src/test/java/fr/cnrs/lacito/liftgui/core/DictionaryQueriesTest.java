package fr.cnrs.lacito.liftgui.core;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DictionaryQueriesTest {

    private LiftDictionary d;
    private LiftEntry entry;
    private LiftSense sense;
    private LiftExample example;

    @BeforeEach
    void setUp() {
        d = LiftDictionary.makeBuilder()
            .withObjectLanguages("xx")
            .withMetaLanguages("en")
            .withNoteType("comment", "unused")
            .build();
        entry = d.getComponentBuilder().entry().withForm("xx", "kato").build();
        sense = d.getComponentBuilder().sense(entry).withGloss("en", "cat").withDefinition("en", "a small feline").build();
        example = d.getComponentBuilder().example(sense).withExample("xx", "kato miaŭas").build();
    }

    @Test
    void owningEntryWalksUpToTheEntry() {
        assertSame(entry, DictionaryQueries.owningEntry(entry));
        assertSame(entry, DictionaryQueries.owningEntry(sense));
        assertSame(entry, DictionaryQueries.owningEntry(example));
    }

    @Test
    void owningEntryOfAMultiTextIsTheEntryOfItsComponent() {
        assertSame(entry, DictionaryQueries.owningEntry(sense.getGlosses()));
        assertSame(entry, DictionaryQueries.owningEntry(entry.getForms()));
    }

    @Test
    void owningEntryOfNullOrHeaderObjectsIsNull() {
        assertNull(DictionaryQueries.owningEntry(null));
        assertNull(DictionaryQueries.owningEntry(d.getHeader()));
    }

    @Test
    void owningSenseOrEntryStopsAtTheSense() {
        assertSame(sense, DictionaryQueries.owningSenseOrEntry(example));
        assertSame(entry, DictionaryQueries.owningSenseOrEntry(entry.getForms()));
    }

    @Test
    void traitValueReturnsTheFirstTraitOfThatName() {
        d.getComponentBuilder().trait(entry, "morph-type", "stem").build();
        assertEquals("stem", DictionaryQueries.traitValue(entry, "morph-type"));
        assertEquals("", DictionaryQueries.traitValue(entry, "absent"));
    }

    @Test
    void traitValueTokensSplitsOnWhitespace() {
        LiftTrait t = d.getComponentBuilder().trait(entry, "domains", " a  b c ").build();
        assertEquals(List.of("a", "b", "c"), DictionaryQueries.traitValueTokens(t));
        t.setValue("");
        assertEquals(List.of(), DictionaryQueries.traitValueTokens(t));
    }

    @Test
    void searchTextCoversFormsGlossesAndDefinitions() {
        String text = DictionaryQueries.searchText(entry);
        assertTrue(text.contains("kato"));
        assertTrue(text.contains("cat"));
        assertTrue(text.contains("a small feline"));
    }

    @Test
    void featureUsageCountsTypedComponents() {
        d.getComponentBuilder().note(entry, "comment").build();
        d.getComponentBuilder().note(sense, "comment").build();
        FeatureSet noteTypes = d.getHeader().getNoteTypeManager();
        var reg = d.getLiftDictionaryRegistry();
        assertEquals(2, DictionaryQueries.countFeatureUsage(reg, noteTypes.getFeature("comment")));
        assertEquals(0, DictionaryQueries.countFeatureUsage(reg, noteTypes.getFeature("unused")));
    }

    @Test
    void renameFeatureUpdatesTraitValuesUsingTheSet() {
        FeatureSet status = d.getHeader().addFeatureSet("status");
        status.addFeature("draft");
        status.addFeature("checked");
        LiftTrait t = d.getComponentBuilder().trait(entry, "status", "draft checked").build();
        LiftTrait other = d.getComponentBuilder().trait(entry, "colour", "draft").build();

        DictionaryQueries.renameFeature(d.getLiftDictionaryRegistry(), status, "draft", "provisional");

        assertTrue(status.hasFeature("provisional"));
        assertFalse(status.hasFeature("draft"));
        assertEquals("provisional checked", t.getValue());
        assertEquals("draft", other.getValue(), "a trait not using the set is left alone");
    }

    @Test
    void translationOfTypeFindsTypedTranslations() {
        d.getComponentBuilder().translation(example, "free").addText("en", "the cat").build();
        MultiText free = DictionaryQueries.translationOfType(example, "free");
        assertNotNull(free);
        assertEquals("the cat", free.getForm("en").orElseThrow().toPlainText());
        assertNull(DictionaryQueries.translationOfType(example, "literal"));
    }

    @Test
    void languageUsageCountsTextsHavingAForm() {
        List<MultiText> texts = List.of(entry.getForms(), sense.getGlosses(), sense.getDefinition());
        assertEquals(1, DictionaryQueries.countLanguageUsage("xx", texts));
        assertEquals(2, DictionaryQueries.countLanguageUsage("en", texts));
        assertEquals(0, DictionaryQueries.countLanguageUsage("fr", texts));
    }
}
