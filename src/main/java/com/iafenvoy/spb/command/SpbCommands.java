package com.iafenvoy.spb.command;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.spb.data.Plan;
import com.iafenvoy.spb.data.resolution.PlanIndex;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.registry.SpbResourceKeys;
import com.iafenvoy.spb.runtime.outcome.OutcomeService;
import com.iafenvoy.spb.runtime.session.SelectionSession;
import com.iafenvoy.spb.runtime.session.SessionAttachment;
import com.iafenvoy.spb.runtime.session.SessionService;
import com.iafenvoy.spb.runtime.session.SessionState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;

/**
 * {@code /spb ...}: manual entry point, diagnostics and administrator recovery.
 */
@EventBusSubscriber
public final class SpbCommands {
    private SpbCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext context = event.getBuildContext();
        dispatcher.register(Commands.literal("starting_point_buy")
                .then(Commands.literal("open")
                        .then(Commands.argument("plan", ResourceArgument.resource(context, SpbResourceKeys.PLAN))
                                .executes(ctx -> open(ctx, ResourceArgument.getResource(ctx, "plan", SpbResourceKeys.PLAN)))))
                .then(Commands.literal("status").executes(SpbCommands::status))
                .then(Commands.literal("refresh").executes(SpbCommands::refresh))
                .then(Commands.literal("unlock")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("plan", ResourceArgument.resource(context, SpbResourceKeys.PLAN))
                                        .executes(ctx -> unlock(ctx, ResourceArgument.getResource(ctx, "plan", SpbResourceKeys.PLAN))))))
                .then(Commands.literal("recover")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SpbCommands::recover))));
    }

    private static int open(CommandContext<CommandSourceStack> context, Holder<Plan> plan) throws CommandSyntaxException {
        SessionService.open(context.getSource().getPlayerOrException(), plan);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        SessionAttachment attachment = SessionService.attachment(player);
        SelectionSession session = attachment.session();
        if (session == null) {
            context.getSource().sendSuccess(() -> Component.translatable("spb.message.no_session"), false);
            return 1;
        }
        Optional<ResolvedPlan> plan = PlanIndex.find(player.level().registryAccess(), session.planId());
        plan.ifPresent(SessionService::logIssues);
        context.getSource().sendSuccess(() -> Component.translatable("spb.command.status",
                session.planId().toString(), session.state().getSerializedName(), session.revision(),
                session.nonZeroSelections().size(), session.tasks().size(), attachment.completions().size()), false);
        return 1;
    }

    private static int refresh(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return SessionService.refresh(context.getSource().getPlayerOrException()) ? 1 : 0;
    }

    private static int unlock(CommandContext<CommandSourceStack> context, Holder<Plan> plan) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        SessionService.unlock(target, plan);
        context.getSource().sendSuccess(() -> Component.translatable("spb.command.unlocked", target.getScoreboardName(), HolderHelper.id(plan).toString()), true);
        return 1;
    }

    private static int recover(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        SessionAttachment attachment = SessionService.attachment(target);
        SelectionSession session = attachment.session();
        if (session == null) {
            context.getSource().sendFailure(Component.translatable("spb.message.no_session"));
            return 0;
        }
        if (session.state() != SessionState.NEEDS_REVIEW) {
            context.getSource().sendFailure(Component.translatable("spb.command.not_in_review"));
            return 0;
        }
        OutcomeService.resume(target, attachment, session);
        context.getSource().sendSuccess(() -> Component.translatable("spb.command.recovered", target.getScoreboardName()), true);
        return 1;
    }
}
