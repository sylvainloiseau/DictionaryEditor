# Project Overview

**Project Name**: Dictionary Editor
**Primary Technology**: Java with JavaFX
**Domain**: Linguistic Dictionary Management System
**File Format**: LIFT (Lexicon Interchange FormaT) - XML-based format for lexical data

# Core Purpose and Functionality

This application provides a two components that are two maven module: 
- an API for managing a dictionary data model and marshall/unmarshall it using the LIFT XML-based format ;
- a **graphical user interface for creating, editing, and managing linguistic dictionaries** 

 It enables linguists and lexicographers to:

1. **Create and edit dictionary entries** with hierarchical structure (entries → senses → examples)
2. **Manage complex linguistic data** including forms, glosses, definitions, translations, pronunciations, etc.
3. **Organize and navigate** dictionary content through a structured interface
4. **Maintain data integrity** with proper relationship management between dictionary components
5. **Support multiple languages** for both object languages (the languages being documented) and meta-languages (languages used for definitions, translations, etc.)

I am creating an API that is modeling a dictionary. I have a hierarchy of objects (Entry which contain Sense, Sense wich contain Example, Entry also contains Variant, Relation, Etymology, and each of those objects have Trait, Annotation, Field, Note). Each of these objects contains references to their components (Entry to Sense), and to its parent (Sense to Entry). Moreover, all linguistic content (actual text of a Note, form of an Entry, definition and gloss of a Sense, etc.) are in objects called MultiText, that allow the content to be represented in several languages or writting systems.

I also need a list view of all of those objects in the Dictionary object: a List of Entry, a List of Sense, a list of Variant, etc. and a list of MultiText. I have a fluid API for creating object and can easily register them in the Dictionary list during the build() method of their builder. For Entry, Sense and Example, I have ID and I can easily create a Map that associate the objects with their IDs. Then, the lists for those objects are made off the values of these Map, and for creating and deleting an object I can get its ID and delete the corresponding entry of the Map. However, for other objects, how to efficiently remove them from the list they are registered in, when their parent object are deleted? I would be costly to implement equals() and to iterate on the list in order to find all the MultiText associated with an Entry (and all its subnode) when an entry is deleted, for instance. What is a good general design pattern for object creation and object deletion in this context?
