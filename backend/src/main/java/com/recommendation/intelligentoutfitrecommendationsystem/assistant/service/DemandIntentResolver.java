package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DeterministicDemandParseResult;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.SubjectMeasurements;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;

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
    private static final String[] OUTFIT_SIGNALS = {
            "怎么穿", "如何穿", "如何搭配", "怎么搭配", "穿什么好", "穿啥好", "穿搭"
    };
    private static final String[] SIZE_SIGNALS = {"穿什么码", "穿多大码", "几码", "尺码", "号型"};
    private static final String[] PRODUCT_SIGNALS = {"推荐几件", "想买", "帮我找", "给我推荐", "看看商品"};
    private static final String[] OTHER_SUBJECT_SIGNALS = {
            "我朋友", "我的朋友", "朋友", "男友", "女友", "爸爸", "妈妈", "家人", "给他", "给她"
    };
    private static final String[] SELF_SUBJECT_SIGNALS = {"我本人", "给我自己", "我自己", "本人"};
    private static final Pattern MEASUREMENT_PAIR_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{3})(?:\\s*(?:cm|厘米|公分))?\\s*[,，/]?\\s+(\\d{2,3})(?:\\s*(kg|公斤|千克|斤))?(?!\\d)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MESSAGE_BUDGET_MAX_PATTERN = Pattern.compile(
            "(?:预算\\s*)?(\\d{2,5})\\s*(?:以内|以下|内)|不超过\\s*(\\d{2,5})"
    );

    public DemandIntent resolve(
            AssistantChatRequest request,
            UserBodyDataResponse bodyData,
            UserProfileResponse profile
    ) {
        DemandIntentPatch patch = resolvePatch(request);
        String profileGender = resolveProfileGender(bodyData, profile);
        if (patch.targetGender() == null && !patch.clearTargetGender() && profileGender != null) {
            patch = new DemandIntentPatch(
                    patch.action(), patch.rawQuery(), patch.requestType(), patch.requestedCapabilities(),
                    profileGender, false, patch.category(), patch.season(), patch.scene(), patch.style(),
                    patch.fitPreferences(), patch.budgetMax(), patch.attributes(), patch.subjectMeasurements(),
                    patch.clearSubjectMeasurements()
            );
        }
        return new DemandIntentMerger().merge(null, patch);
    }

    public DemandIntentPatch resolvePatch(AssistantChatRequest request) {
        String rawQuery = request == null || request.message() == null ? "" : request.message();
        boolean compare = containsAny(rawQuery, new String[]{"男款和女款有什么区别", "男女款有什么区别", "男性和女性有什么区别"});
        boolean reset = containsAny(rawQuery, new String[]{"重新开始", "清空条件", "重置条件"});
        boolean clearGender = containsAny(rawQuery, new String[]{"男女都可以", "男女都行", "男女都看看", "性别不限"});
        String targetGender = clearGender || compare ? null : resolveRequestGender(request);
        String action = compare ? "compare" : reset ? "reset" : clearGender ? "clear"
                : containsAny(rawQuery, new String[]{"那女性呢", "换成女款", "那男性呢", "换成男款", "还是看看男性", "还是看看男款"})
                ? "switch" : "initialize";
        String category = resolveCategory(request);
        List<String> scene = resolveScene(rawQuery);
        String requestType = resolveRequestType(rawQuery);
        SubjectMeasurements measurements = resolveSubjectMeasurements(rawQuery);
        boolean clearMeasurements = reset || measurements == null && resolveExplicitSubject(rawQuery) != null;
        return new DemandIntentPatch(
                action,
                rawQuery,
                requestType,
                resolveCapabilities(requestType, rawQuery),
                targetGender,
                clearGender,
                category,
                resolveRequestSeason(request, rawQuery),
                scene,
                resolveStyle(request, rawQuery, scene),
                resolveFitPreferences(rawQuery),
                resolveBudgetMax(request),
                resolveAttributes(rawQuery),
                measurements,
                clearMeasurements
        );
    }

    /** Resolve Java-owned facts and expose why the remaining text may need semantic completion. */
    public DeterministicDemandParseResult resolveDetailed(AssistantChatRequest request) {
        DemandIntentPatch patch = resolvePatch(request);
        String raw = patch.rawQuery();
        List<String> lockedSlots = new ArrayList<>();
        List<String> matchedFragments = new ArrayList<>();
        if (patch.targetGender() != null || patch.clearTargetGender()) {
            lockedSlots.add("targetGender");
            addLongestMatch(matchedFragments, raw, MALE_SIGNALS);
            addLongestMatch(matchedFragments, raw, FEMALE_SIGNALS);
        }
        if (patch.category() != null) {
            lockedSlots.add("category");
            addLongestMatch(matchedFragments, raw,
                    new String[]{"半身裙", "百褶裙", "直筒裙", "裙子", "外套"});
        }
        if (patch.requestType() != null) {
            lockedSlots.add("requestType");
            addLongestMatch(matchedFragments, raw, OUTFIT_SIGNALS);
            addLongestMatch(matchedFragments, raw, SIZE_SIGNALS);
            addLongestMatch(matchedFragments, raw, PRODUCT_SIGNALS);
        }
        if (patch.season() != null) {
            lockedSlots.add("season");
            addLongestMatch(matchedFragments, raw,
                    new String[]{"春天", "春季", "夏天", "夏季", "秋天", "秋季", "冬天", "冬季"});
        }
        if (patch.budgetMax() != null) {
            lockedSlots.add("budgetMax");
            Matcher matcher = MESSAGE_BUDGET_MAX_PATTERN.matcher(raw);
            if (matcher.find()) {
                matchedFragments.add(matcher.group());
            }
        }
        if (!patch.scene().isEmpty()) {
            lockedSlots.add("scene");
            addLongestMatch(matchedFragments, raw, COMMUTE_SIGNALS);
            addLongestMatch(matchedFragments, raw, STUDENT_SIGNALS);
        }
        if (!patch.style().isEmpty()) {
            lockedSlots.add("style");
        }
        if (!patch.fitPreferences().isEmpty()) {
            lockedSlots.add("fitPreferences");
        }
        if (patch.subjectMeasurements() != null) {
            lockedSlots.add("subjectMeasurements");
            matchedFragments.add(patch.subjectMeasurements().originalText());
        }
        if (!patch.attributes().isEmpty()) {
            lockedSlots.add("attributes");
        }

        String unresolved = raw;
        for (String fragment : matchedFragments) {
            unresolved = unresolved.replace(fragment, " ");
        }
        unresolved = unresolved.replaceAll("给|帮我|想要|想找|找|推荐|买|看看|穿搭|一件|一款", " ")
                .replaceAll("[，。！？,.!?\\s]+", " ").trim();
        boolean shoppingSignal = containsAny(raw,
                new String[]{"穿搭", "推荐", "买", "找", "衣服", "外套", "裙", "裤", "上衣", "搭配"});
        return new DeterministicDemandParseResult(
                patch,
                List.copyOf(new LinkedHashSet<>(lockedSlots)),
                List.copyOf(new LinkedHashSet<>(matchedFragments)),
                unresolved,
                shoppingSignal
        );
    }

    private void addLongestMatch(List<String> matches, String text, String[] signals) {
        String longest = null;
        for (String signal : signals) {
            if (hasText(text) && text.contains(signal)
                    && (longest == null || signal.length() > longest.length())) {
                longest = signal;
            }
        }
        if (longest != null) {
            matches.add(longest);
        }
    }

    private String resolveRequestGender(AssistantChatRequest request) {
        String messageGender = genderFromMessage(request == null ? null : request.message());
        return messageGender != null ? messageGender : normalizeGender(request == null ? null : request.gender());
    }

    private String resolveProfileGender(UserBodyDataResponse bodyData, UserProfileResponse profile) {
        String bodyGender = normalizeGender(bodyData == null ? null : bodyData.gender());
        return bodyGender != null ? bodyGender : normalizeGender(profile == null ? null : profile.gender());
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
        if (hasText(message) && (message.contains("休闲") || message.contains("轻松"))) {
            style.add("casual");
        }
        return List.copyOf(style);
    }

    private String resolveRequestType(String message) {
        if (containsAny(message, OUTFIT_SIGNALS)) {
            return "OUTFIT_ADVICE";
        }
        if (containsAny(message, SIZE_SIGNALS)) {
            return "SIZE_RECOMMENDATION";
        }
        if (containsAny(message, PRODUCT_SIGNALS)) {
            return "PRODUCT_RECOMMENDATION";
        }
        String content = message == null ? "" : message
                .replaceAll("你好|嗨|哈喽|谢谢|谢啦|感谢", "")
                .replaceAll("[，,。.!！？?\\s]+", "");
        return content.isEmpty() && hasText(message) ? "CHAT" : null;
    }

    private List<String> resolveCapabilities(String requestType, String message) {
        if (requestType == null) {
            return List.of();
        }
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        switch (requestType) {
            case "OUTFIT_ADVICE" -> {
                capabilities.add("OUTFIT_PLAN");
                capabilities.add("PRODUCT_SELECTION");
            }
            case "PRODUCT_RECOMMENDATION" -> capabilities.add("PRODUCT_SELECTION");
            case "SIZE_RECOMMENDATION" -> capabilities.add("SIZE_GUIDANCE");
            default -> {
                return List.of();
            }
        }
        if (containsAny(message, SIZE_SIGNALS)) {
            capabilities.add("SIZE_GUIDANCE");
        }
        return List.copyOf(capabilities);
    }

    private String resolveSeason(String message) {
        if (containsAny(message, new String[]{"秋冬", "保暖", "厚款", "厚实", "怕冷", "不容易冷"})) {
            return "winter";
        }
        if (containsAny(message, new String[]{"春天", "春季", "春装"})) {
            return "spring";
        }
        if (containsAny(message, new String[]{"夏天", "夏季", "夏装"})) {
            return "summer";
        }
        if (containsAny(message, new String[]{"秋天", "秋季", "秋装"})) {
            return "autumn";
        }
        if (containsAny(message, new String[]{"冬天", "冬季", "冬装"})) {
            return "winter";
        }
        return null;
    }

    private String resolveRequestSeason(AssistantChatRequest request, String message) {
        String explicit = normalizeSeason(request == null ? null : request.season());
        return explicit == null ? resolveSeason(message) : explicit;
    }

    private String normalizeSeason(String season) {
        if (!hasText(season)) {
            return null;
        }
        return switch (season.trim().toLowerCase(Locale.ROOT)) {
            case "spring", "春", "春季", "春天" -> "spring";
            case "summer", "夏", "夏季", "夏天" -> "summer";
            case "autumn", "fall", "秋", "秋季", "秋天" -> "autumn";
            case "winter", "冬", "冬季", "冬天" -> "winter";
            default -> null;
        };
    }

    private List<String> resolveFitPreferences(String message) {
        return containsAny(message, new String[]{"宽松", "略宽松", "宽一点", "轻松一点", "oversize"})
                ? List.of("relaxed") : List.of();
    }

    private SubjectMeasurements resolveSubjectMeasurements(String message) {
        if (!hasText(message)) {
            return null;
        }
        Matcher matcher = MEASUREMENT_PAIR_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        BigDecimal height = new BigDecimal(matcher.group(1));
        BigDecimal originalWeight = new BigDecimal(matcher.group(2));
        String unit = matcher.group(3);
        String normalizedFrom;
        BigDecimal weightKg;
        if (unit != null && Set.of("kg", "公斤", "千克").contains(unit.toLowerCase(Locale.ROOT))) {
            normalizedFrom = "EXPLICIT_KG";
            weightKg = originalWeight;
        } else {
            normalizedFrom = unit == null ? "ASSUMED_JIN" : "EXPLICIT_JIN";
            weightKg = originalWeight.divide(new BigDecimal("2"), 1, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        if (height.compareTo(new BigDecimal("120")) < 0 || height.compareTo(new BigDecimal("230")) > 0
                || weightKg.compareTo(new BigDecimal("30")) < 0 || weightKg.compareTo(new BigDecimal("250")) > 0) {
            return null;
        }
        String subject = resolveMeasurementSubject(message, matcher.start());
        String prefix = measurementPrefix(message, matcher.start(), subject);
        return new SubjectMeasurements(
                height, weightKg, prefix + matcher.group(), normalizedFrom, subject,
                "ACTIVE_DEMAND", "CURRENT_MESSAGE"
        );
    }

    private String resolveMeasurementSubject(String message, int measurementStart) {
        String context = message.substring(Math.max(0, measurementStart - 10), measurementStart);
        if (containsAny(context, OTHER_SUBJECT_SIGNALS)) {
            return "OTHER";
        }
        if (containsAny(context, SELF_SUBJECT_SIGNALS) || context.matches(".*我(?:高)?\\s*$")) {
            return "SELF";
        }
        return "UNKNOWN";
    }

    private String measurementPrefix(String message, int measurementStart, String subject) {
        String context = message.substring(Math.max(0, measurementStart - 10), measurementStart);
        String[] signals = "OTHER".equals(subject) ? OTHER_SUBJECT_SIGNALS
                : "SELF".equals(subject) ? new String[]{"我本人", "给我自己", "我自己", "本人", "我高", "我"}
                : new String[0];
        String longest = "";
        for (String signal : signals) {
            int index = context.lastIndexOf(signal);
            if (index >= 0 && index + signal.length() == context.length() && signal.length() > longest.length()) {
                longest = signal;
            }
        }
        return longest;
    }

    private String resolveExplicitSubject(String message) {
        if (containsAny(message, OTHER_SUBJECT_SIGNALS)) {
            return "OTHER";
        }
        if (containsAny(message, SELF_SUBJECT_SIGNALS)) {
            return "SELF";
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
