package com.inza.twoh.client.render;

import com.github.standobyte.jojo.action.stand.StandEntityAction;
import com.github.standobyte.jojo.client.render.MeshModelBox;
import com.github.standobyte.jojo.client.render.entity.model.stand.HumanoidStandModel;
import com.github.standobyte.jojo.client.render.entity.pose.IModelPose;
import com.github.standobyte.jojo.client.render.entity.pose.ModelPose;
import com.github.standobyte.jojo.client.render.entity.pose.RotationAngle;
import com.github.standobyte.jojo.client.render.entity.pose.XRotationModelRenderer;
import com.github.standobyte.jojo.client.render.entity.pose.anim.PosedActionAnimation;
import com.github.standobyte.jojo.entity.stand.StandPose;
import com.inza.twoh.entity.ExampleStandEntity;

import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3f;

public class TheWorldOverHavenStandModel extends HumanoidStandModel<ExampleStandEntity> {
    private ModelRenderer pickaxe;
    private ModelRenderer heartLargeAbdomen;

	public TheWorldOverHavenStandModel() {
        super();
        this.root = new ModelRenderer(this);
        root.setPos(0.0F, 0.0F, 0.0F);
        this.head = new ModelRenderer(this);
        head.setPos(0.0F, 0.0F, 0.0F);
        root.addChild(head);
        head.texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0.0F, false);
        ModelRenderer headpieceNew = new ModelRenderer(this);
        headpieceNew.setPos(0.0F, -2.0F, -5.3F);
        head.addChild(headpieceNew);
        ModelRenderer slopeNew = new ModelRenderer(this);
        slopeNew.setPos(0.0F, -6.4F, 1.3F);
        headpieceNew.addChild(slopeNew);
        new MeshModelBox.Builder(true, this).startFace(Direction.EAST).withVertex(4.2D, 3.0D, 8.4D, 30.0D, 22.0D).withVertex(4.2D, -4.4D, 8.4D, 30.0D, 28.0D).withVertex(4.2D, -6.4D, 0.0D, 38.0D, 28.0D).withVertex(4.2D, 0.0D, 0.0D, 38.0D, 22.0D).createFace().startFace(Direction.WEST).withVertex(-4.2D, 3.0D, 8.4D, 54.0D, 22.0D).withVertex(-4.2D, 0.0D, 0.0D, 46.0D, 22.0D).withVertex(-4.2D, -6.4D, 0.0D, 46.0D, 28.0D).withVertex(-4.2D, -4.4D, 8.4D, 54.0D, 28.0D).createFace().startFaceCalcNormal().withVertex(4.2D, 3.0D, 8.4D, 38.0D, 14.0D).withVertex(4.2D, 0.0D, 0.0D, 38.0D, 22.0D).withVertex(-4.2D, 0.0D, 0.0D, 46.0D, 22.0D).withVertex(-4.2D, 3.0D, 8.4D, 46.0D, 14.0D).createFace().startFaceCalcNormal().withVertex(4.2D, -4.4D, 8.4D, 46.0D, 22.0D).withVertex(-4.2D, -4.4D, 8.4D, 54.0D, 22.0D).withVertex(-4.2D, -6.4D, 0.0D, 54.0D, 14.0D).withVertex(4.2D, -6.4D, 0.0D, 46.0D, 14.0D).createFace().startFace(Direction.SOUTH).withVertex(4.2D, 3.0D, 8.4D, 62.0D, 22.0D).withVertex(-4.2D, 3.0D, 8.4D, 54.0D, 22.0D).withVertex(-4.2D, -4.4D, 8.4D, 54.0D, 28.0D).withVertex(4.2D, -4.4D, 8.4D, 62.0D, 28.0D).createFace().buildCube().addCube(slopeNew);
        ModelRenderer faceRightNew = new ModelRenderer(this);
        faceRightNew.setPos(-4.2F, -6.4F, 1.3F);
        headpieceNew.addChild(faceRightNew);
        new MeshModelBox.Builder(true, this).startFaceCalcNormal().withVertex(0.0D, 0.0D, 0.0D, 70.0D, 21.0D).withVertex(-4.2005D, -0.5D, -1.31D, 74.0D, 21.0D).withVertex(-4.2005D, 0.0D, 0.0266D, 74.0D, 22.0D).createFace().startFaceCalcNormal().withVertex(0.0D, -6.4D, 0.0D, 74.0D, 22.0D).withVertex(-4.2005D, -6.4D, 0.0266D, 78.0D, 21.0D).withVertex(-4.2005D, -6.75D, -1.31D, 78.0D, 22.0D).createFace().startFace(new Vector3f(-0.5F, 0.0F, -0.8660254F)).withVertex(0.0D, 0.0D, 0.0D, 70.0D, 22.0D).withVertex(0.0D, -6.4D, 0.0D, 70.0D, 28.0D).withVertex(-4.2005D, -6.75D, -1.31D, 74.0D, 28.0D).withVertex(-4.2005D, -0.5D, -1.31D, 74.0D, 22.0D).createFace().startFaceCalcNormal().withVertex(-4.6173D, 0.0D, 0.0266D, 75.0D, 22.0D).withVertex(-4.2005D, -0.5D, -1.31D, 74.0D, 22.0D).withVertex(-4.6173D, -6.4D, 0.0266D, 75.0D, 28.0D).withVertex(-4.2005D, -6.75D, -1.31D, 74.0D, 28.0D).createFace().buildCube().addCube(faceRightNew);
        ModelRenderer faceLeftNew = new ModelRenderer(this);
        faceLeftNew.setPos(4.2F, -6.4F, 1.3F);
        headpieceNew.addChild(faceLeftNew);
        new MeshModelBox.Builder(true, this).startFaceCalcNormal().withVertex(4.2005D, 0.0D, 0.0266D, 85.0D, 22.0D).withVertex(4.2005D, -0.5D, -1.31D, 85.0D, 21.0D).withVertex(0.0D, 0.0D, 0.0D, 81.0D, 21.0D).createFace().startFaceCalcNormal().withVertex(4.2005D, -6.75D, -1.31D, 89.0D, 22.0D).withVertex(4.2005D, -6.4D, 0.0266D, 89.0D, 21.0D).withVertex(0.0D, -6.4D, 0.0D, 85.0D, 22.0D).createFace().startFace(new Vector3f(0.5F, 0.0F, -0.8660254F)).withVertex(4.2005D, -0.5D, -1.31D, 85.0D, 22.0D).withVertex(4.2005D, -6.75D, -1.31D, 85.0D, 28.0D).withVertex(0.0D, -6.4D, 0.0D, 81.0D, 28.0D).withVertex(0.0D, 0.0D, 0.0D, 81.0D, 22.0D).createFace().startFaceCalcNormal().withVertex(4.6173D, 0.0D, 0.0266D, 85.0D, 22.0D).withVertex(4.6173D, -6.4D, 0.0266D, 85.0D, 28.0D).withVertex(4.2005D, -0.5D, -1.31D, 86.0D, 22.0D).withVertex(4.2005D, -6.75D, -1.31D, 86.0D, 28.0D).createFace().buildCube().addCube(faceLeftNew);
        ModelRenderer leftCable = new ModelRenderer(this);
        leftCable.setPos(1.25F, -3.3F, 0.25F);
        head.addChild(leftCable);
        setRotationAngle(leftCable, 0.0873F, 0.1309F, -1.2217F);
        leftCable.texOffs(13, 16).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 4.0F, 5.0F, 0.0F, true);
        leftCable.texOffs(13, 25).addBox(-0.5F, 1.0F, 1.0F, 1.0F, 2.0F, 3.0F, 0.0F, true);
        ModelRenderer rightCable = new ModelRenderer(this);
        rightCable.setPos(-1.25F, -3.3F, 0.25F);
        head.addChild(rightCable);
        setRotationAngle(rightCable, 0.0873F, -0.1309F, 1.2217F);
        rightCable.texOffs(0, 16).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 4.0F, 5.0F, 0.0F, false);
        rightCable.texOffs(0, 25).addBox(-0.5F, 1.0F, 1.0F, 1.0F, 2.0F, 3.0F, 0.0F, false);
        ModelRenderer heartSmallHead = new ModelRenderer(this);
        heartSmallHead.setPos(0.0F, 0.55F, -4.0F);
        head.addChild(heartSmallHead);
        ModelRenderer smallHeartCube4 = new ModelRenderer(this);
        smallHeartCube4.setPos(0.0F, 0.0F, 0.0F);
        heartSmallHead.addChild(smallHeartCube4);
        setRotationAngle(smallHeartCube4, 0.0F, 0.0F, -0.7854F);
        smallHeartCube4.texOffs(4, 4).addBox(0.0F, -1.0F, -0.5F, 1.0F, 1.0F, 1.0F, -0.2F, false);
        ModelRenderer smallHeartCube5 = new ModelRenderer(this);
        smallHeartCube5.setPos(0.3F, -0.3F, 0.0F);
        heartSmallHead.addChild(smallHeartCube5);
        setRotationAngle(smallHeartCube5, 0.0F, 0.0F, -0.7854F);
        smallHeartCube5.texOffs(4, 6).addBox(0.175F, -1.0F, -0.5F, 0.925F, 1.0F, 1.0F, -0.2F, false);
        ModelRenderer smallHeartCube6 = new ModelRenderer(this);
        smallHeartCube6.setPos(-0.3F, -0.3F, 0.0F);
        heartSmallHead.addChild(smallHeartCube6);
        setRotationAngle(smallHeartCube6, 0.0F, 0.0F, -0.7854F);
        smallHeartCube6.texOffs(0, 6).addBox(0.0F, -1.1F, -0.5F, 1.0F, 0.925F, 1.0F, -0.2F, false);
        this.body = new ModelRenderer(this);
        body.setPos(0.0F, 0.0F, 0.0F);
        root.addChild(body);
        this.upperPart = new ModelRenderer(this);
        upperPart.setPos(0.0F, 12.0F, 0.0F);
        body.addChild(upperPart);
        this.torso = new ModelRenderer(this);
        torso.setPos(0.0F, -12.0F, 0.0F);
        upperPart.addChild(torso);
        torso.texOffs(0, 64).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, 0.0F, false);
        torso.texOffs(0, 48).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, 0.1F, false);
        torso.texOffs(20, 64).addBox(-3.5F, 1.1F, -2.0F, 7.0F, 3.0F, 1.0F, 0.4F, false);
        torso.texOffs(24, 73).addBox(-2.5F, 4.0F, -2.3F, 5.0F, 6.0F, 1.0F, 0.0F, false);
        torso.texOffs(9, 80).addBox(0.6F, 1.0F, 2.0F, 2.0F, 5.0F, 2.0F, 0.1F, true);
        torso.texOffs(0, 80).addBox(-2.6F, 1.0F, 2.0F, 2.0F, 5.0F, 2.0F, 0.1F, false);
        ModelRenderer beltRight = new ModelRenderer(this);
        beltRight.setPos(-2.0F, 10.35F, 0.0F);
        torso.addChild(beltRight);
        setRotationAngle(beltRight, 0.0F, 0.0F, 0.1309F);
        beltRight.texOffs(64, 74).addBox(-2.0F, -0.5F, -2.0F, 4.0F, 1.0F, 4.0F, 0.13F, false);
        ModelRenderer beltLeft = new ModelRenderer(this);
        beltLeft.setPos(2.0F, 10.35F, 0.0F);
        torso.addChild(beltLeft);
        setRotationAngle(beltLeft, 0.0F, 0.0F, -0.1309F);
        beltLeft.texOffs(80, 74).addBox(-2.0F, -0.5F, -2.0F, 4.0F, 1.0F, 4.0F, 0.13F, false);
        ModelRenderer strapLeft = new ModelRenderer(this);
        strapLeft.setPos(2.65F, 10.0F, 0.0F);
        torso.addChild(strapLeft);
        setRotationAngle(strapLeft, 0.0F, 0.0F, 0.0611F);
        strapLeft.texOffs(50, 65).addBox(-1.35F, -10.1F, -2.5F, 2.0F, 10.0F, 5.0F, 0.0F, true);
        ModelRenderer strapRight = new ModelRenderer(this);
        strapRight.setPos(-2.3F, 10.0F, 0.0F);
        torso.addChild(strapRight);
        setRotationAngle(strapRight, 0.0F, 0.0F, -0.0611F);
        strapRight.texOffs(36, 65).addBox(-1.0F, -10.1F, -2.5F, 2.0F, 10.0F, 5.0F, 0.0F, false);
        this.heartLargeAbdomen = new ModelRenderer(this);
        heartLargeAbdomen.setPos(0.0F, 11.5F, -2.0F);
        torso.addChild(heartLargeAbdomen);
        ModelRenderer largeHeartCube1 = new ModelRenderer(this);
        largeHeartCube1.setPos(0.0F, 2.0F, 0.25F);
        heartLargeAbdomen.addChild(largeHeartCube1);
        setRotationAngle(largeHeartCube1, 0.0F, 0.0F, 0.7854F);
        largeHeartCube1.texOffs(28, 81).addBox(-1.0F, -2.0F, -0.5F, 1.0F, 0.5F, 0.0F, 0.25F, false);
        ModelRenderer largeHeartCube2 = new ModelRenderer(this);
        largeHeartCube2.setPos(0.0F, 2.0F, 0.25F);
        heartLargeAbdomen.addChild(largeHeartCube2);
        setRotationAngle(largeHeartCube2, 0.0F, 0.0F, -0.7854F);
        largeHeartCube2.texOffs(25, 81).addBox(0.0F, -2.0F, -0.5F, 1.0F, 0.5F, 0.0F, 0.25F, false);
        ModelRenderer largeHeartCube3 = new ModelRenderer(this);
        largeHeartCube3.setPos(0.0F, 2.0F, 0.25F);
        heartLargeAbdomen.addChild(largeHeartCube3);
        setRotationAngle(largeHeartCube3, 0.0F, 0.0F, -0.7854F);
        largeHeartCube3.texOffs(22, 81).addBox(0.0F, -1.0F, -0.5F, 1.0F, 1.0F, 0.0F, 0.25F, false);
        ModelRenderer heartSmallAbdomen = new ModelRenderer(this);
        heartSmallAbdomen.setPos(0.0F, 11.3F, -2.05F);
        torso.addChild(heartSmallAbdomen);
        ModelRenderer smallHeartCube1 = new ModelRenderer(this);
        smallHeartCube1.setPos(0.0F, 0.0F, 0.0F);
        heartSmallAbdomen.addChild(smallHeartCube1);
        setRotationAngle(smallHeartCube1, 0.0F, 0.0F, -0.7854F);
        smallHeartCube1.texOffs(17, 82).addBox(0.0F, -1.0F, -0.5F, 1.0F, 1.0F, 1.0F, -0.2F, false);
        ModelRenderer smallHeartCube2 = new ModelRenderer(this);
        smallHeartCube2.setPos(0.3F, -0.3F, 0.0F);
        heartSmallAbdomen.addChild(smallHeartCube2);
        setRotationAngle(smallHeartCube2, 0.0F, 0.0F, -0.7854F);
        smallHeartCube2.texOffs(17, 80).addBox(0.175F, -1.0F, -0.5F, 0.825F, 1.0F, 1.0F, -0.2F, false);
        ModelRenderer smallHeartCube3 = new ModelRenderer(this);
        smallHeartCube3.setPos(-0.3F, -0.3F, 0.0F);
        heartSmallAbdomen.addChild(smallHeartCube3);
        setRotationAngle(smallHeartCube3, 0.0F, 0.0F, -0.7854F);
        smallHeartCube3.texOffs(17, 85).addBox(0.0F, -1.0F, -0.5F, 1.0F, 0.825F, 1.0F, -0.2F, false);
        this.leftArmXRot = new ModelRenderer(this);
        leftArmXRot.setPos(6.0F, -10.0F, 0.0F);
        upperPart.addChild(leftArmXRot);
        this.leftArm = new XRotationModelRenderer(this);
        leftArm.setPos(0.0F, 0.0F, 0.0F);
        leftArmXRot.addChild(leftArm);
        leftArm.texOffs(32, 108).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F, 0.0F, false);
        leftArm.texOffs(53, 95).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 6.0F, 4.0F, 0.1F, true);
        leftArm.texOffs(48, 110).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 3.0F, 4.0F, 0.25F, true);
        leftArm.texOffs(48, 105).addBox(-2.0F, 3.0F, -2.0F, 4.0F, 1.0F, 4.0F, 0.075F, true);
        ModelRenderer heartLeftArm = new ModelRenderer(this);
        heartLeftArm.setPos(0.0F, 3.8F, 1.8F);
        leftArm.addChild(heartLeftArm);
        ModelRenderer heartCube4 = new ModelRenderer(this);
        heartCube4.setPos(0.0F, 0.0F, 0.0F);
        heartLeftArm.addChild(heartCube4);
        setRotationAngle(heartCube4, 0.0F, 0.0F, -0.7854F);
        heartCube4.texOffs(48, 119).addBox(0.0F, -1.0F, -0.5F, 1.0F, 1.0F, 1.0F, -0.05F, false);
        ModelRenderer heartCube5 = new ModelRenderer(this);
        heartCube5.setPos(0.5F, -0.5F, 0.0F);
        heartLeftArm.addChild(heartCube5);
        setRotationAngle(heartCube5, 0.0F, 0.0F, -0.7854F);
        heartCube5.texOffs(44, 120).addBox(0.19F, -1.0F, -0.5F, 0.81F, 1.0F, 1.0F, -0.05F, false);
        ModelRenderer heartCube6 = new ModelRenderer(this);
        heartCube6.setPos(-0.5F, -0.5F, 0.0F);
        heartLeftArm.addChild(heartCube6);
        setRotationAngle(heartCube6, 0.0F, 0.0F, -0.7854F);
        heartCube6.texOffs(48, 121).addBox(0.0F, -1.0F, -0.5F, 1.0F, 0.81F, 1.0F, -0.05F, false);
        this.leftArmJoint = new ModelRenderer(this);
        leftArmJoint.setPos(0.0F, 4.0F, 0.0F);
        leftArm.addChild(leftArmJoint);
        leftArmJoint.texOffs(32, 102).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, -0.1F, true);
        this.leftForeArm = new ModelRenderer(this);
        leftForeArm.setPos(0.0F, 4.0F, 0.0F);
        leftArm.addChild(leftForeArm);
        leftForeArm.texOffs(32, 118).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, -0.001F, true);
        leftForeArm.texOffs(48, 105).addBox(-2.0F, 3.1F, -2.0F, 4.0F, 1.0F, 4.0F, 0.075F, true);
        leftForeArm.texOffs(48, 119).addBox(-2.0F, -0.4F, -2.0F, 4.0F, 4.0F, 4.0F, 0.15F, true);
        leftForeArm.texOffs(32, 96).addBox(0.9F, 2.7F, -1.5F, 2.0F, 3.0F, 3.0F, -0.6F, true);
        leftForeArm.texOffs(42, 97).addBox(1.5F, 5.1F, -2.0F, 1.0F, 1.0F, 4.0F, -0.2F, true);
        this.rightArmXRot = new ModelRenderer(this);
        rightArmXRot.setPos(-6.0F, -10.0F, 0.0F);
        upperPart.addChild(rightArmXRot);
        this.rightArm = new XRotationModelRenderer(this);
        rightArm.setPos(0.0F, 0.0F, 0.0F);
        rightArmXRot.addChild(rightArm);
        rightArm.texOffs(0, 108).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F, 0.0F, false);
        rightArm.texOffs(18, 87).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 6.0F, 4.0F, 0.1F, false);
        rightArm.texOffs(16, 110).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 3.0F, 4.0F, 0.25F, false);
        rightArm.texOffs(16, 105).addBox(-2.0F, 3.0F, -2.0F, 4.0F, 1.0F, 4.0F, 0.075F, false);
        ModelRenderer heartRightArm = new ModelRenderer(this);
        heartRightArm.setPos(0.0F, 3.8F, 1.8F);
        rightArm.addChild(heartRightArm);
        ModelRenderer heartCube1 = new ModelRenderer(this);
        heartCube1.setPos(0.0F, 0.0F, 0.0F);
        heartRightArm.addChild(heartCube1);
        setRotationAngle(heartCube1, 0.0F, 0.0F, -0.7854F);
        heartCube1.texOffs(16, 119).addBox(0.0F, -1.0F, -0.5F, 1.0F, 1.0F, 1.0F, -0.05F, false);
        ModelRenderer heartCube2 = new ModelRenderer(this);
        heartCube2.setPos(0.5F, -0.5F, 0.0F);
        heartRightArm.addChild(heartCube2);
        setRotationAngle(heartCube2, 0.0F, 0.0F, -0.7854F);
        heartCube2.texOffs(12, 120).addBox(0.19F, -1.0F, -0.5F, 0.81F, 1.0F, 1.0F, -0.05F, false);
        ModelRenderer heartCube3 = new ModelRenderer(this);
        heartCube3.setPos(-0.5F, -0.5F, 0.0F);
        heartRightArm.addChild(heartCube3);
        setRotationAngle(heartCube3, 0.0F, 0.0F, -0.7854F);
        heartCube3.texOffs(16, 121).addBox(0.0F, -1.0F, -0.5F, 1.0F, 0.81F, 1.0F, -0.05F, false);
        this.rightArmJoint = new ModelRenderer(this);
        rightArmJoint.setPos(0.0F, 4.0F, 0.0F);
        rightArm.addChild(rightArmJoint);
        rightArmJoint.texOffs(0, 102).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, -0.1F, false);
        this.rightForeArm = new ModelRenderer(this);
        rightForeArm.setPos(0.0F, 4.0F, 0.0F);
        rightArm.addChild(rightForeArm);
        rightForeArm.texOffs(0, 118).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, -0.001F, false);
        rightForeArm.texOffs(16, 105).addBox(-2.0F, 3.1F, -2.0F, 4.0F, 1.0F, 4.0F, 0.075F, false);
        rightForeArm.texOffs(16, 119).addBox(-2.0F, -0.4F, -2.0F, 4.0F, 4.0F, 4.0F, 0.15F, false);
        rightForeArm.texOffs(0, 96).addBox(-2.9F, 2.7F, -1.5F, 2.0F, 3.0F, 3.0F, -0.6F, false);
        rightForeArm.texOffs(10, 97).addBox(-2.5F, 5.1F, -2.0F, 1.0F, 1.0F, 4.0F, -0.2F, false);
        this.leftLegXRot = new ModelRenderer(this);
        leftLegXRot.setPos(2.0F, 12.0F, 0.0F);
        body.addChild(leftLegXRot);
        this.leftLeg = new XRotationModelRenderer(this);
        leftLeg.setPos(0.0F, 0.0F, 0.0F);
        leftLegXRot.addChild(leftLeg);
        leftLeg.texOffs(96, 108).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, 0.0F, false);
        leftLeg.texOffs(112, 99).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F, 0.2F, false);
        leftLeg.texOffs(112, 108).addBox(1.8F, -1.75F, -1.5F, 1.0F, 3.0F, 3.0F, 0.0F, true);
        ModelRenderer heartRightLeg = new ModelRenderer(this);
        heartRightLeg.setPos(0.0F, 6.0F, -1.8F);
        leftLeg.addChild(heartRightLeg);
        ModelRenderer heartCube10 = new ModelRenderer(this);
        heartCube10.setPos(0.0F, 0.05F, 0.0F);
        heartRightLeg.addChild(heartCube10);
        setRotationAngle(heartCube10, 0.0F, 0.0F, -0.7854F);
        heartCube10.texOffs(96, 120).addBox(0.0F, -1.05F, -0.5F, 1.0F, 1.0F, 1.0F, -0.05F, false);
        ModelRenderer heartCube11 = new ModelRenderer(this);
        heartCube11.setPos(0.5F, -0.45F, 0.0F);
        heartRightLeg.addChild(heartCube11);
        setRotationAngle(heartCube11, 0.0F, 0.0F, -0.7854F);
        heartCube11.texOffs(92, 120).addBox(0.19F, -1.05F, -0.5F, 0.81F, 1.0F, 1.0F, -0.05F, false);
        ModelRenderer heartCube12 = new ModelRenderer(this);
        heartCube12.setPos(-0.5F, -0.45F, 0.0F);
        heartRightLeg.addChild(heartCube12);
        setRotationAngle(heartCube12, 0.0F, 0.0F, -0.7854F);
        heartCube12.texOffs(92, 118).addBox(0.0F, -1.05F, -0.5F, 1.0F, 0.81F, 1.0F, -0.05F, false);
        this.leftLegJoint = new ModelRenderer(this);
        leftLegJoint.setPos(0.0F, 6.0F, 0.0F);
        leftLeg.addChild(leftLegJoint);
        leftLegJoint.texOffs(96, 102).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, -0.1F, true);
        this.leftLowerLeg = new ModelRenderer(this);
        leftLowerLeg.setPos(0.0F, 6.0F, 0.0F);
        leftLeg.addChild(leftLowerLeg);
        leftLowerLeg.texOffs(96, 118).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, -0.001F, false);
        leftLowerLeg.texOffs(112, 118).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, 0.2F, false);
        this.rightLegXRot = new ModelRenderer(this);
        rightLegXRot.setPos(-2.0F, 12.0F, 0.0F);
        body.addChild(rightLegXRot);
        this.rightLeg = new XRotationModelRenderer(this);
        rightLeg.setPos(0.0F, 0.0F, 0.0F);
        rightLegXRot.addChild(rightLeg);
        rightLeg.texOffs(64, 108).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, 0.0F, false);
        rightLeg.texOffs(80, 99).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F, 0.2F, false);
        rightLeg.texOffs(80, 108).addBox(-2.8F, -1.25F, -1.5F, 1.0F, 3.0F, 3.0F, 0.0F, false);
        ModelRenderer heartLeftLeg = new ModelRenderer(this);
        heartLeftLeg.setPos(0.0F, 6.0F, -1.8F);
        rightLeg.addChild(heartLeftLeg);
        ModelRenderer heartCube7 = new ModelRenderer(this);
        heartCube7.setPos(0.0F, 0.05F, 0.0F);
        heartLeftLeg.addChild(heartCube7);
        setRotationAngle(heartCube7, 0.0F, 0.0F, -0.7854F);
        heartCube7.texOffs(64, 120).addBox(0.0F, -1.05F, -0.5F, 1.0F, 1.0F, 1.0F, -0.05F, false);
        ModelRenderer heartCube8 = new ModelRenderer(this);
        heartCube8.setPos(0.5F, -0.45F, 0.0F);
        heartLeftLeg.addChild(heartCube8);
        setRotationAngle(heartCube8, 0.0F, 0.0F, -0.7854F);
        heartCube8.texOffs(60, 120).addBox(0.19F, -1.05F, -0.5F, 0.81F, 1.0F, 1.0F, -0.05F, false);
        ModelRenderer heartCube9 = new ModelRenderer(this);
        heartCube9.setPos(-0.5F, -0.45F, 0.0F);
        heartLeftLeg.addChild(heartCube9);
        setRotationAngle(heartCube9, 0.0F, 0.0F, -0.7854F);
        heartCube9.texOffs(60, 118).addBox(0.0F, -1.05F, -0.5F, 1.0F, 0.81F, 1.0F, -0.05F, false);
        this.rightLegJoint = new ModelRenderer(this);
        rightLegJoint.setPos(0.0F, 6.0F, 0.0F);
        rightLeg.addChild(rightLegJoint);
        rightLegJoint.texOffs(64, 102).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, -0.1F, false);
        this.rightLowerLeg = new ModelRenderer(this);
        rightLowerLeg.setPos(0.0F, 6.0F, 0.0F);
        rightLeg.addChild(rightLowerLeg);
        rightLowerLeg.texOffs(64, 118).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, -0.001F, false);
        rightLowerLeg.texOffs(80, 118).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, 0.2F, false);
        return;
	}

	@Override // TODO summon poses
    protected RotationAngle[][] initSummonPoseRotations() {
        return new RotationAngle[][] {
            new RotationAngle[] {
                    
            },
            new RotationAngle[] {
                    
            }
		};
    }
    
    @Override
    protected void initActionPoses() { // TODO pickaxe throwing anim
        actionAnim.put(StandPose.RANGED_ATTACK, new PosedActionAnimation.Builder<ExampleStandEntity>()
                .addPose(StandEntityAction.Phase.BUTTON_HOLD, new ModelPose<>(new RotationAngle[] {
                        new RotationAngle(body, 0.0F, -0.48F, 0.0F),
                        new RotationAngle(leftArm, 0.0F, 0.0F, -0.7854F),
                        new RotationAngle(leftForeArm, 0.0F, 0.0F, 0.6109F),
                        new RotationAngle(rightArm, -1.0908F, 0.0F, 1.5708F), 
                        new RotationAngle(rightForeArm, 0.0F, 0.0F, 0.0F)
                }))
                .build(idlePose));
        
        super.initActionPoses();
    }

    @Override
    public void prepareMobModel(ExampleStandEntity entity, float walkAnimPos, float walkAnimSpeed, float partialTick) {
        super.prepareMobModel(entity, walkAnimPos, walkAnimSpeed, partialTick);
        if (pickaxe != null) {
            pickaxe.visible = entity.hasPickaxe();
        }
    }
    
    

    @Override // TODO idle pose
    protected ModelPose<ExampleStandEntity> initIdlePose() {
        return super.initIdlePose();
    }

    @Override
    protected IModelPose<ExampleStandEntity> initIdlePose2Loop() {
        return super.initIdlePose2Loop();
    }
}
