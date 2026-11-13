package fr.cnrs.lacito.liftfx;

import fr.cnrs.lacito.liftfx.internal.Messages;
import java.net.URL;
import java.util.Locale;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/** The settings shared by all the editors of the library. */
public final class LiftFx {

    private static final BooleanProperty HIDE_EMPTY_SECTIONS = new SimpleBooleanProperty(LiftFx.class, "hideEmptySections", false);

    private LiftFx() {}

    /**
     * The language of the editors' texts (labels, tooltips, dialogs). English and French are
     * provided; another locale shows the English texts. The default locale until it is set.
     * Editors built afterwards use it.
     */
    public static void setLocale(Locale locale) {
        Messages.setLocale(locale);
    }

    public static Locale getLocale() {
        return Messages.getLocale();
    }

    /**
     * Whether the sections without any component (no note, no variant…) are hidden in all
     * the editors; false by default. A hidden section can still be added to.
     */
    public static BooleanProperty hideEmptySectionsProperty() {
        return HIDE_EMPTY_SECTIONS;
    }

    /**
     * The stylesheet of the editors, for {@code scene.getStylesheets().add(…)}. A
     * LiftComponentEditor adds it itself; the editors of {@code fr.cnrs.lacito.liftfx.controls}
     * shown on their own need it in their scene.
     */
    public static String stylesheet() {
        URL css = LiftFx.class.getResource("lift-fx.css");
        if (css == null) throw new IllegalStateException("lift-fx.css is missing from the lift-fx module");
        return css.toExternalForm();
    }
}
