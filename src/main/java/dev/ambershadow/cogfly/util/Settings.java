package dev.ambershadow.cogfly.util;

import com.formdev.flatlaf.intellijthemes.FlatNordIJTheme;
import com.google.gson.GsonBuilder;
import dev.ambershadow.cogfly.Cogfly;
import net.harawata.appdirs.AppDirsFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Settings {

    private static final String[] STATIC_PATHS = new String[]
    {
            "Program Files/Steam/steamapps/common/Hollow Knight Silksong",
            "XboxGames/Hollow Knight Silksong/Content",
            "Program Files (x86)/Steam/steamapps/common/Hollow Knight Silksong",
            "Program Files/GOG Galaxy/Games/Hollow Knight Silksong",
            "Program Files (x86)/GOG Galaxy/Games/Hollow Knight Silksong",
            "Steam/steamapps/common/Hollow Knight Silksong",
            "GOG Galaxy/Games/Hollow Knight Silksong"
    };

    public String theme = FlatNordIJTheme.class.getName();
    public String gamePath = findDefaultPath();
    public String profileSavePath = Paths.get(Cogfly.roamingDataPath + "/profiles/").toString();
    public List<String> profileSources = new ArrayList<>();
    public boolean baseGameEnabled = false;
    public boolean modNameSpaces = true;
    public int scrollingIncrement = 16;
    public boolean useRelativeTime = false;

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
        try (Writer writer = Files.newBufferedWriter(Cogfly.dataJson.toPath())) {
            new GsonBuilder().setPrettyPrinting().create()
                    .toJson(this, writer);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        if (FrameManager.isCreated)
            FrameManager.getOrCreate().getCurrentPage().reload();
    }
}
