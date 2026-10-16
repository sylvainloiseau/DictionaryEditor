package fr.cnrs.lacito.liftgui.ui.nav;

/**
 * The views of the application, as listed in the navigation tree.
 * <p>
 * Each view has the i18n key of its name, and says whether it uses the editor panel on the
 * right and the search field. The views of header ranges share {@link #RANGE}; the range is
 * given by the {@link ViewRequest}.
 */
public enum ViewKey {
    ENTRIES("nav.entries"),
    SENSES("nav.senses"),
    EXAMPLES("nav.examples"),
    TRANSLATIONS("nav.translations"),
    NOTES("nav.notes"),
    VARIANTS("nav.variants"),
    PRONUNCIATIONS("nav.pronunciations"),
    MEDIA("nav.media"),
    ETYMOLOGIES("nav.etymologies"),
    RELATIONS("nav.relations"),
    REVERSALS("nav.reversals"),
    ILLUSTRATIONS("nav.illustrations"),
    FIELDS("nav.fields"),
    TRAITS("nav.traits"),
    ANNOTATIONS("nav.annotations"),

    OBJECT_LANGUAGES("nav.objectLangs"),
    META_LANGUAGES("nav.metaLangs"),

    GRAM_INFO("nav.gramInfo"),
    TRAIT_VALUES("nav.traitValues"),
    TRANSLATION_TYPES("nav.transTypes"),
    NOTE_TYPES("nav.noteTypes"),
    RELATION_TYPES("nav.relationTypes"),
    FIELD_TYPES("nav.fieldTypes"),

    CFG_DESCRIPTION("nav.cfgDesc", false, true),
    CFG_FIELD_DEFINITIONS("nav.cfgFieldDefs"),
    CFG_LANGUAGES("nav.cfgManageLangs", false, true),
    CFG_NOTE_TYPES("nav.cfgManageNoteTypes", false, true),
    CFG_TRANSLATION_TYPES("nav.cfgManageTransTypes", false, true),
    CFG_ANNOTATION_TYPES("nav.cfgManageAnnotationTypes", false, true),
    CFG_RELATION_TYPES("nav.cfgManageRelationTypes", false, true),
    RANGE("nav.cfgRanges"),

    QUICK_ENTRY("nav.quickEntry", true, false);

    private final String i18nKey;
    private final boolean editorPanel;
    private final boolean search;

    ViewKey(String i18nKey) {
        this(i18nKey, true, true);
    }

    ViewKey(String i18nKey, boolean editorPanel, boolean search) {
        this.i18nKey = i18nKey;
        this.editorPanel = editorPanel;
        this.search = search;
    }

    public String i18nKey() {
        return i18nKey;
    }

    /** Whether the editor panel on the right is shown. */
    public boolean hasEditorPanel() {
        return editorPanel;
    }

    /** Whether the search field is shown. */
    public boolean hasSearch() {
        return search;
    }
}
