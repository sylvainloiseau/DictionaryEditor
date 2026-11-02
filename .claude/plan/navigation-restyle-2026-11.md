# Restyling the navigation panel

Date: 2026-11-02. Status: implemented (§9).

## 1. Today

`ui/nav/NavigationTree` fills a `TreeView<String>` (`#navTree`, class `nav-tree`) inside the
`left-nav` VBox of `MainView.fxml`, under a "Navigation" label (`section-title`, 20 px bold
white).

The items, which stay **exactly** as they are (same groups, same order, same labels):

| Group | Items |
|---|---|
| Objects | Entries, Senses, Examples, Translations, Notes, Variants, Pronunciations, Media, Etymologies, Relations, Reversals, Illustrations, Fields, Traits, Annotations (from `ComponentKinds.keys()`) |
| Languages | Object languages, Meta-languages |
| Category browser | Grammatical info, Trait values, Translation types, Note types, Relation types, Field types |
| Dictionary configuration | Dictionary description, Field and traits definitions, Manage languages, Manage note types, Manage translation types, Manage annotation types, Manage relation types, **Ranges ▸** (one item per header range) |
| Quick entry | (a leaf at the top level) |

What could be better:

- **The look** is a solid teal block (`#4c6f76`) with white 14 px text.
  - Groups and items look the same, apart from the arrow.
  - The selection is a faint white veil.
  - The "Navigation" title takes space and tells nothing.
- **The ranges** come in file order. The demo has 37, e.g. `etymology`, `grammatical-info`, …, `Nom-slot`, `Verbe-slot`. They are hard to scan.
  - The label is the range's *first* label form, whatever its language.
- **Nothing tells how much there is** in a view: no counts.
- **The context menu "Create object"** is on every item: languages, ranges, groups. It creates in the *current* view, not in the item clicked.
- **Typing a view's name does nothing:** 70 items once the ranges are open, and no way to filter them.
- **Fixed colours** throughout: no dark mode. The `Segoe UI` font does not exist on macOS.
- **The code:**
  - a `TreeView<String>` with side maps (`requests`, `rangeOfItem`) to know what an item is;
  - two items with the same text are told apart only by identity;
  - adding an icon or a count would mean more maps.

## 2. Principles

1. **Same structure, clearer hierarchy:**
   - groups become section headings, not items;
   - items are rows with an icon, a label and a count;
   - one selected row, clearly marked.
2. **Calm and dense.** A light, neutral sidebar, like Finder, Mail, VS Code or Notion. The brand teal is kept as the **accent**, for the selection and the focus. The content stays the focus of the window.
3. **Keyboard first:** a filter field, arrows, Enter, the existing Cmd+1…9 shown in tooltips.
4. **Themeable:**
   - every colour is a named token (CSS looked-up colour) defined once;
   - a dark theme redefines only the tokens, following the system (JavaFX 25: `Platform.getPreferences().colorScheme()`).
5. **One model of the tree:** each item knows what it is (a group, a view or a range), its icon, its count and its request. Future items (a saved filter, a recent view) are a new kind of node, not a new map.

## 3. The design

```
┌────────────────────────────┐
│ 🔍 Filter views…        ⌘K │  ← filter field (replaces the "Navigation" title)
│                            │
│ OBJECTS                  ▾ │  ← group heading: small caps, muted, chevron on hover
│ ▌▣ Entries          2 101  │  ← selected: accent bar + tinted pill, bold label
│  ▣ Senses           2 874  │
│  ▣ Examples           412  │
│  …                         │
│ LANGUAGES                ▾ │
│  ◎ Object languages    11  │
│  ◎ Meta-languages       2  │
│ CATEGORY BROWSER         ▾ │
│  …                         │
│ DICTIONARY CONFIGURATION ▾ │
│  ⚙ Dictionary description  │
│  …                         │
│  ▸ ☰ Ranges            37  │  ← a nested item: folds, keeps its count
│      Anthro code           │
│      Etymology             │  ← sorted A→Z (§4)
│      grammatical-info      │
│      Nom-slot     (grey)   │  ← undeclared: grey, italic; tooltip says why
│ ────────────────────────── │
│  ⚡ Quick entry             │  ← apart, at the bottom, as an action
└────────────────────────────┘
```

**The panel:**

- in a `SplitPane` with the centre, so it can be resized: 240 px by default, the width kept in the preferences;
- View ▸ Show navigation (Shortcut+0) folds it away.

**Group headings:**

- 11 px, semibold, upper case, letter-spaced, muted colour;
- not selectable: the selection skips them, and the keys go to the next item;
- a click or Enter folds and unfolds the group. The folded groups are kept in the preferences.

**Rows:**

- 28 px high, 13 px text; a 16 px monochrome icon; a right-aligned count in a muted badge (§5);
- hover: a light tint;
- selected: a rounded tinted pill (accent 14 %), a 3 px accent bar on the left, a semibold label;
- the focus ring only with keyboard focus (`:focus-visible` style through a pseudo-class).

**Icons:**

- one per view, drawn as SVG paths through `-fx-shape` on a `Region`: no new dependency, coloured by the theme tokens;
- same icon for the same family. For example, the "types" of the category browser share a tag icon; the "Manage …" items share a sliders icon.

**Quick entry** stands apart at the bottom: it is an action (a form to type in), not a list.

**Tooltips:**

- the full label when it is cut;
- the shortcut (`⌘2`);
- for a range: its id, and "not declared in the file" when undeclared.

**Undeclared ranges:** grey and italic, as today, plus the tooltip.

**Colour tokens** on `.root`:

- `-nav-bg`, `-nav-fg`, `-nav-muted`, `-nav-hover`, `-nav-selected-bg`, `-nav-selected-fg`;
- `-accent` = `#4c6f76` (the light theme) / `#7fb0b8` (the dark theme).

## 4. The ranges, sorted

- **Label shown:**
  - the range's label in the interface language when it has one;
  - else in the first meta-language;
  - else its first label;
  - else its id.

  This replaces "first form whatever its language".
- **The sort:**
  - by that label, A→Z, with a `java.text.Collator` for the interface locale at PRIMARY strength, so that case and accents do not split the list: `anthro-code`, `Adjectif-slot`, `étymologie` sort together;
  - ties are broken by the id, so the order is stable.
- **Declared and undeclared** stay mixed in the sort (the grey tells them apart). Separating them is an option (§7 Q4).
- **The tree is resorted** when a range is added, removed or renamed. Today only "declared" is listened to; the label and the list of the header's feature sets will be too.

## 5. Counts

| Item | Count |
|---|---|
| each view of Objects | the number of rows of its table (the registry's lists) |
| Object / Meta-languages | the number of languages |
| Category browser items, Manage … types | the number of types / values |
| Ranges | the number of ranges; each range: its number of values |
| Description, Field definitions, Quick entry | none |

- Counts are `IntegerBinding`s on the observable lists of lift-api, so they stay live with
  undo and redo. A list that is not observable gets its count updated on the document's
  revision.
- A row that shows a subset (a drill-down) keeps the full count; the table's own label says
  "12 of 2 101".
- Large numbers are grouped by the locale (`2 101` / `2,101`).

## 6. Behaviour improvements

1. **A filter field** at the top. Shortcut+K focuses it.
   - Typing keeps the items whose label contains the text, accent-insensitive, with their groups. It searches the closed groups and the ranges too, and highlights the matched part.
   - Enter opens the first match. Esc clears the filter and gives the focus back to the tree.
2. **Context menus per kind:**
   - an Objects view: "New <kind>", only where creating is possible (entries; the other kinds are created inside their owner);
   - a range: "Copy id", "Open in Dictionary configuration";
   - a group: "Fold all / Unfold all".

   The global "Create object" goes away from the other items.
3. **Shortcuts shown:** Cmd+1…9 appear in the tooltips, taken from `Commands`, so a shortcut the user changed is the one shown.
4. **The panel remembers** its width, the folded groups and whether it is shown.
5. **Accessibility:**
   - `accessibleText` = "Senses, 2 874 items";
   - group headings have the role of a heading;
   - contrast ≥ 4.5:1 for text and 3:1 for the icons and the selection bar, in both themes.

## 7. Questions

Answers (2026-11-02):

- Q1 to Q4: the recommendations — (a)+(c) light, dark with the system; counts on every row; SVG icons in the code; one A→Z list of ranges.
- Q5: all three (Show navigation with Shortcut+0, resizable panel, filter field).
- Q6: yes.

1. **The look**:
   - (a) light neutral sidebar with a teal accent *(recommended)*;
   - (b) keep the teal background, modernised (headings, pill selection, icons, counts);
   - (c) follow the system: light or dark.

   (a) and (c) combine: light by default, dark when the system is dark.
2. **Counts** on every row (§5)?
   - (a) Yes *(recommended)*.
   - (b) Only on the Objects.
   - (c) None.
3. **Icons**:
   - (a) SVG paths in the code, no dependency *(recommended)*;
   - (b) the Ikonli library (Material icons), one more dependency and module.
4. **The ranges**:
   - (a) one A→Z list, undeclared in grey *(recommended)*;
   - (b) declared first, then undeclared, each A→Z.
5. **Show navigation / Shortcut+0, the resizable panel and the filter field**: all three *(recommended)*, or only some?
6. **The context menu**: OK to remove "Create object" from the items where it does not create what the item shows (§6.2)?

## 8. Implementation

1. **The model:**
   - `ui/nav/NavNode` is a sealed interface: `Group(key)`, `View(ViewKey, ViewRequest)`, `Range(FeatureSet)`, `Action(ViewKey)` (Quick entry);
   - each node gives its label, icon id, count (`ObservableIntegerValue` or null), tooltip and request;
   - `TreeView<NavNode>` replaces `TreeView<String>`. The maps `requests` and `rangeOfItem` go;
   - the order of the items stays in one place, `NavigationTree`'s constructor, unchanged.
2. **The cell, `NavCell`:**
   - an `HBox` with the icon `Region`, the `Label` and a spacer, then the count `Label`;
   - pseudo-classes `:group`, `:undeclared`, `:folded`, `:match`;
   - a group is unselectable through a selection model that skips groups.
3. **Ranges:** a `SortedList` over the header's feature sets, with the comparator of §4. It is rebuilt when the locale changes (the app reloads the scene anyway).
4. **CSS:**
   - a new block `/* ─── Navigation ─── */` in `app.css`, with the tokens on `.root`;
   - `.nav-tree` rules rewritten; `.left-nav` and the old `section-title` use in the panel removed;
   - the root font becomes `System`. This changes the whole app slightly on macOS (§7 Q1 shows it).
5. **The filter:** a `TextField` `#navFilter` over the tree. It sets a predicate on the `TreeItem`s, rebuilding the children of each group from the full list of nodes.
6. **The panel:**
   - `MainView.fxml`: `left` becomes the first pane of a `SplitPane` (`#navSplit`);
   - `AppPreferences` gets `navWidth`, `navFoldedGroups` and `navShown`;
   - View ▸ Show navigation gets id `view.showNavigation` and a default of Shortcut+0 in `Commands`.
7. **Messages:**
   - `nav.filter.prompt`, `nav.count.accessible`, `nav.range.undeclared`, `nav.foldAll`, `nav.unfoldAll`, `nav.copyId`, `menu.view.showNavigation`;
   - `panel.navigation` is kept only as the tree's accessible name.

**Tests:**

- **`MainWindowTest` helpers** (`navTree`, `openNavItem`, `navLeaves`): adapted to `TreeView<NavNode>`, matching on the label. The 4 test files using them keep their calls.
- **New `NavigationTest`:**
  - the same items in the same order as today (a frozen list of the labels);
  - the ranges A→Z with the collator, and the order re-sorted when a range is renamed;
  - the counts of Entries and Senses equal the tables' rows, and follow an undo;
  - a group is not selected by the arrows;
  - the filter keeps the matches and their groups; Enter opens the first one;
  - the folded groups and the width are restored after a restart;
  - Shortcut+0 hides and shows the panel;
  - the tooltip shows the shortcut changed in the shortcuts dialog.
- **A headless snapshot** of the panel in light and dark, to check the layout.

Order of work: §4 (sorting, standalone and small) → model and cell → CSS and icons → counts → filter → panel (SplitPane, prefs, Shortcut+0) → context menus.

## 9. Status (2026-11-02): implemented

- **Model and cell:**
  - `ui/nav/NavNode` (Group, View, Range) and `NavIcons` (SVG paths);
  - `NavigationTree` rewritten: `TreeView<NavNode>`, its own cell, counts, filter, folding, context menus.
- **Counts:**
  - Objects, languages, "Manage …" types, ranges and range values: live bindings on lift-api's observable collections;
  - category views: `CategoryViews.rowCount`, recomputed 250 ms after `History.revisionProperty()` changes (new).
- **Panel:**
  - `MainView.fxml`: `#navSplit` holds `#navPanel` (filter `#navFilter` and tree) and `#mainSplit`;
  - View ▸ Show navigation (Shortcut+0) and View ▸ Filter navigation (Shortcut+K);
  - preferences `ui.nav.width` (default 270 px), `ui.nav.shown`, `ui.nav.folded` (default: Ranges folded).
- **Stylesheet:**
  - `/* ─── Navigation panel ─── */` with the `-nav-*` tokens on `.root`, redefined on `.root.dark`;
  - the root font is `System`.
- **Tests:**
  - `NavigationPanelTest` (11 tests);
  - helpers of `MainWindowTest` and three tests adapted to `TreeView<NavNode>`;
  - full suite 254/254.

Deviations and details:

- **Theme:** Preferences gain a third choice, "System", which follows the system and switches live. The default stays **Light**. The rest of the app's dark theme is only a change of `-fx-base`, and switching every Mac in dark mode to it by default would expose that. The navigation follows whatever theme is in effect (class `dark` on the root).
- **Section headings** are upper-cased in code (JavaFX CSS has no text-transform), with no letter-spacing.
- **Filter match:** shown in bold, underlined, in the accent colour, rather than with a tinted background. A `TextFlow` cannot tint a part, and labels side by side left gaps.
- **Language views** count languages, not rows (their rows are forms).
- **Context menu of a range:** "Copy id" only. "Open in Dictionary configuration" was dropped, since a range already opens there.
- **Not done:**
  - a `:focus-visible` ring;
  - a test of the shortcut shown in the tooltip;
  - a "restart" test (the preferences written are tested instead).
- **Checked only headless:** light, dark and filtered snapshots, not on a real Mac.
