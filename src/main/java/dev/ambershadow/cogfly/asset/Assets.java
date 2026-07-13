package dev.ambershadow.cogfly.asset;

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
}
