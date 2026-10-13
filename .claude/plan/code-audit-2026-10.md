# Code audit and remediation plan — DictionaryEditor (lift-gui)

Audit date: 2026-10-13. Scope: all of `MainController` (≈7,355 lines), every editor in
`ui/controls`, the `undo`, `core` and `data` packages, `MainApp`, the FXML file and both message
bundles. Doubtful lift-api behaviour was checked against the lift-api jar in use
(`io.github.sylvainloiseau:lift-api:0.1-SNAPSHOT`) and the lift-api source in git history.
The app was not run: every finding comes from reading the code.

Line numbers refer to `src/main/java/fr/cnrs/lacito/liftgui/ui/MainController.java` unless a file
is named.

---

## Findings

### A. Edits that are lost or never saved (critical)

| # | Where | Problem |
|---|---|---|
| A1 | `TraitEditor.buildDefaultCombo` (`TraitEditor.java:247`) | The value combo, used for any trait without a typed definition (the most common case), has no listener, so trait values typed there are never saved. |
| A2 | `AnnotationEditor.setAnnotation` (`:102`, `:107`) | Only `value` is bound. Changes to "who", "when" and the name are ignored. |
| A3 | `NoteEditor` (`:78`), `EtymologyEditor` (`:56`), `ReversalEditor` (`:48`) | Each type `ComboBox` lets the user choose a value, but the choice is never written back. The etymology source field is read-only and cannot be changed. |
| A4 | `FieldEditor`, `TraitEditor`, `AnnotationEditor` | The name/type combos are selectable but not wired to the model, so they look editable and silently do nothing. |
| A5 | `MainApp.reloadScene` via Preferences (`:6883`) | Changing the UI language builds a new controller, whose `initialize()` reloads the last file from disk. **All unsaved changes are discarded without warning.** |
| A6 | Whole application | There is no "modified" tracking. New, Open, Open Recent, Quit (`:4106`) and closing the window all discard changes without asking. |
| A7 | Config "Manage note / translation / annotation / relation types" (`:7119`, `:7281`) | The list is built from the values used in the data, not from the header. Saving then removes every header element that isn't in that list, which **deletes declared but unused types** from the header. Existing descriptions show as empty and edits to them are never saved. |
| A8 | `renameRangeElementInData` (`:6152`) | This renames the Feature, but trait values are stored as plain strings, so traits that use the old id are left pointing at nothing. |
| A9 | Every editor | `dateModified` is never updated when something is edited, so saved files keep stale modification dates. |

### B. Objects created but never built or registered

| # | Where | Problem |
|---|---|---|
| B1 | Sense view, "+ Example" (`:2675`) | `example(sense)` is called without `.build()`, so no example is created. |
| B2 | Sense view, "+ Sub-sense" (`:2757`) | `sense(sense)` is called without `.build()`, so no sub-sense is created. |
| B3 | `createSenseAddActions` / `createExampleAddActions` (`:3650`, `:3709`) | `trait(...)` is called without `.build()`, so the trait is never added. |
| B4 | `VariantEditor.updateVariantType` (`VariantEditor.java:254`) | It calls `getTraits().remove(...)` directly, which bypasses lift-api's deletion path, so the trait stays registered in the dictionary. |
| B5 | Manage languages, `addLanguageToDictionary` (`:4541`) | It "adds" a language by inserting an empty `Form` into a random multitext, which pollutes the data. Then `langs.add(code)` (`:4471`) mutates the manager's internal set directly. `LanguagesManager.addLanguage()` exists and should be used instead. |
| B6 | New field/trait definition dialog (`:5892`) | It always calls `createFieldDefinition`, even when "Trait" is selected, so trait definitions cannot be created. |

### C. Wrong data, wrong lists, wrong lookups

| # | Where | Problem |
|---|---|---|
| C1 | `MultiTextEditor` constructor (`MultiTextEditor.java:76`) | Every multitext gets the **meta** languages, including entry forms, example text, and variant, etymology and pronunciation forms. The `setAvailableLanguages` calls that would set object languages are all commented out. |
| C2 | `TraitEditor` (`TraitEditor.java:128`) | Trait names come from `map(Object::toString)` applied to the definitions, so every trait is flagged "not documented". Its annotations also use object languages, the opposite of everywhere else. |
| C3 | `RelationEditor` (`RelationEditor.java:126`) | The relation type list comes from the **note-type** manager. |
| C4 | `ReversalEditor` (`ReversalEditor.java:48`) | The type list comes from the **etymology-type** manager. |
| C5 | `getTranslations()` is `Map<Feature, MultiText>`, but looked up with a `String` | The example table's translation columns are always empty (`:1239`). "Access objects" for a translation type always finds nothing (`:3505`). Usage counts are always 0 (`:4309`, `:6108`), so a translation type that is in use can be deleted. |
| C6 | `countRangeElementUsage` | Only knows note-type, translation-type (broken, see C5), gram-info and traits whose name equals the range id. Elements of relation, variant, etymology or annotation types report 0 uses and can be deleted while still in use. |
| C7 | Quick entry (`:2414`) | Glosses in several meta languages produce **one sense per language** instead of one sense with several glosses. Gram-info values come from the data rather than the header range. |
| C8 | Entry table columns (`:966`) | The language columns come from forms already present, not from the declared object languages, so a newly added language never gets a column. |
| C9 | Annotation view (`:2064`) | The "Parent type" column is duplicated. The frequency column compares property *objects* with `equals`, so it always shows 1. |
| C10 | Field definition editor (`:6035`) | The option-range combo never shows the current value. Clearing it calls `Optional.of(null)` (NullPointerException). `setTargets` runs on every keystroke. |
| C11 | Titles in the relation/note editors and `MultiTextEditor` annotation panes | They concatenate a `Feature` object, so they show its `toString()` instead of its id. |

### D. State and synchronisation bugs

| # | Where | Problem |
|---|---|---|
| D1 | Entry delete and undo | `DeleteEntryCommand` removes the entry from the dictionary but never from `baseEntries`. **The deleted entry stays in the table**, and undo doesn't put it back. |
| D2 | `show*View` (`:1139`, `:1256`, `:1400`, `:1498`, `:1836`, `:2014`, `:2084`, `:2134`) | The table instances are fields, but a new selection listener is added every time the view is shown. After visiting a view N times, each click rebuilds the editor N times. |
| D3 | `wrapTableWithFilters` | A new listener is added to the global search field for every table ever shown. These listeners are never removed, keep old tables alive, and post stale counts to the count label. |
| D4 | Undo of a sense or example delete | The refresh calls `showSenseView()` / `showExampleView()` whatever view is currently displayed. |
| D5 | Undo/redo | Only three kinds of deletion can be undone. The shortcut uses Ctrl rather than Cmd (wrong on macOS). |
| D6 | Startup (`:400`) | `initialize()` bypasses `DictionaryService`. If the last file fails to load, it silently loads the demo dictionary from a temp directory, so Save writes into that temp copy. |
| D7 | `applyCurrentFilter` (`:3861`) | `entrySource.contains(entry)` on a list for every entry (O(n²)); search text and facets recomputed on every keystroke. |

### E. Dead-end features

- Most objects can't be deleted: only entries, senses and examples can be removed. Variants, pronunciations, relations, etymologies, notes, fields, traits, annotations and reversals can be added but never removed.
- Translations can't be created: no "add translation" anywhere, although lift-api's `translation(...)` builder exists.
- Grammatical info can't be cleared: the combo ignores `null`.
- File → New calls `setDictionary(null)` (`:4060`); every action then reports "no dictionary".
- Tools → Validate only shows counts.
- Pasting anywhere outside a `TextField` creates an entry, in a language picked arbitrarily from a `Set`. Copy always copies the selected *entry*, even from other views.
- Theme and font size are saved but not reapplied at startup; `dark.css` doesn't exist.
- In the etymology, category and field-type views the search field is visible but does nothing.
- `RelationEditor`'s ref-ID field is hidden; its listener would throw on every keystroke if shown.

### F. Behavioural inconsistencies

- Trait and field pickers (`NotableEditor`, `ExtensibleWithFieldEditor`) list every definition, not only those that apply to the object being edited.
- Gram-info values come from the header range in the editors, but from the data in quick entry and the gram-info view.
- Many strings are hard-coded French in the controls and controller, while the rest uses `I18n`. The key `col.language` is missing from both bundles.
- The variant-type combo stays disabled inside `LiftEntryEditor` (it never calls `setVariantTypes`, unlike the Variants view).
- The same "back to parent" button is rebuilt by hand in five places with different label rules.

### G. Dead code

- Unused classes: `core/Keys`, `core/Settings`, `data/LiftRepository`, `ui/model/SenseRow`, `ui/utils/MultiTextTableColumn`.
- Unused methods in `MainController`: `addSectionTitle`, `addListSection` (both overloads), `colCat`, `countFieldUsage`, `showHeaderAllRangesView`, `showConfigDialog` (both overloads), `pinLeftNavigationOnEntries`, `containsSense`, `containsExample`, `getKnownGramInfoValues`, `ensureHeaderComplete` (whole body commented out).
- Handlers wired to nothing: the seven `onConfig*` methods (no Configuration menu in the FXML), and `setModifyButtonVisible` (`modifyButton` / `modifyButtonRow` are not in the FXML).
- Unused fields and variables: `relationTypes` in `SenseEditor` and `VariantEditor`, `variantFormLangs` in `VariantEditor`, `objLangs` / `metaLangs` locals in several methods.
- About 800 lines of commented-out code (old `factory` / `AttributesImpl` / `findParent*`).
- `MainApp`: an artificial 1.2 s "loading" animation before the real loading starts.

---

## Plan

### Phase 1 — stop losing and corrupting data

- [ ] 1. Wire every editor widget to its model, or make it visibly read-only: trait values, annotation name/who/when, note/etymology/reversal types, etymology source. Replace non-editable combos that do nothing with labels. (A1–A4)
- [ ] 2. Add `.build()` to the five builder calls that lack it. (B1–B3)
- [ ] 3. Fix entry deletion and undo so `baseEntries` stays in sync — better, make the table read the dictionary's observable entry list directly. (D1)
- [ ] 4. Add modified tracking (model listeners, or a flag set in every editor action). Prompt before New, Open, Recent, Quit, window close and language change. Keep the dictionary across `reloadScene` instead of reloading from disk. (A5, A6)
- [ ] 5. Rewrite the "Manage types" views to read from and write to the header ranges, keep descriptions, never remove an element still in use. (A7)
- [ ] 6. Make range-element rename also update trait values. (A8)

### Phase 2 — correct data sources

- [ ] 7. Add a language-scope option (object or meta) to `MultiTextEditor` and set it at every call site. (C1)
- [ ] 8. Fix type lists: relation types for `RelationEditor`, reversal range for `ReversalEditor`, definition name for `TraitEditor`; show ids in titles. (C2–C4, C11)
- [ ] 9. Replace every `String` lookup into `getTranslations()` with a lookup by `Feature` id. (C5)
- [ ] 10. Make `countRangeElementUsage` resolve usage from each definition's resolved range and cover every range type. (C6)
- [ ] 11. Quick entry: one sense with several glosses; gram-info values from the header. Entry-table columns from the object languages. (C7, C8)
- [ ] 12. Fix the field/trait definition dialog and editor, the annotation view, and the language manager (use `addLanguage`, stop inserting empty forms). (B5, B6, C9, C10)

### Phase 3 — state, listeners and performance

- [ ] 13. Create selection listeners once at setup; make `wrapTableWithFilters` remove its search listener when its view is replaced. (D2, D3)
- [ ] 14. Make the undo refresh view-aware; use `KeyCombination.SHORTCUT_DOWN`. (D4, D5)
- [ ] 15. Route startup loading through `DictionaryService` and report failures. Use the demo only when no file was ever opened, and label it clearly. (D6)
- [ ] 16. Filter entries with a `Set` and cache the search text. (D7)
- [ ] 17. Update `dateModified` on edit, in a shared editor hook or in lift-api. (A9)

### Phase 4 — missing features

- [ ] 18. Delete actions (with undo) for variants, pronunciations, relations, etymologies, notes, fields, traits, annotations and reversals.
- [ ] 19. "+ Translation" in `ExampleEditor`, and a way to clear grammatical info.
- [ ] 20. File → New creates an empty `LiftDictionary` with a default header, followed by Save As.
- [ ] 21. Tools → Validate runs real checks (dangling references, undeclared types, empty forms), or is removed.
- [ ] 22. Restrict Paste and Copy to the views where they make sense. Apply saved theme and font size at startup, or remove the dark theme.

### Phase 5 — consistency and cleanup

- [ ] 23. Filter trait/field pickers by target. Read gram-info values from the header everywhere. Factor the "back to parent" button into one helper.
- [ ] 24. Move every hard-coded string into the bundles; add `col.language`.
- [ ] 25. Pass the variant types inside `LiftEntryEditor`.
- [ ] 26. Delete the dead code in section G, the unused classes and the commented-out blocks. Remove the fake loading delay.

Suggested order: Phase 1 first, as its own commit.
