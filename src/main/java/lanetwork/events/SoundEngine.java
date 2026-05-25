package lanetwork.events;

import lanetwork.teleport.Teleport;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class SoundEngine {

    private final Teleport plugin;

    public SoundEngine(Teleport plugin) {
        this.plugin = plugin;
    }

    public void playSoundProfile(Player player, String configPath) {
        if (player == null) return;

        FileConfiguration config = plugin.getConfig();
        String rawSound = config.getString(configPath, "").trim().toUpperCase();

        if (rawSound.isEmpty()) {
            executeFallback(player, configPath, "Empty string profile path");
            return;
        }

        // 1. Strip out resource pack namespaces if they exist (e.g. minecraft:entity.enderman.teleport -> ENTITY.ENDERMAN.TELEPORT)
        if (rawSound.contains(":")) {
            rawSound = rawSound.substring(rawSound.indexOf(":") + 1);
        }

        // 2. Format standard dot resource notation into hardcoded Bukkit underscores
        String parsedSound = rawSound.replace(".", "_").replace("-", "_");

        try {
            // Check direct exact enum translation
            Sound sound = Sound.valueOf(parsedSound);
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 1.0f, 1.0f);
        } catch (IllegalArgumentException ex1) {

            // 3. Fallback Translation Layer: If it fails, try translation mappings to catch common typos
            try {
                String translatedSound = parsedSound;

                // If a user typed 'ENTITY_ENDERMAN_TELEPORT' or left an extra prefix hanging:
                if (translatedSound.startsWith("MINECRAFT_")) {
                    translatedSound = translatedSound.substring(10);
                }

                Sound sound = Sound.valueOf(translatedSound);
                player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 1.0f, 1.0f);
            } catch (IllegalArgumentException ex2) {
                // If everything fails, run the distinct diagnostic fallback chime
                executeFallback(player, configPath, parsedSound);
            }
        }
    }

    private void executeFallback(Player player, String configPath, String attempt) {
        // Change the fallback tracking based on path context so you don't hear identical chimes everywhere!
        if (configPath.contains("teleport")) {
            // Distinct crisp dimensional portal warp
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, SoundCategory.MASTER, 0.4f, 1.2f);
        } else if (configPath.contains("deny")) {
            // Low iron bash block warning chime
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, SoundCategory.MASTER, 0.6f, 1.0f);
        } else if (configPath.contains("receive")) {
            // Bright clear string bell notification
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.8f, 1.0f);
        } else {
            // Outbound request default chime
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }
}