# Tables for the components that have none: plan (2026-10-16)

## Goal

Every kind of component of a dictionary has its own table in the "Objects" subtree of the
navigation panel, like the entries, senses, examples, notes, variants, etymologies,
relations and fields today. In each new table:
- selecting a row shows an **editor** of the component on the right;
- the editor has a **link to the parent component**: it opens the table of the parent's
  kind and selects the parent's row;
- the table offers what the existing tables offer (section 5).

---

## 1. Current state

| Kind | lift-api registry list | Table | Editor on the right |
|---|---|---|---|
| Entry, sense, example, note, variant, etymology, relation | yes | yes (Objects) | yes |
| Field | yes | yes (Objects) | **read-only summary** (`FieldEditor` exists) |
| Annotation | yes (`getAnnotations`) | yes, but under **Categories** | **read-only summary** (`AnnotationEditor` exists) |
| Trait | yes (`getTraits`) | no; Categories only counts trait *values* | no (`TraitEditor` exists, used inside other editors) |
| Pronunciation | yes | no | no (`PronunciationEditor` exists) |
| Reversal | yes | no | no (`ReversalEditor` exists) |
| Translation | yes | no | no (edited inside `ExampleEditor`) |
| Media | yes | no | no editor |
| Illustration | yes | no | no editor |
| Grammatical info | yes | no; Categories counts parts of speech | edited inside `SenseEditor` |

**Parent links today:**
- `addBackToParentButton` only knows an entry or a sense as parent.
- `addGoToParentButton` (annotations, fields) always opens the *entry*, not the parent.
- Etymologies have none.

**Code that every table already shares:**
- `ObjectTableView` (a `FilterableTable` with search, count, selection → editor);
- a column catalog per kind (`ComponentColumns`, `SenseColumns`…, built from `Columns`);
- `ObjectTableView.rows(subset, type, all)` (live rows, drill-down subsets);
- `EditorPresenters.showX` (header, delete button, parent link, editor);
- `DeleteActions.component`;
- `Views.create` (one `case` per kind).

---

## 2. Design

### 2.1 One description per component kind

Today each kind is spread over a `ViewKey`, a `case` in `Views.create`, a `case` in
`EditorPresenters.show`, and a `group(...)` entry in `NavigationTree`. Adding seven kinds
that way means seven times four edits.

Introduce `ui.view.ComponentKinds`, one record per kind:

```java
record ComponentKind<T extends AbstractLiftRoot>(
    ViewKey key,                                      // its view
    Class<T> type,
    Function<LiftDictionary, List<ColumnSpec<T>>> columns,
    Function<LiftDictionaryRegistry, ObservableList<T>> rows,
    String placeholder,                               // i18n key, "no pronunciation"
    BiConsumer<EditorPresenters, T> editor) {}        // shows one in the panel
```

with:
- `ComponentKinds.all()` in the order of the navigation tree;
- `ComponentKinds.of(Class<?>)` (the kind of an object, for parent links and `show`);
- `ComponentKinds.view(kind, request, ctx)` → `new ObjectTableView<>(...)`.

`Views.create`, `EditorPresenters.show(Object)`, the "Objects" group of `NavigationTree`
and the View ▸ Objects menu use it. The existing kinds move to it too (no behaviour change), so the
new ones are one line each, plus their columns and editor. Entries keep their own
`EntryView` (new entry, paste), which the description allows (an optional view factory).

### 2.2 Parent links

One method replaces `addBackToParentButton` and `addGoToParentButton`, for every editor:

```java
/** "↑ Sense: to walk" — opens the table of the parent's kind, on the parent's row. */
private void addParentLink(AbstractLiftRoot component)
```

with `Navigator.goToParentOf(component)`:
1. **The parent**:
   - `component.getParentNode()`;
   - for an annotation on a text or a form, the component that holds the text
     (`MultiText.getParent()`, `Form.getMultiText()`), named with the text's role
     ("gloss (fr) of sense …").
2. **Its view**: `ComponentKinds.of(parent.getClass())`. A parent kind without a table
   would climb to the next ancestor that has one; with this plan, every kind has one, so this
   is only a safety net.
3. **Navigation**: `navigator.go(ViewRequest.of(kind.key()).selecting(parent))`. The view
   selects the row (`ObjectTableView.select`), which shows the parent's editor.

The label names the parent's kind and the parent: "Entry: walk", "Sense: to walk",
"Pronunciation: wɔːk", "Example: …". It uses `ObjectLabels`, extended to the new kinds.

The search field is cleared when it would hide the parent's row; `MainController.show`
already does this for a request that selects an object.

### 2.3 Navigation tree

"Objects" lists, in model order:

| Entries | Senses | Examples | Translations | Notes | Variants | Pronunciations | Media | Etymologies | Relations | Reversals | Illustrations | Fields | Traits | Annotations |

- **Annotations** moves from Categories to Objects: it is already an object table.
- **Traits**: Categories keeps its count of trait *values* ("Trait values"), and Objects
  gets the table of the traits themselves. See question 1.
- **Grammatical info**: see question 2.

---

## 3. The new tables

The columns follow the existing catalogs. "Parent" columns show the parent's label,
"Parent type" columns its kind. Columns marked *hidden* are in the catalog but not shown
by default.

| Kind | Columns | Editor (right panel) |
|---|---|---|
| **Pronunciation** | parent type (facet: entry, variant); entry form; pronunciation forms (object languages, live, editable in the cell); number of media; media file(s) *hidden* | `PronunciationEditor` (forms, media, fields, traits) |
| **Media** | entry form; pronunciation form; file (`href`); label (meta languages) | **new `ExternalDocumentEditor`**: file (`href`) with a "Choose…" button; label `MultiTextEditor`; annotations |
| **Illustration** | entry form; sense gloss; file (`href`); label | the same `ExternalDocumentEditor` (both are `HasExternalDocument`) |
| **Reversal** | parent type (sense, reversal); sense gloss; type (facet); forms; main forms *hidden* | `ReversalEditor` |
| **Translation** | example text; type (facet); translation (meta languages, live, editable); sense gloss *hidden* | **new `TranslationEditor`**: type combo (re-keys through `setType`, which refuses duplicates) and `MultiTextEditor`, taken out of `ExampleEditor`, which then uses it too |
| **Trait** | parent type (facet); parent; name (facet); value (facet, editable for a string trait); data model *hidden* | `TraitEditor` (value picker for option traits, text, date, integer) |
| **Annotation** | the current catalog (parent type, parent, name, value, who, when, frequency) | **`AnnotationEditor`** in place of the read-only summary |
| **Field** (existing) | unchanged | **`FieldEditor`** in place of the read-only summary |

Every editor starts with the delete button (`DeleteActions.component`, one undo step) and
the parent link.

---

## 4. Work plan

1. **`ComponentKinds`**: move the 8 existing component tables to it; `Views`,
   `EditorPresenters.show` and the tree use it. No visible change; the TestFX suite proves it.
2. **Parent links**: `Navigator.goToParentOf`, `EditorPresenters.addParentLink`, labels in
   `ObjectLabels`; every existing editor uses them. That also fixes the etymology (no link)
   and the annotation and field links (which open the entry instead of the parent).
3. **Editors**: `ExternalDocumentEditor`, `TranslationEditor` (extracted from
   `ExampleEditor`); `AnnotationEditor` and `FieldEditor` replace the read-only summaries.
4. **The seven new kinds**: `ViewKey`s, columns (`ComponentColumns`, or a new
   `MediaColumns`… if it grows), the `ComponentKinds` lines, i18n (en, fr): view names,
   placeholders, columns, parent labels.
5. **Navigation tree and View menu**: the new "Objects" group; Annotations moved; the
   category "Traits" renamed (question 1). The View ▸ Objects menu (`MainView.fxml`) lists
   the same views; it is generated from `ComponentKinds` too, so the two cannot drift.
6. **Drill-down from parents** (5.4): the counts in the entry and sense editors become
   links to the new tables, restricted to the component's children.
7. **Tests** (TestFX, as in `NavigationTest` and `CrossNavigationTest`):
   - every leaf of "Objects" opens a table with rows (the demo dictionary has
     pronunciations, traits, annotations; media, illustrations, reversals and translations
     are checked on the in-memory demo, adding what it lacks);
   - selecting a row shows its editor, and editing it there changes the model (and is one
     undo step);
   - the parent link opens the parent's table with the parent selected, for each kind, and
     for an annotation on a gloss;
   - deleting from the editor removes the row; undo brings it back.

Each step leaves the tests passing. Steps 1–2 are the foundation; 3 and 4 can then go kind
by kind.

---

## 5. What the new tables get from the existing ones

All of this comes with `ObjectTableView` and the column catalog; the plan only declares the
columns:

1. **Global search** (the toolbar field), and the **row count** ("120 rows shown out of
   2,195").
2. **Per-column filters**: facets (a value list) for categorical columns (types, names,
   parent types), and text filters for texts. Declared with `.facet()` / `.textFilter()`.
3. **Multilingual columns**: one sub-column per language (`Columns.multiText`), with hidden
   columns available in the catalog (`.hidden()`). This is ready for the user-chosen
   columns of the split plan's phase 7 (`ColumnLayout`).
4. **Drill-down subsets**: `ViewRequest.subset(key, objects, title)` shows a table
   restricted to some objects, with a title ("Pronunciations of 'walk'"). Clicking the
   highlighted tree item again goes back to all of them (B6). The parent editors can link
   to their children's tables, as the entry editor's "open in Senses view" does (step 6).
5. **Live rows and cells**: the rows follow the registry (an added, deleted or undone
   component appears or disappears), and `.live(...)` cells follow edits made in the
   panel.
6. **Editing in the cell** where it is safe: the forms of a pronunciation, the text of a
   translation, the value of a string trait (`.editable(...)`, as the entry forms do).
   Each edit is one undo step.
7. **CSV export** of the rows shown (Tools ▸ Export as CSV), which works for any table.
8. **Undo integration**:
   - deletion from the editor is one labelled step;
   - after undo or redo, `Reveal` selects the changed component when the current view
     lists it (`ObjectTableView.select`). With these tables, that becomes true for every
     kind, so undoing in the Traits table stays in the Traits table.
9. **Placeholders** ("No media in this dictionary") and the selection kept when the table
   is refreshed.

**Not offered on purpose: a "New …" button.** A component needs a parent: it is added
from its parent's editor (the existing add buttons), and the table shows it at once.

---

## 6. Questions

1. **Traits:** two tree items called "Traits" would be confusing. Recommended: Objects ▸
   **Traits** (the traits themselves, new), and Categories ▸ **Trait values** (the current
   count by name and value, unchanged).
2. **Grammatical info:** a sense holds at most one, edited in the sense editor, and
   Categories ▸ Grammatical info already counts them. Recommended: no table of its own; a
   parent link from a grammatical info's trait opens the sense. Say if you'd rather have
   one.
3. **Media and illustration files:** the "Choose…" button could copy the chosen file next
   to the dictionary (FieldWorks keeps them in `audio/` and `pictures/` folders) or only
   store its path. Recommended: store a path relative to the dictionary file when the file
   is under it, otherwise the absolute path, and no copying.

---

## 7. Implementation (2026-10-16)

Done, with the recommended answers to the three questions: Objects ▸ Traits and Categories ▸
**Trait values**; no table for grammatical infos; media and illustration paths stored
relative to the dictionary's folder when the file is under it, without copying. 120 tests,
all passing (107 before; new: `ui/fx/ComponentTablesTest`, 13 TestFX tests).

**What is where:**
- `ui.view.ComponentKinds`: the 15 component kinds, in model order. `Views.create`,
  `EditorPresenters.show`, the "Objects" subtree and View ▸ Objects (now filled in code) all
  read it.
- New views: Translations, Pronunciations, Media, Reversals, Illustrations, Traits;
  Annotations moved from Categories to Objects; the Fields item no longer appears under
  Categories in the View menu.
- Columns: `ComponentColumns.translations`, `pronunciations`, `media`, `illustrations`,
  `reversals`, `traits`; type, name and parent-type columns are facets; the texts of
  translations, pronunciations and reversals are live and editable in the cells.
- Editors: new `ExternalDocumentEditor` (media, illustrations) and `TranslationEditor`;
  `AnnotationEditor` and `FieldEditor` replace the read-only summaries; `PronunciationEditor`,
  `ReversalEditor` and `TraitEditor` are reused. Each starts with the delete button (one
  undo step).
- Parent link: `EditorPresenters.addParentLink` ("↑ Sense: to walk", in the existing
  back-button style) on every component editor, etymologies included. It opens the
  parent's table on the parent's row; an annotation on a text or a form leads to the
  component of the text; a parent without a table (a grammatical info) to the nearest one
  above it.
- Children links: the editors of entries, senses, examples and pronunciations link to the
  tables of their children, restricted to them ("Pronunciations of 'walk'").
- Removed: `addBackToParentButton`, `addGoToParentButton` and their strings.

**Differences from the plan:**
- `ExampleEditor` keeps its own translation panes; `TranslationEditor` is used by the
  Translations table only. Extracting it into the example editor would change that
  editor's layout, for no gain in this work.
- Trait values are edited in the editor (`TraitEditor`, with its value picker), not in the
  cells: a trait's value can be an option, a list of options or a date, which a text cell
  would not handle well.
