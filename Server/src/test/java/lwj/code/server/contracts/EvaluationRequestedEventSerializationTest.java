package lwj.code.server.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import lwj.code.server.evaluation.infrastructure.EvaluationRequestedEvent;
import lwj.code.server.evaluation.infrastructure.EvaluationRequestedPayload;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationRequestedEventSerializationTest {

    private static final tools.jackson.databind.ObjectMapper JACKSON3 = JsonMapper.builder().build();
    private static final ObjectMapper JSON_COMPARATOR = new ObjectMapper();
    private static JsonSchema schema;

    @BeforeAll
    static void loadSchema() throws IOException {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        schema = factory.getSchema(Files.readString(rootDocs().resolve("事件契约.json")));
    }

    @Test
    void serializesDtoAccordingToEvaluationRequestedV1Contract() throws IOException {
        EvaluationRequestedEvent event = new EvaluationRequestedEvent(
                "10000000-0000-4000-8000-000000000001",
                "evaluation.requested",
                1,
                "2026-11-17T02:00:00Z",
                "30000000-0000-4000-8000-000000000001",
                "0",
                "trace-01JCV6QZ5XW9F4M9V8P7",
                new EvaluationRequestedPayload(
                        "20000000-0000-4000-8000-000000000001",
                        "30000000-0000-4000-8000-000000000001",
                        "40000000-0000-4000-8000-000000000001",
                        null
                )
        );

        String json = JACKSON3.writeValueAsString(event);
        JsonNode actual = JSON_COMPARATOR.readTree(json);
        JsonNode fixture = JSON_COMPARATOR.readTree(Files.readString(
                rootDocs().resolve("fixtures/events/evaluation-requested.v1.json")
        ));

        assertEquals(fixture, actual);
        assertTrue(schema.validate(json, InputFormat.JSON).isEmpty());
        assertTrue(actual.path("schema_version").isInt());
        assertEquals(1, actual.path("schema_version").intValue());
        assertTrue(actual.path("aggregate_revision").isTextual());
        assertEquals("0", actual.path("aggregate_revision").textValue());
        assertTrue(actual.path("occurred_at").textValue().endsWith("Z"));
        assertTrue(actual.path("payload").has("batch_id"));
        assertTrue(actual.path("payload").get("batch_id").isNull());
    }

    private static Path rootDocs() {
        Path path = Path.of("..", "..", "docs").normalize();
        if (Files.isDirectory(path)) {
            return path;
        }
        return Path.of("docs");
    }
}
