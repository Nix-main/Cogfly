package dev.ambershadow.cogfly.util;

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
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {

    private static final Map<String, String> EXT_TO_UTI = Map.ofEntries(
            Map.entry("png", "public.image"),
            Map.entry("jpg", "public.image"),
            Map.entry("jpeg", "public.image"),
            Map.entry("gif", "public.image"),

            Map.entry("app", "com.apple.application-bundle"),

            Map.entry("sh", "public.unix-executable"),
            Map.entry("bin", "public.unix-executable")
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
        if (!(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)))
            return;
        try {
            Desktop.getDesktop().browse(uri);
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
                if (pointer != null && !(path = pointer.getString(0)).isEmpty())
                    callback.accept(Paths.get(path));
            }
            case LINUX -> {
                String patterns = Arrays.stream(extensions)
                        .map(ext -> name + "." + ext)
                        .collect(Collectors.joining(" "));
                List<String> zenityCmd = new ArrayList<>();
                zenityCmd.add("zenity");
                zenityCmd.add("--file-selection");
                zenityCmd.add("--file-filter=" + patterns + "| " + patterns);
                zenityCmd.add("--file-filter=All files | *");
                Optional<Path> path = readValue(new ProcessBuilder(zenityCmd));
                if (path.isEmpty()) {
                    List<String> kdialogCmd = List.of(
                            "kdialog",
                            "--getopenfilename",
                            ".",
                            patterns + "|" + name + "files\n*|All files"
                    );

                    path = readValue(new ProcessBuilder(kdialogCmd));
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
                         new BufferedReader(new InputStreamReader(p.getInputStream()))) {

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

        profile.getInstalledMods().remove(mod);

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

    public static void downloadMod(ModData mod, Profile profile, boolean deps, boolean enabled){
        if (mod.isInstalled(profile) && profile.getInstalledVersion(mod).equals(mod.getVersionNumber()))
            return;
        Cogfly.logger.info("Attempting to download {} at version {} for profile {}.", mod.getFullName(), mod.getVersionNumber(), profile.getName());
        profile.removeMod(mod);
        profile.getInstalledMods().add(mod);
        if (deps) {
            for (String dep : mod.getDependencies()) {
                if (dep.contains("BepInExPack"))
                    continue;
                ModData m = getModFromDependency(dep);
                if (m != null && !m.isOutdated(profile)) {
                    CompletableFuture.runAsync(() -> downloadMod(m, profile, true));
                }
            }
        }
        Path bepinexRoot = profile.getBepInExPath();

        try (ZipInputStream zis = new ZipInputStream(mod.getDownloadUrl().openStream())) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().isBlank()) continue;

                Path zipPath = Path.of(entry.getName()).normalize();

                if (zipPath.isAbsolute() || zipPath.startsWith("..")) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }

                Path targetBase;
                Path relativeInsideMod;

                if (zipPath.getNameCount() > 0) {
                    String root = zipPath.getName(0).toString();

                    switch (root) {
                        case "monomod", "patchers", "plugins", "core" -> {
                            targetBase = bepinexRoot.resolve(root).resolve(mod.getFullName());
                            if (zipPath.getNameCount() > 1) {
                                relativeInsideMod = zipPath.subpath(1, zipPath.getNameCount());
                            } else {
                                continue;
                            }                        }
                        default -> {
                            targetBase = bepinexRoot.resolve("plugins").resolve(mod.getFullName());
                            relativeInsideMod = zipPath;
                        }
                    }
                } else
                    continue;

                Path outputPath = targetBase.resolve(relativeInsideMod).normalize();

                if (!outputPath.startsWith(targetBase))
                    throw new IOException("Zip traversal detected: " + entry.getName());

                if (entry.isDirectory())
                    Files.createDirectories(outputPath);
                else {
                    Files.createDirectories(outputPath.getParent());
                    try (OutputStream os = Files.newOutputStream(outputPath)) {
                        zis.transferTo(os);
                    }
                }

                zis.closeEntry();
            }
        } catch (IOException ignored){}
        mod.setEnabled(profile, enabled);
        ModPanelElement.redraw(profile);
    }
    private static ModData getModFromDependency(String dependency){
        for (ModData mod : Cogfly.mods) {
            String[] split = dependency.split("-");
            String dep = split[0] + "-" + split[1];
            if (dep.equals(mod.getFullName()))
                return mod;

        }
        return null;
    }

    public static void copyFile(Path path) {
        String fileContent;
        try {
            fileContent = Files.readString(path);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(fileContent), null);
    }

    public static void copyString(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection selection = new StringSelection(text);
        clipboard.setContents(selection, null);
    }
    public static void launchModdedGame(Profile profile){
        Cogfly.logger.info("Attempting to launch game with profile: {}",  profile.getName());
        Cogfly.launchGameAsync(true, profile.getBepInExPath().toString(), profile.getGamePath());
    }

    private static void deleteFolder(Path folder){
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
            Cogfly.logger.error("Failed scanning directory {}: {}", dir, e.getMessage());
        }

        return matches;
    }
    public enum OperatingSystem {
        WINDOWS, MAC, LINUX, OTHER;

        public static OperatingSystem current() {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) return WINDOWS;
            if (os.contains("mac")) return MAC;
            if (os.contains("nix") || os.contains("nux")) return LINUX;
            return OTHER;
        }
    }
}