package fr.cnrs.lacito.liftgui.ui.table.catalogs;

import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithField;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithoutField;
import fr.cnrs.lacito.liftapi.model.AbstractLiftRoot;
import fr.cnrs.lacito.liftapi.model.AbstractNotable;
import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftEtymology;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftField;
import fr.cnrs.lacito.liftapi.model.LiftIllustration;
import fr.cnrs.lacito.liftapi.model.LiftMedia;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftapi.model.LiftRelation;
import fr.cnrs.lacito.liftapi.model.LiftReversal;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftapi.model.LiftTranslation;
import fr.cnrs.lacito.liftapi.model.LiftVariant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The containment of the dictionary model: which components a component holds directly. It
 * gives the counts of subcomponents and the kinds of ancestors a component can have.
 */
public final class ModelTree {

    private ModelTree() {}

    /**
     * A kind of subcomponent of a component.
     *
     * @param id      stable id ("senses", "examples"…)
     * @param i18nKey its name (plural)
     * @param type    the class of the subcomponents
     * @param list    the subcomponents of a component
     */
    public record Child(String id, String i18nKey, Class<?> type, Function<Object, Collection<?>> list) {}

    /** The kinds of component that have a table, in the order of the navigation tree. */
    public static final List<Class<?>> KINDS = List.of(LiftEntry.class, LiftSense.class, LiftExample.class,
        LiftTranslation.class, LiftNote.class, LiftVariant.class, LiftPronunciation.class, LiftMedia.class,
        LiftEtymology.class, LiftRelation.class, LiftReversal.class, LiftIllustration.class, LiftField.class,
        LiftTrait.class, LiftAnnotation.class);

    private static final Child SENSES = new Child("senses", "nav.senses", LiftSense.class, o -> sensesOf(o));
    private static final Child SUBSENSES = new Child("subsenses", "col.subsenses", LiftSense.class, o -> ((LiftSense) o).getSenses());
    private static final Child VARIANTS = new Child("variants", "nav.variants", LiftVariant.class, o -> ((LiftEntry) o).getVariants());
    private static final Child PRONUNCIATIONS = new Child("pronunciations", "nav.pronunciations", LiftPronunciation.class,
        o -> o instanceof LiftEntry e ? e.getPronunciations() : ((LiftVariant) o).getPronunciations());
    private static final Child RELATIONS = new Child("relations", "nav.relations", LiftRelation.class, o -> switch (o) {
        case LiftEntry e -> e.getRelations();
        case LiftSense s -> s.getRelations();
        case LiftVariant v -> v.getRelations();
        default -> List.of();
    });
    private static final Child ETYMOLOGIES = new Child("etymologies", "nav.etymologies", LiftEtymology.class,
        o -> ((LiftEntry) o).getEtymologies());
    private static final Child EXAMPLES = new Child("examples", "nav.examples", LiftExample.class, o -> ((LiftSense) o).getExamples());
    private static final Child ILLUSTRATIONS = new Child("illustrations", "nav.illustrations", LiftIllustration.class,
        o -> ((LiftSense) o).getIllustrations());
    private static final Child REVERSALS = new Child("reversals", "nav.reversals", LiftReversal.class,
        o -> o instanceof LiftSense s ? s.getReversals() : ((LiftReversal) o).getReversals());
    private static final Child TRANSLATIONS = new Child("translations", "nav.translations", LiftTranslation.class,
        o -> ((LiftExample) o).getTranslationComponents());
    private static final Child MEDIA = new Child("media", "nav.media", LiftMedia.class, o -> ((LiftPronunciation) o).getMedias());
    private static final Child NOTES = new Child("notes", "nav.notes", LiftNote.class, o -> ((AbstractNotable) o).getNotes().values());
    private static final Child FIELDS = new Child("fields", "nav.fields", LiftField.class,
        o -> ((AbstractExtensibleWithField) o).getFields().values());
    private static final Child TRAITS = new Child("traits", "nav.traits", LiftTrait.class,
        o -> ((AbstractExtensibleWithoutField) o).getTraits());
    private static final Child ANNOTATIONS = new Child("annotations", "nav.annotations", LiftAnnotation.class, o -> switch (o) {
        case AbstractExtensibleWithoutField x -> x.getAnnotations();
        case LiftTrait t -> t.getAnnotations();
        default -> List.of();
    });

    private static Collection<?> sensesOf(Object o) {
        return o instanceof LiftEntry e ? e.getSenses() : ((LiftSense) o).getSenses();
    }

    /** The kinds of subcomponent a component of class {@code c} holds directly. */
    public static List<Child> children(Class<?> c) {
        List<Child> out = new ArrayList<>();
        if (c == LiftEntry.class) out.addAll(List.of(SENSES, VARIANTS, PRONUNCIATIONS, RELATIONS, ETYMOLOGIES));
        if (c == LiftSense.class) out.addAll(List.of(SUBSENSES, EXAMPLES, ILLUSTRATIONS, REVERSALS, RELATIONS));
        if (c == LiftExample.class) out.add(TRANSLATIONS);
        if (c == LiftVariant.class) out.addAll(List.of(PRONUNCIATIONS, RELATIONS));
        if (c == LiftPronunciation.class) out.add(MEDIA);
        if (c == LiftReversal.class) out.add(new Child("subreversals", "col.subreversals", LiftReversal.class,
            o -> ((LiftReversal) o).getReversals()));
        if (AbstractNotable.class.isAssignableFrom(c)) out.add(NOTES);
        if (AbstractExtensibleWithField.class.isAssignableFrom(c)) out.add(FIELDS);
        if (AbstractExtensibleWithoutField.class.isAssignableFrom(c)) out.addAll(List.of(TRAITS, ANNOTATIONS));
        if (c == LiftTrait.class) out.add(ANNOTATIONS);
        return out;
    }

    /** The nested counts offered for a kind: the subcomponents of a type at any depth. */
    public static List<Child> nested(Class<?> c) {
        if (c == LiftEntry.class) return List.of(
            new Child("senses.all", "nav.senses", LiftSense.class, o -> descendants(o, LiftSense.class)),
            new Child("examples.all", "nav.examples", LiftExample.class, o -> descendants(o, LiftExample.class)),
            new Child("translations.all", "nav.translations", LiftTranslation.class, o -> descendants(o, LiftTranslation.class)),
            new Child("illustrations.all", "nav.illustrations", LiftIllustration.class, o -> descendants(o, LiftIllustration.class)),
            new Child("reversals.all", "nav.reversals", LiftReversal.class, o -> descendants(o, LiftReversal.class)),
            new Child("media.all", "nav.media", LiftMedia.class, o -> descendants(o, LiftMedia.class)));
        if (c == LiftSense.class) return List.of(
            new Child("senses.all", "col.subsenses", LiftSense.class, o -> descendants(o, LiftSense.class)),
            new Child("examples.all", "nav.examples", LiftExample.class, o -> descendants(o, LiftExample.class)),
            new Child("translations.all", "nav.translations", LiftTranslation.class, o -> descendants(o, LiftTranslation.class)));
        if (c == LiftVariant.class) return List.of(
            new Child("media.all", "nav.media", LiftMedia.class, o -> descendants(o, LiftMedia.class)));
        return List.of();
    }

    /** The subcomponents of {@code root} of class {@code type}, at any depth ({@code root} excluded). */
    public static List<Object> descendants(Object root, Class<?> type) {
        List<Object> out = new ArrayList<>();
        Deque<Object> todo = new ArrayDeque<>();
        todo.push(root);
        while (!todo.isEmpty()) {
            Object o = todo.pop();
            for (Child c : children(o.getClass())) {
                // Traits, annotations, notes and fields hold no sense, example, media…
                if (c == TRAITS || c == ANNOTATIONS || c == NOTES || c == FIELDS) {
                    if (!type.isAssignableFrom(c.type())) continue;
                }
                for (Object x : c.list().apply(o)) {
                    if (type.isInstance(x)) out.add(x);
                    todo.push(x);
                }
            }
        }
        return out;
    }

    /**
     * The kinds (of {@link #KINDS}) that can be an ancestor of a component of class {@code c},
     * nearest first.
     */
    public static List<Class<?>> ancestorKinds(Class<?> c) {
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Class<?>> todo = new ArrayDeque<>(List.of(c));
        Set<Class<?>> all = new LinkedHashSet<>(KINDS);
        all.add(fr.cnrs.lacito.liftapi.model.GrammaticalInfo.class);
        while (!todo.isEmpty()) {
            Class<?> x = todo.poll();
            for (Class<?> p : all) {
                if (seen.contains(p) || p == c) continue;
                boolean holds = children(p).stream().anyMatch(ch -> ch.type().isAssignableFrom(x));
                if (p == fr.cnrs.lacito.liftapi.model.GrammaticalInfo.class) holds = x == LiftTrait.class;
                if (holds) {
                    seen.add(p);
                    todo.add(p);
                }
            }
        }
        if (seen.contains(fr.cnrs.lacito.liftapi.model.GrammaticalInfo.class)) seen.add(LiftSense.class);
        seen.remove(fr.cnrs.lacito.liftapi.model.GrammaticalInfo.class);
        seen.remove(c);
        return List.copyOf(seen);
    }

    /** The nearest ancestor of {@code row} of class {@code type} (not the row itself), or null. */
    public static <A> A nearest(Object row, Class<A> type) {
        if (!(row instanceof AbstractLiftRoot r)) return null;
        for (AbstractLiftRoot n = r.getParentNode(); n != null; n = n.getParentNode()) {
            if (type.isInstance(n)) return type.cast(n);
        }
        return null;
    }

    /** The table key ("entry", "sense"…) of a kind. */
    public static String kindId(Class<?> c) {
        return Map.ofEntries(
            Map.entry(LiftEntry.class, "entry"), Map.entry(LiftSense.class, "sense"),
            Map.entry(LiftExample.class, "example"), Map.entry(LiftTranslation.class, "translation"),
            Map.entry(LiftNote.class, "note"), Map.entry(LiftVariant.class, "variant"),
            Map.entry(LiftPronunciation.class, "pronunciation"), Map.entry(LiftMedia.class, "media"),
            Map.entry(LiftEtymology.class, "etymology"), Map.entry(LiftRelation.class, "relation"),
            Map.entry(LiftReversal.class, "reversal"), Map.entry(LiftIllustration.class, "illustration"),
            Map.entry(LiftField.class, "field"), Map.entry(LiftTrait.class, "trait"),
            Map.entry(LiftAnnotation.class, "annotation")).getOrDefault(c, c.getSimpleName());
    }
}
