package fr.cnrs.lacito.liftgui.testsupport;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * Preferences kept in memory, so that the tests never read nor write the user's settings
 * (recent files, interface language…). Installed by the surefire configuration through the
 * {@code java.util.prefs.PreferencesFactory} system property.
 */
public final class InMemoryPreferences extends AbstractPreferences {

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, InMemoryPreferences> children = new HashMap<>();

    private InMemoryPreferences(InMemoryPreferences parent, String name) {
        super(parent, name);
    }

    @Override protected void putSpi(String key, String value) { values.put(key, value); }
    @Override protected String getSpi(String key) { return values.get(key); }
    @Override protected void removeSpi(String key) { values.remove(key); }
    @Override protected void removeNodeSpi() { values.clear(); children.clear(); }
    @Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
    @Override protected String[] childrenNamesSpi() { return children.keySet().toArray(new String[0]); }
    @Override protected AbstractPreferences childSpi(String name) {
        return children.computeIfAbsent(name, n -> new InMemoryPreferences(this, n));
    }
    @Override protected void syncSpi() {}
    @Override protected void flushSpi() {}

    /** Removes every key of every node (between tests). */
    public static void clearAll() {
        clearNode(Factory.USER);
    }

    private static void clearNode(InMemoryPreferences node) {
        node.values.clear();
        node.children.values().forEach(InMemoryPreferences::clearNode);
    }

    public static final class Factory implements PreferencesFactory {
        static final InMemoryPreferences USER = new InMemoryPreferences(null, "");
        static final InMemoryPreferences SYSTEM = new InMemoryPreferences(null, "");

        @Override public Preferences userRoot() { return USER; }
        @Override public Preferences systemRoot() { return SYSTEM; }
    }
}
