package dev.ambershadow.cogfly.util;

import com.sun.jna.WString;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.profile.Profile;
import dev.ambershadow.cogfly.util.swing.FrameManager;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtils {

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

    public static List<Path> collectMatchingFolders(Path dir, String... prefixes) {
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

    public static void deleteFolder(Path folder) {
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

    public static void downloadAndExtract(URL url, Path output) {
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
        } catch (IOException ignored) {}
    }

    public static void pickFolder(Consumer<Path> callback) {
        switch (Cogfly.getOs()) {
            case MAC -> {
                ProcessBuilder pb = new ProcessBuilder("osascript", "-e", "POSIX path of (choose folder)");
                readValue(pb).ifPresent((p) -> {
                    if (!p.equals(Paths.get("")))
                        callback.accept(p);
                });
            }
            case WINDOWS -> {
                WString path = WinUtils.FOLDER_PICKER.pickFolder();
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

    public static void pickFile(Consumer<Path> callback, String name, String... extensions) {
        switch (Cogfly.getOs()) {
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
                String val = WinUtils.FILE_DIALOGS.tinyfd_openFileDialog(
                        "Select File",
                        null,
                        extensions.length,
                        extensions,
                        "",
                        0
                );
                if (val != null && !val.isEmpty())
                    callback.accept(Paths.get(val));
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

    private static Optional<Path> readValue(ProcessBuilder pb) {
        try {
            Process p = pb.start();

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(
                                 p.getInputStream(), StandardCharsets.UTF_8))) {

                String value = reader.readLine();
                int exit = p.waitFor();

                if (exit == 1) { // cancelled in zenity/dialog
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

    public static void openPath(Path path) {
        if (!(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)))
            return;
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void openURI(URI uri) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                Desktop.getDesktop().browse(uri);
            else
                new ProcessBuilder("xdg-open", uri.toString()).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void openSavePath() {
        if (Files.isDirectory(GameUtils.getSavePath())) {
            try (Stream<Path> stream = Files.list(GameUtils.getSavePath())) {
                stream.findFirst().ifPresent(FileUtils::openPath);
            } catch (IOException ignored) {}
        }
    }

    public static void openProfilePath(Profile profile) {
        openPath(profile.getPath());
    }

    private static String manualFilePath(boolean invalid, String name, String... extensions) {
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
}
