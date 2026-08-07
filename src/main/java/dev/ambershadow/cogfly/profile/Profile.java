package dev.ambershadow.cogfly.profile;

import com.google.gson.stream.JsonWriter;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.loader.ModFetcher;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Profile {
    private HashMap<String, ModData> installedMods = new HashMap<>();
    private Set<String> disabledMods = new HashSet<>();
    private final Path path;
    private final String name;
    private String gamePath = Cogfly.settings.gamePath;
    private Icon icon;
    private final Path iconPath;
    public Profile(String name, Path path) {
        this(name, path, null, null);
    }
    public Profile(String name, Path path, Path iconPath, Icon icon) {
        this.path = path;
        this.name = name;
        this.icon = icon;
        this.iconPath = iconPath;
    }

    public Path getPath() {
        return path;
    }

    public Path getBepInExPath() {
        return path.resolve("BepInEx");
    }

    public Path getPluginsPath() {
        return getBepInExPath().resolve("plugins");
    }

    public Path getIconPath() {
        return iconPath;
    }
    public List<ModData> getInstalledMods() {
        return installedMods.values().stream().toList();
    }

    public void removeMod(ModData mod) {
        installedMods.remove(mod.getFullName());
    }

    public void addMod(ModData mod) {
        installedMods.put(mod.getFullName(), mod);
    }

    public void setEnabled(ModData mod, boolean enabled) {
        if (enabled)
            disabledMods.remove(mod.getFullName());
        else
            disabledMods.add(mod.getFullName());
    }

    public boolean isEnabled(ModData mod) {
        return !disabledMods.contains(mod.getFullName());
    }

    public String getInstalledVersion(ModData mod) {
        return installedMods.get(mod.getFullName()).getVersionNumber();
    }

    public Icon getIcon() {
        return icon;
    }
    public void setIcon(Icon icon) {
        this.icon = icon;
    }
    public String getGamePath() {
        return Cogfly.settings.profileSpecificPaths ? gamePath : Cogfly.settings.gamePath;
    }

    public void resetGamePath() {
        try {
            Files.deleteIfExists(getPath().resolve("cogfly_data.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        gamePath = Cogfly.settings.gamePath;
    }
    public void setGamePath(String gamePath) {
        this.gamePath = gamePath;
        try(JsonWriter writer = new JsonWriter(Files.newBufferedWriter(getPath().resolve("cogfly_data.json")))) {
            writer.beginObject();
            writer.name("gamePath");
            writer.value(gamePath);
            writer.endObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void refreshMods() {
        installedMods = ModFetcher.getInstalledMods(getPluginsPath())
                .stream().collect(Collectors.toMap(
                        ModData::getFullName,
                        Function.identity(),
                        (_, v) -> v,
                        HashMap::new
                ));
        disabledMods = installedMods.values().stream()
                .map(ModData::getFullName)
                .filter(fullName -> ModData.containsOldFile(getPluginsPath().resolve(fullName)))
                .collect(Collectors.toSet());
    }

    public List<ModData> getManualMods() {
        return installedMods.values().stream().filter(ModData::isManual).toList();
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
