package com.jsongenerator.discovery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jsongenerator.data.ItemGroup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public class GroupManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path OUTPUT_DIR = FMLPaths.GAMEDIR.get().resolve("generated");
    
    private static GroupManager INSTANCE;
    private final ModConfig config;
    private final Map<String, Map<String, List<ItemStack>>> categorizedItems = new HashMap<>();
    private final Map<String, Map<String, List<MobEffect>>> categorizedEffects = new HashMap<>();

    private GroupManager() {
        config = ModConfig.getInstance();
        indexItemsByGroup();
    }

    public static synchronized GroupManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GroupManager();
        }
        return INSTANCE;
    }

    private void indexItemsByGroup() {
        categorizedItems.clear();
        categorizedEffects.clear();

        // Process item groups
        for (ItemGroup group : config.getItemGroups()) {
            if (group.groupType() == ItemGroup.GroupType.ITEM) {
                indexItemsInGroup(group);
            } else if (group.groupType() == ItemGroup.GroupType.EFFECT) {
                indexEffectsInGroup(group);
            }
        }
    }

    private void indexItemsInGroup(ItemGroup group) {
        String category = group.category();
        categorizedItems.computeIfAbsent(category, k -> new HashMap<>());
        
        List<ItemStack> items = ForgeRegistries.ITEMS.getValues().stream()
            .filter(item -> {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                return id != null && isItemFromGroup(group, id);
            })
            .map(ItemStack::new)
            .collect(Collectors.toList());
        
        if (!items.isEmpty()) {
            categorizedItems.get(category).put(group.groupName(), items);
            LOGGER.info("Indexed {} items for group: {} in category: {}", 
                items.size(), group.groupName(), category);
        }
    }

    private void indexEffectsInGroup(ItemGroup group) {
        String category = group.category();
        categorizedEffects.computeIfAbsent(category, k -> new HashMap<>());
        
        List<MobEffect> effects = ForgeRegistries.MOB_EFFECTS.getValues().stream()
            .filter(effect -> {
                ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
                return id != null && isEffectFromGroup(group, id);
            })
            .collect(Collectors.toList());
        
        if (!effects.isEmpty()) {
            categorizedEffects.get(category).put(group.groupName(), effects);
            LOGGER.info("Indexed {} effects for group: {} in category: {}", 
                effects.size(), group.groupName(), category);
        }
    }

    public boolean isItemFromGroup(ItemGroup group, ResourceLocation itemId) {
        // Check if item's path contains any of the keywords
        String path = itemId.getPath().toLowerCase();
        return group.keywords().stream().anyMatch(path::contains);
    }
    
    public boolean isEffectFromGroup(ItemGroup group, ResourceLocation effectId) {
        // Check if effect's path contains any of the keywords
        String path = effectId.getPath().toLowerCase();
        return group.keywords().stream().anyMatch(path::contains);
    }

    /**
     * Saves all discovered items and effects to JSON files, organized by category
     * @return true if successful, false otherwise
     */
    public boolean saveAllToJson() {
        try {
            Files.createDirectories(OUTPUT_DIR);
            boolean itemsSaved = saveItemsToJson();
            boolean effectsSaved = saveEffectsToJson();
            return itemsSaved && effectsSaved;
        } catch (IOException e) {
            LOGGER.error("Failed to create output directory", e);
            return false;
        }
    }

    private boolean saveItemsToJson() {
        try {
            Path itemsDir = OUTPUT_DIR.resolve("items");
            Files.createDirectories(itemsDir);
            
            JsonObject root = new JsonObject();
            root.addProperty("timestamp", System.currentTimeMillis());
            JsonObject categories = new JsonObject();
            
            for (Map.Entry<String, Map<String, List<ItemStack>>> categoryEntry : categorizedItems.entrySet()) {
                JsonObject categoryObj = new JsonObject();
                
                for (Map.Entry<String, List<ItemStack>> groupEntry : categoryEntry.getValue().entrySet()) {
                    JsonArray itemsArray = new JsonArray();
                    
                    for (ItemStack stack : groupEntry.getValue()) {
                        if (!stack.isEmpty()) {
                            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                            if (itemId != null) {
                                JsonObject itemObj = new JsonObject();
                                itemObj.addProperty("id", itemId.toString());
                                itemObj.addProperty("displayName", stack.getDisplayName().getString());
                                itemObj.addProperty("count", stack.getCount());
                                itemsArray.add(itemObj);
                            }
                        }
                    }
                    
                    if (itemsArray.size() > 0) {
                        categoryObj.add(groupEntry.getKey(), itemsArray);
                    }
                }
                
                if (categoryObj.size() > 0) {
                    categories.add(categoryEntry.getKey(), categoryObj);
                }
            }
            
            root.add("categories", categories);
            
            Path outputFile = itemsDir.resolve("items_by_category.json");
            try (FileWriter writer = new FileWriter(outputFile.toFile())) {
                GSON.toJson(root, writer);
                LOGGER.info("Successfully saved items to {}", outputFile);
                return true;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save items: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean saveEffectsToJson() {
        try {
            Path effectsDir = OUTPUT_DIR.resolve("effects");
            Files.createDirectories(effectsDir);
            
            JsonObject root = new JsonObject();
            root.addProperty("timestamp", System.currentTimeMillis());
            JsonObject categories = new JsonObject();
            
            for (Map.Entry<String, Map<String, List<MobEffect>>> categoryEntry : categorizedEffects.entrySet()) {
                JsonObject categoryObj = new JsonObject();
                
                for (Map.Entry<String, List<MobEffect>> groupEntry : categoryEntry.getValue().entrySet()) {
                    JsonArray effectsArray = new JsonArray();
                    
                    for (MobEffect effect : groupEntry.getValue()) {
                        ResourceLocation effectId = ForgeRegistries.MOB_EFFECTS.getKey(effect);
                        if (effectId != null) {
                            JsonObject effectObj = new JsonObject();
                            effectObj.addProperty("id", effectId.toString());
                            effectObj.addProperty("name", effect.getDisplayName().getString());
                            effectObj.addProperty("category", effect.getCategory().name());
                            effectObj.addProperty("isBeneficial", effect.isBeneficial());
                            effectsArray.add(effectObj);
                        }
                    }
                    
                    if (effectsArray.size() > 0) {
                        categoryObj.add(groupEntry.getKey(), effectsArray);
                    }
                }
                
                if (categoryObj.size() > 0) {
                    categories.add(categoryEntry.getKey(), categoryObj);
                }
            }
            
            root.add("categories", categories);
            
            Path outputFile = effectsDir.resolve("effects_by_category.json");
            try (FileWriter writer = new FileWriter(outputFile.toFile())) {
                GSON.toJson(root, writer);
                LOGGER.info("Successfully saved effects to {}", outputFile);
                return true;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save effects: {}", e.getMessage(), e);
            return false;
        }
    }

}
