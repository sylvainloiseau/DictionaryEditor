# Configurable columns and typed filters in the component tables: plan (2026-11-01)

## Goal

In every component table (entries, senses, examples, translations, notes, variants,
pronunciations, media, etymologies, relations, reversals, illustrations, fields, traits,
annotations):

1. the user chooses **which columns are shown and in which order**:
   - from a **Columns button above the table** (quick, applied live);
   - from a **"Tables" tab in the Preferences dialog** (every table in one place);
2. the columns on offer are:
   - the **current columns**, which stay the default layout;
   - **every property of the component type** (its own texts and attributes, and its LIFT
     fields, traits, notes and annotations, by type or name);
   - the **number of each kind of subcomponent** (direct, and for some of them, nested);
   - the **properties of the parent and of every ancestor**;
3. a **multitext** property gives one column per language, and each language can be shown
   or hidden on its own; multitext cells are **editable** (own, extension and ancestor
   texts);
4. each column has a **filter suited to its type**, which stays aligned with its column:
   - text (ignore case, regular expression) for texts and multitexts;
   - a list of options to tick for fixed-value columns;
   - a min–max range for numbers (including counts) and dates.

### Decisions (2026-11-01)

| # | Decision |
|---|---|
| D1 | Layouts are **global** (not per dictionary). |
| D2 | The nested counts of 3.3 are accepted. |
| D3 | **Multitext columns are editable** (own, extension and ancestor texts). |
| D4 | The **entries table is included**. |
| D5 | Filters are **kept per view for the session** (not stored in the preferences). |
| D6 | Typing in an empty field or note cell **creates** the field or note (one undo step); clearing its last text **removes** it (one undo step), see 3.5. |
| D7 | Filters combine with **AND between columns, OR inside a column**; OR between columns is left for later (saved views). |
| D8 | Filters go **in the column headers** (headers about 26 px taller). |
| D9 | The **histogram** in the range popover and the **empty-cell marker** are kept. |

Other tables built on `FilterableTable` (Categories, languages fields, header field
definitions) get the new typed filters (step 3) with `TEXT` as default type; their columns
stay fixed (out of scope).

---

## 1. Current state

| Piece | What it does today | Gap |
|---|---|---|
| `ColumnSpec` | one column, single value or one sub-column per language; `visible` flag (`hidden()`); `FilterMode {AUTO, FACET, TEXT}` | no value type: everything is sorted and filtered as text (`10 < 9`); no "which languages are shown" |
| Catalogs (`EntryColumns`, `SenseColumns`, `ExampleColumns`, `ComponentColumns`) | a fixed list per kind, a few `hidden()` columns | hidden columns cannot be shown; no fields, counts or ancestors |
| `ColumnLayout` | ordered list of ids, `defaults()` / `apply()`; unit-tested | **never stored, never chosen**: `ObjectTableView` always calls `ColumnLayout.defaults(catalog)` |
| `FilterableTable` | builds columns and a filter row **once**, in the constructor | columns cannot change without rebuilding the view (filters, sort, selection lost) |
| Filter row (`buildFilterRow`) | a `GridPane` under a "Reset filters" bar, one `TextField` or `ComboBox` per leaf | see 1.1 |
| Facets (`refreshFacets`) | recomputes the values of **every** combo at **every** keystroke | O(columns × rows) per keystroke; single choice only |
| `AppPreferences` / `PreferencesDialog` | a `java.util.prefs` node; one grid of settings | no table settings; no tabs |
| Views | `Views.create` builds a new view at each navigation | a stored layout is picked up at the next navigation for free; **filters are lost** at each navigation |
| CSV export | `headers()/rowTexts()` = shown leaves, shown rows | follows the layout and the filters automatically |

### 1.1 Why the filters do not follow the columns

In `FilterableTable.buildFilterRow()`:
1. `cc.prefWidthProperty().bind(table.getVisibleLeafColumn(i).widthProperty())` binds filter
   *i* to the column **at index i at construction time**. After a column is dragged to
   another place (JavaFX allows it natively), filter *i* follows the width of a column
   that is no longer above it.
2. The row's `maxWidth` is bound to the sum of the widths, but when the columns are wider
   than the viewport the `VBox` gives the `GridPane` **less** than that sum, and the
   `GridPane` shrinks every column proportionally (`Hgrow ALWAYS`): filters get narrower
   than their columns and drift to the left.
3. The filter row **does not scroll horizontally** with the table.
4. The dependencies of the `maxWidth` binding are captured once, for the leaf columns that
   existed then.

Fixing 1–4 in place means syncing a separate row with the internals of the table skin
(horizontal scrollbar lookup, reorder listeners). The redesign removes the separate row
instead (section 5).

---

## 2. Column model

### 2.1 Column ids

Stable ids, so that a stored layout survives the evolution of the catalog and of the
dictionary (unknown ids are ignored and kept, as `ColumnLayout.apply` already does).

| Source | Id | Example |
|---|---|---|
| Existing columns | unchanged | `sense.gloss`, `entry.morphType` |
| Own property not yet in a catalog | `<kind>.<property>` | `entry.citation`, `sense.order`, `entry.uuid` |
| LIFT field | `field:<name>` | `field:literal-meaning` |
| Trait | `trait:<name>` | `trait:usage-type` |
| Note | `note:<type>` (no type: `note:`) | `note:encyclopedic` |
| Annotation | `annotation:<name>` | `annotation:status` |
| Count | `count:<child>` / `count:<child>.all` (nested) | `count:senses`, `count:examples.all` |
| Ancestor | `up:<kind>/<id>` | `up:entry/entry.form`, `up:sense/field:scientific-name` |
| Direct parent (generic) | `parent:type`, `parent:label` | notes, fields, traits, annotations |

A **leaf id** names one displayed column: `<id>` or `<id>@<lang>`. Layouts, widths, filters
and sort orders are all keyed by leaf id.

### 2.2 `ColumnSpec` gets a value type

```java
enum ValueType { TEXT, OPTION, NUMBER, DATE }
```

| Type | Used for | Sort | Filter (section 4) | Cell |
|---|---|---|---|---|
| `TEXT` | multitexts, free texts (source, href, ids) | locale collator, empty last | text | left |
| `OPTION` | values from a closed or small set: types, trait values, morph type, gram. info, parent type, booleans, data model, annotation values | collator | options | left |
| `NUMBER` | counts, order, homograph number, annotation frequency | numeric | range | right, tabular figures |
| `DATE` | date created / modified / deleted, annotation "when", date traits | chronological (partial dates allowed) | range | left |

`FilterMode.AUTO`, which picks text or facet by counting distinct values, is **removed**:
each column declares its type. This makes the filter predictable (it no longer changes
when the data change) and lets generated columns be typed at generation time.

Two optional accessors are added to `ColumnSpec`; the defaults are derived from `value`:
- `values(row, lang) → List<String>` for an `OPTION` column whose cell can hold several
  values (repeated trait, several media): the options filter matches **any** of them;
- `key(row, lang) → Comparable<?>` for sorting and range filtering (`Long`, `LiftDate`).

`LiftDate` (new, in `core`) parses LIFT dates, which may be partial (`2023`, `2023-05`,
`2023-05-01T12:00:00Z`), into an interval `[start, end)`: `2023` is the whole year. A range
filter keeps a row when the interval **overlaps** the range.

Editability becomes per cell: `CellCommit` stays, and `editable(row, lang)` (default: true)
says whether this cell can be edited (an ancestor column on a row without that ancestor
cannot).

### 2.3 Layout with languages

`ColumnLayout` becomes a list of `ColumnRef(String id, List<String> languages)`:
- `languages` empty = **all the languages** of the column, including languages added later;
- otherwise the chosen languages, **in that order** (the order of the sub-columns).

Stored form (one string, well under `Preferences.MAX_VALUE_LENGTH`):

```
entry.form|entry.morphType|sense.gloss@fr,en|count:senses|up:entry/entry.form@tpi
```

plus, under a second key, the widths given by dragging (`leafId=width;…`).

`ColumnLayout.apply(catalog)` returns the specs **restricted to the chosen languages**
(`ColumnSpec.withLanguages(List)`), so `TableFactory` keeps building a group column with
one sub-column per shown language; a group with one language is shown as a single column
titled "Gloss · fr".

---

## 3. Generating the catalog

New class `ui.table.catalogs.ColumnCatalog`, used by `ComponentKind.columns` (and by
`EntryView`) in place of the plain catalog functions:

```java
/** All the columns available for a kind, in sections, current columns first. */
static <T> List<ColumnSection<T>> of(ComponentKind<T> kind, LiftDictionary d)

record ColumnSection<T>(String id, String title, List<ColumnSpec<T>> columns) {}
```

The sections are those of the column chooser (section 6).

### 3.1 Section "<Kind>": own properties

- **The current catalog**, unchanged (its `visible` columns are the default layout),
  each column given its `ValueType`.
- **Missing own properties**, declared once per kind in a small table:

  | Kind | Added |
  |---|---|
  | Entry | citation form (multitext), order, homograph number, date deleted, UUID |
  | Sense | order, gram-info traits |
  | Example | source (exists), every translation type (exists) |
  | Variant / Relation / Etymology | ref, order, usage, source… |
  | Every extensible kind | date created, date modified |

  Multitext properties use the languages **actually used in that property**: header
  languages plus languages found in the data, as `EntryColumns` already does for forms.

### 3.2 Section "Fields, traits, notes, annotations": extensions

Found in **one pass over the registry lists** (`getFields()`, `getTraits()`, `getNotes()`,
`getAnnotations()`), grouped by the class of their parent, plus the field and trait
definitions of the header (`LiftFieldAndTraitDefinition` and its target), by an
`ExtensionIndex` built with the catalog (O(dictionary) per view creation):

- one **multitext column per field name** used on (or defined for) this kind;
- one `OPTION` column per **trait name** (several values when repeated; `DATE`/`NUMBER`
  when the trait's data model says so);
- one **multitext column per note type** (for `AbstractNotable` kinds);
- one `OPTION` column per **annotation name** (its value).

### 3.3 Section "Counts": subcomponents

A static description of the containment of the model, shared by counts and ancestors:

```java
record Child<P>(String id, String i18nKey, Class<?> type, Function<P, Collection<?>> direct) {}
```

| Kind | Direct counts | Nested counts (`.all`) |
|---|---|---|
| Entry | senses, variants, pronunciations, relations, etymologies, notes, fields, traits, annotations | senses (with subsenses), examples, translations, illustrations, reversals, media |
| Sense | subsenses, examples, illustrations, reversals, relations, notes, fields, traits | subsenses, examples, translations |
| Example | translations, notes, fields, traits | — |
| Variant | pronunciations, relations, notes, fields, traits | media |
| Pronunciation | media, fields, traits | — |
| Others | fields, traits, annotations where they apply | — |

Direct counts are **live** (`Bindings.size(observableList)`); nested counts are computed by
walking the child lists and refreshed with the table. All are `NUMBER`.

### 3.4 Sections "Parent" and "<Ancestor kind>": ancestors

- For polymorphic parents (notes, fields, traits, annotations, pronunciations…), a generic
  **"Parent"** section: parent type (`OPTION`), parent label (the existing columns).
- One section per **kind of ancestor this kind can have**, computed from the containment
  table (transitive closure of "can be a child of"): Translation → Example, Sense, Entry;
  Note → Entry, Sense, Example, Variant, Pronunciation, Relation, Etymology…
- `up:<kind>/<id>` shows column `<id>` of the **nearest ancestor of that kind** (walk of
  `getParentNode()`, as `owningSense` and `DictionaryQueries.owningEntry` do), empty when
  there is none. This works for subsenses and at any depth.
- An ancestor section offers the ancestor's **own properties, extensions and counts**, not
  *its* ancestors (they have sections of their own): no recursion, a bounded catalog.
- The existing "parent entry" / "parent sense" columns keep their ids and stay in the
  first section.

### 3.5 Editing in cells (decided: multitexts)

Every **multitext** column is editable, with the existing pattern (`editableTexts`: live
cell on `formTextProperty(lang)`, commit = one undo step, an empty text removes the form):

| Column | Edits | Cell without a target |
|---|---|---|
| Own multitext (gloss, definition, citation…) | the row's multitext | — |
| Field / note | the field or note of that name on the row | **creates** the field or note on commit (D6) |
| Ancestor multitext (`up:entry/entry.form`…) | the ancestor's multitext; the other rows of the same ancestor follow (live cells) | read-only when the row has no such ancestor |
| Counts, options, dates, numbers | read-only (morph type stays editable, as today) | — |

An editable cell is shown with the usual hint (pencil cursor; double-click or F2 to edit).

Creation and removal of fields and notes (D6), each **one undo step** through
`History.perform(label, subject, gesture)`, which already groups everything a gesture
changes:
- **typing in an empty cell**: `perform("Add field <name>", row, () -> { create the field
  or note; set the form })`; an empty commit on an empty cell does nothing (no entry in
  the history);
- **clearing a text**: the form of that language is removed; when it was the **last form**
  and the field or note holds nothing else, it is removed in the same gesture. "Nothing
  else" = no trait, no annotation, and for a note no field; otherwise the field or note is
  kept, empty, so that no data is lost silently. The rule lives in one helper
  (`ExtensionCells.commit`) with unit tests;
- the editors on the right follow (they listen to the same model).

An edited row that no longer passes the filters **stays visible** until the filters or the
selection change (the predicate is `passes(row) || row == lastEditedRow`), so a correction
does not make the row vanish under the cursor.

---

## 4. Filter model (no JavaFX)

### 4.1 Types

```java
/** The filter of one leaf column. Immutable; null or inactive = no filter. */
sealed interface ColumnFilter permits TextFilter, OptionFilter, RangeFilter {
    boolean active();
    boolean test(CellValues cell);          // the text, values and key of one cell
    String describe();                      // for the chips: « contains "go" », « 2–5 »
}

record TextFilter(String pattern, TextOp op, boolean ignoreCase, boolean regex, boolean negate)
enum TextOp { CONTAINS, STARTS_WITH, EQUALS, EMPTY, NOT_EMPTY }

record OptionFilter(Set<String> selected, boolean includeEmpty, boolean negate)
// keeps a row when any of the cell's values is selected (OR in a column)

record RangeFilter<K extends Comparable<K>>(K min, K max, boolean includeEmpty)
// min and max optional and inclusive; dates by overlap (2.2)
```

- A row is shown when it passes **every** column filter (AND between columns) and the
  global search.
- `TextFilter` compiles its regular expression **once**; an invalid expression makes the
  filter inactive and reports the error (`Optional<String> error()`).
- `EMPTY` / `NOT_EMPTY` exist for every type (in `TextOp`, `includeEmpty`): spotting a
  missing gloss in a language is the main use of these tables (README).
- Every filter has a **text form** (`toToken()` / `parse()`), unit-tested, so that filters
  can later be saved with a layout ("saved views", section 9) without changing the model.

### 4.2 `FilterModel<T>` and `FilterEngine<T>`

```java
final class FilterModel<T> {
    ObservableMap<String, ColumnFilter> filters();   // by leaf id
    StringProperty search();
    void clear();
}
```

`FilterEngine` turns the model into the predicate of the `FilteredList`, and computes on
demand:
- the **options of a column** with their counts, over the rows that pass the *other*
  filters (as `matches(row, ignoredColumn)` does today), **only when its popover opens**,
  not at each keystroke;
- the **min and max** of a numeric or date column, for the range popover's hints.

Performance:
- text filters are debounced (150 ms) and the predicate is rebuilt once per change;
- a **row-text cache** (`IdentityHashMap<row, String[]>`, cleared on `refresh()` and on
  edits) avoids recomputing nested counts and ancestor texts for every filter and sort.

Filters are kept **per view for the session** (D5) in a `FilterSessions` map owned by
`EditorContext`, keyed by `ViewKey`, for the views **without a subset**; a drill-down view
(the senses of one entry…) starts without filters and does not change those of the full
view. They are dropped when another dictionary is opened. Removing a column from the
layout removes its filter.

**Navigation to an object** (`ViewRequest.selecting`, parent links, cross navigation):
`MainController.show` clears the search when it would hide the target row (today), and
from now on also **the filters that hide it** (only those, computed by `FilterEngine`),
with a notice in the toolbar ("Filters cleared to show …", with *Undo* restoring them).

**Text matching** of the text filter and the search: both sides are normalised to **NFC**
(combining diacritics in IPA and orthographies must match whatever the input method);
ignore case = `CASE_INSENSITIVE | UNICODE_CASE`. With a regular expression, *contains* =
`find()`, *starts with* = `lookingAt()`, *is exactly* = `matches()`.

**Cache invalidation**: the row-text cache is cleared on `refresh()`, on a layout change,
and on every change of the `History` (one listener on its cursor), which covers edits
made in the editors, undo and redo.

---

## 5. Filter widgets: in the column headers

### 5.1 Principle

The filter of a leaf column is **part of its header** (`TableColumn.setGraphic`: title
on top, filter under it). It is then laid out by the table itself, so it follows the
column's **width, position after a drag, and horizontal scrolling** with no syncing code.
The separate `GridPane` row and its bindings are removed (they cause 1.1).

```
┌──────────────┬──────────────────────────────────────┬────────────┐
│              │ Gloss                                │            │
│ Morph type ▾ ├──────────────────┬───────────────────┤ Senses   ▲ │
│              │ fr               │ en                │            │
│ [All      ▾] │ [🔍 …     Aa .*] │ [🔍 to …  Aa .*]  │ [≥ 2     ▾]│
├──────────────┼──────────────────┼───────────────────┼────────────┤
│ root         │ marcher          │ to walk           │          3 │
```

Notes on the header:
- The header label is laid out to the full height of its cell; the graphic is bottom
  aligned (`-fx-alignment: bottom-left`), so the filters of single columns and of
  language sub-columns share **one baseline** even when only some columns are grouped.
- The filter controls **consume** mouse pressed/released/dragged events, so that clicking
  a filter neither sorts the column nor starts a column drag. A click on the title still
  sorts; dragging the title still reorders.
- The minimum leaf width becomes 90 px; under 120 px a text filter folds its toggles into
  its menu (5.2).
- **Spike first** (step 0): the alignment and event handling are checked on JavaFX 21 with
  grouped and single columns. If the header approach fails, the fallback is a separate row
  rebuilt on `getVisibleLeafColumns()` changes, bound **by column** (not by index), and
  shifted by the horizontal scroll value. Both are behind the same `FilterControls` API,
  so the rest of the plan does not depend on this choice.

### 5.2 Text filter (texts and multitexts)

```
[🔍 to w…         Aa  .*  ▾]
```

- one compact field (26 px, like today); typing filters live;
- **`Aa`**: match case (off = ignore case, the default); **`.*`**: regular expression;
  small toggle buttons inside the field, highlighted when on (as in IDE search fields);
- **`▾`** opens a menu: *contains* (default) · *starts with* · *is exactly* · *is empty* ·
  *is not empty* · ☐ *not* (negate) · *clear*;
- an invalid regular expression: red border, the error in a tooltip, the filter inactive;
- Esc clears the field; the placeholder names the operation ("contains…", "regex…").

### 5.3 Options filter (fixed-value columns)

In the header, a button that sums up the selection:

```
[All ▾]    [noun ▾]    [noun, verb ▾]    [3 selected ▾]    [not root ▾]
```

Its popover (live, closes on Esc or a click outside):

```
┌──────────────────────────────┐
│ 🔍 Search…                    │
│ Select all · None · ⇅ A–Z/#   │
│ ☑ noun                  1 204 │
│ ☑ verb                    812 │
│ ☐ adjective               301 │
│ ☐ (empty)                  37 │
│ ───────────────────────────── │
│ ☐ Exclude selected            │
└──────────────────────────────┘
```

- counts are those of the rows that pass the other filters (values that would give no row
  are shown greyed with 0, at the end), sorted by count or alphabetically;
- hovering a value shows an **"only"** link (select only this one);
- **(empty)** selects the rows with no value;
- the list is virtualized (`ListView`), so a column with thousands of values stays fast.

### 5.4 Range filter (numbers, counts, dates)

In the header: `[Any ▾]`, `[2 – 5 ▾]`, `[≥ 2 ▾]`, `[≤ 2024-03 ▾]`. Its popover:

```
┌ Senses ──────────────────────┐        ┌ Date modified ─────────────────────┐
│  ▁▃█▅▂▁ ▁                     │        │ From [2024-01      ] 📅            │
│ Min [ 2 ]  –  Max [   ]       │        │ To   [            ] 📅            │
│ (data: 0 – 14)                │        │ Today · 7 days · 30 days · This year│
│ = 0 · ≥ 1 · ≥ 2 · ≥ 5         │        │ ☐ Include empty                     │
│ ☐ Include empty               │        └─────────────────────────────────────┘
└──────────────────────────────┘
```

- **numbers**: two fields (empty = no bound) with the data's min and max as hints, and
  presets; for counts, *= 0* finds the components **without** a given subcomponent (no
  example, no gloss…);
- **dates**: two fields that take partial dates (`2023`, `2023-05`, `2023-05-01`) with a
  `DatePicker` button, and presets relative to today;
- a **histogram** (D9) of the values over the rows that pass the other filters, computed
  when the popover opens: integer bins for counts (up to 20, then grouped), months or
  years for dates depending on the span; dragging over the bars sets min and max; the
  selected range is highlighted.

### 5.5 Toolbar above the table: columns and active filters

The "Reset filters" bar becomes a toolbar (the button keeps its id, renamed *Clear all*, for the TestFX tests):

```
[▥ Columns ⁵]  Filters: (Gloss·en contains "to" ✕) (Senses ≥ 2 ✕) (Morph type: root, stem ✕)   Clear all   124 / 3 210
```

- **▥ Columns** opens the column chooser (6.1); its badge is the number of shown columns
  when the layout differs from the default;
- one **chip** per active filter (`ColumnFilter.describe()`); a click on a chip scrolls to
  its column and focuses or opens its filter; ✕ removes it;
- **Clear all** replaces "Reset filters" (same effect: filters, search, selection);
- the counter (rows shown / total) is the one shown today in the status bar, duplicated
  here only when filters are active.

### 5.6 Keyboard

- Ctrl+Shift+F: focuses the filter of the column of the selected cell (else the first);
- Tab / Shift+Tab: moves between the filters of the header;
- Alt+↓: opens the popover of the focused options or range filter;
- Esc: clears the focused text filter, or closes the popover;
- ↓ from a filter moves to the first row.

Every control has an accessible text ("Filter Gloss, en").

The new shortcuts are registered in `Commands` (`table.columns` = Ctrl+Shift+K,
`table.focusFilter` = Ctrl+Shift+F; both free today), so they can be changed in the
shortcuts dialog like the others.

### 5.7 Choice of the filter by column type

| `ValueType` | Filter | Can be switched to (header context menu "Filter as") |
|---|---|---|
| `TEXT` | text | options (for a text with few values, e.g. "source") |
| `OPTION` | options | text (for an option column with very many values) |
| `NUMBER`, `DATE` | range | text |

The switch is stored with the layout (`ColumnRef` gets an optional filter kind).

---

## 6. Column chooser

### 6.1 Columns button + popover (main entry point)

The **▥ Columns** button of the toolbar (5.5), also Ctrl+Shift+K, opens an anchored
popover (a `PopupControl` with the app's card style, not a modal dialog), in the style of
the "Properties" menus of Notion, Airtable or Linear:

```
┌ Columns · Senses ──────────────────────── ↺ Default ┐
│ 🔍 Search a column…                                  │
│                                                      │
│ SHOWN                                         5      │
│ ⠿ Parent entry      [tpi]                       ✕    │
│ ⠿ Gram. info                                    ✕    │
│ ⠿ Gloss             [fr] [en] [tpi]             ✕    │
│ ⠿ Definition        [fr] [en]                   ✕    │
│ ⠿ Examples (count)                              ✕    │
│                                                      │
│ AVAILABLE                                            │
│ ▾ Sense                                       3      │
│    + Id   + Order   + Date modified                  │
│ ▸ Fields, traits, notes                       6      │
│ ▸ Counts                                      8      │
│ ▸ Entry (ancestor)                           14      │
└──────────────────────────────────────────────────────┘
```

- **Shown**: drag handle (⠿) to reorder; **language chips** (toggle pills) on multitext
  columns: a click hides or shows one language, a drag reorders the languages; ✕ removes
  the column (and its filter); a small icon marks the editable columns;
- **Available**: collapsible sections (3.1–3.4) with counts; a click (or Enter) appends
  the column to *Shown*; shown columns are not listed;
- **Search** filters both lists on title, section and language code ("gloss en", "count",
  "entry form"); with a query, the sections open;
- **Live**: each change updates the table at once (debounced 150 ms) through
  `setColumns` (section 7); filters, sort and selection of the remaining columns are kept;
- Ctrl+Z in the popover undoes the last layout change (a local stack: layout changes are
  not dictionary changes and stay out of `History`);
- **↺ Default** goes back to the catalog defaults (removes the stored layout);
- keyboard: ↑/↓ moves, Space toggles, Alt+↑/↓ reorders.

### 6.2 Header context menu

Right-click on a column header:
- Hide "Gloss" · Hide language "en" · Show only "en"
- Sort ascending / descending · Clear filter · Filter as… (5.7)
- Add a column… (opens the popover, search focused)
- Default columns

### 6.3 Preferences ▸ Tables

`PreferencesDialog` becomes a `TabPane`: **General** (the current grid, unchanged) and
**Tables**:

```
┌ Tables ──────────────────────────────────────────────────────────┐
│ Entries         │  [ the same ColumnChooser as the popover ]      │
│ Senses        • │                                                 │
│ Examples        │                                                 │
│ …               │                                                 │
│ Annotations     │                                                 │
│─────────────────│                                                 │
│ Reset all       │                                                 │
└──────────────────────────────────────────────────────────────────┘
```

- left: the kinds of `ComponentKinds.all()`; a dot marks a customised table;
- right: the **same `ColumnChooser` control**, here **buffered**: applied on OK, dropped on
  Cancel;
- the catalog needs a dictionary (languages, field names): it uses the open dictionary;
  without one, only the static columns are listed, and the stored ids of the others are
  kept as they are.

`ui.table.ColumnChooser<T>`: input = sections + current `ColumnLayout`; output = an
observable `ColumnLayout`. The popover binds it live; the Preferences tab copies it on OK.

---

## 7. `FilterableTable` after the redesign

`FilterableTable` is split; it keeps its public API (`node`, `table`, `select`, `headers`,
`rowTexts`, `bindSearch`, `onCountChanged`, `onSelect`, `placeholder`, `resetFilters`) so the
views and tests keep working.

```
ColumnSpec<T> ──► TableFactory ──► TableColumn + Leaf (leaf id)
                       │
                       └─► FilterControls.create(leaf, model)  → header graphic
FilterModel<T> ◄──── TextFilterControl / OptionFilterControl / RangeFilterControl
     │                 ActiveFiltersBar (chips)
     ▼
FilterEngine<T> ──► predicate of the FilteredList, options and ranges on demand
```

New method `setColumns(List<ColumnSpec<T>> specs)`:
1. keeps the filters, sort order (`getSortOrder()` by leaf id) and selected row;
2. rebuilds the columns and their filter controls;
3. drops the filters of leaves that are gone, restores the others; `refresh()`.

Listeners keep the stored layout in sync with direct manipulation of the header:
- **reorder** by dragging a header → new order saved;
- **resize** → widths saved (debounced, 300 ms).

`ObjectTableView` gets a `ColumnLayoutStore` (per `ViewKey`) from `EditorContext`: it reads
the stored layout instead of `ColumnLayout.defaults`, and listens to it, so a change made in
the Preferences dialog applies to the open table at once. `EntryView` does the same.

---

## 8. Persistence

`AppPreferences`:

```java
Optional<ColumnLayout> columnLayout(ViewKey key);          // "table.columns.<KEY>"
void setColumnLayout(ViewKey key, ColumnLayout layout);    // null = default
Map<String, Double> columnWidths(ViewKey key);             // "table.widths.<KEY>"
void setColumnWidths(ViewKey key, Map<String, Double> w);
```

`ColumnLayoutStore` wraps them with an `ObjectProperty<ColumnLayout>` per key, shared by open
views and the Preferences dialog. Layouts are **global** (decided): ids missing from a
dictionary are ignored and kept. Filters are kept in memory per view for the session
(section 4.2), not in the preferences.

---

## 9. Improvements suggested (included or for later)

### 9.1 Included in the plan
- **is empty / is not empty** for every filter, and **= 0** presets on counts: finding
  what is missing is the main use of the tables;
- **negation** ("not") on text and options filters;
- **multi-select** options with counts, instead of today's single choice;
- **active filter chips**, so a filter hidden by horizontal scrolling is still visible;
- **"Filter as"** switch (5.7);
- filters **kept when navigating** away and back (D5);
- **histogram** in the range popover and **empty-cell marker** (D9), see 9.2;
- **NFC normalisation** in text matching (4.2).

### 9.2 Empty-cell marker

Empty cells of multitext, field and note columns get a subtle diagonal hatch (CSS
pseudo-class `:empty-value` set by the cell factory, light and dark variants), so a
missing language stands out. Not on cells that cannot have a value (an ancestor column on
a row without that ancestor: plain empty cell). Toggle: View ▸ *Mark empty cells*
(preference `ui.markEmptyCells`, on by default).

### 9.3 For later (the model allows them without redesign)
- **saved views**: a named layout + filters + sort per table (e.g. "Senses without English
  gloss"), in the View menu; `ColumnFilter.toToken()` is the format;
- **OR between columns** (D7), in a small filter-group editor;
- a filter on a **multitext group header** matching any of its languages;
- a **diacritic-insensitive** toggle next to `Aa`;
- **filter on the global search with the same options** (`Aa`, `.*`).

---

## 10. Work plan

0. **Spike: filters in the column headers** (half a day): alignment of grouped and single
   columns, event consumption, horizontal scroll, drag reorder, dark theme. Go / fallback
   decision (5.1).
1. **Value types and layout model**: `ValueType`, `values`/`key` accessors, `LiftDate`,
   `ColumnRef` with languages, parse/format, `withLanguages`; numeric and date sorting.
   Every existing column gets its type. Column comparators by type (the `SortedList`
   sorts through the columns' comparators, which see the cell strings: numbers and
   `LiftDate`s are parsed there). Unit tests (`ColumnLayoutTest`, `LiftDateTest`).
2. **Filter model**: `ColumnFilter` and its three records, `FilterModel`, `FilterEngine`
   (predicate, options with counts, min/max, histogram bins, row-text cache, "filters
   hiding this row"), NFC matching, token format. Unit tests, no JavaFX, including a
   timing test on a generated dictionary (20 000 entries: a refresh under 100 ms).
3. **Filter widgets**: `FilterControls` (text, options, range), header graphics (or the
   fallback row), histogram, `ActiveFiltersBar`, keyboard and `Commands`, i18n (en,
   fr), CSS (light and dark).
   `FilterableTable` uses them; the old `GridPane` row, `FilterMode.AUTO` and
   `refreshFacets` are removed. Existing TestFX filter tests (`SearchAndFilterTest`)
   adapted; new tests: filter follows a dragged and a resized column; regex; options
   multi-select; count range.
4. **Persistence + `setColumns`**: `AppPreferences`, `ColumnLayoutStore`, `ObjectTableView`
   and `EntryView` read the store; reorder and widths saved; `FilterSessions`; navigation
   to an object clears the filters that hide it. TestFX: reorder, navigate away and back,
   order and filters kept; parent link to a filtered-out row.
5. **Generated catalog**: `ColumnCatalog`, sections, `ExtensionIndex`, containment table
   (counts, ancestor kinds), missing own properties, editable multitexts and
   `ExtensionCells` (3.5), titles of generated columns (R6). Unit tests on
   `mock-dictionary.json`: a field gives one sub-column per language; counts; ancestor of
   a subsense example; note on an example → its entry; creating a field by typing, and
   undoing it in one step; clearing the last text removes the field, but not a field
   with a trait. **Default layouts identical to today** (test).
6. **Column chooser**: `ColumnChooser`, Columns popover, header context menu, Preferences ▸
   Tables (`TabPane`, buffered). TestFX: add a count column, hide a language, reset;
   change in the dialog → open table updated; Cancel → unchanged.
7. **Polish and docs**: empty-cell marker and its View menu toggle,
   `documentation/…/configuration` and `fonctionnalites` pages, README screenshots.

Each step builds and passes `mvn test` on its own. After step 3 the filters are fixed and
typed even before columns become configurable, so steps 0–3 can ship first.

---

## 11. Design review (2026-11-01)

Points checked against the code and the decisions, and how the plan handles them.

| # | Risk | Handled by |
|---|---|---|
| R1 | A filter is bound to a column by **index** and squeezed by the `GridPane` (1.1) | filters in the headers (5.1), bound to their own column; spike first, fallback bound by column |
| R2 | Clicking a filter in a header **sorts** or **drags** the column | the controls consume mouse events (5.1); TestFX test |
| R3 | Navigating to an object hidden by a **filter** (only the search is cleared today, `MainController.show`) | clears the filters that hide it, with a notice and *Undo* (4.2) |
| R4 | Clearing a field's text **loses** its traits or annotations | removal only when it holds nothing else (3.5) |
| R5 | Create-and-type and clear-and-remove are **two** undo steps | one `History.perform` gesture each (3.5) |
| R6 | Generated columns are **ambiguous** in headers and CSV ("Form" of the row or of the entry?) | titles carry their section: "Entry › Form", "Field · literal-meaning", "Senses (count)", "Examples (all)"; the CSV header adds `[lang]` (`Leaf.header`) |
| R7 | An edited row **vanishes** when it stops matching a filter | the last edited row stays visible (3.5) |
| R8 | **Stale** cached texts after an edit in the editor panel, undo or redo | cache cleared on every `History` change (4.2) |
| R9 | Counts and dates **sorted as text** | column comparators by `ValueType` (step 1) |
| R10 | Same text, **different Unicode** forms (precomposed / combining) | NFC on both sides (4.2) |
| R11 | Recomputing every facet at **each keystroke** (today) | options and histogram computed when their popover opens; text filters debounced; timing test (step 2) |
| R12 | Drill-down views **overwrite** the filters of the full view | session filters only for views without a subset (4.2) |
| R13 | A stored layout **refers to** fields or languages absent from the open dictionary | ignored and kept (2.1, 8); the chooser lists the languages of the dictionary, those not shown as dimmed chips |
| R14 | A filter on a column the user **hides** keeps filtering invisibly | removing a column removes its filter (4.2) |
| R15 | Shortcut **conflicts** | new shortcuts in `Commands`, customisable; Ctrl+Shift+K/F free today |
| R16 | The **other tables** (Categories, languages fields, header) break when `FilterMode.AUTO` goes | they get `TEXT` by default and the new filters (Decisions) |
| R17 | Catalog **size** (ancestor sections × fields × languages) | specs are cheap lambdas; only shown columns become `TableColumn`s; the chooser lists are virtualized and searchable |
| R18 | Stored **preferences** from an earlier version | none exist yet (`ColumnLayout` was never stored): no migration |

No open question remains; new questions found during the spike (step 0) will be added
here.

---

## 12. Implementation notes (2026-11-01)

All the steps of section 10 are implemented. Where the code differs from the plan:

- **Packages**: the filter model is `ui.table.filter` (`ColumnFilter`, `TextFilter`,
  `OptionFilter`, `RangeFilter`, `FilterModel`, `FilterEngine`, `FilterTokens`), the widgets
  `ui.table.widgets` (`TextFilterControl`, `OptionFilterControl`, `RangeFilterControl`,
  `FilterPopover`, `TableToolbar`, `ColumnChooser`), the generated catalogs
  `ui.table.catalogs` (`ColumnCatalog`, `ModelTree`, `ExtensionCells`); `LiftDate` is in
  `core`; the Tables tab is `ui.view.TablesPreferences` (a `PreferencesDialog.Page`).
- **Spike (step 0)**: the filters are in the headers. The header's label spans the whole
  header; the graphic is aligned at the bottom (`VBox` aligned bottom-left) so that single
  and language columns share one baseline. The mouse events are consumed on a wrapper of
  the widget, not on the widget itself (a control ignores events consumed on its own node).
- **`ColumnSpec`** is a final class (it has more fields than a readable record); every
  `Columns.multiText` column is live and editable (D3), `applies` tells which cells can be
  edited or marked empty.
- **Direct counts** are not bound to the lists: like every cell, they are recomputed when the
  table is refreshed (after each edit made in the editor panel), which shows the same result.
- **Parent**: the generic "Parent type / Parent" columns stay in the kind's own section
  (they are its current columns) instead of a separate section.
- **lift-api**: `getField(name)` and `getNote(type)` throw when there is none;
  `ExtensionCells.field` / `note` look them up in the maps instead.
- **Static properties**: tables listen to `TableFactory.MARK_EMPTY` through a weak listener,
  otherwise every table ever shown stays in memory.
- **Tests**: `ColumnLayoutTest`, `LiftDateTest`, `ColumnFilterTest`, `FilterEngineTest`,
  `ColumnCatalogTest` (no JavaFX toolkit); `SearchAndFilterTest` (rewritten) and
  `ColumnsTest` (TestFX). The TestFX tests need the demo dictionary
  `src/main/resources/lift/20260302.lift`, which is not in git (`*.lift` is ignored).
