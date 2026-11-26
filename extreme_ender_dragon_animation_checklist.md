# Extreme Ender Dragon — Animation Checklist

## Currently Ready / Mapped

### Direct Spec Matches & Replacements
- `neutral_ground` -> Phase 2 ground idle (`neutral_ground`)
- `ground_walk` -> Phase 2 quadruped walk (`ground_walk`)
- `turn_left_90` & `turn_right_90` -> 90° pivot turns **and** 90° claw attacks (`claw_left` / `claw_right`)
- `end_roar` -> Phase 2 entrance roar (`safi_roar`)
- `double_wing_hit` -> Replaces `double_claw`
- `shield_stomp` -> Replaces `forward_stomp`

### Other Existing Animations (Useful Across Phases 1–3)
- `neutral_fly`
- `air_move`
- `wings_tucked`
- `mouth_open`
- `mouth_closed`
- `rear_up`
- `fireball_volley`
- `lift_off_ground`
- `landing`
- `pre_charge_flight`
- `pre_charging_nova_1`
- `charging_nova_1`
- `end_charging_nova_1`

---

## Still Needed

### Phase 2 — Grounded Melee & Knockback
- `wing_flap_forward` (~1.10s, gust at 0.45s) — Quick forward wing buffet pushing close players back
- `wing_flap_left` (~1.10s, gust at 0.45s) — Quick left wing sweep knocking flanking players away
- `wing_flap_right` (~1.10s, gust at 0.45s) — Quick right wing sweep knocking flanking players away
- `bite_quick` (~0.95s, snap at 0.40s) — Fast close-range jaw snap straight ahead (head IK aims at player)
- `bite_lunge` (~1.40s, snap at 0.65s) — Telegraphed neck coil followed by heavier forward lunging bite
- `back_leg_left` (~1.15s, kick at 0.50s) — Safi'jiiva-style backward left hind-leg kick/swipe
- `back_leg_right` (~1.15s, kick at 0.50s) — Safi'jiiva-style backward right hind-leg kick/swipe
- `tail_swipe_lr` (~1.50s, sweep 0.50s–1.10s) — Left-to-right tail whip across the rear arc
- `tail_swipe_rl` (~1.50s, sweep 0.50s–1.10s) — Right-to-left tail whip across the rear arc

### Phase 2 — Grounded Ranged, Breath & Takeoff
- `ground_fireball_short` (~1.60s, shots at 0.50s / 0.70s / 0.90s) — Raises head and sweeps a short volley of fireballs across the front cone
- `fireball_carpet` (~2.40s, takeoff at 0.45s, volley 0.75s–1.75s) — Flies backward into the air while carpet-bombing previous ground spot (leaves airborne)
- `breath_swipe_90` (~2.50s, active 0.55s–2.05s) — Long-range fire breath swept across a 90° front arc
- `flying_breath_45` (~2.40s, takeoff at 0.45s, breath 0.75s–1.95s) — Flies backward into the air and breathes fire onto previous spot angled at 45° (leaves airborne)

### Phase 2 — Airborne Follow-Ups (Return to Ground)
- `air_charged_fireball` (~1.80s, release at 1.10s) — Hovers, gathers energy in jaws, and fires a 2× radius / 2× damage fireball
- `air_dash` (~1.80s, dive at 0.55s, skid 1.00s–1.55s) — Aim windup, diagonal dive-dash into the ground through player, and ground skid (returns grounded)
- `air_stomp` (~1.50s, impact at 0.90s) — Brief hover followed by violent vertical body/claw slam (returns grounded, Sable ragdoll + knockback)

### Phase 1 & Phase 3 — Additional States to Define / Build
- **Phase 1:** Pillar-latch fireball volley and mid-air stop-and-dive charge (if separate from `fireball_volley` / `pre_charge_flight`)
- **Phase 3 Nova Transition:** Ascent (80 blocks above arena), 5s cone breath -> 10s growing laser (3 to 15 blocks wide) -> 2s shrink to 1-block star -> supernova / blackhole collapse
- **Phase 3 Flight Attacks:** Precision lasers (3x), high-speed player dash, island-edge bite, end-portal meteors, spectral dash from blackhole, multidirectional laser (20+ beams), island laser swipe, ledge wing push
- **Phase 3 Thresholds & Desperation:** 20% HP and 7% HP island-fire nova animations, plus 1% HP desperation sequence (upward fireballs, dual 360° laser sweeps, blackhole collapse with star lasers, and final breath/laser)