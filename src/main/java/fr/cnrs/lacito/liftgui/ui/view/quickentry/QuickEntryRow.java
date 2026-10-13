package fr.cnrs.lacito.liftgui.ui.view.quickentry;

import java.util.HashMap;
import java.util.Map;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** A row of the quick entry table: forms, glosses and a grammatical info for a new entry. */
public final class QuickEntryRow {

    private final Map<String, StringProperty> forms = new HashMap<>();
    private final Map<String, StringProperty> glosses = new HashMap<>();
    private final StringProperty gramInfo = new SimpleStringProperty("");
    /** Set once the entry of this row was created, so that it is created only once. */
    private final BooleanProperty created = new SimpleBooleanProperty(false);

    public StringProperty formProperty(String lang) {
        return forms.computeIfAbsent(lang, k -> new SimpleStringProperty(""));
    }

    public StringProperty glossProperty(String lang) {
        return glosses.computeIfAbsent(lang, k -> new SimpleStringProperty(""));
    }

    public StringProperty gramInfoProperty() {
        return gramInfo;
    }

    public BooleanProperty createdProperty() {
        return created;
    }
}
