package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import java.util.Set;

/** Maps Java catalog categories to stable outfit presentation roles. */
public class OutfitRoleResolver {

    private static final Set<String> TOP = Set.of("T恤", "衬衫", "卫衣", "上衣", "针织衫");
    private static final Set<String> BOTTOM = Set.of("牛仔裤", "休闲裤", "短裤", "半身裙", "裤子", "长裤");
    private static final Set<String> OUTER = Set.of("外套", "西装", "羽绒服", "风衣", "大衣");

    /** Returns a stable role without inventing catalog categories. */
    public String resolve(String category) {
        if (category == null || category.isBlank()) {
            return "OTHER";
        }
        String value = category.trim();
        if (TOP.contains(value)) {
            return "TOP";
        }
        if (BOTTOM.contains(value)) {
            return "BOTTOM";
        }
        if (OUTER.contains(value)) {
            return "OUTER";
        }
        if (value.contains("鞋") || value.contains("靴")) {
            return "SHOES";
        }
        if (value.contains("帽") || value.contains("包") || value.contains("配饰") || value.contains("围巾")) {
            return "ACCESSORY";
        }
        return "OTHER";
    }
}
