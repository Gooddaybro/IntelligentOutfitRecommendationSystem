package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把自然语言和显式筛选参数解析成唯一的 DemandIntent。
 *
 * 这里仅保留确定性规则：性别、类目别名、通勤场景、预算和少量属性词。
 */
@Service
public class DemandIntentResolver {

    private static final String MALE = "male";
    private static final String FEMALE = "female";
    private static final String[] MALE_SIGNALS = {"男", "男生", "男性", "男士", "男款", "男朋友", "爸爸", "男友"};
    private static final String[] FEMALE_SIGNALS = {"女", "女生", "女性", "女士", "女款", "女朋友", "妈妈", "女友"};
    private static final String[] SKIRT_SIGNALS = {"裙子", "半裙", "半身裙", "百褶裙", "A字裙", "a字裙", "直筒裙"};
    private static final String[] COMMUTE_SIGNALS = {"上班", "通勤", "职场", "办公室", "上班族"};
    private static final String[] SLIM_SIGNALS = {"显瘦", "遮肉", "修身"};
    private static final String[] TALL_SIGNALS = {"显高", "拉长比例", "小个子"};
    private static final Pattern MESSAGE_BUDGET_MAX_PATTERN = Pattern.compile(
            "(?:预算\\s*)?(\\d{2,5})\\s*(?:以内|以下|内)|不超过\\s*(\\d{2,5})"
    );

    public DemandIntent resolve(
            AssistantChatRequest request,
            UserBodyDataResponse bodyData,
            UserProfileResponse profile
    ) {
        String message = request == null ? null : request.message();
        String targetGender = resolveTargetGender(request, bodyData, profile);
        String category = resolveCategory(request);
        Integer budgetMax = resolveBudgetMax(request);
        Set<String> scene = new LinkedHashSet<>();
        Set<String> style = new LinkedHashSet<>();
        Set<String> attributes = new LinkedHashSet<>();
        Set<String> hardFilters = new LinkedHashSet<>();
        Set<String> softPreferences = new LinkedHashSet<>();

        if (containsAny(message, COMMUTE_SIGNALS)) {
            scene.add("commute");
            style.add("commute");
            style.add("minimal");
        }
        String requestStyle = normalizeText(request == null ? null : request.style());
        if (requestStyle != null) {
            style.add(requestStyle);
        }
        if (containsAny(message, SLIM_SIGNALS)) {
            attributes.add("slim");
        }
        if (containsAny(message, TALL_SIGNALS)) {
            attributes.add("tall");
        }

        addHardFilter(hardFilters, "targetGender", targetGender);
        addHardFilter(hardFilters, "category", category);
        if (budgetMax != null) {
            hardFilters.add("budgetMax");
        }
        addSoftPreference(softPreferences, "scene", scene);
        addSoftPreference(softPreferences, "style", style);
        addSoftPreference(softPreferences, "attributes", attributes);

        return new DemandIntent(
                DemandIntent.VERSION,
                DemandIntent.SOURCE_JAVA_RULE,
                message,
                targetGender,
                category,
                new ArrayList<>(scene),
                new ArrayList<>(style),
                budgetMax,
                new ArrayList<>(attributes),
                new ArrayList<>(hardFilters),
                new ArrayList<>(softPreferences),
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

    private String resolveCategory(AssistantChatRequest request) {
        String requestCategory = normalizeCategory(request == null ? null : request.category());
        if (requestCategory != null) {
            return requestCategory;
        }
        return categoryFromMessage(request == null ? null : request.message());
    }

    private String categoryFromMessage(String message) {
        if (containsAny(message, SKIRT_SIGNALS)) {
            return "半裙";
        }
        return null;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "裙子", "半裙", "半身裙", "百褶裙", "a字裙", "直筒裙" -> "半裙";
            default -> category.trim();
        };
    }

    private String genderFromMessage(String message) {
        boolean hasMale = containsAny(message, MALE_SIGNALS);
        boolean hasFemale = containsAny(message, FEMALE_SIGNALS);
        if (hasMale == hasFemale) {
            return null;
        }
        return hasMale ? MALE : FEMALE;
    }

    private boolean containsAny(String text, String[] signals) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String signal : signals) {
            if (text.contains(signal)) {
                return true;
            }
        }
        return false;
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

    private Integer resolveBudgetMax(AssistantChatRequest request) {
        if (request == null) {
            return null;
        }
        if (request.budgetMax() != null) {
            return request.budgetMax();
        }
        if (request.message() == null || request.message().isBlank()) {
            return null;
        }
        Matcher matcher = MESSAGE_BUDGET_MAX_PATTERN.matcher(request.message());
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return value == null ? null : Integer.valueOf(value);
    }

    private void addHardFilter(Set<String> filters, String key, String value) {
        if (value != null) {
            filters.add(key);
        }
    }

    private void addSoftPreference(Set<String> preferences, String key, Set<String> values) {
        if (!values.isEmpty()) {
            preferences.add(key);
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal confidence(Set<String> hardFilters, Set<String> softPreferences) {
        double score = 0.35 + hardFilters.size() * 0.15 + softPreferences.size() * 0.08;
        return BigDecimal.valueOf(Math.min(score, 0.95)).setScale(2, RoundingMode.HALF_UP);
    }
}
