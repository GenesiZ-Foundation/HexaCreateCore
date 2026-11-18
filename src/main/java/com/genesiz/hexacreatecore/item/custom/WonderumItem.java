package com.genesiz.hexacreatecore.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;
import java.util.List;

public class WonderumItem extends Item {

  public WonderumItem(Properties properties) {
    super(properties.stacksTo(1).rarity(Rarity.EPIC).fireResistant());
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    Level level = context.getLevel();
    BlockPos pos = context.getClickedPos();
    BlockState state = level.getBlockState(pos);
    Player player = context.getPlayer();
    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());

    // 1. Sprint + Right-Click: Kinetic Impulse Strike (jumpstarts or boosts shafts, cogs, flywheels, etc.)
    if (player != null && player.isSprinting() && blockId != null && blockId.getNamespace().equals("create")) {
      if (!level.isClientSide && tryKineticImpulse(level.getBlockEntity(pos))) {
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 1.6f);
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // 2. Create Wrench Behavior (onWrenched / onSneakWrenched via Create's IWrenchable contract)
    if (tryInvokeCreateWrench(state.getBlock(), context, player != null && player.isShiftKeyDown())) {
      return InteractionResult.sidedSuccess(level.isClientSide);
    }

    return super.useOn(context);
  }

  private boolean tryPressDepotItem(ServerLevel level, BlockPos pos, Player player) {
    BlockEntity be = level.getBlockEntity(pos);
    if (be == null) return false;
    try {
      // 1. Fetch DepotBehaviour via SmartBlockEntity#getBehaviour(BehaviourType)
      Class<?> behaviourTypeClass = Class.forName("com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType");
      Class<?> depotBehaviourClass = Class.forName("com.simibubi.create.content.logistics.depot.DepotBehaviour");
      Object depotType = depotBehaviourClass.getField("TYPE").get(null);
      Object depotBehaviour = be.getClass().getMethod("getBehaviour", behaviourTypeClass).invoke(be, depotType);
      if (depotBehaviour == null) return false;

      // 2. Read the held ItemStack on the Depot
      ItemStack held = (ItemStack) depotBehaviourClass.getMethod("getHeldItemStack").invoke(depotBehaviour);
      if (held == null || held.isEmpty()) return false;

      // 3. Look up Create's PressingRecipe (supports both standard PressingRecipe and SequencedAssemblyRecipe!)
      Class<?> allRecipeTypesClass = Class.forName("com.simibubi.create.AllRecipeTypes");
      Object pressingEnum = Enum.valueOf((Class<Enum>) allRecipeTypesClass, "PRESSING");
      Method findMethod = allRecipeTypesClass.getMethod("find", net.minecraft.world.item.crafting.RecipeInput.class, Level.class);

      SingleRecipeInput input = new SingleRecipeInput(held.copyWithCount(1));
      @SuppressWarnings("unchecked")
      var recipeOpt = (java.util.Optional<RecipeHolder<?>>) findMethod.invoke(pressingEnum, input, level);

      if (recipeOpt.isEmpty()) {
        // Also check SequencedAssemblyRecipe if applicable
        Class<?> seqClass = Class.forName("com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe");
        Class<?> pressingRecipeClass = Class.forName("com.simibubi.create.content.kinetics.press.PressingRecipe");
        net.minecraft.world.item.crafting.RecipeType<?> rType =
          (net.minecraft.world.item.crafting.RecipeType<?>) allRecipeTypesClass.getMethod("getType").invoke(pressingEnum);
        @SuppressWarnings("unchecked")
        var seqOpt = (java.util.Optional<RecipeHolder<?>>) seqClass.getMethod("getRecipe", Level.class, ItemStack.class, net.minecraft.world.item.crafting.RecipeType.class, Class.class)
          .invoke(null, level, held.copyWithCount(1), rType, pressingRecipeClass);
        recipeOpt = seqOpt;
      }
      if (recipeOpt.isEmpty()) return false;

      // 4. Apply the PressingRecipe via Create's RecipeApplier (handles rollable outputs + sequenced assembly)
      Class<?> recipeApplierClass = Class.forName("com.simibubi.create.foundation.recipe.RecipeApplier");
      @SuppressWarnings("unchecked")
      List<ItemStack> outputs = (List<ItemStack>) recipeApplierClass
        .getMethod("applyRecipeOn", Level.class, ItemStack.class, net.minecraft.world.item.crafting.Recipe.class, boolean.class)
        .invoke(null, level, held.copyWithCount(1), recipeOpt.get().value(), true);
      if (outputs.isEmpty()) return false;

      // 5. Shrink 1 input item and insert pressed output(s) into Depot's processingOutputBuffer
      held.shrink(1);
      if (held.isEmpty()) {
        depotBehaviourClass.getMethod("removeHeldItem").invoke(depotBehaviour);
      }

      var outputBuffer = (net.neoforged.neoforge.items.IItemHandler) depotBehaviourClass.getField("processingOutputBuffer").get(depotBehaviour);
      for (ItemStack out : outputs) {
        if (out.isEmpty()) continue;
        ItemStack remainder = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(outputBuffer, out, false);
        if (!remainder.isEmpty() && (player == null || !player.getInventory().add(remainder))) {
          Block.popResource(level, pos.above(), remainder);
        }
      }

      // 6. Sync DepotBlockEntity to client immediately
      be.getClass().getMethod("notifyUpdate").invoke(be);
      return true;
    } catch (Throwable t) {
      t.printStackTrace();
      return false;
    }
  }

  private boolean tryInvokeCreateWrench(Block block, UseOnContext context, boolean sneaking) {
    try {
      String methodName = sneaking ? "onSneakWrenched" : "onWrenched";
      for (Method m : block.getClass().getMethods()) {
        if (m.getName().equals(methodName) && m.getParameterCount() == 2) {
          Object res = m.invoke(block, context.getLevel().getBlockState(context.getClickedPos()), context);
          return res instanceof InteractionResult ir && ir.consumesAction();
        }
      }
    } catch (Throwable ignored) {}
    return false;
  }

  private boolean tryKineticImpulse(BlockEntity be) {
    if (be == null) return false;
    try {
      Method setSpeed = be.getClass().getMethod("setSpeed", float.class);
      Method getSpeed = be.getClass().getMethod("getSpeed");
      float current = (float) getSpeed.invoke(be);
      setSpeed.invoke(be, Math.abs(current) < 32.0f ? 64.0f : current * 1.25f);
      be.setChanged();
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
    tooltipComponents.add(Component.literal("Left-Click Depot: Manual Mechanical Press (1 item)"));
    tooltipComponents.add(Component.literal("Right-Click / Sprint + Right-Click: Create Wrench & Kinetic Impulse"));
    tooltipComponents.add(Component.literal("Sneak + Scroll / Right-Click: Select & Anchor Hologram"));
    tooltipComponents.add(Component.literal("Sneak + Right-Click Kinetic Block: Link Gravitational Wormhole"));
    tooltipComponents.add(Component.empty());
    tooltipComponents.add(
      Component.literal("One of the concepts of the world, myths said that it could forge space and motion alike, engraved along the haft is \"The Creator's anvil\"")
    );
  }
}

