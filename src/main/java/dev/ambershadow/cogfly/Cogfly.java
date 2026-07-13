package dev.ambershadow.cogfly;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.elements.ModPanelElement;
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
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Cogfly {

    public static String version = Cogfly.class.getPackage().getImplementationVersion() != null
            ? Cogfly.class.getPackage().getImplementationVersion()
            : "";

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
            add("silksong_modding-BepInExPack_Silksong");
            add("Kesomannen-GaleModManager");
        }
    };
    public static List<ModData> mods = new ArrayList<>();
    public static Path localDataPath;
    public static Path roamingDataPath;
    public static File dataJson;
    public static Settings settings;
    private static URL packUrl;
    public static String latestPackVer;
    public static WinFolderPicker FOLDER_PICKER;
    public static WinTinyFileDialogs FILE_DIALOGS;
    public static Path doorstop;
    public static Path pack;
    private static String oldPackVersion;
    public static boolean createdProfiles;
    public static @SuppressWarnings("unused") void main(String[] args) throws IOException {
        AppDirs dirs = AppDirsFactory.getInstance();
        localDataPath = Paths.get(dirs.getUserDataDir("Cogfly", null, ""));
        roamingDataPath = Paths.get(dirs.getUserDataDir("Cogfly", null, "", true));
        System.setProperty("app.log.dir", localDataPath.resolve("logs").toString());

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
        if (!Files.exists(localDataPath.resolve("icon.ico")))
            try(InputStream stream = Cogfly.getResource("/assets/icon.ico").openStream()) {
                Files.write(localDataPath.resolve("icon.ico"), stream.readAllBytes());
            }
        if (!Files.exists(localDataPath.resolve("icon.png")))
            try(InputStream stream = Assets.icon.url().openStream()) {
                Files.write(localDataPath.resolve("icon.png"), stream.readAllBytes());
            }
        if (!Files.exists(localDataPath.resolve("icon.icns")))
            try(InputStream stream = Cogfly.getResource("/assets/icon.icns").openStream()) {
                Files.write(localDataPath.resolve("icon.icns"), stream.readAllBytes());
            }
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
                        registerWinKey(exe);
                    }
                } else {
                    registerWinKey(exe);
                }
            } catch (UnsatisfiedLinkError | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        final long modStart = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
                    List<JsonObject> m = ModFetcher.getAllMods();
                    List<ModData> data = new ArrayList<>();
                    for (JsonObject object : m) {
                        if (object.get("full_name").getAsString().equals("silksong_modding-BepInExPack_Silksong")) {
                            JsonObject version = object.get("versions").getAsJsonArray().get(0).getAsJsonObject();
                            try {
                                packUrl = URL.of(URI.create(version.get("download_url").getAsString()), null);
                            } catch (MalformedURLException e) {
                                throw new RuntimeException(e);
                            }
                            latestPackVer = version.get("version_number").getAsString();
                        }
                        if (object.get("is_deprecated").getAsBoolean())
                            continue;
                        if (object.get("has_nsfw_content").getAsBoolean())
                            continue;
                        if (excludedMods.contains(object.get("full_name").getAsString()))
                            continue;
                        data.add(new ModData(object));
                    }
                    data.sort(
                            Comparator.comparing(
                                    o -> o.getName().toLowerCase(),
                                    Comparator.nullsLast(Comparator.naturalOrder())
                            ));
                    Cogfly.mods = data;
                }
        ).whenComplete((_, _) -> {
            long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            logger.info("Loaded and parsed mods in {} milliseconds", (System.currentTimeMillis() - modStart));
            SwingUtilities.invokeLater(() -> {
                ModPanelElement.redrawAll();
                long start = System.currentTimeMillis();
                ProfileManager.loadProfiles();
                logger.info("Loaded profiles in {} milliseconds", (System.currentTimeMillis() - start));
                Cogfly.createdProfiles = true;
                ProfilesScreenElement.queueRefresh();
                FrameManager.getOrCreate().getCurrentPage().reload();
            });
            try {
                downloadPack(latestPackVer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (settings.baseGameEnabled)
                downloadBepInEx(Path.of(settings.gamePath));
            queuedPaths.forEach(Cogfly::downloadBepInEx);
            queuedPaths = null;
        });
        UIManager.put("TextComponent.arc", 5);
        logger.info("Showing UI");
        FrameManager.getOrCreate().frame.setVisible(true);
        showEarlyDialogs();
    }

    private static void downloadPack(String version) throws IOException {
        oldPackVersion = "-1";
        Path ver = localDataPath.resolve("pack_version.txt");
        if (Files.exists(ver)){
            oldPackVersion = Files.readString(ver);
        }
        Cogfly.pack = localDataPath.resolve("BepInExPack");
        doorstop = localDataPath.resolve("doorstop");
        if (version.equals(oldPackVersion))
            return;
        Path pack = localDataPath.resolve("bex_pack");
        Utils.downloadAndExtract(packUrl, pack);
        Utils.deleteFolder(Cogfly.pack);
        Files.move(pack.resolve("BepInExPack"), Cogfly.pack);
        Utils.deleteFolder(pack);
        Utils.deleteFolder(doorstop);
        Files.createDirectory(doorstop);
        Files.delete(Cogfly.pack.resolve("changelog.txt"));
        try (Stream<Path> files = Files.list(Cogfly.pack)) {
            files.forEach(file -> {
                if (!Files.isDirectory(file)) {
                    try {
                        Files.move(file, doorstop.resolve(file.getFileName()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        Files.write(ver, version.getBytes());
    }

    private static void downloadDoorstop(Path path){
        if (hasDoorstop(path))
            return;
        try(Stream<Path> files = Files.list(doorstop)) {
            for (Path file : files.toList()) {
                if (!latestPackVer.equals(oldPackVersion)) {
                    Files.deleteIfExists(path.resolve(file.getFileName()));
                }
                else if (Files.exists(path.resolve(file.getFileName())))
                    continue;
                Files.copy(file, path.resolve(file.getFileName()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean hasDoorstop(Path path) {
        boolean exists = false;
        try (Stream<Path> files = Files.list(doorstop)) {
            for (Path file : files.toList()) {
                exists = Files.exists(path.resolve(file.getFileName()));
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return exists;
    }

    private static boolean setLaunchArgs(Path vdf, String args) throws IOException, InterruptedException {
        if (!Files.exists(vdf))
            return false;
        int silksongIndex = -1;
        int launchOptsIndex = -1;
        boolean isSilk = false;
        String launchOpts = "";
        List<String> lines = Files.readAllLines(vdf);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String val = line.trim().replaceAll("\"", "");
            if (val.matches("\\d+"))
                isSilk = false;
            if (val.equals("1030300")){
                silksongIndex = i;
                isSilk = true;
            }
            if (val.startsWith("LaunchOptions") && isSilk) {
                launchOpts = line;
                launchOptsIndex = i;
                logger.info("{}, {}", launchOptsIndex, launchOpts);
                break;
            }
        }
        if (silksongIndex == -1)
            return false;
        if (launchOpts.contains("run_bepinex.sh"))
            return true;
        if (!settings.finishedSteamPopup){
            int opt = JOptionPane.showOptionDialog(FrameManager.getOrCreate().frame,
                    "Cogfly is trying to add " + "\"" + args + "\" to your steam launch arguments, this is necessary for the Launch with Steam setting to work on Mac and Linux. This will not overwrite your existing launch arguments, they will still work. You will not be shown this popup again, but can always modify this value in your settings.",
                    "Steam Launch Args",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    Assets.icon.getAsIcon(),
                    new Object[]{"Allow", "Don't Allow"},
                    "Allow");
            if (opt == JOptionPane.YES_OPTION)
                settings.acceptedSteamArgs = true;
            settings.finishedSteamPopup = true;
            settings.save();
        }
        if (!settings.acceptedSteamArgs)
            return true;
        String val;
        int index;
        if (launchOptsIndex == -1 || launchOpts.isEmpty()){
            String[] vals = lines.get(silksongIndex+2).split("\"");
            vals[1] = "LaunchOptions";
            vals[3] = args + "\"";
            val = String.join("\"", vals);
            index = silksongIndex + 2;
        }
        else {
            String[] vals = launchOpts.split("\"");
            if (vals.length > 3) {
                if (vals[3].contains("%command%")) {
                    List<String> a = new ArrayList<>(Arrays.stream(vals[3].split("%command%")).toList());
                    a.add(1, args + " %command%");
                    a.add("\"");
                    vals[3] = String.join("", a);
                }
                else
                    vals[3] = args + " %command% " + vals[3] + "\"";
            }
            else
                vals[2] = " \t\"" + args + " %command% \"";
            lines.remove(launchOptsIndex);
            index = launchOptsIndex;
            val = String.join("\"", vals);
        }
        Utils.openURI(URI.create("steam://exit"));
        ProcessHandle.allProcesses()
                .filter((p) ->
                        p.info().command().map(cmd -> cmd.toLowerCase().endsWith("steam.exe")
                                || cmd.toLowerCase().endsWith("steam")
                        || cmd.toLowerCase().endsWith("steam_osx")).orElse(false))
                .findFirst()
                .ifPresentOrElse(steam -> steam.onExit().join(), () -> {});
        lines.add(index, String.join("\"", val));
        Files.write(vdf, lines);
        logger.info(val);
        return true;
    }

    private static List<Path> queuedPaths = new ArrayList<>();

    public static void downloadBepInEx(Path path) {
        if (pack == null) {
            queuedPaths.add(path);
            return;
        }
        Path bepindll = path.resolve("BepInEx/core/BepInEx.dll");
        if (Files.exists(bepindll))
            return;
        logger.info("{}", path);
        try(Stream<Path> files = Files.walk(pack.resolve("BepInEx"))) {
            for (Path file : files.toList()) {
                Path relative = pack.resolve("BepInEx").relativize(file);
                Path newF = path.resolve("BepInEx").resolve(relative);
                if (!latestPackVer.equals(oldPackVersion) && !Files.isDirectory(file)) {
                    Files.deleteIfExists(newF);
                }
                if (Files.exists(newF))
                    continue;
                if (Files.isDirectory(file)) {
                    Files.createDirectories(newF);
                    continue;
                }
                Files.copy(file, newF);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<ModData> sortList(SortingType type, String direction, Profile profile, boolean installedOnly){
        List<ModData> mods = new ArrayList<>(getDisplayedMods(profile, installedOnly));
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

    private static void showEarlyDialogs() {
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
        if (!settings.dontShowPatreonAgain) {
            int val = JOptionPane.showOptionDialog(
                    FrameManager.getOrCreate().frame,
                    "I have a Patreon! If you want to support me and Cogfly, please do so at https://www.patreon/com/c/AmberShadowo",
                    "Support me?",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    Assets.icon.getAsIcon(),
                    new Object[]{"Close & Don't Show Again", "Open My Patreon", "Close"},
                    "Open My Patreon");
            if (val == JOptionPane.YES_OPTION){
                settings.dontShowPatreonAgain = true;
                settings.save();
            }
            else if (val == JOptionPane.NO_OPTION)
                Utils.openURI(URI.create("https://www.patreon.com/c/AmberShadowo?utm_medium=unknown&utm_source=join_link&utm_campaign=creatorshare_creator&utm_content=copyLink"));
        }
    }


    private static void showLaunchError(String details) {
        String[] lines = details.split("\n");
        Path logFile = localDataPath.resolve("logs/launch-error.log");
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
            Path game = Paths.get(gamePath);
            if (enabled)
                downloadDoorstop(game);
            List<String> args = new ArrayList<>();
            args.add("--doorstop-enabled");
            args.add(String.valueOf(enabled));
            if (enabled) {
                args.add("--doorstop-target-assembly");
                String target = Paths.get(path).resolve("core/BepInEx.Preloader.dll").toString();
                target = Utils.OperatingSystem.current() == Utils.OperatingSystem.WINDOWS ? "\"" + target + "\"" : target;
                if (settings.launchWithSteam)
                    target = target.replace("/", "%2F");
                args.add(target);
            }
            String arg = String.join(" ", args);
            logger.info("Launch arguments: {}", arg);
            if (settings.launchWithSteam) {
                arg = URLEncoder.encode(arg, StandardCharsets.UTF_8);
                String cmd = "steam://rungameid/1030300//" + arg + "/";
                if (!gamePath.equals(settings.gamePath)){
                    try {
                        long val = getSteamIdSafe(game);
                        if (val == -1){
                            JOptionPane.showMessageDialog(FrameManager.getOrCreate().frame,
                                    "You must add this executable as a non-steam game in your steam client to launch this profile through steam.",
                                    "Missing non-steam game!",
                                    JOptionPane.WARNING_MESSAGE, Assets.icon.getAsIcon());
                            return;
                        }
                        // steam doesn't pass launch args to non-steam games because it's CRINGE and LAME
                        cmd = "steam://rungameid/" + Long.toUnsignedString(val);
                        // also you actually CAN launch a non-steam game by its signed ID but this is easier for clarity
                        List<String> lines = Files.readAllLines(game.resolve("doorstop_config.ini"));
                        for (String line : lines) {
                            if (line.startsWith("enabled"))
                                lines.set(lines.indexOf(line), "enabled=" + enabled);
                            if (line.startsWith("target_assembly"))
                                lines.set(lines.indexOf(line), "target_assembly=" + Paths.get(path).resolve("core/BepInEx.Preloader.dll"));
                        }
                        Files.write(game.resolve("doorstop_config.ini"), lines);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    if (Utils.OperatingSystem.current() != Utils.OperatingSystem.WINDOWS) {
                        try {
                            for (Path config : getSteamFolders()) {
                                Path vdf = config.resolve("localconfig.vdf");
                                boolean argsSet = setLaunchArgs(vdf, "/bin/sh \\\"" + game.resolve("run_bepinex.sh").toAbsolutePath() + "\\\"");
                                if (argsSet)
                                    break;
                            }
                        } catch (IOException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                logger.info("Launching with Steam Client. Command={}", cmd);
                cmd = cmd.replace("+", "%20");
                Utils.openURI(URI.create(cmd));
            } else {
                List<String> cmds = new ArrayList<>();
                switch (Utils.OperatingSystem.current()) {
                    case MAC -> {
                        cmds.add("arch");
                        cmds.add("-x86_64");
                        cmds.add("sh");
                    }
                    case LINUX -> {
                        cmds.add("setsid");
                        cmds.add("sh");
                    }
                    default -> {
                        cmds.add("cmd");
                        cmds.add("/c");
                        cmds.add("start");
                        cmds.add("\"\"");
                    }
                }
                cmds.add(Utils.getGameExecutable());
                ProcessBuilder builder = new ProcessBuilder();
                builder.directory(game.toFile());
                cmds.addAll(args);
                builder.command(cmds);
                logger.info("Launching standalone. Command={}, Directory={}", String.join(" ", cmds), game);
                try {
                    Process process = builder.start();
                    Supplier<String> supplier = () -> {
                        try {
                            return new String(process.getErrorStream().readAllBytes());
                        } catch (IOException e) {
                            return "";
                        }
                    };
                    CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(supplier);
                    CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(supplier);
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

    private static List<Path> getSteamFolders() throws IOException {
        Path steamRoot = switch (Utils.OperatingSystem.current()) {
            case WINDOWS -> Paths.get(Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "Software\\Valve\\Steam", "SteamPath"));
            case LINUX -> Paths.get(System.getProperty("user.home"), ".local/share/Steam");
            case MAC -> Paths.get(System.getProperty("user.home"), "Library/Application Support/Steam");
            default -> null;
        };
        if (steamRoot == null) return List.of();
        List<Path> paths = new ArrayList<>();
        for (int id : getSteamUserIds(steamRoot.resolve("config", "loginusers.vdf"))){
            paths.add(steamRoot.resolve("userdata", id + "/config"));
        }
        return paths;
    }

    private static long getSteamIdSafe(Path executable) throws IOException {
        for (Path folder : getSteamFolders()) {
            Path vdf = folder.resolve("shortcuts.vdf");
            if (!Files.exists(vdf)) continue;
            long appid = getSteamId(executable, vdf);
            logger.info("Found Steam app id {} for executable {} under user {}", appid, executable, vdf);
            // conversion from 64-bit appid to BPID
            // as seen at https://github.com/ValveSoftware/steam-for-linux/issues/9463#issuecomment-2558366504
            // and https://gist.github.com/sonic2kk/934fc97d27d9d8c4ac9c1d817e163bf1
            if (appid != -1) return (appid << 32) | 0x02000000;
        }
        return -1;
    }

    private static Set<Integer> getSteamUserIds(Path vdf) throws IOException {
        Map<Long, Integer> map = new LinkedHashMap<>();
        String lastId = null;
        int lastMostRecent = 0;
        for (String line : Files.readAllLines(vdf)) {
            String trimmed = line.trim();
            if (trimmed.matches("\"\\d+\"")) {
                if (lastId != null)
                    map.put(Long.parseLong(lastId), lastMostRecent);
                lastId = trimmed.replaceAll("\"", "");
                lastMostRecent = 0;
            }
            if (trimmed.startsWith("\"MostRecent\""))
                lastMostRecent = Integer.parseInt(trimmed.replaceAll("[^0-9]", ""));
        }
        if (lastId != null)
            map.put(Long.parseLong(lastId), lastMostRecent);
        return map.keySet().stream()
                .sorted(Comparator.comparing(map::get))
                .map(l -> (int)(l - 0x0110000100000000L))
                .collect(Collectors.toCollection(LinkedHashSet::new)).reversed();
    }


    // documentation of the steam VDF format can be found at https://developer.valvesoftware.com/wiki/Binary_VDF
    private static long getSteamId(Path exePath, Path vdf) {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(vdf))) {

            String exe = null;
            Integer appid = null;
            // this ^ is necessary because the appid key comes before the exe path
            // for me, exe always comes 3 entries after appid, so I could theoretically just skip to it, but I wasn't sure if this was safe
            // or the same for everybody/across systems, so I'm doing this instead
            while (true) { // always exits either exceptionally or with a return
                switch (in.readUnsignedByte()) {
                    case 0x00 -> getString(in);
                    case 0x01 -> {
                        String key = getString(in), value = getString(in);
                        if (key.equals("Exe"))
                            exe = value.replace("\"", "");
                    }
                    case 0x02 -> {
                        String key = getString(in);
                        int value = Integer.reverseBytes(in.readInt());
                        if (key.equals("appid"))
                            appid = value;
                    }
                    case 0x08 -> { // app read ended
                        if (exe != null && appid != null && Path.of(exe).getParent().equals(exePath))
                            return appid;
                    }
                }
            }
        } catch (EOFException ignored) {
            // reached end of file, no game found
            return -1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // steam shortcut.vdf strings are terminated by a null byte as documented at https://developer.valvesoftware.com/wiki/Binary_VDF
    // which java natively doesn't handle
    private static String getString(DataInputStream in) throws IOException {
        byte[] buffer = new byte[256];
        int index = 0;
        while ((buffer[index] = in.readByte()) != 0) {
            index++;
        }
        buffer = Arrays.copyOf(buffer, index);
        return new String(buffer, StandardCharsets.UTF_8);
    }

    private static void registerWinKey(Path exe) {
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, "Software\\Classes\\cogfly");
        Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                "Software\\Classes\\cogfly",
                "",
                "URL:Cogfly Protocol");
        Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                "Software\\Classes\\cogfly",
                "URL Protocol",
                "");
        Advapi32Util.registryCreateKey(
                WinReg.HKEY_CURRENT_USER,
                "Software\\Classes\\cogfly\\shell\\open\\command");
        Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                "Software\\Classes\\cogfly\\shell\\open\\command",
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