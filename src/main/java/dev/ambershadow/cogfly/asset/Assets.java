package dev.ambershadow.cogfly.asset;

import dev.ambershadow.cogfly.Cogfly;
public class Assets {
    private static CogflyAsset getAsset(String path) {
        return new CogflyAsset(Cogfly.getResource("/assets/" + path));
    }

    public static CogflyAsset icon = getAsset("icon.png");
    public static CogflyAsset openSaves = getAsset("openSaves.png");
    public static CogflyAsset centralIcon = getAsset("cogfly_art.png");
    private static CogflyAsset discord = getAsset("Discord-Symbol-White.svg");
    private static CogflyAsset github = getAsset("GitHub_Invertocat_White.svg");
    private static CogflyAsset patreon = getAsset("PATREON_SYMBOL_1_WHITE_RGB.svg");
    public static CogflyAsset[] linkIcons = {discord, github, patreon};
    public static CogflyAsset silksongIcon = getAsset("silksong64x64.png");
}
