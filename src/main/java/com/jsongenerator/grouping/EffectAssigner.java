package com.jsongenerator.grouping;

import com.jsongenerator.data.EntryValue;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class EffectAssigner {
    public Map<String, Map<String, Float>> assignEffectsToItems(List<EntryValue> items, List<EntryValue> effects) {
        Map<String, Map<String, Float>> itemsWithEffects = new java.util.HashMap<>(); // <item, <effect, value>>
        for (EntryValue item : items) {
            Map<String,Float> effectValues = new java.util.HashMap<>();

            float multiplier = .25f;
            float variation = .35f;
            boolean isRare = item.tags().stream().anyMatch(List.of("Uncommon", "Rare", "Epic", "Legendary")::contains);

            for (EntryValue effect : effects) {

                float value = Math.round(new Random().nextFloat()*variation * 100) / 100.0f;

                if (effect.namespace().equals(item.namespace())) {
                    value += multiplier;
                }
                
                if (item.tags().stream().anyMatch(effect.tags()::contains)) {
                    value += multiplier;
                }

                if (isRare) {
                    value += multiplier;
                }

                effectValues.put(effect.namespace() + ":" + effect.name(), value);
            }
            // get 3 effects with the highest value
            Map<String,Float> highestEffects = effectValues.entrySet().stream()
                    .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                    .limit(isRare? 4 : 3)
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new
                    ));
            itemsWithEffects.put(item.namespace() + ":" + item.name(), highestEffects);
        }
        return itemsWithEffects;
    }
}
