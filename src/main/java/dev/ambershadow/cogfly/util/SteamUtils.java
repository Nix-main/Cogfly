package dev.ambershadow.cogfly.util;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.util.swing.FrameManager;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class SteamUtils {

    public static boolean setLaunchArgs(Path vdf, String args) throws IOException {
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
            if (val.equals("1030300")) {
                silksongIndex = i;
                isSilk = true;
            }
            if (val.startsWith("LaunchOptions") && isSilk) {
                launchOpts = line;
                launchOptsIndex = i;
                Cogfly.logger.info("{}, {}", launchOptsIndex, launchOpts);
                break;
            }
        }
        if (silksongIndex == -1)
            return false;
        if (launchOpts.contains("run_bepinex.sh"))
            return true;
        if (!Cogfly.settings.finishedSteamPopup) {
            int opt = JOptionPane.showOptionDialog(FrameManager.getOrCreate().frame,
                    "Cogfly is trying to add " + "\"" + args + "\" to your steam launch arguments, this is necessary for the Launch with Steam setting to work on Mac and Linux. This will not overwrite your existing launch arguments, they will still work. You will not be shown this popup again, but can always modify this value in your settings.",
                    "Steam Launch Args",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    Assets.icon.getAsIcon(),
                    new Object[]{"Allow", "Don't Allow"},
                    "Allow");
            if (opt == JOptionPane.YES_OPTION)
                Cogfly.settings.acceptedSteamArgs = true;
            Cogfly.settings.finishedSteamPopup = true;
            Cogfly.settings.save();
        }
        if (!Cogfly.settings.acceptedSteamArgs)
            return true;
        String val;
        int index;
        if (launchOptsIndex == -1 || launchOpts.isEmpty()) {
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
        FileUtils.openURI(URI.create("steam://exit"));
        ProcessHandle.allProcesses()
                .filter((p) ->
                        p.info().command().map(cmd -> cmd.toLowerCase().endsWith("steam.exe")
                                || cmd.toLowerCase().endsWith("steam")
                                || cmd.toLowerCase().endsWith("steam_osx")).orElse(false))
                .findFirst()
                .ifPresentOrElse(steam -> steam.onExit().join(), () -> {});
        lines.add(index, String.join("\"", val));
        Files.write(vdf, lines);
        Cogfly.logger.info(val);
        return true;
    }

    public static List<Path> getSteamFolders() throws IOException {
        Path steamRoot = switch (Cogfly.getOs()) {
            case WINDOWS -> Paths.get(Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "Software\\Valve\\Steam", "SteamPath"));
            case LINUX -> Paths.get(System.getProperty("user.home"), ".local/share/Steam");
            case MAC -> Paths.get(System.getProperty("user.home"), "Library/Application Support/Steam");
            default -> null;
        };
        if (steamRoot == null) return List.of();
        List<Path> paths = new ArrayList<>();
        for (int id : getSteamUserIds(steamRoot.resolve("config", "loginusers.vdf"))) {
            paths.add(steamRoot.resolve("userdata", id + "/config"));
        }
        return paths;
    }

    public static long getSteamIdSafe(Path executable) throws IOException {
        for (Path folder : getSteamFolders()) {
            Path vdf = folder.resolve("shortcuts.vdf");
            if (!Files.exists(vdf)) continue;
            long appid = getSteamId(executable, vdf);
            Cogfly.logger.info("Found Steam app id {} for executable {} under user {}", appid, executable, vdf);
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
}
