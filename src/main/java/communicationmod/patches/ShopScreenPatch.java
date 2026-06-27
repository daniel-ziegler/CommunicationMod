package communicationmod.patches;

import basemod.ReflectionHacks;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.shop.ShopScreen;
import communicationmod.GameStateListener;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;

public class ShopScreenPatch {

    public static boolean doHover = false;
    public static AbstractCard hoverCard;
    // Watch mode: hold the hover (don't clear it each frame) so the bot's pending shop-card buy is
    // visible before it commits. Set by the "hover" command, cleared when the buy actually fires.
    public static boolean hold = false;


    @SpirePatch(
            clz = ShopScreen.class,
            method = "purgeCard"
    )
    public static class PurgeCardPatch {

        public static void Postfix() {
            GameStateListener.resumeStateUpdate();  // Needed to wait for the rest of the logic to complete after card was selected.
        }

    }


    @SpirePatch(
            clz=ShopScreen.class,
            method = "update"
    )
    public static class HoverCardPatch {

        @SuppressWarnings("unchecked")
        @SpireInsertPatch(
                locator=Locator.class
        )
        public static void Insert(ShopScreen _instance) {
            if(doHover) {
                ArrayList<AbstractCard> coloredCards = (ArrayList<AbstractCard>) ReflectionHacks.getPrivate(_instance, ShopScreen.class, "coloredCards");
                ArrayList<AbstractCard> colorlessCards = (ArrayList<AbstractCard>) ReflectionHacks.getPrivate(_instance, ShopScreen.class, "colorlessCards");
                for(AbstractCard card : coloredCards) {
                    card.hb.hovered = card == hoverCard;
                }
                for(AbstractCard card : colorlessCards) {
                    card.hb.hovered = card == hoverCard;
                }
                if (hoverCard != null) {
                    // Warp the real cursor onto the card the bot is about to buy so the game's own
                    // hover renders and the cursor visibly moves to it (same as the card-reward path).
                    com.badlogic.gdx.Gdx.input.setCursorPosition(
                            (int) hoverCard.hb.cX,
                            (int) (com.badlogic.gdx.Gdx.graphics.getHeight() - hoverCard.hb.cY));
                }
                if (!hold) {
                    doHover = false;
                }
            }
        }

        private static class Locator extends SpireInsertLocator {
            public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
                Matcher matcher = new Matcher.MethodCallMatcher(ShopScreen.class, "updateHand");
                return LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
            }
        }

    }

}
