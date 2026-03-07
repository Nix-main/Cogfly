package dev.ambershadow.cogfly.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.util.Profile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ModData {

    public static ModData getModAtVersion(JsonObject parent, String version) {
        JsonArray versions = parent.get("versions").getAsJsonArray();
        final JsonObject[] targetVersion = {null};
        for (JsonElement ver : versions) {
            if (ver.getAsJsonObject().get("version_number")
                    .getAsString().equals(version)) {

                targetVersion[0] = ver.getAsJsonObject();
                break;
            }
        }
        if (targetVersion[0] == null)
            return null;
        return new ModData(parent, targetVersion[0]);
    }

    public static ModData getModAtVersion(String fullName, String version){
        Optional<JsonObject> mod = rawModData.stream()
                .filter(obj -> obj.get("full_name")
                        .getAsString().equals(fullName)).findFirst();
        return mod.map(jsonObject -> getModAtVersion(jsonObject, version)).orElse(null);
    }

    public static ModData getMod(String fullName){
        return Cogfly.mods.stream().filter(mod -> mod.getFullName().equals(fullName)).findFirst().orElse(null);
    }

    public static ModData getMod(ModData other){
        return Cogfly.mods.stream().filter(
                mod -> mod.getFullName().equals(other.getFullName())
                        && mod.getAuthor().equals(other.getAuthor())
                        && mod.getDescription().equals(other.getDescription())
        ).findFirst().orElse(null);
    }

    private static boolean containsOldFile(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().endsWith(".old"));
        } catch (IOException e) {
            return false;
        }
    }

    private static void processDirectory(Path root, boolean enabled) {
        try (Stream<Path> files = Files.walk(root)) {
            files
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("manifest.json"))
                    .forEach(p -> renameFile(p, enabled));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void renameFile(Path file, boolean enabled) {
        String fileName = file.getFileName().toString();
        try {
            if (enabled) {
                if (fileName.endsWith(".old")) {
                    String newName = fileName.substring(0, fileName.length() - 4);
                    Path target = file.resolveSibling(newName);
                    Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                if (!fileName.endsWith(".old")) {
                    Path target = file.resolveSibling(fileName + ".old");
                    Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    static List<JsonObject> rawModData = new ArrayList<>();
    final JsonObject rawObj;
    private final String name;
    private final String fullName;
    private final String author;
    private URL downloadUrl;
    private final List<String> dependencies;
    private final String versionNumber;
    private final String description;
    private final String dateCreated;
    private final String dateModified;
    private int totalDownloads;
    private final URI packageUrl;
    private final URI websiteUrl;

    private ModData(JsonObject parentObject, JsonObject version){
        rawObj = parentObject;
        name = parentObject.get("name").getAsString();
        author = parentObject.get("owner").getAsString();
        fullName = parentObject.get("full_name").getAsString();
        dependencies = new ArrayList<>();
        totalDownloads = 0;
        dateCreated = parentObject.get("date_created").getAsString();
        try {
            downloadUrl = URL.of(URI.create(version.get("download_url").getAsString()), null);
        } catch (MalformedURLException ignored){}
        // thunderstore URLs won't be malformed
        packageUrl = URI.create(parentObject.get("package_url").getAsString());
        String website = version.get("website_url").getAsString();
        websiteUrl = website.isEmpty() ? null : URI.create(website);
        JsonArray dependencies = version.get("dependencies").getAsJsonArray();
        dependencies.forEach(dep -> {
            if (dep.getAsString().contains("BepInExPack") || dep.getAsString().trim().isEmpty())
                return;
            this.dependencies.add(dep.getAsString());
        });
        dateModified = version.get("date_created").getAsString();
        versionNumber = version.get("version_number").getAsString();
        description = version.get("description").getAsString();

        parentObject.get("versions").getAsJsonArray()
        .forEach(v -> totalDownloads += v.getAsJsonObject().get("downloads").getAsInt());
    }
    public ModData(JsonObject parentObject){
        JsonArray versions = parentObject.get("versions").getAsJsonArray();
        JsonObject latestVersion = versions.get(0).getAsJsonObject();
        this(parentObject, latestVersion);
    }

    private boolean manual = false;
    // for disabling & enabling manuals
    private boolean enabled = true;

    public ModData(String name, boolean enabled){
        manual = true;
        rawObj = new JsonObject();
        this.name = name;
        fullName = this.name;
        author = this.name;
        dependencies = new ArrayList<>();
        versionNumber = "Manually installed, no version data.";
        description = "Manually installed, no description data.";
        dateCreated = "2000-01-01T00:00:00Z";
        dateModified = "2000-01-01T00:00:00Z";
        packageUrl = null;
        websiteUrl = null;
        this.enabled = enabled;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public String getAuthor() {
        return author;
    }

    public URL getDownloadUrl() {
        return downloadUrl;
    }

    public String getName() {
        return name;
    }
    public String getFullName(){
        return fullName;
    }
    public String getVersionNumber() {
        return versionNumber;
    }
    public String getDescription() {
        return description;
    }
    public String getDateCreated() {
        return dateCreated;
    }
    public String getDateModified() {
        return dateModified;
    }
    public int getTotalDownloads(){
        return totalDownloads;
    }
    public URI getPackageUrl(){
        return packageUrl;
    }
    public URI getWebsiteUrl(){
        return websiteUrl;
    }

    public boolean isInstalled(Profile profile){
        if (manual)
            return true;
        return profile.getInstalledMods()
                .stream().anyMatch(m ->
                        m.getFullName().equals(getFullName()));
    }

    public boolean isOutdated(Profile profile){
        if (manual)
            return false;
        if (!isInstalled(profile))
            return false;
        JsonObject version = rawObj.get("versions").getAsJsonArray().get(0).getAsJsonObject();
        return !(version.get("version_number").getAsString()
                .equals(profile.getInstalledVersion(this)));
    }

    public boolean isEnabled(Profile profile) {
        if (manual)
            return enabled;
        if (!isInstalled(profile))
            return false;
        try (Stream<Path> paths = Files.walk(profile.getBepInExPath())) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equals(getFullName()))
                    .noneMatch(ModData::containsOldFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setEnabled(Profile profile, boolean enabled) {
        if (manual) {
            if (enabled == this.enabled)
                return;
            try (Stream<Path> paths = Files.walk(profile.getPluginsPath())) {
                paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().startsWith(name))
                        .forEach(p -> renameFile(p, enabled));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            this.enabled = enabled;
        }
        if (enabled == isEnabled(profile))
            return;
        try (Stream<Path> paths = Files.walk(profile.getBepInExPath())) {
            paths
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equals(getFullName()))
                    .forEach(p -> processDirectory(p, enabled));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isManual(){
        return manual;
    }

    public String getManualFileName() {
        return name + (enabled ? "" : ".old");
    }

    @Override
    public boolean equals(Object o){
        return o instanceof ModData md &&
                equalsIgnoreVersion(md)
                && md.getVersionNumber().equals(getVersionNumber());
    }

    public boolean equalsIgnoreVersion(ModData md){
        return md.getName().equals(getName())
                && md.getAuthor().equals(getAuthor())
                && md.getDescription().equals(getDescription())
                && md.getFullName().equals(getFullName());
    }
}
