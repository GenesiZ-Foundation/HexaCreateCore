package com.genesiz.hexacreatecore.bossrush.dragon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DragonMultipartCollider {

  public static final class BonePart extends PartEntity<EnderDragon> {
    public final String partName;
    public final boolean solidCollision;
    private EntityDimensions dims;
    private Vec3 lastCenter;

    public BonePart(EnderDragon parent, String partName, float width, float height, boolean solidCollision) {
      super(parent);
      this.partName = partName;
      this.solidCollision = solidCollision;
      this.dims = EntityDimensions.scalable(width, height);
      this.noPhysics = true;
      this.refreshDimensions();
    }

    public void syncBox(Vec3 center, float sizeX, float sizeY, float sizeZ) {
      float sx = Math.max(0.25f, sizeX);
      float sy = Math.max(0.25f, sizeY);
      float sz = Math.max(0.25f, sizeZ);
      float horiz = Math.max(sx, sz);

      if (Math.abs(this.dims.width() - horiz) > 0.05f || Math.abs(this.dims.height() - sy) > 0.05f) {
        this.dims = EntityDimensions.scalable(horiz, sy);
        this.refreshDimensions();
      }

      double bottomY = center.y - (sy * 0.5D);
      if (this.lastCenter == null) {
        this.xo = center.x;
        this.yo = bottomY;
        this.zo = center.z;
        this.xOld = center.x;
        this.yOld = bottomY;
        this.zOld = center.z;
      } else {
        double prevBottomY = this.lastCenter.y - (sy * 0.5D);
        this.xo = this.lastCenter.x;
        this.yo = prevBottomY;
        this.zo = this.lastCenter.z;
        this.xOld = this.lastCenter.x;
        this.yOld = prevBottomY;
        this.zOld = this.lastCenter.z;
      }
      this.lastCenter = center;

      this.setPos(center.x, bottomY, center.z);
      this.setBoundingBox(new AABB(
        center.x - (sx * 0.5D), bottomY, center.z - (sz * 0.5D),
        center.x + (sx * 0.5D), bottomY + sy, center.z + (sz * 0.5D)
      ));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
      return true;
    }

    @Override
    public boolean canBeCollidedWith() {
      return this.solidCollision;
    }

    @Override
    public boolean canCollideWith(Entity other) {
      if (!this.solidCollision) return false;
      if (other == this.getParent()) return false;
      if (other instanceof BonePart bp && bp.getParent() == this.getParent()) return false;
      return super.canCollideWith(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
      EnderDragon parent = this.getParent();
      if (parent == null || this.isInvulnerableTo(source)) return false;
      float mult = switch (this.partName) {
        case "head" -> 1.25f;
        case "neck_span", "tail_base", "tail_tip" -> 1.00f;
        case "frontleg_span_r", "frontleg_span_l", "frontfoot", "frontfoot1",
        "wing_r", "wing_l", "wingtip_r", "wingtip_l" -> 0.95f;
        case "body" -> 0.90f;
        case "rearleg_span_r", "rearleg_span_l", "rearfoot", "rearfoot1" -> 0.85f;
        default -> 1.00f;
      };

      float finalAmount = amount;
      if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
        float baseCap = Math.max(1.0f, parent.getMaxHealth() * 0.02f);
        finalAmount = Math.min(amount, baseCap) * mult;
      }

      IN_PART_HURT.set(true);
      try {
        return parent.hurt(source, finalAmount);
      } finally {
        IN_PART_HURT.set(false);
      }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
      return this.dims;
    }
  }

  public static final class PartRig {
    public final BonePart body;
    public final BonePart head;
    public final BonePart neckSpan;
    public final BonePart rearFootR;
    public final BonePart rearFootL;
    public final BonePart frontFootR;
    public final BonePart frontFootL;
    public final BonePart rearLegSpanR;
    public final BonePart rearLegSpanL;
    public final BonePart frontLegSpanR;
    public final BonePart frontLegSpanL;
    public final BonePart tailBase;
    public final BonePart tailTip;
    public final BonePart wingR;
    public final BonePart wingL;
    public final BonePart wingTipR;
    public final BonePart wingTipL;
    public final BonePart[] all;

    public Vec3 bodyOffset, headOffset, neckOffset, neck5Offset;
    public Vec3 wingROffset, wingLOffset, wingTipROffset, wingTipLOffset;
    public Vec3 rearFootROffset, rearFootLOffset, frontFootROffset, frontFootLOffset;
    public Vec3 rearLegROffset, rearLegLOffset, frontLegROffset, frontLegLOffset;
    public Vec3 tailOffset, tail6Offset, tail12Offset;
    public boolean hasClientPivots = false;

    public PartRig(EnderDragon dragon) {
      // Solid (Sable / physical entity collision)
      this.body          = new BonePart(dragon, "body",            1.50f,  1.50f, true);
      this.neckSpan      = new BonePart(dragon, "neck_span",       0.625f, 0.625f, true);
      this.tailBase      = new BonePart(dragon, "tail_base",       0.625f, 0.625f, true);
      this.rearFootR     = new BonePart(dragon, "rearfoot",        1.125f, 0.375f, true);
      this.rearFootL     = new BonePart(dragon, "rearfoot1",       1.125f, 0.375f, true);
      this.frontFootR    = new BonePart(dragon, "frontfoot",       0.50f,  0.25f,  true);
      this.frontFootL    = new BonePart(dragon, "frontfoot1",      0.50f,  0.25f,  true);
      this.rearLegSpanR  = new BonePart(dragon, "rearleg_span_r",  1.00f,  1.00f, true);
      this.rearLegSpanL  = new BonePart(dragon, "rearleg_span_l",  1.00f,  1.00f, true);
      this.frontLegSpanR = new BonePart(dragon, "frontleg_span_r", 0.50f,  0.50f, true);
      this.frontLegSpanL = new BonePart(dragon, "frontleg_span_l", 0.50f,  0.50f, true);

      // Pass-through (vanilla EnderDragonPart behavior: hittable via isPickable(), walkable through)
      this.head          = new BonePart(dragon, "head",            1.00f,  1.00f, false);
      this.tailTip       = new BonePart(dragon, "tail_tip",        0.625f, 0.625f, false);
      this.wingR         = new BonePart(dragon, "wing_r",          3.50f,  0.65f, false);
      this.wingL         = new BonePart(dragon, "wing_l",          3.50f,  0.65f, false);
      this.wingTipR      = new BonePart(dragon, "wingtip_r",       3.50f,  0.55f, false);
      this.wingTipL      = new BonePart(dragon, "wingtip_l",       3.50f,  0.55f, false);

      this.all = new BonePart[]{
        this.body, this.head, this.neckSpan,
        this.wingR, this.wingL, this.wingTipR, this.wingTipL,
        this.rearFootR, this.rearFootL, this.frontFootR, this.frontFootL,
        this.rearLegSpanR, this.rearLegSpanL, this.frontLegSpanR, this.frontLegSpanL,
        this.tailBase, this.tailTip
    };

    }
  }

  private static final Map<UUID, PartRig> CLIENT_RIGS = new ConcurrentHashMap<>();
  private static final Map<UUID, PartRig> SERVER_RIGS = new ConcurrentHashMap<>();

  private static final ThreadLocal<Boolean> IN_PART_HURT = ThreadLocal.withInitial(() -> false);

  public static boolean isInPartHurt() {
    return IN_PART_HURT.get();
  }


  private DragonMultipartCollider() {}

  public static PartRig getOrCreateRig(EnderDragon dragon) {
    Map<UUID, PartRig> map = dragon.level().isClientSide() ? CLIENT_RIGS : SERVER_RIGS;
    PartRig rig = map.get(dragon.getUUID());
    // Rebuild if missing OR if the dragon entity / dimension instance was recreated after dimension change / respawn
    if (rig == null || rig.all[0].getParent() != dragon || rig.all[0].level() != dragon.level()) {
      rig = new PartRig(dragon);
      map.put(dragon.getUUID(), rig);
    }
    int baseId = dragon.getId();
    if (baseId > 0 && rig.all[0].getId() != baseId + 1) {
      for (int i = 0; rig.all.length > i; i++) {
        rig.all[i].setId(baseId + i + 1);
      }
    }
    return rig;
  }

  public static void removeRig(UUID id) {
    CLIENT_RIGS.remove(id);
    SERVER_RIGS.remove(id);
  }

  public static void captureRenderedWorldBones(EnderDragon dragon, software.bernie.geckolib.cache.object.BakedGeoModel baked) {
    if (!dragon.level().isClientSide()) return;
    PartRig rig = getOrCreateRig(dragon);
    Vec3 origin = dragon.position();

    Vec3 b   = boneRelWorld(baked, "body", origin);
    Vec3 h   = boneRelWorld(baked, "head", origin);
    Vec3 n0  = boneRelWorld(baked, "neck", origin);
    Vec3 n5  = boneRelWorld(baked, "neck5", origin);

    Vec3 wR  = boneRelWorld(baked, "wing", origin);
    Vec3 wL  = boneRelWorld(baked, "wing1", origin);
    Vec3 wtR = boneRelWorld(baked, "wingtip", origin);
    Vec3 wtL = boneRelWorld(baked, "wingtip1", origin);

    Vec3 rlU = boneRelWorld(baked, "rearleg", origin);
    Vec3 llU = boneRelWorld(baked, "rearleg1", origin);
    Vec3 flU = boneRelWorld(baked, "frontleg", origin);
    Vec3 frU = boneRelWorld(baked, "frontleg1", origin);

    Vec3 rr  = boneRelWorld(baked, "rearfoot", origin);
    Vec3 rl  = boneRelWorld(baked, "rearfoot1", origin);
    Vec3 fr  = boneRelWorld(baked, "frontfoot", origin);
    Vec3 fl  = boneRelWorld(baked, "frontfoot1", origin);

    Vec3 t0  = boneRelWorld(baked, "tail", origin);
    Vec3 t6  = boneRelWorld(baked, "tail6", origin);
    Vec3 t12 = boneRelWorld(baked, "tail12", origin);

    if (b != null && h != null && n0 != null && n5 != null && t0 != null && t6 != null && t12 != null) {
      // Shift from each bone's pivot to the center of its drawn geo.json mesh along the live bone axes
      // body pivot is at [0, 20, 8] while torso center is [0, 8, 24] -> 1.0 block backward (-bodyDir) & 0.75 blocks down (-bodyUp)
      Vec3 bodyFwd = n0.subtract(t0);
      Vec3 bodyDir = (bodyFwd.lengthSqr() > 0.001D) ? bodyFwd.normalize() : Vec3.ZERO;
      Vec3 bodyUp = new Vec3(0.0D, 1.0D, 0.0D);
      if (rlU != null && llU != null) {
        Vec3 bodyRight = rlU.subtract(llU);
        if (bodyRight.lengthSqr() > 0.001D && bodyDir.lengthSqr() > 0.001D) {
          bodyUp = bodyRight.normalize().cross(bodyDir).normalize();
        }
      }
      rig.bodyOffset = b.add(bodyDir.scale(-1.00D)).add(bodyUp.scale(-0.75D));

      Vec3 tailWard = bodyDir.scale(-1.0D);
      if (wR != null && wtR != null) {
        Vec3 spanDirR = wtR.subtract(wR);
        Vec3 outR = (spanDirR.lengthSqr() > 0.001D) ? spanDirR.normalize() : Vec3.ZERO;
        rig.wingROffset    = wR.add(outR.scale(1.75D)).add(tailWard.scale(1.625D));
        rig.wingTipROffset = wtR.add(outR.scale(1.75D)).add(tailWard.scale(1.75D));
      }
      if (wL != null && wtL != null) {
        Vec3 spanDirL = wtL.subtract(wL);
        Vec3 outL = (spanDirL.lengthSqr() > 0.001D) ? spanDirL.normalize() : Vec3.ZERO;
        rig.wingLOffset    = wL.add(outL.scale(1.75D)).add(tailWard.scale(1.625D));
        rig.wingTipLOffset = wtL.add(outL.scale(1.75D)).add(tailWard.scale(1.75D));
      }

      Vec3 headFwd = h.subtract(n5);
      Vec3 headDir = (headFwd.lengthSqr() > 0.001D) ? headFwd.normalize() : bodyDir;
      rig.headOffset = h.add(headDir.scale(0.85D));

      rig.neckOffset = n0;
      rig.neck5Offset = n5.add(headDir.scale(0.55D));

      rig.rearLegROffset = rlU;
      rig.rearLegLOffset = llU;
      rig.frontLegROffset = flU;
      rig.frontLegLOffset = frU;

      rig.rearFootROffset = (rr != null) ? rr.add(bodyDir.scale(0.45D)).add(0.0D, -0.18D, 0.0D) : null;
      rig.rearFootLOffset = (rl != null) ? rl.add(bodyDir.scale(0.45D)).add(0.0D, -0.18D, 0.0D) : null;
      rig.frontFootROffset = (fr != null) ? fr.add(bodyDir.scale(0.25D)).add(0.0D, -0.12D, 0.0D) : null;
      rig.frontFootLOffset = (fl != null) ? fl.add(bodyDir.scale(0.25D)).add(0.0D, -0.12D, 0.0D) : null;

      Vec3 tailDir = t12.subtract(t6);
      Vec3 tailTipDir = (tailDir.lengthSqr() > 0.001D) ? tailDir.normalize() : bodyDir.scale(-1.0D);
      rig.tailOffset = t0;
      rig.tail6Offset = t6;
      rig.tail12Offset = t12.add(tailTipDir.scale(0.625D));
      rig.hasClientPivots = true;
    }
  }

  private static Vec3 boneRelWorld(software.bernie.geckolib.cache.object.BakedGeoModel baked, String boneName, Vec3 origin) {
    GeoBone bone = baked.getBone(boneName).orElse(null);
    if (bone == null) return null;
    org.joml.Vector3d wp = bone.getWorldPosition();
    if (wp == null || (wp.x == 0.0D && wp.y == 0.0D && wp.z == 0.0D)) return null;
    return new Vec3(wp.x - origin.x, wp.y - origin.y, wp.z - origin.z);
  }



  public static void syncHitboxes(EnderDragon dragon, IMhEnderDragon mh) {
    if (!dragon.isAlive()) {
      removeRig(dragon.getUUID());
      return;
    }

    PartRig rig = getOrCreateRig(dragon);
    ensurePartsRegistered(dragon.level(), rig);
    Vec3 origin = dragon.position();
    float bodyYaw = dragon.yBodyRot;

    Vec3 bodyWorld, headWorld, n0World, n5World;
    Vec3 wingRWorld, wingLWorld, wingTipRWorld, wingTipLWorld;
    Vec3 rrWorld, rlWorld, frWorld, flWorld;
    Vec3 rLegRWorld, rLegLWorld, fLegRWorld, fLegLWorld;
    Vec3 t0World, t6World, t12World;

    if (dragon.level().isClientSide() && rig.hasClientPivots) {
      bodyWorld     = origin.add(rig.bodyOffset);
      headWorld     = origin.add(rig.headOffset);
      n0World       = origin.add(rig.neckOffset);
      n5World       = origin.add(rig.neck5Offset);
      wingRWorld    = (rig.wingROffset != null) ? origin.add(rig.wingROffset) : origin;
      wingLWorld    = (rig.wingLOffset != null) ? origin.add(rig.wingLOffset) : origin;
      wingTipRWorld = (rig.wingTipROffset != null) ? origin.add(rig.wingTipROffset) : origin;
      wingTipLWorld = (rig.wingTipLOffset != null) ? origin.add(rig.wingTipLOffset) : origin;
      rrWorld       = (rig.rearFootROffset != null) ? origin.add(rig.rearFootROffset) : origin;
      rlWorld       = (rig.rearFootLOffset != null) ? origin.add(rig.rearFootLOffset) : origin;
      frWorld       = (rig.frontFootROffset != null) ? origin.add(rig.frontFootROffset) : origin;
      flWorld       = (rig.frontFootLOffset != null) ? origin.add(rig.frontFootLOffset) : origin;
      rLegRWorld    = (rig.rearLegROffset != null) ? origin.add(rig.rearLegROffset) : null;
      rLegLWorld    = (rig.rearLegLOffset != null) ? origin.add(rig.rearLegLOffset) : null;
      fLegRWorld    = (rig.frontLegROffset != null) ? origin.add(rig.frontLegROffset) : null;
      fLegLWorld    = (rig.frontLegLOffset != null) ? origin.add(rig.frontLegLOffset) : null;
      t0World       = origin.add(rig.tailOffset);
      t6World       = origin.add(rig.tail6Offset);
      t12World      = origin.add(rig.tail12Offset);
    } else {
      DragonAnimState.DragonPoseMode mode = mh.hexacreatecore$getPoseMode();
      DragonAnimState.DragonAction action = mh.hexacreatecore$getActiveAction();
      int actionTick = mh.hexacreatecore$getActionTick();

      boolean onGround = (mode == DragonAnimState.DragonPoseMode.GROUND_IDLE
      || mode == DragonAnimState.DragonPoseMode.GROUND_WALK);
      boolean isPerching = (mode == DragonAnimState.DragonPoseMode.PILLAR_PERCH);
      double liftY = onGround ? 1.84D : (isPerching ? 0.84D : 0.0D);
      double shiftZ = isPerching ? 1.50D : 0.0D;

      bodyWorld     = modelOffsetToWorld(origin, new Vec3(0.0D, liftY + 0.50D, shiftZ + 1.50D), bodyYaw);
      n0World       = modelOffsetToWorld(origin, new Vec3(0.0D, liftY + 0.44D, shiftZ - 0.50D), bodyYaw);
      wingRWorld    = modelOffsetToWorld(origin, new Vec3(-2.50D, liftY + 1.19D, shiftZ + 1.75D), bodyYaw);
      wingLWorld    = modelOffsetToWorld(origin, new Vec3( 2.50D, liftY + 1.19D, shiftZ + 1.75D), bodyYaw);
      wingTipRWorld = modelOffsetToWorld(origin, new Vec3(-6.00D, liftY + 1.19D, shiftZ + 1.875D), bodyYaw);
      wingTipLWorld = modelOffsetToWorld(origin, new Vec3( 6.00D, liftY + 1.19D, shiftZ + 1.875D), bodyYaw);

      if (action == DragonAnimState.DragonAction.DOUBLE_CLAW) {
        if (actionTick >= 15 && 25 >= actionTick) {
          float t = Mth.clamp((actionTick - 15.0f) / 10.0f, 0.0f, 1.0f);
          double xWing = Mth.lerp(t, -2.6D,  1.2D);
          double zWing = Mth.lerp(t, -2.5D, -4.5D);
          double xTip  = Mth.lerp(t, -4.5D,  2.2D);
          double zTip  = Mth.lerp(t, -4.5D, -8.2D);
          wingRWorld    = modelOffsetToWorld(origin, new Vec3(xWing, liftY + 0.85D, zWing), bodyYaw);
          wingTipRWorld = modelOffsetToWorld(origin, new Vec3(xTip,  liftY + 0.45D, zTip), bodyYaw);
        } else if (actionTick >= 41 && 52 >= actionTick) {
          float t = Mth.clamp((actionTick - 41.0f) / 11.0f, 0.0f, 1.0f);
          double xWing = Mth.lerp(t,  2.6D, -1.2D);
          double zWing = Mth.lerp(t, -2.5D, -4.5D);
          double xTip  = Mth.lerp(t,  4.5D, -2.2D);
          double zTip  = Mth.lerp(t, -4.5D, -8.2D);
          wingLWorld    = modelOffsetToWorld(origin, new Vec3(xWing, liftY + 0.85D, zWing), bodyYaw);
          wingTipLWorld = modelOffsetToWorld(origin, new Vec3(xTip,  liftY + 0.45D, zTip), bodyYaw);
        }
      }

      // Rotate neck & head along the side-bite arc during turn_left_90 / turn_right_90
      float headSweepYaw = bodyYaw;
      double neckYOffset = liftY + 0.44D;
      if (action == DragonAnimState.DragonAction.TURN_LEFT_90 || action == DragonAnimState.DragonAction.CLAW_LEFT
      || action == DragonAnimState.DragonAction.TURN_RIGHT_90 || action == DragonAnimState.DragonAction.CLAW_RIGHT) {
        boolean isLeft = (action == DragonAnimState.DragonAction.TURN_LEFT_90 || action == DragonAnimState.DragonAction.CLAW_LEFT);
        float sign = isLeft ? -1.0f : 1.0f;
        float sweepProg = Mth.clamp((actionTick - 10.0f) / 6.0f, 0.0f, 1.0f);
        // Sweeps across the flank bite arc right through 0.50s of the animation (ticks 11..16)
        float relAngleDeg = sign * Mth.lerp(sweepProg, 75.0f, -15.0f);
        headSweepYaw = bodyYaw + relAngleDeg;
        neckYOffset = liftY + 0.15D;
      }

      n5World    = modelOffsetToWorld(origin, new Vec3(0.0D, neckYOffset, shiftZ - 3.63D), headSweepYaw);
      headWorld  = modelOffsetToWorld(origin, new Vec3(0.0D, neckYOffset, shiftZ - 4.75D), headSweepYaw);

      double footY = (onGround || isPerching) ? 0.19D : (liftY - 0.90D);
      frWorld    = modelOffsetToWorld(origin, new Vec3(-0.75D, footY, shiftZ - 0.125D), bodyYaw);
      flWorld    = modelOffsetToWorld(origin, new Vec3( 0.75D, footY, shiftZ - 0.125D), bodyYaw);
      rrWorld    = modelOffsetToWorld(origin, new Vec3(-1.00D, footY, shiftZ + 2.06D), bodyYaw);
      rlWorld    = modelOffsetToWorld(origin, new Vec3( 1.00D, footY, shiftZ + 2.06D), bodyYaw);

      rLegRWorld = modelOffsetToWorld(origin, new Vec3(-1.00D, liftY + 0.50D, shiftZ + 2.625D), bodyYaw);
      rLegLWorld = modelOffsetToWorld(origin, new Vec3( 1.00D, liftY + 0.50D, shiftZ + 2.625D), bodyYaw);
      fLegRWorld = modelOffsetToWorld(origin, new Vec3(-0.75D, liftY + 0.25D, shiftZ + 0.125D), bodyYaw);
      fLegLWorld = modelOffsetToWorld(origin, new Vec3( 0.75D, liftY + 0.25D, shiftZ + 0.125D), bodyYaw);

      t0World    = modelOffsetToWorld(origin, new Vec3(0.0D, liftY + 0.875D, shiftZ + 3.50D), bodyYaw);
      t6World    = modelOffsetToWorld(origin, new Vec3(0.0D, liftY + 0.875D, shiftZ + 6.625D), bodyYaw);
      t12World   = modelOffsetToWorld(origin, new Vec3(0.0D, liftY + 0.875D, shiftZ + 11.00D), bodyYaw);
    }

    syncOrientedBox(rig.body,     bodyWorld,     1.50f, 1.50f, 4.00f, bodyYaw);
    syncOrientedBox(rig.head,     headWorld,     1.00f, 1.00f, 1.875f, bodyYaw);
    syncOrientedBox(rig.wingR,    wingRWorld,    3.50f, 0.65f, 3.75f, bodyYaw);
    syncOrientedBox(rig.wingL,    wingLWorld,    3.50f, 0.65f, 3.75f, bodyYaw);
    syncOrientedBox(rig.wingTipR, wingTipRWorld, 3.50f, 0.55f, 3.75f, bodyYaw);
    syncOrientedBox(rig.wingTipL, wingTipLWorld, 3.50f, 0.55f, 3.75f, bodyYaw);

    syncOrientedBox(rig.rearFootR,  rrWorld, 1.125f, 0.375f, 1.50f, bodyYaw);
    syncOrientedBox(rig.rearFootL,  rlWorld, 1.125f, 0.375f, 1.50f, bodyYaw);
    syncOrientedBox(rig.frontFootR, frWorld, 0.50f,  0.25f,  1.00f, bodyYaw);
    syncOrientedBox(rig.frontFootL, flWorld, 0.50f,  0.25f,  1.00f, bodyYaw);

    syncSpanBox(rig.neckSpan, n0World, n5World, 0.625f);

    if (rLegRWorld != null) {
      syncLimbSpanBox(rig.rearLegSpanR, rLegRWorld, rrWorld.add(0.0D, 0.19D, 0.0D), 0.85f);
    }
    if (rLegLWorld != null) {
      syncLimbSpanBox(rig.rearLegSpanL, rLegLWorld, rlWorld.add(0.0D, 0.19D, 0.0D), 0.85f);
    }
    if (fLegRWorld != null) {
      syncLimbSpanBox(rig.frontLegSpanR, fLegRWorld, frWorld.add(0.0D, 0.13D, 0.0D), 0.50f);
    }
    if (fLegLWorld != null) {
      syncLimbSpanBox(rig.frontLegSpanL, fLegLWorld, flWorld.add(0.0D, 0.13D, 0.0D), 0.50f);
    }

    syncSpanBox(rig.tailBase, t0World, t6World, 0.625f);
    syncSpanBox(rig.tailTip,  t6World, t12World, 0.625f);
  }

  private static Vec3 boneModelPos(MhEnderDragonModel<?> model, String boneName) {
    GeoBone bone = model.getBone(boneName).orElse(null);
    if (bone == null) return null;
    org.joml.Vector3d mp = bone.getModelPosition();
    if (mp == null || (mp.x == 0.0D && mp.y == 0.0D && mp.z == 0.0D)) return null;
    return new Vec3(mp.x, mp.y, mp.z);
  }


  private static void syncOrientedBox(BonePart part, Vec3 center, float localWidth, float height, float localLength, float bodyYaw) {
    float rad = (180.0f - bodyYaw) * Mth.DEG_TO_RAD;
    float absCos = Math.abs(Mth.cos(rad));
    float absSin = Math.abs(Mth.sin(rad));
    float sizeX = (localWidth * absCos) + (localLength * absSin);
    float sizeZ = (localWidth * absSin) + (localLength * absCos);
    part.syncBox(center, sizeX, height, sizeZ);
  }

  private static void syncSpanBox(BonePart part, Vec3 a, Vec3 b, float thickness) {
    Vec3 mid = a.lerp(b, 0.5D);
    float sizeX = (float) Math.abs(b.x - a.x) + thickness;
    float sizeY = (float) Math.abs(b.y - a.y) + thickness;
    float sizeZ = (float) Math.abs(b.z - a.z) + thickness;
    part.syncBox(mid, sizeX, sizeY, sizeZ);
  }

  private static Vec3 modelOffsetToWorld(Vec3 origin, Vec3 modelOffset, float bodyYaw) {
    float rad = (180.0f - bodyYaw) * Mth.DEG_TO_RAD;
    double cos = Math.cos(rad);
    double sin = Math.sin(rad);
    double wx = (modelOffset.x * cos) + (modelOffset.z * sin);
    double wz = -(modelOffset.x * sin) + (modelOffset.z * cos);
    return new Vec3(origin.x + wx, origin.y + modelOffset.y, origin.z + wz);
  }


  @SuppressWarnings("unchecked")
  private static void ensurePartsRegistered(net.minecraft.world.level.Level level, PartRig rig) {
    if (level == null) return;
    try {
      it.unimi.dsi.fastutil.ints.Int2ObjectMap partMap = null;
      for (java.lang.reflect.Field f : net.minecraft.world.level.Level.class.getDeclaredFields()) {
        if (it.unimi.dsi.fastutil.ints.Int2ObjectMap.class.isAssignableFrom(f.getType())) {
          f.setAccessible(true);
          Object val = f.get(level);
          if (val instanceof it.unimi.dsi.fastutil.ints.Int2ObjectMap<?> map) {
            var cast = (it.unimi.dsi.fastutil.ints.Int2ObjectMap) map;
            partMap = cast;
            break;
          }
        }
      }
      if (partMap == null) {
        for (java.lang.reflect.Field f : level.getClass().getDeclaredFields()) {
          if (it.unimi.dsi.fastutil.ints.Int2ObjectMap.class.isAssignableFrom(f.getType())) {
            f.setAccessible(true);
            Object val = f.get(level);
            if (val instanceof it.unimi.dsi.fastutil.ints.Int2ObjectMap<?> map) {
              @SuppressWarnings("unchecked")
              var cast = (it.unimi.dsi.fastutil.ints.Int2ObjectMap) map;
              partMap = cast;
              break;
            }
          }
        }
      }
      if (partMap != null) {
        for (BonePart part : rig.all) {
          if (partMap.get(part.getId()) != part) {
            partMap.put(part.getId(), part);
          }
        }
      }
    } catch (Throwable ignored) {}
  }

  private static void syncLimbSpanBox(BonePart part, Vec3 topSocket, Vec3 ankleTop, float horizThickness) {
    Vec3 mid = topSocket.lerp(ankleTop, 0.5D);
    float sizeX = (float) Math.abs(ankleTop.x - topSocket.x) + horizThickness;
    float sizeY = Math.max(0.25f, (float) Math.abs(ankleTop.y - topSocket.y));
    float sizeZ = (float) Math.abs(ankleTop.z - topSocket.z) + horizThickness;
    part.syncBox(mid, sizeX, sizeY, sizeZ);
  }


}
