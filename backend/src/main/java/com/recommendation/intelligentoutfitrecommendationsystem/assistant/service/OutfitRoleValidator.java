package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import java.util.Locale;
import java.util.Set;

/**
 * 在 Java 商品分类事实边界内校验 Python 提议的穿搭角色。
 *
 * Python 角色仅在与 Java 分类映射一致时被采信；空值、非法值或冲突值均回退到 Java 的安全映射。
 */
public class OutfitRoleValidator {

    private static final Set<String> ROLES = Set.of(
            "TOP", "BOTTOM", "OUTER", "SHOES", "ACCESSORY", "OTHER"
    );

    private final OutfitRoleResolver roleResolver;

    public OutfitRoleValidator() {
        this(new OutfitRoleResolver());
    }

    public OutfitRoleValidator(OutfitRoleResolver roleResolver) {
        this.roleResolver = roleResolver;
    }

    /**
     * 返回与 Java 分类兼容的规范角色，任何不可信提议都不会越过 Java 商品事实边界。
     *
     * @param categoryName Java 商品分类名称，可为空
     * @param proposedRole Python 提议角色，可为空或非法
     * @return 六种规范角色之一；未知分类固定返回 {@code OTHER}
     */
    public String validate(String categoryName, String proposedRole) {
        String safeRole = roleResolver.resolve(categoryName);
        String normalizedProposal = normalize(proposedRole);
        if (ROLES.contains(normalizedProposal) && safeRole.equals(normalizedProposal)) {
            return normalizedProposal;
        }
        return safeRole;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
