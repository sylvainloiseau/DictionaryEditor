package fr.cnrs.lacito.liftgui.undo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftVersion;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftapi.model.MultiText;

/**
 * The history on an in-memory dictionary. The tasks it hands to "the end of the JavaFX
 * event" are queued, and run by {@link #endOfEvent()}.
 */
class HistoryTest {

    private final Deque<Runnable> later = new ArrayDeque<>();
    private History history;
    private LiftDictionary d;
    private LiftEntry entry;

    @BeforeEach
    void setUp() {
        d = LiftDictionary.makeBuilder()
            .withLiftVersion(LiftVersion.V0_15)
            .withObjectLanguages("qyz")
            .withMetaLanguages("en", "fr")
            .withNoteType("general")
            .build();
        entry = d.getComponentBuilder().entry().withForm("qyz", "walk")
            .addSense(s -> s.withGloss("en", "to walk")).build();
        history = new History(later::add);
        history.attach(d);
    }

    private void endOfEvent() {
        while (!later.isEmpty()) later.poll().run();
    }

    private MultiText gloss() {
        return entry.getSenses().getFirst().getGlosses();
    }

    @Test
    void attachingRecordsNothing() {
        assertEquals(0, history.entries().size());
        assertTrue(history.isAtSavePoint());
        assertFalse(history.canUndoProperty().get());
    }

    @Test
    void theChangesOfOneEventAreOneEntry() {
        LiftSense s = new LiftSense();
        s.addGloss(new Form("en", "to stroll"));
        entry.addSense(s);                      // reports the id given to the sense, then the addition
        entry.setOrder("1");
        endOfEvent();
        assertEquals(1, history.entries().size());
        assertEquals(HistoryEntry.Kind.CHANGE, history.entries().getFirst().kind());

        entry.setOrder("2");
        endOfEvent();
        assertEquals(2, history.entries().size(), "the next event makes another entry");
    }

    @Test
    void undoAndRedoRestoreTheDictionaryWithoutRecordingAnything() {
        gloss().formTextProperty("en").set("to amble");
        endOfEvent();
        history.undo();
        assertEquals("to walk", gloss().getForm("en").orElseThrow().toPlainText());
        history.redo();
        assertEquals("to amble", gloss().getForm("en").orElseThrow().toPlainText());
        assertEquals(1, history.entries().size(), "the history's own changes are not recorded");
    }

    @Test
    void anActionIsOneLabelledEntry() {
        LiftSense[] made = new LiftSense[1];
        history.perform("add a sense", null, () -> {
            made[0] = d.getComponentBuilder().sense(entry).withGloss("en", "to march").build();
            made[0].getGlosses().formTextProperty("fr").set("marcher");
        });
        assertEquals(1, history.entries().size());
        HistoryEntry e = history.entries().getFirst();
        assertEquals(HistoryEntry.Kind.ACTION, e.kind());
        assertEquals("add a sense", history.label(e));
        history.undo();
        assertFalse(entry.getSenses().contains(made[0]));
    }

    @Test
    void anActionThatChangesNothingRecordsNothing() {
        history.perform("cancelled dialog", null, () -> {});
        assertEquals(0, history.entries().size());
    }

    @Test
    void aFailedActionIsRevertedAndNotRecorded() {
        assertThrows(IllegalStateException.class, () -> history.perform("fails", null, () -> {
            entry.setDateCreated("2026-10-14");
            throw new IllegalStateException("half done");
        }));
        assertTrue(entry.getDateCreated().isEmpty(), "the change made before the failure is reverted");
        assertEquals(0, history.entries().size());
        assertTrue(history.isAtSavePoint());
    }

    @Test
    void theSavePointFollowsUndoAndRedo() {
        entry.setOrder("1");
        endOfEvent();
        history.markSaved();
        assertTrue(history.isAtSavePoint());
        entry.setOrder("2");
        assertFalse(history.isAtSavePoint(), "modified as soon as the change is made");
        endOfEvent();
        history.undo();
        assertTrue(history.isAtSavePoint(), "undoing back to the save removes the *");
        history.undo();
        assertFalse(history.isAtSavePoint());
        history.redo();
        assertTrue(history.isAtSavePoint());
    }

    @Test
    void aNewChangeDiscardsWhatCouldBeRedone() {
        entry.setOrder("1");
        endOfEvent();
        history.markSaved();
        history.undo();
        entry.setOrder("other");
        endOfEvent();
        assertEquals(1, history.entries().size());
        assertFalse(history.canRedoProperty().get());
        assertFalse(history.isAtSavePoint(), "the saved state can no longer be reached");
        history.undo();
        assertFalse(history.isAtSavePoint(), "the original state was not the one saved");
    }

    @Test
    void theOldestEntriesAreDroppedBeyondTheLimit() {
        history.setLimit(3);
        for (int i = 0; i < 5; i++) {
            entry.setOrder(String.valueOf(i));
            endOfEvent();
        }
        assertEquals(3, history.entries().size());
        while (history.canUndoProperty().get()) history.undo();
        assertEquals("1", entry.getOrder().orElseThrow(), "the two oldest changes cannot be undone");
        assertFalse(history.isAtSavePoint());
    }

    @Test
    void whatIsTypedInAFieldIsOneEntry() {
        Object field = new Object();
        history.beginTyping(field);
        var text = gloss().formTextProperty("fr");
        text.set("m");
        text.set("ma");
        text.set("marcher");
        endOfEvent();
        history.endTyping(field);
        assertEquals(1, history.entries().size());
        assertEquals(HistoryEntry.Kind.TYPING, history.entries().getFirst().kind());
        history.undo();
        assertFalse(gloss().containsLang("fr"), "the form added by the first keystroke is gone too");
    }

    @Test
    void anotherFieldMakesAnotherEntry() {
        Object first = new Object();
        Object second = new Object();
        history.beginTyping(first);
        gloss().formTextProperty("fr").set("marcher");
        history.endTyping(first);
        history.beginTyping(second);
        gloss().formTextProperty("en").set("to march");
        history.endTyping(second);
        assertEquals(2, history.entries().size());
    }

    @Test
    void undoingWhileTypingStartsANewEntryAtTheNextKeystroke() {
        Object field = new Object();
        history.beginTyping(field);
        gloss().formTextProperty("fr").set("marcher");
        history.undo();
        gloss().formTextProperty("fr").set("aller");
        assertEquals(1, history.cursor());
        assertEquals("aller", gloss().getForm("fr").orElseThrow().toPlainText());
    }

    @Test
    void theFinisherChangesBelongToTheEntryTheyFinish() {
        history.setFinisher(e -> ModificationDates.touch(e, () -> "2026-10-14T12:00:00Z"));
        Object field = new Object();
        history.beginTyping(field);
        gloss().formTextProperty("en").set("to stroll");
        history.endTyping(field);
        assertEquals("2026-10-14T12:00:00Z", entry.getDateModified().orElseThrow());
        assertEquals(1, history.entries().size());
        history.undo();
        assertTrue(entry.getDateModified().isEmpty(), "undo restores the previous date");
    }

    @Test
    void theDateIsNotSetForAChangeOfTheHeader() {
        history.setFinisher(e -> ModificationDates.touch(e, () -> "2026-10-14T12:00:00Z"));
        d.getHeader().getNoteTypeManager().addFeature("usage");
        endOfEvent();
        assertTrue(entry.getDateModified().isEmpty());
    }

    @Test
    void aDeletionIsUndoneInPlace() {
        LiftSense second = d.getComponentBuilder().sense(entry).withGloss("en", "to go").build();
        LiftSense first = entry.getSenses().getFirst();
        endOfEvent();
        history.perform("delete", first, () -> entry.deleteSense(first));
        history.undo();
        assertSame(first, entry.getSenses().getFirst());
        assertSame(second, entry.getSenses().get(1));
    }

    @Test
    void attachingAnotherDictionaryForgetsTheHistory() {
        entry.setOrder("1");
        endOfEvent();
        LiftDictionary other = LiftDictionary.makeBuilder().withLiftVersion(LiftVersion.V0_15).build();
        history.attach(other);
        assertEquals(0, history.entries().size());
        entry.setOrder("2");
        assertEquals(0, history.entries().size(), "the old dictionary is no longer listened to");
    }
}
