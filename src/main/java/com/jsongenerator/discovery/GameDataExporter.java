package com.jsongenerator.discovery;

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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GameDataExporter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path OUTPUT_DIR = FMLPaths.GAMEDIR.get().resolve("generated");
    
    private List<String> getItemEntries() {
        return ForgeRegistries.ITEMS.getValues().stream()
            .filter(item -> !(item instanceof BlockItem))
            .map(item -> ForgeRegistries.ITEMS.getKey(item).toString())
            .collect(Collectors.toList());
    }
    
    private List<String> getEffectEntries() {
        return ForgeRegistries.MOB_EFFECTS.getValues().stream()
            .map(effect -> ForgeRegistries.MOB_EFFECTS.getKey(effect).toString())
            .collect(Collectors.toList());
    }
    
    private List<String> getBlockEntries() {
        return ForgeRegistries.BLOCKS.getValues().stream()
            .map(block -> ForgeRegistries.BLOCKS.getKey(block).toString())
            .collect(Collectors.toList());
    }
    
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
    
    private Map<String, List<String>> groupByCustomGroups(List<String> entries, 
                                                         Map<String, List<String>> customGroups) {
        Map<String, List<String>> result = new HashMap<>();
        Set<String> matchedEntries = new HashSet<>();
        
        for (Map.Entry<String, List<String>> group : customGroups.entrySet()) {
            List<String> groupEntries = entries.stream()
                .filter(entry -> group.getValue().stream().anyMatch(entry::contains))
                .collect(Collectors.toList());
                
            if (!groupEntries.isEmpty()) {
                result.put(group.getKey(), groupEntries);
                matchedEntries.addAll(groupEntries);
            }
        }
        
        // Add uncategorized entries
        List<String> uncategorized = entries.stream()
            .filter(entry -> !matchedEntries.contains(entry))
            .collect(Collectors.toList());
            
        if (!uncategorized.isEmpty()) {
            result.put("Uncategorized", uncategorized);
        }
        
        return result;
    }
    
    public void exportAll() {
        try {
            GroupConfig config = loadGroupConfig();
            
            if (config.getEntryCategories().contains(GroupConfig.EntryCategory.ITEMS)) {
                exportEntries(config, "items", getItemEntries(), this::groupItemsByRarity);
            }
            
            if (config.getEntryCategories().contains(GroupConfig.EntryCategory.EFFECTS)) {
                exportEntries(config, "effects", getEffectEntries(), this::groupEffectsByCategory);
            }
            
            if (config.getEntryCategories().contains(GroupConfig.EntryCategory.BLOCKS)) {
                exportEntries(config, "blocks", getBlockEntries(), null);
            }
            
        } catch (IOException e) {
            LOGGER.error("Error during export", e);
        }
    }
    
    private Map<String, List<String>> groupItemsByRarity(List<String> itemIds) {
        Map<String, List<String>> result = new HashMap<>();
        
        for (String itemId : itemIds) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                String rarity = stack.getRarity().name().toLowerCase();
                String groupName = "Rarity: " + rarity.substring(0, 1).toUpperCase() + rarity.substring(1);
                
                // For common items, further categorize by item category
                if (rarity.equals("common")) {
                    String itemCategory = getItemCategory(stack);
                    if (!itemCategory.isEmpty()) {
                        groupName = "Common - " + itemCategory;
                    } else {
                        groupName = "Common - Misc";
                    }
                }
                result.computeIfAbsent(groupName, k -> new ArrayList<>()).add(itemId);
            }
        }
        return result;
    }
    
    private String getItemCategory(ItemStack stack) {
        Item item = stack.getItem();
        
        // Check item type and return appropriate category
        if (item instanceof SwordItem) return "Weapons";
        if (item instanceof PickaxeItem || item instanceof ShovelItem || 
            item instanceof AxeItem || item instanceof HoeItem) return "Tools";
        if (item instanceof ArmorItem) return "Armor";
        if (item instanceof BlockItem) return "Blocks";
        if (item.isEdible()) return "Food";
        if (item instanceof ProjectileWeaponItem) return "Ranged";
        if (item instanceof DyeItem) return "Dyes";
        
        // Check for specific item properties
        if (item.getDescription().getString().toLowerCase().contains("potion")) return "Potions";
        if (item.getDescription().getString().toLowerCase().contains("book")) return "Books";
        if (item.getDescription().getString().toLowerCase().contains("record")) return "Music Discs";
        
        return "";
    }
    
    private Map<String, List<String>> groupEffectsByCategory(List<String> effectIds) {
        Map<String, List<String>> byCategory = new HashMap<>();
        for (String effectId : effectIds) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(effectId));
            if (effect != null) {
                String category = effect.getCategory().name();
                String displayName = "Category: " + category.charAt(0) + 
                                  category.substring(1).toLowerCase();
                byCategory.computeIfAbsent(displayName, k -> new ArrayList<>()).add(effectId);
            }
        }
        return byCategory;
    }
    
    private void exportEntries(GroupConfig config, String type, List<String> entries,
                             Function<List<String>, Map<String, List<String>>> uncategorizedProcessor) {
        // Filter out blacklisted entries
        List<String> filtered = entries.stream()
            .filter(entry -> config.getBlacklist().stream().noneMatch(entry::contains))
            .collect(Collectors.toList());
        
        // Group by custom groups first
        Map<String, List<String>> grouped = groupByCustomGroups(filtered, config.getCustomGroups());
        
        // Process uncategorized entries if processor is provided
        if (uncategorizedProcessor != null && grouped.containsKey("Uncategorized")) {
            List<String> uncategorized = grouped.remove("Uncategorized");
            Map<String, List<String>> processed = uncategorizedProcessor.apply(uncategorized);
            grouped.putAll(processed);
        }
        
        // Save to JSON
        saveGroupedData(type, grouped);
    }
    
    private void saveGroupedData(String type, Map<String, List<String>> groups) {
        JsonObject result = new JsonObject();
        int totalItems = 0;
        
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            JsonArray itemsArray = new JsonArray();
            entry.getValue().stream()
                .sorted()
                .forEach(itemsArray::add);
                
            result.add(entry.getKey(), itemsArray);
            totalItems += entry.getValue().size();
            LOGGER.debug("Added {} items to group '{}' for type '{}'", 
                entry.getValue().size(), entry.getKey(), type);
        }
        
        saveToFile(type + "_by_group.json", result);
        LOGGER.info("Exported {} {} to {}_by_group.json", 
            totalItems, type, type);
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
