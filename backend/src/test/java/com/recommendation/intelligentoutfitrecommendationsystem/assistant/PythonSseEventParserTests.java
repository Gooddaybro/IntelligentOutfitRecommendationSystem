package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonSseEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonSseEventParser;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PythonSseEventParserTests {

    @Test
    void rejectedReasonCountsAreNeverNullAndCannotBeMutatedThroughTheSourceMap() {
        PythonChatResponse legacy = new PythonChatResponse("req-legacy", "ok", "recommendation", List.of());
        assertThat(legacy.rejectedReasons()).isEmpty();
        PythonChatResponse missing = new PythonChatResponse(
                "req-missing", "ok", "recommendation", List.of(), null
        );
        assertThat(missing.rejectedReasons()).isEmpty();

        Map<String, Integer> source = new HashMap<>(Map.of("HARD_FILTER_MISMATCH", 2));
        PythonChatResponse response = new PythonChatResponse(
                "req-copy", "ok", "recommendation", List.of(), source
        );
        source.put("SIZE_MISMATCH", 1);

        assertThat(response.rejectedReasons()).containsExactlyEntriesOf(Map.of("HARD_FILTER_MISMATCH", 2));
        assertThatThrownBy(() -> response.rejectedReasons().put("LOW_STYLE_SCORE", 3))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void parsesTokenEventAfterBlankLine() {
        PythonSseEventParser parser = new PythonSseEventParser();

        assertThat(parser.accept("event: token")).isEmpty();
        assertThat(parser.accept("data: {\"content\":\"我建议\"}")).isEmpty();

        Optional<PythonSseEvent> event = parser.accept("");

        assertThat(event).isPresent();
        assertThat(event.get().event()).isEqualTo("token");
        assertThat(event.get().data()).isEqualTo("{\"content\":\"我建议\"}");
    }

    @Test
    void parsesDoneAndErrorEvents() throws Exception {
        PythonSseEventParser parser = new PythonSseEventParser();

        parser.accept("event: done");
        parser.accept("data: {\"request_id\":\"req-done\",\"answer\":\"ok\",\"intent\":\"recommendation\","
                + "\"product_refs\":[],\"rejected_reasons\":{\"HARD_FILTER_MISMATCH\":2,"
                + "\"SIZE_MISMATCH\":1,\"LOW_STYLE_SCORE\":3,\"MISSING_REQUIRED_EVIDENCE\":4}}");
        Optional<PythonSseEvent> done = parser.accept("");

        parser.accept("event: error");
        parser.accept("data: {\"code\":\"internal_error\",\"message\":\"failed\"}");
        Optional<PythonSseEvent> error = parser.accept("");

        assertThat(done).isPresent();
        PythonChatResponse response = new ObjectMapper().readValue(done.orElseThrow().data(), PythonChatResponse.class);
        assertThat(response.rejectedReasons()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "HARD_FILTER_MISMATCH", 2,
                "SIZE_MISMATCH", 1,
                "LOW_STYLE_SCORE", 3,
                "MISSING_REQUIRED_EVIDENCE", 4
        ));
        assertThat(error).contains(new PythonSseEvent("error", "{\"code\":\"internal_error\",\"message\":\"failed\"}"));
    }

    @Test
    void joinsMultipleDataLinesWithNewline() {
        PythonSseEventParser parser = new PythonSseEventParser();

        parser.accept("event: token");
        parser.accept("data: line-1");
        parser.accept("data: line-2");

        Optional<PythonSseEvent> event = parser.accept("");

        assertThat(event).contains(new PythonSseEvent("token", "line-1\nline-2"));
    }

    @Test
    void ignoresCommentsAndFramesWithoutEventName() {
        PythonSseEventParser parser = new PythonSseEventParser();

        assertThat(parser.accept(": ping")).isEmpty();
        parser.accept("data: {\"content\":\"missing event\"}");

        assertThat(parser.accept("")).isEmpty();
    }
}
