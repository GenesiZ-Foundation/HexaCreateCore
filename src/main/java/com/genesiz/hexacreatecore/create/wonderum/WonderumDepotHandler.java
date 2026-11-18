package com.genesiz.hexacreatecore.create.wonderum;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class WonderumDepotHandler {

  private WonderumDepotHandler() {}

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void onLeftClickDepot(PlayerInteractEvent.LeftClickBlock event) {
    if (!event.getItemStack().is(ModItems.WONDERUM.get())) return;
    Level level = event.getLevel();
    BlockPos pos = event.getPos();
    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
    if (blockId == null || !blockId.getNamespace().equals("create") || !blockId.getPath().contains("depot")) return;

    // Cancel block-breaking & Create's normal left-click item extraction
    event.setCanceled(true);

    if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
      if (pressDepotOnce(serverLevel, pos, event.getEntity())) {
        serverLevel.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.55f, 1.35f);
      }
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static boolean pressDepotOnce(ServerLevel level, BlockPos pos, Player player) {
    BlockEntity be = level.getBlockEntity(pos);
    if (be == null) return false;
    try {
      Class<?> behaviourTypeClass = Class.forName("com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType");
      Class<?> depotBehaviourClass = Class.forName("com.simibubi.create.content.logistics.depot.DepotBehaviour");
      Object depotType = depotBehaviourClass.getField("TYPE").get(null);
      Object depotBehaviour = be.getClass().getMethod("getBehaviour", behaviourTypeClass).invoke(be, depotType);
      if (depotBehaviour == null) return false;

      ItemStack held = (ItemStack) depotBehaviourClass.getMethod("getHeldItemStack").invoke(depotBehaviour);
      if (held == null || held.isEmpty()) return false;

      ItemStack singleInput = held.copyWithCount(1);
      Class<?> allRecipeTypesClass = Class.forName("com.simibubi.create.AllRecipeTypes");
      Object pressingEnum = Enum.valueOf((Class<Enum>) allRecipeTypesClass, "PRESSING");
      Method findMethod = allRecipeTypesClass.getMethod("find", RecipeInput.class, Level.class);

      Optional<RecipeHolder<?>> recipeOpt = (Optional<RecipeHolder<?>>) findMethod.invoke(pressingEnum, new SingleRecipeInput(singleInput), level);
      if (recipeOpt.isEmpty()) {
        Class<?> seqClass = Class.forName("com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe");
        Class<?> pressingRecipeClass = Class.forName("com.simibubi.create.content.kinetics.press.PressingRecipe");
        RecipeType<?> rType = (RecipeType<?>) allRecipeTypesClass.getMethod("getType").invoke(pressingEnum);
        recipeOpt = (Optional<RecipeHolder<?>>) seqClass.getMethod("getRecipe", Level.class, ItemStack.class, RecipeType.class, Class.class)
          .invoke(null, level, singleInput, rType, pressingRecipeClass);
      }
      if (recipeOpt.isEmpty()) return false;

      Class<?> recipeApplierClass = Class.forName("com.simibubi.create.foundation.recipe.RecipeApplier");
      List<ItemStack> outputs = (List<ItemStack>) recipeApplierClass
        .getMethod("applyRecipeOn", Level.class, ItemStack.class, Recipe.class, boolean.class)
        .invoke(null, level, singleInput, recipeOpt.get().value(), true);
      if (outputs.isEmpty()) return false;

      // Spawn forge impact particles
      level.sendParticles(
        new ItemParticleOption(ParticleTypes.ITEM, singleInput),
        pos.getX() + 0.5D, pos.getY() + 0.92D, pos.getZ() + 0.5D,
        12, 0.14D, 0.08D, 0.14D, 0.06D
      );

      // Consume 1 input item
      held.shrink(1);
      boolean heldNowEmpty = held.isEmpty();
      if (heldNowEmpty) {
        depotBehaviourClass.getMethod("removeHeldItem").invoke(depotBehaviour);
      }

      // Access package-private processingOutputBuffer & TransportedItemStack safely
      var bufferField = depotBehaviourClass.getDeclaredField("processingOutputBuffer");
      bufferField.setAccessible(true);
      IItemHandler outputBuffer = (IItemHandler) bufferField.get(depotBehaviour);

      Class<?> transportedClass = Class.forName("com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack");
      Method setCenteredMethod = depotBehaviourClass.getMethod("setCenteredHeldItem", transportedClass);

      for (ItemStack out : outputs) {
        if (out.isEmpty()) continue;
        if (heldNowEmpty) {
          Object transportedOut = transportedClass.getConstructor(ItemStack.class).newInstance(out);
          setCenteredMethod.invoke(depotBehaviour, transportedOut);
          heldNowEmpty = false;
        } else {
          ItemStack remainder = ItemHandlerHelper.insertItemStacked(outputBuffer, out, false);
          if (!remainder.isEmpty() && (player == null || !player.getInventory().add(remainder))) {
            Block.popResource(level, pos.above(), remainder);
          }
        }
      }

      be.getClass().getMethod("notifyUpdate").invoke(be);
      return true;
    } catch (Throwable t) {
      t.printStackTrace();
      return false;
    }
  }
}
