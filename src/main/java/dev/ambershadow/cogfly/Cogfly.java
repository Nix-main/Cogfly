package dev.ambershadow.cogfly;

import com.google.gson.JsonObject;
import com.sun.jna.Native;
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
import java.net.URL;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Cogfly {

    public static URL getResource(String path){
        return Cogfly.class.getResource(path);
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
    public static void main(String[] args) {
        AppDirs dirs = AppDirsFactory.getInstance();
        localDataPath = dirs.getUserDataDir("Cogfly", null, "");
        roamingDataPath = dirs.getUserDataDir("Cogfly", null, "", true);
        String logDir = Paths.get(localDataPath).resolve("logs").toString();
        System.setProperty("app.log.dir", logDir);

        logger = LoggerFactory.getLogger(Cogfly.class);
        logger.info("Initializing...");

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> logger.error("Uncaught exception in thread {}", thread.getName(), throwable));

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
            } catch (IOException | UnsatisfiedLinkError e) {
                throw new RuntimeException(e);
            }
        }
        dataJson = new File(localDataPath + "/settings.json");
        //noinspection ResultOfMethodCallIgnored
        dataJson.getParentFile().mkdirs();
        if (!dataJson.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                dataJson.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        settings = new Settings(dataJson);
        settings.load();
        packUrl = Cogfly.getResource("/packs/BepInExPack.zip");
        packUrlNoConsole = Cogfly.getResource("/packs/BepInExPack_NoConsole.zip");
        logger.info("Loaded settings");
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
        if (bepindll.toFile().exists())
            return;
        if (!path.toFile().exists())
            return;
        Utils.downloadAndExtract(packUrlNoConsole, path);
    }
    public static void downloadBepInEx(Path path){
        Path bepindll = path.resolve("BepInEx/core/BepInEx.dll");
        if (bepindll.toFile().exists())
            return;
        Utils.downloadAndExtract(packUrl, path);
    }

    public static void sortList(SortingType type, String direction){
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
            case INSTALLED:
                break;
        }
        if (direction.equalsIgnoreCase("descending")){
            mods = mods.reversed();
        }
    }

    public static List<ModData> getDisplayedMods(SortingType type, Profile profile){
        if (type == SortingType.INSTALLED)
            return profile.getInstalledMods();
        return mods;
    }

    private static void showEarlyDialogs(){
        if (settings.getData() != null && settings.getData().has("profileSavePath")) {
            Cogfly.settings.profileSavePath = settings.getData().get("profileSavePath").getAsString();
        } else {
            logger.info("No stored profile save path! Prompting:");
            JDialog prompt = new JDialog(FrameManager.getOrCreate().frame, "Profile Save Path", true);
            prompt.setLayout(new BorderLayout());
            prompt.setLocationRelativeTo(null);
            prompt.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            prompt.setResizable(false);
            prompt.setPreferredSize(new Dimension(450, 160));

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
            }, "Hollow Knight Silksong", "exe", "app", "x86_64"));
            JPanel confirmPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            confirmPanel.add(confirm);
            prompt.add(confirmPanel, BorderLayout.SOUTH);

            prompt.pack();
            prompt.setVisible(true);
        }
    }


    public static void launchGameAsync(boolean enabled, String path){
        CompletableFuture.runAsync(() -> {
            logger.info("Launching game. OS: {}, Path: {}", Utils.OperatingSystem.current(), path);
            ProcessBuilder builder = new ProcessBuilder();
            List<String> cmds = new ArrayList<>();
            Path gameAppPath = Paths.get(settings.gamePath).resolve(Utils.getGameExecutable());
            if (Utils.OperatingSystem.current().equals(Utils.OperatingSystem.MAC)) {
                builder.directory(Paths.get(settings.gamePath).toFile());
                cmds.add("arch");
                cmds.add("-x86_64");
                cmds.add("sh");
                cmds.add(Utils.getGameExecutable());
            } else if (Utils.OperatingSystem.current().equals(Utils.OperatingSystem.LINUX)) {
                builder.directory(Paths.get(settings.gamePath).toFile());
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
            try {
                builder.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public enum SortingType {
        NAME,
        DOWNLOADS,
        DATE_CREATED,
        DATE_UPDATED,
        INSTALLED
    }
}