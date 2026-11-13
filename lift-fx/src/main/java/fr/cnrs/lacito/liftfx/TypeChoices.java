package fr.cnrs.lacito.liftfx;

import fr.cnrs.lacito.liftfx.internal.Dialogs;
import fr.cnrs.lacito.liftfx.internal.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;

/**
 * The types offered when adding a component that needs one, as menu items: one per type,
 * those already used disabled (a note, a field, a translation: one per type), and
 * "Other…" for a type typed in freely when the list is open.
 *
 * @param types              the types, in order
 * @param used               the types that cannot be chosen again
 * @param open               whether a type outside the list may be typed in ("Other…")
 * @param noTypeTooltipKey   the tooltip (i18n key) of the disabled [+] when nothing can be chosen
 */
public record TypeChoices(List<String> types, Set<String> used, boolean open, String noTypeTooltipKey) {

    /** Beyond this number of types, the menu starts with a filter field. */
    static final int FILTER_THRESHOLD = 15;

    public TypeChoices {
        types = List.copyOf(types);
        used = Set.copyOf(used);
    }

    /** Types of an open list: any other type may be typed in. */
    public static TypeChoices open(List<String> types) {
        return new TypeChoices(types, Set.of(), true, null);
    }

    /** Whether nothing can be chosen: no free type and every type used (or none). */
    public boolean isEmpty() {
        return !open && types.stream().allMatch(used::contains);
    }

    /**
     * The menu items: the filter field (beyond {@value #FILTER_THRESHOLD} types), the types,
     * and "Other…".
     *
     * @param title    the title of the "Other…" dialog ("Add a note")
     * @param onChoose receives the chosen type, trimmed
     */
    public List<MenuItem> menuItems(String title, Consumer<String> onChoose) {
        List<MenuItem> items = new ArrayList<>();
        List<MenuItem> typeItems = new ArrayList<>();
        for (String t : types) {
            MenuItem mi = new MenuItem(t);
            mi.setMnemonicParsing(false);
            mi.setDisable(used.contains(t));
            mi.setOnAction(e -> onChoose.accept(t));
            typeItems.add(mi);
        }
        if (types.size() > FILTER_THRESHOLD) {
            TextField filter = new TextField();
            filter.setPromptText(Messages.get("menu.filterTypes"));
            filter.getStyleClass().add("type-filter");
            filter.textProperty().addListener((obs, o, n) -> {
                String f = n == null ? "" : n.trim().toLowerCase(Locale.ROOT);
                for (MenuItem mi : typeItems) mi.setVisible(mi.getText().toLowerCase(Locale.ROOT).contains(f));
            });
            CustomMenuItem filterItem = new CustomMenuItem(filter, false);
            items.add(filterItem);
            items.add(new SeparatorMenuItem());
            // The field gets the focus once the menu is shown.
            filter.sceneProperty().addListener((obs, o, s) -> {
                if (s != null) Platform.runLater(filter::requestFocus);
            });
        }
        items.addAll(typeItems);
        if (open) {
            if (!types.isEmpty()) items.add(new SeparatorMenuItem());
            MenuItem other = new MenuItem(Messages.get("menu.otherType"));
            other.getStyleClass().add("other-type");
            other.setOnAction(e -> askType(title).ifPresent(onChoose));
            items.add(other);
        }
        return items;
    }

    /** Asks for a type typed in freely. */
    static Optional<String> askType(String title) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle(title);
        dlg.setHeaderText(Messages.get("col.type"));
        return Dialogs.owned(dlg).showAndWait().map(String::trim).filter(t -> !t.isEmpty());
    }
}
