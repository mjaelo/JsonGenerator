package com.jsongenerator.config;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class GroupConfig {
    @SerializedName("entryCategories")
    private List<EntryCategory> entryCategories;
    
    @SerializedName("blacklist")
    private List<String> blacklist;
    
    @SerializedName("CustomGroups")
    private Map<String, List<String>> customGroups;
    
    public List<EntryCategory> getEntryCategories() {
        return entryCategories;
    }
    
    public List<String> getBlacklist() {
        return blacklist;
    }
    
    public Map<String, List<String>> getCustomGroups() {
        return customGroups;
    }
    
    public enum EntryCategory {
        ITEMS,
        BLOCKS,
        EFFECTS
    }
}
