package com.jsongenerator.discovery;

import com.google.gson.*;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class GameDataExporter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path OUTPUT_DIR = FMLPaths.GAMEDIR.get().resolve("generated");
    
    public void exportAll() {
        try {
            Files.createDirectories(OUTPUT_DIR);
            exportEffectsByCategory();
            exportItemsByRarity();
        } catch (IOException e) {
            LOGGER.error("Failed to create output directory", e);
        }
    }
    
    private void exportEffectsByCategory() {
        // Group effects by their category
        Map<MobEffectCategory, List<String>> effectsByCategory = new HashMap<>();
        
        for (MobEffect effect : ForgeRegistries.MOB_EFFECTS) {
            MobEffectCategory category = effect.getCategory();
            String effectId = Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getKey(effect)).toString();
            effectsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(effectId);
        }
        
        // Convert to JSON structure
        JsonObject effectsJson = new JsonObject();
        for (Map.Entry<MobEffectCategory, List<String>> entry : effectsByCategory.entrySet()) {
            JsonArray effectArray = new JsonArray();
            entry.getValue().stream().sorted().forEach(effectArray::add);
            effectsJson.add(entry.getKey().name().toLowerCase() + "Effects", effectArray);
        }
        
        saveToFile("effects_by_category.json", effectsJson);
    }
    
    private void exportItemsByRarity() {
        // Define blacklisted keywords
        Set<String> blacklist = Set.of(
            "spawn_egg", "command_block", "debug_", "barrier", "structure_",
            "jigsaw", "knowledge_book", "written_book", "writable_book"
        );
        
        // Group items by their rarity
        Map<String, List<String>> itemsByRarity = new HashMap<>();

        for (Item item : ForgeRegistries.ITEMS) {
            if (item == null) continue;
            String itemId = Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item)).toString().toLowerCase();
            
            // Skip blocks and blacklisted items
            if (item instanceof BlockItem || blacklist.stream().anyMatch(itemId::contains)) {
                continue;
            }
            
            // Get item rarity and add to the appropriate list
            ItemStack stack = new ItemStack(item);
            String rarity = stack.getRarity().name().toLowerCase();
            itemsByRarity.computeIfAbsent(rarity, k -> new ArrayList<>()).add(itemId);
        }
        
        // Create and populate the JSON object
        JsonObject rarityJson = new JsonObject();
        for (Map.Entry<String, List<String>> entry : itemsByRarity.entrySet()) {
            JsonArray itemArray = new JsonArray();
            List<String> sortedItems = new ArrayList<>(entry.getValue());
            Collections.sort(sortedItems);
            
            // Add items to the array
            sortedItems.forEach(itemArray::add);
            
            // Add the array to the JSON object
            String categoryName = entry.getKey().toLowerCase() + "_items";
            rarityJson.add(categoryName, itemArray);

            LOGGER.debug("Added {} items to category '{}'", sortedItems.size(), categoryName);
        }
        
        saveToFile("items_by_rarity.json", rarityJson);
    }
    
    private void saveToFile(String filename, JsonObject data) {
        Path outputFile = OUTPUT_DIR.resolve(filename);
        try (FileWriter writer = new FileWriter(outputFile.toFile())) {
            GSON.toJson(data, writer);
            LOGGER.info("Successfully saved data to {}", outputFile);
        } catch (IOException e) {
            LOGGER.error("Failed to save data to {}: {}", outputFile, e.getMessage(), e);
        }
    }
}
