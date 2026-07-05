package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatHistoryItem;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductRef;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonUserContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class SharedJavaPythonContractTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void javaRequestDtosMatchSharedContractFieldNames() throws Exception {
        JsonNode contract = readSharedContract();

        assertThat(jsonPropertyNames(PythonChatRequest.class))
                .isEqualTo(contractFields(contract, "python_chat_request"));
        assertThat(jsonPropertyNames(PythonChatHistoryItem.class))
                .isEqualTo(contractFields(contract, "chat_history_item"));
        assertThat(jsonPropertyNames(PythonUserContext.class))
                .isEqualTo(contractFields(contract, "user_context"));
        assertThat(jsonPropertyNames(PythonProductCandidate.class))
                .isEqualTo(contractFields(contract, "product_candidate"));
    }

    @Test
    void javaResponseDtosMatchFieldsConsumedFromSharedContract() throws Exception {
        JsonNode contract = readSharedContract();

        assertThat(jsonPropertyNames(PythonChatResponse.class))
                .isEqualTo(contractFields(contract, "java_consumed_chat_response"));
        assertThat(jsonPropertyNames(PythonProductRef.class))
                .isEqualTo(contractFields(contract, "product_ref"));
    }

    private JsonNode readSharedContract() throws IOException {
        Path contractPath = resolveSharedContractPath();
        assertThat(Files.exists(contractPath))
                .as("shared Java-Python contract exists at %s", contractPath)
                .isTrue();

        return objectMapper.readTree(contractPath.toFile());
    }

    private Path resolveSharedContractPath() {
        Path cwd = Path.of("").toAbsolutePath();
        for (Path current = cwd; current != null; current = current.getParent()) {
            Path direct = current.resolve("outfit-project-contract/contracts/java-python-chat/v1.fields.json");
            if (Files.exists(direct)) {
                return direct;
            }

            Path sibling = current.resolve("../outfit-project-contract/contracts/java-python-chat/v1.fields.json").normalize();
            if (Files.exists(sibling)) {
                return sibling;
            }
        }

        return cwd.resolve("../../outfit-project-contract/contracts/java-python-chat/v1.fields.json").normalize();
    }

    private Set<String> jsonPropertyNames(Class<? extends Record> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents())
                .map(RecordComponent::getAccessor)
                .map(accessor -> accessor.getAnnotation(JsonProperty.class))
                .map(JsonProperty::value)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<String> contractFields(JsonNode contract, String fieldSetName) {
        JsonNode fieldSet = contract.path("field_sets").path(fieldSetName);
        assertThat(fieldSet.isArray())
                .as("contract field set %s is declared", fieldSetName)
                .isTrue();

        return StreamSupport.stream(fieldSet.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
