/**
 
* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui;

import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class MainApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        primaryStage.setTitle(I18n.get("app.title"));
        addAppIcons(primaryStage);

        // Afficher l'écran de chargement
        Parent loadingRoot = (Parent) FXMLLoader.load(
            MainApp.class.getResource("/fr/cnrs/lacito/liftgui/ui/LoadingView.fxml")
        );
        Scene loadingScene = new Scene(loadingRoot, 600, 400);
        loadingScene.getStylesheets().add(
            MainApp.class.getResource("/fr/cnrs/lacito/liftgui/ui/loading.css").toExternalForm()
        );
        primaryStage.setScene(loadingScene);
        primaryStage.setMinWidth(500);
        primaryStage.setMinHeight(350);
        primaryStage.setMaximized(true);
        primaryStage.centerOnScreen();
        primaryStage.show();

        // The loading screen stays up while the main view (and the dictionary) load.
        ProgressBar progressBar = (ProgressBar) loadingRoot.lookup("#progressBar");
        if (progressBar != null) progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        Platform.runLater(this::finishLoading);
    }

    /** The application icon, in the sizes the window systems pick from. */
    private static void addAppIcons(Stage stage) {
        for (int size : new int[] {16, 32, 48, 64, 128, 256, 512}) {
            var url = MainApp.class.getResource("/fr/cnrs/lacito/liftgui/ui/icons/app/icon-" + size + ".png");
            if (url != null) stage.getIcons().add(new javafx.scene.image.Image(url.toExternalForm()));
        }
    }

    private void finishLoading() {
        try {
            loadScene();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Erreur au chargement de l'application", ex);
        }
    }

    public static void reloadScene() {
        try { loadScene(); }
        catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur au rechargement de la scène", e);
        }
    }

    private static void loadScene() throws Exception {
        FXMLLoader loader = new FXMLLoader(
            MainApp.class.getResource("/fr/cnrs/lacito/liftgui/ui/MainView.fxml"),
            I18n.getBundle()
        );
        double w = primaryStage.getScene() != null ? primaryStage.getScene().getWidth() : 1200;
        double h = primaryStage.getScene() != null ? primaryStage.getScene().getHeight() : 720;
        Scene scene = new Scene(loader.load(), w, h);
        scene.getStylesheets().add(MainApp.class.getResource("/fr/cnrs/lacito/liftgui/ui/app.css").toExternalForm());
        // The controller of the new scene binds the title to its document.
        primaryStage.titleProperty().unbind();
        primaryStage.setScene(scene);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

