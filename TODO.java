
Finir LiftXMLFactoryNew
Finaliser entry depuis le handler et "addEntryToDictionary"
garder RefID pour le contrôle des références

registry et component sont concurrent dans l'API LiftDictionary
on ne peut récupérer le DIctionaryComponent avec un getter.
quand un dictionnaire est créé avec un Component, devrait peupler le registry et être abandonné.

Quand un élément est retiré du dictionnaire, protester si il est référencé par un autre élément (refId) et ne le retirer que s'il n'est pas référencé.


Dans registry, les SimpleListProperty devraient être ReadOnly puisque dérivées des map
Finir d'implémente remove
Chercher un Sense par gloss, une entrée par form... avoir des map pour récupérer les senses par gloss ou form ?

Refactoring de l'API pour soulager le MainController:

MainController

   ensureHeaderComplete()
     ajoute "cfg.autoAdded"
     doit être fait à la fin du chargement avec une fonction "searchForFieldTraitUsage..."
     ou "discoverNewFieldTrait" dans le dictionary
     ou
     discoverUndocumentedFeature()

    /** Adds a language by inserting an empty form in the first available multitext of the appropriate type. */
    private boolean addLanguageToDictionary

    ->  faire dans le header.

    getKnownTraitValues
      voir rempalcment dansDictionaryValue

  getObjectLanguages
  getMetaLanguages
  getKnownTraitNamesFor(LiftFieldAndTraitDefinitionTarget)
  getKnownFieldTypesFor(LiftFieldAndTraitDefinitionTarget target) {
  getKnownFieldTypes : plusieurs occurrences dans MainController
  getKnownAnnotationNames
  getKnownNoteTypes
  getKnownRelationTypes
  getKnownGramInfoValues
  getHeaderRangeValues private List<String> getHeaderRangeValues(String rangeId)
  getFactory

  LiftFieldAndTraitDefinitionTarget -> LiftFieldAndTraitDefinitionHostElement

  in showAddEtymologyDialog see knownTypes : stream all entry/etymology in order to extract etymology types

- Prevoir dans dictionary :
  rajouter note_type, translation_type relation_type dans le header

  changement :
  - ID example
    - note_type, translation_type relation_type grammatical-info dans le header
