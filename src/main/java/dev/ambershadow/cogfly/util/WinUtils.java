package dev.ambershadow.cogfly.util;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.util.jna.WinFolderPicker;
import dev.ambershadow.cogfly.util.jna.WinTinyFileDialogs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class WinUtils {

    public static WinFolderPicker FOLDER_PICKER;
    public static WinTinyFileDialogs FILE_DIALOGS;

    public static void init() throws IOException {
        Path folder = Cogfly.localDataPath.resolve("winfolderpicker.dll");
        Path file = Cogfly.localDataPath.resolve("wintinyfiledialogs.dll");
        if (!Files.exists(folder)){
            Files.copy(
                    Cogfly.getResource("/winfolderpicker.dll").openStream(),
                    folder
            );
        }
        if (!Files.exists(file)){
            Files.copy(
                    Cogfly.getResource("/wintinyfiledialogs.dll").openStream(),
                    file
            );
        }
        try {
            FOLDER_PICKER =
                    Native.load(folder.toAbsolutePath().toString(),
                            WinFolderPicker.class
                    );
            FILE_DIALOGS =
                    Native.load(file.toAbsolutePath().toString(),
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
            Files.createDirectories(Cogfly.localDataPath.resolve("updater"));
            try(InputStream stream = Cogfly.getResource("/updater.ps1").openStream()) {
                Files.write(Cogfly.localDataPath.resolve("updater","updater.ps1"), stream.readAllBytes());
            }
        } catch (UnsatisfiedLinkError | URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
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
}
