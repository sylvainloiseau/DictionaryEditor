package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.builder.DictionaryComponentBuilderFactory;
import fr.cnrs.lacito.liftapi.model.*;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * What an editor needs to add and delete the components it shows: the dictionary, its file
 * (for the illustrations and media), and how to show the change. One instance serves a
 * whole editor and the editors nested in it. An editor given none is read-only.
 */
public final class EditActions {

    private final LiftDictionary dictionary;
    private final Supplier<File> dictionaryFile;
    private final Runnable refresh;

    private EditActions(LiftDictionary dictionary, Supplier<File> dictionaryFile, Runnable refresh) {
        this.dictionary = Objects.requireNonNull(dictionary);
        this.dictionaryFile = dictionaryFile == null ? () -> null : dictionaryFile;
        this.refresh = refresh == null ? () -> {} : refresh;
    }

    /**
     * @param dictionaryFile the file of the dictionary; may give null (an unsaved dictionary)
     * @param refresh        rebuilds the editor after an addition or a deletion
     */
    public static EditActions of(LiftDictionary dictionary, Supplier<File> dictionaryFile, Runnable refresh) {
        return new EditActions(dictionary, dictionaryFile, refresh);
    }

    public LiftDictionary dictionary() {
        return dictionary;
    }

    /** The file of the dictionary, or null when it has none yet. */
    public File dictionaryFile() {
        return dictionaryFile.get();
    }

    /** The folder of the dictionary's file, or null. */
    public File dictionaryFolder() {
        File f = dictionaryFile();
        File dir = f == null ? null : f.getAbsoluteFile().getParentFile();
        return dir != null && dir.isDirectory() ? dir : null;
    }

    /** Rebuilds the editor, after an addition or a deletion. */
    public void refresh() {
        refresh.run();
    }

    private DictionaryComponentBuilderFactory builder() {
        return dictionary.getComponentBuilder();
    }

    /**
     * Creates a component of {@code kind} in {@code owner}.
     *
     * @param choice the type (or name) for a {@link ComponentKind.Input#TYPE} kind, the path
     *               of the file for a {@link ComponentKind.Input#FILE} kind; ignored otherwise
     * @return the new component
     */
    public Object add(ComponentKind kind, Object owner, String choice) {
        if (!kind.canBeAddedTo(owner)) throw new IllegalArgumentException(kind + " cannot be added to " + owner);
        return switch (kind) {
            case SENSE, SUB_SENSE -> builder().sense((HasSense) owner).build();
            case EXAMPLE -> builder().example((LiftSense) owner).build();
            case TRANSLATION -> builder().translation((LiftExample) owner, choice).build();
            case PRONUNCIATION -> builder().pronunciation((HasPronunciation) owner).build();
            case VARIANT -> builder().variant((LiftEntry) owner).build();
            case RELATION -> builder().relation(choice, (HasRelations) owner).build();
            case ETYMOLOGY -> builder().etymology((LiftEntry) owner, choice, "").build();
            case REVERSAL -> owner instanceof LiftReversal r
                ? builder().reversalMain(r).withType(choice).build()
                : builder().reversal((HasReversal) owner).withType(choice).build();
            case ILLUSTRATION -> builder().illustration((LiftSense) owner, choice).build();
            case MEDIA -> builder().media((LiftPronunciation) owner, choice).build();
            case NOTE -> builder().note((HasNote) owner, choice).build();
            case FIELD -> builder().field((HasField) owner, choice).build();
            case TRAIT -> builder().trait((HasTrait) owner, choice, "").build();
            case ANNOTATION -> builder().annotation((HasAnnotation) owner, choice).build();
        };
    }

    /** The types offered when adding a component of a {@link ComponentKind.Input#TYPE} kind to {@code owner}. */
    public TypeChoices choices(ComponentKind kind, Object owner) {
        LiftHeader header = dictionary.getHeader();
        return switch (kind) {
            case TRANSLATION -> {
                Set<String> used = ((LiftExample) owner).getTranslationComponents().stream()
                    .map(t -> EditorSupport.featureId(t.getType())).collect(Collectors.toSet());
                yield new TypeChoices(EditorSupport.sortedFeatureIds(header.getTranslationTypeManager()), used, false,
                    "tooltip.noType.translation");
            }
            case RELATION -> TypeChoices.open(EditorSupport.sortedFeatureIds(header.getRelationTypeManager()));
            case ETYMOLOGY -> TypeChoices.open(EditorSupport.sortedFeatureIds(header.getEtymologyTypeManager()));
            case REVERSAL -> TypeChoices.open(EditorSupport.sortedFeatureIds(header.getInverseTypeManager()));
            case NOTE -> {
                Set<String> used = owner instanceof AbstractNotable n
                    ? n.getNotes().keySet().stream().map(EditorSupport::featureId).collect(Collectors.toSet())
                    : Set.of();
                yield new TypeChoices(EditorSupport.sortedFeatureIds(header.getNoteTypeManager()), used, true, null);
            }
            case FIELD -> {
                Set<String> used = owner instanceof AbstractExtensibleWithField f ? Set.copyOf(f.getFields().keySet()) : Set.of();
                yield new TypeChoices(definitionNames(EditorSupport.fieldDefinitions(header, EditorSupport.targetOf(owner))),
                    used, false, "tooltip.noType.field");
            }
            case TRAIT -> new TypeChoices(
                definitionNames(EditorSupport.traitDefinitions(header, EditorSupport.targetOf(owner))),
                Set.of(), false, "tooltip.noType.trait");
            case ANNOTATION -> TypeChoices.open(EditorSupport.sortedFeatureIds(header.getAnnotationTypeManager()));
            default -> throw new IllegalArgumentException(kind + " has no type");
        };
    }

    private static List<String> definitionNames(List<LiftFieldAndTraitDefinition> definitions) {
        return definitions.stream().map(LiftFieldAndTraitDefinition::getName).distinct().sorted().toList();
    }

    /**
     * Whether a component has nothing in it: no form, no text, no child component. Deleting
     * an empty component never asks first.
     */
    public static boolean isEmpty(Object component) {
        boolean own = switch (component) {
            case LiftSense s -> s.getGlosses().isEmpty() && s.getDefinition().isEmpty()
                && s.getGrammaticalInfo().isEmpty() && s.getExamples().isEmpty() && s.getSenses().isEmpty()
                && s.getRelations().isEmpty() && s.getReversals().isEmpty() && s.getIllustrations().isEmpty();
            case LiftExample e -> e.getExample().isEmpty() && e.getTranslationComponents().isEmpty()
                && e.getSource().filter(x -> !x.isBlank()).isEmpty();
            case LiftVariant v -> v.getForms().isEmpty() && v.getPronunciations().isEmpty() && v.getRelations().isEmpty();
            case LiftEtymology e -> e.getForms().isEmpty() && e.getGlosses().isEmpty()
                && (e.getSource() == null || e.getSource().isBlank());
            case LiftReversal r -> r.getForms().isEmpty() && r.getMain() == null;
            default -> false;
        };
        if (!own) return false;
        if (component instanceof AbstractExtensibleWithoutField x
            && (!x.getTraits().isEmpty() || !x.getAnnotations().isEmpty())) return false;
        if (component instanceof AbstractExtensibleWithField x && !x.getFields().isEmpty()) return false;
        return !(component instanceof AbstractNotable x) || x.getNotes().isEmpty();
    }
}
