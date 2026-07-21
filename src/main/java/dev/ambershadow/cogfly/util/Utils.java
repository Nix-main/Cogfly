package dev.ambershadow.cogfly.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.ModPanelElement;
import dev.ambershadow.cogfly.loader.ModData;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class Utils {

    private static final Map<String, String> EXT_TO_UTI = Map.ofEntries(
            Map.entry("png", "public.image"),
            Map.entry("jpg", "public.image"),
            Map.entry("jpeg", "public.image"),
            Map.entry("gif", "public.image"),
            Map.entry("app", "com.apple.application-bundle"),
            Map.entry("sh", "public.unix-executable"),
            Map.entry("bin", "public.unix-executable"),
            Map.entry("zip", "com.pkware.zip-archive"),
            Map.entry("dll", "com.microsoft.windows-dynamic-link-library")
    );

    private static final Map<String, String> EXT_TO_MIME = Map.ofEntries(
            Map.entry("linux_executable", "application/x-executable"), // not a real extension but a filler to use the MIME type
            Map.entry("exe", "application/x-msdownload"),
            Map.entry("sh",  "application/x-sh"),
            Map.entry("bin", "application/octet-stream"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg","image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("zip", "application/zip"),
            Map.entry("dll", "application/x-msdownload")
    );

    public static Path getSavePath() {
        String home = System.getProperty("user.home");
        return switch (OperatingSystem.current()){
            case MAC -> Paths.get(home + "/Library/Application Support/unity.Team-Cherry.Silksong/");
            case LINUX -> Paths.get(home + "/.config/unity3d/Team Cherry/Hollow Knight Silksong/");
            case WINDOWS -> Paths.get(home + "\\AppData\\LocalLow\\Team Cherry\\Hollow Knight Silksong\\");
            case OTHER -> Paths.get("");
        };
    }

    public static String getGameExecutable(){
        return switch (OperatingSystem.current()){
            case WINDOWS -> "Hollow Knight Silksong.exe";
            case LINUX, MAC -> "run_bepinex.sh";
            default -> "";
        };
    }


    public static void openPath(Path path){
        if (!(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)))
            return;
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void openURI(URI uri){
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                Desktop.getDesktop().browse(uri);
            else
                new ProcessBuilder("xdg-open", uri.toString()).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void openSavePath(){
        if (Files.isDirectory(getSavePath())) {
            try (Stream<Path> stream = Files.list(getSavePath())) {
                stream.findFirst().ifPresent(Utils::openPath);
            } catch (IOException ignored) {}
        }
    }

    public static void openProfilePath(Profile profile) {
        openPath(profile.getPath());
    }

    public static void pickFolder(Consumer<Path> callback){
        switch (OperatingSystem.current()){
            case MAC -> {
                ProcessBuilder pb = new ProcessBuilder("osascript", "-e", "POSIX path of (choose folder)");
                readValue(pb).ifPresent((p) -> {
                    if (!p.equals(Paths.get("")))
                        callback.accept(p);
                });
            }
            case WINDOWS -> {
                WString path = Cogfly.FOLDER_PICKER.pickFolder();
                if (path != null && !path.toString().isEmpty()) {
                    callback.accept(Paths.get(path.toString()));
                }
            }
            case LINUX -> {
                Optional<Path> path = readValue(new ProcessBuilder(
                        "zenity", "--file-selection", "--directory"
                ));

                if (path.isEmpty()) {
                    path = readValue(new ProcessBuilder(
                            "kdialog", "--getexistingdirectory"
                    ));
                }

                path.ifPresentOrElse(
                        (p) -> {
                            if (!p.equals(Paths.get("")))
                                callback.accept(p);
                        },
                        () -> {
                            String input = JOptionPane.showInputDialog(FrameManager.getOrCreate().frame,
                                    "Please manually enter a folder path. It is highly recommended that you install either Zenity or KDialog for a proper display."
                            );
                            if (input != null) {
                                Path p = Paths.get(input);
                                callback.accept(p.toFile().isDirectory() ? p : p.getParent());
                            }
                        }
                );
            }
        }
    }

    public static void pickFile(Consumer<Path> callback, String name, String... extensions){
        switch (OperatingSystem.current()){
            case MAC -> {
                Set<String> utis = new LinkedHashSet<>();

                for (String ext : extensions) {
                    String uti = EXT_TO_UTI.get(ext.toLowerCase());
                    if (uti != null)
                        utis.add(uti);
                }

                StringJoiner joiner = new StringJoiner(", ");
                for (String uti : utis)
                    joiner.add("\"" + uti + "\"");

                String appleScriptCommand =
                        "POSIX path of (choose file of type {" + joiner + "})";
                ProcessBuilder pb = new ProcessBuilder(
                        "osascript", "-e", appleScriptCommand
                );
                readValue(pb).ifPresent(p -> {
                    if (!p.equals(Paths.get("")))
                        callback.accept(p);
                });
            }
            case WINDOWS -> {
                for (int i = 0; i < extensions.length; i++) {
                    extensions[i] = name + "." + extensions[i];
                }
                Pointer pointer = Cogfly.FILE_DIALOGS.tinyfd_openFileDialog(
                        "Select File",
                        null,
                        extensions.length,
                        extensions,
                        "",
                        0
                );
                String path;
                if (pointer != null && !(path = pointer.getString(0, StandardCharsets.UTF_8.name())).isEmpty())
                    callback.accept(Paths.get(path));
            }
            case LINUX -> {
                String patterns = Arrays.stream(extensions)
                        .flatMap(ext -> {
                            String mime = EXT_TO_MIME.get(ext);
                            return mime != null
                                    ? Stream.of("*." + ext, mime)
                                    : Stream.of("*." + ext);
                        })
                        .distinct()
                        .collect(Collectors.joining(" "));
                Optional<Path> path = readValue(new ProcessBuilder(List.of(
                        "zenity",
                        "--file-selection",
                        "--file-filter=" + name + " | " + patterns,
                        "--file-filter=All files | *"
                )));
                if (path.isEmpty()) {
                    path = readValue(new ProcessBuilder(List.of(
                            "kdialog",
                            "--getopenfilename",
                            ".",
                            patterns + "|" + name + " files\n*|All files"
                    )));
                }

                if (path.isEmpty()) {
                    String val = manualFilePath(false, name, extensions);
                    callback.accept(Paths.get(val));
                }

                path.ifPresent(p -> {
                    if (!p.equals(Paths.get("")))
                        callback.accept(p);
                });
            }
        }
    }

    private static String manualFilePath(boolean invalid, String name, String... extensions){
        StringJoiner filterJoiner = new StringJoiner(",");
        for (String extension : extensions)
            filterJoiner.add("\"" + extension + "\"");
        String input = JOptionPane.showInputDialog(FrameManager.getOrCreate().frame,
                (invalid ? "Invalid extension or null input. " : "") + "Please manually enter a file path. Allowed extensions: " + filterJoiner + ". It is highly recommended that you install either Zenity or KDialog for a proper display."
        );
        if (input != null) {
            for (String val : extensions) {
                if (input.endsWith(name + "." + val))
                    return input;
            }
        }
        return manualFilePath(true, name, extensions);
    }

    private static Optional<Path> readValue(ProcessBuilder pb) {
        try {
            Process p = pb.start();

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(
                                 p.getInputStream(), StandardCharsets.UTF_8))) {

                String value = reader.readLine();
                int exit = p.waitFor();

                if (exit == 1){ // cancelled in zenity/dialog
                    return Optional.of(Paths.get(""));
                }

                if (exit == 0 && value != null && !value.isBlank()) {
                    return Optional.of(Paths.get(value.trim()));
                }
            }
        } catch (IOException | InterruptedException ignored) {
        }
        return Optional.empty();
    }

    public static void downloadAndExtract(URL url, Path output){
        try (ZipInputStream zis = new ZipInputStream(url.openStream())) {
            Files.createDirectories(output);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outputPath = output.resolve(entry.getName()).normalize();
                if (!outputPath.startsWith(output)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    try (OutputStream os = Files.newOutputStream(outputPath)) {
                        zis.transferTo(os);
                    }
                }

            }
            zis.closeEntry();
        } catch (IOException ignored){}
    }

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

        toDelete.addAll(collectMatchingFolders(profile.getPluginsPath(), mod.getFullName(), mod.getName()));
        toDelete.addAll(collectMatchingFolders(profile.getBepInExPath().resolve("patchers"), mod.getFullName(), mod.getName()));
        toDelete.addAll(collectMatchingFolders(profile.getBepInExPath().resolve("core"), mod.getFullName(), mod.getName()));
        toDelete.addAll(collectMatchingFolders(profile.getBepInExPath().resolve("monomod"), mod.getFullName(), mod.getName()));

        for (Path path : toDelete) {
            deleteFolder(path);
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
    public static void launchModdedGame(Profile profile){
        Cogfly.logger.info("Attempting to launch game with profile: {}",  profile.getName());
        if (isDownloading(profile)){
            JOptionPane.showMessageDialog(FrameManager.getOrCreate().frame, "Downloads are currently in-progress for this profile. Please wait for them to complete before launching.", "Downloads in progress!", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Cogfly.launchGameAsync(true, profile.getBepInExPath().toString(), profile.getGamePath());
    }

    public static void deleteFolder(Path folder){
        if (!Files.exists(folder))
            return;
        try(Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Path> collectMatchingFolders(Path dir, String... prefixes) {
        List<Path> matches = new ArrayList<>();

        if (!Files.isDirectory(dir)) return matches;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;

                String name = entry.getFileName().toString();
                for (String prefix : prefixes) {
                    if (name.startsWith(prefix)) {
                        matches.add(entry);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return matches;
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
    public enum OperatingSystem {
        WINDOWS, MAC, LINUX, OTHER;

        private static OperatingSystem current;
        public static OperatingSystem current() {
            if (current == null) {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) current = WINDOWS;
                else if (os.contains("mac")) current = MAC;
                else if (os.contains("nix") || os.contains("nux")) current = LINUX;
                else current = OTHER;
            }
            return current;
        }
    }
}