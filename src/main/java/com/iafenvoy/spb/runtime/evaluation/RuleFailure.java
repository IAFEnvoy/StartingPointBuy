package com.iafenvoy.spb.runtime.evaluation;

import net.minecraft.network.chat.Component;

public record RuleFailure(String path, Component message) {
}
