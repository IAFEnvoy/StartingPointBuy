package com.iafenvoy.spb.runtime.session;

public final class CheckpointException extends IllegalStateException {
    CheckpointException(Exception cause) {
        super("Could not persist SPB checkpoint", cause);
    }
}
