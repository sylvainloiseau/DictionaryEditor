package fr.cnrs.lacito.liftgui.ui.table.widgets;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.Skinnable;

/**
 * A small window anchored under a filter widget, closed by Esc or a click outside. Being a
 * {@link PopupControl}, it takes the styles of the scene of its owner (app.css); the inline
 * style of the scene root (font size, dark theme) is copied to its content.
 */
public final class FilterPopover extends PopupControl {

    private final Parent content;

    public FilterPopover(Parent content) {
        this.content = content;
        content.getStyleClass().add("filter-popover");
        setAutoHide(true);
        setHideOnEscape(true);
        setAutoFix(true);
        // A click outside closes the popover and still does what it was meant to (remove a chip…).
        setConsumeAutoHidingEvents(false);
        setSkin(new Skin<Skinnable>() {
            @Override public Skinnable getSkinnable() { return FilterPopover.this; }
            @Override public Node getNode() { return content; }
            @Override public void dispose() {}
        });
    }

    /** Shows the popover under {@code owner}. */
    public void showBelow(Node owner) {
        if (owner.getScene() != null && owner.getScene().getRoot() != null) {
            content.setStyle(owner.getScene().getRoot().getStyle());
        }
        Bounds b = owner.localToScreen(owner.getBoundsInLocal());
        if (b == null) return;
        show(owner, b.getMinX(), b.getMaxY() + 2);
    }
}
