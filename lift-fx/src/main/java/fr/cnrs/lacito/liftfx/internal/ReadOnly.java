package fr.cnrs.lacito.liftfx.internal;

import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;

/**
 * Makes a tree of editor nodes read-only, including the nodes added to it later (a
 * section expanded, a card built on demand): texts stay selectable and copyable but cannot
 * be changed; the controls that would change a value (choices, check boxes, spinners,
 * buttons) are disabled; links, which only navigate, and the controls marked with
 * {@link #keepEnabled(Node)} (folding a card), which only change the view, are left as they are.
 */
public final class ReadOnly {

    /** On the root of a read-only editor, for style sheets. */
    public static final PseudoClass READ_ONLY = PseudoClass.getPseudoClass("read-only");

    private static final Object INSTALLED = new Object();

    /** The key, in {@link Node#getProperties()}, of the controls read-only mode leaves enabled. */
    private static final Object VIEW_ONLY = new Object();

    private ReadOnly() {
    }

    /**
     * Leaves {@code node} enabled in a read-only editor: for a control that changes only the
     * view, not the component (the arrow that folds a card). Returns {@code node}.
     */
    public static <N extends Node> N keepEnabled(N node) {
        node.getProperties().put(VIEW_ONLY, Boolean.TRUE);
        return node;
    }

    /** Applies to {@code node} and everything under it, now and when nodes are added. */
    public static void install(Node node) {
        apply(node);
        if (node instanceof Parent parent && parent.getProperties().put(INSTALLED, INSTALLED) == null) {
            parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
                while (change.next()) {
                    for (Node added : change.getAddedSubList()) {
                        install(added);
                    }
                }
            });
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                install(child);
            }
        }
    }

    private static void apply(Node node) {
        if (node.getProperties().containsKey(VIEW_ONLY)) return;
        switch (node) {
            case TextInputControl text -> text.setEditable(false);
            case Hyperlink link -> {
                // navigation only
            }
            case ButtonBase button -> button.setDisable(true);
            case ComboBoxBase<?> combo -> combo.setDisable(true);
            case ChoiceBox<?> choice -> choice.setDisable(true);
            case Spinner<?> spinner -> spinner.setDisable(true);
            case Slider slider -> slider.setDisable(true);
            default -> {
            }
        }
    }
}
