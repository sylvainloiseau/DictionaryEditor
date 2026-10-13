package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.builder.SenseBuilder;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

/**
 * Editor for a single {@link LiftEntry}.
 *
 * Displays: forms MultiText, pronunciations, senses (SenseEditor each),
 * variants, relations, etymologies,
 * + NotableEditor for inherited properties (notes, fields, traits, annotations, dates),
 * + identity block and buttons to add senses, variants, pronunciations, relations, etymologies.
 *
 * Self-contained: every component is created through the dictionary's component builder,
 * and the editor rebuilds itself after each addition. Hosts may optionally register
 * {@link #setOnChanged(Runnable)} (e.g. to refresh tables) and
 * {@link #setOnOpenSense(Consumer)} (shows an "open in Senses view" link per sense).
 */
public final class LiftEntryEditor extends VBox {

    private final MultiTextEditor formsEditor;
    private final VBox pronunciationsBox = new VBox(6);
    private final VBox sensesBox = new VBox(6);
    private final VBox variantsBox = new VBox(6);
    private final VBox relationsBox = new VBox(6);
    private final VBox etymologiesBox = new VBox(6);
    private final TitledPane pronunciationsPane;
    private final TitledPane sensesPane;
    private final TitledPane variantsPane;
    private final TitledPane relationsPane;
    private final TitledPane etymologiesPane;
    private final NotableEditor notableEditor;
    private final GridPane identityBlock = new GridPane();
    private final FlowPane addButtons = new FlowPane(8, 6);
    /** Currently displayed entry — used to rebuild the editor after an addition. */
    private LiftEntry currentEntry = null;
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

        formsEditor.setFixedLanguageRows(true);
        TitledPane formsPane = titledPane(I18n.get(Keys.EDITOR_FORMS), formsEditor, true);
        pronunciationsPane = titledPane(I18n.get(Keys.EDITOR_PRONUNCIATIONS), pronunciationsBox, false);
        sensesPane = titledPane(I18n.get(Keys.EDITOR_SENSES), sensesBox, true);

        Label variantsTitle = sectionTitle("editor.section.variantsRelations");
        variantsPane = titledPane(I18n.get(Keys.EDITOR_VARIANTS), variantsBox, false);
        relationsPane = titledPane(I18n.get(Keys.EDITOR_RELATIONS), relationsBox, false);
        etymologiesPane = titledPane(I18n.get(Keys.EDITOR_ETYMOLOGIES), etymologiesBox, false);

        Label metadataTitle = sectionTitle("editor.section.metadata");
        TitledPane extPane = titledPane(
            "Propriétés héritées (notes, champs, traits, annotations, dates)",
            notableEditor,
            false
        );

        identityBlock.setHgap(8);
        identityBlock.setVgap(6);
        identityBlock.setStyle(
            "-fx-background-color: #e8e8e8; -fx-padding: 8; -fx-background-radius: 4;"
        );
        TitledPane identityPane = titledPane(I18n.get("editor.identity"), identityBlock, true);

        addButtons.setPadding(new Insets(8, 0, 0, 0));

        getChildren().addAll(
            lexicalTitle,
            formsPane,
            pronunciationsPane,
            sensesPane,
            variantsTitle,
            variantsPane,
            relationsPane,
            etymologiesPane,
            metadataTitle,
            extPane,
            identityPane,
            addButtons
        );
    }

    /** Called after the editor modified the entry (added a component, changed a gram. info). */
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
        currentEntry = entry;
        pronunciationsBox.getChildren().clear();
        sensesBox.getChildren().clear();
        variantsBox.getChildren().clear();
        relationsBox.getChildren().clear();
        etymologiesBox.getChildren().clear();
        identityBlock.getChildren().clear();
        addButtons.getChildren().clear();

        if (entry == null) {
            formsEditor.setMultiText(null);
            notableEditor.setModel(null);
            updateCounts(0, 0, 0, 0, 0);
            return;
        }

        Set<String> objLangs = dictionary.getObjectLanguageManager().getLanguages();
        Set<String> metaLangs = dictionary.getMetaLanguageManager().getLanguages();
        List<String> relationTypes = relationTypes();

        formsEditor.setMultiText(entry.getForms());

        for (LiftPronunciation p : entry.getPronunciations()) {
            PronunciationEditor pe = new PronunciationEditor(dictionary);
            pe.setPronunciation(p);
            pronunciationsBox.getChildren().add(pe);
        }

        List<LiftSense> senses = entry.getSenses();
        for (int i = 0; i < senses.size(); i++) {
            sensesBox.getChildren().add(buildSensePane(senses.get(i), i + 1, relationTypes));
        }

        for (LiftVariant v : entry.getVariants()) {
            VariantEditor ve = new VariantEditor(dictionary);
            ve.setRelationTypes(relationTypes);
            ve.setVariant(v, variantAddActions(v));
            variantsBox.getChildren().add(ve);
        }

        for (LiftRelation r : entry.getRelations()) {
            RelationEditor re = new RelationEditor(dictionary);
            re.setRelation(r);
            relationsBox.getChildren().add(re);
        }

        for (LiftEtymology et : entry.getEtymologies()) {
            EtymologyEditor ee = new EtymologyEditor(dictionary);
            ee.setEtymology(et, objLangs, metaLangs);
            etymologiesBox.getChildren().add(ee);
        }

        updateCounts(
            entry.getPronunciations().size(),
            senses.size(),
            entry.getVariants().size(),
            entry.getRelations().size(),
            entry.getEtymologies().size()
        );

        notableEditor.setModel(entry, extensibleAddActions(entry));

        // Identity block: ID, dates (read-only, grey)
        addReadOnlyRow(0, I18n.get("field.id"), entry.getId().orElse(""));
        addReadOnlyRow(1, I18n.get("field.dateCreated"), entry.getDateCreated().orElse(""));
        addReadOnlyRow(2, I18n.get("field.dateModified"), entry.getDateModified().orElse(""));

        buildAddButtons(entry, relationTypes);
    }

    /* ─── Senses ─── */

    private TitledPane buildSensePane(LiftSense sense, int index, List<String> relationTypes) {
        VBox box = new VBox(4);
        if (onOpenSense != null) {
            Hyperlink link = new Hyperlink(I18n.get("sense.openInSensesView"));
            link.setOnAction(e -> onOpenSense.accept(sense));
            box.getChildren().add(link);
        }
        SenseEditor se = new SenseEditor(dictionary);
        se.setRelationTypes(relationTypes);
        se.setGrammaticalInfoValues(headerRangeValues("grammatical-info"));
        se.setOnGramInfoChanged(this::fireChanged);
        BiConsumer<String, MultiText> onAddAnnotation =
            (name, mt) -> dictionary.getComponentBuilder().annotation(mt, name).build();
        se.setSense(
            sense,
            onAddAnnotation,
            annotationNames(),
            this::extensibleAddActions,
            this::extensibleAddActions
        );
        box.getChildren().add(se);
        return titledPane("#" + index + "  " + senseDisplayText(sense), box, index == 1);
    }

    /** Display text of a sense: gloss(es) joined by " / ", else definition, else "?". */
    public static String senseDisplayText(LiftSense sense) {
        if (sense == null) return "?";
        String gloss = plainText(sense.getGlosses());
        if (!gloss.isEmpty()) return gloss;
        String def = plainText(sense.getDefinition());
        if (!def.isEmpty()) return def;
        return "?";
    }

    private static String plainText(MultiText mt) {
        return mt
            .getForms()
            .stream()
            .map(Form::toPlainText)
            .filter(t -> t != null && !t.isBlank())
            .collect(Collectors.joining(" / "));
    }

    /* ─── Add buttons ─── */

    private void buildAddButtons(LiftEntry entry, List<String> relationTypes) {
        Button addSenseBtn = new Button(I18n.get("btn.addSense"));
        addSenseBtn.setOnAction(e -> {
            SenseBuilder sb = dictionary.getComponentBuilder().sense(entry);
            List<String> giValues = headerRangeValues("grammatical-info");
            if (!giValues.isEmpty()) {
                ChoiceDialog<String> dlg = new ChoiceDialog<>(giValues.get(0), giValues);
                dlg.setTitle(I18n.get("btn.addSense"));
                dlg.setHeaderText(I18n.get("col.gramInfo"));
                dlg.showAndWait()
                    .filter(v -> v != null && !v.isBlank())
                    .ifPresent(v -> sb.withPartOfSpeech(v.trim()));
            }
            sb.build();
            refresh();
        });

        Button addVariantBtn = new Button(I18n.get("btn.addVariant"));
        addVariantBtn.setOnAction(e -> {
            dictionary.getComponentBuilder().variant(entry).build();
            refresh();
        });

        Button addPronBtn = new Button(I18n.get("btn.addPronunciation"));
        addPronBtn.setOnAction(e -> {
            dictionary.getComponentBuilder().pronunciation(entry).build();
            refresh();
        });

        Button addRelationBtn = new Button(I18n.get("btn.addRelation"));
        boolean noRelationTypes = relationTypes.isEmpty();
        addRelationBtn.setDisable(noRelationTypes);
        addRelationBtn.setOnAction(e -> {
            if (relationTypes.isEmpty()) return;
            ChoiceDialog<String> dlg = new ChoiceDialog<>(relationTypes.get(0), relationTypes);
            dlg.setTitle(I18n.get("btn.addRelation"));
            dlg.setHeaderText(I18n.get("col.type"));
            dlg.showAndWait()
                .filter(t -> t != null && !t.isBlank())
                .ifPresent(type -> {
                    dictionary.getComponentBuilder().relation(type.trim(), entry).build();
                    refresh();
                });
        });
        StackPane addRelationWrapper = new StackPane(addRelationBtn);
        if (noRelationTypes) {
            Tooltip.install(addRelationWrapper, new Tooltip(I18n.get("tooltip.noRelationTypes")));
        }

        Button addEtymologyBtn = new Button(I18n.get("btn.addEtymology"));
        addEtymologyBtn.setOnAction(e -> showAddEtymologyDialog(entry));

        addButtons.getChildren().addAll(
            addSenseBtn,
            addVariantBtn,
            addPronBtn,
            addRelationWrapper,
            addEtymologyBtn
        );
    }

    private void showAddEtymologyDialog(LiftEntry entry) {
        Dialog<Pair<String, String>> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("btn.addEtymology"));
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField sourceField = new TextField();
        sourceField.setPromptText(I18n.get("col.source"));
        List<String> knownTypes = dictionary.getHeader().getEtymologyTypeManager().getFeatures().values().stream().map(x -> x.getId()).toList();
        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList(knownTypes));
        typeCombo.setEditable(true);
        typeCombo.setPromptText(I18n.get("col.type"));
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label(I18n.get("col.type")), 0, 0);
        grid.add(typeCombo, 1, 0);
        grid.add(new Label(I18n.get("col.source")), 0, 1);
        grid.add(sourceField, 1, 1);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(btn ->
            btn == ButtonType.OK
                ? new Pair<>(
                      typeCombo.getValue() != null
                          ? typeCombo.getValue()
                          : typeCombo.getEditor().getText(),
                      sourceField.getText()
                  )
                : null
        );
        dlg.showAndWait().ifPresent(pair -> {
            String type = pair.getKey() != null ? pair.getKey().trim() : "";
            String source = pair.getValue() != null ? pair.getValue().trim() : "";
            if (type.isEmpty()) type = "unknown";
            dictionary.getComponentBuilder().etymology(entry, type, source).build();
            refresh();
        });
    }

    /* ─── Add actions: every addition rebuilds this editor ─── */

    private <T extends AbstractNotable> ExtensibleAddActions extensibleAddActions(T model) {
        return new ExtensibleAddActions() {
            @Override
            public void addTrait(String name, String value) {
                dictionary.getComponentBuilder().trait(model, name, value).build();
            }

            @Override
            public void addAnnotation(String name) {
                dictionary.getComponentBuilder().annotation(model, name).build();
            }

            @Override
            public void addField(String type) {
                dictionary.getComponentBuilder().field(model, type).build();
            }

            @Override
            public void addNote(String type) {
                dictionary.getComponentBuilder().note(model, type).build();
            }

            @Override
            public void refresh() {
                LiftEntryEditor.this.refresh();
            }
        };
    }

    private ExtensibleAddActions variantAddActions(LiftVariant v) {
        return new ExtensibleAddActions() {
            @Override
            public void addTrait(String name, String value) {
                dictionary.getComponentBuilder().trait(v, name, value).build();
            }

            @Override
            public void addAnnotation(String name) {
                dictionary.getComponentBuilder().annotation(v, name).build();
            }

            @Override
            public void addField(String type) {
                dictionary.getComponentBuilder().field(v, type).build();
            }

            @Override
            public void addPronunciation() {
                dictionary.getComponentBuilder().pronunciation(v).build();
            }

            @Override
            public void addRelation(String type) {
                dictionary.getComponentBuilder().relation(type, v).build();
            }

            @Override
            public void refresh() {
                LiftEntryEditor.this.refresh();
            }
        };
    }

    private void refresh() {
        setEntry(currentEntry);
        fireChanged();
    }

    private void fireChanged() {
        if (onChanged != null) onChanged.run();
    }

    /* ─── Header values ─── */

    private List<String> relationTypes() {
        return dictionary.getHeader().getRelationTypeManager().getFeatures().values().stream().map(x -> x.getId()).toList();
    }

    private List<String> annotationNames() {
        return dictionary.getHeader().getAnnotationTypeManager().getFeatures().values().stream().map(x -> x.getId()).toList();
    }

    private List<String> headerRangeValues(String rangeId) {
        LiftHeader h = dictionary.getHeader();
        if (h == null) return List.of();
        return h
            .getFeatureSets()
            .stream()
            .filter(r -> rangeId.equals(r.getId()))
            .findFirst()
            .map(r -> r.getFeatures().values().stream().map(Feature::getId).sorted().toList())
            .orElse(List.of());
    }

    /* ─── Layout helpers ─── */

    private void updateCounts(int pronunciations, int senses, int variants, int relations, int etymologies) {
        pronunciationsPane.setText(I18n.get(Keys.EDITOR_PRONUNCIATIONS) + " (" + pronunciations + ")");
        sensesPane.setText(I18n.get(Keys.EDITOR_SENSES) + " (" + senses + ")");
        variantsPane.setText(I18n.get(Keys.EDITOR_VARIANTS) + " (" + variants + ")");
        relationsPane.setText(I18n.get(Keys.EDITOR_RELATIONS) + " (" + relations + ")");
        etymologiesPane.setText(I18n.get(Keys.EDITOR_ETYMOLOGIES) + " (" + etymologies + ")");
    }

    private void addReadOnlyRow(int row, String label, String value) {
        TextField tf = new TextField(value);
        tf.setEditable(false);
        tf.setStyle("-fx-text-fill: #666; -fx-background-color: #f0f0f0;");
        GridPane.setHgrow(tf, Priority.ALWAYS);
        identityBlock.add(new Label(label), 0, row);
        identityBlock.add(tf, 1, row);
    }

    private static Label sectionTitle(String i18nKey) {
        Label lbl = new Label(I18n.get(i18nKey));
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
