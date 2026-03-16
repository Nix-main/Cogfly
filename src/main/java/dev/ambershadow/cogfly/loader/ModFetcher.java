package dev.ambershadow.cogfly.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.asset.Assets;

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
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class ModFetcher {
    private static final String Url = "https://thunderstore.io/c/hollow-knight-silksong/api/v1/package-listing-index/";
    private static List<JsonObject> fallbackList = new ArrayList<>();
    public static List<JsonObject> getAllMods() {
        List<JsonObject> all = new ArrayList<>();
        String content;
        try (GZIPInputStream gzip = new GZIPInputStream(URL.of(URI.create(Url), null).openStream());
             GZIPInputStream gzip1 = new GZIPInputStream(URL.of(URI.create(new String(gzip.readAllBytes()).split("\"")[1]), null).openStream())
        ) {
            content = new String(gzip1.readAllBytes());
        }
        catch (UnknownHostException unknown) {
            JOptionPane.showMessageDialog(
                null,
                "An UnknownHostException was thrown during mod discovery.\nMods will not show",
                "No Internet?",
                JOptionPane.WARNING_MESSAGE
            );

            return new ArrayList<>();
        }
        catch (IOException e) {
            return fallbackList;
        }

        JsonArray items = JsonParser.parseString(content).getAsJsonArray();
        for (JsonElement el : items)
            all.add(el.getAsJsonObject());

        fallbackList = all;
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
        for (File file : files){
            File[] innerFiles = file.listFiles();
            if (innerFiles == null)
                continue;
            Path manifest = Paths.get(file.getAbsolutePath() + "/manifest.json");
            if (Files.exists(manifest)) {
                try (JsonReader reader = new JsonReader(Files.newBufferedReader(manifest))) {
                    JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
                    String author = get(object, "namespace");
                    String website = get(object, "website_url");
                    String name = get(object, "name");
                    String description = get(object, "description");
                    List<String> dependencies = new ArrayList<>();
                    JsonArray deps = object.has("dependencies") ? object.get("dependencies").getAsJsonArray() : null;
                    if (deps != null)
                        deps.forEach(dep -> {
                            if (dep.getAsString().contains("BepInEx-BepInExPack") || dep.getAsString().trim().isEmpty())
                                return;
                            dependencies.add(dep.getAsString());
                        });

                    for (ModData mod : Cogfly.mods) {
                        if (installedMods.contains(mod))
                            continue;
                        int matches = 0;
                        if (author.equals(mod.getAuthor()))
                            matches++;
                        if (description.equals(mod.getDescription()))
                            matches++;
                        if (mod.getWebsiteUrl() != null)
                            if (website.equals(mod.getWebsiteUrl().getPath()))
                                matches++;
                        if (name.equals(mod.getName()))
                            matches++;
                        boolean de = new HashSet<>(dependencies).equals(new HashSet<>(mod.getDependencies()));
                        if (de)
                            matches++;
                        if (matches >= 3) {
                            var installedVersion = get(object, "version_number");
                            if (installedVersion.isEmpty()) {
                                installedVersion = mod.getVersionNumber();
                            }
                            var md = ModData.getModAtVersion(mod.rawObj, installedVersion);
                            installedMods.add(md);
                            break;
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        try (Stream<Path> paths = Files.list(plugins)){
            paths
                    .filter(Files::isRegularFile)
                    .filter(pth -> pth.getFileName().toString().endsWith(".dll")
                            || pth.getFileName().toString().endsWith(".dll.old"))
                    .forEach(pth -> {
                        System.out.println(pth);
                        installedMods.add(new ModData(pth.getFileName().toString(), !pth.getFileName().toString().endsWith(".dll.old")));
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
