/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftfx.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftMedia;
import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftfx.ComponentKind;
import fr.cnrs.lacito.liftfx.EditActions;
import fr.cnrs.lacito.liftfx.internal.ComponentSection;
import fr.cnrs.lacito.liftfx.internal.Messages;
import fr.cnrs.lacito.liftfx.internal.Reveal;
import fr.cnrs.lacito.liftfx.internal.SectionPane;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

/**
 * Editor for a {@link LiftPronunciation}: its forms, its media files, and its properties.
 */
public final class PronunciationEditor extends VBox {

    private final MultiTextEditor pronTextEditor;
    private final ComponentSection mediaSection;
    private final ExtensibleWithFieldEditor extensibleEditor;
    private final LiftDictionary dictionary;

    public PronunciationEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.pronTextEditor = new MultiTextEditor(dictionary);
        this.extensibleEditor = new ExtensibleWithFieldEditor(dictionary);
        setPadding(new Insets(4));

        pronTextEditor.useObjectLanguages();
        pronTextEditor.getStyleClass().add(Reveal.FIRST_INPUT);

        TitledPane textPane = new TitledPane(Messages.get("editor.pronunciation"), pronTextEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        mediaSection = new ComponentSection(ComponentKind.MEDIA, Messages.get("nav.media"), false);

        SectionPane extPane = SectionPane.properties(Messages.get("editor.properties.withFields"), extensibleEditor);

        getChildren().addAll(textPane, mediaSection, extPane);
    }

    public void setPronunciation(LiftPronunciation p) {
        setPronunciation(p, null);
    }

    /** With {@code actions}, the media and the properties can be added and deleted. */
    public void setPronunciation(LiftPronunciation p, EditActions actions) {
        mediaSection.setOwner(p, actions);
        if (p == null) {
            pronTextEditor.setMultiText(null);
            extensibleEditor.setModel(null);
            return;
        }
        pronTextEditor.setMultiText(p.getPronunciation());
        pronTextEditor.setEditActions(actions);

        Supplier<File> file = actions == null ? () -> null : actions::dictionaryFile;
        List<Node> cards = new ArrayList<>();
        for (LiftMedia m : p.getMedias()) {
            ExternalDocumentEditor ede = new ExternalDocumentEditor(dictionary, file);
            ede.setDocument(m, actions);
            cards.add(mediaSection.card(m, Objects.toString(m.getHref(), ""), ede, () -> p.deleteMedia(m)));
        }
        mediaSection.setItems(cards);
        extensibleEditor.setModel(p, actions);
    }
}
