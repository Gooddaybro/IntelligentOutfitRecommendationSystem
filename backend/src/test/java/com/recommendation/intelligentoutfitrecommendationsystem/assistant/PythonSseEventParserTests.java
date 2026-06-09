package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonSseEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonSseEventParser;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PythonSseEventParserTests {

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
    void parsesDoneAndErrorEvents() {
        PythonSseEventParser parser = new PythonSseEventParser();

        parser.accept("event: done");
        parser.accept("data: {\"answer\":\"ok\"}");
        Optional<PythonSseEvent> done = parser.accept("");

        parser.accept("event: error");
        parser.accept("data: {\"code\":\"internal_error\",\"message\":\"failed\"}");
        Optional<PythonSseEvent> error = parser.accept("");

        assertThat(done).contains(new PythonSseEvent("done", "{\"answer\":\"ok\"}"));
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
