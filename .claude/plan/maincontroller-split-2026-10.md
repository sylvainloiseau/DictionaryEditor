# Splitting MainController — plan (2026-10-13)

## Implementation status (2026-10-13)

Phases 0–6 are done; phase 7 (user-chosen columns) is left for later, as planned.
`MainController` is down from 6,298 to ~390 lines; no class is above 410 lines.

Checked with a UI-level harness, which:
- visits every navigation item;
- selects rows;
- clicks the editor panel's navigation buttons;
- deletes an entry, then undoes and redoes the deletion;
- compares a fingerprint of each view with the code before the split.

Every difference from that fingerprint is one of the fixes listed below. Also checked
with 20 new unit tests (`mvn test`).

How the implementation differs from the plan:
- **Editor services:** `ViewContext` became `editor/EditorContext`. It sits in `editor`, so
  that `view` depends on `editor` and not the reverse.
- **File menu:** the actions are in `app/FileActions`, and `DictionaryDocument` only holds
  the state.
- **Configuration views:** they are `view/header/*View`, and `CategoryViews` builds the six
  count views.
- **Column catalogs:** they are `table/catalogs/EntryColumns`, `SenseColumns`,
  `ExampleColumns` and `ComponentColumns`. The last one groups notes, variants, relations,
  etymologies, annotations and fields.
- **Not done:** `FeatureTree` is a helper, `EditorSupport.featureTree`, rather than a
  control.
- **Language views:** `getObjectText()` / `getMetaText()` are used *by role*. Each view
  also takes the forms of the other role's texts that are in a language belonging only to
  this view, so every form is listed once.

Bugs of §4:
- **Fixed:** 1–17, 20 (cached counts), 22 (facet "all" item) and 23 (dark theme style),
  plus the two below.
- **Open:**
  - 18 (undo back to the saved state): it needs undoable edits, because typing is not in
    the undo history.
  - 19 (a cancelled "+" button still marks the dictionary as modified).
  - 21 (the filter type is chosen once, when the view is built).

Bugs found during the work:
- 26. The Relations table's "target (form)" column was always empty: it looked an id up in
  a map keyed by UUID. It now uses `getRefObject()`.
- 27. lift-api's builders refuse incomplete components:
  - an entry without a form;
  - a sense without a gloss or definition;
  - an example without its text;
  - a reversal without a type.

  So "New entry", "+ Sense", "+ Sub-sense", "+ Example" and "+ Reversal" threw and did
  nothing. Each now asks for the required text or type first. In quick entry, a row with a
  grammatical info but no gloss no longer fails; its sense is skipped.


`ui/MainController.java` is 6,298 lines, with about 180 methods and 40 mutable fields.
It currently handles at least ten separate jobs:

| Concern | Methods (examples) | Approx. lines |
|---|---|---|
| File/document state, dirty flag, title, close | `markDirty`, `installDirtyTracking`, `confirmDiscardChanges`, `onSave*`, `onNewDictionary`, `openDictionaryFile`, `Handoff` | 350 |
| Recent files, preferences, theme, font | `saveRecentFile`, `refreshRecentMenu`, `showPreferencesDialog`, `applyTheme` | 250 |
| Navigation tree and view switching | `buildNavTree`, `rebuildHeaderCfgChildren`, `switchView`, `selectNavItem`, `keep*Subset*` flags | 300 |
| 13 object/category table views | `showSenseView` … `showFieldTypesView`, `showCategoryTable` | 1,600 |
| Column filters and search (two implementations) | `buildEntryFilterRow`, `applyCurrentFilter`, `refreshEntryFacetChoices`, `wrapTableWithFilters`, `rowMatches*` | 650 |
| Right-panel editors | `populate*Editor` (×14), `populateSummaryEditor`, delete/back buttons | 900 |
| Cross-object navigation | `navigateTo*`, `showObjectsWith*`, `showMatching*` | 450 |
| Header configuration views | ranges, field definitions, feature sets, languages | 900 |
| Queries over the dictionary | `countFeatureUsage`, `renameFeature`, `describeParent*`, `buildSearchText`, `collect*Rows` | 400 |
| Tools | validation, CSV export, demo loading | 350 |

The aim is a set of classes that each own one job, and a shared table/column model.
Each table would then be described as data rather than written as code. That is also what
makes user-chosen columns possible later.

---

## 1. Target architecture

```
fr.cnrs.lacito.liftgui
├── core/
│   ├── DictionaryService          (existing) + demo loading moved here
│   ├── DictionaryQueries          pure, no JavaFX: owningEntry, describeParentType, usage counts,
│   │                              renameFeature, traitValueTokens, translationOfType, searchText,
│   │                              objectsWithTrait/NoteType/FieldType/…
│   ├── DictionaryValidator        returns List<Issue>; the dialog only displays it
│   └── CsvExporter                takes a list of ColumnSpec + rows; no TableView needed
├── ui/
│   ├── MainController             FXML fields + @FXML handlers that delegate (~300 lines)
│   ├── app/
│   │   ├── DictionaryDocument     dictionary, file, demo, dirty as JavaFX properties;
│   │   │                          open/save/saveAs/new; window title binding; Handoff
│   │   ├── DirtyTracker           installDirtyTracking + isEditEvent
│   │   ├── RecentFiles            Preferences + menu rebuild
│   │   ├── AppPreferences         typed access to font/theme/path/locale prefs
│   │   ├── PreferencesDialog
│   │   └── Dialogs                showError / showInfo / confirm / choose (merge with EditorSupport.showError)
│   ├── nav/
│   │   ├── ViewKey (enum)         replaces the 25 NAV_* strings: i18n key, group, showsAddButton,
│   │   │                          hidesEditorPanel, searchable
│   │   ├── NavigationTree         builds the TreeView, keeps the item↔key map, select(key)
│   │   ├── ViewRequest (record)   key + optional subset + title + object to select
│   │   └── Navigator              go(ViewRequest), goTo(Object) (via owningEntry), back-to-parent
│   ├── view/
│   │   ├── DictionaryView         interface: Node build(ViewContext); onSearch(String); refresh()
│   │   ├── ViewContext            dictionary, document, editor panel, navigator, undo, count label
│   │   ├── ObjectTableView<T>     generic: rows supplier + ColumnCatalog<T> + editor presenter
│   │   ├── EntryView, QuickEntryView, LanguageFieldView, … (only views with specific behaviour)
│   │   ├── CategoryView           gram-info / translation / relation / note / field types, traits
│   │   └── header/  HeaderDescriptionView, FieldDefinitionsView, RangeView,
│   │               FeatureSetView, LanguagesView
│   ├── table/
│   │   ├── ColumnSpec<T>          one column definition (see §2)
│   │   ├── Columns                fluent factory: text(), multiText(), trait(), editable()
│   │   ├── ColumnCatalog<T>       all columns available for an object type
│   │   ├── ColumnLayout           which columns, order, widths (default + user-saved)
│   │   ├── TableFactory           builds TableColumns from catalog + layout + languages
│   │   ├── FilterableTable<T>     the single filter/search/facet implementation
│   │   ├── catalogs/              EntryColumns, SenseColumns, ExampleColumns, … (one per type)
│   │   └── cells/                 CommitOnFocusLossCell, FilterComboCell
│   ├── editor/
│   │   ├── EditorPanel            title, code, container; show(...), clear(), summary(map)
│   │   ├── EditorPresenters       show(Object) → right editor; one small method per type
│   │   ├── DeleteActions          confirm + undoable command for entry / sense / example / component
│   │   └── ComponentActions       the single ExtensibleAddActions implementation (builder-backed)
│   └── controls/                  (existing editors)
```

Dependencies only point downward:

`MainController → app, nav → view → table, editor → controls → core`.

No class below `MainController` sees the FXML fields. They receive what they need, such as
`EditorPanel`, through `ViewContext`.

### Main objects

* **`DictionaryDocument`** is the single source of truth for "which dictionary, which file, is
  it modified".
  * `dirtyProperty()` and `titleProperty()` let the window title use a binding instead of
    `updateWindowTitle()` calls scattered across the code.
  * Listeners on `dictionaryProperty()` replace the work done by hand in `setDictionary`:
    rebuilding the header nav and reconfiguring the entry columns.
* **`ViewKey` enum** replaces the string constants. It also replaces the `switch` and the
  separate `hideRightPanel` boolean list in `switchView`, because each constant carries its
  own flags:

  ```java
  enum ViewKey {
      ENTRIES("nav.entries", Group.OBJECTS, Flags.ADD_BUTTON),
      SENSES("nav.senses", Group.OBJECTS),
      …
      CFG_LANGUAGES("nav.cfgManageLangs", Group.CONFIG, Flags.NO_EDITOR_PANEL);
  }
  ```

  Ranges stay dynamic keys, as `ViewRequest.range(id)`.
* **`ViewRequest`** replaces these five flags: `entrySubsetOverride`, `entrySubsetTitle`,
  `keepEntrySubsetOnNextEntryView`, `senseSubsetOverride` and `keepSenseSubsetOnNextSenseView`.

  ```java
  record ViewRequest(ViewKey key, List<?> subset, String title, Object select) {}
  ```

  `refreshCurrentView()` becomes `navigator.go(currentRequest)`.
* **`EditorPanel`** wraps `editorContainer`, `editEntryTitle` and `editEntryCode`. Today 40+
  methods write to these three nodes directly.

  ```java
  panel.show(title, code)            // clears and sets the header
       .deleteButton(deleteAction)   // optional
       .backTo(parent)               // optional, via Navigator
       .add(editorNode);
  panel.summary(title, Map<String,String>) .accessButton(label, action);
  ```

---

## 2. Columns: replacing the long-argument builders and preparing user-chosen columns

### Current situation

* `col(title, extractor)` is used about 60 times. Around it, the same per-language loop is
  written out about 15 times, in these shapes:
  ```java
  TableColumn<X,String> group = new TableColumn<>(I18n.get(...));
  for (String l : metaLangs) group.getColumns().add(col(l, x -> x.getGlosses().getForm(l).map(Form::toPlainText).orElse("")));
  ```
* Settings are passed out of band. For example, `getProperties().put("filterMode", FILTER_MODE_TEXT)`
  marks a column as using a free-text filter.
* Several methods take long positional argument lists, which are hard to read at the call
  site:
  * `collectMtRows(rows, type, id, obj, mt, langs)`, which has 6 arguments;
  * `wrapTableWithFilters(table, (f,t)->updateCountLabel(f,t), searchField != null ? … : null)`;
  * `rowMatchesAllFilters` and `refreshEntryFacetChoices`, with 5–6 arguments each.

### Proposal

```java
/** One column the user can show for objects of type T. */
public record ColumnSpec<T>(
    String id,                       // stable, e.g. "sense.gloss" — used to save layouts
    String titleKey,                 // i18n key of the header
    LanguageScope languages,         // NONE, OBJECT, META: NONE = one leaf, else one leaf per language
    BiFunction<T, String, String> value,   // (row, lang) -> text; lang null when NONE
    FilterMode filter,               // FACET (combo), TEXT (free text), AUTO (decide from data)
    double prefWidth,
    BiConsumer<T, String> commit     // null = read-only; else (row, newText) for editable cells
) { … }
```

These records are created with a fluent builder, so a call site reads like a declaration:

```java
public final class SenseColumns {
    public static ColumnCatalog<LiftSense> catalog() {
        return ColumnCatalog.of("sense",
            Columns.<LiftSense>multiText("sense.parentEntry", "col.parentEntry", OBJECT,
                    s -> s.getParentEntry().getForms()).width(120),
            Columns.<LiftSense>text("sense.gramInfo", "col.gramInfo",
                    s -> s.getGrammaticalInfo().map(g -> g.getGramInfoValue().getId()).orElse("")).facet(),
            Columns.<LiftSense>multiText("sense.gloss", "col.gloss", META, LiftSense::getGlosses),
            Columns.<LiftSense>multiText("sense.definition", "col.definition", META, LiftSense::getDefinition).textFilter(),
            // available but hidden by default — the start of user-chosen columns:
            Columns.<LiftSense>trait("sense.trait", "col.trait", …).hidden(),
            Columns.<LiftSense>text("sense.id", "field.id", s -> s.getId().orElse("")).hidden()
        );
    }
}
```

* `TableFactory.build(table, catalog, layout, languages)` creates the `TableColumn`s.
  * A group column is created for each multi-language spec.
  * Each leaf column gets a `userData` record (`spec id` plus `lang`). This replaces the
    `"filterMode"` property and gives CSV export a proper header such as "Gloss [fr]".
* `ColumnLayout` is an ordered list of `(columnId, lang?, visible, width)`.
  * `ColumnLayout.defaults(catalog)` gives the columns shown today, so nothing changes visually.
  * Later, `ColumnLayoutStore` saves and loads a layout per object type in `Preferences`,
    under a key like `columns.sense`.
* `FilterableTable<T>` holds a `List<ColumnSpec>` and reads the specs, not the `TableColumn`s.
  * It builds its filter row from the leaves that are visible.
  * It rebuilds that row when the layout changes. That is what lets user-chosen columns work
    without touching any view.
* **Future column-chooser feature:**
  * Step 1, almost free: `table.setTableMenuButtonVisible(true)` gives a show/hide menu. It
    needs the filter row to follow column visibility, which `FilterableTable` handles.
  * Step 2: a "Columns…" dialog that lists the catalog, with checkboxes, ordering and a choice
    of languages. It writes a `ColumnLayout`.
  * Step 3: column catalogs for traits and fields. These would add one column per trait or
    field name declared in the header for that target, using
    `EditorSupport.traitDefinitions(header, target)`. This is the most requested kind of
    custom column in lexicography tools.

### Other long-argument call sites

| Today | Proposal |
|---|---|
| `collectMtRows(rows, type, id, obj, mt, langs)` and the 150 lines of hand traversal in `showLangFieldView` / `collect*RowsForSense` | `reg.getObjectText()` / `reg.getMetaText()` already list every MultiText (they are used by Manage Languages and Validate). One row per form becomes `MultiTextRow.of(mt, form)`, with the parent and type derived from `mt.getParent()`. This removes the traversal and the gaps listed in §4. |
| `wrapTableWithFilters(table, onCount, searchProp)` | `new FilterableTable<>(specs, rows).onCountChanged(…).searchText(prop)` |
| `rowMatchesAllFilters(row, leaves, inputs, textCols, clear, ignore)` | A method of `FilterableTable` that reads its own fields: `matches(row, ignoredColumn)` |
| `refreshEntryFacetChoices(q, leaves, clear, source, searchTextOf)` | Deleted. The entry table uses `FilterableTable` like the others. |
| `populateCategorySummaryEditor(title, row, "grammatical-info")`, which dispatches on a string | `CategoryView` gets a `Function<String, ViewRequest> drillDown` when it is constructed |
| `new DeleteEntryCommand(entry, idx, () -> dict, refresh, refresh)` and the copies for sense and example | `DeleteActions.entry(entry)`, `.sense(sense)`, `.example(ex)`, `.component(remove, restore)`: one confirm dialog and one refresh path |
| `se.setSense(sense, onAddAnnotation, annotationIds, this::createSenseAddActions, this::createExampleAddActions)` | `SenseEditor.Config` record (or builder) built once by `ComponentActions`, shared with `LiftEntryEditor` |

---

## 3. Duplication to remove

| Duplicated code | Copies | Replace with |
|---|---|---|
| Filter row, facet combos and search: entry-specific version vs `wrapTableWithFilters` | 2 full implementations (~450 lines) | `FilterableTable<T>` |
| Combo cell factory with a disabled "clear" item | 2 | `cells/FilterComboCell` |
| Per-language column loop | ~15 | `Columns.multiText(…)` |
| `ExtensibleAddActions` anonymous classes | 5 (3 in the controller, 2 in `LiftEntryEditor`) | `ComponentActions.forObject(obj, refresh)`: one class that switches on the capabilities of `obj` |
| `(name, mt) -> builder.annotation(mt, name).build()` | 3 | `ComponentActions.annotateMultiText()` |
| Delete confirm dialog plus clearing the panel | 4 | `DeleteActions` + `EditorPanel.clear()` |
| "Access objects" / "Go to parent" button | 5 | `EditorPanel.accessButton(label, action)` |
| Parent → owning entry `instanceof` chains (`navigateToObject`, `getParentEntryForms`, `showObjectsWith*`) | 6 | `DictionaryQueries.owningEntry(Object)` (already exists, but private) |
| Trait matching lambda in `showObjectsWithTrait` | 5 | `reg.getTraits().stream().filter(…).map(owningEntry)` |
| Counting by key into `Map<String, Long>`, then a category table | 5 | `CategoryView.of(rows, keyFn)` |
| `showError` / `showInfo` / `EditorSupport.showError` | 3 | `Dialogs` |
| "Add note" / "add relation" type choosers (ChoiceDialog or TextInputDialog fallback) | 3 (controller + `LiftEntryEditor`) | `Dialogs.chooseType(featureSet, allowFreeText)` |
| Reading a feature's first label/abbrev for display | 4 (`TraitEditor`, range tree, range editor, header nav) | `EditorSupport.featureLabel(Feature)` |
| Range-element tree building (`TraitEditor.showRangePickerDialog` and `buildRangeElementTree`) | 2 | `controls/FeatureTree` |

---

## 4. Bugs found

The **Certain** entries are confirmed by reading the code. The **Likely** entries are behaviour
I inferred but did not run.

### Certain

1. **Field definitions: Add/Delete throw.**
   * `wrapTableWithFilters` replaces the table's items with a `SortedList`.
   * `showHeaderFieldDefsView` then calls `table.getItems().remove(sel)`, and
     `showNewFieldDefDialog` calls `table.getItems().add(fd)`.
   * Both throw `UnsupportedOperationException`, so the definition is removed from or added
     to the header, but the UI fails and the table is not updated.
   * A new definition with a name that is already used is also dropped without any message.
2. **Quick entry: listeners pile up.**
   * `quickEntryTable` is a field, but every visit of the view adds another
     `selectedIndexProperty` listener.
   * After *n* visits, *n* listeners run on each row change, and each one holds the language
     sets that were current at its visit. Only the `created` flag prevents duplicate entries.
3. **Relation types: "(no type)" leads nowhere.** Relations with an empty type are counted
   under the translated label `placeholder.noType`. "Access objects" then searches for
   relations whose type id *equals that label*, so it finds none.
4. **Translation types view can throw an NPE on an untyped translation.** It calls
   `x.getId()` on the map key, while `translationOfType` guards against a null key, which
   suggests null keys can occur.
5. **Traits view shows a misleading parent type.** Rows are grouped by `name|value`, but the
   parent-type column keeps the type of the *first* occurrence. A trait used on both entries
   and senses shows only "Entries". (The note-types view groups by `type|parentType`, which
   is correct.)
6. **"Access objects" drops objects.**
   * `showObjectsWithTrait` only looks at entries, senses, examples, variants and
     etymologies. It misses traits on relations, notes, fields, reversals, pronunciations and
     so on.
   * `showObjectsWithNoteType` and `showObjectsWithFieldType` only look at entry and sense
     parents.
   * So the frequency shown in the table does not match what is opened.
   * It can also add a `null` entry (a variant or etymology without a parent), and it throws
     an NPE when a trait value is null (`traitValue.equals`).
7. **"Go to parent" does nothing for many parents.** `navigateToObject` has no branch for:
   * a `MultiText`, which is the parent of annotations added on a text;
   * a trait, a field, a pronunciation on a variant, or a note on an example.

   The button is shown but has no effect. `owningEntry` already solves this.
8. **Java class names appear in the UI.** `describeParentType` has no case for relations,
   pronunciations, reversals, fields, annotations, translations or illustrations. Users see
   "LiftRelation", "LiftReversal" and so on in the "Parent type" column.
9. **Annotation summary can throw an NPE.** `populateAnnotationSummaryEditor` uses
   `annotation.getType().getId()`. The table itself uses the null-safe `EditorSupport.featureId`.
10. **Examples table hides some translations.** Translation columns are built from the
    header's translation types only. Translations whose type is not declared, or that have
    no type, are not shown, and the search does not see them either.
11. **The language views miss texts.**
    * Object/meta language fields: the hand traversal omits etymology glosses, notes and
      annotations on variants, notes on relations, and so on. These texts *are* counted in
      Manage Languages, which uses `getObjectText()` / `getMetaText()`, so the two views
      disagree.
    * The parent id is not displayed as a column.
12. **Sense editor: "Add note" is unusable without declared note types.** It opens a
    `ChoiceDialog` with an empty list. "Add relation" has a free-text fallback for the same
    case.
13. **Demo dictionary leaves temporary files behind.** `tempDir.deleteOnExit()` on a
    non-empty directory does nothing, and the two files inside are not marked, so every start
    leaves files in the temp directory. The demo is also loaded directly instead of through
    `DictionaryService`.
14. **CSV export headers are ambiguous.** Only leaf headers are exported, so "Gloss/fr" and
    "Definition/fr" both appear as `fr`.
15. **Viewing a range can create it.** `showHeaderRangeView` creates the range when it does
    not exist (`builder.range(rangeId).build()`). The header nav (ranges) is only rebuilt in
    `setDictionary`, so the ranges listed can go stale.
16. **Opening an entry's senses shows the wrong senses.** `applySenseTableFilterByEntry`
    filters the senses table by the entry's *form text*:
    * the senses of homographs are shown too;
    * if the form is not among the combo's values, nothing is filtered.

    It should use a subset (a `ViewRequest`).

### Likely or minor

17. **New and deleted objects don't show up in tables right away.** The generic tables are
    snapshots: `wrapTableWithFilters` copies the items. Objects created from the editor
    panel, for example a sub-sense, appear only after the view is rebuilt, and the code works
    around this with `refreshSenseTableAndFilters` / `refreshCurrentView`. Deleted objects
    stay listed until the refresh.
18. **Undo back to the saved state keeps the `*`.** `UndoManager` could record a save point.
19. **Cancelled "+" buttons still count as edits.** Clicking any button in the editor
    (for example "+ Note") marks the dictionary as modified even if its dialog is then
    cancelled.
20. **Performance on large dictionaries:**
    * the annotation frequency column is O(n²): each cell rescans all annotations;
    * the field-definition usage column rescans fields and traits for every cell;
    * facets are recomputed for every column on every key stroke (columns × rows ×
      `cellText`).
21. **The filter type (text vs combo) is chosen once from the initial data.**
22. **The clear-filter value is the translated "(all)" label.** A real value equal to it
    cannot be filtered on. `null` or a sentinel object would be safer.
23. **The dark theme only works by accident.** `dark.css` does not exist, so the dark theme
    is an inline style appended to the root. It is only removed when switching back to light
    because `applyFontSize` overwrites the whole style first.
24. **`createEntriesFromQuickTableRow` checks `currentDictionary == null` after using it.**
25. **Two parallel sets of constants exist.** `Keys.NAV_QUICK_ENTRY` and the local
    `NAV_QUICK_ENTRY` both exist, which is confusing. The `ViewKey` enum makes this go away.

---

## 5. Suggested improvements (beyond the split)

* **Live tables.** Feed `ObjectTableView` from the registry's observable lists, as is
  already done for entries with `bindContent`, instead of copying them. This fixes bug 17
  and removes most of the manual refreshes.
* **Unit tests.** There are no tests in the project. The pure classes extracted in phase 1
  are the first ones worth testing: `DictionaryQueries`, `DictionaryValidator`,
  `CsvExporter`, `ColumnSpec` / `TableFactory` and `renameFeature`. A small TestFX smoke test
  could then cover the dirty-flag and close-prompt behaviour.
* **Caching in category views.** Compute counts once per view build, in a
  `Map<Feature, Long>`, instead of in each cell.
* **Consistent drill-down.** Gram-info drills down to *senses*, but translation types drill
  down to *entries*. With `ViewRequest`, each category can open the right object table:
  * translation types → examples;
  * relation types → relations;
  * note types → notes;
  * field types → fields.
* **One "type chooser".** `Dialogs.chooseType(featureSet)` would allow a new id to be added
  on the spot, written to the header. This fixes bug 12 and the inconsistency between the
  note and relation dialogs.
* **Fewer inline styles.** Move the inline styles (`#e74c3c` delete button, `#eef2f3`
  filter row, title labels) to `app.css`. That is also the step needed for a real
  `dark.css`.

---

## 6. Steps (each one compiles and leaves behaviour unchanged unless stated)

**Phase 0: quick bug fixes in place (before moving code).**
Bugs 1, 2, 3, 4, 8, 9, 12, 13 and 24. Each is a change of a few lines.

**Phase 1: extract the pure parts (no UI change).**
1. `core/DictionaryQueries`: `owningEntry`, `describeParent*`, usage counts,
   `renameFeature`, `traitValueTokens`, `translationOfType`, `buildSearchText`, `entryLabel`.
2. `core/DictionaryValidator` (returns issues) and `core/CsvExporter`.
3. Demo loading moved to `DictionaryService.loadDemo()`.
4. `app/RecentFiles`, `app/AppPreferences`, `app/PreferencesDialog`, `app/Dialogs`.
5. `QuickEntryRow` and `CommitOnFocusLossCell` moved to their own files.

**Phase 2: document state.**
6. `app/DictionaryDocument`: dictionary, file, demo and dirty as properties; open, save,
   save as, new and confirm-discard; window title binding; handoff.
7. `app/DirtyTracker`.

**Phase 3: the table model.**
8. `table/ColumnSpec`, `Columns`, `ColumnCatalog`, `ColumnLayout` (defaults only) and
   `TableFactory`.
9. `table/FilterableTable`, merging both filter implementations. The entry table switches
   to it, with its custom search text supplied as a `Function<T,String>`.
10. One catalog per type (`EntryColumns`, `SenseColumns`, …) reproducing today's columns
    exactly. CSV export uses spec titles (fixes bug 14).

**Phase 4: views and navigation.**
11. `nav/ViewKey`, `NavigationTree`, `ViewRequest` and `Navigator` (fixes bugs 7 and 16;
    subsets replace the `keep*` flags).
12. The `DictionaryView` interface and `ObjectTableView<T>`. Move the views one at a time.
    Simple object tables (senses, examples, notes, variants, relations, etymologies,
    annotations, fields) become ~20-line configurations. Entry, quick-entry and language
    views keep their own classes.
13. `CategoryView` for the five count tables and traits (fixes bugs 5 and 6; drill-down via
    `ViewRequest`).
14. Header views moved to `view/header/`.

**Phase 5: editor panel.**
15. `editor/EditorPanel`, `DeleteActions` and `ComponentActions`. `LiftEntryEditor` uses
    `ComponentActions` too, so the remaining anonymous `ExtensibleAddActions` classes go away.
16. `editor/EditorPresenters` (the `populate*Editor` methods).

**Phase 6: improvements.**
17. Live tables (bug 17), an undo save point (bug 18), cached counts (bug 20), CSS cleanup.

**Phase 7: user-chosen columns (future).**
18. `ColumnLayoutStore` in Preferences, then `setTableMenuButtonVisible(true)`, then a
    "Columns…" dialog, then trait and field column catalogs driven by the header definitions.

Expected size afterwards:
* `MainController`: ~300 lines;
* no class above ~500 lines;
* the largest classes would be `FilterableTable` (~350), `EntryView` (~300) and
  `RangeView` (~250).
