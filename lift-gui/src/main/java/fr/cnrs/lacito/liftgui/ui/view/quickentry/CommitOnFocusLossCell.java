package fr.cnrs.lacito.liftgui.ui.view.quickentry;

import java.util.function.Function;
import javafx.beans.property.StringProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;

/**
 * A table cell that always shows a text field, and stores its text on focus loss, Tab and
 * Enter (not only on Enter as the default {@code TextFieldTableCell}).
 */
final class CommitOnFocusLossCell<S> extends TableCell<S, String> {

    private final TextField textField = new TextField();
    private final Function<S, StringProperty> propertyAccessor;
    private StringProperty boundProperty;

    CommitOnFocusLossCell(Function<S, StringProperty> propertyAccessor) {
        this.propertyAccessor = propertyAccessor;
        textField.setOnAction(e -> commitValue());
        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) commitValue();
            // The text field takes the clicks, so the table's selection would never change:
            // select the row here. The field left has already committed its text, so the
            // row left is complete when the table's selection listener sees it.
            else if (getTableView() != null && getIndex() >= 0) getTableView().getSelectionModel().select(getIndex());
        });
    }

    private void commitValue() {
        if (boundProperty != null) boundProperty.set(textField.getText() != null ? textField.getText() : "");
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || getTableRow() == null || getTableRow().getItem() == null) {
            setGraphic(null);
            boundProperty = null;
            return;
        }
        boundProperty = propertyAccessor.apply(getTableRow().getItem());
        if (!textField.isFocused()) textField.setText(boundProperty.get());
        setGraphic(textField);
    }
}
