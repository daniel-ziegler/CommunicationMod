package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

/**
 * Guards against a stale monster turn running outside combat.
 *
 * A fast combat-end (e.g. an elite dying reactively to Thorns/Flame Barrier during its own turn)
 * can transition rooms while GameActionManager's monster-turn machinery is mid-flight, leaving
 * monsterQueue populated. In the next, non-combat room the leftover monster's takeTurn then runs
 * where AbstractDungeon.getCurrRoom().monsters is null -- a NullPointerException that crashes the
 * whole game (observed: Gremlin Leader's rally move firing in a RestRoom). This is normally hidden
 * by human pacing draining the queue first; a no-delay controller exposes it.
 *
 * Monsters only ever act in a combat room, so any leftover monster queue elsewhere is stale -- drop
 * it before getNextAction can process it. In combat the queue is untouched and is repopulated by
 * MonsterGroup.queueMonsters() at the start of each monster turn.
 */
@SpirePatch(
        clz = GameActionManager.class,
        method = "getNextAction"
)
public class StaleMonsterTurnGuardPatch {

    @SpirePrefixPatch
    public static void Prefix(GameActionManager __instance) {
        AbstractRoom room = AbstractDungeon.getCurrRoom();
        if (room == null || room.phase != AbstractRoom.RoomPhase.COMBAT) {
            // Reset the whole monster-turn state machine, not just the queue: getNextAction has
            // several branches (queue monsters / take turns / apply end-of-turn powers) gated on
            // monsterAttacksQueued / monsterQueue / turnHasEnded, and the later ones deref
            // AbstractDungeon.getMonsters() -- null outside combat -> NPE. Forcing the "no monster
            // turn pending" state makes all of them short-circuit while the normal action queue
            // (campfire choices etc.) still processes. Combat re-initialises these at its start.
            __instance.turnHasEnded = false;
            __instance.monsterAttacksQueued = true;
            if (!__instance.monsterQueue.isEmpty()) {
                __instance.monsterQueue.clear();
            }
        }
    }
}
