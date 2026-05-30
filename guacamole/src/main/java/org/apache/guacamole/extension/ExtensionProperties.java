package org.apache.guacamole.extension;

import org.apache.guacamole.properties.StringSetProperty;

public final class ExtensionProperties {

    public static final StringSetProperty SKIP_IF_UNAVAILABLE = new StringSetProperty() {
        @Override
        public String getName() {
            return "skip-if-unavailable";
        }
    };

    private ExtensionProperties() {}
}
