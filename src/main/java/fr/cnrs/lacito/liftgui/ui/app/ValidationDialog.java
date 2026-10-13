package fr.cnrs.lacito.liftgui.ui.app;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftDictionaryRegistry;
import fr.cnrs.lacito.liftgui.core.DictionaryValidator;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;

/** Tools → Validate: the problems found by {@link DictionaryValidator}, grouped by kind. */
public final class ValidationDialog {

    private static final int MAX_PER_KIND = 200;

    private ValidationDialog() {}

    public static void show(LiftDictionary dictionary) {
        List<DictionaryValidator.Issue> issues = DictionaryValidator.validate(dictionary);
        Map<String, List<String>> byKind = new LinkedHashMap<>();
        for (DictionaryValidator.Issue i : issues) {
            byKind.computeIfAbsent(I18n.get(i.key()), k -> new ArrayList<>()).add(i.where());
        }
        StringBuilder report = new StringBuilder();
        for (var e : byKind.entrySet()) {
            report.append(e.getKey()).append(" (").append(e.getValue().size()).append(")\n");
            e.getValue().stream().limit(MAX_PER_KIND).forEach(w -> report.append("    ").append(w).append("\n"));
            if (e.getValue().size() > MAX_PER_KIND) report.append("    …\n");
            report.append("\n");
        }
        LiftDictionaryRegistry reg = dictionary.getLiftDictionaryRegistry();
        Alert a = new Alert(issues.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        a.setTitle(I18n.get("error.validation"));
        a.setHeaderText(
            I18n.get(
                "info.validationResult",
                reg.getEntries().size(),
                reg.getSenses().size(),
                reg.getExamples().size(),
                String.join(", ", dictionary.getObjectLanguageManager().getLanguages()),
                String.join(", ", dictionary.getMetaLanguageManager().getLanguages())
            ) + "\n" + (issues.isEmpty() ? I18n.get("validate.noIssue") : I18n.get("validate.issues", issues.size()))
        );
        if (!issues.isEmpty()) {
            TextArea area = new TextArea(report.toString());
            area.setEditable(false);
            area.setWrapText(false);
            area.setPrefSize(720, 420);
            a.getDialogPane().setContent(area);
            a.setResizable(true);
        }
        a.showAndWait();
    }
}
