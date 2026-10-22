package fr.cnrs.lacito.liftgui.core;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DictionaryValidatorTest {

    private static LiftDictionary dictionary() {
        return LiftDictionary.makeBuilder().withObjectLanguages("xx").withMetaLanguages("en").build();
    }

    private static List<String> keys(LiftDictionary d) {
        return DictionaryValidator.validate(d).stream().map(DictionaryValidator.Issue::key).toList();
    }

    @Test
    void aRangeMissingFromItsSharedFileAndTheValuesItDoesNotDeclareAreReported(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("shared.lift-ranges"), """
            <lift-ranges><range id="note-type"><range-element id="grammar"/></range></lift-ranges>
            """);
        Path lift = dir.resolve("test.lift");
        Files.writeString(lift, """
            <?xml version="1.0" encoding="UTF-8"?>
            <lift version="0.15" producer="test">
            <header><ranges>
            <range id="note-type" href="shared.lift-ranges"/>
            <range id="dialect" href="shared.lift-ranges"/>
            </ranges></header>
            <entry id="e1"><lexical-unit><form lang="xx"><text>kato</text></form></lexical-unit>
            <sense><gloss lang="en"><text>cat</text></gloss></sense>
            <note type="grammar"><form lang="en"><text>n.</text></form></note>
            <note type="reference"><form lang="en"><text>p. 3</text></form></note>
            </entry>
            </lift>
            """);
        LiftDictionary d = LiftDictionary.loadDictionaryFromFile(lift.toFile());
        List<DictionaryValidator.Issue> issues = DictionaryValidator.validate(d);
        assertTrue(issues.contains(new DictionaryValidator.Issue("validate.externalRangeMissing",
            "dialect : shared.lift-ranges")), issues.toString());
        assertTrue(issues.contains(new DictionaryValidator.Issue("validate.valueNotInExternalRange",
            "note-type = reference")), issues.toString());
        assertFalse(issues.stream().anyMatch(i -> i.where().contains("grammar")), issues.toString());
    }

    @Test
    void aCompleteEntryHasNoIssue() {
        LiftDictionary d = dictionary();
        LiftEntry e = d.getComponentBuilder().entry().withForm("xx", "kato").build();
        d.getComponentBuilder().sense(e).withGloss("en", "cat").build();
        assertEquals(List.of(), keys(d));
    }

    @Test
    void entriesWithoutFormAndSensesWithoutGlossAreReported() {
        // The builders refuse such components; they come from files, or from later edits.
        LiftDictionary d = dictionary();
        LiftEntry e = d.getComponentBuilder().entry().withForm("xx", "kato").build();
        LiftSense s = d.getComponentBuilder().sense(e).withGloss("en", "cat").build();
        e.getForms().formTextProperty("xx").set("");
        s.getGlosses().formTextProperty("en").set("");
        List<String> keys = keys(d);
        assertTrue(keys.contains("validate.entryWithoutForm"), keys.toString());
        assertTrue(keys.contains("validate.senseWithoutGloss"), keys.toString());
    }

    @Test
    void undeclaredTraitsAndFieldsAreReportedButNotTraitsNamedAfterARange() {
        LiftDictionary d = dictionary();
        d.getHeader().addFeatureSet("status");
        d.getHeader().createTraitDefinition("register");
        LiftEntry e = d.getComponentBuilder().entry()
            .withForm("xx", "kato")
            .addTrait("status", "draft")
            .addTrait("register", "formal")
            .build();
        d.getComponentBuilder().sense(e).withGloss("en", "cat").build();
        assertEquals(List.of(), keys(d));

        d.getComponentBuilder().trait(e, "colour", "red").build();
        d.getComponentBuilder().field(e, "summary").addText("en", "A cat").build();
        List<String> keys = keys(d);
        assertTrue(keys.contains("validate.undeclaredTrait"), keys.toString());
        assertTrue(keys.contains("validate.undeclaredField"), keys.toString());
    }

    @Test
    void describeNamesTheKindAndTheEntry() {
        LiftDictionary d = dictionary();
        LiftEntry e = d.getComponentBuilder().entry().withForm("xx", "kato").build();
        assertEquals("Entry — kato", DictionaryValidator.describe(e));
        assertEquals("Sense — kato", DictionaryValidator.describe(d.getComponentBuilder().sense(e).withGloss("en", "cat").build()));
    }
}
