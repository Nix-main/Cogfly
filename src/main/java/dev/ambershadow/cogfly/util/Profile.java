package dev.ambershadow.cogfly.util;

import com.google.gson.stream.JsonWriter;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.loader.ModData;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Profile {
    List<ModData> installedMods = new ArrayList<>();
    private final Path path;
    private final String name;
    private String gamePath = Cogfly.settings.gamePath;
    private Icon icon;
    public Profile(String name, Path path) {
        this(name, path, null);
    }
    public Profile(String name, Path path, Icon icon) {
        this.path = path;
        this.name = name;
        this.icon = icon;
    }

    public Path getPath() {
        return path;
    }

    public Path getBepInExPath(){
        return path.resolve("BepInEx");
    }

    public Path getPluginsPath(){
        return getBepInExPath().resolve("plugins");
    }
    public List<ModData> getInstalledMods() {
        return installedMods;
    }

    public void removeMod(ModData mod){
        ModData m = new ArrayList<>(installedMods).stream().filter(md -> md.equalsIgnoreVersion(mod)).findFirst().orElse(null);
        installedMods.remove(m);
    }

    public String getInstalledVersion(ModData mod) {
        var x = installedMods.stream().filter(md -> md.equalsIgnoreVersion(mod)).toList();
        if (!x.isEmpty())
            return x.getFirst().getVersionNumber();
        return "";
    }

    public Icon getIcon() {
        return icon;
    }
    public void setIcon(Icon icon) {
        this.icon = icon;
    }
    public String getGamePath(){
        return Cogfly.settings.profileSpecificPaths ? gamePath : Cogfly.settings.gamePath;
    }

    public void resetGamePath(){
        try {
            Files.deleteIfExists(getPath().resolve("cogfly_data.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        gamePath = Cogfly.settings.gamePath;
    }

    public void setGamePathWithoutSaving(String gamePath){
        this.gamePath = gamePath;
        CompletableFuture.runAsync(() -> Cogfly.downloadBepInExNoConsole(Paths.get(gamePath)));
    }
    public void setGamePath(String gamePath){
        setGamePathWithoutSaving(gamePath);
        try(JsonWriter writer = new JsonWriter(Files.newBufferedWriter(getPath().resolve("cogfly_data.json")))) {
            writer.beginObject();
            writer.name("gamePath");
            writer.value(gamePath);
            writer.endObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Profile prof &&
                prof.getPath().equals(path)
                && prof.getName().equals(name);
    }
}
