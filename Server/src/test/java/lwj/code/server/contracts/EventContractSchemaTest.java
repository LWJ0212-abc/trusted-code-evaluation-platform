package lwj.code.server.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventContractSchemaTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static JsonSchema schema;

    @BeforeAll
    static void loadSchema() throws IOException {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        schema = factory.getSchema(Files.readString(rootDocs().resolve("事件契约.json")));
    }

    @Test
    void acceptsEvaluationRequestedFixture() throws IOException {
        assertContractValid(fixture("evaluation-requested.v1.json"));
    }

    @Test
    void acceptsResultAcceptedFixture() throws IOException {
        assertContractValid(fixture("result-accepted.v1.json"));
    }

    @Test
    void rejectsMissingRequiredField() throws IOException {
        JsonNode event = fixture("evaluation-requested.v1.json");
        ((com.fasterxml.jackson.databind.node.ObjectNode) event.get("payload")).remove("submission_id");

        assertContractInvalid(event);
    }

    @Test
    void rejectsMalformedAggregateRevision() throws IOException {
        JsonNode event = fixture("evaluation-requested.v1.json");
        ((com.fasterxml.jackson.databind.node.ObjectNode) event).put("aggregate_revision", "01");

        assertContractInvalid(event);
    }

    @Test
    void rejectsAdditionalEnvelopeField() throws IOException {
        JsonNode event = fixture("result-accepted.v1.json");
        ((com.fasterxml.jackson.databind.node.ObjectNode) event).put("unexpected", true);

        assertContractInvalid(event);
    }

    @Test
    void rejectsAdditionalPayloadField() throws IOException {
        JsonNode event = fixture("evaluation-requested.v1.json");
        ((com.fasterxml.jackson.databind.node.ObjectNode) event.get("payload")).put("source_code", "secret");

        assertContractInvalid(event);
    }

    @Test
    void rejectsTaskAggregateIdMismatch() throws IOException {
        JsonNode event = fixture("result-accepted.v1.json");
        ((com.fasterxml.jackson.databind.node.ObjectNode) event).put(
                "aggregate_id",
                "30000000-0000-4000-8000-000000000099"
        );

        assertTrue(schema.validate(OBJECT_MAPPER.writeValueAsString(event), InputFormat.JSON).isEmpty());
        assertFalse(taskAggregateMatches(event));
    }

    private static JsonNode fixture(String name) throws IOException {
        return OBJECT_MAPPER.readTree(Files.readString(rootDocs().resolve("fixtures/events").resolve(name)));
    }

    private static void assertContractValid(JsonNode event) throws JsonProcessingException {
        assertTrue(schema.validate(OBJECT_MAPPER.writeValueAsString(event), InputFormat.JSON).isEmpty());
        assertTrue(taskAggregateMatches(event));
    }

    private static void assertContractInvalid(JsonNode event) throws JsonProcessingException {
        Set<ValidationMessage> schemaErrors = schema.validate(OBJECT_MAPPER.writeValueAsString(event), InputFormat.JSON);
        assertTrue(!schemaErrors.isEmpty() || !taskAggregateMatches(event));
    }

    private static boolean taskAggregateMatches(JsonNode event) {
        String eventType = event.path("event_type").asText();
        if (!"evaluation.requested".equals(eventType) && !"result.accepted".equals(eventType)) {
            return true;
        }
        return event.path("aggregate_id").asText().equals(event.path("payload").path("evaluation_task_id").asText());
    }

    private static Path rootDocs() {
        Path path = Path.of("..", "..", "docs").normalize();
        if (Files.isDirectory(path)) {
            return path;
        }
        return Path.of("docs");
    }
}
