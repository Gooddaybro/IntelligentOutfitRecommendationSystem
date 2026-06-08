package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import java.util.Optional;

/**
 * 面向 Python `/chat/stream` 的逐行 SSE 解析器。
 *
 * 解析器保持无业务依赖，只负责把 event/data 行聚合为完整 SSE 帧；JSON 负载由调用方按事件类型解析。
 */
public class PythonSseEventParser {

    private String eventName;
    private final StringBuilder data = new StringBuilder();

    /**
     * 接收一行 Python SSE 响应内容，并在空行结束一帧时返回事件。
     *
     * @param line 不包含行尾换行符的 SSE 响应行
     * @return 当前行结束完整事件时返回解析结果，否则返回空
     */
    public Optional<PythonSseEvent> accept(String line) {
        if (line == null || line.isEmpty()) {
            return flush();
        }
        if (line.startsWith(":")) {
            return Optional.empty();
        }
        if (line.startsWith("event:")) {
            eventName = valueAfterColon(line);
            return Optional.empty();
        }
        if (line.startsWith("data:")) {
            if (!data.isEmpty()) {
                data.append('\n');
            }
            data.append(valueAfterColon(line));
        }
        return Optional.empty();
    }

    private Optional<PythonSseEvent> flush() {
        if (eventName == null || eventName.isBlank()) {
            clear();
            return Optional.empty();
        }
        PythonSseEvent event = new PythonSseEvent(eventName, data.toString());
        clear();
        return Optional.of(event);
    }

    private void clear() {
        eventName = null;
        data.setLength(0);
    }

    private String valueAfterColon(String line) {
        int colonIndex = line.indexOf(':');
        String value = colonIndex < 0 ? "" : line.substring(colonIndex + 1);
        if (value.startsWith(" ")) {
            return value.substring(1);
        }
        return value;
    }
}
