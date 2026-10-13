package fr.cnrs.lacito.liftgui.ui.app;

import fr.cnrs.lacito.liftgui.ui.I18n;
import java.io.File;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;

/** The preferences dialog: interface language, font size, theme and default directory. */
public final class PreferencesDialog {

    private PreferencesDialog() {}

    /**
     * Shows the dialog and applies the choices on OK.
     *
     * @param scene            the main scene, to which font and theme are applied
     * @param onLocaleChanged  called after a change of interface language (the caller
     *                         typically reloads the main scene)
     */
    public static void show(AppPreferences prefs, Scene scene, Runnable onLocaleChanged) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("prefs.title"));
        dlg.setHeaderText(I18n.get("prefs.title"));
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefSize(480, 380);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> langCombo = new ComboBox<>(FXCollections.observableArrayList("Français", "English"));
        langCombo.setValue("fr".equals(I18n.getLocale().getLanguage()) ? "Français" : "English");

        Slider fontSlider = new Slider(10, 20, prefs.fontSize());
        fontSlider.setShowTickLabels(true);
        fontSlider.setShowTickMarks(true);
        fontSlider.setMajorTickUnit(2);
        fontSlider.setBlockIncrement(1);
        fontSlider.setSnapToTicks(true);
        Label fontValueLabel = new Label((int) fontSlider.getValue() + "px");
        fontSlider.valueProperty().addListener((obs, o, n) -> fontValueLabel.setText((int) n.doubleValue() + "px"));

        ToggleGroup themeGroup = new ToggleGroup();
        RadioButton lightBtn = new RadioButton(I18n.get("prefs.themeLight"));
        lightBtn.setToggleGroup(themeGroup);
        RadioButton darkBtn = new RadioButton(I18n.get("prefs.themeDark"));
        darkBtn.setToggleGroup(themeGroup);
        (prefs.darkTheme() ? darkBtn : lightBtn).setSelected(true);

        TextField pathField = new TextField(prefs.defaultPath());
        pathField.setPromptText(System.getProperty("user.home"));
        Button browseBtn = new Button(I18n.get("prefs.browse"));
        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle(I18n.get("prefs.defaultPath"));
            File dir = dc.showDialog(dlg.getDialogPane().getScene().getWindow());
            if (dir != null) pathField.setText(dir.getAbsolutePath());
        });
        HBox pathBox = new HBox(8, pathField, browseBtn);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        HBox fontBox = new HBox(8, fontSlider, fontValueLabel);
        HBox.setHgrow(fontSlider, Priority.ALWAYS);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label(I18n.get("prefs.language")), langCombo);
        grid.addRow(1, new Label(I18n.get("prefs.fontSize")), fontBox);
        grid.addRow(2, new Label(I18n.get("prefs.theme")), new HBox(12, lightBtn, darkBtn));
        grid.addRow(3, new Label(I18n.get("prefs.defaultPath")), pathBox);
        GridPane.setHgrow(langCombo, Priority.ALWAYS);
        GridPane.setHgrow(fontBox, Priority.ALWAYS);
        GridPane.setHgrow(pathBox, Priority.ALWAYS);
        dlg.getDialogPane().setContent(grid);

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            prefs.setFontSize((int) fontSlider.getValue());
            prefs.setDarkTheme(darkBtn.isSelected());
            prefs.setDefaultPath(pathField.getText().trim());
            prefs.applyAppearance(scene);
            Locale newLocale = "Français".equals(langCombo.getValue()) ? Locale.FRENCH : Locale.ENGLISH;
            if (!newLocale.getLanguage().equals(I18n.getLocale().getLanguage())) {
                I18n.setLocale(newLocale);
                onLocaleChanged.run();
            }
            return null;
        });
        dlg.showAndWait();
    }
}
