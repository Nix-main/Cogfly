package dev.ambershadow.cogfly;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import dev.ambershadow.cogfly.elements.profiles.ProfilesScreenElement;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.loader.ModFetcher;
import dev.ambershadow.cogfly.util.*;
import net.harawata.appdirs.AppDirs;
import net.harawata.appdirs.AppDirsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Cogfly {

    public static String version = "1.1.2";

    public static URL getResource(String path) {
        URL url = Cogfly.class.getResource(path);
        if (url == null) throw new IllegalStateException("Resource not found: " + path);
        return url;
    }

    public static Logger logger;
    public static List<String> excludedMods = new ArrayList<>(){
        {
            add("ebkr-r2modman");
            add("BepInEx-BepInExPack_Silksong");
            add("Kesomannen-GaleModManager");
        }
    };
    public static List<ModData> mods = null;
    public static String localDataPath;
    public static String roamingDataPath;
    public static File dataJson;
    public static Settings settings;
    public static URL packUrl;
    public static String latestPackVer;
    public static URL packUrlNoConsole;

    public static WinFolderPicker FOLDER_PICKER;
    public static WinTinyFileDialogs FILE_DIALOGS;
    public static @SuppressWarnings("unused") void main(String[] args) throws IOException {
        AppDirs dirs = AppDirsFactory.getInstance();
        localDataPath = dirs.getUserDataDir("Cogfly", null, "");
        roamingDataPath = dirs.getUserDataDir("Cogfly", null, "", true);
        String logDir = Paths.get(localDataPath).resolve("logs").toString();
        System.setProperty("app.log.dir", logDir);

        logger = LoggerFactory.getLogger(Cogfly.class);
        logger.info("Initializing...");

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Uncaught exception in thread {}", thread.getName(), throwable);
            Utils.throwNonFatalError(throwable);
        });
        dataJson = new File(localDataPath + "/settings.json");
        //noinspection ResultOfMethodCallIgnored
        dataJson.getParentFile().mkdirs();
        if (!dataJson.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dataJson.createNewFile();
        }
        settings = Settings.load(dataJson);
        if (args.length > 0){
            String arg = args[0].replace("cogfly://", "");
            if (arg.toLowerCase().startsWith("launch/")){
                String name = arg.substring(7);
                final String[] profile = new String[]{null};
                List<Path> paths = new ArrayList<>();
                paths.add(Path.of(settings.profileSavePath));
                paths.addAll(settings.profileSources.stream().map(Path::of).toList());
                System.out.println(name);
                for (Path profiles : paths) {
                    try(Stream<Path> stream = Files.list(profiles)){
                        stream.filter(path -> Files.isDirectory(path) && path.getFileName().toString().equalsIgnoreCase(name))
                                .findFirst()
                                .ifPresent(path -> profile[0] = path.toAbsolutePath().toString());
                    }
                }
                if (profile[0] != null){
                    Profile f = ProfileManager.loadProfile(new File(profile[0]));
                    launchGameAsync(true, f.getPath().toString(), f.getGamePath());
                } else {
                    JOptionPane.showMessageDialog(null, "This profile does not exist.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
            return;
        }
        packUrl = Cogfly.getResource("/packs/BepInExPack.zip");
        packUrlNoConsole = Cogfly.getResource("/packs/BepInExPack_NoConsole.zip");
        logger.info("Loaded settings");
        if (Utils.OperatingSystem.current() == Utils.OperatingSystem.WINDOWS){
            try {
                FOLDER_PICKER =
                        Native.load(Native.extractFromResourcePath("winfolderpicker").getAbsolutePath(),
                                WinFolderPicker.class
                                );
                FILE_DIALOGS =
                        Native.load(Native.extractFromResourcePath("wintinyfiledialogs").getAbsolutePath(),
                                WinTinyFileDialogs.class
                        );

                String commandKey = "Software\\Classes\\cogfly\\shell\\open\\command";
                Path exe = Paths.get(
                                Cogfly.class.getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI())
                        .getParent()
                        .getParent()
                        .resolve("Cogfly.exe");
                if (Advapi32Util.registryValueExists(
                        WinReg.HKEY_CURRENT_USER,
                        commandKey,
                        "")) {

                    String command = Advapi32Util.registryGetStringValue(
                            WinReg.HKEY_CURRENT_USER,
                            commandKey,
                            "");

                    if (!command.equals("\"" + exe + "\" \"%1\"")) {
                        registerWinKey(exe, "Software\\Classes\\cogfly");
                    }
                } else {
                    registerWinKey(exe, "Software\\Classes\\cogfly");
                }
            } catch (UnsatisfiedLinkError | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        long start = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> downloadBepInExNoConsole(Paths.get(settings.gamePath)));
        List<JsonObject> m = ModFetcher.getAllMods();
        List<ModData> data = new ArrayList<>();
        m.forEach(object -> {
            if (object.get("full_name").getAsString().equals("BepInEx-BepInExPack_Silksong")){
                latestPackVer = object.get("versions").getAsJsonArray().get(0).getAsJsonObject().get("version_number").getAsString();
            }
            if (object.get("is_deprecated").getAsBoolean())
                return;
            if (object.get("has_nsfw_content").getAsBoolean())
                return;
            if (excludedMods.contains(object.get("full_name").getAsString()))
                return;
            data.add(new ModData(object));
        });
        data.sort(
                Comparator.comparing(
                        o -> o.getName().toLowerCase(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                ));
        Cogfly.mods = data;
        logger.info("Loaded and parsed mods in {} milliseconds", (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        ProfileManager.loadProfiles();
        logger.info("Loaded profiles in {} milliseconds", (System.currentTimeMillis() - start));
        UIManager.put("TextComponent.arc", 5);
        logger.info("Showing UI");
        FrameManager.getOrCreate().frame.setVisible(true);
        showEarlyDialogs();
    }

    public static void downloadBepInExNoConsole(Path path){
        Path bepindll = path.resolve("BepInEx/core/BepInEx.dll");
        if (Files.exists(bepindll))
            return;
        if (!Files.exists(path))
            return;
        Utils.downloadAndExtract(packUrlNoConsole, path);
    }
    public static void downloadBepInEx(Path path){
        Path bepindll = path.resolve("BepInEx/core/BepInEx.dll");
        if (Files.exists(bepindll))
            return;
        Utils.downloadAndExtract(packUrl, path);
    }

    public static List<ModData> sortList(SortingType type, String direction, Profile profile, boolean installedOnly){
        List<ModData> mods = getDisplayedMods(profile, installedOnly);
        switch (type) {
            case NAME:
                mods.sort(
                        Comparator.comparing(
                                o -> o.getName().toLowerCase(),
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ));
                break;
            case DOWNLOADS:
                mods.sort(Comparator.comparingInt(ModData::getTotalDownloads));
                break;
            case DATE_CREATED:
                mods.sort(Comparator.comparing(mod -> Instant.parse(mod.getDateCreated())));
                break;
            case DATE_UPDATED:
                mods.sort(Comparator.comparing(mod -> Instant.parse(mod.getDateModified())));
                break;
        }
        if (direction.equalsIgnoreCase("descending")){
            mods = mods.reversed();
        }
        return mods;
    }

    public static List<ModData> getDisplayedMods(Profile profile, boolean installedOnly){
        if (installedOnly)
            return profile.getInstalledMods();
        List<ModData> mds = new ArrayList<>(profile.getManualMods());
        mds.addAll(mods);
        return mds;
    }

    private static void showEarlyDialogs(){
        if (settings.getData() != null && settings.getData().has("profileSavePath")) {
            Cogfly.settings.profileSavePath = settings.getData().get("profileSavePath").getAsString();
        } else {
            logger.info("No stored profile save path! Prompting:");
            JDialog prompt = new JDialog(FrameManager.getOrCreate().frame, "Profile Save Path", true);
            prompt.setLayout(new BorderLayout());
            prompt.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            prompt.setResizable(false);
            prompt.setPreferredSize(new Dimension(450, 160));
            prompt.pack();
            prompt.setLocationRelativeTo(FrameManager.getOrCreate().frame);

            JPanel texts = new JPanel(new BorderLayout());
            JLabel first = new JLabel("You don't have a path on file for saving profiles. ");
            first.setHorizontalAlignment(SwingConstants.CENTER);
            JLabel second = new JLabel("Please select one, or click Confirm for the default.");
            second.setHorizontalAlignment(SwingConstants.CENTER);
            JLabel third = new JLabel("(" + settings.profileSavePath + ")");
            third.setHorizontalAlignment(SwingConstants.CENTER);
            texts.add(first, BorderLayout.NORTH);
            texts.add(second, BorderLayout.CENTER);
            texts.add(third, BorderLayout.SOUTH);
            texts.setAlignmentX(Component.CENTER_ALIGNMENT);
            prompt.add(texts, BorderLayout.NORTH);

            JButton path = new JButton("Click here to select a file.");
            path.addActionListener(_ -> Utils.pickFolder((folder) -> path.setText(folder.toFile().getAbsolutePath())));
            JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            centerPanel.add(path);
            prompt.add(centerPanel, BorderLayout.CENTER);

            JButton confirm = new JButton("Confirm");

            confirm.addActionListener(_ -> {
                settings.profileSavePath =
                        !path.getText().equals("Click here to select a file.") ? path.getText() : settings.profileSavePath;
                prompt.dispose();
                settings.save();
            });
            JPanel confirmPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            confirmPanel.add(confirm);
            prompt.add(confirmPanel, BorderLayout.SOUTH);

            prompt.pack();
            prompt.setVisible(true);
        }
        if (settings.gamePath.isEmpty()) {
            logger.info("No game path! Prompting:");
            JDialog prompt = new JDialog(FrameManager.getOrCreate().frame, "Game Path", true);
            prompt.setLayout(new BorderLayout());
            prompt.setLocationRelativeTo(null);
            prompt.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            prompt.setResizable(false);
            prompt.setPreferredSize(new Dimension(450, 140));
            prompt.pack();
            prompt.setLocationRelativeTo(FrameManager.getOrCreate().frame);

            JPanel texts = new JPanel(new BorderLayout());
            JLabel first = new JLabel("You don't have a path on file for your silksong installation. ");
            first.setHorizontalAlignment(SwingConstants.CENTER);
            JLabel second = new JLabel("Please select one.");
            second.setHorizontalAlignment(SwingConstants.CENTER);
            texts.add(first, BorderLayout.NORTH);
            texts.add(second, BorderLayout.CENTER);
            texts.setAlignmentX(Component.CENTER_ALIGNMENT);
            prompt.add(texts, BorderLayout.NORTH);

            JButton path = new JButton("Click here to select a file");
            JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            centerPanel.add(path);
            prompt.add(centerPanel, BorderLayout.CENTER);

            JButton confirm = new JButton("Confirm");
            confirm.setEnabled(false);

            confirm.addActionListener(_ -> {
                settings.gamePath = path.getText();
                prompt.dispose();
                settings.save();
            });

            path.addActionListener(_ -> Utils.pickFile((file) -> {
                path.setText(file.toFile().getParentFile().getAbsolutePath());
                confirm.setEnabled(true);
            }, "Hollow Knight Silksong", "exe", "app", ""));
            JPanel confirmPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            confirmPanel.add(confirm);
            prompt.add(confirmPanel, BorderLayout.SOUTH);

            prompt.pack();
            prompt.setVisible(true);
        }


        if (ProfileManager.profiles.isEmpty() && !settings.baseGameEnabled){
            int confirm = JOptionPane.showConfirmDialog(FrameManager.getOrCreate().frame,
                    "You don't have any profiles! Are you ready to create one?",
                    "Profile Onboarding",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                FrameManager.getOrCreate().setPage(
                        FrameManager.CogflyPage.PROFILES,
                        FrameManager.getOrCreate().profilesPageButton
                );
                ProfilesScreenElement.createPrompt(() -> JOptionPane.showMessageDialog(
                        FrameManager.getOrCreate().frame,
                        "Congratulations on creating your first profile! Click on its icon to manage it and install mods!",
                        "Profile Onboarding",
                        JOptionPane.INFORMATION_MESSAGE));
            }
        }

        String latestVer = ((Supplier<String>)() -> {
            try (HttpClient client = HttpClient.newHttpClient()){
                HttpRequest request = HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("https://api.github.com/repos/nix-main/Cogfly/releases"))
                        .build();
                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    return JsonParser.parseString(response.body()).getAsJsonArray().get(0).getAsJsonObject().get("tag_name").getAsString();
                } catch (IOException | InterruptedException e) {
                    if (e instanceof ConnectException)
                        return version;
                    throw new RuntimeException(e);
                } catch (IllegalStateException e) {
                    JOptionPane.showMessageDialog(FrameManager.getOrCreate().frame, "The github rate limit was reached, or another IllegalStateException was encountered.");
                    return version;
                }
            }
        }).get();
        if (!version.equals(latestVer)) {
            int update = JOptionPane.showOptionDialog(
                    FrameManager.getOrCreate().frame,
                    String.format("There is an update available! You are using version %s. The latest version is %s.", version, latestVer),
                    "Update",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new Object[]{
                            "Open Release Page",
                            "Close"
                    },
                    null
            );
            if (update == JOptionPane.YES_OPTION) {
                Utils.openURI(URI.create("https://github.com/nix-main/Cogfly/releases/latest"));
            }
        }
    }


    private static void showLaunchError(String details) {
        String[] lines = details.split("\n");
        Path logFile = Paths.get(localDataPath).resolve("logs/launch-error.log");
        boolean truncated = lines.length > 20;
        if (truncated) {
            try {
                Files.writeString(logFile, details);
            } catch (IOException e) {
                logger.error("Failed to write launch error log", e);
            }
        }
        String message = truncated
                ? "%s\n... (%d lines total)".formatted(String.join("\n", Arrays.copyOf(lines, 20)), lines.length)
                : details;
        SwingUtilities.invokeLater(() -> {
            if (!truncated) {
                JOptionPane.showMessageDialog(null, message, "Game Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int choice = JOptionPane.showOptionDialog(null, message, "Game Error",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null,
                    new Object[]{"Open Log", "OK"}, "OK");
            if (choice == 0) Utils.openPath(logFile.getParent());
        });
    }

    public static void launchGameAsync(boolean enabled, String path, String gamePath){
        CompletableFuture.runAsync(() -> {
            logger.info("Launching game. OS: {}, Path: {}", Utils.OperatingSystem.current(), path);
            ProcessBuilder builder = new ProcessBuilder();
            List<String> cmds = new ArrayList<>();
            Path game = Paths.get(gamePath);
            Path gameAppPath = game.resolve(Utils.getGameExecutable());
            if (Utils.OperatingSystem.current().equals(Utils.OperatingSystem.MAC)) {
                builder.directory(game.toFile());
                cmds.add("arch");
                cmds.add("-x86_64");
                cmds.add("sh");
                cmds.add(Utils.getGameExecutable());
            } else if (Utils.OperatingSystem.current().equals(Utils.OperatingSystem.LINUX)) {
                builder.directory(game.toFile());
                cmds.add("setsid");
                cmds.add("sh");
                cmds.add(Utils.getGameExecutable());
            } else {
                cmds.add("cmd");
                cmds.add("/c");
                cmds.add("start");
                cmds.add("\"\"");
                cmds.add(gameAppPath.toString());
            }
            cmds.add("--doorstop-enabled");
            cmds.add(String.valueOf(enabled));
            if (enabled) {
                cmds.add("--doorstop-target-assembly");
                cmds.add(Paths.get(path).resolve("core/BepInEx.Preloader.dll").toString());
            }
            builder.command(cmds);
            logger.info("Launch command: {}", cmds);
            /*if (settings.launchWithSteam) {
                logger.info("Launching with Steam Client");
                if (Files.exists(game.resolve("steam_appid.txt")))
                    try {
                        Files.delete(game.resolve("steam_appid.txt"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
            } else {
                logger.info("Launching standalone");
                if (!Files.exists(game.resolve("steam_appid.txt")))
                    try {
                        Files.writeString(game.resolve("steam_appid.txt"), "1030300");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
            }*/
            try {
                Process process = builder.start();
                CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> {
                    try { return new String(process.getInputStream().readAllBytes()); }
                    catch (IOException e) { return ""; }
                });
                CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
                    try { return new String(process.getErrorStream().readAllBytes()); }
                    catch (IOException e) { return ""; }
                });
                int exitCode = process.waitFor();
                String stdout = stdoutFuture.join();
                String stderr = stderrFuture.join();
                if (exitCode != 0) {
                    String details = Stream.of(
                            stdout.isBlank() ? null : "stdout: " + stdout.trim(),
                            stderr.isBlank() ? null : "stderr: " + stderr.trim()
                    ).filter(Objects::nonNull).collect(Collectors.joining("\n"));
                    if (details.isBlank()) details = "Process exited with code " + exitCode;
                    logger.warn("Game process exited with code {}\n{}", exitCode, details);
                    showLaunchError(details);
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).exceptionally(e -> {
            logger.error("Failed to launch game", e);
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null,
                    "Failed to launch game: " + e.getCause().getMessage(),
                    "Game Error",
                    JOptionPane.ERROR_MESSAGE)
            );
            return null;
        });
    }

    private static void registerWinKey(Path exe, String key) {
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, key);
        Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                key,
                "",
                "URL:Cogfly Protocol");
        Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                key,
                "URL Protocol",
                "");
        Advapi32Util.registryCreateKey(
                WinReg.HKEY_CURRENT_USER,
                key + "\\shell\\open\\command");
        Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                key + "\\shell\\open\\command",
                "",
                "\"" + exe + "\" \"%1\""
        );
    }

    public enum SortingType {
        NAME,
        DOWNLOADS,
        DATE_CREATED,
        DATE_UPDATED,
    }
}