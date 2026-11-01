package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * A list of components of one kind, held by one component (its owner): the title bar shows
 * their number and a [+] button adding one, and each component is an {@link ItemCard}
 * with its delete button.
 *
 * The [+] does not fold or unfold the pane; after the addition the pane opens, and the new
 * component gets the focus. When the kind needs a type, the [+] is a menu of the types.
 */
public final class ComponentSection extends SectionPane {

    /** Whether the sections without any component are hidden (View ▸ Hide Empty Sections). */
    public static final BooleanProperty HIDE_EMPTY = new SimpleBooleanProperty(false);

    private final ComponentKind kind;
    private final VBox itemsBox = new VBox(6);
    private final StackPane addSlot = new StackPane();
    /** Shows the section while it is hidden, for an addition started from elsewhere. */
    private final BooleanProperty forcedShown = new SimpleBooleanProperty(false);
    private Object owner;
    private EditActions actions;
    private Runnable refresh;
    private ButtonBase addButton;
    private int maxCount = Integer.MAX_VALUE;

    public ComponentSection(ComponentKind kind, String title, boolean expanded) {
        super(title, null, expanded);
        this.kind = kind;
        setContent(itemsBox);
        getStyleClass().add("component-section");
        setId("section-" + kind.id());
        addSlot.getStyleClass().add("section-add-slot");
        header.getChildren().add(addSlot);

        visibleProperty().bind(Bindings.createBooleanBinding(
            () -> !HIDE_EMPTY.get() || getCount() > 0 || forcedShown.get(),
            HIDE_EMPTY, countProperty(), forcedShown));
        managedProperty().bind(visibleProperty());

        header.setOnContextMenuRequested(this::showContextMenu);
        // The keyboard's context-menu key, while the pane itself has the focus.
        setOnContextMenuRequested(e -> {
            if (e.isKeyboardTrigger() && e.getTarget() == this) showContextMenu(e);
        });
    }

    public ComponentKind kind() {
        return kind;
    }

    /** The component holding the components listed; null before {@link #setOwner}. */
    public Object owner() {
        return owner;
    }

    /**
     * Sets the component whose components are listed. With {@code actions}, the [+] adds one
     * and each card can be deleted; without, the section is read-only.
     */
    public void setOwner(Object owner, EditActions actions) {
        setOwner(owner, actions, actions == null ? null : actions::refresh);
    }

    /** Same, rebuilding with {@code refresh} instead of the actions' refresh. */
    public void setOwner(Object owner, EditActions actions, Runnable refresh) {
        this.owner = owner;
        this.actions = owner == null ? null : actions;
        this.refresh = refresh == null ? () -> {} : refresh;
        itemsBox.getChildren().clear();
        setCount(0);
        rebuildAddButton();
    }

    /** At most this many components (a reversal has one main reversal at most). */
    public void setMaxCount(int max) {
        this.maxCount = max;
        rebuildAddButton();
    }

    /** The cards of the components, in order. */
    public void setItems(List<? extends Node> cards) {
        itemsBox.getChildren().setAll(cards);
        setCount(cards.size());
        rebuildAddButton();
    }

    public List<ItemCard> cards() {
        return itemsBox.getChildren().stream().filter(ItemCard.class::isInstance).map(ItemCard.class::cast).toList();
    }

    /**
     * A card for a component of this section; with edit actions, its delete button runs
     * {@code remove} (typically {@code owner.deleteX(x)}), asking first for a main
     * component that is not empty.
     */
    public ItemCard card(Object component, String title, Node editor, Runnable remove) {
        ItemCard card = new ItemCard(component, title, editor);
        if (actions != null && remove != null) card.setOnDelete(() -> delete(card, remove), kind.deleteTooltip());
        return card;
    }

    private void delete(ItemCard card, Runnable remove) {
        Scene scene = getScene();
        List<ItemCard> cards = cards();
        int i = cards.indexOf(card);
        Object next = i + 1 < cards.size() ? cards.get(i + 1).component()
            : i > 0 ? cards.get(i - 1).component() : null;
        Object sectionOwner = owner;
        if (ComponentDeletion.delete(card.component(), remove, refresh)) {
            Reveal.afterDeletion(scene, next, sectionOwner, kind);
        }
    }

    /* ─── Adding ─── */

    /** Whether the [+] can add a component now. */
    public boolean canAdd() {
        return addButton != null && !addButton.isDisabled();
    }

    /** The [+] button (a menu button when a type is chosen), or null for a read-only section. */
    public ButtonBase addButton() {
        return addButton;
    }

    private void rebuildAddButton() {
        addSlot.getChildren().clear();
        if (addSlot.getProperties().remove("tooltip") instanceof Tooltip old) Tooltip.uninstall(addSlot, old);
        addButton = null;
        if (actions == null) return;
        if (kind.input() == ComponentKind.Input.TYPE) {
            MenuButton mb = new MenuButton("+");
            // The items are made when the menu opens: the types used may have changed.
            mb.setOnShowing(e -> mb.getItems().setAll(typeItems()));
            mb.setOnHidden(e -> forcedShown.set(false));
            mb.getItems().add(new MenuItem());
            addButton = mb;
        } else {
            Button b = new Button("+");
            b.setOnAction(e -> addNow());
            addButton = b;
        }
        addButton.getStyleClass().add("section-add");
        addButton.setId("add-" + kind.id());
        addButton.setTooltip(new Tooltip(kind.addTooltip()));
        addButton.setAccessibleText(kind.addTooltip());
        // Clicking the [+] must not fold or unfold the pane.
        addButton.addEventHandler(MouseEvent.ANY, Event::consume);
        String disabledTip = disabledReason();
        if (disabledTip != null) {
            addButton.setDisable(true);
            Tooltip tip = new Tooltip(disabledTip);
            addSlot.getProperties().put("tooltip", tip);
            Tooltip.install(addSlot, tip);
        }
        addSlot.getChildren().add(addButton);
    }

    /** Why nothing can be added, or null. */
    private String disabledReason() {
        if (getCount() >= maxCount) return I18n.get("tooltip.maxReached");
        if (kind.input() == ComponentKind.Input.TYPE) {
            TypeChoices choices = actions.choices(kind, owner);
            if (choices.isEmpty()) return I18n.get(choices.noTypeTooltipKey() == null ? "tooltip.noType" : choices.noTypeTooltipKey());
        }
        return null;
    }

    private List<MenuItem> typeItems() {
        return actions.choices(kind, owner).menuItems(kind.addTooltip(), this::create);
    }

    /** Adds a component of a kind without a type: at once, or after choosing the file. */
    private void addNow() {
        if (kind.input() == ComponentKind.Input.FILE) {
            chooseFile().ifPresent(this::create);
        } else {
            create(null);
        }
    }

    private Optional<String> chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(kind.addTooltip());
        File base = actions.dictionaryFolder();
        if (base != null) chooser.setInitialDirectory(base);
        File chosen = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        return chosen == null ? Optional.empty() : Optional.of(ExternalDocumentEditor.pathFor(chosen, base));
    }

    /** Creates the component, rebuilds the editor, and puts the focus in the new component. */
    private void create(String choice) {
        Scene scene = getScene();
        Object created;
        try {
            created = actions.add(kind, owner, choice);
        } catch (RuntimeException e) {
            Dialogs.error(kind.addTooltip(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return;
        } finally {
            forcedShown.set(false);
        }
        setExpanded(true);
        refresh.run();
        Reveal.created(scene, created);
    }

    /**
     * Adds a component as the [+] would, for the Add menu and the shortcuts: shows the
     * section (and what contains it), then adds the component, or opens the menu of the
     * types under the [+].
     */
    public void fireAdd() {
        if (!canAdd()) return;
        forcedShown.set(true);
        Reveal.expandAncestors(this);
        setExpanded(true);
        if (addButton instanceof MenuButton mb) {
            Platform.runLater(() -> {
                Reveal.scrollTo(this);
                mb.show();
            });
        } else {
            forcedShown.set(false);
            addNow();
        }
    }

    /** "Add a note": one item, or a submenu of the types. */
    public MenuItem addMenuItem() {
        String text = kind.addTooltip();
        if (kind.input() == ComponentKind.Input.TYPE) {
            Menu menu = new Menu(text);
            menu.getItems().add(new MenuItem());
            menu.setOnShowing(e -> menu.getItems().setAll(actions.choices(kind, owner).menuItems(text, this::create)));
            menu.setDisable(!canAdd());
            return menu;
        }
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> addNow());
        item.setDisable(!canAdd());
        return item;
    }

    private void showContextMenu(javafx.scene.input.ContextMenuEvent e) {
        e.consume();
        if (!canAdd()) return;
        ContextMenu menu = new ContextMenu(addMenuItem());
        menu.show(header, e.getScreenX(), e.getScreenY());
    }

    /* ─── Lookups ─── */

    /** The sections under {@code root} listing the components of {@code owner}. */
    public static List<ComponentSection> sectionsOf(Node root, Object owner) {
        List<ComponentSection> found = new ArrayList<>();
        collect(root, owner, found);
        return found;
    }

    private static void collect(Node n, Object owner, List<ComponentSection> acc) {
        if (n instanceof ComponentSection s && (owner == null || s.owner() == owner)) acc.add(s);
        if (n instanceof javafx.scene.control.TitledPane tp && tp.getContent() != null) {
            collect(tp.getContent(), owner, acc);
        } else if (n instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) {
            collect(sp.getContent(), owner, acc);
        } else if (n instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) collect(c, owner, acc);
        }
    }

    /** The section listing the components of {@code kind} of {@code owner} under {@code root}, or null. */
    public static ComponentSection find(Node root, Object owner, ComponentKind kind) {
        return sectionsOf(root, owner).stream().filter(s -> s.kind() == kind).findFirst().orElse(null);
    }

    /** The innermost section around a node, or null. */
    public static ComponentSection around(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n instanceof ComponentSection s) return s;
        }
        return null;
    }
}
