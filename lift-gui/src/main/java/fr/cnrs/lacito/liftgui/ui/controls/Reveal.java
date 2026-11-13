package fr.cnrs.lacito.liftgui.ui.controls;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.util.Duration;

/**
 * Shows a component of the editor to the user: opens what contains its card, scrolls to
 * it, and puts the focus in it. Used after an addition (the new component) and after a
 * deletion (the next one).
 */
public final class Reveal {

    /** The style class of the input that gets the focus in a new component (e.g. a sense's gloss). */
    public static final String FIRST_INPUT = "first-input";
    /** The style class of a card just created, for a short highlight. */
    static final String NEW_CARD = "item-card-new";

    private Reveal() {}

    /**
     * After an addition: once the editor is rebuilt, finds the card of {@code component},
     * opens it and what contains it, scrolls to it, focuses its first input and
     * highlights it briefly.
     */
    public static void created(Scene scene, Object component) {
        if (scene == null || component == null) return;
        Platform.runLater(() -> {
            ItemCard card = find(scene.getRoot(), component);
            if (card == null) return;
            card.setExpanded(true);
            expandAncestors(card);
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            scrollTo(card);
            focusFirstInput(card);
            card.getStyleClass().add(NEW_CARD);
            PauseTransition pause = new PauseTransition(Duration.seconds(1));
            pause.setOnFinished(e -> card.getStyleClass().remove(NEW_CARD));
            pause.play();
        });
    }

    /**
     * After a deletion: puts the focus on the header of {@code next} (the card that followed
     * the deleted one, else the one before), or on the [+] of the section when it is empty.
     */
    static void afterDeletion(Scene scene, Object next, Object owner, ComponentKind kind) {
        if (scene == null) return;
        Platform.runLater(() -> {
            ItemCard card = next == null ? null : find(scene.getRoot(), next);
            if (card != null) {
                card.header().requestFocus();
                return;
            }
            ComponentSection section = owner == null ? null : ComponentSection.find(scene.getRoot(), owner, kind);
            if (section != null && section.addButton() != null) section.addButton().requestFocus();
        });
    }

    /** The card of a component under {@code root}, or null. */
    public static ItemCard find(Node root, Object component) {
        if (root instanceof ItemCard c && c.component() == component) return c;
        if (root instanceof TitledPane tp && tp.getContent() != null) return find(tp.getContent(), component);
        if (root instanceof ScrollPane sp && sp.getContent() != null) return find(sp.getContent(), component);
        if (root instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                ItemCard found = find(child, component);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Opens the panes and cards containing a node. */
    public static void expandAncestors(Node node) {
        for (Node n = parentOf(node); n != null; n = parentOf(n)) {
            if (n instanceof TitledPane tp) tp.setExpanded(true);
            if (n instanceof ItemCard c) c.setExpanded(true);
        }
    }

    /**
     * The parent of a node; for the content of a folded section, whose skin may not hold it
     * yet, the section.
     */
    static Node parentOf(Node n) {
        Node p = n.getParent();
        if (p == null && n.getProperties().get(SectionPane.PANE) instanceof Node pane) return pane;
        return p;
    }

    /** Scrolls the scroll pane around a node, if needed, so that the node's top is in view. */
    public static void scrollTo(Node node) {
        ScrollPane sp = null;
        for (Node n = node.getParent(); n != null; n = n.getParent()) {
            if (n instanceof ScrollPane s) {
                sp = s;
                break;
            }
        }
        if (sp == null || sp.getContent() == null) return;
        Node content = sp.getContent();
        Bounds b = content.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
        double contentHeight = content.getBoundsInLocal().getHeight();
        double viewport = sp.getViewportBounds().getHeight();
        if (contentHeight <= viewport) return;
        double top = (sp.getVvalue() - sp.getVmin()) / (sp.getVmax() - sp.getVmin()) * (contentHeight - viewport);
        if (b.getMinY() >= top && b.getMinY() + Math.min(b.getHeight(), viewport) <= top + viewport) return;
        double y = Math.max(0, Math.min(b.getMinY() - 8, contentHeight - viewport));
        sp.setVvalue(sp.getVmin() + y / (contentHeight - viewport) * (sp.getVmax() - sp.getVmin()));
    }

    /**
     * Focuses the input marked {@link #FIRST_INPUT} in the card, else its first editable
     * input, else its header. Inputs of the cards nested in it are skipped.
     */
    static void focusFirstInput(ItemCard card) {
        Node marked = card.content().lookupAll("." + FIRST_INPUT).stream()
            .filter(n -> ItemCard.of(n) == card).findFirst().orElse(null);
        Node input = firstInput(marked != null ? marked : card.content(), card);
        (input != null ? input : card.header()).requestFocus();
    }

    /** The first editable input under {@code root}, in the order of the scene graph. */
    private static Node firstInput(Node root, ItemCard card) {
        if (root.isDisabled() || !root.isVisible()) return null;
        if (root instanceof TextInputControl t && t.isEditable() && ItemCard.of(t) == card) return t;
        if (root instanceof ComboBoxBase<?> c && ItemCard.of(c) == card) return c;
        if (root instanceof ItemCard other && other != card) return null;
        Iterable<Node> children = root instanceof TitledPane tp
            ? (tp.isExpanded() && tp.getContent() != null ? java.util.List.of(tp.getContent()) : java.util.List.of())
            : root instanceof Parent p ? p.getChildrenUnmodifiable() : java.util.List.of();
        for (Node child : children) {
            Node found = firstInput(child, card);
            if (found != null) return found;
        }
        return null;
    }
}
