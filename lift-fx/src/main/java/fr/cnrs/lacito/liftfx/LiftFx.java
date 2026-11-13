package fr.cnrs.lacito.liftfx;

import fr.cnrs.lacito.liftfx.internal.Messages;
import java.util.Locale;

/** The settings shared by all the editors of the library. */
public final class LiftFx {

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
}
