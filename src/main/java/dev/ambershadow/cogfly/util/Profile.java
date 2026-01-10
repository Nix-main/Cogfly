package dev.ambershadow.cogfly.util;

import dev.ambershadow.cogfly.loader.ModData;

import javax.swing.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Profile {
    List<ModData> installedMods = new ArrayList<>();
    private final Path path;
    private final String name;

    private final Icon icon;
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
        ModData m = new ArrayList<>(installedMods).stream().filter(md ->md.getFullName().equals(mod.getFullName()) && md.getDescription().equals(mod.getDescription())
                && md.getAuthor().equals(mod.getAuthor())).findFirst().orElse(null);
        installedMods.remove(m);
    }

    public String getInstalledVersion(ModData mod) {
        return installedMods.stream().filter(md -> md.getFullName().equals(mod.getFullName()) && md.getDescription().equals(mod.getDescription())
            && md.getAuthor().equals(mod.getAuthor())
        ).toList().getFirst().getVersionNumber();
    }

    public Icon getIcon() {
        return icon;
    }

    public String getName() {
        return name;
    }
}
