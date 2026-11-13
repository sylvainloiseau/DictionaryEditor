package fr.cnrs.lacito.liftfx;

import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftfx.internal.ComponentSection;
import fr.cnrs.lacito.liftfx.internal.Messages;
import java.util.function.Predicate;

/**
 * The kinds of component an editor lists in a {@link ComponentSection}, and adds with its
 * [+] button or with the Add menu.
 */
public enum ComponentKind {
    SENSE("sense", Input.NONE, o -> o instanceof LiftEntry),
    SUB_SENSE("subSense", Input.NONE, o -> o instanceof LiftSense),
    EXAMPLE("example", Input.NONE, o -> o instanceof LiftSense),
    TRANSLATION("translation", Input.TYPE, o -> o instanceof LiftExample),
    PRONUNCIATION("pronunciation", Input.NONE, o -> o instanceof HasPronunciation),
    VARIANT("variant", Input.NONE, o -> o instanceof LiftEntry),
    RELATION("relation", Input.TYPE, o -> o instanceof HasRelations),
    ETYMOLOGY("etymology", Input.TYPE, o -> o instanceof LiftEntry),
    REVERSAL("reversal", Input.TYPE, o -> o instanceof HasReversal),
    ILLUSTRATION("illustration", Input.FILE, o -> o instanceof LiftSense),
    MEDIA("media", Input.FILE, o -> o instanceof LiftPronunciation),
    NOTE("note", Input.TYPE, o -> o instanceof HasNote),
    FIELD("field", Input.TYPE, o -> o instanceof HasField),
    TRAIT("trait", Input.TYPE, o -> o instanceof HasTrait),
    ANNOTATION("annotation", Input.TYPE, o -> o instanceof HasAnnotation);

    /** What the user gives when adding a component of this kind. */
    public enum Input {
        /** Nothing: the component is created empty. */
        NONE,
        /** A type (or a name), chosen in a menu. */
        TYPE,
        /** A file. */
        FILE
    }

    private final String id;
    private final Input input;
    private final Predicate<Object> owners;

    ComponentKind(String id, Input input, Predicate<Object> owners) {
        this.id = id;
        this.input = input;
        this.owners = owners;
    }

    /** "subSense": used in the i18n keys, the node ids ({@code add-subSense}) and the command ids. */
    public String id() {
        return id;
    }

    public Input input() {
        return input;
    }

    /** Whether a component of this kind can be added to {@code owner}. */
    public boolean canBeAddedTo(Object owner) {
        return owner != null && owners.test(owner);
    }

    /** The name of the kind, singular ("Sense"). */
    public String label() {
        return Messages.get("kind." + id);
    }

    /** "Add a sense". */
    public String addTooltip() {
        return Messages.get("tooltip.add." + id);
    }

    /** "Delete this sense". */
    public String deleteTooltip() {
        return Messages.get("tooltip.delete." + id);
    }

    /**
     * Whether deleting a component of this kind asks first, when the component is not empty:
     * the main components, which contain others.
     */
    public boolean confirmsDeletion() {
        return switch (this) {
            case SENSE, SUB_SENSE, EXAMPLE, VARIANT, ETYMOLOGY, REVERSAL -> true;
            default -> false;
        };
    }

    /** The kind of a component, or null when no section lists it. */
    public static ComponentKind of(Object component) {
        return switch (component) {
            case LiftSense s -> s.getParent() instanceof LiftSense ? SUB_SENSE : SENSE;
            case LiftExample e -> EXAMPLE;
            case LiftTranslation t -> TRANSLATION;
            case LiftPronunciation p -> PRONUNCIATION;
            case LiftVariant v -> VARIANT;
            case LiftRelation r -> RELATION;
            case LiftEtymology e -> ETYMOLOGY;
            case LiftReversal r -> REVERSAL;
            case LiftIllustration i -> ILLUSTRATION;
            case LiftMedia m -> MEDIA;
            case LiftNote n -> NOTE;
            case LiftField f -> FIELD;
            case LiftTrait t -> TRAIT;
            case LiftAnnotation a -> ANNOTATION;
            case null, default -> null;
        };
    }
}
