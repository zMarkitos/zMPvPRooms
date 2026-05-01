package dev.zm.pvprooms.managers;

import dev.zm.pvprooms.ZMPvPRooms;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private final ZMPvPRooms plugin;
    private FileConfiguration langConfig;
    private File langFile;

    public ConfigManager(ZMPvPRooms plugin) {
        this.plugin = plugin;
        setupLang();
    }

    public void setupLang() {
        saveDefaultLangFiles();

        String lang = plugin.getConfig().getString("settings.language", "ES").toUpperCase();
        langFile = new File(plugin.getDataFolder() + File.separator + "lang", "lang_" + lang + ".yml");

        if (!langFile.exists()) {
            plugin.getLogger()
                    .warning("Language file lang_" + lang + ".yml does not exist. Loading EN by default.");
            langFile = new File(plugin.getDataFolder() + File.separator + "lang", "lang_ES.yml");
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    private void saveDefaultLangFiles() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        File esFile = new File(langFolder, "lang_ES.yml");
        if (!esFile.exists()) {
            plugin.saveResource("lang/lang_ES.yml", false);
        }

        File enFile = new File(langFolder, "lang_EN.yml");
        if (!enFile.exists()) {
            plugin.saveResource("lang/lang_EN.yml", false);
        }
    }

    public FileConfiguration getLang() {
        return langConfig;
    }

    /**
     * Obtiene un mensaje con el prefijo incluido.
     */
    public String getMessage(String path) {
        String prefix = getRawMessage("prefix");
        String message = getRawMessage(path);

        // Si no se encuentra el mensaje, getRawMessage ya devuelve el error.
        // Pero para getMessage, si es el prefijo el que no se encuentra, evitamos
        // duplicar errores.
        if (path.equals("prefix"))
            return message;

        return dev.zm.pvprooms.utils.CC.translate(prefix + message);
    }

    /**
     * Obtiene un mensaje sin el prefijo.
     */
    public String getRawMessage(String path) {
        // 1. Try in the root
        if (langConfig.isString(path)) {
            return dev.zm.pvprooms.utils.CC.translate(langConfig.getString(path));
        }

        // 2. Try in the messages section
        String msgPath = "messages." + path;
        if (langConfig.isString(msgPath)) {
            return dev.zm.pvprooms.utils.CC.translate(langConfig.getString(msgPath));
        }

        // 3. Fallback (for debugging)
        // plugin.getLogger().warning("Message not found: " + path);
        return "&cMessage not found: " + path;
    }

    /**
     * Obtiene una lista de mensajes traducidos.
     */
    public List<String> getMessageList(String path) {
        List<String> list = langConfig.getStringList(path);
        if (list.isEmpty()) {
            list = langConfig.getStringList("messages." + path);
        }
        return dev.zm.pvprooms.utils.CC.translate(list);
    }

    /**
     * Read list-like messages from either YAML list format or legacy string with
     * line breaks.
     */
    public List<String> getFlexibleMessageList(String path) {
        if (langConfig.isList(path)) {
            return dev.zm.pvprooms.utils.CC.translate(langConfig.getStringList(path));
        }
        String msgPath = "messages." + path;
        if (langConfig.isList(msgPath)) {
            return dev.zm.pvprooms.utils.CC.translate(langConfig.getStringList(msgPath));
        }

        String raw = null;
        if (langConfig.isString(path)) {
            raw = langConfig.getString(path);
        } else if (langConfig.isString(msgPath)) {
            raw = langConfig.getString(msgPath);
        }

        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }

        String[] lines = raw.split("\\\\n|\\n");
        List<String> result = new ArrayList<>(lines.length);
        for (String line : lines) {
            result.add(dev.zm.pvprooms.utils.CC.translate(line));
        }
        return result;
    }

    public void reloadLang() {
        setupLang();
    }
}
