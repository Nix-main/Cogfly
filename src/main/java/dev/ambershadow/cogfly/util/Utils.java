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
        if (!(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)))
            return;
        try {
            Desktop.getDesktop().browse(path.toUri());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void openSavePath(){
        Path savePath = getSavePath();
        if (savePath.toString().isEmpty() || savePath.toString().isBlank())
            return;
        if (new File(savePath.toString()).exists()){
            File file = new File(savePath.toString());
            if (!file.isDirectory())
                return;
            String[] files =  file.list();
            if (files == null || files.length == 0)
                return;
            URI open = savePath.resolve(files[0]).toUri();
            if (!(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)))
                return;
            try {
                Desktop.getDesktop().browse(open);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void openProfilePath(Profile profile) {
        if (!(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)))
            return;
        try {
            Desktop.getDesktop().browse(profile.getPath().toUri());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    public static void removeMod(ModData mod, Profile profile){
        Cogfly.logger.info("Attempting to remove {} at version {} for profile {}.", mod.getFullName(), mod.getVersionNumber(), profile.getName());
        profile.getInstalledMods().remove(mod);
        Path path = profile.getPluginsPath()
                .resolve(mod.getFullName());
        if (!path.toFile().exists()) {
            Cogfly.logger.warn("Could not find a folder at {} to remove this mod!", path);
            path = profile.getPluginsPath().resolve(mod.getName());
        }
        if (!path.toFile().exists()) {
            Cogfly.logger.warn("Could not find a folder at {} to remove this mod!", path);
            Cogfly.logger.warn("Could not find any folder to remove this mod.");
            return;
        }
        deleteFolder(path);
        Path patcher = profile.getBepInExPath().resolve("patchers").resolve(mod.getFullName());
        if (patcher.toFile().exists())
            deleteFolder(patcher);
        Path core = profile.getBepInExPath().resolve("core").resolve(mod.getFullName());
        if (core.toFile().exists())
            deleteFolder(core);
        Path monomod = profile.getBepInExPath().resolve("monomod").resolve(mod.getFullName());
        if (monomod.toFile().exists())
            deleteFolder(monomod);

        ModPanelElement.redraw(profile);
    }
    public static void downloadLatestMod(ModData mod, Profile profile, boolean deps){
        downloadMod(ModData.getMod(mod), profile, deps);
    }

    public static void downloadMod(ModData mod, Profile profile){
        downloadMod(ModData.getMod(mod), profile, true);
    }
    public static void downloadMod(ModData mod, Profile profile, boolean deps){
        Cogfly.logger.info("Attempting to download {} at version {} for profile {}.", mod.getFullName(), mod.getVersionNumber(), profile.getName());
        profile.removeMod(mod);
        profile.getInstalledMods().add(mod);
        if (deps) {
            for (String dep : mod.getDependencies()) {
                if (dep.contains("BepInExPack"))
                    continue;
                ModData m = getModFromDependency(dep);
                if (m != null && !m.isOutdated(profile))
                    CompletableFuture.runAsync(() -> downloadMod(m, profile));
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
                            relativeInsideMod = zipPath.subpath(1, zipPath.getNameCount());
                        }
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
        ModPanelElement.redraw(profile);
    }
    private static ModData getModFromDependency(String dependency){
        for (ModData mod : Cogfly.mods) {
            String combined = mod.getAuthor() + "-" + mod.getName();
            if (dependency.contains(combined))
                return mod;
        }
        return null;
    }

    public static void copyFile(Path path){

        List<String> command = new ArrayList<>();
        String file = path.toAbsolutePath().toString();

        switch (OperatingSystem.current()) {
            case WINDOWS:
                command.add("powershell.exe");
                command.add("Set-Clipboard");
                command.add("-Path");
                command.add("'" + file + "'");
                break;
            case MAC:
                command.add("osascript");
                command.add("-e");
                command.add("set the clipboard to (POSIX file \"" + file + "\")");
                break;
            case LINUX:
                String[][] cmds = {
                        {"bash", "-c", "echo \"" + file + "\" | xclip -selection clipboard -t text/uri-list"},
                        {"bash", "-c", "echo \"" + file + "\" | xsel --clipboard --input"},
                        {"bash", "-c", "echo \"" + file + "\" | wl-copy"}
                };
                for (String[] cmd : cmds) {
                    ProcessBuilder builder = new ProcessBuilder(cmd);
                    try {
                        builder.inheritIO().start().waitFor();
                        return;
                    } catch (Exception ignored) {}
                }
                break;
                // this doesn't really work...at all, and is kind of a broken impl to begin with. I need to fix it.
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        try {
            pb.inheritIO().start().waitFor();
        } catch (IOException | InterruptedException e) {
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
        Cogfly.launchGameAsync(profile.getBepInExPath().toString());
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