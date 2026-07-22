package dev.ambershadow.cogfly.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.ModPanelElement;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.profile.Profile;
import dev.ambershadow.cogfly.util.swing.FrameManager;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
        return !getActiveDownloads(profile).isEmpty();
    }
    private static final Map<Profile, Set<String>> activeDownloads =
            new ConcurrentHashMap<>();

    private static Set<String> getActiveDownloads(Profile profile) {
        return activeDownloads.computeIfAbsent(
                profile,
                _ -> ConcurrentHashMap.newKeySet()
        );
    }
    public static void downloadManualMod(Path path, Profile profile, boolean copy){
        final String[] fname = {""};
        runAsync(() -> {
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
                downloadModZipStream(Files.newInputStream(path), fname[0], profile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((_, _) -> {
            profile.refreshMods();
            ModPanelElement.redraw(profile);
            getActiveDownloads(profile).remove(fname[0]);
            ModPanelElement.setProgressBar(profile);
            if (getActiveDownloads(profile).isEmpty())
                activeDownloads.remove(profile);
        });
        getActiveDownloads(profile).add(fname[0]);
        ModPanelElement.setProgressBar(profile);
    }
    public static void downloadMod(ModData mod, Profile profile, boolean deps, boolean enabled){
        CompletableFuture<Void> download = runAsync(() -> {
            String fn = mod.getFullName();
            if (!getActiveDownloads(profile).add(fn)) {
                return;
            }
            SwingUtilities.invokeLater(() -> ModPanelElement.setProgressBar(profile));
            try(InputStream is = mod.getDownloadUrl().openStream()) {
                if (mod.isInstalled(profile))
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
                downloadModZipStream(is, fn, profile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            finally {
                getActiveDownloads(profile).remove(mod.getFullName());
                if (getActiveDownloads(profile).isEmpty())
                    activeDownloads.remove(profile);
            }
            mod.setEnabled(profile, enabled);
            SwingUtilities.invokeLater(() -> {
                profile.addMod(mod);
                ModPanelElement.redraw(profile);
            });
        });
        download.whenComplete((_, _) -> ModPanelElement.setProgressBar(profile));
    }

    public static void downloadModZipStream(InputStream is, String fullName, Profile profile) throws IOException {
        Path bepinexRoot = profile.getBepInExPath();

        Path temp = Files.createTempFile(fullName, ".zip");
        Files.write(temp, is.readAllBytes());
        try (ZipFile zipFile = new ZipFile(temp.toFile(), StandardCharsets.ISO_8859_1)) {
            int total = 0;
            int extracted = 0;

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                total++;
                if (entry.getName().isBlank()) continue;

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
                } catch (IOException e) {
                    Cogfly.logger.warn("Failed to extract entry '{}': {}", entry.getName(), e.getMessage());
                }
            }

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

    public static void copyFile(Path path){
        try {
            copyString(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void copyString(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection selection = new StringSelection(text);
        clipboard.setContents(selection, null);
    }



    public static CompletableFuture<Void> runAsync(Runnable runnable){
        CompletableFuture<Void> future = CompletableFuture.runAsync(runnable);
        future.exceptionally(f -> {
            throw new RuntimeException(f);
        });
        return future;
    }

    public static void throwNonFatalError(Throwable e){
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String[] lines = sw.toString().split("\\R");
        int maxLines = 15;

        String stackTrace = String.join(
                System.lineSeparator(),
                Arrays.copyOfRange(lines, 0, Math.min(lines.length, maxLines))
        );
        if (lines.length > maxLines) {
            stackTrace += System.lineSeparator()
                    + "... (" + (lines.length - maxLines) + " more lines)";
        }
        int val = JOptionPane.showOptionDialog(
                FrameManager.getOrCreate().frame,
                stackTrace,
                "An error has occurred!",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                new Object[]{"Copy To Clipboard", "Close"},
                0);
        if (val == JOptionPane.YES_OPTION) {
            copyString(sw.toString());
        }
    }
}