package dev.ambershadow.cogfly;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.elements.ModPanelElement;
import dev.ambershadow.cogfly.elements.profiles.ProfilesScreenElement;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.loader.ModFetcher;
import dev.ambershadow.cogfly.profile.Profile;
import dev.ambershadow.cogfly.profile.ProfileManager;
import dev.ambershadow.cogfly.util.*;
import dev.ambershadow.cogfly.util.swing.FrameManager;
import net.harawata.appdirs.AppDirs;
import net.harawata.appdirs.AppDirsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Cogfly {

    public static final String version = Cogfly.class.getPackage().getImplementationVersion() != null
            ? Cogfly.class.getPackage().getImplementationVersion()
            : "";

    private static String latestVersion = version;

    public static URL getResource(String path) {
        URL url = Cogfly.class.getResource(path);
        if (url == null) throw new IllegalStateException("Resource not found: " + path);
        return url;
    }

    public static Logger logger;
    public static final List<String> excludedMods = new ArrayList<>() {
        {
            add("ebkr-r2modman");
            add("BepInEx-BepInExPack_Silksong");
            add("silksong_modding-BepInExPack_Silksong");
            add("Kesomannen-GaleModManager");
        }
    };
    public static Map<String, ModData> mods = new HashMap<>();
    public static Path localDataPath;
    public static Path roamingDataPath;
    public static Path dataJson;
    public static Settings settings;
    public static boolean createdProfiles;
    public static boolean showUnknownHost;
    private static String windowsSha256;
    private static String macSha256;
    public static Path tempDir;
    private static HashMap<String, List<String>> failedDownloads = new HashMap<>();
    
    public static @SuppressWarnings("unused") void main(String[] args) throws IOException {
        LocaleManager.setLocale(Locale.getDefault());
        AppDirs dirs = AppDirsFactory.getInstance();
        localDataPath = Paths.get(dirs.getUserDataDir("Cogfly", null, ""));
        roamingDataPath = Paths.get(dirs.getUserDataDir("Cogfly", null, "", true));
        tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "cogfly-downloads");
        System.setProperty("app.log.dir", localDataPath.resolve("logs").toString());

        logger = LoggerFactory.getLogger(Cogfly.class);
        logger.info("Initializing...");
        if (Files.exists(tempDir)) {
            try (Stream<Path> stream = Files.walk(tempDir)) {
                for (Path path : stream.toList().reversed()) {
                    if (!Files.isDirectory(path)) {
                        Cogfly.logger.info("Cogfly previously failed to install {} for profile {}.", path.getFileName(), path.getParent().getFileName());
                        failedDownloads.computeIfAbsent(path.getParent().getFileName().toString(), k -> new ArrayList<>()).add(path.getFileName().toString().split("\\d")[0]);
                    }
                    Files.delete(path);
                }
            }
        }
        Files.createDirectory(tempDir);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Uncaught exception in thread {}", thread.getName(), throwable);
            throwNonFatalError(throwable);
        });
        dataJson = localDataPath.resolve("settings.json");
        Files.createDirectories(dataJson.getParent());
        if (!Files.exists(dataJson)) {
            Files.createFile(dataJson);
        }
        settings = Settings.load(dataJson);
        extractIcons();
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)) {
            Desktop.getDesktop().setOpenURIHandler(event -> {
                try {
                    handleArgs(event.getURI().toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        if (args.length > 0) {
            boolean a = handleArgs(args[0]);
            if (a)
                return;
        }
        logger.info("Loaded settings");
        switch (getOs()) {
            case WINDOWS -> WinUtils.init();
            case LINUX -> {
                if (System.getenv("APPIMAGE") != null) {
                    Files.createDirectories(localDataPath.resolve("updater"));
                    Path updaterSh = localDataPath.resolve("updater", "updater.sh");
                    try (InputStream stream = getResource("/updater.sh").openStream()) {
                        Files.write(updaterSh, stream.readAllBytes());
                        setExecutable(updaterSh);
                    }
                    Path updater = localDataPath.resolve("appimageupdatetool-x86_64.appimage");
                    try (InputStream stream = URL.of(URI.create("https://github.com/AppImageCommunity/AppImageUpdate/releases/latest/download/appimageupdatetool-x86_64.AppImage"), null).openStream()) {
                        Files.copy(stream, updater, StandardCopyOption.REPLACE_EXISTING);
                    }
                    setExecutable(updater);
                }
            }
            case MAC -> {
                Files.createDirectories(localDataPath.resolve("updater"));
                Path updaterMac = localDataPath.resolve("updater", "updater_mac.sh");
                try (InputStream stream = getResource("/updater_mac.sh").openStream()) {
                    Files.write(updaterMac, stream.readAllBytes());
                    setExecutable(updaterMac);
                }
            }
        }
        logger.info("Extracted updater.");
        runAsync(() -> {
            long start = System.currentTimeMillis();
            List<JsonObject> m = ModFetcher.getAllMods();
            List<ModData> data = new ArrayList<>();
            for (JsonObject object : m) {
                if (object.get("full_name").getAsString().equals("silksong_modding-BepInExPack_Silksong")) {
                    JsonObject version = object.get("versions").getAsJsonArray().get(0).getAsJsonObject();
                    try {
                        GameUtils.packUrl = URL.of(URI.create(version.get("download_url").getAsString()), null);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                    GameUtils.latestPackVer = version.get("version_number").getAsString();
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
            Cogfly.mods = data.stream().collect(Collectors.toMap(
                    ModData::getFullName,
                    Function.identity(),
                    (v, a) -> v,
                    HashMap::new
            ));
            logger.info("Loaded and parsed mods in {} milliseconds", (System.currentTimeMillis() - start));
            start = System.currentTimeMillis();
            ProfileManager.loadProfiles();
            logger.info("Loaded profiles in {} milliseconds", (System.currentTimeMillis() - start));
            Cogfly.createdProfiles = true;
        }).whenComplete((_, _) -> {
            SwingUtilities.invokeLater(() -> {
                ModPanelElement.redrawAll();
                ProfilesScreenElement.queueRefresh();
                if (FrameManager.getOrCreate().getCurrentPage() != null)
                    FrameManager.getOrCreate().getCurrentPage().reload();
                try {
                    showEarlyDialogs();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            GameUtils.afterLoad();
        });
        UIManager.put("TextComponent.arc", 5);
        logger.info("Showing UI");
        FrameManager.getOrCreate().frame.setVisible(true);
    }

    public static void setExecutable(Path path) throws IOException {
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);
        Files.setPosixFilePermissions(path, perms);
    }

    private static boolean handleArgs(String arg) throws IOException {
        Cogfly.logger.info("Received arguments: {}", arg);
        String a = arg.replace("cogfly://", "");
        if (a.toLowerCase().startsWith("launch/")) {
            String name = a.substring(7);
            final String[] profile = new String[]{null};
            List<Path> paths = new ArrayList<>();
            paths.add(Path.of(settings.profileSavePath));
            paths.addAll(settings.profileSources.stream().map(Path::of).toList());
            for (Path profiles : paths) {
                try(Stream<Path> stream = Files.list(profiles)) {
                    stream.filter(path -> Files.isDirectory(path) && path.getFileName().toString().equalsIgnoreCase(name))
                            .findFirst()
                            .ifPresent(path -> profile[0] = path.toAbsolutePath().toString());
                }
            }
            if (profile[0] != null) {
                Profile f = ProfileManager.loadProfile(Paths.get(profile[0]));
                if (Files.exists(localDataPath.resolve("doorstop")))
                    GameUtils.doorstop = localDataPath.resolve("doorstop");
                else {
                    List<JsonObject> m = new ArrayList<>(ModFetcher.getAllMods());
                    for (JsonObject object : m) {
                        if (object.get("full_name").getAsString().equals("silksong_modding-BepInExPack_Silksong")) {
                            JsonObject version = object.get("versions").getAsJsonArray().get(0).getAsJsonObject();
                            GameUtils.packUrl = URL.of(URI.create(version.get("download_url").getAsString()), null);
                            GameUtils.latestPackVer = version.get("version_number").getAsString();
                        }
                    }
                }
                GameUtils.launchModdedGame(f);
            } else {
                JOptionPane.showMessageDialog(null, LocaleManager.errorProfileNotExist.get(), LocaleManager.titleError.get(), JOptionPane.ERROR_MESSAGE);
            }
            return true;
        }
        return false;
    }

    private static void extractIcons() throws IOException {
        String ext = switch (getOs()) {
            case WINDOWS -> "ico";
            case MAC -> "icns";
            default -> "png";
        };
        if (!Files.exists(localDataPath.resolve("icon." + ext)))
            try(InputStream stream = getResource("/assets/icon." + ext).openStream()) {
                Files.write(localDataPath.resolve("icon." + ext), stream.readAllBytes());
            }
    }

    private static void autoUpdateWindows() throws IOException {
        new ProcessBuilder(
                "cmd.exe",
                "/c",
                "start",
                "powershell.exe",
                "-ExecutionPolicy", "Bypass",
                "-File",
                localDataPath.resolve("updater", "updater.ps1").toString(),
                ProcessHandle.current().pid() + "",
                String.format("https://github.com/Nix-main/Cogfly/releases/latest/download/Cogfly-%s-installer.exe", latestVersion),
                windowsSha256
        ).start();
        System.exit(0);
    }

    private static void autoUpdateAppImage() throws IOException {
        new ProcessBuilder(
                "bash", localDataPath.resolve("updater").resolve("updater.sh").toString(),
                ProcessHandle.current().pid() + "",
                System.getenv("APPIMAGE"),
                localDataPath.resolve("appimageupdatetool-x86_64.appimage").toString()
        ).start();
        System.exit(0);
    }

    private static void autoUpdateMac() throws IOException {
        new ProcessBuilder(
                "osascript",
                "-e",
                String.format(
                        "do shell script \"'%s' %s '%s' '%s'\" with administrator privileges",
                        localDataPath.resolve("updater").resolve("updater_mac.sh"),
                        ProcessHandle.current().pid(),
                        String.format("https://ambershadow.dev/Cogfly-%s.dmg", latestVersion),
                        macSha256
                )
        ).start();
        System.exit(0);
    }

    public static List<ModData> sortList(SortingType type, String direction, Profile profile, boolean installedOnly) {
        List<ModData> mods = new ArrayList<>(getDisplayedMods(profile, installedOnly));
        switch (type) {
            case NAME:
                mods.sort(
                        Comparator.comparing(
                                o -> o.getName().toLowerCase(),
                                Comparator.reverseOrder())
                        );
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
        if (direction.equalsIgnoreCase("descending")) {
            mods = mods.reversed();
        }
        return mods;
    }

    public static List<ModData> getDisplayedMods(Profile profile, boolean installedOnly) {
        if (installedOnly)
            return profile.getInstalledMods();
        List<ModData> mds = new ArrayList<>(profile.getManualMods());
        mds.addAll(mods.values());
        return mds;
    }

    private static void showEarlyDialogs() throws IOException {
        if (settings.getData() != null && settings.getData().has("profileSavePath")) {
            Cogfly.settings.profileSavePath = settings.getData().get("profileSavePath").getAsString();
        } else {
            logger.info("No stored profile save path! Prompting:");
            JDialog prompt = new JDialog(FrameManager.getOrCreate().frame, "Profile Save Path", true);
            prompt.setLayout(new BorderLayout());
            prompt.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            prompt.setResizable(true);
            prompt.setPreferredSize(new Dimension(450, 160));
            prompt.pack();
            prompt.setLocationRelativeTo(FrameManager.getOrCreate().frame);
            addPathPanel(
                    "You don't have a path on file for saving profiles.",
                    "Please select one, or click Confirm for the default.",
                    true,
                    () -> settings.profileSavePath,
                    (path) -> settings.profileSavePath =
                            !path.getText().equals(LocaleManager.buttonSelectFile.get()) ? path.getText() : settings.profileSavePath,
                    (path, _) -> FileUtils.pickFolder((folder) -> path.setText(folder.toFile().getAbsolutePath())),
                    prompt);
            prompt.pack();
            prompt.setVisible(true);
        }
        if (settings.gamePath.isEmpty()) {
            logger.info("No game path! Prompting:");
            JDialog prompt = new JDialog(FrameManager.getOrCreate().frame, "Game Path", true);
            prompt.setLayout(new BorderLayout());
            prompt.setLocationRelativeTo(null);
            prompt.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            prompt.setResizable(true);
            prompt.setPreferredSize(new Dimension(450, 140));
            prompt.pack();
            prompt.setLocationRelativeTo(FrameManager.getOrCreate().frame);
            addPathPanel("You don't have a path on file for your silksong installation. ",
                    "Please select one.",
                    false,
                    () -> null,
                    (path) -> settings.gamePath = path.getText(),
                    (path, confirm) ->
                        FileUtils.pickFile((file) -> {
                            path.setText(file.toFile().getParentFile().getAbsolutePath());
                            confirm.setEnabled(true);
                        }, "Hollow Knight Silksong", "exe", "app", ""),
                    prompt);
            prompt.pack();
            prompt.setVisible(true);
        }


        if (ProfileManager.profiles.isEmpty() && !settings.baseGameEnabled) {
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
                ProfilesScreenElement.createProfilePrompt(ProfilesScreenElement.defaultCallback, () -> JOptionPane.showMessageDialog(
                        FrameManager.getOrCreate().frame,
                        "Congratulations on creating your first profile! Click on its icon to manage it and install mods!",
                        "Profile Onboarding",
                        JOptionPane.INFORMATION_MESSAGE));
            }
        }

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("https://ambershadow.dev/api/cogfly/latest/"))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            windowsSha256 = obj.get("windowsSha256").getAsString();
            macSha256 = obj.get("macSha256").getAsString();
            latestVersion = obj.get("version").getAsString();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (!version.equals(latestVersion)) {
            int update = JOptionPane.showOptionDialog(
                    FrameManager.getOrCreate().frame,
                    String.format(LocaleManager.messageUpdateAvailable.get(), version, latestVersion),
                    LocaleManager.titleUpdate.get(),
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new Object[]{
                            "Update Automatically",
                            "Open Release Page",
                            LocaleManager.buttonClose.get()
                    },
                    "Update Automatically"
            );
            if (update == JOptionPane.YES_OPTION) {
                switch (getOs()) {
                    case WINDOWS -> autoUpdateWindows();
                    case LINUX -> {
                        if (System.getenv("APPIMAGE") != null)
                            autoUpdateAppImage();
                        else
                            JOptionPane.showMessageDialog(FrameManager.getOrCreate().frame, "Cogfly is managed by your system's package manager (apt/dnf/yum). Please run an upgrade through it to update Cogfly.", "No auto-update available.", JOptionPane.INFORMATION_MESSAGE);
                    }
                    case MAC -> autoUpdateMac();
                }
            }
            if (update == JOptionPane.NO_OPTION) {
                FileUtils.openURI(URI.create("https://github.com/nix-main/Cogfly/releases/latest"));
            }
        }
        if (!settings.lastLaunchedVersion.equals(version)){
            settings.lastLaunchedVersion = version;
            settings.dontShowPatreonAgain = false;
            settings.save();
        }
        if (!settings.dontShowPatreonAgain) {
            int val = JOptionPane.showOptionDialog(
                    FrameManager.getOrCreate().frame,
                    "I have a Patreon! If you want to support me and Cogfly, please do so at https://www.patreon/com/c/AmberShadowo",
                    "Support me?",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    Assets.icon.getAsIcon(),
                    new Object[]{"Close & Don't Show Again", "Open My Patreon", LocaleManager.buttonClose.get()},
                    "Open My Patreon");
            if (val == JOptionPane.YES_OPTION) {
                settings.dontShowPatreonAgain = true;
                settings.save();
            }
            else if (val == JOptionPane.NO_OPTION)
                FileUtils.openURI(URI.create("https://www.patreon.com/c/AmberShadowo?utm_medium=unknown&utm_source=join_link&utm_campaign=creatorshare_creator&utm_content=copyLink"));
        }

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("https://ambershadow.dev/cogfly/dynamic_message.json"))
                    .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject message = JsonParser.parseString(response.body()).getAsJsonObject();
                String content = message.get("content").getAsString();
                if (!content.isBlank()) {
                    //noinspection MagicConstant
                    JOptionPane.showMessageDialog(
                            FrameManager.getOrCreate().frame,
                            content,
                            message.get("title").getAsString(),
                            message.get("type").getAsInt()
                    );
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (showUnknownHost) {
            JOptionPane.showMessageDialog(
                    FrameManager.getOrCreate().frame,
                    "An UnknownHostException was thrown during mod discovery.\nMods may not install properly.",
                    "No Internet?",
                    JOptionPane.WARNING_MESSAGE
            );
        }

        for (String key : failedDownloads.keySet()) {
            List<String> failed = failedDownloads.get(key);
            if (failed.isEmpty()) continue;
            String message = failed.size() + " mod" + (failed.size() == 1 ? " " : "s ") + "failed to download for profile " + key + " on last launch.";
            JOptionPane.showMessageDialog(
                    FrameManager.getOrCreate().frame,
                    message + "\n\n" + String.join("\n", failed),
                    "Failed to download mods",
                    JOptionPane.WARNING_MESSAGE
            );
        }
        failedDownloads = null;
    }

    private static void addPathPanel(String text, String text2, boolean ia, Supplier<String> def, Consumer<JButton> set, BiConsumer<JButton, JButton> a, JDialog prompt) {
        JPanel texts = new JPanel(new BorderLayout());
        JLabel first = new JLabel(text);
        first.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel second = new JLabel(text2);
        second.setHorizontalAlignment(SwingConstants.CENTER);
        texts.add(first, BorderLayout.NORTH);
        texts.add(second, BorderLayout.CENTER);
        if (def.get() != null) {
            JLabel third = new JLabel("(" + def.get() + ")");
            third.setHorizontalAlignment(SwingConstants.CENTER);
            texts.add(third, BorderLayout.SOUTH);
        }
        texts.setAlignmentX(Component.CENTER_ALIGNMENT);
        prompt.add(texts, BorderLayout.NORTH);

        JButton path = new JButton("Click here to select a file.");
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        centerPanel.add(path);
        prompt.add(centerPanel, BorderLayout.CENTER);

        JButton confirm = new JButton("Confirm");
        path.addActionListener(_ -> a.accept(path, confirm));
        confirm.addActionListener(_ -> {
            set.accept(path);
            prompt.dispose();
            settings.save();
        });
        confirm.setEnabled(ia);
        JPanel confirmPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        confirmPanel.add(confirm);
        prompt.add(confirmPanel, BorderLayout.SOUTH);
    }

    public static void copyFile(Path path) {
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



    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(runnable);
        future.exceptionally(f -> {
            throw new RuntimeException(f);
        });
        return future;
    }

    public static void throwNonFatalError(Throwable e) {
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
                    + String.format(LocaleManager.textMoreLines.get(), lines.length - maxLines);
        }
        int val = JOptionPane.showOptionDialog(
                FrameManager.getOrCreate().frame,
                stackTrace,
                LocaleManager.titleError.get(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                new Object[]{LocaleManager.buttonCopy, LocaleManager.buttonClose.get()},
                0);
        if (val == JOptionPane.YES_OPTION) {
            copyString(sw.toString());
        }
    }

    public static boolean isWindows() {
        return OperatingSystem.current() == OperatingSystem.WINDOWS;
    }
    public static boolean isLinux() {
        return OperatingSystem.current() == OperatingSystem.LINUX;
    }
    public static boolean isMac() {
        return OperatingSystem.current() == OperatingSystem.MAC;
    }
    public static OperatingSystem getOs() {
        return OperatingSystem.current();
    }

    public enum SortingType {
        NAME,
        DOWNLOADS,
        DATE_CREATED,
        DATE_UPDATED,
    }

    public enum OperatingSystem {
        WINDOWS, MAC, LINUX, OTHER;

        private static OperatingSystem current;
        private static OperatingSystem current() {
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