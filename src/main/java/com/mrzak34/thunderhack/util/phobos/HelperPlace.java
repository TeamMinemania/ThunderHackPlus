package com.mrzak34.thunderhack.util.phobos;

import com.mrzak34.thunderhack.Thunderhack;
import com.mrzak34.thunderhack.command.Command;
import com.mrzak34.thunderhack.modules.combat.AutoCrystal;
import com.mrzak34.thunderhack.setting.Setting;
import com.mrzak34.thunderhack.util.BlockUtils;
import com.mrzak34.thunderhack.util.EntityUtil;
import com.mrzak34.thunderhack.util.InventoryUtil;
import com.mrzak34.thunderhack.util.MathUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Set;

import static com.mrzak34.thunderhack.util.Util.mc;

public class HelperPlace
{
   // private static final SettingCache<Float, Setting<Float>, Safety> MD = Caches.getSetting(Safety.class, NumberSetting.class, "MaxDamage", 4.0f);

    private final AutoCrystal module;

    public HelperPlace(AutoCrystal module)
    {
        this.module = module;
    }

    public PlaceData getData(List<EntityPlayer> general,
                             List<EntityPlayer> players,
                             List<EntityPlayer> enemies,
                             List<EntityPlayer> friends,
                             List<Entity> entities,
                             float minDamage,
                             Set<BlockPos> blackList,
                             double maxY)
    {
        PlaceData data = new PlaceData(minDamage);
        EntityPlayer target = module.isSuicideModule() ? mc.player : module.getTTRG(players, enemies, module.targetRange.getValue());

        if (target == null && module.targetMode.getValue() != AutoCrystal.Target.Damage)
        {
            return data;
        }

        data.setTarget(target);
        evaluate(data, general, friends, entities, blackList, maxY);
        data.addAllCorrespondingData();
        return data;
    }

    private void evaluate(PlaceData data, List<EntityPlayer> players, List<EntityPlayer> friends, List<Entity> entities, Set<BlockPos> blackList, double maxY)
    {
        boolean obby = module.obsidian.getValue()
                && module.obbyTimer.passedMs(module.obbyDelay.getValue())
                && (InventoryUtil.isHolding(Blocks.OBSIDIAN)
                || module.obbySwitch.getValue()
                && InventoryUtil.findHotbarBlock(Blocks.OBSIDIAN) != -1);

        switch (module.preCalc.getValue())
        {
            case Damage:
                for (EntityPlayer player : players)
                {
                    preCalc(data, player, obby, entities, friends, blackList);
                }
            case Target:
                if (data.getTarget() == null)
                {
                    if (data.getData().isEmpty())
                    {
                        break;
                    }
                }
                else
                {
                    preCalc(data, data.getTarget(),
                            obby, entities, friends, blackList);
                }

                for (PositionData positionData : data.getData())
                {
                    if (positionData.getMaxDamage()
                            > data.getMinDamage()
                            && positionData.getMaxDamage()
                            > module.preCalcDamage.getValue())
                    {
                        return;
                    }
                }

                break;
            default:
        }

        BlockPos middle = mc.player.getPosition();

        int maxRadius = Sphere.getRadius(module.placeRange.getValue());
        for (int i = 1; i < maxRadius; i++)
        {
            calc(middle.add(Sphere.get(i)), data, players, friends, entities, obby, blackList, maxY);
        }
    }

    private void preCalc(PlaceData data,
                         EntityPlayer player,
                         boolean obby,
                         List<Entity> entities,
                         List<EntityPlayer> friends,
                         Set<BlockPos> blackList)
    {
        MotionTracker extrapolationEntity;
        switch (module.preCalcExtra.getValue()) {
            case Place:
                extrapolationEntity = module.extrapol.getValue() == 0
                        ? null
                        : module.extrapolationHelper.getTrackerFromEntity(player);
                break;
            case Break:
                extrapolationEntity = module.bExtrapol.getValue() == 0
                        ? null
                        : module.extrapolationHelper.getBreakTrackerFromEntity(player);
                break;
            case Block:
                extrapolationEntity = module.blockExtrapol.getValue() == 0
                        ? null
                        : module.extrapolationHelper.getBlockTracker(player);
                break;
            default:
                extrapolationEntity = null;
                break;
        }

        BlockPos pos =
                extrapolationEntity == null || !extrapolationEntity.active
                        ? (player.getPosition()).down()
                        : (extrapolationEntity.getPosition()).down();

        for (EnumFacing facing : EnumFacing.HORIZONTALS)
        {
            PositionData pData = selfCalc(data, pos.offset(facing),
                    entities, friends, obby, blackList);
            if (pData == null)
            {
                continue;
            }

            checkPlayer(data, player, pData);
        }
    }

    private PositionData selfCalc(PlaceData placeData,
                                  BlockPos pos,
                                  List<Entity> entities,
                                  List<EntityPlayer> friends,
                                  boolean obby,
                                  Set<BlockPos> blackList)
    {
        if (blackList.contains(pos))
        {
            return null;
        }

        PositionData data = PositionData.create(
                pos,
                obby,
                module.rotate.getValue() != AutoCrystal.ACRotate.None
                        && module.rotate.getValue() != AutoCrystal.ACRotate.Break
                        ? 0 // TODO: ???
                        : module.helpingBlocks.getValue(),
                module.newVer.getValue(),
                module.newVerEntities.getValue(),
                module.getDeathTime(),
                entities,
                module.lava.getValue(),
                module.water.getValue(),
                module.ignoreLavaItems.getValue(), module);

        if (data.isBlocked() && !module.fallBack.getValue())
        {
            return null;
        }

        if (data.isLiquid())
        {
            if (!data.isLiquidValid()
                    // we wont be able to raytrace the
                    // 2 blocks on top if its above us
                    || module.liquidRayTrace.getValue()
                    && (module.newVer.getValue()
                    && data.getPos().getY()
                    >= mc.player.posY + 2
                    || !module.newVer.getValue()
                    && data.getPos().getY()
                    >= mc.player.posY + 1)
                    || BlockUtils.getDistanceSq(pos.up())
                    >= MathUtil.square(module.placeRange.getValue())
                    || BlockUtils.getDistanceSq(pos.up(2))
                    >= MathUtil.square(module.placeRange.getValue()))
            {
                return null;
            }

            if (data.usesObby())
            {
                if (data.isObbyValid())
                {
                    placeData.getLiquidObby().put(data.getPos(), data);
                }

                return null;
            }

            placeData.getLiquid().add(data);
            return null;
        }
        else if (data.usesObby())
        {
            if (data.isObbyValid())
            {
                placeData.getAllObbyData().put(data.getPos(), data);
            }

            return null;
        }

        if (!data.isValid())
        {
            return null;
        }

        return validate(placeData, data, friends);
    }

    public PositionData validate(PlaceData placeData, PositionData data,
                                 List<EntityPlayer> friends)
    {
        if (BlockUtils.getDistanceSq(data.getPos())
                >= MathUtil.square(module.placeTrace.getValue())
                && noPlaceTrace(data.getPos()))
        {
            if (module.rayTraceBypass.getValue()
                    && module.forceBypass.getValue()
                    && !data.isLiquid()
                    && !data.usesObby())
            {
                data.setRaytraceBypass(true);
            }
            else
            {
                return null;
            }
        }

        float selfDamage = module.damageHelper.getDamage(data.getPos());
        if (selfDamage > placeData.getHighestSelfDamage())
        {
            placeData.setHighestSelfDamage(selfDamage);
        }

        if (selfDamage > (mc.player.getHealth()) - 1.0)
        {
       //     if (!data.usesObby() && !data.isLiquid())
         //   {
         //       Managers.SAFETY.setSafe(false);
          //  }

            if (!module.suicide.getValue())
            {
                return null;
            }
        }

       // if (selfDamage > MD.getValue()
       //         && (!data.usesObby() && !data.isLiquid()))
      //  {
       //     Managers.SAFETY.setSafe(false);
       // }

        if (selfDamage > module.maxSelfPlace.getValue()
                && !module.override.getValue())
        {
            return null;
        }

        if (checkFriends(data, friends))
        {
            return null;
        }

        data.setSelfDamage(selfDamage);
        return data;
    }

    private boolean noPlaceTrace(BlockPos pos)
    {
        if (module.isNotCheckingRotations() || module.rayTraceBypass.getValue() && !Visible.INSTANCE.check(pos, module.bypassTicks.getValue()))
        {
            return false;
        }

        if (module.smartTrace.getValue())
        {
            for (EnumFacing facing : EnumFacing.values())
            {
                Ray ray = RayTraceFactory.rayTrace(
                        mc.player,
                        pos,
                        facing,
                        mc.world,
                        Blocks.OBSIDIAN.getDefaultState(),
                        module.traceWidth.getValue());
                if (ray.isLegit())
                {
                    return false;
                }
            }

            return true;
        }

        if (module.ignoreNonFull.getValue())
        {
            for (EnumFacing facing : EnumFacing.values())
            {
                Ray ray = RayTraceFactory.rayTrace(
                        mc.player,
                        pos,
                        facing,
                        mc.world,
                        Blocks.OBSIDIAN.getDefaultState(),
                        module.traceWidth.getValue());

                //noinspection deprecation
                if (!mc.world.getBlockState(ray.getResult().getBlockPos())
                        .getBlock()
                        .isFullBlock(mc.world.getBlockState(
                                ray.getResult().getBlockPos())))
                {
                    return false;
                }
            }
        }

        return !RayTraceUtil.raytracePlaceCheck(mc.player, pos);
    }

    private void calc(BlockPos pos,
                      PlaceData data,
                      List<EntityPlayer> players,
                      List<EntityPlayer> friends,
                      List<Entity> entities,
                      boolean obby,
                      Set<BlockPos> blackList,
                      double maxY)
    {
        if (placeCheck(pos, maxY)
                || (data.getTarget() != null
                && data.getTarget().getDistanceSq(pos)
                > MathUtil.square(module.range.getValue())))
        {
            return;
        }

        PositionData positionData = selfCalc(
                data, pos, entities, friends, obby, blackList);

        if (positionData == null)
        {
            return;
        }

        calcPositionData(data, positionData, players);
    }

    public void calcPositionData(PlaceData data, PositionData positionData, List<EntityPlayer> players)
    {

        boolean isAntiTotem = false;
        if (data.getTarget() == null)
        {
            for (EntityPlayer player : players)
            {
                isAntiTotem = checkPlayer(data, player, positionData)
                        || isAntiTotem;
            }
        }
        else
        {
            isAntiTotem = checkPlayer(data, data.getTarget(), positionData);
        }

        if (positionData.isRaytraceBypass()
                && (module.rayBypassFacePlace.getValue()
                && positionData.getFacePlacer() != null
                || positionData.getMaxDamage() > data.getMinDamage()))
        {
            data.getRaytraceData().add(positionData);
            return;
        }

        if (positionData.isForce())
        {
            ForcePosition forcePosition = new ForcePosition(positionData,module);
            for (EntityPlayer forced : positionData.getForced())
            {
                data.addForceData(forced, forcePosition);
            }
        }

        if (isAntiTotem)
        {
            data.addAntiTotem(new AntiTotemData(positionData, module));
        }

        if (positionData.getFacePlacer() != null || positionData.getMaxDamage() > data.getMinDamage())
        {
            data.getData().add(positionData);
        }
        else if (module.shield.getValue()
                && !positionData.usesObby()
                && !positionData.isLiquid()
                && positionData.isValid()
                && positionData.getSelfDamage()
                <= module.shieldSelfDamage.getValue())
        {
            if (module.shieldPrioritizeHealth.getValue())
            {
                positionData.setDamage(0.0f);
            }

            positionData.setTarget(data.getShieldPlayer());
            data.getShieldData().add(positionData);
        }
    }

    private boolean placeCheck(BlockPos pos, double maxY)
    {
        if (pos.getY() < 0
                || pos.getY() - 1 >= maxY
                || BlockUtils.getDistanceSq(pos)
                >= MathUtil.square(module.placeRange.getValue()))
        {
            return true;
        }

        if (module.isOutsideBreakRange(pos, module)
                || module.rangeHelper.isCrystalOutsideNegativeRange(pos)) {
            return true;
        }

        if (DistanceUtil.distanceSq(pos.getX() + 0.5f, pos.getY() + 1, pos.getZ() + 0.5f, mc.player)
                > MathUtil.square(module.pbTrace.getValue()))
        {
            return !RayTraceUtil.canBeSeen(
                    new Vec3d(pos.getX() + 0.5f,
                            pos.getY() + 1 + 1.7,
                            pos.getZ() + 0.5f),
                    mc.player);
        }

        return false;
    }

    private boolean checkFriends(PositionData data, List<EntityPlayer> friends)
    {
        if (!module.shouldCalcFuckinBitch(AutoCrystal.AntiFriendPop.Place))
        {
            return false;
        }

        for (EntityPlayer friend : friends)
        {
            if (friend != null
                    && !EntityUtil.isDead(friend)
                    && module.damageHelper.getDamage(data.getPos(), friend)
                    > (friend.getHealth()) - 0.5f)
            {
                return true;
            }
        }

        return false;
    }

    private boolean checkPlayer(PlaceData data,
                                EntityPlayer player,
                                PositionData positionData)
    {
        BlockPos pos = positionData.getPos();
        if (data.getTarget() == null
                && player.getDistanceSq(pos)
                > MathUtil.square(module.range.getValue()))
        {
            return false;
        }

        boolean result = false;
        float health = player.getHealth();
        float damage = module.damageHelper.getDamage(pos, player);
        if (module.antiTotem.getValue()
                && !positionData.usesObby()
                && !positionData.isLiquid()
                && !positionData.isRaytraceBypass())
        {
            if (module.antiTotemHelper.isDoublePoppable(player))
            {
                if (damage > module.popDamage.getValue())
                {
                    data.addCorrespondingData(player, positionData);
                }
                else if (damage < health + module.maxTotemOffset.getValue()
                        && damage > health + module.minTotemOffset.getValue())
                {
                    positionData.addAntiTotem(player);
                    result = true;
                }
            }
            else if (module.forceAntiTotem.getValue()
                    && Thunderhack.combatManager.lastPop(player) > 500)
            {
                if (damage > module.popDamage.getValue())
                {
                    data.confirmHighDamageForce(player);
                }

                if (damage > 0.0f
                        && damage < module.totemHealth.getValue()
                        + module.maxTotemOffset.getValue())
                {
                    data.confirmPossibleAntiTotem(player);
                }

                float force = health - damage;
                if (force > 0.0f && force < module.totemHealth.getValue())
                {
                    positionData.addForcePlayer(player);
                    if (force < positionData.getMinDiff())
                    {
                        positionData.setMinDiff(force);
                    }
                }
            }
        }

        if (damage > module.minFaceDmg.getValue())
        {
            if (health < module.facePlace.getValue() || ((IEntityLivingBase) player).getLowestDurability() <= module.armorPlace.getValue())
            {
                positionData.setFacePlacer(player);
            }
        }

        if (damage > positionData.getMaxDamage())
        {
            positionData.setDamage(damage);
            positionData.setTarget(player);
        }

        return result;
    }

}