package com.genesiz.hexacreatecore.animations;

import java.util.Optional;

public record AnimationSequenceData(
    String sequenceId,
    Optional<String> preAnim,
    Optional<String> loopAnim,
    Optional<String> postAnim,
    boolean waitForCompletion
) {}
