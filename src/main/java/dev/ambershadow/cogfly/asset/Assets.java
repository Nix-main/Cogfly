package dev.ambershadow.cogfly.asset;

import com.formdev.flatlaf.FlatLaf;
import dev.ambershadow.cogfly.Cogfly;
public class Assets {
    private static CogflyAsset getAsset(String path) {
        return new CogflyAsset(Cogfly.getResource("/assets/" + path));
    }

    public static final CogflyAsset icon = getAsset("icon.png");
    public static final CogflyAsset openSaves = getAsset("openSaves.png");
    public static final CogflyAsset centralIcon = getAsset("cogfly_art.png");
    private static final CogflyAsset discord = getAsset("Discord-Symbol-White.svg");
    private static final CogflyAsset github = getAsset("GitHub_Invertocat_White.svg");
    private static final CogflyAsset patreon = getAsset("PATREON_SYMBOL_1_WHITE_RGB.svg");
    public static final CogflyAsset[] linkIcons = {discord, github, patreon};
    public static final CogflyAsset silksongIcon = getAsset("silksong64x64.png");
    private static final CogflyAsset play = getAsset("profile/play.svg");
    private static final CogflyAsset edit = getAsset("profile/edit.svg");
    private static final CogflyAsset copy = getAsset("profile/copy.svg");
    private static final CogflyAsset delete = getAsset("profile/delete.svg");
    private static final CogflyAsset play_dark = getAsset("profile/play_dark.svg");
    private static final CogflyAsset edit_dark = getAsset("profile/edit_dark.svg");
    private static final CogflyAsset copy_dark = getAsset("profile/copy_dark.svg");
    private static final CogflyAsset delete_dark = getAsset("profile/delete_dark.svg");
    public static CogflyAsset[] getProfileIcons(){
        if (FlatLaf.isLafDark()){
            return new CogflyAsset[]{play, edit, copy, delete};
        } else {
            return new CogflyAsset[]{play_dark, edit_dark, copy_dark, delete_dark};
        }
    }
}
