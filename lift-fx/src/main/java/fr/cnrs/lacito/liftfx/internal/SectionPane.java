package fr.cnrs.lacito.liftfx.internal;

import fr.cnrs.lacito.liftfx.controls.ExtensibleWithoutFieldEditor;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * A collapsible pane whose title shows a count as a small badge: "Traits  2". The text of
 * the pane stays "Traits (2)", for screen readers and tests. {@link ComponentSection} adds
 * the [+] button; a properties pane shows the total of the components inside.
 */
public class SectionPane extends TitledPane {

    private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");
    /**
     * The key, in the content's properties, of the pane holding it: a folded pane may not
     * hold its content in its children yet (see {@link Reveal#parentOf}).
     */
    static final String PANE = "section-pane";
    /** The arrow and the paddings of the title bar, left of the graphic. */
    private static final double TITLE_INSETS = 40;

    private final String title;
    private final Label countLabel = new Label();
    private final IntegerProperty count = new SimpleIntegerProperty(this, "count", 0);
    /** The title bar's content: the title, the count, then what subclasses add on the right. */
    protected final HBox header;

    public SectionPane(String title, Node content, boolean expanded) {
        super(title, content);
        this.title = title;
        setExpanded(expanded);
        setAnimated(false);
        getStyleClass().add("section-pane");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-pane-title");
        countLabel.getStyleClass().add("section-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header = new HBox(6, titleLabel, countLabel, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("section-header");
        // The header spans the title bar, so that what is added at its end is on the right.
        header.minWidthProperty().bind(widthProperty().subtract(TITLE_INSETS));
        header.prefWidthProperty().bind(widthProperty().subtract(TITLE_INSETS));
        setGraphic(header);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        if (content != null) content.getProperties().put(PANE, this);
        contentProperty().addListener((obs, o, n) -> {
            if (o != null) o.getProperties().remove(PANE);
            if (n != null) n.getProperties().put(PANE, this);
        });
        count.addListener((obs, o, n) -> showCount(n.intValue()));
        showCount(0);
    }

    private void showCount(int n) {
        countLabel.setText(String.valueOf(n));
        countLabel.pseudoClassStateChanged(EMPTY, n == 0);
        setText(title + " (" + n + ")");
    }

    /** The title, without the count. */
    public String title() {
        return title;
    }

    public IntegerProperty countProperty() {
        return count;
    }

    public int getCount() {
        return count.get();
    }

    public void setCount(int n) {
        count.unbind();
        count.set(n);
    }

    /** A pane holding the properties of a component, its title showing their total. */
    public static SectionPane properties(String title, ExtensibleWithoutFieldEditor editor) {
        SectionPane pane = new SectionPane(title, editor, false);
        pane.getStyleClass().add("properties-pane");
        pane.countProperty().bind(editor.totalProperty());
        return pane;
    }
}
