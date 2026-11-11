package com.iafenvoy.spb.runtime.evaluation;

/**
 * Keys used in Saturn's generic {@code Context} data map. Only plain values are stored, never global
 * "current player" state.
 */
public final class ContextKeys {
    /**
     * {@link EvaluationSink} installed while the session evaluator runs conditions.
     */
    public static final String EVAL_SINK = "spb:eval_sink";
    /**
     * Session id ({@link java.util.UUID}) made available to delayed actions.
     */
    public static final String SESSION = "spb:session";

    private ContextKeys() {
    }
}
