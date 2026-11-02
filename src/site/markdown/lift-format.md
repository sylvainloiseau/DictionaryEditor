# LIFT format

*XML structure of lexicographic dictionaries*

## Overview

LIFT (Lexicon Interchange Format) is a standard XML format for lexicographic dictionaries: multilingual texts, entries, senses, examples, traits, annotations, etc.

## Main structure

- `header`: ranges, field definitions, metadata
- `entry`: lexical entries, with their forms, senses, examples…

## Key elements

- `lexical-unit` / `form`: the lexical form (object language)
- `sense`: a sense, with its definition and gloss (meta-languages)
- `example`: an example, with its translations
- `trait`: a trait (name/value pair)
- `grammatical-info`: the grammatical category
- `note`, `relation`, `field`

## Resources

- [The LIFT standard (GitHub)](https://github.com/sillsdev/lift-standard)
