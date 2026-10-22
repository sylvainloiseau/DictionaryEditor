# Counting the uses of the header's declarations with lift-api: plan (2026-10-18, revised 2026-10-22)

## Goal

DictionaryEditor stops counting by itself how many components use a feature (a type, a
part of speech, a range element) or a field/trait definition, and asks lift-api
(`LiftHeader.countUses`, `isUsed`). Two counters can disagree: the number the user sees
before deleting, and the refusal lift-api applies when deleting, must be the same thing.

**Revisions:**
- 2026-10-21, after lift-api's trait specifications
  (`../lift-api/.claude/plan/trait-specification-2026-10.md`): traits named after a range hold
  `Feature` objects, so lift-api counts them. The lift-api step this plan first needed is
  no longer needed.
- 2026-10-22, after external ranges are read
  (`../lift-api/.claude/plan/external-ranges-2026-10.md`): the ranges' content (hierarchy,
  traits) is loaded, external sets are read-only, and the features a file does not declare
  are flagged. With that, the two counters now disagree on real data (§1.3).
- Decided: "Delete" stays enabled and explains lift-api's refusal with the number of uses
  (question 1 of the previous version).

---

## 1. Current state

### 1.1 lift-api (committed: `a305268`, `85b5163`, `e0e060d`)

- **What `countUses(Feature)` / `isUsed(Feature)` count:**
  - the types of notes, relations, annotations, etymologies, reversals, translations;
  - grammatical infos;
  - typed traits (alone, collection, sequence, by identity), including those named after a
    range (`RangeReference`);
  - superordinate links of every set;
  - what the header's own components hold, including the traits of range elements read
    from a `.lift-ranges` file.
- `countUses(LiftFieldAndTraitDefinition)`: fields and traits holding that very definition,
  the header's included.
- `countUses(FeatureSet)`: definitions with that range, and traits named after it.
- `removeFeature`, `removeFeatureSet`, `removeFieldOrTraitDefinition` refuse what is in use
  (`IllegalStateException`).
- **External sets** (`<range href>`) are read from their file and **read-only**. Any change to
  them is refused (`ExternalDefinitionGuard`, `IllegalStateException`) until
  `makeFeatureSetInternal`.
- **A value the external file does not declare** becomes a bare feature, flagged
  `Feature.isUndeclaredInTheExternalFile()`. A range listed in the header but missing from
  its file is empty, and flagged `FeatureSet.isExternalDefinitionMissing()`.
- In an **internal** set, the reader still creates an undeclared value silently, without a
  flag.

### 1.2 DictionaryEditor (committed: `9f7c88b`, `c303b62`)

- **External sets:** `FeatureSetView` and `RangeView` are read-only for them, show where they
  are defined, and offer "make editable" (`makeFeatureSetInternal`). `RangeView` marks the
  elements declared inline.
- **Validator:**
  - reports values undeclared in their external file (`isUndeclaredInTheExternalFile`);
  - reports external ranges missing from their file;
  - still has the old "trait value out of range" check (§3).
- **Its own counter, `core.DictionaryQueries`:**

  | Method | Now |
  |---|---|
  | `countFeatureUsage(reg, f)` | types, grammatical infos, traits whose specification's range is `f`'s set and whose value holds `f`. **Misses** superordinate links and what the header holds |
  | `countFieldOrTraitUsage(reg, fd)` | by identity. **Misses** the header's fields and traits |
  | `traitUsesFeatureSet`, `traitValueTokens` | helpers of the above and of the validator |
  | `renameFeature(set, old, new)` | only `changeFeatureId` |

- **Callers:**
  - `FeatureSetView` (note, translation, annotation, relation types): the "uses" column; the
    delete pre-check; the rename;
  - `RangeView` (other ranges): the delete pre-check; the rename;
  - `FieldDefinitionsView`: the "uses" column; the delete pre-check; the message of a refused
    data-model change;
  - `DictionaryValidator`: "trait value out of range";
  - `DictionaryQueriesTest`.

### 1.3 The two counters, measured with the ranges loaded

| Dictionary | Features | Counts that differ | Why |
|---|---|---|---|
| lift-api's corpus (`DictionaryLift20261022.lift` + `.lift-ranges`) | 2,917 (26 undeclared in their file) | **576** | 558 are superordinates of other features; 18 are used by traits of range elements (`mood-feature-value/inf`…) |
| DictionaryEditor's demo (`20260302.lift` + `.lift-ranges`) | 2,921 (28 undeclared) | **576** | same |

The definition counts agree (no field or trait held by the header uses a definition in
these files). **lift-api is right in every case where they differ:**
- deleting a superordinate would leave its sub-features with a parent no range declares;
- a range element's trait holds the feature.

Today the "uses" column shows 0 for those 576 features while lift-api refuses to delete
them: exactly the disagreement this plan removes.

---

## 2. Work plan in DictionaryEditor

lift-api is ready (committed and installed). It must also be **published** to GitHub
Packages before the release workflow can build DictionaryEditor.

1. **Feature counts and deletion** (`FeatureSetView`, `RangeView`, internal sets only: the
   views are already read-only for external ones):
   - the "uses" column: `header.countUses(f)`;
   - "Delete" stays enabled:
     - it calls `removeFeature`;
     - if lift-api refuses, the message gives the number of uses (`countUses`). It says
       that this includes the values it is the parent of, since a parent link is a use the
       user does not see in the entries;
     - one code path for the refusal; no pre-check that could disagree.
2. **Renaming** (`FeatureSetView`, `RangeView`): call `set.changeFeatureId(f, newId)` directly;
   `DictionaryQueries.renameFeature` goes. One undo step.
3. **Definitions** (`FieldDefinitionsView`):
   - the "uses" column, and the message of a refused data-model change: `header.countUses(fd)`;
   - "Delete": `removeFieldOrTraitDefinition`, explaining its refusal with the number, as in
     step 1.
4. **Validator**: remove "trait value out of range" (§3) and its message key
   (`validate.traitValueOutOfRange`, en and fr); no test covers it.
5. **Clean-up:**
   - remove `countFeatureUsage`, `countFieldOrTraitUsage`, `traitUsesFeatureSet`,
     `traitValueTokens` and `renameFeature` from `DictionaryQueries`;
   - remove their tests from `DictionaryQueriesTest` (whitespace splitting,
     `countFeatureUsage`, the rename, the id with a space). lift-api's tests cover what
     they checked.
6. **Tests** (TestFX, `HeaderConfigurationTest`). The demo's ranges are external, so each test
   makes its range internal first, as the external-ranges tests do:
   - the "uses" column counts:
     - a superordinate link;
     - a trait on a range element;
     - a collection value;
     - a trait named after the range;
   - deleting a feature used only as a parent is refused, with the number, and changes
     nothing;
   - renaming a feature used by a trait named after its range: the trait shows the new id,
     and one undo restores both;
   - deleting a used definition is refused, with the number.

---

## 3. The validator's "trait value out of range"

It compares each trait value with its range. It can no longer report anything:
- the reader turns every undeclared value into a feature of the range, so a typed trait's
  value is always in its range;
- text traits have no range.

What it was meant to find is now reported, for external ranges, by the check on
`isUndeclaredInTheExternalFile()` (26 values in the corpus). So the old check is removed
(step 4).

**One gap remains: internal ranges.** A value that an inline range (in the `.lift` header)
does not declare is created without a flag, so nothing reports it. See question 1.

---

## 4. Performance

Measured on the corpus (2,604 entries, 5,127 traits, ranges loaded):
- **one `countUses(Feature)`**: about 0.7–1.5 ms. Each call walks the dictionary and the
  header, which now holds 2,917 features and their texts;
- **over all 1,795 features of `semantic-domain-ddp4`**: 1.3–2.6 s (the app's counter: about
  0.3 s, because it skips the header).

Where it is called:
- **`FeatureSetView`'s column** (one call per row): only on note, translation, annotation and
  relation types, small sets. A few ms.
- **`RangeView`** (the large ranges, semantic domains included): only when deleting, so one
  call.
- **`FieldDefinitionsView`'s column**: `countUses(fd)`, a dozen definitions.

So no batch method is needed now. If a "uses" column is later added to `RangeView`, add to
lift-api `Map<Feature, Long> countUses(FeatureSet)` (one pass over the dictionary and the
header for all the features of a set) and use it there.

---

## 5. Questions

1. **Undeclared values of internal ranges** (§3): ask lift-api to flag them too (generalising
   `isUndeclaredInTheExternalFile()` to "undeclared in its range", set by the reader for any
   set), so that the validator reports them like the external ones? Recommended: yes, as a
   small separate lift-api step; this plan does not depend on it.

---

## 6. Implementation status (2026-10-22)

Steps 1–6 are done and uncommitted. The question in §5 is left out.

- `HeaderViews.deleteFeature`: one undo step around `removeFeature`. A refusal shows
  `cfg.featureInUse` with `countUses`, or lift-api's message when the count is 0, for
  example an external set.
- `FeatureSetView`, `RangeView`: count with `countUses`, rename with `changeFeatureId`,
  delete through `deleteFeature`.
- `FieldDefinitionsView`: counts with `countUses(fd)`. Delete calls
  `removeFieldOrTraitDefinition` and shows a refusal as `cfg.deleteNotAllowed` with the
  number.
- `DictionaryValidator`: "trait value out of range" is removed, along with its key in en
  and fr.
- `DictionaryQueries`: the five methods are removed, along with their four tests.
- `HeaderConfigurationTest`: four new tests on an inline `note-type` range: the "uses"
  column (parent, range-element trait, collection, trait named after the range), the
  refused delete of a parent, rename plus one undo, and the refused delete of a
  definition.
- `mvn -o clean test`: 124 tests, all pass.
