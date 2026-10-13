package fr.cnrs.lacito.liftgui.ui.view.header;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftVersion;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.view.DictionaryView;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** The description of the dictionary, with its LIFT version and producer. */
public final class HeaderDescriptionView implements DictionaryView {

    private final VBox box = new VBox(10);

    public HeaderDescriptionView(EditorContext ctx) {
        LiftDictionary d = ctx.dictionary();
        box.setPadding(new Insets(12));
        box.getChildren().add(HeaderViews.pageTitle(I18n.get("nav.cfgDesc")));
        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(12);
        infoGrid.setVgap(6);
        int row = 0;
        LiftVersion version = d.getLiftVersion();
        if (version != null) addInfo(infoGrid, row++, I18n.get("cfg.liftVersion"), version.toString());
        String producer = d.getLiftProducer();
        if (producer != null && !producer.isBlank()) addInfo(infoGrid, row, I18n.get("cfg.producer"), producer);
        box.getChildren().add(infoGrid);
        HeaderViews.addMultiTextRow(box, d, I18n.get("cfg.description"), d.getHeader().getDescription());
        ctx.panel().show(I18n.get("nav.cfgDesc"), "", null);
    }

    private static void addInfo(GridPane grid, int row, String label, String value) {
        Label l = new Label(label);
        l.setStyle("-fx-font-weight:bold;");
        TextField tf = new TextField(value);
        tf.setEditable(false);
        tf.setPrefWidth(200);
        tf.setStyle("-fx-background-color: #eee;");
        grid.addRow(row, l, tf);
    }

    @Override
    public Node node() {
        return box;
    }
}
