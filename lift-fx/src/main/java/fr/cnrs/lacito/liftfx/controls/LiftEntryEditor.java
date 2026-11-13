package fr.cnrs.lacito.liftfx.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftfx.ComponentKind;
import fr.cnrs.lacito.liftfx.EditActions;
import fr.cnrs.lacito.liftfx.internal.ComponentSection;
import fr.cnrs.lacito.liftfx.internal.EditorSupport;
import fr.cnrs.lacito.liftfx.internal.Messages;
import fr.cnrs.lacito.liftfx.internal.SectionPane;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Editor for a single {@link LiftEntry}.
 *
 * Displays: forms MultiText, pronunciations, senses (SenseEditor each),
 * variants, relations, etymologies,
 * + NotableEditor for inherited properties (notes, fields, traits, annotations, dates),
 * + identity block.
 *
 * Each list is a {@link ComponentSection}: with {@link EditActions}, its [+] adds a
 * component (created through the dictionary's component builder) and each component can
 * be deleted. Hosts may optionally register {@link #setOnChanged(Runnable)} (e.g. to
 * refresh tables after a change of grammatical info) and {@link #setOnOpenSense(Consumer)}
 * (shows an "open in Senses view" link per sense).
 */
public final class LiftEntryEditor extends VBox {

    private final MultiTextEditor formsEditor;
    private final ComponentSection pronunciationsSection;
    private final ComponentSection sensesSection;
    private final ComponentSection variantsSection;
    private final ComponentSection relationsSection;
    private final ComponentSection etymologiesSection;
    private final NotableEditor notableEditor;
    private final GridPane identityBlock = new GridPane();
    private Runnable onChanged = null;
    private Consumer<LiftSense> onOpenSense = null;
    private final LiftDictionary dictionary;

    public LiftEntryEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.formsEditor = new MultiTextEditor(dictionary);
        this.notableEditor = new NotableEditor(dictionary);
        setPadding(new Insets(4));

        Label lexicalTitle = sectionTitle("editor.section.lexicalContent");

        formsEditor.useObjectLanguages();
        formsEditor.setFixedLanguageRows(true);
        TitledPane formsPane = titledPane(Messages.get("editor.forms"), formsEditor, true);
        pronunciationsSection = new ComponentSection(ComponentKind.PRONUNCIATION, Messages.get("editor.pronunciations"), false);
        sensesSection = new ComponentSection(ComponentKind.SENSE, Messages.get("editor.senses"), true);

        Label variantsTitle = sectionTitle("editor.section.variantsRelations");
        variantsSection = new ComponentSection(ComponentKind.VARIANT, Messages.get("editor.variants"), false);
        relationsSection = new ComponentSection(ComponentKind.RELATION, Messages.get("editor.relations"), false);
        etymologiesSection = new ComponentSection(ComponentKind.ETYMOLOGY, Messages.get("editor.etymologies"), false);

        Label metadataTitle = sectionTitle("editor.section.metadata");
        SectionPane extPane = SectionPane.properties(Messages.get("editor.properties.inherited"), notableEditor);

        identityBlock.setHgap(8);
        identityBlock.setVgap(6);
        identityBlock.getStyleClass().add("identity-block");
        TitledPane identityPane = titledPane(Messages.get("editor.identity"), identityBlock, true);

        getChildren().addAll(
            lexicalTitle,
            formsPane,
            pronunciationsSection,
            sensesSection,
            variantsTitle,
            variantsSection,
            relationsSection,
            etymologiesSection,
            metadataTitle,
            extPane,
            identityPane
        );
    }

    /** Called after the editor modified the entry outside its sections (e.g. a grammatical info). */
    public void setOnChanged(Runnable callback) {
        this.onChanged = callback;
    }

    /**
     * When set, each sense shows a link calling this consumer (e.g. to open the sense in
     * the Senses view). When null (default), no link is shown.
     */
    public void setOnOpenSense(Consumer<LiftSense> callback) {
        this.onOpenSense = callback;
    }

    public void setEntry(LiftEntry entry) {
        setEntry(entry, null);
    }

    /** Shows an entry; with {@code actions}, its components can be added and deleted. */
    public void setEntry(LiftEntry entry, EditActions actions) {
        pronunciationsSection.setOwner(entry, actions);
        sensesSection.setOwner(entry, actions);
        variantsSection.setOwner(entry, actions);
        relationsSection.setOwner(entry, actions);
        etymologiesSection.setOwner(entry, actions);
        identityBlock.getChildren().clear();

        if (entry == null) {
            formsEditor.setMultiText(null);
            formsEditor.setEditActions(null);
            notableEditor.setModel(null);
            return;
        }

        formsEditor.setMultiText(entry.getForms());
        formsEditor.setEditActions(actions);

        List<Node> prons = new ArrayList<>();
        for (LiftPronunciation p : entry.getPronunciations()) {
            PronunciationEditor pe = new PronunciationEditor(dictionary);
            pe.setPronunciation(p, actions);
            prons.add(pronunciationsSection.card(p, EditorSupport.firstForm(p.getPronunciation()), pe,
                () -> entry.deletePronunciation(p)));
        }
        pronunciationsSection.setItems(prons);

        List<Node> senses = new ArrayList<>();
        List<LiftSense> senseList = entry.getSenses();
        for (int i = 0; i < senseList.size(); i++) {
            LiftSense sense = senseList.get(i);
            senses.add(SenseEditor.senseCard(sensesSection, i, sense, senseContent(sense, actions),
                () -> entry.deleteSense(sense), i == 0));
        }
        sensesSection.setItems(senses);

        List<Node> variants = new ArrayList<>();
        for (LiftVariant v : entry.getVariants()) {
            VariantEditor ve = new VariantEditor(dictionary);
            ve.setVariant(v, actions);
            String title = v.getRefId().orElse(EditorSupport.firstForm(v.getForms()));
            variants.add(variantsSection.card(v, title, ve, () -> entry.deleteVariant(v)));
        }
        variantsSection.setItems(variants);

        List<Node> relations = new ArrayList<>();
        for (LiftRelation r : entry.getRelations()) {
            RelationEditor re = new RelationEditor(dictionary);
            re.setRelation(r, actions);
            relations.add(relationsSection.card(r, SenseEditor.relationTitle(r), re, () -> entry.deleteRelation(r)));
        }
        relationsSection.setItems(relations);

        List<Node> etymologies = new ArrayList<>();
        for (LiftEtymology et : entry.getEtymologies()) {
            EtymologyEditor ee = new EtymologyEditor(dictionary);
            ee.setEtymology(et, actions);
            String title = (EditorSupport.featureId(et.getType()) + "  " + EditorSupport.firstForm(et.getForms())).strip();
            etymologies.add(etymologiesSection.card(et, title, ee, () -> entry.deleteEtymology(et)));
        }
        etymologiesSection.setItems(etymologies);

        notableEditor.setModel(entry, actions);

        // Identity block: ID, dates (read-only, grey)
        addReadOnlyRow(0, Messages.get("field.id"), entry.getId().orElse(""));
        addReadOnlyRow(1, Messages.get("field.dateCreated"), entry.getDateCreated().orElse(""));
        addReadOnlyRow(2, Messages.get("field.dateModified"), entry.getDateModified().orElse(""));
    }

    /* ─── Senses ─── */

    private Node senseContent(LiftSense sense, EditActions actions) {
        VBox box = new VBox(4);
        if (onOpenSense != null) {
            Hyperlink link = new Hyperlink(Messages.get("sense.openInSensesView"));
            link.setOnAction(e -> onOpenSense.accept(sense));
            box.getChildren().add(link);
        }
        SenseEditor se = new SenseEditor(dictionary);
        se.setGrammaticalInfoValues(EditorSupport.sortedFeatureIds(dictionary.getHeader().getGrammaticalInfoManager()));
        se.setOnGramInfoChanged(this::fireChanged);
        se.setSense(sense, actions);
        box.getChildren().add(se);
        return box;
    }

    /** Display text of a sense: gloss(es) joined by " / ", else definition, else "?". */
    public static String senseDisplayText(LiftSense sense) {
        if (sense == null) return "?";
        String gloss = EditorSupport.plainText(sense.getGlosses());
        if (!gloss.isEmpty()) return gloss;
        String def = EditorSupport.plainText(sense.getDefinition());
        if (!def.isEmpty()) return def;
        return "?";
    }

    private void fireChanged() {
        if (onChanged != null) onChanged.run();
    }

    /* ─── Layout helpers ─── */

    private void addReadOnlyRow(int row, String label, String value) {
        TextField tf = EditorSupport.readOnlyField(value);
        GridPane.setHgrow(tf, Priority.ALWAYS);
        identityBlock.add(new Label(label), 0, row);
        identityBlock.add(tf, 1, row);
    }

    private static Label sectionTitle(String i18nKey) {
        Label lbl = new Label(Messages.get(i18nKey));
        lbl.getStyleClass().add("editor-section-title");
        return lbl;
    }

    private static TitledPane titledPane(String title, javafx.scene.Node content, boolean expanded) {
        TitledPane tp = new TitledPane(title, content);
        tp.setExpanded(expanded);
        tp.setAnimated(false);
        return tp;
    }
}
