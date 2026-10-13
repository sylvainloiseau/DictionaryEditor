package fr.cnrs.lacito.liftgui.ui.view;

import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.MultiText;

/** A row of the language views: one form of a multitext. */
public record MultiTextRow(MultiText multiText, Form form) {

    public String lang() {
        return form.getLang();
    }

    public String text() {
        return form.toPlainText();
    }
}
