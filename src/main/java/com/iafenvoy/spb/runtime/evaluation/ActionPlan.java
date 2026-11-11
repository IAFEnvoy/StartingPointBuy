package com.iafenvoy.spb.runtime.evaluation;

import com.iafenvoy.saturn.data.action.EntityAction;

public record ActionPlan(String key, int delay, int repeat, EntityAction action) {
}
