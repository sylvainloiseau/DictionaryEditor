package fr.cnrs.lacito.liftgui.ui.utils;

import fr.cnrs.lacito.liftapi.model.AbstractLiftRoot;
import javafx.scene.control.TableColumn;

class MultiTextTableColumn< T extends AbstractLiftRoot, X > extends TableColumn<T, X> {

    public MultiTextTableColumn(String columnName) {
        super(columnName);
    }

}
