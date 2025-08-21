package com.jsongenerator.data;

import java.util.List;

public record ItemGroup(
    String groupName,
    GroupType groupType,
    List<String> keywords
) {
    public enum GroupType {
        ITEM,
        EFFECT
    }
}
