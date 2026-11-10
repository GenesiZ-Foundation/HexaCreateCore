package com.genesiz.hexacreatecore.animations;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.*;

public final class AnimationSequenceReloadListener extends SimpleJsonResourceReloadListener {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Map<String, Map<String, AnimationSequenceData>> CACHE = new HashMap<>();
  private static final AnimationSequenceReloadListener INSTANCE = new AnimationSequenceReloadListener();

  private AnimationSequenceReloadListener() {
    super(GSON, "animation_sequences");
  }

  /**
   * Registers the asset reload listener safely onto the client distribution mod bus.
   */
  public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
    event.registerReloadListener(INSTANCE);
  }

  /**
   * Resolves loaded animation sequence parameters matching targeted type structures.
   */
  public static AnimationSequenceData getSequence(String entityType, String sequenceId) {
    Map<String, AnimationSequenceData> sequences = CACHE.get(entityType);
    return sequences != null ? sequences.get(sequenceId) : null;
  }

  @Override
  protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
    CACHE.clear();

    for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
      ResourceLocation fileLocation = entry.getKey();
      JsonElement element = entry.getValue();

      JsonObject root = GsonHelper.convertToJsonObject(element, "animation_sequence");
      String entityType = GsonHelper.getAsString(root, "affected_entity");

      JsonObject seqsObj = GsonHelper.getAsJsonObject(root, "sequences");
      Map<String, AnimationSequenceData> sequenceMap = new HashMap<>();

      for (Map.Entry<String, JsonElement> seqEntry : seqsObj.entrySet()) {
        String seqId = seqEntry.getKey();
        JsonObject body = GsonHelper.convertToJsonObject(seqEntry.getValue(), seqId);

        Optional<String> pre = body.has("pre") ? Optional.of(GsonHelper.getAsString(body, "pre")) : Optional.empty();
        Optional<String> loop = body.has("loop") ? Optional.of(GsonHelper.getAsString(body, "loop")) : Optional.empty();
        Optional<String> post = body.has("post") ? Optional.of(GsonHelper.getAsString(body, "post")) : Optional.empty();
        boolean wait = GsonHelper.getAsBoolean(body, "wait_for_completion", false);

        sequenceMap.put(seqId, new AnimationSequenceData(seqId, pre, loop, post, wait));
      }

      // Index globally across multiple string mutations to ensure lookup hits
      CACHE.put(entityType, sequenceMap);
      CACHE.put(fileLocation.toString(), sequenceMap);
      CACHE.put(fileLocation.getPath(), sequenceMap);
    }
  }
}
