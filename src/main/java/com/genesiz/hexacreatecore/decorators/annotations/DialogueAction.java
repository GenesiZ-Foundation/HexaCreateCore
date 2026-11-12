package com.genesiz.hexacreatecore.decorators.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DialogueAction {
  /** The full action ID, e.g. "hexacreatecore:start_forging" */
  String value();

  /** Set to true for player-only actions that skip the 6-block NPC reach check */
  boolean selfTargeted() default false;
}
