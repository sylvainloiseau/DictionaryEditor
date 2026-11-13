package fr.cnrs.lacito.liftgui.ui.nav;

import java.util.EnumMap;
import java.util.Map;
import javafx.scene.layout.Region;

/**
 * The icons of the navigation tree: SVG paths on a 16 × 16 grid, drawn by a {@link Region}
 * whose colour comes from the stylesheet. Outlines are cut out by a sub-path wound the
 * other way. The views of one family share an icon.
 */
final class NavIcons {

    private static final String BOOK = "M3 1.5H13V14.5H3Z M5 3V13H11.5V3Z M6.5 5H10V6.2H6.5Z M6.5 7.5H10V8.7H6.5Z";
    private static final String LIST = "M2 3h2v2H2z M6 3.25h8v1.5H6z M2 7h2v2H2z M6 7.25h8v1.5H6z M2 11h2v2H2z M6 11.25h8v1.5H6z";
    private static final String QUOTE = "M2 4h5v4.5c0 2-1.2 3.3-3.3 3.6l-.4-1.3c1-.3 1.6-.9 1.7-1.8H2z"
        + " M9 4h5v4.5c0 2-1.2 3.3-3.3 3.6l-.4-1.3c1-.3 1.6-.9 1.7-1.8H9z";
    private static final String BUBBLES = "M1.5 2H9V8H5L3 10V8H1.5Z M10 6H14.5V12H13V14L11 12H7V9.5H10Z";
    private static final String NOTE = "M3 1.5H10L13 4.5V14.5H3Z M4.5 3V13H11.5V5.5H9V3Z";
    private static final String BRANCH = "M3 2h2.5v2.5H3z M3 11.5h2.5V14H3z M10.5 4h2.5v2.5h-2.5z M3.6 4.5h1.3v7H3.6z"
        + " M4.3 9.6L11 6.2l.6 1.1L4.9 10.7z";
    private static final String SOUND = "M2 6.5h1.5v3H2z M5 4h1.5v8H5z M8 2h1.5v12H8z M11 5h1.5v6H11z M13.8 7H15v2h-1.2z";
    private static final String PLAY = "M1.5 3H14.5V13H1.5Z M3 4.5V11.5H13V4.5Z M6.5 6L10.5 8L6.5 10Z";
    private static final String CLOCK = "M8 1.5a6.5 6.5 0 1 1 0 13a6.5 6.5 0 1 1 0-13z M8 3a5 5 0 1 0 0 10a5 5 0 1 0 0-10z"
        + " M7.25 4.5h1.5v3.2l2.2 1.3-.75 1.3-2.95-1.75z";
    private static final String LINK = "M1 8l3-3v2h8V5l3 3-3 3V9H4v2z";
    private static final String SWAP = "M1.5 5L4.5 2V4H14V6H4.5V8Z M14.5 11L11.5 8V10H2V12H11.5V14Z";
    private static final String IMAGE = "M1.5 2.5H14.5V13.5H1.5Z M3 4V12H13V4Z M4 11L6.5 7.5L8.5 10L10 8.5L12 11Z"
        + " M10.5 5a1.2 1.2 0 1 1 0 2.4a1.2 1.2 0 1 1 0-2.4z";
    private static final String FIELD = "M1.5 4.5H14.5V11.5H1.5Z M3 6V10H13V6Z M4 6.75h1.2v2.5H4z";
    private static final String TAG = "M1.5 2.5H8L14 8.5L8.5 14L1.5 7Z M4.5 4a1 1 0 1 0 0 2a1 1 0 1 0 0-2z";
    private static final String COMMENT = "M2 2.5H14V11H7L4 14V11H2Z M3.5 4V9.5H5.5V10.8L6.8 9.5H12.5V4Z";
    private static final String GLOBE = "M8 1.5a6.5 6.5 0 1 1 0 13a6.5 6.5 0 1 1 0-13z M8 3a5 5 0 1 0 0 10a5 5 0 1 0 0-10z"
        + " M7.3 2h1.4v12H7.3z M2 7.3h12v1.4H2z";
    private static final String CHART = "M2 13h12v1.5H2z M3 8h2.5v4.5H3z M6.75 4h2.5v8.5h-2.5z M10.5 6.5H13v6h-2.5z";
    private static final String INFO = "M8 1.5a6.5 6.5 0 1 1 0 13a6.5 6.5 0 1 1 0-13z M8 3a5 5 0 1 0 0 10a5 5 0 1 0 0-10z"
        + " M7.25 7h1.5v4.5h-1.5z M7.25 4.5h1.5V6h-1.5z";
    private static final String SLIDERS = "M2 4h12v1.2H2z M2 10.8h12V12H2z M9 2.8h2v3.6H9z M4.5 9.6h2v3.6h-2z";
    private static final String LAYERS = "M8 1.5L14.5 5L8 8.5L1.5 5Z M2.6 7.6L8 10.5l5.4-2.9 1.1.6L8 11.7 1.5 8.2z"
        + " M2.6 10.6L8 13.5l5.4-2.9 1.1.6L8 14.7 1.5 11.2z";
    private static final String BOLT = "M9 1L3 9h4l-1 6 6-8H8z";
    /** The chevron of a group, pointing down; turned by the stylesheet when the group is folded. */
    static final String CHEVRON = "M3.5 6l1-1L8 8.5 11.5 5l1 1L8 10.5z";

    private static final Map<ViewKey, String> ICONS = new EnumMap<>(ViewKey.class);

    static {
        ICONS.put(ViewKey.ENTRIES, BOOK);
        ICONS.put(ViewKey.SENSES, LIST);
        ICONS.put(ViewKey.EXAMPLES, QUOTE);
        ICONS.put(ViewKey.TRANSLATIONS, BUBBLES);
        ICONS.put(ViewKey.NOTES, NOTE);
        ICONS.put(ViewKey.VARIANTS, BRANCH);
        ICONS.put(ViewKey.PRONUNCIATIONS, SOUND);
        ICONS.put(ViewKey.MEDIA, PLAY);
        ICONS.put(ViewKey.ETYMOLOGIES, CLOCK);
        ICONS.put(ViewKey.RELATIONS, LINK);
        ICONS.put(ViewKey.REVERSALS, SWAP);
        ICONS.put(ViewKey.ILLUSTRATIONS, IMAGE);
        ICONS.put(ViewKey.FIELDS, FIELD);
        ICONS.put(ViewKey.TRAITS, TAG);
        ICONS.put(ViewKey.ANNOTATIONS, COMMENT);
        ICONS.put(ViewKey.OBJECT_LANGUAGES, GLOBE);
        ICONS.put(ViewKey.META_LANGUAGES, GLOBE);
        for (ViewKey k : new ViewKey[] {ViewKey.GRAM_INFO, ViewKey.TRAIT_VALUES, ViewKey.TRANSLATION_TYPES,
                ViewKey.NOTE_TYPES, ViewKey.RELATION_TYPES, ViewKey.FIELD_TYPES}) {
            ICONS.put(k, CHART);
        }
        ICONS.put(ViewKey.CFG_DESCRIPTION, INFO);
        ICONS.put(ViewKey.CFG_LANGUAGES, GLOBE);
        for (ViewKey k : new ViewKey[] {ViewKey.CFG_FIELD_DEFINITIONS, ViewKey.CFG_NOTE_TYPES,
                ViewKey.CFG_TRANSLATION_TYPES, ViewKey.CFG_ANNOTATION_TYPES, ViewKey.CFG_RELATION_TYPES}) {
            ICONS.put(k, SLIDERS);
        }
        ICONS.put(ViewKey.RANGE, LAYERS);
        ICONS.put(ViewKey.QUICK_ENTRY, BOLT);
    }

    private NavIcons() {}

    /** The path of a view's icon, or null. */
    static String of(ViewKey key) {
        return ICONS.get(key);
    }

    /** A 16 × 16 icon of style class {@code nav-icon}, drawn with {@code path}. */
    static Region region(String path, String styleClass) {
        Region r = new Region();
        r.getStyleClass().add(styleClass);
        r.setStyle(path == null ? null : "-fx-shape: \"" + path + "\";");
        return r;
    }
}
