package communicationmod;

import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.rooms.VictoryRoom;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

import java.util.ArrayList;

public class GameStateListener {
    private static AbstractDungeon.CurrentScreen previousScreen = null;
    private static boolean previousScreenUp = false;
    private static AbstractRoom.RoomPhase previousPhase = null;
    private static boolean previousGridSelectConfirmUp = false;
    private static int previousGold = 99;
    private static boolean externalChange = false;
    private static boolean myTurn = false;
    private static boolean blocked = false;
    private static boolean waitingForCommand = false;
    private static boolean hasPresentedOutOfGameState = false;
    private static boolean waitOneUpdate = false;
    private static int timeout = 0;
    // Frames spent waiting for a pending card-obtain animation to drain. Bounded by OBTAIN_WAIT_CAP so
    // the readiness gate can never block forever, even in the (effectively impossible, since the effect
    // is time-driven) case where the real progress gate sits behind it.
    private static int obtainWaitFrames = 0;
    private static final int OBTAIN_WAIT_CAP = 240;  // ~4s at 60fps; an obtain animation resolves in ~1s

    /**
     * Used to indicate that something (in game logic, not external command) has been done that will change the game state,
     * and hasStateChanged() should indicate a state change when the state next becomes stable.
     */
    public static void registerStateChange() {
        externalChange = true;
        waitingForCommand = false;
    }

    /**
     * Used to tell hasStateChanged() to indicate a state change after a specified number of frames.
     * @param newTimeout The number of frames to wait
     */
    public static void setTimeout(int newTimeout) {
        timeout = newTimeout;
    }

    /**
     * Used to indicate that an external command has been executed
     */
    public static void registerCommandExecution() {
        waitingForCommand = false;
    }

    /**
     * Prevents hasStateChanged() from indicating a state change until resumeStateUpdate() is called.
     */
    public static void blockStateUpdate() {
        blocked = true;
    }

    /**
     * Removes the block instantiated by blockStateChanged()
     */
    public static void resumeStateUpdate() {
        blocked = false;
    }

    /**
     * Used by a patch in the game to signal the start of your turn. We do not care about state changes
     * when it is not our turn in combat, as we cannot take action until then.
     */
    public static void signalTurnStart() {
        myTurn = true;
    }

    /**
     * Used by patches in the game to signal the end of your turn (or the end of combat).
     */
    public static void signalTurnEnd() {
        myTurn = false;
    }

    /**
     * Resets all state detection variables for the start of a new run.
     */
    public static void resetStateVariables() {
        previousScreen = null;
        previousScreenUp = false;
        previousPhase = null;
        previousGridSelectConfirmUp = false;
        previousGold = 99;
        externalChange = false;
        myTurn = false;
        blocked = false;
        waitingForCommand = false;
        waitOneUpdate = false;
    }

    /**
     * Detects whether the game state is stable and we are ready to receive a command from the user.
     *
     * @return whether the state is stable
     */
    private static boolean hasDungeonStateChanged() {
        if (blocked) {
            return false;
        }
        hasPresentedOutOfGameState = false;
        AbstractDungeon.CurrentScreen newScreen = AbstractDungeon.screen;
        boolean newScreenUp = AbstractDungeon.isScreenUp;
        AbstractRoom.RoomPhase newPhase = AbstractDungeon.getCurrRoom().phase;
        boolean inCombat = (newPhase == AbstractRoom.RoomPhase.COMBAT);
        // Lots of stuff can happen while the dungeon is fading out, but nothing that requires input from the user.
        if (AbstractDungeon.isFadingOut || AbstractDungeon.isFadingIn) {
            return false;
        }
        // This check happens before the rest since dying can happen in combat and messes with the other cases.
        if (newScreen == AbstractDungeon.CurrentScreen.DEATH && newScreen != previousScreen) {
            return true;
        }
        // A card-obtain animation (ShowCardAndObtainEffect, used by events like Council of Ghosts and by
        // Neow) adds the card to the master deck partway through its update, not when it is queued. The
        // game is otherwise "ready" the instant the option is chosen, so a bot that acts with no delay
        // transitions rooms and the still-pending obtain effects are dropped -- the deck never gains the
        // card even though any cost (e.g. the -50% max HP) already applied. Stay un-ready until they drain,
        // but only up to OBTAIN_WAIT_CAP frames so a stuck queue can never deadlock the readiness gate.
        if (hasPendingCardObtainEffect()) {
            if (obtainWaitFrames < OBTAIN_WAIT_CAP) {
                obtainWaitFrames++;
                return false;
            }
        } else {
            obtainWaitFrames = 0;
        }
        // These screens have no interaction available.
        if (newScreen == AbstractDungeon.CurrentScreen.DOOR_UNLOCK || newScreen == AbstractDungeon.CurrentScreen.NO_INTERACT) {
            return false;
        }
        // We are not ready to receive commands when it is not our turn, except for some pesky screens
        if (inCombat && (!myTurn || AbstractDungeon.getMonsters().areMonstersBasicallyDead())) {
            if (!newScreenUp) {
                return false;
            }
        }
        // In event rooms, we need to wait for the event wait timer to reach 0 before we can accurately assess its state.
        AbstractRoom currentRoom = AbstractDungeon.getCurrRoom();
        if ((currentRoom instanceof EventRoom
                || currentRoom instanceof NeowRoom
                || (currentRoom instanceof VictoryRoom && ((VictoryRoom) currentRoom).eType == VictoryRoom.EventType.HEART))
                && AbstractDungeon.getCurrRoom().event.waitTimer != 0.0F) {
            return false;
        }
        // The state has always changed in some way when one of these variables is different.
        // However, the state may not be finished changing, so we need to do some additional checks.
        if (newScreen != previousScreen || newScreenUp != previousScreenUp || newPhase != previousPhase) {
            if (inCombat) {
                // In combat, newScreenUp being true indicates an action that requires our immediate attention.
                if (newScreenUp) {
                    return true;
                }
                // In combat, if no screen is up, we should wait for all actions to complete before indicating a state change.
                else if (AbstractDungeon.actionManager.phase.equals(GameActionManager.Phase.WAITING_ON_USER)
                        && AbstractDungeon.actionManager.cardQueue.isEmpty()
                        && AbstractDungeon.actionManager.actions.isEmpty()) {
                    return true;
                }

            // Out of combat, we want to wait one update cycle, as some screen transitions trigger further updates.
            } else {
                waitOneUpdate = true;
                previousScreenUp = newScreenUp;
                previousScreen = newScreen;
                previousPhase = newPhase;
                return false;
            }
        } else if (waitOneUpdate) {
            waitOneUpdate = false;
            return true;
        }
        // We are assuming that commands are only being submitted through our interface. Some actions that require
        // our attention, like retaining a card, occur after the end turn is queued, but the previous cases
        // cover those actions. We would like to avoid registering other state changes after the end turn
        // command but before the game actually ends your turn.
        if (inCombat && AbstractDungeon.player.endTurnQueued) {
            return false;
        }
        // If some other code registered a state change through registerStateChange(), or if we notice a state
        // change through the gold amount changing, we still need to wait until all actions are finished
        // resolving to claim a stable state and ask for a new command.
        if ((externalChange || previousGold != AbstractDungeon.player.gold)
                && AbstractDungeon.actionManager.phase.equals(GameActionManager.Phase.WAITING_ON_USER)
                && AbstractDungeon.actionManager.preTurnActions.isEmpty()
                && AbstractDungeon.actionManager.actions.isEmpty()
                && AbstractDungeon.actionManager.cardQueue.isEmpty()) {
            return true;
        }
        // In a grid select screen, if a confirm screen comes up or goes away, it doesn't change any other state.
        if (newScreen == AbstractDungeon.CurrentScreen.GRID) {
            boolean newGridSelectConfirmUp = AbstractDungeon.gridSelectScreen.confirmScreenUp;
            if (previousScreen == AbstractDungeon.CurrentScreen.GRID && newGridSelectConfirmUp != previousGridSelectConfirmUp) {
                return true;
            }
        }
        // Sometimes, we need to register an external change in combat while an action is resolving which brings
        // the screen up. Because the screen did not change, this is not covered by other cases.
        if (externalChange && inCombat && newScreenUp) {
            return true;
        }
        if (timeout > 0) {
            timeout -= 1;
            if(timeout == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects whether the state of the game menu has changed. Right now, this only occurs when you first enter the
     * menu, either after starting Slay the Spire for the first time, or after ending a game and returning to the menu.
     *
     * @return Whether the main menu has just been entered.
     */
    public static boolean checkForMenuStateChange() {
        boolean stateChange = false;
        if (!hasPresentedOutOfGameState && CardCrawlGame.mode == CardCrawlGame.GameMode.CHAR_SELECT && CardCrawlGame.mainMenuScreen != null) {
            stateChange = true;
            hasPresentedOutOfGameState = true;
        }
        if (stateChange) {
            externalChange = false;
            waitingForCommand = true;
        }
        return stateChange;
    }

    /**
     * Detects a state change in AbstractDungeon, and updates all of the local variables used to detect
     * changes in the dungeon state. Sets waitingForCommand = true if a state change was registered since
     * the last command was sent.
     *
     * @return Whether a dungeon state change was detected
     */
    public static boolean checkForDungeonStateChange() {
        boolean stateChange = false;
        if (CommandExecutor.isInDungeon()) {
            stateChange = hasDungeonStateChanged();
            if (stateChange) {
                externalChange = false;
                waitingForCommand = true;
                previousPhase = AbstractDungeon.getCurrRoom().phase;
                previousScreen = AbstractDungeon.screen;
                previousScreenUp = AbstractDungeon.isScreenUp;
                previousGold = AbstractDungeon.player.gold;
                previousGridSelectConfirmUp = AbstractDungeon.gridSelectScreen.confirmScreenUp;
                timeout = 0;
            }
        } else {
            myTurn = false;
        }
        return stateChange;
    }

    public static boolean isWaitingForCommand() {
        return waitingForCommand;
    }

    /**
     * Whether a card is still mid-obtain in any of the dungeon effect queues. ShowCardAndObtainEffect
     * (event/Neow card grants) only adds the card to the master deck during its update, so reporting a
     * stable state before it finishes lets a fast client skip the obtain on the next room transition.
     */
    private static boolean hasPendingCardObtainEffect() {
        return queueHasObtainEffect(AbstractDungeon.effectList)
                || queueHasObtainEffect(AbstractDungeon.topLevelEffects)
                || queueHasObtainEffect(AbstractDungeon.topLevelEffectsQueue)
                || queueHasObtainEffect(AbstractDungeon.effectsQueue);
    }

    private static boolean queueHasObtainEffect(ArrayList<AbstractGameEffect> effects) {
        if (effects == null) {
            return false;
        }
        // Index loop (not for-each) to avoid a ConcurrentModificationException if the effect list is
        // mutated during iteration; a transient miss is harmless (re-checked every dungeon update).
        for (int i = 0; i < effects.size(); i++) {
            if (effects.get(i) instanceof ShowCardAndObtainEffect) {
                return true;
            }
        }
        return false;
    }
}