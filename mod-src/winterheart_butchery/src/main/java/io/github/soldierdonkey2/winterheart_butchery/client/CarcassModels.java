package io.github.soldierdonkey2.winterheart_butchery.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Draws the live animal and Butchery's own carcass block models, so none of its assets are copied. */
final class CarcassModels {
    /** Centre and largest dimension of a block model, in blocks. */
    private record Bounds(float x, float y, float z, float size) {}

    private final Carcass carcass;
    private final Map<BlockState, Bounds> bounds = new HashMap<>();
    private LivingEntity entity;

    CarcassModels(Carcass carcass) {
        this.carcass = carcass;
    }

    void drawEntity(GuiGraphics graphics, int x, int y, int scale) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == null && minecraft.level != null) {
            Entity created = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(carcass.entity()))
                    .create(minecraft.level);
            if (created instanceof LivingEntity living) {
                living.yBodyRot = living.yHeadRot = living.yHeadRotO = 135;
                living.setYRot(135);
                entity = living;
            }
        }
        if (entity != null) {
            Quaternionf upright = new Quaternionf().rotateZ((float) Math.PI);
            InventoryScreen.renderEntityInInventory(graphics, x, y, scale, upright, null, entity);
        }
    }

    /** Draws the carcass stage turned by `angle` degrees, centred on (x, y) and fitted into `size` pixels. */
    void drawBlock(GuiGraphics graphics, Carcass.Stage stage, int x, int y, int size, float angle) {
        BlockState state = state(stage);
        Bounds box = bounds.computeIfAbsent(state, CarcassModels::measure);
        float scale = size * 0.85F / box.size();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 150);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(20));
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-box.x(), -box.y(), -box.z());
        Lighting.setupFor3DItems();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, graphics.bufferSource(),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        graphics.flush();
        pose.popPose();
    }

    private static BlockState state(Carcass.Stage stage) {
        BlockState state = BuiltInRegistries.BLOCK.get(new ResourceLocation(stage.block())).defaultBlockState();
        Property<?> property = state.getBlock().getStateDefinition().getProperty("blockstate");
        return property == null ? state : with(state, property, Integer.toString(stage.state()));
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map(v -> state.setValue(property, v)).orElse(state);
    }

    /** Carcass models stick well out of their block, so fit the view to the model's actual vertices. */
    private static Bounds measure(BlockState state) {
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
            return new Bounds(0.5F, 0.5F, 0.5F, 1);
        }
        float size = Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
        return new Bounds((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2, Math.max(size, 0.1F));
    }
}
