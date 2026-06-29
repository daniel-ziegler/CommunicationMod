# Changes relative to upstream (ForgottenArbiter/CommunicationMod)

This fork extends CommunicationMod to drive Slay the Spire from a zero-delay
automated controller (the `sts_lightspeed` engine + its vendored `spirecomm`),
and adds a "watch mode" so a human can see what the controller is about to pick
before it commits. The changes fall into three groups.

## 1. Bugfixes

Genuine defects in the mod, mostly hidden by human pacing and only surfaced by a
controller that acts with no delay between commands.

- **Stale monster-turn guard** (`patches/StaleMonsterTurnGuardPatch.java`, new).
  A fast combat-end (e.g. an elite dying to Thorns/Flame Barrier during its own
  turn) can transition rooms with `monsterQueue` still populated; the leftover
  `takeTurn` then runs in a non-combat room where `getCurrRoom().monsters` is
  null — an NPE that crashes the whole game (observed: Gremlin Leader's rally in
  a RestRoom). The guard resets the monster-turn state machine
  (`monsterAttacksQueued=true`, `monsterQueue.clear()`, `turnHasEnded=false`)
  whenever `getNextAction` runs outside combat, short-circuiting every branch
  that dereferences the (null) monster group. In-combat behavior is untouched.

- **Pending card-obtain wait** (`GameStateListener.java`).
  `ShowCardAndObtainEffect` (event/Neow card grants) adds the card to the master
  deck partway through its animation. A no-delay controller transitions rooms
  first and the grant is silently dropped — even though any cost (e.g. -50% max
  HP) already applied. Readiness is now gated until the effect drains, capped at
  `OBTAIN_WAIT_CAP` (240 frames) so a stuck queue can't deadlock.

- **Event wait-timer cap** (`GameStateListener.java`).
  A stuck `event.waitTimer` (seen on Bonfire Spirits' Offer) pinned readiness
  false forever (-> hang). The gate is now bounded by `EVENT_WAIT_CAP` (240
  frames); past the cap it forces a state change so the pending choice can fire.

- **Reliable shop relic/potion purchase** (`ChoiceScreenUtils.makeShopScreenChoice`).
  `relic.hb.clicked = true` only registers if the item happens to be hovered on
  the right frame — flaky, can silently no-op and make the bridge re-issue the
  same buy forever. Switched to the public `purchaseRelic()` / `purchasePotion()`,
  which carry their own gold guards.

- **Surrounded back-attack facing** (`CommandExecutor.executePlayCommand`).
  The `cardQueue` play path skips the manual-targeting flip that real
  `AbstractPlayer.playCard` performs, so while Surrounded (act-4 Spire Shield +
  Spear) the 1.5x back-attack never tracked the chosen target. Now mirrors the
  game: `flipHorizontal = target.drawX < player.drawX` when Surrounded.

## 2. Generic enhancements

- **`hover` command + watch-cursor system**
  (`CommandExecutor.executeHoverCommand`, `ChoiceScreenUtils.hoverChoice` /
  `hoverProceed` / `hoverLeave`, `patches/WatchCursorPatch.java` (new), plus
  `hold` flags and cursor warp/park in `CardRewardScreenPatch`, `ShopScreenPatch`,
  `AbstractRelicUpdatePatch`).
  A self-contained "watch mode": hold-hover a pending choice (no click) and warp
  the real OS cursor onto it so a human can see what the controller will pick
  before it commits, then park the cursor afterward. Covers card reward, boss
  relic, shop card/relic/potion/removal, campfire, map node, combat-reward items,
  grid, events/Neow, and the proceed/leave buttons. Built against stock
  CommunicationMod choice lists with no engine-specific semantics. A committed
  action clears any active generic hover and parks the cursor.

- **maven-shade-plugin 2.4.2 → 3.2.4** (`pom.xml`). Build-toolchain bump.

## 3. Adaptations for `sts_lightspeed`

State-export additions whose only purpose is to feed (and verify) the
`sts_lightspeed` engine's `BattleContext` reconstruction; dead weight for a stock
client.

- **Per-card `base_damage` / `damage`** (`GameStateConverter.convertCardToJson`).
  Consumed by the vendored `spirecomm/spire/card.py` and `comm.py`'s damage
  divergence oracle to validate a reconstruction against the live game.

- **`cards_played_this_turn` / `attacks_played_this_turn` /
  `skills_played_this_turn`** (`GameStateConverter` combat-state block).
  Read by `spirecomm/spire/game.py` and assigned into
  `bc.player.cardsPlayedThisTurn` / `attacksPlayedThisTurn` / `skillsPlayedThisTurn`
  when rebuilding the C++ `BattleContext`.
