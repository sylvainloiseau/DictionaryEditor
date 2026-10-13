package fr.cnrs.lacito.liftgui.ui.nav;

import static fr.cnrs.lacito.liftgui.ui.nav.ViewKey.*;

import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftHeader;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/**
 * The navigation tree on the left: objects, languages, categories, dictionary
 * configuration (with one item per header range) and quick entry.
 */
public final class NavigationTree {

    private final TreeView<String> tree;
    /** The request of each leaf item. */
    private final Map<TreeItem<String>, ViewRequest> requests = new HashMap<>();
    private final TreeItem<String> rangesNode = new TreeItem<>(I18n.get(Keys.NAV_CFG_RANGES));
    private boolean ignoreSelection;

    /**
     * @param onSelect called when the user selects a view
     * @param onCreate called by the "create object" item of the context menu
     */
    public NavigationTree(TreeView<String> tree, Consumer<ViewRequest> onSelect, Runnable onCreate) {
        this.tree = tree;
        TreeItem<String> root = new TreeItem<>(I18n.get(Keys.NAV_DICTIONARY));
        root.setExpanded(true);
        root.getChildren().addAll(
            group(Keys.NAV_OBJECTS, ENTRIES, SENSES, EXAMPLES, NOTES, VARIANTS, ETYMOLOGIES, RELATIONS, FIELDS),
            group(Keys.NAV_LANGUAGES, OBJECT_LANGUAGES, META_LANGUAGES),
            group(Keys.NAV_CATEGORIES, GRAM_INFO, TRAITS, ANNOTATIONS, TRANSLATION_TYPES, NOTE_TYPES, RELATION_TYPES, FIELD_TYPES),
            group(Keys.NAV_HEADER_CONFIG, CFG_DESCRIPTION, CFG_FIELD_DEFINITIONS, CFG_LANGUAGES, CFG_NOTE_TYPES,
                CFG_TRANSLATION_TYPES, CFG_ANNOTATION_TYPES, CFG_RELATION_TYPES),
            item(QUICK_ENTRY)
        );
        root.getChildren().get(3).getChildren().add(rangesNode);
        tree.setRoot(root);
        tree.setShowRoot(false);

        tree.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (ignoreSelection || n == null || !n.isLeaf()) return;
            ViewRequest r = requests.get(n);
            if (r != null) onSelect.accept(r);
        });
        tree.setCellFactory(tv -> {
            TreeCell<String> cell = new TreeCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            MenuItem createItem = new MenuItem(I18n.get(Keys.BTN_CREATE_OBJECT));
            createItem.setOnAction(e -> onCreate.run());
            cell.setContextMenu(new ContextMenu(createItem));
            return cell;
        });
    }

    private TreeItem<String> group(String i18nKey, ViewKey... keys) {
        TreeItem<String> g = new TreeItem<>(I18n.get(i18nKey));
        g.setExpanded(true);
        for (ViewKey k : keys) g.getChildren().add(item(k));
        return g;
    }

    private TreeItem<String> item(ViewKey key) {
        TreeItem<String> item = new TreeItem<>(I18n.get(key.i18nKey()));
        requests.put(item, ViewRequest.of(key));
        return item;
    }

    /** Lists the ranges of a header (none when {@code header} is null), by label or id. */
    public void setRanges(LiftHeader header) {
        rangesNode.getChildren().forEach(requests::remove);
        rangesNode.getChildren().clear();
        if (header == null) return;
        for (FeatureSet range : header.getFeatureSets()) {
            String label = range.getLabel().getForms().stream().findFirst().map(Form::toPlainText).orElse(range.getId());
            TreeItem<String> item = new TreeItem<>(label);
            requests.put(item, ViewRequest.range(range.getId()));
            rangesNode.getChildren().add(item);
        }
    }

    /** Highlights the item of a view without triggering a view change. */
    public void select(ViewRequest request) {
        TreeItem<String> item = requests.entrySet().stream()
            .filter(e -> e.getValue().key() == request.key() && Objects.equals(e.getValue().rangeId(), request.rangeId()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
        if (item == null) return;
        ignoreSelection = true;
        try {
            tree.getSelectionModel().select(item);
        } finally {
            ignoreSelection = false;
        }
    }
}
