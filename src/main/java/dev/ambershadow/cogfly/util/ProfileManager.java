package dev.ambershadow.cogfly.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.loader.ModFetcher;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import javax.swing.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ProfileManager {

    public static Profile baseGame;
    public static List<Profile> profiles = new ArrayList<>();
    public static void createProfile(String name, String iconPath){
        Path profile = Paths.get(Cogfly.settings.profileSavePath).resolve(name);
        try {
            Files.createDirectories(profile);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
        Cogfly.downloadBepInEx(profile);
        Icon icon;
        if (iconPath.isEmpty()) {
            icon = UIManager.getIcon("OptionPane.informationIcon");
        } else {
            icon = new ImageIcon(iconPath);
        }
        Profile prof = new Profile(name, profile, icon);
        try {
            Files.copy(Paths.get(iconPath), prof.getPath().resolve("icon." + Paths.get(iconPath).getFileName().toString()
                            .split("\\.")[1]));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // ignore. No icon was specified.
        }
        profiles.add(prof);
    }
    private static void deleteFolder(Path path){
        try(Stream<Path> stream = Files.walk(path)) {
            stream
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void removeProfile(Profile profile){
        if (profile == null)
            return;
        profiles.remove(profile);
        deleteFolder(profile.getPath());
    }

    public static void changeIcon(Profile profile, String iconPath, boolean setToDefault){
        String[] extensions = {"png", "jpeg", "jpg", "gif"};
        for (String extension : extensions) {
            Path existingIconPath = Paths.get(profile.getPath().toString()+"/icon."+extension);
            if (existingIconPath.toFile().exists()) {
                try{
                    Files.delete(Path.of(profile.getPath().toString()+"/icon."+extension));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
        }
        if (setToDefault) {
            profile.setIcon(UIManager.getIcon("OptionPane.informationIcon"));

            return;
        }

        try {
            Files.copy(Paths.get(iconPath),
                    profile.getPath().resolve("icon." + Paths.get(iconPath).getFileName().toString()
                            .split("\\.")[1]));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        profile.setIcon(new ImageIcon(iconPath));
    }

    public static void loadProfiles() {
        profiles.clear();
        List<String> paths = new ArrayList<>(Cogfly.settings.profileSources);
        paths.add(Cogfly.settings.profileSavePath);
        for (String m : paths) {
            System.out.println("Path: " + m);
            Path path = Paths.get(m);
            if (!path.toFile().exists())
                continue;
            File[] files = path.toFile().listFiles();
            if (files == null)
                continue;
            for (File file : files) {
                if (!file.isDirectory())
                    continue;
                String[] extensions = {"png", "jpeg", "jpg", "gif"};
                ImageIcon icon = null;
                for (String extension : extensions) {
                    Path path2 = Paths.get(file.getPath() + "/icon." + extension);
                    if (path2.toFile().exists()){
                        icon = new ImageIcon(path2.toString());
                        break;
                    }
                }
                Profile profile = new Profile(file.getName(), Paths.get(file.getAbsolutePath()), icon);
                profile.installedMods = ModFetcher.getInstalledMods(profile.getPluginsPath());
                profiles.add(profile);
            }
        }
        baseGame = new Profile("Base Game", Paths.get(Cogfly.settings.gamePath), Assets.silksongIcon.getAsIcon());
        baseGame.installedMods = ModFetcher.getInstalledMods(baseGame.getPluginsPath());
    }

    public static void fromFile(Path path, BiConsumer<Profile, ModData[]> outdated){
        File file = path.toFile();
        try(ZipInputStream zis = new ZipInputStream(new FileInputStream(file))){
            fromZipStream(zis, outdated);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    public static void fromId(String id, BiConsumer<Profile, ModData[]> outdated){
        if (id == null) return;
        try {
            URL url = URL.of(URI.create("https://thunderstore.io/api/experimental/legacyprofile/get/" + id), null);
            InputStream is = url.openStream();
            String content = new String(
                    is.readAllBytes());
            System.out.println(content);
            content = content.replace("#r2modman", "");
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(
                    Base64.getMimeDecoder().decode(content)));
            is.close();
            fromZipStream(zis, outdated);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @SuppressWarnings("unchecked")
    private static void fromZipStream(ZipInputStream zis,BiConsumer<Profile, ModData[]> outdated){
        String r2xContent = "";
        byte[] doorstopConfigData = new byte[0];
        Map<String, byte[]> configData = new HashMap<>();
        try {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = zis.read(chunk)) != -1) {
                    buffer.write(chunk, 0, n);
                }
                byte[] data = buffer.toByteArray();
                System.out.println("Read " + entry.getName() + " (" + data.length + " bytes)");
                if (entry.getName().equals("export.r2x"))
                    r2xContent = new String(data);
                if (entry.getName().equals("doorstop_config.ini")){
                    doorstopConfigData = data;
                }
                if (entry.getName().contains("config/")){
                    configData.put(entry.getName(), data);
                }
                zis.closeEntry();
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }

        Yaml yaml = new Yaml();
        if (r2xContent.isBlank())
            return;
        Map<String, Object> data = yaml.load(r2xContent);
        String profileName = (String) data.get("profileName");
        List<Map<String, Object>> mods =
                (List<Map<String, Object>>) data.get("mods");
        List<ModData> outdatedMods = new ArrayList<>();
        Profile profile = new Profile(profileName, Paths.get(Cogfly.settings.profileSavePath + "/" + profileName));
        FrameManager.getOrCreate().setPage(FrameManager.CogflyPage.PROFILES,
                FrameManager.getOrCreate().profilesPageButton);
        if (Paths.get(Cogfly.settings.profileSavePath).resolve(profileName).toFile().exists()) {
            int result = JOptionPane.showConfirmDialog(FrameManager.getOrCreate().frame,
                    "A profile with this name in this location already exists. Would you like to overwrite it?", "Profile already exists.",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
            deleteFolder(Paths.get(Cogfly.settings.profileSavePath).resolve(profileName));
            loadProfiles();
        }
        profiles.add(profile);
        Cogfly.downloadBepInEx(profile.getPath());
        mods.forEach(mod -> {
            String name = mod.get("name").toString();
            if (Cogfly.excludedMods.contains(name))
                return;
            Map<String, Integer> version = (Map<String, Integer>) mod.get("version");
            int major = version.get("major");
            int minor = version.get("minor");
            int patch = version.get("patch");
            String v = String.format("%d.%d.%d", major, minor, patch);
            ModData d = ModData.getModAtVersion(name, v);
            if (d != null) {
                if (d.isOutdated(profile)) {
                    outdatedMods.add(d);
                }
                Utils.downloadMod(d, profile, false);
            }
        });

        try {
            for (String key : configData.keySet()) {
                Files.copy(new ByteArrayInputStream(configData.get(key)), profile.getBepInExPath().resolve(key),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.copy(new ByteArrayInputStream(doorstopConfigData), profile.getPath().resolve("doorstop_config.ini"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
        outdated.accept(profile, outdatedMods.toArray(ModData[]::new));
    }

    public static void toFile(Profile profile, Path path){
        try {
            Files.write(path.resolve(profile.getName() + ".r2z"), toZip(profile));
        } catch (IOException ignored) {}
    }

    public static String toId(Profile profile){
        byte[] data = toZip(profile);
        String base64 = Base64.getEncoder().encodeToString(data);
        base64 = "#r2modman\n" + base64;
        try(HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://thunderstore.io/api/experimental/legacyprofile/create/"))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(base64))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.body());
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            return json.get("key").getAsString();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] toZip(Profile profile){
        String r2x = "export.r2x";
        String stappid = "steam_appid.txt";
        String stappidContent = "1030300";
        File doorStop = profile.getPath().resolve("doorstop_config.ini").toFile();
        Path config = profile.getBepInExPath().resolve("config/");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("profileName", profile.getName());
        List<Map<String, Object>> mods = new ArrayList<>();

        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("name", "BepInEx-BepInExPack_Silksong");
        Map<String, Integer> version = new LinkedHashMap<>();
        String[] v = Cogfly.latestPackVer.split("\\.");
        version.put("major", Integer.parseInt(v[0]));
        version.put("minor", Integer.parseInt(v[1]));
        version.put("patch", Integer.parseInt(v[2]));
        pack.put("version", version);
        pack.put("enabled", true);
        mods.add(pack);

        for (ModData data : profile.getInstalledMods()){
            Map<String, Object> mod = new LinkedHashMap<>();
            mod.put("name", data.getFullName());
            Map<String, Integer> ver = new LinkedHashMap<>();
            String[] vf = data.getVersionNumber().split("\\.");
            ver.put("major", Integer.parseInt(vf[0]));
            ver.put("minor", Integer.parseInt(vf[1]));
            ver.put("patch", Integer.parseInt(vf[2]));
            mod.put("enabled", true);
            mod.put("version", ver);
            mods.add(mod);
        }
        root.put("mods", mods);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Yaml yaml = new Yaml(options);
        String r2xContent = yaml.dump(root).trim();

        try{
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(bos);

            zipFolder(config.toFile(), config.toFile().getName(), zos);

            try (FileInputStream fis = new FileInputStream(doorStop)) {
                ZipEntry zipEntry = new ZipEntry(doorStop.getName());
                zos.putNextEntry(zipEntry);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) >= 0) {
                    zos.write(buffer, 0, length);
                }
                zos.closeEntry();
            }

            ZipEntry steamAppid = new ZipEntry(stappid);
            zos.putNextEntry(steamAppid);
            byte[] bytes = stappidContent.getBytes(StandardCharsets.UTF_8);
            zos.write(bytes, 0, bytes.length);
            zos.closeEntry();

            ZipEntry r2xC = new ZipEntry(r2x);
            zos.putNextEntry(r2xC);
            byte[] b = r2xContent.getBytes(StandardCharsets.UTF_8);
            zos.write(b, 0, b.length);
            zos.closeEntry();

            zos.finish();
            zos.close();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void zipFolder(File folder, String parentFolder, ZipOutputStream zos) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                String dirEntryName = parentFolder + "/" + file.getName() + "/";
                zos.putNextEntry(new ZipEntry(dirEntryName));
                zos.closeEntry();
                zipFolder(file, parentFolder + "/" + file.getName(), zos);
            } else {
                try (FileInputStream fis = new FileInputStream(file)) {
                    String zipEntryName = parentFolder + "/" + file.getName();
                    zos.putNextEntry(new ZipEntry(zipEntryName));

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) >= 0) {
                        zos.write(buffer, 0, length);
                    }
                    zos.closeEntry();
                }
            }
        }
    }
}