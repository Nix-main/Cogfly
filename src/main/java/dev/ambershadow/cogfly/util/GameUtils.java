package dev.ambershadow.cogfly.util;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.profile.Profile;
import dev.ambershadow.cogfly.util.swing.FrameManager;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GameUtils {

    public static URL packUrl;
    public static String latestPackVer;
    public static Path doorstop;
    public static Path pack;
    private static String oldPackVersion;
    private static List<Path> queuedPaths = new ArrayList<>();

    public static Path getSavePath() {
        String home = System.getProperty("user.home");
        return switch (Cogfly.getOs()) {
            case MAC -> Paths.get(home + "/Library/Application Support/unity.Team-Cherry.Silksong/");
            case LINUX -> Paths.get(home + "/.config/unity3d/Team Cherry/Hollow Knight Silksong/");
            case WINDOWS -> Paths.get(home + "\\AppData\\LocalLow\\Team Cherry\\Hollow Knight Silksong\\");
            case OTHER -> Paths.get("");
        };
    }

    public static String getGameExecutable() {
        return switch (Cogfly.getOs()) {
            case WINDOWS -> "Hollow Knight Silksong.exe";
            case LINUX, MAC -> "run_bepinex.sh";
            default -> "";
        };
    }

    private static void downloadPack(String version) throws IOException {
        oldPackVersion = "-1";
        Path ver = Cogfly.localDataPath.resolve("pack_version.txt");
        if (Files.exists(ver)) {
            oldPackVersion = Files.readString(ver);
        }
        Path newPack = Cogfly.localDataPath.resolve("BepInExPack");
        Path newDoorstop = Cogfly.localDataPath.resolve("doorstop");
        if (version.equals(oldPackVersion)) {
            pack = newPack;
            doorstop = newDoorstop;
            return;
        }
        Path downloadedPack = Cogfly.localDataPath.resolve("bex_pack");
        FileUtils.deleteFolder(downloadedPack);
        FileUtils.downloadAndExtract(packUrl, downloadedPack);
        FileUtils.deleteFolder(newPack);
        Files.move(downloadedPack.resolve("BepInExPack"), newPack);
        FileUtils.deleteFolder(downloadedPack);
        Files.deleteIfExists(newDoorstop);
        Files.createDirectory(newDoorstop);
        Files.deleteIfExists(newPack.resolve("changelog.txt"));
        try (Stream<Path> files = Files.list(newPack)) {
            files.forEach(file -> {
                if (!Files.isDirectory(file)) {
                    try {
                        Files.move(file, newDoorstop.resolve(file.getFileName()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        Files.write(ver, version.getBytes());

        // only mark the pack as available after it is actually ready
        pack = newPack;
        doorstop = newDoorstop;
    }

    private static void downloadDoorstop(Path path) {
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

    public static void afterLoad() {
        try {
            downloadPack(latestPackVer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (Cogfly.settings.baseGameEnabled)
            downloadBepInEx(Path.of(Cogfly.settings.gamePath));
        queuedPaths.forEach(GameUtils::downloadBepInEx);
        queuedPaths = null;
    }

    public static void downloadBepInEx(Path path) {
        Cogfly.logger.info("downloadBepInEx({})", path);
        if (pack == null || !Files.exists(pack.resolve("BepInEx"))) {
            queuedPaths.add(path);
            return;
        }
        Path bepindll = path.resolve("BepInEx/core/BepInEx.dll");
        if (Files.exists(bepindll))
            return;
        Cogfly.logger.info("{}", path);
        try (Stream<Path> files = Files.walk(pack.resolve("BepInEx"))) {
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
                } else {
                    Files.createDirectories(newF.getParent());
                    Files.copy(file, newF);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void showLaunchError(String details) {
        String[] lines = details.split("\n");
        Path logFile = Cogfly.localDataPath.resolve("logs/launch-error.log");
        boolean truncated = lines.length > 20;
        if (truncated) {
            try {
                Files.writeString(logFile, details);
            } catch (IOException e) {
                Cogfly.logger.error("Failed to write launch error log", e);
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
            if (choice == 0) FileUtils.openPath(logFile.getParent());
        });
    }

    public static void launchModdedGame(Profile profile) {
        launchModdedGame(profile, false);
    }

    public static void launchModdedGame(Profile profile, boolean shortcut) {
        Cogfly.logger.info("Attempting to launch game with profile: {}",  profile.getName());
        if (ModUtils.isDownloading(profile)) {
            JOptionPane.showMessageDialog(FrameManager.getOrCreate().frame, "Downloads are currently in-progress for this profile. Please wait for them to complete before launching.", "Downloads in progress!", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!profile.getGamePath().equals(Cogfly.settings.gamePath)){
            int launch = JOptionPane.showOptionDialog(FrameManager.getOrCreate().frame,
                    "This profile has a custom game path. How would you like to launch it?",
                    "Launch",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    Assets.icon.getAsIcon(),
                    new Object[]{"Launch Modded", "Launch Vanilla"},
                    "Launch Modded");
            if (launch == JOptionPane.NO_OPTION)
                launchGameAsync(false, profile.getBepInExPath().toString(), profile.getGamePath(), shortcut);

            if (launch != JOptionPane.YES_OPTION)
                return;
        }
        launchGameAsync(true, profile.getBepInExPath().toString(), profile.getGamePath(), shortcut);
    }

    public static void launchGameAsync(boolean enabled, String path, String gamePath, boolean shortcut) {
        CompletableFuture.runAsync(() -> {
            Cogfly.logger.info("Launching game. OS: {}, BepInExPath: {}, GamePath: {}", Cogfly.getOs(), path, gamePath);
            Path game = Paths.get(gamePath);
            if (enabled && !shortcut)
                downloadDoorstop(game);
            List<String> args = new ArrayList<>();
            args.add("--doorstop-enabled");
            args.add(String.valueOf(enabled));
            Path bix = Paths.get(path);
            if (enabled) {
                args.add("--doorstop-target-assembly");
                String target = bix.resolve("core/BepInEx.Preloader.dll").toString();
                target = Cogfly.isWindows() || Cogfly.settings.launchWithSteam ? "\"" + target + "\"" : target;
                args.add(target);
            }
            String arg = String.join(" ", args);
            Cogfly.logger.info("Launch arguments: {}", arg);
            if (Cogfly.settings.launchWithSteam) {
                arg = URLEncoder.encode(arg, StandardCharsets.UTF_8);
                String cmd = "steam://rungameid/1030300//" + arg + "/";
                if (!gamePath.equals(Cogfly.settings.gamePath)) {
                    try {
                        long val = SteamUtils.getSteamIdSafe(game);
                        if (val == -1) {
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
                                lines.set(lines.indexOf(line), "target_assembly=" + bix.resolve("core/BepInEx.Preloader.dll"));
                        }
                        Files.write(game.resolve("doorstop_config.ini"), lines);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    if (!Cogfly.isWindows()) {
                        try {
                            for (Path config : SteamUtils.getSteamFolders()) {
                                Path vdf = config.resolve("localconfig.vdf");
                                String prefix = Cogfly.isMac() ? "/usr/bin/arch -x86_64 " : "";
                                boolean argsSet;
                                if (Cogfly.isProton(game))
                                    argsSet = SteamUtils.setLaunchArgs(vdf, "WINEDLLOVERRIDES=\\\"winhttp=n,b\\\" %command%", s -> s.contains("WINEDLLOVERRIDES="));
                                else
                                    argsSet = SteamUtils.setLaunchArgs(vdf, prefix + "/bin/sh \\\"" + game.resolve("run_bepinex.sh").toAbsolutePath() + "\\\" %command%",
                                        s -> s.contains("run_bepinex.sh"));
                                if (argsSet)
                                    break;
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                cmd = cmd.replace("+", "%20");
                Cogfly.logger.info("Launching with Steam Client. Command={}", cmd);
                FileUtils.openURI(URI.create(cmd));
            } else {
                List<String> cmds = getStrings();
                ProcessBuilder builder = new ProcessBuilder();
                builder.directory(game.toFile());
                if (Cogfly.isProton(game)){
                    JOptionPane.showMessageDialog(FrameManager.getOrCreate().frame, "Cogfly can't launch proton standalone. Please use \"Launch with steam\".");
                    return;
                }
                cmds.addAll(args);
                builder.command(cmds);
                Cogfly.logger.info("Launching standalone. Command={}, Directory={}", String.join(" ", cmds), game);
                try {
                    Process process = builder.start();
                    CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(getSupplier(process::getInputStream));
                    CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(getSupplier(process::getErrorStream));
                    int exitCode = process.waitFor();
                    String stdout = stdoutFuture.join();
                    String stderr = stderrFuture.join();
                    if (exitCode != 0) {
                        String details = Stream.of(
                                stdout.isBlank() ? null : "stdout: " + stdout.trim(),
                                stderr.isBlank() ? null : "stderr: " + stderr.trim()
                        ).filter(Objects::nonNull).collect(Collectors.joining("\n"));
                        if (details.isBlank()) details = "Process exited with code " + exitCode;
                        Cogfly.logger.warn("Game process exited with code {}\n{}", exitCode, details);
                        showLaunchError(details);
                    }
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).exceptionally(e -> {
            Cogfly.logger.error("Failed to launch game", e);
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(null,
                            "Failed to launch game: " + e.getCause().getMessage(),
                            "Game Error",
                            JOptionPane.ERROR_MESSAGE)
            );
            return null;
        });
    }

    private static List<String> getStrings() {
        List<String> cmds = new ArrayList<>();
        switch (Cogfly.getOs()) {
            case MAC -> {
                cmds.add("/usr/bin/arch");
                cmds.add("-x86_64");
                cmds.add("/bin/sh");
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
        cmds.add(GameUtils.getGameExecutable());
        return cmds;
    }

    private static Supplier<String> getSupplier(Supplier<InputStream> stream) {
        return () -> {
            try {
                return new String(stream.get().readAllBytes());
            } catch (IOException e) {
                return "";
            }
        };
    }
}
