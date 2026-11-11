package com.iafenvoy.spb.runtime.evaluation;

import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.spb.data.validation.Issue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;

final class EvaluationDiagnostics implements EvaluationSink {
    private final Player player;
    private final FormulaContext context;
    private final List<Issue> issues;
    private String path;

    EvaluationDiagnostics(Player player, FormulaContext context, List<Issue> issues, String base) {
        this.player = player;
        this.context = context;
        this.issues = issues;
        this.path = base;
    }

    void enter(String path) {
        this.path = path;
    }

    Player player() {
        return this.player;
    }

    FormulaContext context() {
        return this.context;
    }

    @Override
    public double number(NumberProvider provider, String field) {
        try {
            return provider.evaluate(this.context);
        } catch (RuntimeException exception) {
            this.error(field + " failed: " + exception.getMessage());
            return Double.NaN;
        }
    }

    @Override
    public void error(String message) {
        this.issues.add(Issue.error(this.path, Component.literal(message)));
    }
}
