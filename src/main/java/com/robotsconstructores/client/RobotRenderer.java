package com.robotsconstructores.client;

import com.robotsconstructores.entity.BaseRobotEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

public class RobotRenderer<T extends BaseRobotEntity> extends MobRenderer<T, RobotModel<T>> {
    private final ResourceLocation texture;

    public RobotRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer,
                         ResourceLocation texture, ResourceLocation glowTexture) {
        super(context, new RobotModel<>(context.bakeLayer(layer)), 0.45F);
        this.texture = texture;
        final RenderType glow = RenderType.eyes(glowTexture);
        addLayer(new EyesLayer<T, RobotModel<T>>(this) {
            @Override
            public RenderType renderType() {
                return glow;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }
}
