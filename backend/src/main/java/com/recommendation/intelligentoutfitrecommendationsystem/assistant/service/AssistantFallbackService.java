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
            "AI 导购暂时不可用，我不会编造商品价格、库存或订单信息。"
                    + "你仍然可以继续浏览商品、加入购物车或下单；也可以稍后再试。";
    private static final String SAFE_STREAM_FALLBACK = "AI 导购暂时不可用，请稍后再试。";
    private static final String FALLBACK_INTENT = "assistant_fallback";

    public PythonChatResponse chatFallbackResponse(PythonChatRequest pythonRequest) {
        return new PythonChatResponse(
                pythonRequest.requestId(),
                SAFE_CHAT_FALLBACK,
                FALLBACK_INTENT,
                List.of()
        );
    }

    public AssistantStreamErrorEvent streamFallbackError() {
        return new AssistantStreamErrorEvent("python_stream_unavailable", SAFE_STREAM_FALLBACK);
    }
}
