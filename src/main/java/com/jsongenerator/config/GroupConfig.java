package com.jsongenerator.config;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class GroupConfig {
    @SerializedName("entryTypes")
    private List<EntryType> entryTypes;
    
    @SerializedName("categories")
    private List<String> categories;
    
    @SerializedName("blacklist")
    private List<String> blacklist;
    
    @SerializedName("CustomGroups")
    private Map<String, List<String>> customGroups;
    
    public List<EntryType> getEntryTypes() {
        return entryTypes != null ? entryTypes : List.of(EntryType.ITEMS, EntryType.EFFECTS);
    }
    
    public List<String> getCategories() {
        return categories != null ? categories : List.of("Misc");
    }
    
    public List<String> getBlacklist() {
        return blacklist != null ? blacklist : List.of();
    }
    
    public Map<String, List<String>> getCustomGroups() {
        return customGroups != null ? customGroups : Map.of();
    }
    
    public enum EntryType {
        ITEMS,
        EFFECTS
    }
}
