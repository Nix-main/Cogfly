package dev.ambershadow.cogfly.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.util.FrameManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class ModFetcher {
    private static final String Url = "https://thunderstore.io/c/hollow-knight-silksong/api/v1/package-listing-index/";
    private static Path cache = null;
    public static List<JsonObject> getAllMods() {
        if (cache == null)
            cache = Cogfly.localDataPath.resolve("mod_cache.json");
        List<JsonObject> all = new ArrayList<>();
        String content;
        boolean found = false;
        try (GZIPInputStream gzip = new GZIPInputStream(URL.of(URI.create(Url), null).openStream());
             GZIPInputStream gzip1 = new GZIPInputStream(URL.of(URI.create(new String(gzip.readAllBytes()).split("\"")[1]), null).openStream())
        ) {
            content = new String(gzip1.readAllBytes());
            found = true;
            Files.writeString(cache, content);
        }
        catch (UnknownHostException unknown) {
            JOptionPane.showMessageDialog(
                    FrameManager.getOrCreate().frame,
                "An UnknownHostException was thrown during mod discovery.\nMods may not install properly.",
                "No Internet?",
                JOptionPane.WARNING_MESSAGE
            );
            if (!found && Files.exists(cache)) {
                try {
                    content = Files.readString(cache);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return all;
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        JsonArray items = JsonParser.parseString(content).getAsJsonArray();
        for (JsonElement el : items)
            all.add(el.getAsJsonObject());

        ModData.rawModData = all;
        return all;
    }
    public static List<ModData> getInstalledMods(Path plugins){
        List<ModData> installedMods = new ArrayList<>();
        if (!Files.exists(plugins))
            return installedMods;
        File[] files = plugins.toFile().listFiles();
        if (files == null)
            return installedMods;
        files: for (File file : files){
            if (file.isDirectory()){
                Path manifest = Paths.get(file.getAbsolutePath()).resolve("manifest.json");
                if (Files.exists(manifest)) {
                    try (JsonReader reader = new JsonReader(Files.newBufferedReader(manifest))) {
                        JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
                        String author = get(object, "namespace");
                        String website = get(object, "website_url");
                        String name = get(object, "name");
                        String description = get(object, "description");
                        String verion = get(object, "version_number");
                        List<String> dependencies = new ArrayList<>();
                        JsonArray deps = object.has("dependencies") ? object.get("dependencies").getAsJsonArray() : null;
                        if (deps != null)
                            deps.forEach(dep -> {
                                if (dep.getAsString().contains("BepInEx-BepInExPack") || dep.getAsString().trim().isEmpty())
                                    return;
                                dependencies.add(dep.getAsString());
                            });

                        for (ModData mod : Cogfly.mods.values()) {
                            if (installedMods.contains(mod))
                                continue;
                            int matches = 0;
                            if (author.equals(mod.getAuthor()))
                                matches++;
                            if (description.equals(mod.getDescription()))
                                matches++;
                            if (mod.getWebsiteUrl() != null)
                                if (website.equals(mod.getWebsiteUrl().toString()))
                                    matches++;
                            if (name.equals(mod.getName()))
                                matches++;
                            if (verion.equals(mod.getVersionNumber()))
                                matches++;
                            boolean de = new HashSet<>(dependencies).equals(new HashSet<>(mod.getDependencies()));
                            if (de && !mod.getDependencies().isEmpty())
                                matches++;

                            if (matches >= 3) {
                                var installedVersion = get(object, "version_number");
                                if (installedVersion.isEmpty()) {
                                    installedVersion = mod.getVersionNumber();
                                }
                                var md = ModData.getModAtVersion(mod.rawObj, installedVersion);
                                if (md == null) {
                                    Cogfly.logger.info("Failed to check if mod '{}' is installed. Make sure you are using an official version published on Thunderstore", mod.getFullName());
                                    break;
                                }
                                installedMods.add(md);
                                continue files;
                            }
                        }
                        installedMods.add(new ModData(name + " (manual)", author, dependencies, verion, description, website));
                        continue;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            installedMods.add(new ModData(file.getName(), !file.getName().endsWith(".old")));
        }
        try (Stream<Path> paths = Files.list(plugins)){
            paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".dll")
                            || path.getFileName().toString().endsWith(".dll.old"))
                    .forEach(path -> {
                        Cogfly.logger.info("Found manual mod at {}", path);
                        installedMods.add(new ModData(path.getFileName().toString(), !path.getFileName().toString().endsWith(".dll.old")));
                    });
        }
        catch (IOException e){
            throw new RuntimeException(e);
        }
        return installedMods;
    }

    private static String get(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsString() : "";
    }
}
