package net.camacraft.noconflict.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists radial menu selections across game restarts as name-based maps
 * (physical key name -> keybind name) in config/noconflict-selections.json.
 *
 * The name-based maps stay authoritative: entries pointing at keybinds from a
 * temporarily uninstalled mod are kept on disk and come back once the mod returns.
 */
public class SelectionStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("noconflict-selections.json");

    private static final Map<String, String> tapSelections = new LinkedHashMap<>();
    private static final Map<String, String> holdSelections = new LinkedHashMap<>();
    private static boolean loaded = false;

    public static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            readMap(root.getAsJsonObject("tap"), tapSelections);
            readMap(root.getAsJsonObject("hold"), holdSelections);
        } catch (Exception e) {
            LOGGER.error("NoConflict: failed to read {}", FILE, e);
        }
    }

    private static void readMap(JsonObject source, Map<String, String> target) {
        if (source == null) return;
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                target.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
    }

    public static Map<String, String> getTapSelections() {
        return Collections.unmodifiableMap(tapSelections);
    }

    public static Map<String, String> getHoldSelections() {
        return Collections.unmodifiableMap(holdSelections);
    }

    /** mappingName == null removes the entry. */
    public static void setTap(String keyName, String mappingName) {
        if (update(tapSelections, keyName, mappingName)) write();
    }

    public static void setHold(String keyName, String mappingName) {
        if (update(holdSelections, keyName, mappingName)) write();
    }

    public static void clearAll() {
        if (tapSelections.isEmpty() && holdSelections.isEmpty()) return;
        tapSelections.clear();
        holdSelections.clear();
        write();
    }

    private static boolean update(Map<String, String> map, String keyName, String mappingName) {
        if (mappingName == null) {
            return map.remove(keyName) != null;
        }
        return !mappingName.equals(map.put(keyName, mappingName));
    }

    private static void write() {
        try {
            JsonObject root = new JsonObject();
            JsonObject tap = new JsonObject();
            tapSelections.forEach(tap::addProperty);
            JsonObject hold = new JsonObject();
            holdSelections.forEach(hold::addProperty);
            root.add("tap", tap);
            root.add("hold", hold);
            Files.writeString(FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("NoConflict: failed to write {}", FILE, e);
        }
    }
}
