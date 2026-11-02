# TODO

## dark mode : follow system

## check LIFT

Move the lift verification to lift-api (once done there): remove the stax parser here.

## Documentation

Translate into english and improve the documentation. Complete documentation. Move the makeup to markdown.

## merge entries

## Check that external file exist and offer to import range, trait and field definition into internal header

##

plan for adding common shortcut. save, save as. Including shifting between browser (menu items in the View menu).
- relation editor -> aller à l'object référencé
- parfois pas de nombre d'occ entre parenthèse
- parfois bouton vert (dans Notes), parfois un bouton tout en bas

##

16, 33, 41

> Le Vaillant, François. *Histoire naturelle des oiseaux de paradis et des rolliers, suivie de celle des toucans et des barbus*. Vol. 1. Paris: Denné le jeune, Perlet, 1806. Plate 1: « Le grand Oiseau de Paradis, émeraude, mâle » (the greater bird-of-paradise, *Paradisaea apoda*, male), painted by Jacques Barraband, engraved by Pérée, printed by Langlois. DOI: [10.5962/bhl.title.61652](https://doi.org/10.5962/bhl.title.61652).

The scan is from the Smithsonian Libraries copy, digitised by the [Biodiversity Heritage Library](https://www.biodiversitylibrary.org/page/40634945). The file used is [Histoire naturelle des oiseaux de paradis et des rolliers (No. 1) (8112348391).jpg](https://commons.wikimedia.org/wiki/File:Histoire_naturelle_des_oiseaux_de_paradis_et_des_rolliers_(No._1)_(8112348391).jpg) on Wikimedia Commons. The illustration is in the public domain.

The previous icon, made the same way from plate 1 (« Le grand Oiseau de Paradis, émeraude, mâle », the greater bird-of-paradise, *Paradisaea apoda*, male; [Commons file](https://commons.wikimedia.org/wiki/File:Histoire_naturelle_des_oiseaux_de_paradis_et_des_rolliers_(No._1)_(8112348391).jpg), [BHL page](https://www.biodiversitylibrary.org/page/40634945)), is kept in `src/site/resources/icon/plate-01/`.

# Done

Plan for restyling and reorganizing the create/delete and component number display in the editors (in the fr/cnrs/lacito/liftgui/ui/app/controls package).

I would like harmonization regarding the following aspects:
  - for components displayed in TitledPane: in some cases the number of existing components is diplayed (for instance, pronunciationsPane in the LiftEntryEditor class), in other cases it is not (for instance, traits in the traitsPane object in the ExtensibleWithoutFieldEditor class). Please display the actual number of components in all TitledPane displaying a component. In some cases, when there is no object, a label "None" is diplayed: Annotations TitledPane in the FieldEditor for instance. In that case, it is not necessary to display "None", the count "(0)" will be enough.
- For creating a component: in some case there are buttons at the bottom of the form, such as addSenseBtn button and other in LiftEntryEditor. In other cases, a green button is diplayed inside the TitledPane, for instance for traits; in other case there is no Button at all for creating a new component, for instance for the annotation components of a trait (although there is a "+ Annotation" button for creating annotation in the ExtensibleWithoutFieldEditor annotation TitledPane). Please make a suggestion for an unified way of displaying button; they should stay close to the created component (not at the bottom of the page); they should not take too much space; maybe a [+] button directly in the Title bar of the TitledPand will be great since it will avoid consuming space and also make the button available to the user without a supplementary click for opening the empty TitledPane.
- Not all components can be deleted: for instance Field can not be deleted. Some components have a small delete button on the top: please use this pattern on all components when the component can be deleted.

Please make a plan for a good user experience, using modern GUI design and efficient data input. Ask question or suggest improvements if usefull.

## Column configuration + filter

Could you make a plan to make column configurable for each component tables. The configuration should be done in the settings dialog but also through a button on the table header : please select a modern and efficient gui design for this. In addition to existing columns, that should be th default, columns configuration should offer to display any field existing on the component type, together with count of the various subcomponents and property of the parent and ancestor. Field that are multitext should generate as many column as languages. Please also include in the plan a redesign of the filtering widgets above each column. They tend not to follow the width of the column when they are resized. For different column type, different filtering tools should be provided: text (with ignore case and regexp) for multitext, drop down with selectable option for fixed-value column, min-max range for date and number (including those resulting from a count of subcomponents). Please elaborate a clean and future proof design and a modern and efficient gui  design. Ask question when needed and suggest improvements if relevant.

## Automatically create screenshot of the app

You will create a small program for automatically create screenshots of the App for the documentation.

The program will be in the Doc directory. It will be runned by maven during the appropriate maven phase.

The program will run the app, open the 20260302.lift dictionary, and then take various screenshots. All are png files:

- open the "Entry" table, select the first row, and take a screenshot. The screenshot will be named "entries.png".
- open the "Sense" table, select the first row, and take a snapshot.  The screenshot will be named "Sense.png".
- open the "Meta-language" table, select the first row, and take a snapshot.  The screenshot will be named "MetaLanguages.png".
- open the "grammatical-info" table under "Ranges", select the second row, and take a snapshot.  The screenshot will be named "GramInfo.png".
- open the "Note types" table under "Category browsers", select the first row, and take a snapshot.  The screenshot will be named "BrowseNoteType.png".

Then move all screenshots into `Doc/img/` directory.

Please look for modern and standard way to manage such a task. Provide a clean and future-proof solution. Select the technology accordingly.
