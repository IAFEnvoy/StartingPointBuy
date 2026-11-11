package com.iafenvoy.spb.testmod;

import com.iafenvoy.spb.runtime.outcome.TaskState;
import com.iafenvoy.spb.runtime.session.SelectionSession;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.nio.file.Path;

public final class CheckpointObserver {
    private final ServerPlayer player;
    private final GameTestHelper helper;
    private final boolean corrupt;
    private boolean sawRunning;

    CheckpointObserver(ServerPlayer player, GameTestHelper helper, boolean corrupt) {
        this.player = player;
        this.helper = helper;
        this.corrupt = corrupt;
    }

    boolean sawRunning() {
        return this.sawRunning;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSave(PlayerEvent.SaveToFile event) {
        if (event.getEntity() != this.player) return;
        SelectionSession onDisk = RegressionTests.saved(this.player).session();
        if (onDisk.tasks().stream().noneMatch(task -> task.state() == TaskState.RUNNING)) return;
        this.sawRunning = true;
        this.helper.assertValueEqual(this.player.getInventory().countItem(Items.BREAD), 0,
                "checkpoint precedes the inventory side effect");
        if (this.corrupt) {
            try {
                Path path = this.player.level().getServer().getPlayerList().getPlayerIo().getPlayerDir().toPath()
                        .resolve(this.player.getStringUUID() + ".dat");
                CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                tag.getCompoundOrEmpty("neoforge:attachments").remove("spb:session");
                NbtIo.writeCompressed(tag, path);
            } catch (Exception exception) { throw new IllegalStateException(exception); }
        }
    }
}
