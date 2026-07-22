package dev.ambershadow.cogfly.util;

import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import com.formdev.flatlaf.intellijthemes.FlatNordIJTheme;
import com.google.gson.*;
import dev.ambershadow.cogfly.Cogfly;
import net.harawata.appdirs.AppDirsFactory;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Settings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] STATIC_PATHS = new String[]
    {
            "Program Files/Steam/steamapps/common/Hollow Knight Silksong",
            "XboxGames/Hollow Knight Silksong/Content",
            "XboxGames/Hollow Knight- Silksong/Content",
            "Program Files (x86)/Steam/steamapps/common/Hollow Knight Silksong",
            "Program Files/GOG Galaxy/Games/Hollow Knight Silksong",
            "Program Files (x86)/GOG Galaxy/Games/Hollow Knight Silksong",
            "Steam/steamapps/common/Hollow Knight Silksong",
            "GOG Galaxy/Games/Hollow Knight Silksong",
    };

    private static JsonObject getData(Path dataJson){
        String content;
        try(InputStream stream = Files.newInputStream(dataJson)) {
            content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (!content.isEmpty()) {
            JsonElement element = JsonParser.parseString(content);
            if (element != null)
                return element.getAsJsonObject();
        }
        return null;
    }
    public static Settings load(Path file) {
        Settings settings = null;
        if (Files.exists(file))
            settings = GSON.fromJson(getData(file), Settings.class);
        if (settings == null)
            settings = new Settings();
        settings.dataFile = file;
        settings.save();

        for (FlatAllIJThemes.FlatIJLookAndFeelInfo info : FlatAllIJThemes.INFOS) {
            if (info.getClassName().equals(settings.theme)) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return settings;
    }


    public String theme = FlatNordIJTheme.class.getName();
    public String gamePath = findDefaultPath();
    public String profileSavePath = Cogfly.roamingDataPath.resolve("profiles").toString();
    public List<String> profileSources = new ArrayList<>();
    public boolean baseGameEnabled = false;
    public boolean modNameSpaces = true;
    public int scrollingIncrement = 16;
    public boolean useRelativeTime = false;
    public boolean profileSpecificPaths = false;
    public boolean showInstalledModsOnTop = false;
    public boolean launchWithSteam = false;
    public boolean dontShowPatreonAgain = false;
    public boolean finishedSteamPopup = false;
    public boolean acceptedSteamArgs = false;
    public int profileButtonSize = 15;

    private Settings(){}
    private transient Path dataFile;
    public JsonObject getData() {
        return getData(dataFile);
    }
    private String findDefaultPath(){
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            for (String path : STATIC_PATHS) {
                Path combined = root.resolve(path);
                if (Files.isDirectory(combined)) {
                    return combined.toAbsolutePath().toString();
                }
            }
        }
        if (Cogfly.isMac()){
            String path = AppDirsFactory.getInstance().getUserDataDir
                    ("Steam", null, "Steam")
                    + "/steamapps/common/Hollow Knight Silksong/";
            return Files.isDirectory(Paths.get(path)) ? path : "";
        }
        if (Cogfly.isLinux()){
            String path = System.getProperty("user.home") + "/.local/share/Steam/steamapps/common/Hollow Knight Silksong/";
            return Files.isDirectory(Paths.get(path)) ? path : "";
        }
        return "";
    }

    public void save(){
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(this, writer);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public int hashCode(){
        return GSON.toJsonTree(this).hashCode();
    }

    @Override
    public boolean equals(Object other){
        return other instanceof Settings && GSON.toJsonTree(other).equals(GSON.toJsonTree(this));
    }
}
