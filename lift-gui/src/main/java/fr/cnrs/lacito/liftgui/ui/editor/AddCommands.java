package fr.cnrs.lacito.liftgui.ui.editor;

import fr.cnrs.lacito.liftapi.model.AbstractLiftRoot;
import fr.cnrs.lacito.liftfx.ComponentKind;
import fr.cnrs.lacito.liftfx.internal.ComponentSection;
import fr.cnrs.lacito.liftfx.internal.ItemCard;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

/**
 * The Add menu: adds a component of a kind to the component open in the editor panel, or
 * to the one around the keyboard focus, as the [+] of the matching section would.
 *
 * The target of an item is, in this order:
 * <ol>
 * <li>the component around the focus, going up the cards, that has a section of this kind
 * (with the focus in sense #2's gloss, Example adds to sense #2);</li>
 * <li>the component open in the editor panel;</li>
 * <li>the only component one level below it that has such a section (Example, with an
 * entry open, adds to its sense when it has exactly one).</li>
 * </ol>
 * An item without a target is disabled; when there are several candidates, its text says
 * to put the cursor in one first. "In this section…" adds to the innermost section around
 * the focus.
 */
public final class AddCommands {

    /** The kinds of the menu, in order; null marks a separator. */
    private static final ComponentKind[] ORDER = {
        ComponentKind.SENSE, ComponentKind.SUB_SENSE, ComponentKind.EXAMPLE, ComponentKind.TRANSLATION,
        ComponentKind.PRONUNCIATION, ComponentKind.VARIANT, ComponentKind.RELATION, ComponentKind.ETYMOLOGY,
        ComponentKind.REVERSAL, ComponentKind.ILLUSTRATION, ComponentKind.MEDIA, null,
        ComponentKind.NOTE, ComponentKind.FIELD, ComponentKind.TRAIT, ComponentKind.ANNOTATION, null,
    };

    private final EditorContext ctx;
    private final Node editorRoot;
    private final Map<ComponentKind, MenuItem> items = new EnumMap<>(ComponentKind.class);
    private MenuItem inSection;

    /**
     * @param editorRoot the node holding the editor panel's content
     */
    public AddCommands(EditorContext ctx, Node editorRoot) {
        this.ctx = ctx;
        this.editorRoot = editorRoot;
    }

    /** Fills the Add menu, and keeps its items enabled or not as the editor and the focus change. */
    public void install(Menu menu) {
        for (ComponentKind kind : ORDER) {
            if (kind == null) {
                menu.getItems().add(new SeparatorMenuItem());
                continue;
            }
            MenuItem item = new MenuItem(kind.label());
            item.setId("add." + kind.id());
            item.setOnAction(e -> add(kind));
            items.put(kind, item);
            menu.getItems().add(item);
        }
        inSection = new MenuItem(I18n.get("menu.add.inSection"));
        inSection.setId("add.inSection");
        inSection.setOnAction(e -> addInSection());
        menu.getItems().add(inSection);

        menu.setOnShowing(e -> refresh());
        ctx.panel().revisionProperty().addListener((obs, o, n) -> Platform.runLater(this::refresh));
        editorRoot.sceneProperty().addListener((obs, o, scene) -> {
            if (scene != null) scene.focusOwnerProperty().addListener((o2, a, b) -> refresh());
        });
        refresh();
    }

    /** Enables the items that have a target. */
    public void refresh() {
        for (Map.Entry<ComponentKind, MenuItem> e : items.entrySet()) {
            Target t = target(e.getKey());
            e.getValue().setDisable(t.section() == null || !t.section().canAdd());
            e.getValue().setText(t.ambiguous() ? I18n.get("menu.add.chooseFirst", e.getKey().label()) : e.getKey().label());
        }
        ComponentSection around = sectionAroundFocus();
        inSection.setDisable(around == null || !around.canAdd());
    }

    /** Adds a component of {@code kind} to its target, as its [+] would. */
    public void add(ComponentKind kind) {
        Target t = target(kind);
        if (t.section() != null) t.section().fireAdd();
    }

    /** Adds to the innermost section around the focus. */
    public void addInSection() {
        ComponentSection s = sectionAroundFocus();
        if (s != null) s.fireAdd();
    }

    /**
     * The section that an item of the Add menu adds to.
     *
     * @param section   the section, or null when there is none
     * @param ambiguous whether several components could take it (the cursor decides)
     */
    record Target(ComponentSection section, boolean ambiguous) {}

    Target target(ComponentKind kind) {
        // 1. The component around the focus.
        Node focus = focusInEditor();
        for (Node n = focus; n != null && n != editorRoot; n = n.getParent()) {
            if (n instanceof ItemCard card) {
                ComponentSection s = section(card.content(), card.component(), kind);
                if (s != null) return new Target(s, false);
            }
        }
        // 2. The component open in the editor.
        Object shown = ctx.panel().shown();
        if (shown == null) return new Target(null, false);
        ComponentSection s = section(editorRoot, shown, kind);
        if (s != null) return new Target(s, false);
        // 3. The only one below it.
        List<ComponentSection> below = ComponentSection.sectionsOf(editorRoot, null).stream()
            .filter(x -> x.kind() == kind && x.owner() instanceof AbstractLiftRoot r && r.getParentNode() == shown)
            .toList();
        if (below.size() == 1) return new Target(below.getFirst(), false);
        return new Target(null, below.size() > 1);
    }

    private static ComponentSection section(Node root, Object owner, ComponentKind kind) {
        return ComponentSection.find(root, owner, kind);
    }

    private Node focusInEditor() {
        Scene scene = editorRoot.getScene();
        Node focus = scene == null ? null : scene.getFocusOwner();
        for (Node n = focus; n != null; n = n.getParent()) {
            if (n == editorRoot) return focus;
        }
        return null;
    }

    private ComponentSection sectionAroundFocus() {
        return ComponentSection.around(focusInEditor());
    }
}
