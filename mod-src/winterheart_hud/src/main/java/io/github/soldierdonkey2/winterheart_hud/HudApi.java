package io.github.soldierdonkey2.winterheart_hud;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Other mods add sections to the panel: {@code HudApi.addSection(player -> List.of(new HudApi.Line("warning", "text")))}. */
public final class HudApi {
    /** style: section, normal, accent, warning, critical, muted or spacer; prefix any style with "c:" to centre it. */
    public record Line(String style, String text) {}

    static final List<Function<ServerPlayer, List<Line>>> SECTIONS = new ArrayList<>();

    private HudApi() {}

    /** Called every update for each player; return an empty list to show nothing. Sections appear in registration order. */
    public static void addSection(Function<ServerPlayer, List<Line>> section) { SECTIONS.add(section); }
}
