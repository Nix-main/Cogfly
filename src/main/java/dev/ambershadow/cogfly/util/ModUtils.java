package dev.ambershadow.cogfly.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.ModPanelElement;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.profile.Profile;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class ModUtils {

    public static void removeMod(ModData mod, Profile profile) {
        Cogfly.logger.info("Attempting to remove {} at version {} for profile {}.",
                mod.getFullName(), mod.getVersionNumber(), profile.getName());

        if (mod.isManual()){
            try {
                Files.delete(profile.getPluginsPath().resolve(mod.getManualFileName()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            profile.removeMod(mod);
            ModPanelElement.redraw(profile);
            return;
        }

        profile.removeMod(mod);

        List<Path> toDelete = new ArrayList<>();

        toDelete.addAll(FileUtils.collectMatchingFolders(profile.getPluginsPath(), mod.getFullName(), mod.getName()));
        toDelete.addAll(FileUtils.collectMatchingFolders(profile.getBepInExPath().resolve("patchers"), mod.getFullName(), mod.getName()));
        toDelete.addAll(FileUtils.collectMatchingFolders(profile.getBepInExPath().resolve("core"), mod.getFullName(), mod.getName()));
        toDelete.addAll(FileUtils.collectMatchingFolders(profile.getBepInExPath().resolve("monomod"), mod.getFullName(), mod.getName()));

        for (Path path : toDelete) {
            FileUtils.deleteFolder(path);
        }

        ModPanelElement.redraw(profile);
    }
    public static void downloadLatestMod(ModData mod, Profile profile, boolean deps){
        downloadMod(ModData.getMod(mod), profile, deps);
    }

    public static void downloadMod(ModData mod, Profile profile, boolean deps){
        downloadMod(mod, profile, deps, true);
    }
    public static boolean isDownloading(Profile profile) {
        return activeDownloads.containsKey(profile);
    }

    private static final Map<Profile, AtomicLong> activeDownloads = new ConcurrentHashMap<>();
    private static final Map<Profile, AtomicLong> totalDownloads = new ConcurrentHashMap<>();
    private static final Map<Profile, Set<String>> downloads = new ConcurrentHashMap<>();

    public static long getDownloadCount(Profile profile){
        return activeDownloads.containsKey(profile) ? activeDownloads.get(profile).get() : 0L;
    }

    public static long getTotalDownloadCount(Profile profile){
        return totalDownloads.containsKey(profile) ? totalDownloads.get(profile).get() : 0L;
    }

    private static AtomicLong getActiveDownloads(Profile profile) {
        return activeDownloads.computeIfAbsent(
                profile,
                _ -> new AtomicLong(0L)
        );
    }

    private static AtomicLong getTotalDownloads(Profile profile) {
        return totalDownloads.computeIfAbsent(
                profile,
                _ -> new AtomicLong(0L)
        );
    }

    private static Set<String> getDownloads(Profile profile){
        return downloads.computeIfAbsent(
                profile,
                _ -> ConcurrentHashMap.newKeySet()
        );
    }

    public static void downloadManualMod(Path path, Profile profile, boolean copy){
        final String[] fname = {""};
        Cogfly.runAsync(() -> {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(path))) {
                String fullName = "";
                String name = "";
                boolean isZip = false;
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    isZip = true;
                    if (entry.getName().endsWith("manifest.json")) {
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                        String content = os.toString(StandardCharsets.UTF_8);
                        JsonObject object = JsonParser.parseString(content).getAsJsonObject();
                        JsonElement element = object.get("FullName");
                        JsonElement nm = object.get("name");
                        if (element != null && !element.isJsonNull())
                            fullName = element.getAsString();
                        if (nm != null && !nm.isJsonNull())
                            name = nm.getAsString();
                        zis.closeEntry();
                        break;
                    }
                    zis.closeEntry();
                }
                if (copy) {
                    Files.createDirectories(profile.getPath().resolve("manual"));
                    Files.copy(path, profile.getPath().resolve("manual").resolve(path.getFileName()));
                }
                if (!isZip) {
                    Files.copy(path, profile.getPluginsPath().resolve(path.getFileName()));
                    fname[0] = path.getFileName().toString();
                    return;
                }
                if (!fullName.isBlank())
                    fname[0] = fullName;
                else
                    if (!name.isBlank()) {
                        ModData md = ModData.getModByName(name);
                        if (md != null)
                            fname[0] = md.getFullName();
                        else
                            fname[0] = name;
                    } else
                        fname[0] = path.getFileName().toString();
                downloadModZipStream(Files.newInputStream(path), fname[0], profile, Files.size(path));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((_, _) -> {
            profile.refreshMods();
            ModPanelElement.redraw(profile);
            ModPanelElement.setProgressBar(profile);
            if (activeDownloads.containsKey(profile) && activeDownloads.get(profile).get() == 0)
                activeDownloads.remove(profile);
            if (totalDownloads.containsKey(profile) && totalDownloads.get(profile).get() == 0)
                totalDownloads.remove(profile);
        });
        ModPanelElement.setProgressBar(profile);
    }
    public static void downloadMod(ModData mod, Profile profile, boolean deps, boolean enabled){
        CompletableFuture<Void> download = Cogfly.runAsync(() -> {
            String fn = mod.getFullName();
            if (!getDownloads(profile).add(fn))
                return;
            try {
                HttpsURLConnection connection = (HttpsURLConnection) mod.getDownloadUrl().openConnection();
                try(InputStream is = connection.getInputStream()) {
                    if (mod.isInstalled(profile) && !mod.isOutdated(profile))
                        return;
                    Cogfly.logger.info("Attempting to download {} at version {} for profile {}.", fn, mod.getVersionNumber(), profile.getName());
                    profile.removeMod(mod);
                    for (String dep : mod.getDependencies()) {
                        if (dep.contains("BepInExPack"))
                            continue;
                        ModData m = getModFromDependency(dep);
                        if (m != null) {
                            if (!m.isInstalled(profile) || (deps && m.isOutdated(profile))) // install new dependencies when updating a mod
                                downloadMod(m, profile, true);
                        }
                    }
                    downloadModZipStream(is, fn, profile, connection.getContentLengthLong());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            finally {
                if (activeDownloads.containsKey(profile) && activeDownloads.get(profile).get() == 0)
                    activeDownloads.remove(profile);
                if (totalDownloads.containsKey(profile) && totalDownloads.get(profile).get() == 0)
                    totalDownloads.remove(profile);
            }
            mod.setEnabled(profile, enabled);
            SwingUtilities.invokeLater(() -> {
                SwingUtilities.invokeLater(() -> ModPanelElement.setProgressBar(profile));
                profile.addMod(mod);
                ModPanelElement.redraw(profile);
            });
        });
        download.whenComplete((_, _) -> ModPanelElement.setProgressBar(profile));
    }

    public static void downloadModZipStream(InputStream is, String fullName, Profile profile, long totalBytes) throws IOException {
        Path bepinexRoot = profile.getBepInExPath();

        getActiveDownloads(profile).addAndGet(totalBytes);
        getTotalDownloads(profile).addAndGet(totalBytes);
        SwingUtilities.invokeLater(() -> ModPanelElement.setProgressBar(profile));
        Path temp = Files.createTempFile(fullName, ".zip");
        Cogfly.logger.info("Downloading {} ({} bytes) to {}", fullName, totalBytes, temp);
        byte[] buffer = new byte[8192];
        int n;
        try (OutputStream os = Files.newOutputStream(temp)) {
            while ((n = is.read(buffer)) != -1) {
                os.write(buffer, 0, n);
                getActiveDownloads(profile).getAndAdd(-n);
                SwingUtilities.invokeLater(() -> ModPanelElement.setProgressBar(profile));
            }
        }
        getTotalDownloads(profile).getAndAdd(-totalBytes);
        try (ZipFile zipFile = new ZipFile(temp.toFile(), StandardCharsets.ISO_8859_1)) {
            int total = 0;
            int extracted = 0;

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            getActiveDownloads(profile).addAndGet(zipFile.size());
            getTotalDownloads(profile).addAndGet(zipFile.size());
            SwingUtilities.invokeLater(() -> ModPanelElement.setProgressBar(profile));
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().isBlank()) continue;
                total++;

                Path zipPath = Path.of(entry.getName()).normalize();
                if (zipPath.isAbsolute() || zipPath.startsWith("..")) {
                    Cogfly.logger.warn("Bad zip entry, skipping: {}", entry.getName());
                    continue;
                }
                if (zipPath.getNameCount() == 0) continue;

                Path targetBase;
                Path relativeInsideMod;
                String root = zipPath.getName(0).toString();

                switch (root) {
                    case "monomod", "patchers", "plugins", "core" -> {
                        if (zipPath.getNameCount() == 1) continue;
                        targetBase = bepinexRoot.resolve(root).resolve(fullName);
                        relativeInsideMod = zipPath.subpath(1, zipPath.getNameCount());
                    }
                    default -> {
                        targetBase = bepinexRoot.resolve("plugins").resolve(fullName);
                        relativeInsideMod = zipPath;
                    }
                }

                Path outputPath = targetBase.resolve(relativeInsideMod).normalize();
                if (!outputPath.startsWith(targetBase)) {
                    Cogfly.logger.warn("Zip traversal detected, skipping: {}", entry.getName());
                    continue;
                }

                try {
                    if (entry.isDirectory()) {
                        Files.createDirectories(outputPath);
                    } else {
                        Files.createDirectories(outputPath.getParent());
                        try (InputStream in = zipFile.getInputStream(entry);
                             OutputStream os = Files.newOutputStream(outputPath)) {
                            in.transferTo(os);
                        }
                    }
                    extracted++;
                    getActiveDownloads(profile).getAndAdd(-1);
                    SwingUtilities.invokeLater(() -> ModPanelElement.setProgressBar(profile));
                } catch (IOException e) {
                    Cogfly.logger.error("Failed to extract entry '{}'", entry.getName(), e);
                }
            }
            getActiveDownloads(profile).getAndAdd(-(total - extracted));
            getTotalDownloads(profile).addAndGet(-zipFile.size());
            Cogfly.logger.info("Extracted {}/{} entries for {}", extracted, total, fullName);
        } finally {
            Files.delete(temp);
        }
    }

    private static ModData getModFromDependency(String dependency){
        String[] split = dependency.split("-");
        String dep = split[0] + "-" + split[1];
        return ModData.getMod(dep);
    }
}