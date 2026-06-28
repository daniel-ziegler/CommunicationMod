package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;

public class CardRewardScreenPatch {

    public static boolean doHover = false;
    public static AbstractCard hoverCard;
    // Watch mode: when true the hover is HELD (not cleared each frame and not auto-clicked), so a
    // human can see which card is about to be taken before it commits. Set by the "hover" command.
    public static boolean hold = false;

    @SpirePatch(
            clz=CardRewardScreen.class,
            method = "cardSelectUpdate"
    )
    public static class HoverCardPatch {

        @SpireInsertPatch(
                locator=Locator.class,
                localvars = {"c"}
        )
        public static void Insert(CardRewardScreen _instance, AbstractCard c) {
            if(doHover) {
                if(c.equals(hoverCard)) {
                    hoverCard.hb.hovered = true;
                    // Warp the real OS cursor onto the pick so the game's own hover renders and the
                    // cursor visibly moves to signal the choice. Hitbox cX/cY are bottom-left origin
                    // (in window pixels); Gdx cursor coords are top-left, hence the height flip.
                    com.badlogic.gdx.Gdx.input.setCursorPosition(
                            (int) hoverCard.hb.cX,
                            (int) (com.badlogic.gdx.Gdx.graphics.getHeight() - hoverCard.hb.cY));
                } else {
                    c.hb.hovered = false;
                }
            }
        }

        private static class Locator extends SpireInsertLocator {
            public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
                Matcher matcher = new Matcher.MethodCallMatcher(AbstractCard.class, "updateHoverLogic");
                int[] match = LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
                match[0] += 1;
                return match;
            }
        }

    }

    @SpirePatch(
            clz=CardRewardScreen.class,
            method = "cardSelectUpdate"
    )
    public static class AcquireCardPatch {

        @SpireInsertPatch(
                locator=Locator.class
        )
        public static void Insert(CardRewardScreen _instance) {
            if (!hold) {
                if (doHover) {
                    // Pick committed -> park the cursor at top-middle so it isn't left on the card.
                    com.badlogic.gdx.Gdx.input.setCursorPosition(com.badlogic.gdx.Gdx.graphics.getWidth() / 2, (int)(com.badlogic.gdx.Gdx.graphics.getHeight() * 0.15f));
                }
                doHover = false;
            }
        }

        private static class Locator extends SpireInsertLocator {
            public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
                Matcher matcher = new Matcher.FieldAccessMatcher(CardRewardScreen.class, "skipButton");
                return LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
            }
        }

    }
}
