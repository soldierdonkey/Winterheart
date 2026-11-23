package io.github.soldierdonkey2.winterheart_hud;

import io.github.soldierdonkey2.winterheart.api.Temperature;
import io.github.soldierdonkey2.winterheart.world.WorldState;
import io.github.soldierdonkey2.winterheart_hud.net.HudNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/** The server half of the survival HUD: builds each player's lines every 5 ticks and sends them as a packet. */
public final class HudServer {
    public static final int UPDATE_TICKS = 5;

    private HudServer() {}

    private static String weatherStyle(ServerLevel l) { return l.isThundering() ? "critical" : l.isRaining() ? "warning" : "accent"; }
    private static String weatherLabel(ServerLevel l) { return l.isThundering() ? "Blizzard" : l.isRaining() ? "Snowfall" : "Clear"; }

    static String phaseStyle(String phase) {
        switch (phase) {
            case "DAWN": case "NOON": return "accent";
            case "AFTERNOON": case "DUSK": return "warning";
            case "NIGHT": case "MIDNIGHT": case "ETERNAL_NIGHT": return "critical";
            default: return "normal";
        }
    }

    private static String tempStyle(double t) { return t <= 25 ? "critical" : t <= 40 ? "warning" : "accent"; }

    /** Like JavaScript's Number.toPrecision(3). */
    static String precision3(double v) {
        if (v == 0) return "0.00";
        BigDecimal b = new BigDecimal(v).round(new MathContext(3));
        String s = b.toPlainString();
        int digits = s.replace("-", "").replace(".", "").replaceAll("^0+", "").length();
        if (!s.contains(".") && digits < 3) s += ".";
        StringBuilder sb = new StringBuilder(s);
        while (sb.toString().replace("-", "").replace(".", "").replaceAll("^0+", "").length() < 3) sb.append('0');
        return sb.toString().endsWith(".") ? sb.substring(0, sb.length() - 1) : sb.toString();
    }

    public static List<String> buildLines(ServerPlayer player, ServerLevel overworld) {
        CompoundTag data = WorldState.tag(player.server);
        int day = data.getInt("custom_day") == 0 ? 1 : data.getInt("custom_day");
        String phase = data.getString("current_phase").isEmpty() ? "DAWN" : data.getString("current_phase");
        double ambient = Double.parseDouble(precision3(Temperature.getAmbient(player)));
        List<String> lines = new ArrayList<>(List.of(
                "section|-- Forecast --",
                "normal|Day: " + day,
                phaseStyle(phase) + "|Time: " + phase,
                weatherStyle(overworld) + "|Weather: " + weatherLabel(overworld),
                "spacer|",
                tempStyle(ambient) + "|Ambient: " + precision3(Temperature.getAmbient(player)) + "°"));
        for (var section : HudApi.SECTIONS) {
            List<HudApi.Line> extra = section.apply(player);
            if (extra == null || extra.isEmpty()) continue;
            for (HudApi.Line l : extra) lines.add(l.style() + "|" + l.text());
        }
        return lines;
    }

    public static void update(ServerPlayer player) {
        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        String weather = overworld.isThundering() ? "blizzard" : overworld.isRaining() ? "snow" : "clear";
        HudNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HudNet.HudPacket("SURVIVAL", weather, buildLines(player, overworld)));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer p)) return;
        if (p.tickCount % UPDATE_TICKS != 0) return;
        update(p);
    }
}
