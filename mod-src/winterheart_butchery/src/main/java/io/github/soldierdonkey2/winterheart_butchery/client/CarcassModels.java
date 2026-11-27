package io.github.soldierdonkey2.winterheart_butchery.client;

import io.github.soldierdonkey2.winterheart_butchery.WinterheartButchery;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Draws the live animal and Butchery's own carcass block models, so none of its assets are copied. */
final class CarcassModels {
    /** Centre and dimensions of a block model, in blocks. */
    private record Bounds(float x, float y, float z, float width, float height, float depth) {
        static Bounds between(float[] min, float[] max) {
            return new Bounds((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2,
                    max[0] - min[0], max[1] - min[1], max[2] - min[2]);
        }

        float size() {
            return Math.max(0.1F, Math.max(width, Math.max(height, depth)));
        }
    }

    private static final float FRONT = 205;
    private static final float SWAY = 30;
    private static final long SWAY_MILLIS = 8000;
    /** Vanilla's default water blue. The pack recolours water by biome, and outside one it comes out olive. */
    private static final int WATER_TINT = 0xFF3F76E4;

    private final Carcass carcass;
    private final Map<BlockState, Optional<Bounds>> bounds = new HashMap<>();
    private LivingEntity entity;
    private boolean entityTried;

    CarcassModels(Carcass carcass) {
        this.carcass = carcass;
    }

    /** Draws the mob standing on (x, y), scaled to fit a width x height pixel box. */
    void drawEntity(GuiGraphics graphics, int x, int y, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!entityTried && minecraft.level != null) {
            entityTried = true;
            entity = create(minecraft.level);
        }
        if (entity != null) {
            // Entities differ from silverfish to ender dragon, so size each one by its hitbox.
            // Many models are longer than their hitbox, hence the margin on the width. The ender dragon's hitbox is far
            // wider than what shows from this angle, so it gets a fixed scale.
            float scale = entity.getType() == EntityType.ENDER_DRAGON ? 3
                    : Math.min(width / (entity.getBbWidth() * 1.8F), height / Math.max(0.1F, entity.getBbHeight()));
            Quaternionf upright = new Quaternionf().rotateZ((float) Math.PI);
            InventoryScreen.renderEntityInInventory(graphics, x, y, Math.max(1, Math.min(40, (int) scale)), upright,
                    null, entity);
        }
    }

    private LivingEntity create(Level level) {
        try {
            Entity created = carcass.entity().equals("minecraft:player") ? player(level)
                    : BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(carcass.entity())).create(level);
            if (!(created instanceof LivingEntity living)) {
                return null;
            }
            if (carcass.entityNbt() != null) {
                living.load(TagParser.parseTag(carcass.entityNbt())); // picks the colour variant or slime size
            }
            living.yBodyRot = living.yHeadRot = living.yHeadRotO = 135;
            living.setYRot(135);
            return living;
        } catch (Exception e) {
            WinterheartButchery.LOGGER.error("[Butchery] could not create {} for its JEI page", carcass.entity(), e);
            return null;
        }
    }

    /** Players can't be created from their entity type. This one has a default skin and sits far off, so no name tag. */
    private static Entity player(Level level) {
        if (!(level instanceof ClientLevel client)) {
            return null;
        }
        RemotePlayer player = new RemotePlayer(client, new GameProfile(new UUID(0, 0), "Player"));
        player.setPos(0, -10000, 0);
        return player;
    }

    /**
     * Draws a carcass stage (block ID, blockstate) facing the viewer and swaying, centred on (x, y) and fitted into
     * `size` pixels. Carcasses drawn by a block entity renderer have no block model, so they show as their item instead.
     */
    void drawBlock(GuiGraphics graphics, List<String> stage, int x, int y, int size) {
        drawColumn(graphics, List.of(state(stage.get(0), stage.get(1))), x, y, size);
    }

    /** Like drawBlock, for blocks stacked bottom first (a rose bush is two tall). Water is drawn as a cube. */
    void drawColumn(GuiGraphics graphics, List<BlockState> column, int x, int y, int size) {
        draw(graphics, column, x, y, size, false);
    }

    /** Like drawBlock, with water blue on the block's tinted faces: a basin takes its water colour from the biome. */
    void drawHoldingWater(GuiGraphics graphics, List<String> stage, int x, int y, int size) {
        draw(graphics, List.of(state(stage.get(0), stage.get(1))), x, y, size, true);
    }

    private void draw(GuiGraphics graphics, List<BlockState> column, int x, int y, int size, boolean water) {
        Optional<Bounds> measured = union(column);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        if (measured.isEmpty()) {
            pose.translate(x, y, 0);
            pose.scale(2.5F, 2.5F, 1);
            graphics.renderItem(new ItemStack(column.get(0).getBlock()), -8, -8);
        } else {
            Bounds box = measured.get();
            float scale = size * 0.85F / box.size();
            pose.translate(x, y, 150);
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(20));
            pose.mulPose(Axis.YP.rotationDegrees(angle()));
            pose.translate(-box.x(), -box.y(), -box.z());
            Lighting.setupFor3DItems();
            for (BlockState state : column) {
                if (state.is(Blocks.WATER)) {
                    drawWater(graphics, pose);
                } else if (water) {
                    drawTinted(graphics, pose, state);
                } else {
                    Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, graphics.bufferSource(),
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                }
                pose.translate(0, 1, 0);
            }
            graphics.flush();
        }
        pose.popPose();
    }

    private static void drawTinted(GuiGraphics graphics, PoseStack pose, BlockState state) {
        var blocks = Minecraft.getInstance().getBlockRenderer();
        blocks.getModelRenderer().renderModel(pose.last(), graphics.bufferSource().getBuffer(Sheets.cutoutBlockSheet()),
                state, blocks.getBlockModel(state), (WATER_TINT >> 16 & 255) / 255F, (WATER_TINT >> 8 & 255) / 255F,
                (WATER_TINT & 255) / 255F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    /** The box around the whole column; empty if any block has nothing to measure. */
    private Optional<Bounds> union(List<BlockState> column) {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int height = 0; height < column.size(); height++) {
            BlockState state = column.get(height);
            Optional<Bounds> one = state.is(Blocks.WATER) ? Optional.of(new Bounds(0.5F, 0.5F, 0.5F, 1, 1, 1))
                    : bounds.computeIfAbsent(state, CarcassModels::measure);
            if (one.isEmpty()) {
                return Optional.empty();
            }
            Bounds box = one.get();
            float[] centre = {box.x(), box.y() + height, box.z()};
            float[] extent = {box.width(), box.height(), box.depth()};
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(min[axis], centre[axis] - extent[axis] / 2);
                max[axis] = Math.max(max[axis], centre[axis] + extent[axis] / 2);
            }
        }
        return Optional.of(Bounds.between(min, max));
    }

    /** Fluids have no block model, so this draws the top and sides of a source block. */
    private static void drawWater(GuiGraphics graphics, PoseStack pose) {
        IClientFluidTypeExtensions water = IClientFluidTypeExtensions.of(Fluids.WATER);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(water.getStillTexture());
        VertexConsumer buffer = graphics.bufferSource().getBuffer(Sheets.translucentCullBlockSheet());
        float top = 14 / 16F;
        // Each face is anticlockwise seen from outside: its normal, then its four corners.
        float[][][] faces = {
                {{0, 1, 0}, {0, top, 0}, {0, top, 1}, {1, top, 1}, {1, top, 0}},
                {{0, 0, 1}, {0, top, 1}, {0, 0, 1}, {1, 0, 1}, {1, top, 1}},
                {{0, 0, -1}, {1, top, 0}, {1, 0, 0}, {0, 0, 0}, {0, top, 0}},
                {{1, 0, 0}, {1, top, 1}, {1, 0, 1}, {1, 0, 0}, {1, top, 0}},
                {{-1, 0, 0}, {0, top, 0}, {0, 0, 0}, {0, 0, 1}, {0, top, 1}}};
        float[][] uv = {{sprite.getU0(), sprite.getV0()}, {sprite.getU0(), sprite.getV1()},
                {sprite.getU1(), sprite.getV1()}, {sprite.getU1(), sprite.getV0()}};
        for (float[][] face : faces) {
            for (int corner = 0; corner < 4; corner++) {
                float[] at = face[corner + 1];
                buffer.vertex(pose.last().pose(), at[0], at[1], at[2])
                        .color(WATER_TINT >> 16 & 255, WATER_TINT >> 8 & 255, WATER_TINT & 255, WATER_TINT >>> 24)
                        .uv(uv[corner][0], uv[corner][1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT)
                        .normal(pose.last().normal(), face[0][0], face[0][1], face[0][2])
                        .endVertex();
            }
        }
    }

    /** The models face north, away from the viewer: turn them to a front quarter view and sway either side of it. */
    private static float angle() {
        double phase = Util.getMillis() % SWAY_MILLIS / (double) SWAY_MILLIS * 2 * Math.PI;
        return FRONT + SWAY * (float) Math.sin(phase);
    }

    private static BlockState state(String block, String value) {
        BlockState state = BuiltInRegistries.BLOCK.get(new ResourceLocation(block)).defaultBlockState();
        Property<?> property = state.getBlock().getStateDefinition().getProperty("blockstate");
        return property == null ? state : with(state, property, value);
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map(v -> state.setValue(property, v)).orElse(state);
    }

    /** Carcass models stick well out of their block, so fit the view to the model's actual vertices. */
    private static Optional<Bounds> measure(BlockState state) {
        if (state.getRenderShape() != RenderShape.MODEL) {
            return Optional.empty();
        }
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        Direction[] sides = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST,
                Direction.EAST};
        for (Direction side : sides) {
            List<BakedQuad> quads = model.getQuads(state, side, RandomSource.create(0), ModelData.EMPTY, null);
            for (BakedQuad quad : quads) {
                int[] vertices = quad.getVertices();
                int stride = vertices.length / 4;
                for (int v = 0; v < 4; v++) {
                    for (int axis = 0; axis < 3; axis++) {
                        float value = Float.intBitsToFloat(vertices[v * stride + axis]);
                        min[axis] = Math.min(min[axis], value);
                        max[axis] = Math.max(max[axis], value);
                    }
                }
            }
        }
        if (min[0] > max[0]) {
            return Optional.empty();
        }
        return Optional.of(Bounds.between(min, max));
    }
}
