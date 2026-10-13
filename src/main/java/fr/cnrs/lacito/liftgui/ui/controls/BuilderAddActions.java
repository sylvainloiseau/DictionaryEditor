package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.builder.DictionaryComponentBuilderFactory;
import fr.cnrs.lacito.liftapi.model.*;
import java.util.function.BiConsumer;

/**
 * The add actions of any component, creating the new components through the dictionary's
 * component builder. An action the component does not support (e.g. a note on a variant)
 * does nothing; the editors only offer the actions that apply.
 */
public final class BuilderAddActions implements ExtensibleAddActions {

    private final LiftDictionary dictionary;
    private final Object model;
    private final Runnable refresh;

    private BuilderAddActions(LiftDictionary dictionary, Object model, Runnable refresh) {
        this.dictionary = dictionary;
        this.model = model;
        this.refresh = refresh;
    }

    /**
     * @param model   the component to which components are added
     * @param refresh rebuilds the editor showing the component after an addition
     */
    public static ExtensibleAddActions of(LiftDictionary dictionary, Object model, Runnable refresh) {
        return new BuilderAddActions(dictionary, model, refresh);
    }

    /** Adds an annotation of the given type to a multitext. */
    public static BiConsumer<String, MultiText> multiTextAnnotator(LiftDictionary dictionary) {
        return (name, mt) -> dictionary.getComponentBuilder().annotation(mt, name).build();
    }

    private DictionaryComponentBuilderFactory builder() {
        return dictionary.getComponentBuilder();
    }

    @Override
    public void addTrait(String name, String value) {
        if (model instanceof HasTrait m) builder().trait(m, name, value).build();
    }

    @Override
    public void addAnnotation(String name) {
        if (model instanceof HasAnnotation m) builder().annotation(m, name).build();
    }

    @Override
    public void addField(String type) {
        if (model instanceof HasField m) builder().field(m, type).build();
    }

    @Override
    public void addNote(String type) {
        if (model instanceof HasNote m) builder().note(m, type).build();
    }

    @Override
    public void addPronunciation() {
        if (model instanceof HasPronunciation m) builder().pronunciation(m).build();
    }

    @Override
    public void addRelation(String type) {
        if (model instanceof HasRelations m) builder().relation(type, m).build();
    }

    @Override
    public void refresh() {
        if (refresh != null) refresh.run();
    }
}
