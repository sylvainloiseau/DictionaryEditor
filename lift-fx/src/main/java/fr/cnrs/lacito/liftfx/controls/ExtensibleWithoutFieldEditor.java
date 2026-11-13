/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftfx.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithoutField;
import fr.cnrs.lacito.liftapi.model.HasAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinitionTarget;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftfx.ComponentKind;
import fr.cnrs.lacito.liftfx.EditActions;
import fr.cnrs.lacito.liftfx.internal.ComponentSection;
import fr.cnrs.lacito.liftfx.internal.EditorSupport;
import fr.cnrs.lacito.liftfx.internal.Messages;
import fr.cnrs.lacito.liftfx.internal.SectionPane;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Programmatic editor for any {@link AbstractExtensibleWithoutField}.
 *
 * Displays: dateCreation, dateModification, the traits (TraitEditor) and the annotations
 * (AnnotationEditor), each in a {@link ComponentSection}. With {@link EditActions}, they
 * can be added and deleted. This component is reused inside FieldEditor, NoteEditor, etc.
 * Its {@link #totalProperty() total} is the number of components in all its sections.
 */
public class ExtensibleWithoutFieldEditor extends VBox {

    private final TextField dateCreatedField = EditorSupport.readOnlyField("");
    private final TextField dateModifiedField = EditorSupport.readOnlyField("");
    private final ComponentSection traitsSection;
    private final ComponentSection annotationsSection;
    private final List<SectionPane> counted = new ArrayList<>();
    private final ReadOnlyIntegerWrapper total = new ReadOnlyIntegerWrapper(this, "total", 0);
    private final LiftDictionary dictionary;

    public ExtensibleWithoutFieldEditor(LiftDictionary dictionary) {
        super(8);
        this.dictionary = dictionary;
        setPadding(new Insets(4));

        dateCreatedField.setPromptText(Messages.get("editor.undefined"));
        dateModifiedField.setPromptText(Messages.get("editor.undefined"));

        GridPane datesGrid = new GridPane();
        datesGrid.setHgap(8);
        datesGrid.setVgap(6);
        datesGrid.add(new Label(Messages.get("field.dateCreated")), 0, 0);
        datesGrid.add(dateCreatedField, 1, 0);
        datesGrid.add(new Label(Messages.get("field.dateModified")), 0, 1);
        datesGrid.add(dateModifiedField, 1, 1);
        GridPane.setHgrow(dateCreatedField, Priority.ALWAYS);
        GridPane.setHgrow(dateModifiedField, Priority.ALWAYS);

        traitsSection = new ComponentSection(ComponentKind.TRAIT, Messages.get("editor.traits"), false);
        annotationsSection = new ComponentSection(ComponentKind.ANNOTATION, Messages.get("editor.annotations"), false);
        countIn(traitsSection);
        countIn(annotationsSection);

        getChildren().addAll(datesGrid, traitsSection, annotationsSection);
    }

    /** Adds the components of a section to the {@link #totalProperty() total}. */
    protected final void countIn(SectionPane section) {
        counted.add(section);
        Observable[] deps = counted.stream().map(SectionPane::countProperty).toArray(Observable[]::new);
        total.bind(Bindings.createIntegerBinding(() -> counted.stream().mapToInt(SectionPane::getCount).sum(), deps));
    }

    /** The number of components in all the sections of this editor. */
    public ReadOnlyIntegerProperty totalProperty() {
        return total.getReadOnlyProperty();
    }

    public void setModel(AbstractExtensibleWithoutField model) {
        setModel(model, null);
    }

    /** With {@code actions}, traits and annotations can be added and deleted; without, read-only. */
    public void setModel(AbstractExtensibleWithoutField model, EditActions actions) {
        traitsSection.setOwner(model, actions);
        annotationsSection.setOwner(model, actions);

        if (model == null) {
            dateCreatedField.setText("");
            dateModifiedField.setText("");
            return;
        }

        dateCreatedField.setText(model.getDateCreated().orElse(""));
        dateModifiedField.setText(model.getDateModified().orElse(""));
        LiftFieldAndTraitDefinitionTarget target = EditorSupport.targetOf(model);

        List<Node> traits = new ArrayList<>();
        for (LiftTrait t : model.getTraits()) {
            TraitEditor te = new TraitEditor(dictionary);
            te.setTrait(t, target, actions);
            traits.add(traitsSection.card(t, traitTitle(t), te, () -> model.deleteTrait(t)));
        }
        traitsSection.setItems(traits);

        annotationsSection.setItems(annotationCards(annotationsSection, model, dictionary, actions));
    }

    /** "status = done". */
    static String traitTitle(LiftTrait t) {
        String value = t.getValue();
        return value == null || value.isBlank() ? t.getName() : t.getName() + " = " + value;
    }

    /** The cards of the annotations of {@code owner}, for its annotations section. */
    static List<Node> annotationCards(ComponentSection section, HasAnnotation owner, LiftDictionary dictionary,
                                      EditActions actions) {
        List<Node> cards = new ArrayList<>();
        int i = 1;
        for (LiftAnnotation a : owner.getAnnotations()) {
            if (a == null) continue;
            AnnotationEditor ae = new AnnotationEditor(dictionary);
            ae.setAnnotation(a);
            String type = EditorSupport.featureId(a.getType());
            String title = type.isBlank() ? "#" + i : "#" + i + "  " + type;
            cards.add(section.card(a, title, ae, () -> owner.deleteAnnotation(a)));
            i++;
        }
        return cards;
    }
}
