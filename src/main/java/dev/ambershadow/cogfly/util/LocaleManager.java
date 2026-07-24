package dev.ambershadow.cogfly.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ambershadow.cogfly.Cogfly;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public class LocaleManager {
    private static Locale locale;
    private static JsonObject data;

    public static void setLocale(Locale locale) {
        LocaleManager.locale = locale;
        try(Reader reader = new InputStreamReader(Objects.requireNonNull(Cogfly.getResource("/locale/" + locale.toLanguageTag() + ".json")).openStream(), StandardCharsets.UTF_8)) {
            data = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IllegalStateException ignored){
            setLocale(Locale.US);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Locale getLocale() {
        return locale;
    }

    public static final Supplier<String> title = () -> data.get("title").getAsString();
    public static final Supplier<String> titleError = () -> data.get("title.error").getAsString();
    public static final Supplier<String> titleUpdate = () -> data.get("title.update").getAsString();

    public static final Supplier<String> textMoreLines = () -> data.get("text.more-lines").getAsString();

    public static final Supplier<String> messageUpdateAvailable = () -> data.get("message.update-available").getAsString();

    public static final Supplier<String> errorProfileNotExist = () -> data.get("error.profile-not-exist").getAsString();

    public static final Supplier<String> buttonClose = () -> data.get("button.close").getAsString();
    public static final Supplier<String> buttonCopy = () -> data.get("button.copy").getAsString();
    public static final Supplier<String> buttonSelectFile = () -> data.get("button.select-file").getAsString();

}
