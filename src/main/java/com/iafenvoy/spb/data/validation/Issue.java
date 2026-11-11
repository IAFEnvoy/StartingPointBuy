package com.iafenvoy.spb.data.validation;

import net.minecraft.network.chat.Component;

/**
 * A diagnostic with a definition path. Load-time errors reject the datapack; runtime errors block
 * completion. Warnings are informational. Components allow the GUI to translate messages.
 */
public record Issue(String path, Component message, IssueSeverity severity) {

    public static Issue error(String path, Component message) {
        return new Issue(path, message, IssueSeverity.ERROR);
    }

    public static Issue warning(String path, Component message) {
        return new Issue(path, message, IssueSeverity.WARNING);
    }

    public boolean isError() {
        return this.severity == IssueSeverity.ERROR;
    }

    public Component display() {
        return Component.translatable("spb.issue.format", Component.literal(this.path), this.message);
    }
}
