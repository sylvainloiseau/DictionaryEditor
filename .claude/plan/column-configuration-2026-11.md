# Configurable columns in the component tables: plan (2026-11-01)

## Goal

In every component table (entries, senses, examples, translations, notes, variants,
pronunciations, media, etymologies, relations, reversals, illustrations, fields, traits,
annotations), the user chooses which columns are shown and in which order:

- from a **Columns button in the table header** (quick, applied live);
- from a **"Tables" tab in the Preferences dialog** (every table in one place).

The columns on offer are:
1. the **current columns**, which stay the default layout;
2. **every property of the component type** (its own texts and attributes, and its LIFT
   fields, traits, notes and annotations, by type or name);
3. the **number of each kind of subcomponent** (direct, and for some of them, nested);
4. the **properties of the parent and of every ancestor** (the entry of an example, the
   sense of a translation…).

A **multitext** property gives one column per language, and each language can be shown or
hidden on its own.

---

## 1. Current state

| Piece | What it does today | Gap |
|---|---|---|
| `ColumnSpec` | one column, single value or one sub-column per language; `visible` flag (`hidden()`) | no notion of "which languages are shown"; values sort as text (`10 < 9`) |
| Catalogs (`EntryColumns`, `SenseColumns`, `ExampleColumns`, `ComponentColumns`) | a fixed list per kind, a few `hidden()` columns | hidden columns cannot be shown; no fields, counts or ancestors |
| `ColumnLayout` | ordered list of ids, `defaults()` / `apply()`; unit-tested | **never stored, never chosen by the user**: `ObjectTableView` always calls `ColumnLayout.defaults(catalog)` |
| `FilterableTable` | builds the columns and filter row **once**, in the constructor | the columns cannot be changed without rebuilding the whole view (filters, sort and selection are lost) |
| `ComponentKinds` | one record per kind: key, type, catalog, rows, editor | good anchor for a per-kind layout |
| `AppPreferences` / `PreferencesDialog` | a `java.util.prefs` node; a single grid of settings | no table settings; no tabs |
| Views | `Views.create` builds a new view at each navigation (no cache) | a stored layout is picked up at the next navigation for free |
| CSV export | `FilterableTable.headers()/rowTexts()` = shown leaves | follows the layout automatically |

The lift-api model gives everything needed to generate columns: `AbstractExtensibleWithoutField`
(dates, traits, annotations), `AbstractExtensibleWithField` (`getFields()` by name),
`AbstractNotable` (`getNotes()` by type), the child lists (`getSenses()`, `getExamples()`,
`getPronunciations()`, `getMedias()`…) and `getParentNode()`.

---

## 2. Column model

### 2.1 Column ids

Stable ids, so that a stored layout survives the evolution of the catalog and of the
dictionary (unknown ids are ignored, as `ColumnLayout.apply` already does).

| Source | Id | Example |
|---|---|---|
| Existing columns | unchanged | `sense.gloss`, `entry.morphType` |
| Own property not yet in a catalog | `<kind>.<property>` | `entry.citation`, `sense.order`, `entry.uuid` |
| LIFT field | `field:<name>` | `field:literal-meaning` |
| Trait | `trait:<name>` | `trait:usage-type` |
| Note | `note:<type>` (empty type: `note:`) | `note:encyclopedic` |
| Annotation | `annotation:<name>` | `annotation:status` |
| Count | `count:<child>` / `count:<child>.all` (nested) | `count:senses`, `count:examples.all` |
| Ancestor | `up:<kind>/<id>` | `up:entry/entry.form`, `up:sense/field:scientific-name` |
| Direct parent (generic) | `parent:type`, `parent:label` | for notes, fields, traits, annotations |

### 2.2 Layout with languages

`ColumnLayout` becomes a list of `ColumnRef(String id, List<String> languages)`:
- `languages` empty = **all the languages** of the column, including languages added later;
- otherwise the chosen languages, **in that order** (the order of the sub-columns).

Stored form (one string, under `Preferences.MAX_VALUE_LENGTH`):

```
entry.form|entry.morphType|sense.gloss@fr,en|count:senses|up:entry/entry.form@tpi
```

plus, under a second key, the widths the user gave by dragging (`id[@lang]=width;…`).

`ColumnLayout.apply(catalog)` returns the specs **restricted to the chosen languages**
(`ColumnSpec.withLanguages(List)`), so `TableFactory` is unchanged: a multitext still gives
a group column with one sub-column per (shown) language; a group with one language is
shown as a single column titled "Gloss (fr)".

### 2.3 Value types

`ColumnSpec` gets a `ValueType { TEXT, NUMBER, DATE }`:
- `NUMBER` (counts, order): numeric comparator, right-aligned cells, facet sorted
  numerically;
- `DATE`: ISO dates sort as text already, but facets are sorted newest first.

---

## 3. Generating the catalog

New class `ui.table.catalogs.ColumnCatalog`, used by `ComponentKind.columns` in place of
the plain catalog functions:

```java
/** All the columns available for a kind, in sections, current columns first. */
static <T> List<ColumnSection<T>> of(ComponentKind<T> kind, LiftDictionary d)

record ColumnSection<T>(String id, String title, List<ColumnSpec<T>> columns) {}
```

The sections are what the column chooser shows (section 5).

### 3.1 Section "<Kind>" — own properties

- **The current catalog**, unchanged (its `visible` columns are the default layout).
- **Missing own properties**, declared once per kind in a small table, e.g.:

  | Kind | Added |
  |---|---|
  | Entry | citation form (multitext), order, date deleted, number of the homograph, UUID |
  | Sense | order, definition (if missing), gram-info traits |
  | Example | source (exists), every translation type (exists) |
  | Variant / Relation / Etymology | ref, order, usage, source… |
  | All extensible kinds | date created, date modified |

  Multitext properties are declared with `Columns.multiText`, over the languages actually
  used in that property (header languages + languages found in the data, as
  `EntryColumns` already does for forms).

### 3.2 Section "Fields, traits, notes, annotations" — extensions

Discovered from the dictionary in **one pass over the registry lists** (`getFields()`,
`getTraits()`, `getNotes()`, `getAnnotations()`), grouped by the class of their parent,
plus the field and trait definitions of the header (`LiftFieldAndTraitDefinition`, with
their target):

- one **multitext column per field name** used on (or defined for) this kind: one
  sub-column per language;
- one column per **trait name** (values joined with ", " when a trait is repeated; facet);
- one **multitext column per note type** (for `AbstractNotable` kinds);
- one column per **annotation name** (its value).

The pass is done by a `ExtensionIndex` built when the catalog is built (not at each
cell), so the cost is O(size of the dictionary) per view creation, like the existing
`annotations` frequency map.

### 3.3 Section "Counts" — subcomponents

A static description of the containment of the model:

```java
record Child<P>(String id, String i18nKey, Class<?> type, Function<P, Collection<?>> direct) {}
```

| Kind | Direct counts | Nested counts (`.all`) |
|---|---|---|
| Entry | senses, variants, pronunciations, relations, etymologies, notes, fields, traits, annotations | senses (with subsenses), examples, translations, illustrations, reversals, media |
| Sense | subsenses, examples, illustrations, reversals, relations, notes, fields, traits | subsenses, examples, translations |
| Example | translations, notes, fields, traits | — |
| Variant | pronunciations, relations, … | media |
| Pronunciation | media | — |
| … | | |

Direct counts are **live** (`Bindings.size(observableList)`); nested counts are computed
from the descendants (walk of the child lists) and refreshed with the table
(`refreshCells`). Type `NUMBER`.

### 3.4 Sections "Parent" and "<Ancestor kind>" — ancestors

- For polymorphic parents (notes, fields, traits, annotations, pronunciations…), a generic
  **"Parent"** section: parent type, parent label (the existing columns).
- One section per **kind of ancestor this kind can have**, computed from the containment
  table of 3.3 (transitive closure of "can be a child of"): e.g. Translation → Example,
  Sense, Entry; Note → Entry, Sense, Example, Variant, Pronunciation, Relation, Etymology…
- The value of `up:<kind>/<id>` is the column `<id>` of the **nearest ancestor of that kind**
  (`for (n = row.getParentNode(); n != null; n = n.getParentNode())`, as `owningSense`
  and `DictionaryQueries.owningEntry` already do), empty when there is none. This works for
  subsenses (nearest sense) and for any depth.
- An ancestor section offers the ancestor's **own properties, extensions and counts**, but
  not *its* ancestors (they are already sections of their own): no recursion, a bounded
  catalog.
- The existing "parent entry" / "parent sense" columns keep their ids and stay in the
  first section (they are defaults); the ancestor section shows them as "already shown".

### 3.5 Editing in cells

Own multitext columns of the first two sections can be made editable with the existing
`editableTexts` helper (live + commit through `formTextProperty`, one undo step). Ancestor
and count columns are read-only. (Phase 2, see question 3.)

---

## 4. `FilterableTable`: changing the columns in place

New method `FilterableTable.setColumns(List<ColumnSpec<T>> specs)`:
1. remembers the filter of each leaf (`id@lang` → text or facet value), the sort order
   (`table.getSortOrder()` by leaf id) and the selected row;
2. `TableFactory.build(table, specs)` and rebuilds the filter row;
3. restores the filters, sort order and selection of the leaves that are still there;
4. `refresh()`.

And two listeners to keep the layout in sync with direct manipulation of the header:
- **reorder** by dragging a column header (JavaFX native) → new order saved;
- **resize** → widths saved (debounced, 300 ms).

`ObjectTableView` gets a `ColumnLayoutStore` (per `ViewKey`) from `EditorContext`: it reads
the stored layout instead of `ColumnLayout.defaults`, and listens to it, so a change made in
the Preferences dialog is applied to the open table at once.

---

## 5. GUI

### 5.1 Header button + popover (main entry point)

A **"Columns" icon button** (columns glyph, tooltip "Choose columns…", `Ctrl+Shift+K`) in the
bar above the filter row, on the right, before "Reset filters". It shows a small **badge**
with the number of shown columns when the layout differs from the default.

It opens an **anchored popover** (a `PopupControl` with the app's card style, not a modal
dialog), in the style of the "Properties" menus of Notion / Airtable / Linear:

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

- **Shown** list: drag handle (⠿) to reorder; **language chips** (toggle pills) on
  multitext columns — a click hides or shows one language, a drag reorders the languages;
  ✕ removes the column.
- **Available**: collapsible sections (3.1–3.4) with counts; a click (or Enter) on an item
  appends it to *Shown*. Columns already shown are not listed.
- **Search** filters both lists on the title, the section and the language codes
  ("gloss en", "count", "entry form"); with a query, the sections open.
- **Live**: each change rebuilds the table at once (debounced 150 ms) through
  `setColumns`; filters and selection are kept (section 4). No OK button; Esc or a click
  outside closes; **Ctrl+Z** in the popover undoes the last layout change (a small local
  stack; layout changes are not dictionary changes, so they stay out of `History`).
- **↺ Default** goes back to the catalog defaults (removes the stored layout).
- Keyboard: ↑/↓ moves in the lists, Space toggles, Alt+↑/↓ reorders.

### 5.2 Header context menu (shortcut)

Right-click on a column header (`TableColumn.setContextMenu`):
- Hide "Gloss" · Hide language "en" · Show only "en"
- Add a column… (opens the popover, search focused)
- Default columns

### 5.3 Preferences ▸ Tables

`PreferencesDialog` becomes a `TabPane`: **General** (the current grid, unchanged) and
**Tables**:

```
┌ Tables ──────────────────────────────────────────────────────────┐
│ Entries         │  [ the same ColumnChooser as the popover ]      │
│ Senses        • │                                                 │
│ Examples        │                                                 │
│ Translations    │                                                 │
│ …               │                                                 │
│ Annotations     │                                                 │
│─────────────────│                                                 │
│ Reset all       │                                                 │
└──────────────────────────────────────────────────────────────────┘
```

- left: the kinds of `ComponentKinds.all()`; a dot marks a customised table;
- right: the **same `ColumnChooser` control** as the popover (one implementation, two
  hosts), here **buffered**: changes are applied on OK, dropped on Cancel;
- the catalog needs a dictionary (languages, field names): it uses the open dictionary;
  without one, only the static columns are listed and the stored ids of the others are
  kept as they are.

### 5.4 Shared control

`ui.table.ColumnChooser<T>`: input = sections + current `ColumnLayout`; output = an
observable `ColumnLayout`. The popover binds it live; the Preferences tab copies the result
on OK.

---

## 6. Persistence

`AppPreferences`:

```java
Optional<ColumnLayout> columnLayout(ViewKey key);          // "table.columns.<KEY>"
void setColumnLayout(ViewKey key, ColumnLayout layout);    // null = default
Map<String, Double> columnWidths(ViewKey key);             // "table.widths.<KEY>"
void setColumnWidths(ViewKey key, Map<String, Double> w);
```

`ColumnLayoutStore` wraps them with an `ObjectProperty<ColumnLayout>` per key, so open
views and the Preferences dialog see the same value. Layouts are **global** (not per
dictionary): ids that do not exist in a dictionary are ignored and kept (question 1).

---

## 7. Work plan

1. **Layout model** — `ColumnRef`, languages in `ColumnLayout`, parse/format,
   `ColumnSpec.withLanguages`, `ValueType` (numeric sort). Unit tests
   (`ColumnLayoutTest`: round trip, unknown ids, language subset and order, new languages
   when "all"). *No visible change.*
2. **Persistence + `FilterableTable.setColumns`** — `AppPreferences`, `ColumnLayoutStore`,
   `ObjectTableView` reads the store; drag-reorder and resize saved. TestFX: reorder a
   column, navigate away and back, order kept.
3. **Generated catalog** — `ColumnCatalog`, sections, `ExtensionIndex`, the containment
   table (counts, ancestor kinds), missing own properties. Unit tests on the mock dictionary
   (`mock-dictionary.json`): a field gives one sub-column per language; counts; ancestor of
   a subsense example; note on an example → its entry. **Default layouts identical to
   today** (test: `ColumnLayout.defaults` of each kind = the ids shown today).
4. **`ColumnChooser` + header popover + context menu** — CSS in `app.css` and the dark
   theme; i18n (en, fr). TestFX: add a count column, hide a language, reset.
5. **Preferences ▸ Tables** — `TabPane`, kind list, buffered chooser, "Reset all".
   TestFX: change in the dialog → open table updated; Cancel → unchanged.
6. **Editable own columns** (optional, question 3) and **docs** (`documentation/…/configuration`,
   README screenshot).

Each step builds and passes `mvn test` on its own; steps 1–3 are invisible to the user
except for the kept reorder/resize.

---

## 8. Questions

1. **Global or per-dictionary layouts?** Proposed: global, unknown ids ignored. A
   per-dictionary override (keyed like the LaTeX settings, by file) could come later.
2. **Nested counts**: is the proposed list (3.3) right, or should every descendant kind be
   offered (larger catalog)?
3. **Editing**: should the added own-property columns (citation form, field texts, note
   texts) be editable in the cells, like forms and translations today?
4. **Entries table**: the same mechanism applies (`EntryView` uses the entry catalog); OK
   to include the entry table, with its current columns as default?
