# A complete undo system: plan (2026-10-13, updated 2026-10-14)

## Goal

Every change the user makes to the dictionary can be undone and redone, in order, from a
single history:
- typing in any text field, and editing table cells;
- choices in combo boxes, date pickers, check boxes and the trait value picker;
- creating and deleting components (entries, senses, examples, notes, traits…);
- the dictionary configuration (languages, types, ranges, field definitions, renames).

The `*` in the title follows from the history: it disappears when the user undoes back to
the last save.

**Not in scope:** undo across sessions (the history is not saved), and undo of file
operations (open, new, save as).

**Design decided:** lift-api reports every change of a dictionary as a reversible event;
DictionaryEditor builds its undo history on these events. **The lift-api side is
implemented** (`../lift-api/.claude/plan/observable-model-and-changes-2026-10.md`,
section 6), and DictionaryEditor already compiles and passes its tests against it.
Section 7 keeps the comparison that led to this choice.

---

## 1. Current state

| Part | What it does today |
|---|---|
| `undo.UndoManager` | Two stacks of `UndoableCommand` (`undo()`, `redo()`); `canUndo`/`canRedo`. No labels, merging, transactions, limit or save point. |
| Commands | `DeleteEntryCommand`, `DeleteSenseCommand`, `DeleteExampleCommand` (they restore the position), `DeleteComponentCommand` (the component comes back **at the end** of its list). |
| `ComponentDeletion` | A static "undo sink" for the editors in `ui.controls`. |
| Modified flag | `DirtyTracker` (typing, focused controls), `markDirty()` calls, `EditEvent`. Independent of the history. |
| How edits reach the model | Text fields are bound bidirectionally to lift-api properties; the other editors call setters and builders directly, from about 30 classes. Nothing records them. |
| Shortcuts | ⌘Z / Ctrl+Z and ⌘Y / Ctrl+Y are scene accelerators. A focused text field's built-in undo takes ⌘Z first: two separate histories. |
| Modification date | `EditorPanel.touchEditedEntry`, called by `DirtyTracker`. |
| lift-api | Adapted to the new API (variant type range, `removeFieldOrTraitDefinition`, refused data-model change shown to the user). The change stream is there, unused. |

---

## 2. What lift-api provides

All in the exported package `fr.cnrs.lacito.liftapi.change`; its `package-info` states the
rules.

**Listening.** `LiftDictionary.addChangeListener(Consumer<? super ModelChange>)` returns a
`javafx.util.Subscription`. The sealed `ModelChange` (`dictionary()`, `subject()`,
`revert()`, `reapply()`) has these records:

| Event | Fields | Reported for |
|---|---|---|
| `ValueChanged<T>` | `owner`, `property` (`ModelProperty<T>`), `oldValue`, `newValue` | a value: dates, ids, order, type, href, source, trait value, form text (`FORM_TEXT`, the form's markup), annotation value/who/when, feature id, definition attributes… |
| `ComponentAdded` | `parent`, `component`, `position` (`OptionalInt`), `registered` | `addX`, the builders, `LiftDictionary.addEntry` (parent: the dictionary) |
| `ComponentRemoved` | `parent`, `component`, `position`, `unregistered` | `deleteX` (`unregistered`), `detach()` (not), `removeEntry` |
| `FormAdded` / `FormRemoved` | `text` (`MultiText`), `form` | `MultiText.add`, `removeForm`, and `formTextProperty(lang)` going from or to an empty text |
| `FeatureAdded` / `FeatureRemoved`, `FeatureSetAdded` / `FeatureSetRemoved`, `DefinitionAdded` / `DefinitionRemoved`, `LanguageAdded` / `LanguageRemoved` | the header or the languages | the configuration views |

`ModelProperty` has one constant per kind of value, with a stable `name()` for labels.

**Rules that shape the history** (all tested in lift-api):
- **Synchronous, after success.** A refused operation throws and reports nothing, and has
  changed nothing (adoption and release are all-or-nothing).
- **Every change is reported**, whatever code made it: a bidirectional binding, a table
  commit, a builder, a cascade. lift-api's random round-trip test checks it on the corpus:
  reverting every event gives back the loaded dictionary, reapplying them the edited one.
- **One gesture can report several events**, in order, which the history groups:
  - typing in a field bound to `formTextProperty(lang)`: `FormAdded` for the first
    character, a `ValueChanged` (`FORM_TEXT`, owner: the `Form`) per keystroke,
    `FormRemoved` when the text becomes empty;
  - adding a sense without an id: a `ValueChanged` (`LIFT_ID`) for the id the dictionary
    gives it, then the `ComponentAdded`;
  - adding a component whose forms use a new language: `LanguageAdded`, then
    `ComponentAdded`;
  - replacing a grammatical info or a reversal's main: the removal of the old one, then the
    addition;
  - a move: `ComponentRemoved` (`unregistered` false), then `ComponentAdded`
    (`registered` false).
- **`revert()` and `reapply()` go through the public API and report their own events.**
  The history must ignore what it causes.
- **A listener must not change the dictionary** while an event is delivered: lift-api
  throws `IllegalStateException`. The history can't react to an event with a change (see
  3.6).
- An exception thrown by a listener is rethrown to the code that made the change, after
  the other listeners ran.
- **Loading reports nothing**, and the dictionary is only handed out once loaded.
- **`ChangeRecorder`** (`dictionary.recordChanges()`): records a block's changes and
  reverts them on `close()` unless `commit()` was called. Recorders nest.
- **Cost:** negligible per edit; nothing while no dictionary is observed.

**From a text change to its component:** a `FORM_TEXT` change has the `Form` as owner;
`Form.getMultiText()` gives its multitext, and `MultiText.getParent()` the component (for
labels like "Typing in Gloss (fr)" and for `Reveal`).

Consequences for the application:
- **No change can escape the history.**
- The editors keep changing the model as they do today. They don't need a gateway or a
  migration.

---

## 3. Architecture

### 3.1 Packages

```
fr.cnrs.lacito.liftgui.undo        (no controls: lift-api and javafx.beans only)
    HistoryEntry       one undo step: a label, a subject, the ModelChanges in order
    History            listens to the dictionary, groups, merges, undo/redo, save point, limit
    UserAction         opens a labelled group: try (var a = history.action("…", subject)) {…}

fr.cnrs.lacito.liftgui.ui.undo     (JavaFX controls)
    UndoKeys           routes ⌘Z / ⇧⌘Z / Ctrl+Y to the history, also from text fields
    TypingSessions     tells the history which field is being typed in (merge key)
    Reveal             after undo/redo, shows the object it affected
```

The current `undo` package (`UndoManager`, the four commands, `UndoableCommand`) is
replaced, and `ComponentDeletion` loses its static sink.

### 3.2 History entries

```java
public record HistoryEntry(String label, Object subject, List<ModelChange> changes,
                           Object mergeKey) {
    void undo()  { for (changes, in reverse) change.revert(); }
    void redo()  { for (changes, in order)   change.reapply(); }
}
```

An entry refers to model **objects** through its changes, never to ids, so a rename can't
break it.

### 3.3 How changes become entries

The `History` subscribes to the dictionary's changes. Each incoming `ModelChange` goes:
1. **Into the open user action, if any.** A `UserAction` is opened by the UI around one
   user gesture (a button, a dialog, a menu item, a drop). Everything that happens until it
   closes is one entry, with the action's label and subject. Actions can nest; only the
   outer one makes an entry.
2. **Otherwise, into a typing entry**, when a typing session is active (3.5) and the change
   is one typing makes in that field: a `ValueChanged` of `FORM_TEXT` on a form of the
   field's multitext, or a `FormAdded`/`FormRemoved` in it (the first character, an
   emptied field), or a text value (`SOURCE`, `ANNOTATION_VALUE`…) of the field's owner.
3. **Otherwise, into an entry of its own**, with a label derived from the event (for
   example "Change of Gloss (fr)", from the `ModelProperty` and the language).
   - The entry stays open until the JavaFX event being handled is over: the first change
     schedules its closing with `Platform.runLater`. So a cascade (section 2) made by one
     call is one entry, even without an action.
   - Nothing is lost, even from a path that opens no action.
   - In the tests, such entries are reported with the call site, so that a proper action
     can be added.

**Changes made by the history itself** (during `revert`/`reapply`) are ignored: lift-api
reports them, but they aren't new user changes. The history sets a flag around its own
calls.

**If an action fails** (an exception escapes the gesture), the changes it collected are
reverted, with the flag set, so the model is never left half-changed and the history
records nothing. A single refused lift-api operation needs nothing: it changed nothing.
`UserAction` can be written on lift-api's `ChangeRecorder` (commit on success, close
reverts otherwise); the history then routes the recorder's changes into one entry.

**Labels derived from events** (case 3, and the typing label) come from a pattern switch
over the sealed `ModelChange`: the `ModelProperty` name, the kind of component, and for a
text, the multitext's role and language (`Form.getMultiText()`, then `MultiText.getParent()`).

### 3.4 The history

```java
public final class History {
    UserAction action(String label, Object subject);   // try-with-resources
    void undo();  void redo();
    void breakMerge();
    void markSaved();
    void attach(LiftDictionary d);                     // clear, and subscribe to a new dictionary
    ReadOnlyBooleanProperty atSavePointProperty();
    ReadOnlyObjectProperty<Optional<String>> undoLabelProperty(), redoLabelProperty();
}
```

**Rules:**
- **One list and a cursor.** Recording a new entry discards the "redo" part of the list.
- **Save point:** the cursor position at the last save. `dirty = cursor != savePoint`. If
  the save point is discarded (a new entry after undoing past it, or the limit), the
  document stays modified until it's saved.
- **Limit:** 1,000 entries (decided), set in the preferences; the oldest are dropped.
- **Errors during undo** (lift-api refuses a revert): the changes already reverted are
  reapplied, the entry stays where it is, and the user sees the error.
- **JavaFX thread only**, which the history asserts.

### 3.5 Typing

- **Granularity (decided): one entry per focus session of a field.**
- `TypingSessions` watches the focus of text inputs in the editor and table panels. While
  a field has the focus, its session id is the merge key of the text changes it causes.
  - All keystrokes in that field merge into one entry: the first old value and the last
    new value.
  - Its label is "Typing in Gloss (fr)".
- Merging stops when the field loses the focus, on any other entry, on undo or redo, and
  on save.
- A finer policy (per word, after a pause) could be added later inside `TypingSessions`
  only.

### 3.6 The modification date

- **Decided: undo restores the previous date.**
- The update of an entry's `dateModified` is an ordinary `ValueChanged`, recorded in the
  same history entry as the change it follows, so undo restores it with the rest.
- **It can't be made from the change listener** (lift-api refuses changes during delivery).
  It is made at the end of the gesture instead, while the entry is still open:
  - a user action: just before it closes;
  - a typing session: when it closes (focus lost, undo, save);
  - a change outside both (3.3, case 3): when its entry closes (the `Platform.runLater`
    of 3.3), appended to that entry.
- The history knows which entries a group touched from its events (the component, or the
  `Form` → multitext → component, up to the `LiftEntry`). A change of `DATE_MODIFIED` itself
  touches nothing.
- `EditorPanel.touchEditedEntry` and its call from `DirtyTracker` go away.

### 3.7 Shortcuts and menu

- **Decided: a single history.**
- `UndoKeys` is a scene event filter for ⌘Z / Ctrl+Z, ⇧⌘Z / Ctrl+Shift+Z and Ctrl+Y.
  - It closes the current typing session, calls the history, and consumes the event
    before a text field's built-in undo sees it.
  - It also adds the macOS redo shortcut.
- The menu items and the toolbar tooltips show the label: "Undo typing in Gloss (fr)",
  "Redo delete sense".

### 3.8 Showing what changed

- **Decided: show the changed object**, even if the view changes.
- After an undo or redo, `Reveal` takes the entry's subject (the action's subject, or the
  first event's `subject()`, brought back to a component: a `Form` through its multitext, a
  `MultiText` through `getParent()`, a header object to its configuration view):
  - if the current view lists it, it selects it;
  - otherwise it navigates to its entry (`Navigator.goToEntryOf`), as the cross-navigation
    links already do.
- The editor panel is rebuilt (`EditorPresenters.redisplay`).
- Tables follow on their own (live rows since B7).
- With a fully observable model, most of the manual `refreshTable()`/`redisplay()` calls
  after edits can go too.

### 3.9 Life cycle

| Event | History |
|---|---|
| Open, New, demo, language change (scene reload) | `attach(dictionary)`, once the dictionary is loaded (loading reports nothing anyway) |
| Save, Save as | `breakMerge()`, `markSaved()` |
| Close with changes | asks to save when `!atSavePoint` |
| Quick entry | each created row is one action ("New entry 'x'"); "Create entries" is one action for all the rows |
| Validation fixes, imports, batch changes | one action each |

The history belongs to `DictionaryDocument`, next to the dictionary it describes.
`DictionaryDocument.dirty` becomes a binding on `atSavePoint`.

### 3.10 Labels: where actions are opened

User actions are opened at the gesture's entry point, which is a much smaller set than the
places that change the model:
- the add and delete buttons (`BuilderAddActions`, `EditorPresenters`, `LiftEntryEditor`,
  `DeleteActions`);
- the toolbar's "New entry" and paste (`EntryView`);
- quick entry;
- the header views' buttons (`LanguagesView`, `FeatureSetView`, `RangeView`,
  `FieldDefinitionsView`);
- the trait picker, the morph-type and grammatical-info combos.

Each becomes `try (var a = history.action(I18n.get("undo.newEntry", form), entry)) { … }`.
A missed one still works (3.3, case 3); it only has a generic label.

---

## 4. Libraries

No runtime library is needed:
- **UndoFX** depends on ReactFX, and neither is maintained.
- **`javax.swing.undo`** would bring Swing in.

Modern Java covers the rest: records for the entries, pattern matching over the sealed
`ModelChange` for labels, try-with-resources for actions. The decisive property test
("revert all, the file is unchanged") lives in lift-api, next to the operations.

---

## 5. Phases

The lift-api prerequisite is done.

1. **History on the change stream:**
   - `History`, `HistoryEntry`, `UserAction`;
   - one entry per event (no grouping yet);
   - undo/redo through `revert`/`reapply`;
   - the existing deletion commands (`DeleteEntryCommand`, `DeleteSenseCommand`,
     `DeleteExampleCommand`, `DeleteComponentCommand`) and `ComponentDeletion`'s sink are
     removed: the stream reports the deletions, with their position;
   - unit tests on an in-memory dictionary: save point, limit, the redo part discarded,
     errors during undo, the history's own changes ignored, a failed action leaves
     nothing. Like lift-api's `ChangeRoundTripTest`, a test can apply random edits and
     check that undoing everything gives back the loaded dictionary.
2. **The `*` from the history:**
   - `DictionaryDocument.dirty` bound to `atSavePoint`;
   - `DirtyTracker`, `EditEvent` and `markDirty()` are removed: nothing escapes the
     stream, so they're no longer needed as a fallback.
3. **Shortcuts and typing:**
   - `UndoKeys`, `TypingSessions`, merging;
   - TestFX:
     - type, undo, and the text is back;
     - one undo removes a whole typing session;
     - ⌘Z in a focused field undoes in the history;
     - undo back to the save point removes the `*`;
     - undo of a text with `<span>`s shows it correctly in the field (see section 6).
4. **User actions and labels** at the entry points of 3.10; the menu labels; the date
   update (3.6). TestFX: an addition with its dialog is one undo step; a cancelled dialog
   records nothing.
5. **`Reveal`**, and removal of the manual refreshes the live model makes unnecessary.
6. **Preferences:** the history limit (default 1,000).

Each phase leaves all the tests passing. Phases 3 to 5 are independent of each other.

---

## 6. Behaviour details

- **Undo while typing:** the session is closed first, then undone. The field shows the
  restored text and keeps the focus.
- **Empty text:** clearing a form removes it. lift-api reports `FormRemoved` with the
  `Form` object, so undo brings back the form with its annotations.
- **Texts with spans:** a text change is reported, and reverted, as the form's markup
  (`Form.getMarkup()`), so spans survive an undo. But a field bound to
  `formTextProperty(lang)` shows plain text, and that property follows `Form.textProperty()`,
  which holds the markup once the text was changed: after an undo, the field could show
  `<span…>` tags. To check in phase 3; the fix, if needed, is in lift-api (keep the
  multitext's text property in plain text).
- **Refused operations** (a used definition's data model, deleting a referenced entry…)
  throw and change nothing: the UI shows the message, and the history records nothing.
- **Cascades** (the cases listed in section 2) are reported change by change and grouped by
  the user action: one undo restores everything. A cascade outside an action is one entry too
  (3.3, case 3): "add a sense" is one step even without a label.
- **Memory:** entries keep removed objects alive (a deleted entry and its senses). With the
  limit, this stays small compared to the dictionary.

---

## 7. Why this design (kept from the discussion)

Two designs were compared:
- an **application gateway**, through which every edit of the application would go,
  recording how to reverse it;
- **reversible events from lift-api**, with the history in the application.

The second was chosen:
- **Completeness:** every change is seen, whatever code made it. With a gateway, a
  forgotten path is silently missed.
- **Where the inverses are written:** in lift-api, next to the operations they reverse
  (positions, maps keyed by type, registration). A gateway would mirror lift-api's
  internals in the application.
- **Work in the application:** the history, plus labels at the gesture entry points,
  instead of migrating about 30 classes away from bindings and setters.
- **lift-api becomes more consistent and more useful to other consumers:** fully
  observable, encapsulated, with transactions.

Observable properties alone would not have been enough. A deletion is several low-level
collection changes (the parent's list, the registry's indexes), and replaying their
inverses would bypass lift-api's registration. Undo needs the *operation*, which only
lift-api can report.

What can only live in the application: grouping by user action, labels, merging of typing,
the save point, the limit, shortcuts and navigation.

## 8. Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Granularity of typing | **One undo per focus session of a field** (recommended): predictable ("undo what I typed in this field"), simple, and the field is the unit the user sees. Finer policies can come later inside `TypingSessions`. |
| 2 | Navigation after undo | **Show the changed object** (recommended): an undo the user can't see is indistinguishable from a bug, and could undo something unintended. Most editors scroll to the change for this reason. |
| 3 | ⌘Z in a text field | **The single history** (recommended): two histories would undo different things with the same keys, and a field's own history is lost when the editor is rebuilt. Since a typing session is one entry, ⌘Z in a field gives the expected result. |
| 4 | Modification date on undo | **Restore the previous date** (recommended): after an undo the entry is as it was. Otherwise undoing everything would still leave new dates, and the saved file would differ from the original. |
| 5 | Indexed `add(int, X)` in lift-api | **Done** in lift-api, for list-held components; map-held ones (notes, fields, forms…) are put back under their key. |
| 6 | History limit | **Agreed: 1,000 entries**, adjustable in the preferences. |
| 7 | Where the logic lives | **lift-api reports reversible changes; the history stays in the application** (section 7). lift-api side done. |
| 8 | Date update | **At the end of the gesture** (3.6), since a listener can't change the dictionary. |

---

## 9. Implementation (2026-10-14)

Phases 1 to 6 are implemented. 107 tests, all passing (81 before; new: `undo/HistoryTest`,
16 unit tests on an in-memory dictionary, and `ui/fx/UndoTest`, 10 TestFX tests).

**What is where:**
- `undo.History`, `undo.HistoryEntry`, `undo.ModificationDates` (the date finisher);
  `ui.undo.ChangeLabels`, `UndoKeys`, `TypingSessions`, `Reveal`.
- `DictionaryDocument` owns the history; `dirty` is "not at the save point". The history is
  handed over to a reloaded scene (language change) with the dictionary, so undo and the
  `*` carry on.
- Removed: `UndoManager`, `UndoableCommand`, the four `Delete…Command`s, `DirtyTracker`,
  `EditEvent`, `markDirty()`, `EditorPanel.notifyEdited`/`touchEditedEntry`, and the
  `restore` argument of `ComponentDeletion.delete`.
- Preferences: "Undo history length" (default 1,000).

**Where the implementation differs from the plan, and why:**
- **`history.perform(label, subject, Runnable)` instead of a try-with-resources
  `UserAction`.** A `UserAction` needs a `commit()` to tell success from failure, and a
  forgotten one would revert the user's work. `perform` commits on return and reverts on
  an exception.
- **Labelled actions only where they add something**: the deletions (`DeleteActions`,
  through `EditorContext.delete`), the new and pasted entries, quick entry. The other
  gestures (add buttons, header views, combos, the trait picker) are grouped by the
  "one JavaFX event" rule (3.3, case 3) and labelled from their events ("addition of note",
  "change of grammatical category"); a gesture spanning a modal dialog is still one entry,
  since the changes are made after the dialog closes.
- **The date of a typing session is set when the session ends** (focus lost, undo, save),
  as 3.6 says; `EditingTest` now leaves the field before checking it.
- **Reveal**: typing undone in the entry shown keeps the field and its focus (the fields
  are bound); anything else rebuilds the current view and selects the object, or shows its
  entry. A form removed by the undo leads to its text through the event.
- **Not done:** reporting the call site of unlabelled entries in the tests (3.3), and
  removing the manual `refreshTable()`/`redisplay()` calls (phase 5): both are clean-ups,
  with no visible effect.
