package fr.cnrs.lacito.liftgui.core;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import java.util.List;
import org.junit.jupiter.api.Test;

class DictionaryValidatorTest {

    private static LiftDictionary dictionary() {
        return LiftDictionary.makeBuilder().withObjectLanguages("xx").withMetaLanguages("en").build();
    }

    private static List<String> keys(LiftDictionary d) {
        return DictionaryValidator.validate(d).stream().map(DictionaryValidator.Issue::key).toList();
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
    void describeNamesTheKindAndTheEntry() {
        LiftDictionary d = dictionary();
        LiftEntry e = d.getComponentBuilder().entry().withForm("xx", "kato").build();
        assertEquals("Entry — kato", DictionaryValidator.describe(e));
        assertEquals("Sense — kato", DictionaryValidator.describe(d.getComponentBuilder().sense(e).withGloss("en", "cat").build()));
    }
}
