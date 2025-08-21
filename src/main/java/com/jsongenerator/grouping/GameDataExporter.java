package com.jsongenerator.grouping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jsongenerator.config.GroupConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.jsongenerator.JsonGenerator.MODID;

public class GameDataExporter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path OUTPUT_DIR = FMLPaths.GAMEDIR.get().resolve(MODID);

    private GroupConfig loadGroupConfig() throws IOException {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("json-generator/group_config.json");

        // Copy default config if it doesn't exist
        if (!Files.exists(configPath)) {
            Files.createDirectories(configPath.getParent());
            try (var in = getClass().getResourceAsStream("/data/jsongenerator/config/default_group_config.json")) {
                if (in != null) {
                    Files.copy(in, configPath);
                    LOGGER.info("Created default config at: " + configPath);
                } else {
                    throw new IOException("Default config not found in resources");
                }
            }
        }

        // Read the file content for debugging
        String jsonContent = new String(Files.readAllBytes(configPath));
        LOGGER.debug("Loading config from: " + configPath);
        LOGGER.debug("Config content:\n" + jsonContent);

        try (var reader = new StringReader(jsonContent)) {
            GroupConfig config = GSON.fromJson(reader, GroupConfig.class);
            if (config == null) {
                throw new IOException("Failed to parse config: result is null");
            }
            return config;
        } catch (Exception e) {
            LOGGER.error("Error parsing config file at " + configPath, e);
            throw new IOException("Failed to parse config file: " + e.getMessage(), e);
        }
    }

    public void exportAll() {
        try {
            GroupConfig config = loadGroupConfig();

            if (config.getEntryTypes().contains(GroupConfig.EntryType.ITEMS)) {
                exportItemsByNamespace(config);
            }

            if (config.getEntryTypes().contains(GroupConfig.EntryType.EFFECTS)) {
                exportEffectsByNamespace(config);
            }

        } catch (IOException e) {
            LOGGER.error("Error during export", e);
        }
    }

    private void exportItemsByNamespace(GroupConfig config) {
        JsonObject root = new JsonObject();

        List<Item> items = ForgeRegistries.ITEMS.getEntries().stream()
                .filter(entry -> config.getBlacklist().stream().noneMatch(entry.getKey().toString()::contains))
                .filter(entry -> isItemTypeAllowed(entry.getValue()))
                .map(Map.Entry::getValue)
                .toList();

        for (Item item : items) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);

            String namespace = id.getNamespace();
            String path = id.getPath();
            ItemStack stack = new ItemStack(item);

            // Get item tags
            JsonArray tags = new JsonArray();

            // Add rarity
            String rarity = stack.getRarity().name();
            if (!rarity.equals("COMMON")) {
                tags.add(rarity.charAt(0) + rarity.substring(1).toLowerCase());
            }

            // Add custom group tags
            for (Map.Entry<String, List<String>> group : config.getCustomGroups().entrySet()) {
                if (group.getValue().stream().anyMatch(path::contains)) {
                    tags.add(group.getKey());
                }
            }

            // Add to JSON structure
            if (!root.has(namespace)) {
                root.add(namespace, new JsonObject());
            }

            JsonObject namespaceObj = root.getAsJsonObject(namespace);
            if (!namespaceObj.has(path)) {
                namespaceObj.add(path, tags);
            }
        }

        saveToFile("items_by_namespace.json", root);
    }

    private static boolean isItemTypeAllowed(Item item) {
        return !(
                item instanceof BlockItem || item instanceof ArmorItem || item instanceof SwordItem || item instanceof PickaxeItem ||
                item instanceof ProjectileWeaponItem || item instanceof ShovelItem || item instanceof AxeItem ||
                item instanceof HoeItem
        );
    }

    private void exportEffectsByNamespace(GroupConfig config) {
        JsonObject root = new JsonObject();

        for (MobEffect effect : ForgeRegistries.MOB_EFFECTS) {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
            if (id == null || config.getBlacklist().stream().anyMatch(id.toString()::contains)) {
                continue;
            }

            String namespace = id.getNamespace();
            String path = id.getPath();

            // Get effect tags
            JsonArray tags = new JsonArray();

            // Add category as a tag if not NEUTRAL
            String category = effect.getCategory().name();
            if (!category.equals("NEUTRAL")) {
                tags.add(category);
            }

            // Add custom group tags
            for (Map.Entry<String, List<String>> group : config.getCustomGroups().entrySet()) {
                if (group.getValue().stream().anyMatch(path::contains)) {
                    tags.add(group.getKey());
                }
            }

            // Add to JSON structure
            if (!root.has(namespace)) {
                root.add(namespace, new JsonObject());
            }

            JsonObject namespaceObj = root.getAsJsonObject(namespace);

            // Use a single "Items" category for all items
            namespaceObj.add(path, tags);
        }

        saveToFile("effects_by_namespace.json", root);
    }

    private void saveToFile(String filename, JsonObject data) {
        try {
            // Create parent directories if they don't exist
            Files.createDirectories(OUTPUT_DIR);

            Path outputFile = OUTPUT_DIR.resolve(filename);
            try (FileWriter writer = new FileWriter(outputFile.toFile())) {
                GSON.toJson(data, writer);
                LOGGER.info("Successfully saved data to {}", outputFile);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save data to {}: {}", OUTPUT_DIR.resolve(filename), e.getMessage(), e);
        }
    }
}
