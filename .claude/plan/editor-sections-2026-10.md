# Component sections in the editors: count, add, delete (plan, 2026-10-31)

## Goal

Every list of components shown in an editor (package `fr.cnrs.lacito.liftgui.ui.controls`)
looks and behaves the same way:
- its title bar shows the **number of components**, "(0)" included, and never "None";
- its title bar carries a small **[+] button** that creates a component, without opening the pane first;
- each component that can be deleted has a small **delete button at its top**.

---

## 1. Current state

### 1.1 Lists of components, by editor

| Editor | Section | Count | Add | Delete |
|---|---|---|---|---|
| `LiftEntryEditor` | Pronunciations, Senses, Variants, Relations, Etymologies | yes | buttons **at the bottom** of the form (`addButtons`) | yes |
| `SenseEditor` | Examples, Relations, Reversals, Sub-senses | no | **none** in the editor; in the Senses view, buttons at the bottom (`EditorPresenters.senseAddButtons`, which also adds notes) | yes, if add actions |
| `ExampleEditor` | Translations | no | green button inside the pane | yes |
| `VariantEditor` | Pronunciations, Relations | no | green buttons inside the panes | yes |
| `ExtensibleWithoutFieldEditor` | Traits, Annotations | no | green buttons inside the panes, only if add actions | only if add actions |
| `ExtensibleWithFieldEditor` | Fields | no | same | same |
| `NotableEditor` | Notes | no | same | same |
| `MultiTextEditor` | Annotations | no; **"None"** when empty and read-only | green button inside the pane | yes, if editable |
| `TraitEditor` | Annotations | no | **none** | **none** |
| `ReversalEditor` | Main reversal (0 or 1) | no | none | none |
| `SenseEditor` | Illustrations | **not shown** | — | — |
| `PronunciationEditor` | Media | **not shown** | — | — |

### 1.2 Why some components can't be added or deleted

The traits, annotations and fields can be added and deleted only when the editor gets an
`ExtensibleAddActions`. Several editors pass none to their nested properties editor:
- `FieldEditor`, `NoteEditor`, `PronunciationEditor`, `RelationEditor` and `EtymologyEditor` call `extensibleEditor.setModel(x)`;
- `TraitEditor` builds its `AnnotationEditor`s without delete.

So a field can't be deleted inside a note, and a trait gets no annotation, for instance.
In the separate views (Notes, Fields, …), `EditorPresenters.showX` also builds these
editors without actions. lift-api can delete every component (`deleteField`,
`deleteTranslation`, `deleteMain`, `deleteMedia`, `deleteIllustration`…). The limits come
only from the GUI.

### 1.3 Other differences

- **Type choice.** It goes through a modal `ChoiceDialog`, or a `TextInputDialog` when the
  range is empty. This code is written 9 times, with small differences:
  - the translation filters out the types already used;
  - the relation in `LiftEntryEditor` is disabled with a tooltip when there is no type;
  - the relation in `VariantEditor` falls back to free text.
- **Notes and fields are keyed by type** (`ObservableMap`), so there is one per type.
  Today, choosing a type that is already used is not prevented.
- **Deleting.** Inside an editor, a component is deleted without confirmation
  (`ComponentDeletion`). In its own view it is deleted with a confirmation
  (`DeleteActions`). Both can be undone.
- **Appearance.** Three styles of add button: plain buttons, the green `example-add-button`
  stretched to the full width, and none. The delete button (`withDeleteButton`) is a red
  "Delete" text above the component, alone on its line.

---

## 2. Design

### 2.1 The section title bar

```
▸ Traits  2                                   [+]
▾ Annotations  0                              [+]
▾ Senses  3                                   [+]
  ┌──────────────────────────────────────────────┐
  │ #1  to walk                              [🗑] │
  │  …sense editor…                               │
  └──────────────────────────────────────────────┘
```

- **Count.** The count is shown as a small grey badge after the title, dimmed when 0. It
  replaces both the "(n)" text and the "None" label. The count stays in the label text as
  well ("Traits (2)"), for screen readers and tests.
- **[+] button.** Small, flat and borderless, light grey, aligned right in the title bar, and
  always visible (Q4).
  - Its tooltip is the current `btn.addX` text ("+ Trait" → "Add a trait").
  - Clicking it does not open or close the pane. After the creation, the pane opens.
- **One type to pick.** When the component needs a type (trait, annotation, field, note,
  relation, reversal, translation), the [+] is a `MenuButton` that lists the types
  directly. That takes one click instead of a modal dialog.
  - The types already used are disabled for notes, fields and translations, since there
    is one per type.
  - The last item is "Other…" (free text) when the range is open or empty.
  - Beyond about 15 types, the menu starts with a filter field.
  - When there is no type at all, the [+] is disabled, with a tooltip saying which range
    to fill in.
- **Read-only.** A section with no add action shows the count but no [+].

### 2.2 The item card

- Each component in a section is a card with a header row: a short label on the left and
  the delete button on the right.
  - The label is, for example, "status = done", "#2 to walk" or "note: grammar".
  - The delete button is a small trash icon, or "✕", grey and red on hover, with the
    tooltip "Delete this trait".
- This replaces the "Delete" button that sits alone on its line. It keeps the button at
  the top, as today, and costs no extra line.
- The header row has no delete button when the component cannot be deleted. In practice
  that only happens in read-only contexts.

### 2.3 After an action

- **After a creation**, the pane opens. The new component is scrolled into view, its first
  field gets the focus, and it is highlighted briefly (a 1 s fade). The user can type
  immediately.
- **After a deletion**, the focus goes to the next card, or to the [+] when the list is
  empty.

### 2.4 Deleting: confirm or not (decided, Q3)

Every deletion can be undone (Cmd+Z).
- **No confirmation** for the "leaf" components: trait, annotation, field, note,
  translation, pronunciation, relation, media, illustration.
- **A confirmation** for the main components, when they are not empty: sense, example,
  variant, etymology, reversal.
  - "Not empty" means that the component has a form, text or child component.
  - An empty one, such as one just created by mistake, goes without asking.
  - The entry, deleted from its own view, keeps its confirmation.
- The same rule applies inside an editor (`ComponentDeletion`) and in a component's own
  view (`DeleteActions`).

### 2.5 Sense, sub-sense, example: created empty (decided, Q2)

The [+] creates the component at once, with no dialog, and puts the focus in its first
field. This replaces the gloss/text dialogs of `LiftEntryEditor.buildAddButtons` and
`EditorPresenters.senseAddButtons`.

| Component | Created with | Focus |
|---|---|---|
| Sense, sub-sense | nothing (no gloss, no grammatical info) | the gloss, in the first meta-language |
| Example | nothing | the example text, in the first object language |

- **Undo.** The creation is one undoable step, so Cmd+Z right after the [+] removes the
  component.
- **Empty components that remain.**
  - An empty sense is titled "#2 (empty)" instead of "?", in grey italics, so it is easy
    to spot.
  - Deleting it asks nothing (§2.4).
  - Nothing is removed automatically: an empty sense is valid LIFT.
- **lift-api.** `SenseBuilder` and `ExampleBuilder` already build without any form, as
  `VariantBuilder` does for the variants created empty today. Nothing to change there.
- **Tests.** The tests of the sense and example dialogs (`CreationTest`) change
  accordingly.

### 2.6 Hiding the empty sections (option)

- A View menu item, "Hide empty sections", is saved in `AppPreferences`. It is off by
  default.
- When it is on, the sections at 0 disappear. An "Add…" menu at the end of each editor
  lists them (the same entries as their [+]).
- Creating a component through that menu shows its section again, with the focus in the
  new component.

### 2.7 Totals on the properties panes

The "Properties" panes (dates + traits + annotations + fields + notes) show in their
title the total of the components inside, with the same badge. A closed pane thus tells
whether it contains anything.

### 2.8 Keyboard

- **Adding.** When the focus is in a section (its title bar, or any field of a component
  inside it), Cmd+Shift+N (Ctrl+Shift+N elsewhere) opens the [+] of that section:
  - it creates the component when there is nothing to choose;
  - it shows the menu of types when there is a type to choose.

  In nested sections, the innermost one wins: in a trait's annotations, it adds an
  annotation, not a trait. Cmd+Shift+N is free today: the only shortcuts are undo and
  redo.
- **Context menu.** A right-click, the context-menu key or Shift+F10 opens a context menu.
  - On a section title, it offers "Add …", with the types as a submenu.
  - On a card, it offers "Add …" and "Delete".

  The context-menu key exists on Windows and Linux keyboards. On macOS there is none, so
  it is the right-click (or Ctrl+click), together with Cmd+Shift+N.
- **Deleting.** Delete or Backspace on a focused card header deletes the component, with
  the rule of §2.4. The header row is focusable for that. Inside a text field, the keys
  keep editing the text.

### 2.9 Nesting

Each level of nesting gets a left border (2 px, a shade per level) and an indentation,
instead of the nested boxes with their background colours. This makes it clear which
component a card belongs to, and gives back horizontal space in deep nesting (a note's
field's trait's annotation).

### 2.10 An "Add" menu, with a shortcut per kind of component

A new **Add** menu in the menu bar, between Edit and View, lists every kind of component:

```
Add
  Sense                 ⌥⌘S
  Sub-sense
  Example               ⌥⌘E
  Translation
  Pronunciation         ⌥⌘P
  Variant
  Relation              ⌥⌘R
  Etymology
  Reversal
  Illustration
  Media
  ──────────
  Note                  ⌥⌘N
  Field                 ⌥⌘F
  Trait                 ⌥⌘T
  Annotation            ⌥⌘A
  ──────────
  In this section…      ⇧⌘N
```

- **Menu bar.** On macOS the menu is in the system menu bar (`useSystemMenuBar`), like the
  others. A menu item's accelerator is its shortcut, so the menu shows the shortcuts by
  itself.
- **Default shortcuts (decided, Q6).** Cmd+Option + the initial (Ctrl+Alt elsewhere), as
  shown above, for Sense, Example, Pronunciation, Relation, Note, Field, Trait and
  Annotation. The other kinds have none by default. D, H and M are avoided, because macOS
  uses Cmd+Option with them.
- **Target.** Each item adds to the component open in the editor, or to the one around the
  focus (see "Choosing the target" below). It is disabled when there is no such component.
  For example, Example is disabled while a note is displayed. A disabled item's shortcut
  does nothing.
- **Same path as the [+].** An item does what the [+] of the matching section does:
  1. it shows the section if it is hidden (§2.6);
  2. it opens the section;
  3. it creates the component, or opens the type menu under the [+] when a type is
     needed;
  4. it puts the focus in the new component.
- **"In this section…"** is the contextual Cmd+Shift+N of §2.8.

**Choosing the target.** The component open in the editor is already known:
`EditorPanel.show(title, code, edited)` receives it. `EditorContext` will expose it as a
property, `editedComponent`. Each `ItemCard` records its component in the node's
properties. The target of an item is found in this order:
1. **The focused component.** Starting from the focus owner, go up the cards to the
   first component that can hold this kind. For example, with the focus in sense #2's
   gloss, Example adds to sense #2, and Note adds a note to sense #2.
2. **The displayed component.** Without a focus, the component open in the editor.
3. **The only candidate, one level down.** For example, Example while an entry is
   displayed adds to its sense if the entry has exactly one sense. Otherwise the item is
   disabled until the focus is in one of the senses (decided, Q7); its tooltip in the
   menu says so.

Two items have a fixed meaning, so that there is no ambiguity:
- **Sense** always adds a sense to the *entry*, the displayed one or the one around the
  focus;
- **Sub-sense** always adds to a *sense*.

**Updating.** The enabled state of the items is recomputed when the editor changes
(`editedComponent`) and when the focus moves (`Scene.focusOwnerProperty`). It is cheap: a
walk up the scene graph.

### 2.11 The keyboard shortcuts dialog

**Opening it.** Help ▸ Keyboard Shortcuts…, or from a "Shortcuts…" button in the
Preferences dialog. The Help menu is new, and also takes "About" later. On macOS,
Cmd+/ opens the dialog.

```
┌ Keyboard Shortcuts ────────────────────────────────────────────┐
│ 🔍 [filter                ]                                    │
│ Command                       Shortcut          Default        │
│ ▾ File                                                         │
│   Save                        ⌘S                ⌘S             │
│   Save As…                    ⇧⌘S               ⇧⌘S            │
│ ▾ Add                                                          │
│   Example                     ⌥⌘E               ⌥⌘E            │
│   Variant                     [press keys…]  ⟲  —              │
│ ▾ Edit (fixed)                                                 │
│   Copy                        ⌘C  🔒                            │
│                                                                │
│ ⚠ ⌥⌘P is already used by Add ▸ Pronunciation. [Replace] [Cancel]│
│                                       [Reset all]  [Close]     │
└────────────────────────────────────────────────────────────────┘
```

**The table.** A `TreeTableView` that lists every command of the menus, grouped by menu,
with its current shortcut and its default. The filter field matches the name or the
shortcut ("⌘S", "cmd+s").

**Changing a shortcut.**
- Double-click the shortcut, or press Enter on it. The cell then waits for keys
  ("press keys…") and records the first combination that has a modifier.
- Esc cancels. Backspace removes the shortcut.
- ⟲ puts back the default of that line. "Reset all" puts back every default.

**Checks at recording.**
- **Conflict.** When the combination is already used, the dialog names the command and
  offers Replace (the other command loses it) or Cancel.
- **Refused.** The keys of text editing and of the system:
  - Cmd+C/V/X/A/Z, Cmd+Shift+Z and Cmd+Y;
  - Cmd+Q/H/M/W/Tab, Cmd+Space and Cmd+Option+D/H/M/Esc on macOS;
  - Alt+F4 elsewhere;
  - a key with no modifier, other than the F keys.

  A text field would take these keys, or the system would, so they could not work.
- **Fixed commands.** The Edit commands (copy, paste, cut, undo, redo) are listed with
  their shortcut and a lock. They follow the system and cannot be changed.

**Saving.** The shortcuts are saved in `AppPreferences`, under the key `shortcut.<command
id>`, and only when they differ from the default: an empty value means "no shortcut".
They apply at once, with no restart: the accelerators of the menu items are reassigned.

**Platforms.** The shortcuts are stored portably, with `Shortcut` for Cmd on macOS and
Ctrl elsewhere (`KeyCombination.getName()`/`valueOf`), and shown with the platform's
symbols (`getDisplayText()`).

### 2.12 Default shortcuts of the general commands (decided, Q8)

The menus get the usual shortcuts. "Shortcut" is Cmd on macOS and Ctrl elsewhere.

| Command | Default | Notes |
|---|---|---|
| File ▸ New | Shortcut+N | |
| File ▸ Open… | Shortcut+O | |
| File ▸ Save | Shortcut+S | |
| File ▸ Save As… | Shortcut+Shift+S | |
| File ▸ Preferences… | Shortcut+, | the macOS convention; on the other systems as well |
| File ▸ Quit | Ctrl+Q (Windows, Linux) | On macOS, Cmd+Q is the application menu's Quit (see `QuitTest`); the File item gets no shortcut there, and the command is listed as fixed. |
| Edit ▸ Undo, Redo | Shortcut+Z, Shortcut+Shift+Z / Shortcut+Y | unchanged (`UndoKeys`), fixed |
| Edit ▸ Copy, Paste, Cut | Shortcut+C, V, X | shown in the menu; fixed, the text fields handle them |
| Help ▸ Keyboard Shortcuts… | Shortcut+/ | |

**The views: Shortcut+1 … Shortcut+9**, for the 9 most used tables of View ▸ Objects:

| Key | View | Key | View | Key | View |
|---|---|---|---|---|---|
| 1 | Entries | 4 | Notes | 7 | Relations |
| 2 | Senses | 5 | Variants | 8 | Etymologies |
| 3 | Examples | 6 | Pronunciations | 9 | Fields |

- The other views (Translations, Media, Reversals, Illustrations, Traits, Annotations,
  Languages, Categories) have no default shortcut. They can get one in the dialog: every
  item of the View menu and its submenus is a command.
- To give these items commands, the View ▸ Objects items, built in `MainController` from
  `ComponentKinds.keys()`, get the id `view.<key>` (e.g. `view.entries`). The FXML items
  of View ▸ Languages and View ▸ Categories get ids too.
- **Shortcuts in text fields.** Shortcut+1…9 and Shortcut+N/O/S are not taken by text
  fields, so they also work while typing in an editor.
- **Saving before switching.** Switching the view while typing first commits the text
  being edited, as a click on the navigation tree does today.

---

## 3. Implementation

1. **`ComponentSection`** (new, extends `TitledPane`): the title, the count, the [+] and the item cards.
   - Methods: `setCount(int)`, `setAdd(Runnable)`, `setAddChoices(TypeChoices, Consumer<String>)`, `setItems(List<Node>)`, `reveal(Node)`.
   - The title graphic is an `HBox` of a label, the badge, a spacer and the [+]. Its width
     is bound to the pane's width minus the arrow, so that the [+] stays on the right.
   - The [+] consumes its mouse events, so that the pane does not toggle. This needs
     checking under TestFX and on macOS.
   - Ids for the tests: `add-<kind>` (e.g. `add-trait`), and a style class `component-section`.
2. **`ItemCard`** (new): the header row (label, delete) above the editor. It replaces
   `EditorSupport.withDeleteButton`.
3. **`TypeChoices`** (new): a single type choice, built from a `FeatureSet` or from the
   field/trait definitions.
   - It covers the types already used, "Other…", the filter beyond 15 types, and the
     disabled state with its tooltip.
   - It replaces the 9 `ChoiceDialog`/`TextInputDialog` blocks.
4. **Add actions everywhere.**
   - `FieldEditor`, `NoteEditor`, `PronunciationEditor`, `RelationEditor`,
     `EtymologyEditor`, `TraitEditor` and `TranslationEditor` take an
     `ExtensibleAddActions` and pass it on to their nested editors.
   - `EditorPresenters.showX` gives one (`addActions(x)`), so the separate views can edit
     too.
   - `ExtensibleAddActions` gains `addExample`, `addReversal`, `addSense`,
     `addTranslation`, `addEtymology`, `addVariant`, `addMedia` and `addIllustration`,
     implemented in `BuilderAddActions`. Each returns the new component, so that the
     editor can focus it.
5. **Migrating the editors**, one commit per group:
   1. the extensible editors (Traits, Annotations, Fields, Notes) and `MultiTextEditor`: "None" is removed;
   2. `TraitEditor`: annotations with [+] and delete;
   3. `LiftEntryEditor`: the [+] in the 5 sections. `addButtons` and the gloss and
      grammatical-info dialogs are removed (§2.5);
   4. `SenseEditor`: [+] for Examples, Relations, Reversals and Sub-senses.
      `EditorPresenters.senseAddButtons` is removed (§2.5);
   5. `ExampleEditor` (Translations) and `VariantEditor` (Pronunciations, Relations);
   6. `ReversalEditor`: Main (0 or 1: the [+] is disabled once there is one);
   7. new sections (Q1):
      - **Illustrations** in `SenseEditor`: the [+] opens a file chooser, which starts in
        the illustrations folder next to the LIFT file when there is one. The cards are
        `ExternalDocumentEditor`s;
      - **Media** in `PronunciationEditor`: the same, for the audio files.
6. **Deleting:** the rule of §2.4 in `ComponentDeletion` and `DeleteActions`, with a
   helper `isEmpty(component)` (no form, text or child component).
7. **Totals (§2.7):** the properties editors expose their total, and the pane holding them
   shows it.
8. **Hiding the empty sections (§2.6):** the View menu item and the `AppPreferences` key.
   Each editor gets an "Add…" `MenuButton`, fed by its hidden sections.
9. **Keyboard (§2.8):**
   - an event filter on each `ComponentSection` for Cmd+Shift+N; the innermost section
     consumes it;
   - a `ContextMenu` on each section title and card header (right-click,
     context-menu key, Shift+F10);
   - Delete/Backspace on the focused card header.
10. **Nesting (§2.9):** CSS only. The `setStyle` borders and backgrounds of
    `FieldEditor`, `PronunciationEditor`, `SenseEditor`, etc. move to classes in `app.css`.
11. **CSS (`app.css`):**
    - new classes `.component-section`, `.section-count`, `.section-add`, `.item-card`,
      `.item-card-header`, `.item-delete`, `.item-empty` and `.nesting-<n>`;
    - `.example-add-button` and the inline `-fx-…` styles of the delete button are removed.
12. **i18n (en/fr):**
    - `tooltip.add.<kind>`, `tooltip.delete.<kind>`, `menu.otherType` and
      `tooltip.noType.<range>`;
    - `menu.add`, `menu.add.<kind>`, `menu.add.inSection`, `menu.hideEmptySections`,
      `menu.help` and `menu.help.shortcuts`;
    - `sense.empty`, and the texts of the shortcuts dialog (`shortcuts.*`);
    - `editor.none` is removed once it is unused.
13. **Commands (§2.10–2.11).**
    - **`Command`** (new record): an id (`file.save`, `add.example`…), a label key, a
      group, a default `KeyCombination` (or none), and whether it is fixed.
    - **`Commands`** (new): the registry of all the commands.
      - It is filled from the menu items: each `MenuItem` gets an id equal to the command
        id.
      - It applies the saved shortcuts to the accelerators.
      - It is the single place that knows the defaults and the refused combinations.
    - The general commands and their defaults (§2.12) are registered there, as are the
      `view.<key>` items of the View menu.
    - `UndoKeys` stays, for the keys inside text fields. Its combinations are registered
      as fixed commands.
14. **The Add menu (§2.10).**
    - It is built in `MainController` from the list of kinds.
    - Each item calls `AddCommands.add(kind)`, which:
      1. finds the target (focus, then the displayed component, then the only candidate);
      2. reaches the editor's `ComponentSection` for that kind and that target;
      3. calls its add action.
    - `EditorContext` gets `editedComponent`, set by `EditorPanel.show`.
    - `ItemCard` puts its component in `getProperties()`.
    - A listener on `editedComponent` and on the scene's `focusOwner` recomputes the
      disabled state of the items.
15. **`ShortcutsDialog`** (new, in `ui/app` next to `PreferencesDialog`).
    - It is the dialog of §2.11.
    - It edits a copy of the shortcuts, which "Close" applies through `Commands` and
      saves.
    - It is opened from the Help menu and from the Preferences dialog.
16. **Tests (TestFX):**
    - `CreationTest`/`UndoTest` look buttons up by id (`#add-note`) instead of by text,
      and create senses and examples without a dialog.
    - New test `ComponentSectionsTest`:
      - every section shows its count, including 0, and a properties pane its total;
      - the [+] of a closed pane does not toggle it, creates the component, then opens the pane;
      - a type already used is disabled;
      - a field inside a note can be deleted;
      - a trait gets an annotation;
      - a new sense is empty, titled "(empty)", with the focus in its gloss, and one undo
        removes it;
      - an illustration and a media item are added and deleted;
      - deleting an empty sense asks nothing, and deleting a non-empty one asks;
      - Cmd+Shift+N in a trait's annotations adds an annotation;
      - the context menu adds and deletes;
      - Delete on a card header deletes;
      - with "Hide empty sections", the sections at 0 are hidden, and the "Add…" menu
        shows them again;
      - a deletion is undone with the undo menu item.
    - New test `ShortcutsTest`:
      - Add ▸ Example, with the focus in sense #2 of an entry, adds the example to sense #2;
      - Add ▸ Example is disabled while a note is displayed, and while an entry with two
        senses is displayed without focus;
      - Add ▸ Note opens the type menu;
      - a shortcut changed in the dialog becomes the accelerator at once, and is saved in
        `InMemoryPreferences`;
      - a conflict offers Replace;
      - Cmd+C is refused;
      - "Reset all" puts the defaults back;
      - Shortcut+S saves, Shortcut+2 opens the Senses table, including while a text field
        of the editor has the focus;
      - every default is unique, and none is a refused combination (a unit test on
        `Commands`).

---

## 4. Decisions (2026-10-31)

- **Q1, illustrations and media: yes.** They get their sections (§3, step 5.7).
- **Q2, sense, sub-sense and example: created empty**, with the focus in the first field
  (§2.5).
- **Q3, deleting:** a confirmation only for a non-empty sense, example, variant,
  etymology or reversal (§2.4).
- **Q4, the [+]: always visible** (a), in light grey (§2.1).
- **Q5, improvements: all included** (§2.6 to §2.9). For the keyboard, both Cmd+Shift+N
  and the context menu.
- **Shortcuts: feasible and included.** The Add menu with a shortcut per kind of component
  (§2.10), and the shortcuts dialog (§2.11).
- **Q6, default shortcuts for adding:** Cmd+Option + the initial for the 8 frequent kinds,
  none for the others (§2.10).
- **Q7, Example with an entry displayed:** to the only sense; disabled when there are
  several, until the focus is in one of them (§2.10).
- **Q8, general commands: included.** The usual defaults, and Shortcut+1…9 for the views
  (§2.12). This covers the "common shortcuts" item of `TODO.md`.

No question is open.

---

## 6. Status (2026-10-31): implemented

Everything in §2 and §3 is implemented. The full suite passes: 169 tests, of which 28 are
new (`ComponentSectionsTest`, `ShortcutsTest`, `CommandsTest`).

**New classes.**
- `controls`: `ComponentKind`, `EditActions` (replaces `ExtensibleAddActions` and
  `BuilderAddActions`, which are deleted), `SectionPane`, `ComponentSection`, `ItemCard`,
  `TypeChoices`, `Reveal`.
- `ui.app`: `Commands`, `ShortcutsDialog`.
- `ui.editor`: `AddCommands`.

**Deviations and details.**
- **"Other…"** is offered for the open lists: note, annotation, relation, etymology,
  reversal. The trait, field and translation are chosen only from the header's
  definitions; when none is left, the [+] is disabled with a tooltip naming the
  configuration view to fill in.
- **No shortcut is set on Copy, Paste and Cut.** The dialog lists them as fixed with
  ⌘C/⌘V/⌘X, but no accelerator is put on their menu items, so that the menu and the
  focused text field do not both handle the keys (the system menu bar on macOS handles
  key equivalents itself).
- **Disabled Add item.** A menu item has no tooltip in JavaFX, so a disabled Add item
  that has several candidates says it in its text: "Example (put the cursor in one
  first)".
- **The "Add…" menu** at the end of the panel lists the hidden sections of the displayed
  component. Those of the nested components are reached through the context menu of
  their card, or the Add menu.
- **Shortcuts dialog layout.** A conflict is shown in a bar under the table, with
  Replace and Cancel.
- **No more dialogs on creation.**
  - An etymology no longer asks for its source: its type is chosen in the [+] menu, then
    the focus goes to its forms.
  - The relation of an entry is no longer disabled without relation types: "Other…" types
    one.
- **Dialog owners.** `Dialogs.owned` gives the app's dialogs the active stage as owner,
  so that they are centred on it.
- **Renamed style class.** `example-add-button` is still used by the panel's navigation
  buttons, so it is renamed `panel-nav-button` rather than removed.

**Not verified on a real Mac.** The following have only been checked headless:
- the system menu bar with the Add and Help menus;
- the ⌥⌘ shortcuts;
- the context-menu gesture (Ctrl+click).
