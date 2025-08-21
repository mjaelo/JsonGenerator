package com.jsongenerator.grouping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jsongenerator.config.GroupConfig;
import com.jsongenerator.data.EntryValue;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.jsongenerator.JsonGenerator.MODID;

public class GameDataExporter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path OUTPUT_DIR = FMLPaths.GAMEDIR.get().resolve(MODID);
    private static final Path OUTPUT_JSON_DIR = FMLPaths.GAMEDIR.get().resolve(MODID).resolve("json");

    private final DataGrouper dataGrouper = new DataGrouper();
    private final EffectAssigner effectAssigner = new EffectAssigner();

    public void groupAll() {
        try {
            GroupConfig config = loadGroupConfig();
            List<EntryValue> items = List.of();
            List<EntryValue> effects = List.of();

            if (config.getEntryTypes().contains(GroupConfig.EntryType.ITEMS)) {
                JsonObject jsonItems = dataGrouper.GroupItemsByNamespace(config);
                saveToFile("items_by_namespace.json", jsonItems);
                items = dataGrouper.toEntryValues(jsonItems);
            }

            if (config.getEntryTypes().contains(GroupConfig.EntryType.EFFECTS)) {
                JsonObject jsonEffects = dataGrouper.GroupEffectsByNamespace(config);
                saveToFile("effects_by_namespace.json", jsonEffects);
                effects = dataGrouper.toEntryValues(jsonEffects);
            }

            Map<String, Map<String, Float>> itemsWithEffects = effectAssigner.assignEffectsToItems(items, effects);
            saveToFile("items_with_effects.json", GSON.toJsonTree(itemsWithEffects).getAsJsonObject());

            saveFormattedJsons(itemsWithEffects);

        } catch (IOException e) {
            LOGGER.error("Error during export", e);
        }
    }

    private void saveFormattedJsons(Map<String, Map<String, Float>> itemsWithEffects) {
        try {
            // Create the output directory if it doesn't exist
            Files.createDirectories(OUTPUT_JSON_DIR);
            
            for (Map.Entry<String, Map<String, Float>> itemEntry : itemsWithEffects.entrySet()) {
                try {
                    String itemId = itemEntry.getKey();
                    String[] itemParts = itemId.split(":");
                    if (itemParts.length != 2) continue; // Skip invalid item IDs
                    
                    String itemNamespace = itemParts[0];
                    String itemName = itemParts[1];
                    Map<String, Float> effects = itemEntry.getValue();
                    
                    // Create effects array
                    JsonArray effectsArray = new JsonArray();
                    for (Map.Entry<String, Float> effectEntry : effects.entrySet()) {
                        String effectId = effectEntry.getKey();
                        float strength = effectEntry.getValue();
                        
                        JsonObject effectObj = new JsonObject();
                        effectObj.addProperty("effect", effectId);
                        effectObj.addProperty("strength", strength);
                        effectsArray.add(effectObj);
                    }
                    
                    // Create the main item JSON
                    JsonObject itemJson = new JsonObject();
                    itemJson.addProperty("type", "mysticalchemy:potion_ingredient");
                    itemJson.addProperty("item", itemId);
                    itemJson.add("effects", effectsArray);
                    
                    // Create namespace directory if it doesn't exist
                    Path namespaceDir = OUTPUT_JSON_DIR.resolve(itemNamespace);
                    Files.createDirectories(namespaceDir);
                    
                    // Save to file in the namespace directory
                    Path outputFile = namespaceDir.resolve(itemName + ".json");
                    saveToFile(outputFile.toString(), itemJson);
                    
                } catch (Exception e) {
                    LOGGER.error("Error processing item: " + itemEntry.getKey(), e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error saving formatted JSON files", e);
        }
    }

    private GroupConfig loadGroupConfig() throws IOException {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("json-generator/group_config.json");
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
        try (var reader = Files.newBufferedReader(configPath)) {
            return GSON.fromJson(reader, GroupConfig.class);
        } catch (Exception e) {
            throw new IOException("Failed to parse config file: " + e.getMessage(), e);
        }
    }

    private void saveToFile(String filename, JsonObject data) {
        try {
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
