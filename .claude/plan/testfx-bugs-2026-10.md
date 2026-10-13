# Bugs found by the TestFX tests — plan (2026-10-13)

## Test setup

**Dependencies** (`pom.xml`, test scope): `testfx-core` and `testfx-junit5` 4.0.18, and
`openjfx-monocle` 21.0.2 for running without a window.

**Surefire configuration:**
- the tests run on the class path (`useModulePath=false`), because TestFX and Monocle are
  not modules;
- in-memory preferences (`testsupport/InMemoryPreferences`), so the tests never read or
  write the user's recent files or settings;
- headless Monocle, with a 1920×1200 virtual screen.

**Running them:** `mvn test` runs everything: 80 tests in about 2 minutes, 73 passing and
7 disabled (see below). To watch the UI tests in real windows, override `headless` and
`glass.platform` (`-Dheadless=false -Dglass.platform=Mac`). Not tried yet.

**UI tests** (`src/test/java/.../ui/fx`): `MainWindowTest` opens the main window on the
demo dictionary, in English. It provides lookups, dialog helpers and `fireMenu`. It fails a
test when an exception is thrown on the JavaFX thread, which is how B3 was found.

| Class | What it covers |
|---|---|
| `StartupTest` | demo dictionary, entries view, first entry shown |
| `NavigationTest` | every navigation item, the View menu, editor panel and search visibility, browsing is not an edit |
| `SearchAndFilterTest` | search, facet and text filters, "Reset filters", filters are not edits |
| `EditingTest` | typing marks the document modified; the table follows; table cell editing; modification date |
| `CreationTest` | new entry, + Sense, + Example, + Sub-sense, + Note, + Reversal, Paste |
| `DeletionTest` | delete entry / sense / example / note; undo, redo and the shortcut; referenced entries refused |
| `CrossNavigationTest` | back buttons, "Open in Senses view", example links, "Go to parent", drill-downs |
| `QuickEntryTest` | "Create entries"; rows without gloss |
| `DocumentTest` | close with and without changes (Cancel / Discard), File → New, Validate |
| `HeaderConfigurationTest` | note types, field definitions, range elements, languages |

**Not covered:** the actions that open native file choosers (Open, Save as, Export CSV), and
the change of interface language. That change reloads the scene through `MainApp`'s static
stage.

**About the undo shortcut:** a first test of ⌘Z / Ctrl+Z failed, but that was the headless
robot, which does not send modifier keys. The accelerators work: the test now sends the key
event directly (`MainWindowTest.pressShortcut`).

Each bug below has a test marked `@Disabled("Bug Bn of this plan…")`. I checked that each
one fails on the current code. After a fix, remove the `@Disabled` line. To run the disabled
tests anyway:
`mvn test -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'`.

---

## Bugs

### B1. A cancelled action still marks the dictionary as modified
*Test:* `DeletionTest.cancellingTheConfirmationLeavesTheDictionaryUnmodified`.

**What happens:** clicking "Delete" then "Cancel" leaves the `*` in the title, and closing
the window then asks to save. The same happens for every button whose dialog is cancelled
("+ Note", "+ Example", "New entry"…).

**Cause:** `DirtyTracker` treats every button action in the editor and table panels as an
edit, before knowing whether anything changes. This is bug 19 of the split plan.

**Suggested fix (either):**
1. **Mark the document modified where the change happens.**
   - `DirtyTracker` would ignore buttons and only watch typing and focused combo boxes or
     date pickers.
   - Each change would call `markDirty`:
     - `EditorContext.executeDeletion` and `recordExecuted` already do;
     - `BuilderAddActions` would take a "changed" callback and call it after `build()`;
     - `EditorPresenters` (the sense add buttons) and `LiftEntryEditor` (its add buttons,
       through `setOnChanged`) need it too;
     - the configuration views already call `ctx.markDirty()` on their changes.
2. **Better, if lift-api can offer it:** a dictionary-wide change notification, for example a
   modification counter or an event on the registry. `DictionaryDocument` would then derive
   `dirty` from the model itself, and `DirtyTracker` could be removed. This also fixes edits
   made by code paths that `DirtyTracker` cannot see.

### B2. Quick entry: leaving a filled row does not create its entry
*Test:* `QuickEntryTest.leavingAFilledRowCreatesItsEntryOnce`.

**What happens:** the view promises that an entry is created when the user leaves a filled
row. But moving to the next row by clicking (or tabbing into) its fields creates nothing;
only "Create entries" works.

**Cause:** the cells' text fields take the mouse clicks, so the table's selection never
changes; the selected index stays at -1. `QuickEntryView` only listens to the selection.

**Suggested fix:** in `CommitOnFocusLossCell`, when its text field gains the focus, select
its row:
```java
textField.focusedProperty().addListener((obs, was, is) -> {
    if (is && getTableView() != null) getTableView().getSelectionModel().select(getIndex());
});
```
The previous field loses the focus first, so its text is already committed when the
selection listener creates the entry of the row left.

### B3. "+ Reversal" fails when the header declares no reversal type
*Test:* `CreationTest.theSenseEditorAddsAReversal`.

**What happens:** with the demo dictionary, whose inverse types are empty, the dialog asks
for a free type. Then `ReversalBuilder.withType` throws `No feature with id … in feature set
'inverse-type'`, and nothing is created. This regression comes from commit 26208cd: the
free-text fallback suits notes and relations, whose builders declare new types themselves,
but not reversals.

**Suggested fix (either):**
- in `EditorPresenters.senseAddButtons`, declare the type before building it:
  ```java
  FeatureSet types = header.getInverseTypeManager();
  if (!types.hasFeature(type)) types.addFeature(type);
  ```
- or disable "+ Reversal" with a tooltip when no type is declared, as `LiftEntryEditor` does
  for relations.

Also worth reporting to lift-api: the message of a reversal without a type says "Relation
must have a type".

### B4. A new entry hidden by the search is not shown
*Test:* `CreationTest.aNewEntryHiddenByTheSearchIsShownAnyway`.

**What happens:** with a search text active, "New entry" (or Paste) creates the entry but
the search hides it. The editor panel keeps showing the previously selected entry, so the
user's next edits go to the wrong entry.

**Cause:** `EntryView.onAdd` and `pasteAsNewEntry` call `table.select(entry)`, which returns
false when the row is filtered out. The result is ignored.

**Suggested fix:** when `select` returns false, call `table.resetFilters()` and select again.
In any case, call `ctx.editors().showEntry(entry)`, so the panel always shows the new entry.

### B5. Counts are formatted in the system's locale, and "1 rows"
*Test:* `StartupTest.countsFollowTheInterfaceLanguage`.

**What happens:** with an English interface on a French system, the count reads "2 101
rows" (with a narrow no-break space). A single row reads "1 rows shown out of …".

**Cause:** `I18n.get(key, args)` uses `MessageFormat.format`, which formats numbers in the
JVM's default locale, not the interface's.

**Suggested fix:**
- format with the interface locale:
  ```java
  return new MessageFormat(get(key), currentLocale).format(args);
  ```
- use choice formats in `table.count.*`, for example in English
  `{0,choice,0#no row|1#1 row|1<{0,number,integer} rows}`, and the same in French.

### B6. Clicking the highlighted navigation item does not leave a subset
*Test:* `NavigationTest.clickingTheHighlightedItemShowsAllObjectsAgain`.

**What happens:** after "Open in Senses view" (the senses of one entry) or a drill-down
("Access objects"), the navigation tree highlights "Senses" or "Entries". Clicking that item
does nothing, so the only way back to the whole list is to visit another view first.

**Cause:** `NavigationTree` only reacts to changes of the selected item, and clicking the
item already selected is not a change.

**Suggested fix:**
- in the tree cells, handle a click on a leaf that is already selected: when the current
  request is not that item's request (it has a subset or a range), call
  `onSelect.accept(request)`;
- or don't highlight any item while a subset is shown (`NavigationTree.select` would clear
  the selection for subset requests).

The first keeps the visual cue; it needs the navigator's current request
(`Navigator.current()`).

### B7. A deleted object stays listed in a subset
*Test:* `DeletionTest.deletingFromASubsetRemovesTheRow`.

**What happens:** in a drill-down (for example "Trait: morph-type = stem", 1,870 entries),
deleting an entry leaves it in the table. Selecting it shows an entry that is no longer in
the dictionary, and edits to it are lost.

**Cause:** `ObjectTableView.rows(subset, …)` copies the subset. After the deletion,
`navigator.refresh()` rebuilds the view from that copy, which still holds the entry.

**Suggested fix:** keep the rows live, filtering the registry's list by membership in the
subset:
```java
Set<Object> members = Collections.newSetFromMap(new IdentityHashMap<>());
members.addAll(subset);
return new FilteredList<>(all, members::contains);
```
A deleted object then disappears at once, and an undo brings it back. Two consequences:
- the rows follow the registry's order rather than the subset's;
- the `refresh()` after a deletion becomes unnecessary for tables, though it is still needed
  to clear the editor panel.

### B8. Untranslated texts in the French interface
**Not tested:** this is a review of `messages_fr.properties` against the English file.

About 15 French values are still the English text. Many identical values are right ("Notes",
"Source", "Type", "Description"…), but these are not:

| Key | English (current French) | Suggested French |
|---|---|---|
| `nav.fields` | Field | Champs |
| `nav.gramInfo` | Grammatical info | Info. grammaticale |
| `col.form` | Form | Forme |
| `col.gloss` | Gloss | Glose |
| `col.gramInfo` | Gram. Info | Info. gram. |
| `col.name` | Name | Nom |
| `col.value` | Value | Valeur |
| `col.when` | When | Quand |
| `cfg.kindField` | Field | Champ |
| `cfg.optionRange` | Option-range | Plage d'options |
| `cfg.autoAdded` | Automatically added by … | Ajouté automatiquement par l'application … |
| `editor.reversals` | Reversals | Index inversés |
| `btn.addReversal` | + Reversal | + Index inversé |
| `mtkind.relationUsage` | relation (usage) | relation (usage) — to check |
| `mtkind.reversal` | reversal | index inversé |

---

## Suggested order

1. **B3, B4, B7:** they lose or misplace the user's work; each is a small change.
2. **B2, B6:** behaviour the UI promises but doesn't deliver.
3. **B1:** choose between explicit `markDirty` calls and a lift-api change event; the second
   is the better long-term fix.
4. **B5, B8:** wording and formatting.
