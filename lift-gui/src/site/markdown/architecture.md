# Architecture

*Structure of the project*

## Maven project

The editor is a Maven project with two modules, which depends on a separate library:

- `lift-api`: the library that reads and writes dictionaries in the LIFT format;
- `DictionaryEditor` (this project), the parent of two modules, which share its version:
  - `lift-fx`: the JavaFX editors of the LIFT components (entry, sense, example, note…),
    a library that any JavaFX application can use, published on Maven Central;
  - `lift-gui`: the JavaFX application, i.e. the graphical interface, built on `lift-fx`.

## lift-fx

`LiftComponentEditor` shows a component of a dictionary with its editor. Given the
`LiftDictionary`, it offers the languages of the dictionary (object languages for the forms,
metalanguages for the glosses and definitions), its types and ranges, and adds and deletes
components through lift-api.

- `fr.cnrs.lacito.liftfx`: `LiftComponentEditor`, `LiftFx` (the locale, the stylesheet,
  the hidden empty sections), `EditActions`, `ComponentKind`;
- `fr.cnrs.lacito.liftfx.controls`: the editor of each kind of component
  (`LiftEntryEditor`, `SenseEditor`…);
- `fr.cnrs.lacito.liftfx.internal`: helpers shared with `lift-gui` only (not exported to
  other modules).

The library has its own texts (English and French), its own stylesheet (`lift-fx.css`), and
never touches the user's preferences: the application chooses the locale
(`LiftFx.setLocale`).

## lift-api

The data model and the XML parsers and serializers of the LIFT format. Its main classes are `LiftDictionary`, `LiftEntry`, `LiftSense`, `LiftExample`, etc.

## The JavaFX application (lift-gui)

- A navigation tree (Objects, Languages, Categories, Configuration)
- Tables that follow the selected view
- An editing form on the right
- The category browser (traits, annotations, note types, etc.)
- The management of the object languages and meta-languages

## Technologies

- Java 25
- JavaFX 25
- Maven 3.x
