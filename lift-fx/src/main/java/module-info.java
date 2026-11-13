/**
 * JavaFX editors for the components of a LIFT dictionary (lift-api).
 */
module fr.cnrs.lacito.liftfx {
    // In the API: the editors take a LiftDictionary and are JavaFX nodes.
    requires transitive fr.cnrs.lacito.liftapi;
    requires transitive javafx.controls;

    exports fr.cnrs.lacito.liftfx;
    exports fr.cnrs.lacito.liftfx.controls;
    // Not part of the API: shared with the DictionaryEditor application only.
    exports fr.cnrs.lacito.liftfx.internal to fr.cnrs.lacito.liftgui;
}
