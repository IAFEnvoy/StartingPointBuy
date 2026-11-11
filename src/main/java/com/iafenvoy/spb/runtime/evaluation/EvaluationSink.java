package com.iafenvoy.spb.runtime.evaluation;

import com.iafenvoy.saturn.util.formula.NumberProvider;

/**
 * Error-reporting bridge for conditions. Conditions cannot return errors through Saturn's boolean
 * {@code test}, so the evaluator installs a sink into the condition context under
 * {@link ContextKeys#EVAL_SINK}. {@code spb:compare} uses it to report a provider that evaluates to
 * a non-finite value, which Saturn would otherwise silently turn into zero.
 */
public interface EvaluationSink {
    /**
     * Evaluates a provider against the evaluator's context. Records an error and returns
     * {@link Double#NaN} when the result is not finite.
     */
    double number(NumberProvider provider, String field);

    /**
     * Reports an error against the evaluator's current path.
     */
    void error(String message);
}
