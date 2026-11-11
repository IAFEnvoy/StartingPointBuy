package com.iafenvoy.spb.data.condition;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.math.CompareOperation;
import com.iafenvoy.spb.runtime.evaluation.ContextKeys;
import com.iafenvoy.spb.runtime.evaluation.EvaluationSink;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

/**
 * {@code spb:compare}: compares two number providers with an optional absolute epsilon.
 *
 * <p>The operator is Saturn's {@link CompareOperation}. With a positive {@code epsilon} the comparison
 * is widened by that absolute tolerance, which Saturn's enum cannot express, and which is what
 * "the remaining points are exactly zero" needs after a few floating point contributions. With
 * {@code epsilon} zero the operator is used as-is, so {@code ==} keeps Saturn's
 * {@link Double#equals} behaviour ({@code -0.0 != 0.0}, {@code NaN == NaN}).
 *
 * <p>When a session evaluator is running it installs an {@link EvaluationSink} into the condition context.
 * The sink is used so a provider that evaluates to a non-finite value fails the comparison and is
 * reported instead of silently becoming zero.
 */
public record CompareEntityCondition(NumberProvider left, CompareOperation operator, NumberProvider right,
                                     double epsilon) implements EntityCondition {
    private static final Codec<Double> EPSILON = Codec.DOUBLE.validate(value -> Double.isFinite(value) && value >= 0.0
            ? DataResult.success(value)
            : DataResult.error(() -> "epsilon must be a finite non-negative number: " + value));

    public static final MapCodec<CompareEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("left").forGetter(CompareEntityCondition::left),
            CompareOperation.CODEC.fieldOf("operator").forGetter(CompareEntityCondition::operator),
            NumberProvider.CODEC.fieldOf("right").forGetter(CompareEntityCondition::right),
            EPSILON.optionalFieldOf("epsilon", 1.0E-8).forGetter(CompareEntityCondition::epsilon)
    ).apply(i, CompareEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        EvaluationSink sink = ctx.getOrDefault(ContextKeys.EVAL_SINK, null);
        double leftValue = read(this.left, sink, ctx, "left");
        double rightValue = read(this.right, sink, ctx, "right");
        if (!Double.isFinite(leftValue) || !Double.isFinite(rightValue)) {
            return false;
        }
        return this.epsilon <= 0.0 ? this.operator.compare(leftValue, rightValue) : this.tolerant(leftValue, rightValue);
    }

    private boolean tolerant(double left, double right) {
        return switch (this.operator) {
            case EQUAL -> Math.abs(left - right) <= this.epsilon;
            case NOT_EQUAL -> Math.abs(left - right) > this.epsilon;
            case LESS_THAN -> left < right - this.epsilon;
            case LESS_THAN_OR_EQUAL -> left <= right + this.epsilon;
            case GREATER_THAN -> left > right + this.epsilon;
            case GREATER_THAN_OR_EQUAL -> left >= right - this.epsilon;
        };
    }

    private static double read(NumberProvider provider, EvaluationSink sink, EntityConditionContext ctx, String field) {
        if (sink != null) {
            return sink.number(provider, field);
        }
        return provider.evaluate(ctx.formula());
    }

    @Override
    public @NonNull MapCodec<CompareEntityCondition> codec() {
        return CODEC;
    }
}
