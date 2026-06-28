package communicationmod.patches;

import com.badlogic.gdx.Gdx;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;

/**
 * Watch-mode generic cursor warp. The screen-specific patches (card reward / shop card / boss relic)
 * drive their own hover; this covers everything else that has no hover patch -- shop relics/potions/
 * the card-removal service, and event option buttons (incl. Neow) -- by warping the OS cursor to a
 * target point every frame while active, so the game's own hover renders and the cursor visibly
 * travels to the pending pick. The "hover" command sets the target; the committed pick clears it.
 */
public class WatchCursorPatch {

    public static boolean active = false;
    private static float targetX = 0.0f;   // render coords (bottom-left origin), as used by Hitbox.cX/cY
    private static float targetY = 0.0f;

    public static void hoverHitbox(Hitbox hb) {
        if (hb == null) {
            active = false;
            return;
        }
        targetX = hb.cX;
        targetY = hb.cY;
        active = true;
    }

    public static void hoverPoint(float x, float y) {
        targetX = x;
        targetY = y;
        active = true;
    }

    /** Stop warping and park the cursor at horizontal center, 15% down from the top. */
    public static void clearAndPark() {
        active = false;
        Gdx.input.setCursorPosition(Gdx.graphics.getWidth() / 2, (int) (Gdx.graphics.getHeight() * 0.15f));
    }

    @SpirePatch(
            clz = AbstractDungeon.class,
            method = "update"
    )
    public static class WarpPatch {
        @SpirePostfixPatch
        public static void Postfix() {
            if (active) {
                // Hitbox cY is bottom-left origin; Gdx cursor y is top-left, hence the height flip.
                Gdx.input.setCursorPosition((int) targetX, (int) (Gdx.graphics.getHeight() - targetY));
            }
        }
    }
}
