package io.github.soldierdonkey2.winterheart_hud.client;

import io.github.soldierdonkey2.winterheart_hud.WinterheartHud;
import io.github.soldierdonkey2.winterheart_hud.net.HudNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the survival HUD panel (top right) with a tiled snowy spruce frame whose snow level follows the weather.
 *
 * Border tiles are in textures/gui/wood_border.png, a 64x120 sheet of 8x8 tiles. It holds three 64x40 blocks, one per
 * snow level (0 light = clear, 1 medium = snow, 2 heavy = blizzard), each laid out as:
 *   rows 0-3: top, bottom, left, right edges, 8 variants each (one per column)
 *   row 4:    top-left, top-right, bottom-left, bottom-right corners
 * Each edge tile is picked from the variants by a hash of its position, so the pattern looks random but is stable.
 * Overflow: planks poking out past the frame come from wood_stubs.png (32x32, 8x8 sprites: row 0 left, 1 right,
 * 2 top, 3 bottom stubs, 4 variants each). The snow piles on the top and bottom bars and the snow clumps on the
 * sides are drawn in code, so the pile is one smooth curve across the whole width.
 * python/survival_hud/hud_render.js mirrors this file for the offline preview: keep them in step.
 */
public final class HudClient {
    private static final ResourceLocation TEXTURE = new ResourceLocation("winterheart", "textures/gui/wood_border.png");
    private static final int SHEET_W = 64, SHEET_H = 120, BLOCK_H = 40, VARIANTS = 8;
    private static final ResourceLocation BG_TEXTURE = new ResourceLocation("winterheart", "textures/gui/plank_bg.png");
    private static final int BG_TILE_W = 16, BG_TILE_H = 8;
    private static final ResourceLocation STUB_TEXTURE = new ResourceLocation("winterheart", "textures/gui/wood_stubs.png");
    private static final int STUB_SHEET = 32;
    private static final double[] STUB_CHANCE = {0.10, 0.14, 0.18};    // chance per border tile of a plank poking out, per snow level
    private static final double[] CLUMP_CHANCE = {0.0, 0.18, 0.4};     // chance per side tile of a snow clump on the outside
    private static final double[] PILE_BASE = {2.6, 4.8, 7.2};         // average pile height in px per snow level
    private static final double[] PILE_AMP = {1.6, 2.6, 3.8};          // how much the height rolls up and down
    private static final int PILE_STEP = 1;                            // pile heights snap to multiples of this many px
    private static final double PILE_CUTOFF = 1.5;                     // columns lower than this (px) are not drawn

    private static final int SNOW_BODY = argb(255, 234, 245, 252), SNOW_HIGHLIGHT = argb(255, 252, 254, 255), SNOW_SHADE = argb(255, 176, 202, 226);
    private static final int TILE = 8, WIDTH = 128, MARGIN = 10, PAD = 8, LINE_HEIGHT = 10, SPACER_HEIGHT = 5;
    private static final int BG_SHADE = argb(90, 0, 0, 0);
    private static final int TITLE_COLOR = argb(255, 200, 232, 250);

    private static final Map<String, Integer> STYLE_COLORS = Map.of(
            "section", argb(255, 150, 205, 240), "normal", argb(255, 226, 232, 238), "accent", argb(255, 160, 215, 235),
            "warning", argb(255, 235, 175, 70), "critical", argb(255, 225, 60, 60), "muted", argb(255, 130, 140, 150));

    private record Line(String style, String text) {}

    private static volatile String title = "";
    private static volatile String weather = "clear";
    private static volatile List<Line> lines = List.of();
    private static boolean errorLogged = false;

    private HudClient() {}

    private static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(HudClient::onOverlay);
    }

    public static void receive(HudNet.HudPacket p) {
        List<Line> parsed = new ArrayList<>();
        for (String raw : p.lines()) {
            int split = raw.indexOf('|');
            parsed.add(new Line(raw.substring(0, split), raw.substring(split + 1)));
        }
        title = p.title();
        weather = p.weather();
        lines = parsed;
    }

    private static void onOverlay(RenderGuiOverlayEvent.Post event) {
        // Post fires for every overlay; draw once per frame, after the hotbar
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || lines.isEmpty()) return;
        try {
            render(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), mc.font);
        } catch (RuntimeException e) {
            if (!errorLogged) { errorLogged = true; WinterheartHud.LOGGER.error("[SurvivalHUD] render failed", e); }
        }
    }

    // ---- drawing helpers

    private static void blit(GuiGraphics g, int level, int u, int v, int x, int y) {
        g.blit(TEXTURE, x, y, (float) u, (float) (level * BLOCK_H + v), TILE, TILE, SHEET_W, SHEET_H);
    }

    /** Fills (x, y, w, h) with the plank tile. w must be a multiple of 16 and h a multiple of 8. */
    private static void drawBackground(GuiGraphics g, int x, int y, int w, int h) {
        for (int dy = 0; dy < h; dy += BG_TILE_H)
            for (int dx = 0; dx < w; dx += BG_TILE_W)
                g.blit(BG_TEXTURE, x + dx, y + dy, 0f, 0f, BG_TILE_W, BG_TILE_H, BG_TILE_W, BG_TILE_H);
        g.fill(x, y, x + w, y + h, BG_SHADE);
    }

    /** Stable pseudo-random variant (0..VARIANTS-1) for the index-th tile along a side. */
    private static int variant(int index, int side) {
        double n = Math.sin((index + 1) * 12.9898 + (side + 1) * 78.233) * 43758.5453;
        return (int) (Math.floor((n - Math.floor(n)) * VARIANTS)) % VARIANTS;
    }

    /** Stable pseudo-random number in [0, 1) for (index, side, salt). */
    private static double rand(int index, int side, int salt) {
        double n = Math.sin((index + 1) * 12.9898 + (side + 1) * 78.233 + salt * 37.719) * 43758.5453;
        return n - Math.floor(n);
    }

    /** Border drawn around the rectangle (x, y, w, h): half of it outside, half inside. w and h must be multiples of 8. */
    private static void drawBorder(GuiGraphics g, int level, int x, int y, int w, int h) {
        int x0 = x - TILE / 2, y0 = y - TILE / 2, outerW = w + TILE, outerH = h + TILE;
        int x1 = x0 + outerW - TILE, y1 = y0 + outerH - TILE;
        for (int dx = TILE; dx < outerW - TILE; dx += TILE) {
            int i = dx / TILE;
            blit(g, level, variant(i, 0) * TILE, 0, x0 + dx, y0);
            blit(g, level, variant(i, 1) * TILE, 8, x0 + dx, y1);
        }
        for (int dy = TILE; dy < outerH - TILE; dy += TILE) {
            int i = dy / TILE;
            blit(g, level, variant(i, 2) * TILE, 16, x0, y0 + dy);
            blit(g, level, variant(i, 3) * TILE, 24, x1, y0 + dy);
        }
        blit(g, level, 0, 32, x0, y0);
        blit(g, level, 8, 32, x1, y0);
        blit(g, level, 16, 32, x0, y1);
        blit(g, level, 24, 32, x1, y1);
    }

    private static final Map<String, double[]> PROFILE_CACHE = new HashMap<>();

    /**
     * Height (px, fractional) of the snow pile for each column of a bar that is len px wide: rolling hills from a few
     * sines, a couple of extra humps in heavy snow, and the ends rounded off so the pile slopes down past the corners.
     */
    private static double[] pileProfile(int len, int level, int seed) {
        String key = len + ":" + level + ":" + seed;
        double[] cached = PROFILE_CACHE.get(key);
        if (cached != null) return cached;
        double base = PILE_BASE[level], amp = PILE_AMP[level];
        double hump1 = len * (0.2 + 0.2 * rand(seed, 0, 1)), hump2 = len * (0.6 + 0.25 * rand(seed, 0, 2));
        double[] out = new double[len];
        for (int i = 0; i < len; i++) {
            double v = base + amp * (0.55 * Math.sin(i * 0.16 + seed) + 0.3 * Math.sin(i * 0.07 + seed * 1.7 + 1) + 0.15 * Math.sin(i * 0.33 + seed * 2.3));
            if (level == 2) v += 2.2 * Math.exp(-Math.pow((i - hump1) / 9, 2)) + 1.6 * Math.exp(-Math.pow((i - hump2) / 7, 2));
            int edge = Math.min(i, len - 1 - i);
            double round = edge >= 8 ? 1 : Math.sqrt(1 - Math.pow(1 - edge / 8.0, 2));
            out[i] = Math.max(0, v * round);
        }
        PROFILE_CACHE.put(key, out);
        return out;
    }

    /**
     * Snow pile sitting on a surface. Columns run from x0 to x0 + len; surfaceY is the top row of the wood. The pile
     * grows upward (or is scaled down for the bottom bar) and sinks one pixel into the wood to hide its outline.
     */
    private static void drawPile(GuiGraphics g, int x0, int len, int surfaceY, int level, int seed, double scale) {
        double[] profile = pileProfile(len, level, seed);
        int baseY = surfaceY + 1;
        for (int i = 0; i < len; i++) {
            double raw = profile[i] * scale;
            if (raw < PILE_CUTOFF) continue;
            int full = (int) Math.max(PILE_STEP, Math.floor(raw / PILE_STEP) * PILE_STEP);
            int top = baseY - full, x = x0 + i;
            // ragged underside: stretches of 4 columns slump a pixel over the plank
            int bottom = (full >= 3 && rand(i / 4, seed, 4) < 0.4) ? baseY + 1 : baseY;
            g.fill(x, top, x + 1, bottom, SNOW_BODY);
            g.fill(x, top, x + 1, top + 1, SNOW_HIGHLIGHT);
            g.fill(x, bottom - 1, x + 1, bottom, SNOW_SHADE);
        }
    }

    /** Round snow clump clinging to the outside of a side bar. dir is -1 for the left side, +1 for the right. */
    private static void drawClump(GuiGraphics g, int faceX, int cy, int radius, int dir) {
        for (int dy = -radius; dy <= radius; dy++) {
            int reach = (int) Math.round(Math.sqrt(radius * radius - dy * dy));
            if (reach < 1) continue;
            int xa = dir < 0 ? faceX - reach + 1 : faceX;
            int xb = dir < 0 ? faceX + 1 : faceX + reach;
            g.fill(xa, cy + dy, xb, cy + dy + 1, dy == -radius || dy == -radius + 1 ? SNOW_HIGHLIGHT : (dy == radius ? SNOW_SHADE : SNOW_BODY));
        }
    }

    private static void drawStub(GuiGraphics g, int side, int variant, int x, int y) {
        g.blit(STUB_TEXTURE, x, y, (float) (variant * TILE), (float) (side * TILE), TILE, TILE, STUB_SHEET, STUB_SHEET);
    }

    /** Everything that leaks outside the plain frame: planks poking out, snow clumps on the sides and the two piles. */
    private static void drawOverflow(GuiGraphics g, int level, int x, int y, int w, int h) {
        int x0 = x - TILE / 2, y0 = y - TILE / 2, outerW = w + TILE, outerH = h + TILE;
        int x1 = x0 + outerW - TILE, y1 = y0 + outerH - TILE;
        int tilesX = outerW / TILE, tilesY = outerH / TILE;

        // planks poking out; each gets a random sub-tile offset along the bar
        for (int i = 1; i < tilesX - 1; i++) {
            int jitter = (int) Math.floor(rand(i, 8, 1) * 5) - 2;
            if (rand(i, 4, 0) < STUB_CHANCE[level]) drawStub(g, 2, (int) Math.floor(rand(i, 4, 1) * 4), x0 + i * TILE + jitter, y0 + 3 - 7);
            if (rand(i, 5, 0) < STUB_CHANCE[level]) drawStub(g, 3, (int) Math.floor(rand(i, 5, 1) * 4), x0 + i * TILE + jitter, y1 + 7);
        }
        for (int i = 1; i < tilesY - 1; i++) {
            int jitter = (int) Math.floor(rand(i, 9, 1) * 5) - 2;
            if (rand(i, 6, 0) < STUB_CHANCE[level]) drawStub(g, 0, (int) Math.floor(rand(i, 6, 1) * 4), x0 - 6, y0 + i * TILE + jitter);
            if (rand(i, 7, 0) < STUB_CHANCE[level]) drawStub(g, 1, (int) Math.floor(rand(i, 7, 1) * 4), x1 + 5, y0 + i * TILE + jitter);
        }

        // snow clumps clinging to the outer faces of the side bars (outer face: column 1 on the left, 5 on the right)
        for (int i = 1; i < tilesY - 1; i++) {
            if (rand(i, 10, 0) < CLUMP_CHANCE[level]) drawClump(g, x0 + 1, y0 + i * TILE + 1 + (int) Math.floor(rand(i, 10, 1) * 6), 2 + (int) Math.floor(rand(i, 10, 2) * 2), -1);
            if (rand(i, 11, 0) < CLUMP_CHANCE[level]) drawClump(g, x1 + 5, y0 + i * TILE + 1 + (int) Math.floor(rand(i, 11, 1) * 6), 2 + (int) Math.floor(rand(i, 11, 2) * 2), 1);
        }

        // piles: the top one starts 2px past each corner so it spills over the sides; the bottom one sits on the inside
        drawPile(g, x0 - 1, outerW + 2, y0 + 3, level, 3, 1);
        drawPile(g, x0 + 2, outerW - 4, y1 + 3, level, 7, 0.55);
    }

    /** x for a line: left-aligned inside the padding, or centred across the panel interior. */
    private static int lineX(Font font, String text, int panelX, boolean centered) {
        return centered ? panelX + (int) Math.floor((WIDTH - font.width(text)) / 2.0) : panelX + PAD;
    }

    private static void render(GuiGraphics g, int screenW, Font font) {
        int snow = switch (weather) { case "snow" -> 1; case "blizzard" -> 2; default -> 0; };
        List<Line> ls = lines;
        int contentH = LINE_HEIGHT + 4;
        for (Line l : ls) contentH += l.style().equals("spacer") ? SPACER_HEIGHT : LINE_HEIGHT;
        int h = (int) Math.ceil((contentH + PAD * 2) / (double) TILE) * TILE;
        int x = screenW - MARGIN - WIDTH, y = MARGIN;

        drawBackground(g, x, y, WIDTH, h);
        drawBorder(g, snow, x, y, WIDTH, h);
        drawOverflow(g, snow, x, y, WIDTH, h);

        int ty = y + PAD;
        g.drawString(font, title, lineX(font, title, x, true), ty, TITLE_COLOR, true);
        ty += LINE_HEIGHT + 4;
        for (Line l : ls) {
            if (l.style().equals("spacer")) { ty += SPACER_HEIGHT; continue; }
            // 'c:' prefix centres any style; 'section' headers are always centred
            boolean centered = l.style().startsWith("c:");
            String style = centered ? l.style().substring(2) : l.style();
            centered = centered || style.equals("section");
            int color = STYLE_COLORS.getOrDefault(style, STYLE_COLORS.get("normal"));
            g.drawString(font, l.text(), lineX(font, l.text(), x, centered), ty, color, true);
            ty += LINE_HEIGHT;
        }
    }
}
