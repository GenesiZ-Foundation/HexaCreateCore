package com.genesiz.hexacreatecore.dialogue.network;

import com.genesiz.hexacreatecore.hexcasting.HexagonalGridManager;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DialogueClientPackets {

  /**
   * Unloads the incoming server stack string data directly into your active 3D HUD arrays.
   */
  public static void handleStackSyncOnClient(final DialogueStackSyncPayload payload, final IPayloadContext context) {
    context.enqueueWork(() -> {
      try {
        // Find our active text collections via reflectively matching fields or clear public helpers
        java.lang.reflect.Field stringsField = HexagonalGridManager.class.getDeclaredField("STACK_DISPLAY_STRINGS");
        stringsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<String> activeList = (java.util.List<String>) stringsField.get(null);

        activeList.clear();
        activeList.addAll(payload.stackDescs());

        java.lang.reflect.Field mindField = HexagonalGridManager.class.getDeclaredField("ravensmindString");
        mindField.setAccessible(true);
        mindField.set(null, payload.ravensmind().isEmpty() ? null : payload.ravensmind());
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }
}
