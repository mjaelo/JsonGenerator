package com.jsongenerator.grouping;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jsongenerator.config.GroupConfig;
import com.jsongenerator.data.EntryValue;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

public class DataGrouper {
    public JsonObject GroupItemsByNamespace(GroupConfig config) {
        JsonObject root = new JsonObject();
        
        for (Map.Entry<ResourceKey<Item>, Item> entry : ForgeRegistries.ITEMS.getEntries()) {
            if (!isItemAllowed(entry, config)) {
                continue;
            }
            
            Item item = entry.getValue();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            String namespace = itemId.getNamespace();
            String itemName = itemId.getPath();
            
            // Get item stack for checking rarity
            ItemStack stack = new ItemStack(item);
            
            // Get item tags
            JsonArray tags = getItemTags(stack, config);
            
            // Add to JSON structure
            if (!root.has(namespace)) {
                root.add(namespace, new JsonObject());
            }
            root.getAsJsonObject(namespace).add(itemName, tags);
        }
        return root;
    }

    public JsonObject GroupEffectsByNamespace(GroupConfig config) {
        JsonObject root = new JsonObject();
        
        for (MobEffect effect : ForgeRegistries.MOB_EFFECTS) {
            ResourceLocation effectId = ForgeRegistries.MOB_EFFECTS.getKey(effect);
            if (effectId == null) continue;
            
            String namespace = effectId.getNamespace();
            String effectName = effectId.getPath();
            
            // Skip blacklisted effects
            if (config.getBlacklist().stream().anyMatch(effectId.toString()::contains)) {
                continue;
            }
            
            // Get effect tags
            JsonArray tags = getEffectTags(effect, effectId, config);
            
            // Add to JSON structure
            if (!root.has(namespace)) {
                root.add(namespace, new JsonObject());
            }
            root.getAsJsonObject(namespace).add(effectName, tags);
        }
        return root;
    }

    public List<EntryValue> toEntryValues(JsonObject items) {
        return items.entrySet().stream()
                .flatMap(namespaceEntry -> {
                    String namespace = namespaceEntry.getKey();
                    return namespaceEntry.getValue().getAsJsonObject().entrySet().stream()
                            .map(itemEntry -> {
                                String itemName = itemEntry.getKey();
                                List<String> tags = itemEntry.getValue().getAsJsonArray().asList().stream()
                                        .map(JsonElement::getAsString)
                                        .toList();
                                return new EntryValue(itemName, tags, namespace);
                            });
                })
                .toList();
    }

    private static boolean isItemAllowed(Map.Entry<ResourceKey<Item>, Item> itemData, GroupConfig config) {
        Item item = itemData.getValue();
        return !(item instanceof BlockItem ||
                item instanceof ArmorItem ||
                item instanceof SwordItem ||
                item instanceof PickaxeItem ||
                item instanceof ProjectileWeaponItem ||
                item instanceof ShovelItem ||
                item instanceof AxeItem ||
                item instanceof HoeItem ||
                config.getBlacklist().stream().anyMatch(itemData.getKey().location().toString()::contains)
        );
    }


    private JsonArray getItemTags(ItemStack stack, GroupConfig config) {
        JsonArray tags = new JsonArray();
        
        // Add rarity if not common
        String rarity = stack.getRarity().name();
        if (!rarity.equals("COMMON")) {
            tags.add(rarity.substring(0, 1) + rarity.substring(1).toLowerCase());
        }
        
        // Add custom group tags based on item ID
        String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().toLowerCase();
        for (Map.Entry<String, List<String>> group : config.getCustomGroups().entrySet()) {
            if (group.getValue().stream().anyMatch(tag -> itemId.contains(tag.toLowerCase()))) {
                tags.add(group.getKey());
            }
        }
        
        return tags;
    }

    private JsonArray getEffectTags(MobEffect effect, ResourceLocation effectId, GroupConfig config) {
        JsonArray tags = new JsonArray();
        String path = effectId.getPath().toLowerCase();
        
        // Add effect category if not NEUTRAL
        String category = effect.getCategory().name();
        if (!category.equals("NEUTRAL")) {
            tags.add(category);
        }
        
        // Add custom group tags
        for (Map.Entry<String, List<String>> group : config.getCustomGroups().entrySet()) {
            if (group.getValue().stream().anyMatch(tag -> path.contains(tag.toLowerCase()))) {
                tags.add(group.getKey());
            }
        }
        
        return tags;
    }
}
