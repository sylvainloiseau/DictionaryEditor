package fr.cnrs.lacito.liftgui.core;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftDictionaryRegistry;
import fr.cnrs.lacito.liftapi.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks a dictionary for common problems: entries without form, senses without gloss or
 * definition, references to unknown entries or senses, fields and traits whose name is not
 * declared in the header, trait values outside their range, types not declared in the
 * header, ranges missing from the shared file their header refers to, values a shared
 * range does not declare, and empty forms.
 */
public final class DictionaryValidator {

    /**
     * One problem found.
     *
     * @param key   message key of the kind of problem (e.g. {@code validate.emptyForm})
     * @param where description of the component concerned, see {@link #describe(Object)}
     */
    public record Issue(String key, String where) {}

    private DictionaryValidator() {}

    public static List<Issue> validate(LiftDictionary dictionary) {
        LiftDictionaryRegistry reg = dictionary.getLiftDictionaryRegistry();
        List<Issue> issues = new ArrayList<>();
        for (LiftEntry e : reg.getEntries()) {
            if (e.getForms().isEmpty()) issues.add(new Issue("validate.entryWithoutForm", describe(e)));
        }
        for (LiftSense sn : reg.getSenses()) {
            if (sn.getGlosses().isEmpty() && sn.getDefinition().isEmpty()) {
                issues.add(new Issue("validate.senseWithoutGloss", describe(sn)));
            }
        }
        for (LiftRelation r : reg.getRelations()) {
            if (r.getRefId().filter(id -> !id.isBlank()).isPresent() && r.getRefObject() == null) {
                issues.add(new Issue("validate.danglingReference", describe(r) + " → " + r.getRefId().get()));
            }
        }
        for (LiftVariant v : reg.getVariants()) {
            if (v.getRefId().filter(id -> !id.isBlank()).isPresent() && v.getRefObject() == null) {
                issues.add(new Issue("validate.danglingReference", describe(v) + " → " + v.getRefId().get()));
            }
        }
        // A name declared nowhere: neither a definition nor, for a trait, a range.
        for (LiftField f : reg.getFields()) {
            if (f.getSpecification() instanceof UndeclaredName) {
                issues.add(new Issue("validate.undeclaredField", describe(f) + " : " + f.getName()));
            }
        }
        for (LiftTrait t : reg.getTraits()) {
            TraitSpecification spec = t.getSpecification();
            if (spec instanceof UndeclaredName) {
                issues.add(new Issue("validate.undeclaredTrait", describe(t) + " : " + spec.getName()));
            }
            spec.getResolvedFeatureSet().ifPresent(set -> {
                for (String token : DictionaryQueries.traitValueTokens(t)) {
                    if (!set.hasFeature(token)) {
                        issues.add(new Issue("validate.traitValueOutOfRange",
                            describe(t) + " : " + spec.getName() + " = " + token));
                    }
                }
            });
        }
        // The ranges defined in a shared .lift-ranges file: a range the file lacks, and the
        // values used that it does not declare (the dictionary holds them as bare features).
        for (FeatureSet set : dictionary.getHeader().getFeatureSets()) {
            if (!set.isExternallyDefined()) continue;
            if (set.isExternalDefinitionMissing()) {
                issues.add(new Issue("validate.externalRangeMissing", set.getId() + " : " + set.getHref().orElse("")));
            }
            set.getFeatures().values().stream()
                .filter(Feature::isUndeclaredInTheExternalFile)
                .map(Feature::getId)
                .sorted()
                .forEach(id -> issues.add(new Issue("validate.valueNotInExternalRange", set.getId() + " = " + id)));
        }
        List<HasType> typed = new ArrayList<>();
        typed.addAll(reg.getNotes());
        typed.addAll(reg.getTranslations());
        typed.addAll(reg.getAnnotations());
        typed.addAll(reg.getRelations());
        typed.addAll(reg.getEtymologies());
        typed.addAll(reg.getReversals());
        for (HasType x : typed) {
            Feature type = x.getType();
            if (type == null) continue;
            FeatureSet set = type.getParent();
            if (set == null || set.getFeature(type.getId()) != type) {
                issues.add(new Issue("validate.undeclaredType", describe(x) + " : " + type.getId()));
            }
        }
        List<MultiText> texts = new ArrayList<>(reg.getObjectText());
        texts.addAll(reg.getMetaText());
        for (MultiText mt : texts) {
            for (Form f : mt.getForms()) {
                if (f.toPlainText() == null || f.toPlainText().isBlank()) {
                    issues.add(new Issue("validate.emptyForm", describe(mt.getParent()) + " [" + f.getLang() + "]"));
                }
            }
        }
        return issues;
    }

    /** Short description of a component for a report: its kind and the form of its entry. */
    public static String describe(Object o) {
        String kind = o == null ? "" : o.getClass().getSimpleName().replaceFirst("^Lift", "");
        LiftEntry e = DictionaryQueries.owningEntry(o);
        if (e == null) return kind;
        String form = e.getForms().getForms().stream()
            .map(Form::toPlainText)
            .filter(t -> t != null && !t.isBlank())
            .findFirst()
            .orElse(e.getId().orElse("?"));
        return kind + " — " + form;
    }
}
