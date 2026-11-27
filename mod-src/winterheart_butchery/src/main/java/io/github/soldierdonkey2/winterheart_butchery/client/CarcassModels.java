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
    /** Centre and largest dimension of a block model, in blocks. */
    private record Bounds(float x, float y, float z, float size) {}

    private static final float FRONT = 205;
    private static final float SWAY = 30;
    private static final long SWAY_MILLIS = 8000;

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
        BlockState state = state(stage.get(0), stage.get(1));
        Optional<Bounds> measured = bounds.computeIfAbsent(state, CarcassModels::measure);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        if (measured.isEmpty()) {
            pose.translate(x, y, 0);
            pose.scale(2.5F, 2.5F, 1);
            graphics.renderItem(new ItemStack(state.getBlock()), -8, -8);
        } else {
            Bounds box = measured.get();
            float scale = size * 0.85F / box.size();
            pose.translate(x, y, 150);
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(20));
            pose.mulPose(Axis.YP.rotationDegrees(angle()));
            pose.translate(-box.x(), -box.y(), -box.z());
            Lighting.setupFor3DItems();
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            graphics.flush();
        }
        pose.popPose();
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
        float size = Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
        return Optional.of(new Bounds((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2,
                Math.max(size, 0.1F)));
    }
}
