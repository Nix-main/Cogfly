package dev.ambershadow.cogfly.util;

import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import com.formdev.flatlaf.intellijthemes.FlatNordIJTheme;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ambershadow.cogfly.Cogfly;
import net.harawata.appdirs.AppDirsFactory;

import javax.swing.*;
import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Settings {

    private static final String[] STATIC_PATHS = new String[]
    {
            "Program Files/Steam/steamapps/common/Hollow Knight Silksong",
            "XboxGames/Hollow Knight Silksong/Content",
            "Program Files (x86)/Steam/steamapps/common/Hollow Knight Silksong",
            "Program Files/GOG Galaxy/Games/Hollow Knight Silksong",
            "Program Files (x86)/GOG Galaxy/Games/Hollow Knight Silksong",
            "Steam/steamapps/common/Hollow Knight Silksong",
            "GOG Galaxy/Games/Hollow Knight Silksong",
    };

    public String theme = FlatNordIJTheme.class.getName();
    public String gamePath = findDefaultPath();
    public String profileSavePath = Paths.get(Cogfly.roamingDataPath + "/profiles/").toString();
    public List<String> profileSources = new ArrayList<>();
    public boolean baseGameEnabled = false;
    public boolean modNameSpaces = true;
    public int scrollingIncrement = 16;
    public boolean useRelativeTime = false;
    public boolean profileSpecificPaths = false;


    private final transient File dataJson;
    public Settings(File data){
        dataJson = data;
    }

    public JsonObject getData(){
        String content;
        try(FileReader reader = new FileReader(dataJson)) {
            content = new BufferedReader(reader).lines().collect(Collectors.joining("\n"));
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

    private String findDefaultPath(){
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            for (String path : STATIC_PATHS) {
                Path combined = root.resolve(path);
                if (Files.isDirectory(combined)) {
                    return combined.toAbsolutePath().toString();
                }
            }
        }
        if (Utils.OperatingSystem.current() == Utils.OperatingSystem.MAC){
            String path = AppDirsFactory.getInstance().getUserDataDir
                    ("Steam", null, "Steam")
                    + "/steamapps/common/Hollow Knight Silksong/";
            return Files.isDirectory(Paths.get(path)) ? path : "";
        }
        if (Utils.OperatingSystem.current() == Utils.OperatingSystem.LINUX){
            String path = System.getProperty("user.home") + "/.local/share/Steam/steamapps/common/Hollow Knight Silksong/";
            return Files.isDirectory(Paths.get(path)) ? path : "";
        }
        return "";
    }

    public void save(){
        try (Writer writer = Files.newBufferedWriter(dataJson.toPath())) {
            new GsonBuilder().setPrettyPrinting().create()
                    .toJson(this, writer);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        if (FrameManager.isCreated)
            FrameManager.getOrCreate().getCurrentPage().reload();
    }


    public void load(){
        JsonObject jsonSettingsFile = getData();
        if (jsonSettingsFile.has("theme"))
            theme = jsonSettingsFile.get("theme").getAsString();
        if (jsonSettingsFile.has("gamePath"))
            gamePath = jsonSettingsFile.get("gamePath").getAsString();
        if (jsonSettingsFile.has("profileSources")){
            List<String> src = new ArrayList<>();
            jsonSettingsFile.get("profileSources")
                    .getAsJsonArray().forEach(o -> src.add(o.getAsString()));
            profileSources = src;
        }
        if (jsonSettingsFile.has("baseGameEnabled"))
            baseGameEnabled = jsonSettingsFile.get("baseGameEnabled").getAsBoolean();
        if (jsonSettingsFile.has("modNameSpaces"))
            modNameSpaces = jsonSettingsFile.get("modNameSpaces").getAsBoolean();
        if (jsonSettingsFile.has("scrollingIncrement"))
            scrollingIncrement = jsonSettingsFile.get("scrollingIncrement").getAsInt();
        if (jsonSettingsFile.has("useRelativeTime"))
            useRelativeTime = jsonSettingsFile.get("useRelativeTime").getAsBoolean();
        if (jsonSettingsFile.has("profileSpecificPaths"))
            profileSpecificPaths = jsonSettingsFile.get("profileSpecificPaths").getAsBoolean();
        save();

        for (FlatAllIJThemes.FlatIJLookAndFeelInfo info : FlatAllIJThemes.INFOS) {
            if (info.getClassName().equals(Cogfly.settings.theme)) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
