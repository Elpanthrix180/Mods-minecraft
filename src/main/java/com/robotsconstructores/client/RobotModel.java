package com.robotsconstructores.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.robotsconstructores.RobotsConstructores;
import com.robotsconstructores.entity.BaseRobotEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Modelo de bloques sencillo (ligero para ordenadores modestos). */
public class RobotModel<T extends BaseRobotEntity> extends EntityModel<T> {
    public static final ModelLayerLocation BUILDER_LAYER =
            new ModelLayerLocation(new ResourceLocation(RobotsConstructores.MOD_ID, "builder_robot"), "main");
    public static final ModelLayerLocation MINER_LAYER =
            new ModelLayerLocation(new ResourceLocation(RobotsConstructores.MOD_ID, "miner_robot"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public RobotModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    private static PartDefinition createBase(MeshDefinition mesh) {
        PartDefinition p = mesh.getRoot();
        p.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, 0.0F, -3.0F, 8, 10, 6),
                PartPose.offset(0.0F, 8.0F, 0.0F));
        p.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, 0.0F, -1.5F, 3, 6, 3),
                PartPose.offset(-2.0F, 18.0F, 0.0F));
        p.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(12, 32).addBox(-1.5F, 0.0F, -1.5F, 3, 6, 3),
                PartPose.offset(2.0F, 18.0F, 0.0F));
        p.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(44, 16).addBox(0.0F, -1.0F, -1.5F, 3, 9, 3),
                PartPose.offset(4.0F, 9.0F, 0.0F));
        return p;
    }

    /** Constructor: antena en la cabeza y brazo con "colocador" de bloques. */
    public static LayerDefinition createBuilderLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = createBase(mesh);
        PartDefinition head = p.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8)
                        .texOffs(56, 0).addBox(-0.5F, -11.0F, -0.5F, 1, 3, 1)
                        .texOffs(56, 8).addBox(-1.0F, -13.0F, -1.0F, 2, 2, 2),
                PartPose.offset(0.0F, 8.0F, 0.0F));
        PartDefinition arm = p.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(32, 16).addBox(-3.0F, -1.0F, -1.5F, 3, 9, 3),
                PartPose.offset(-4.0F, 9.0F, 0.0F));
        arm.addOrReplaceChild("tool", CubeListBuilder.create()
                        .texOffs(24, 32).addBox(-3.5F, 7.0F, -5.0F, 4, 2, 5),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Minero: casco con lámpara y taladro en el brazo. */
    public static LayerDefinition createMinerLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = createBase(mesh);
        p.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8)
                        .texOffs(40, 0).addBox(-1.0F, -9.0F, -5.0F, 2, 2, 1),
                PartPose.offset(0.0F, 8.0F, 0.0F));
        PartDefinition arm = p.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(32, 16).addBox(-3.0F, -1.0F, -1.5F, 3, 9, 3),
                PartPose.offset(-4.0F, 9.0F, 0.0F));
        arm.addOrReplaceChild("tool", CubeListBuilder.create()
                        .texOffs(24, 40).addBox(-3.0F, 7.0F, -6.0F, 3, 3, 6)
                        .texOffs(44, 32).addBox(-2.0F, 8.0F, -9.0F, 1, 1, 3),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 1.4F * limbSwingAmount;
        if (entity.isWorkingClient()) {
            // brazo de trabajo moviéndose arriba y abajo
            rightArm.xRot = -1.2F + Mth.sin(ageInTicks * 0.5F) * 0.5F;
        } else {
            rightArm.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * limbSwingAmount;
        }
        leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * limbSwingAmount;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
