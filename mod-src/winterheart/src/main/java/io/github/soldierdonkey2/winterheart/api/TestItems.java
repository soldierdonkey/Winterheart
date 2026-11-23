package io.github.soldierdonkey2.winterheart.api;

import io.github.soldierdonkey2.winterheart.world.WorldState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Right-click diagnostics: vanilla items that print or change sanity and temperature, for testing. */
public final class TestItems {
    private TestItems() {}

    private static void say(ServerPlayer p, String s) { p.sendSystemMessage(Component.literal(s)); }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer p)) return;
        Item item = event.getItemStack().getItem();
        if (item == Items.NETHER_STAR) {
            say(p, "Nether Star right-clicked! Running diagnostics!");
            say(p, "Current sanity: " + Sanity.get(p));
            Sanity.add(p, 10);
            say(p, "New sanity: " + Sanity.get(p));
        } else if (item == Items.NETHERITE_INGOT) {
            say(p, "Netherite Ingot right-clicked! Running diagnostics!");
            say(p, "Current sanity: " + Sanity.get(p));
            Sanity.add(p, -10);
            say(p, "New sanity: " + Sanity.get(p));
        } else if (item == Items.END_PORTAL_FRAME) {
            say(p, "End Portal Frame right-clicked! Running diagnostics!");
            say(p, "Current sanity: " + Sanity.get(p));
            say(p, "Previous sanity: " + WorldState.tag(p.server).getDouble("previous_sanity_" + p.getUUID()));
        } else if (item == Items.QUARTZ) {
            say(p, "Quartz right-clicked! Running diagnostics!");
            say(p, "Current temperature: " + Temperature.get(p));
            Temperature.add(p, 5);
            say(p, "New temperature: " + Temperature.get(p));
        } else if (item == Items.NETHER_BRICK) {
            say(p, "Nether Brick right-clicked! Running diagnostics!");
        }
    }
}
