package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantStreamErrorEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 服务降级策略。
 *
 * Python 不可用时，Java 只返回安全说明和空商品引用，避免把外部模型故障扩散到商品、购物车、订单等 Java 事实边界。
 */
@Service
public class AssistantFallbackService {

    private static final String SAFE_CHAT_FALLBACK =
            "AI 导购暂时不可用，但仍可继续浏览当前条件筛选出的商品。"
                    + "你可以补充分类、场景或预算；系统不会放宽已确认的筛选条件。";
    private static final String SAFE_STREAM_FALLBACK = "AI 导购暂时不可用，请稍后再试。";
    private static final String FALLBACK_INTENT = "assistant_fallback";

    public PythonChatResponse chatFallbackResponse(PythonChatRequest pythonRequest) {
        return streamFallbackResponse(pythonRequest.requestId());
    }

    public PythonChatResponse streamFallbackResponse(String requestId) {
        return new PythonChatResponse(
                requestId,
                SAFE_CHAT_FALLBACK,
                FALLBACK_INTENT,
                List.of()
        );
    }

    public AssistantStreamErrorEvent streamFallbackError() {
        return new AssistantStreamErrorEvent("python_stream_unavailable", SAFE_STREAM_FALLBACK);
    }
}
