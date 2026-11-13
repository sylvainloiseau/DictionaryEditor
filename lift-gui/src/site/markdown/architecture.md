# Architecture

*Structure of the project*

## Maven project

The editor is a single Maven project, which depends on a separate library:

- `lift-api`: the library that reads and writes dictionaries in the LIFT format;
- `DictionaryEditor` (this project): the JavaFX application, i.e. the graphical interface.

## lift-api

The data model and the XML parsers and serializers of the LIFT format. Its main classes are `LiftDictionary`, `LiftEntry`, `LiftSense`, `LiftExample`, etc.

## The JavaFX application

- A navigation tree (Objects, Languages, Categories, Configuration)
- Tables that follow the selected view
- An editing form on the right
- The category browser (traits, annotations, note types, etc.)
- The management of the object languages and meta-languages

## Technologies

- Java 25
- JavaFX 25
- Maven 3.x
