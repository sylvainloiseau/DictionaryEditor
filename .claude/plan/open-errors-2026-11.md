# Opening a file that is not LIFT: a real check and clear messages

Date: 2026-11-02. Status: implemented (§7).

## 1. Today

`DictionaryService.loadFromFile` (core) checks, in order:

1. the file exists and is readable;
2. the name ends in `.lift`, else it refuses the file (`service.notLiftExtension`);
3. `looksLikeLift`: reads up to 50 lines as UTF-8, skips the blank lines and the lines
   *starting* with `<?xml` or `<!--`, then wants `<lift` in the next line;
4. `LiftDictionary.loadDictionaryFromFile` (lift-api, SAX).

The problems:

- **Wrong refusals by step 3**, all on well-formed LIFT:
  - `<?xml version="1.0"?><lift version="0.13">` on one line;
  - a comment over several lines before the root;
  - a UTF-16 file, or a processing instruction before the root.
- **Wrong acceptances by step 3**, e.g. a text file mentioning `<lift`.
- **A well-formed XML file with another root**, e.g. `<html>` that contains `<lift`, gets through. lift-api then
  skips every unknown element and loads an **empty dictionary, silently**. Its SAX handler
  never checks the root element.
- **Step 2** refuses any other extension, although the Open dialog offers "All files".
- **The messages** (`FileActions.openFile` → `Dialogs.error` with `I18n.formatErrorMessage`):
  - a generic text: "An error occurred while opening the file or loading recent dictionaries";
  - then the raw exception chain, e.g. `SAXException: Fatal error … lineNumber: 12; columnNumber: 5; …`.

  The user is not told what is wrong in their own words, nor where.

## 2. Goal

Remove the heuristic. Tell, in plain language and with the position when there is one, which of these happened:

| Case | Detected by | Message (header / content) |
|---|---|---|
| File missing / not readable | `Files` (as today) | "“x.lift” cannot be found" / its path |
| Empty file | the check (§3) | "“x.lift” is empty" |
| Not XML at all (binary, plain text, PDF…) | the check: XML error **before** the root element | "“x.lift” is not an XML file" / "A LIFT file is XML; this file starts with something else (line 1, column 1)." |
| XML, but not well-formed | lift-api: `SAXParseException` in the cause chain | "“x.lift” is not well-formed XML" / the parser's message, "line 12, column 5", and **that line of the file with a caret ^ under the column** |
| Well-formed XML, root is not `<lift>` | the check: name and namespace of the root | "“x.lift” is not a LIFT document" / "Its root element is <html>; a LIFT document starts with <lift>." |
| LIFT, but its content is refused by lift-api (`IllegalStateException`, `DuplicateIdException`…) | lift-api, as today | "“x.lift” could not be read as LIFT" / lift-api's message, with the position when it has one |
| DOCTYPE (lift-api forbids it, for security) | lift-api `SAXParseException` | the "not well-formed" message, plus a sentence: "LIFT files do not need a DOCTYPE; remove it." |

## 3. The check: the standard StAX parser, up to the root element

A new `core/LiftFileCheck`, called by `loadFromFile` in place of `looksLikeLift`:

- `XMLInputFactory` (JDK, module `java.xml`, to add to `module-info.java`), configured
  safely: `SUPPORT_DTD=false`, `IS_SUPPORTING_EXTERNAL_ENTITIES=false`.
- Reads the events **only until the first `START_ELEMENT`**, so the cost is constant whatever the file size.
- The parser handles the real XML rules:
  - the encoding from the declaration or the BOM;
  - declaration, comments and PIs on any line;
  - whitespace.
- Outcomes:
  - an `XMLStreamException` before the root, or the first non-blank byte is not `<`: **not XML**, with `getLocation()` line and column;
  - end of document with no element: **empty**, or not XML when there is content;
  - root `localName` ≠ `lift` or namespace ≠ `""` (`LiftVocabulary.LIFT_URI`): **not LIFT**, naming the root found.
- The rest of the file (well-formedness beyond the root, LIFT content) stays with
  lift-api. We parse once more only the prolog, never the whole file twice.

## 4. Reporting

**`LiftOpenException`** gets:

- a `Reason` enum: NOT_FOUND, NOT_READABLE, EMPTY, NOT_XML, MALFORMED, NOT_LIFT, INVALID_LIFT, IO;
- the file;
- line and column (or −1);
- a technical detail.

`DictionaryService` builds it. It searches lift-api's cause chain for a `SAXParseException` to get the position, so we do not parse its message.

**`Dialogs.openError(LiftOpenException)`**, an ERROR Alert:

- the title "Cannot open the file";
- a header per reason (table §2), with the file name;
- the content: the explanation, the position, and the excerpt of the faulty line (cut to ~80 characters around the column, in a monospace label);
- the raw exception chain moved to the Alert's **expandable "Details"** (a read-only `TextArea`), out of the main text.

Used by:

- `FileActions.openFile` (Open, Open recent);
- `loadInitial`, i.e. the last file reopened at startup, whose header says "The last dictionary could not be reopened".

Other exceptions keep the present `Dialogs.error`.

**Messages**: in `messages_en` / `messages_fr`:

- `open.error.*` replace `service.notLiftDocument`, `service.notLiftExtension`, `service.invalidFormat`, `service.cannotLoad`;
- `error.open.detail` stays for the unexpected cases.

## 5. Questions

Answers (2026-11-02): the recommendations — 1(a), 2(b), 3 yes.

1. **The `.lift` extension.** Once the content is checked, the name check only refuses
   good files (a LIFT export named `.xml`).
   - (a) Accept any name. *(recommended)*
   - (b) Ask "This file has no .lift extension; open it anyway?"
   - (c) Keep the refusal.
2. **lift-api**: should it also refuse a root other than `<lift>`, instead of
   returning an empty dictionary? The app is safe either way through §3. The fix belongs in lift-api for its other users, but needs a release (the app uses 0.1.0 from Maven Central).
   - (a) Fix it in lift-api now too, and use the release when it is published.
   - (b) App only for now; note it in lift-api's TODO. *(recommended)*
3. **A failed reopen at startup**: today the app shows an empty window and the error.
   Should the dialog also offer to remove the file from Recent files? *(recommended: yes, a "Remove from recent files" button)*

## 6. Tests

**`DictionaryServiceTest`** (unit, temporary files):

- accepted:
  - the declaration and `<lift>` on one line (the bug reported);
  - a multi-line comment before the root;
  - a BOM;
  - a UTF-16 file;
  - a PI before the root;
- refused, with the expected reason:
  - empty → EMPTY;
  - plain text → NOT_XML (line 1);
  - PNG bytes → NOT_XML;
  - `<html>` root → NOT_LIFT naming `html`;
  - `<lift xmlns="urn:x">` → NOT_LIFT;
  - an unclosed tag on line 12 → MALFORMED at line 12 with the line's text;
  - a DOCTYPE → MALFORMED with the DOCTYPE sentence;
  - a duplicate id → INVALID_LIFT;
  - a missing file → NOT_FOUND;
- with 1(a): a valid file named `.xml` is accepted.

**TestFX**:

- open recent on a malformed file → the error dialog, with the header naming the file and "line 12";
- the Details are expandable;
- the previous dictionary is still shown.

**Removed**: `looksLikeLift`, the `BufferedReader` imports, and the `service.not*` messages.

## 7. Status (2026-11-02): implemented

- `core/LiftFileCheck` (StAX up to the root element) replaces `looksLikeLift`.
- `LiftOpenException` carries its reason, file, line, column and excerpt. `loadFromFile` now throws only it, and accepts any file name.
- `ui/app/OpenErrorDialog`: the header per reason, the explanation, "Line l, column c:", the excerpt in monospace, the path, and the stack trace in the expandable details. `RecentFiles.remove` was added. `requires java.xml` was added to `module-info`.
- Messages `open.error.*` (en, fr) replace `service.*`.
- Tests: `DictionaryServiceTest` (16), `OpenErrorTest` (2), `OpenErrorAtStartupTest` (1).

Deviations and details:

- **"Remove from recent files"** is also offered when Open recent fails, not only at startup. It is not offered for a file chosen in the Open dialog unless that file is in the list.
- **The classification of errors:**
  - an XML error before the root element is NOT_XML only when the first non-blank character is not `<`; otherwise it is MALFORMED (e.g. an error inside the `<lift …>` tag);
  - a DOCTYPE is detected by the check itself, with its position;
  - a file with only whitespace is EMPTY;
  - a `SAXParseException` from lift-api that wraps an exception of lift-api is INVALID_LIFT, with its position. When the error lies in the ranges file, no excerpt is shown.
- **The excerpt** is read as UTF-8, or as UTF-16 when the first bytes say so. A line that does not decode is not shown.
- **lift-api:** the root-element check is noted in its TODO.md ("## files").
