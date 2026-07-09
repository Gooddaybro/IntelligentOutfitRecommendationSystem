package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Java 侧权威的穿搭需求意图。
 *
 * 该解析器只做确定性小词表匹配；硬过滤由 Java 执行，软偏好交给 Python 在候选池内排序解释。
 */
public class DemandIntentResolver {

    private static final String MALE = "male";
    private static final String FEMALE = "female";
    private static final String[] MALE_SIGNALS = {"男", "男生", "男性", "男士", "男款", "男朋友", "爸爸", "男友"};
    private static final String[] FEMALE_SIGNALS = {"女", "女生", "女性", "女士", "女款", "女朋友", "妈妈", "女友"};
    private static final String[] COMMUTE_SIGNALS = {"通勤", "上班", "办公室", "职场", "上班穿", "上班通勤"};
    private static final String[] STUDENT_SIGNALS = {"学生党", "学生", "大学生", "校园", "上课", "上学"};
    private static final String[] VERSATILE_SIGNALS = {"百搭", "好搭", "基础款", "日常也能穿", "不挑场合", "一衣多穿"};
    private static final String[] WARM_SIGNALS = {
            "秋冬", "冬季", "冬天", "保暖", "厚款", "厚实", "怕冷", "暖和", "不容易冷", "加绒", "抗冻"
    };
    private static final String[] BUDGET_VALUE_SIGNALS = {
            "平价", "便宜", "不贵", "别太贵", "预算有限", "学生预算", "性价比"
    };
    private static final String[] SLIMMER_SIGNALS = {"显瘦", "遮肉", "不显胖", "梨形", "腿粗", "胯宽"};
    private static final String[] TALLER_SIGNALS = {"显高", "小个子", "显腿长", "不压个子"};
    private static final Pattern MESSAGE_BUDGET_MAX_PATTERN = Pattern.compile(
            "(?:预算\\s*)?(\\d{2,5})\\s*(?:以内|以下|内)|不超过\\s*(\\d{2,5})"
    );

    public DemandIntent resolve(
            AssistantChatRequest request,
            UserBodyDataResponse bodyData,
            UserProfileResponse profile
    ) {
        String rawQuery = request == null || request.message() == null ? "" : request.message();
        String targetGender = resolveTargetGender(request, bodyData, profile);
        String category = resolveCategory(request);
        List<String> scene = resolveScene(rawQuery);
        List<String> style = resolveStyle(request, rawQuery, scene);
        Integer budgetMax = resolveBudgetMax(request);
        List<String> attributes = resolveAttributes(rawQuery);
        List<String> hardFilters = hardFilters(targetGender, category, budgetMax);
        List<String> softPreferences = softPreferences(scene, style, attributes);

        return new DemandIntent(
                DemandIntent.VERSION,
                DemandIntent.SOURCE_JAVA_RULE,
                rawQuery,
                targetGender,
                category,
                scene,
                style,
                budgetMax,
                attributes,
                hardFilters,
                softPreferences,
                confidence(hardFilters, softPreferences),
                List.of()
        );
    }

    private String resolveTargetGender(
            AssistantChatRequest request,
            UserBodyDataResponse bodyData,
            UserProfileResponse profile
    ) {
        String messageGender = genderFromMessage(request == null ? null : request.message());
        if (messageGender != null) {
            return messageGender;
        }
        String requestGender = normalizeGender(request == null ? null : request.gender());
        if (requestGender != null) {
            return requestGender;
        }
        String bodyGender = normalizeGender(bodyData == null ? null : bodyData.gender());
        if (bodyGender != null) {
            return bodyGender;
        }
        return normalizeGender(profile == null ? null : profile.gender());
    }

    private String genderFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        boolean hasMale = containsAny(message, MALE_SIGNALS);
        boolean hasFemale = containsAny(message, FEMALE_SIGNALS);
        if (hasMale == hasFemale) {
            return null;
        }
        return hasMale ? MALE : FEMALE;
    }

    private String normalizeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        String normalized = gender.trim().toLowerCase(Locale.ROOT);
        if (MALE.equals(normalized) || FEMALE.equals(normalized)) {
            return normalized;
        }
        if ("男".equals(normalized) || "男生".equals(normalized) || "男性".equals(normalized)) {
            return MALE;
        }
        if ("女".equals(normalized) || "女生".equals(normalized) || "女性".equals(normalized)) {
            return FEMALE;
        }
        return null;
    }

    private String resolveCategory(AssistantChatRequest request) {
        String explicitCategory = normalizeCategory(request == null ? null : request.category());
        if (explicitCategory != null) {
            return explicitCategory;
        }
        String message = request == null ? null : request.message();
        if (!hasText(message)) {
            return null;
        }
        if (containsAny(message, new String[]{"裙子", "半裙", "半身裙", "百褶裙", "a字裙", "直筒裙"})) {
            return "半裙";
        }
        if (message.contains("外套")) {
            return "外套";
        }
        return null;
    }

    private String normalizeCategory(String category) {
        if (!hasText(category)) {
            return null;
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "裙子", "半裙", "半身裙", "百褶裙", "a字裙", "直筒裙" -> "半裙";
            default -> category.trim();
        };
    }

    private List<String> resolveScene(String message) {
        LinkedHashSet<String> scene = new LinkedHashSet<>();
        if (containsAny(message, COMMUTE_SIGNALS)) {
            scene.add("commute");
        }
        if (hasText(message) && message.contains("约会")) {
            scene.add("date");
        }
        if (containsAny(message, STUDENT_SIGNALS)) {
            scene.add("campus");
        }
        if (hasText(message) && message.contains("日常")) {
            scene.add("daily");
        }
        if (hasText(message) && message.contains("旅行")) {
            scene.add("travel");
        }
        if (hasText(message) && message.contains("运动")) {
            scene.add("sport");
        }
        return List.copyOf(scene);
    }

    private List<String> resolveStyle(AssistantChatRequest request, String message, List<String> scene) {
        LinkedHashSet<String> style = new LinkedHashSet<>();
        if (hasText(request == null ? null : request.style())) {
            style.add(request.style().trim());
        }
        if (scene.contains("commute")) {
            style.add("commute");
            style.add("minimal");
        }
        if (containsAny(message, VERSATILE_SIGNALS)) {
            style.add("minimal");
            style.add("casual");
        }
        if (containsAny(message, STUDENT_SIGNALS)) {
            style.add("casual");
        }
        if (hasText(message) && message.contains("休闲")) {
            style.add("casual");
        }
        return List.copyOf(style);
    }

    private Integer resolveBudgetMax(AssistantChatRequest request) {
        if (request == null) {
            return null;
        }
        if (request.budgetMax() != null) {
            return request.budgetMax();
        }
        String message = request.message();
        if (!hasText(message)) {
            return null;
        }
        Matcher matcher = MESSAGE_BUDGET_MAX_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return value == null ? null : Integer.valueOf(value);
    }

    private List<String> resolveAttributes(String message) {
        LinkedHashSet<String> attributes = new LinkedHashSet<>();
        addIfContains(attributes, message, "显高");
        addIfContains(attributes, message, "显瘦");
        addIfContains(attributes, message, "遮肉");
        addIfContains(attributes, message, "高腰");
        addIfContains(attributes, message, "垂顺");
        addIfContains(attributes, message, "挺括");
        if (containsAny(message, WARM_SIGNALS)) {
            attributes.add("保暖");
        }
        if (hasText(message) && (message.contains("厚款") || message.contains("厚实") || message.contains("怕冷"))) {
            attributes.add("厚款");
        }
        if (containsAny(message, BUDGET_VALUE_SIGNALS)) {
            attributes.add("平价");
        }
        if (containsAny(message, SLIMMER_SIGNALS)) {
            attributes.add("显瘦");
        }
        if (containsAny(message, TALLER_SIGNALS)) {
            attributes.add("显高");
        }
        return List.copyOf(attributes);
    }

    private List<String> hardFilters(String targetGender, String category, Integer budgetMax) {
        List<String> filters = new ArrayList<>();
        if (targetGender != null) {
            filters.add("targetGender");
        }
        if (category != null) {
            filters.add("category");
        }
        if (budgetMax != null) {
            filters.add("budgetMax");
        }
        return List.copyOf(filters);
    }

    private List<String> softPreferences(List<String> scene, List<String> style, List<String> attributes) {
        List<String> preferences = new ArrayList<>();
        if (!scene.isEmpty()) {
            preferences.add("scene");
        }
        if (!style.isEmpty()) {
            preferences.add("style");
        }
        if (!attributes.isEmpty()) {
            preferences.add("attributes");
        }
        return List.copyOf(preferences);
    }

    private BigDecimal confidence(List<String> hardFilters, List<String> softPreferences) {
        if (hardFilters.isEmpty() && softPreferences.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal("0.80");
    }

    private void addIfContains(Set<String> values, String text, String value) {
        if (hasText(text) && text.contains(value)) {
            values.add(value);
        }
    }

    private boolean containsAny(String text, String[] signals) {
        if (!hasText(text)) {
            return false;
        }
        for (String signal : signals) {
            if (text.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
