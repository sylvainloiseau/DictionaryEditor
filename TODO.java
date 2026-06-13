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
  getKnownAnnotationNames
  getKnownNoteTypes
  getKnownRelationTypes
  getKnownGramInfoValues
  getHeaderRangeValues private List<String> getHeaderRangeValues(String rangeId)
  getFactory

  LiftFieldAndTraitDefinitionTarget -> LiftFieldAndTraitDefinitionHostElement

  
- Prevoir dans dictionary :
  rajouter note_type, translation_type relation_type dans le header

  changement :
  - ID example
    - note_type, translation_type relation_type grammatical-info dans le header