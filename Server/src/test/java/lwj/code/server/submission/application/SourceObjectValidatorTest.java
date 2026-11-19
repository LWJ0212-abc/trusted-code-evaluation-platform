package lwj.code.server.submission.application;

import lwj.code.server.submission.domain.SourceObject;
import lwj.code.server.submission.infrastructure.objectstore.ObjectMetadata;
import lwj.code.server.submission.infrastructure.objectstore.ObjectStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceObjectValidatorTest {

    private static final LocalDateTime TEST_NOW = LocalDateTime.of(2026, 1, 1, 12, 0);
    private static final Clock TEST_CLOCK = Clock.fixed(TEST_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private SourceObjectRepository objectRepository;

    @Mock
    private ObjectStore objectStore;

    private SourceObjectValidator validator;
    private SourceObject sourceObject;
    private byte[] content;
    private String sha256;

    @BeforeEach
    void setUp() throws Exception {
        validator = new SourceObjectValidator(objectRepository, objectStore, TEST_CLOCK);
        content = "trusted source".getBytes(StandardCharsets.UTF_8);
        sha256 = sha256(content);
        sourceObject = new SourceObject(
                "upload-1",
                "owner-1",
                "assignment-1",
                "content-v1",
                "tcep-source",
                "uploads/upload-1.zip",
                null,
                sha256,
                content.length,
                "VALIDATED",
                TEST_NOW.plusMinutes(10),
                TEST_NOW.minusMinutes(1),
                TEST_NOW.minusMinutes(1)
        );
    }

    @Test
    void validateReturnsSourceObjectWhenAllChecksPass() {
        ObjectMetadata metadata = metadata(content.length);
        when(objectRepository.findByUploadId("upload-1"))
                .thenReturn(Optional.of(sourceObject));
        when(objectStore.head("tcep-source", "uploads/upload-1.zip", null))
                .thenReturn(metadata);
        when(objectStore.read("tcep-source", "uploads/upload-1.zip", null))
                .thenReturn(new ByteArrayInputStream(content));

        SourceObject result = validator.validate(
                "upload-1", "owner-1", "assignment-1", "content-v1", sha256
        );

        assertEquals(sourceObject, result);
        verify(objectStore).head("tcep-source", "uploads/upload-1.zip", null);
        verify(objectStore).read("tcep-source", "uploads/upload-1.zip", null);
    }

    @Test
    void validateRejectsObjectOwnedByAnotherUser() {
        when(objectRepository.findByUploadId("upload-1"))
                .thenReturn(Optional.of(sourceObject));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "upload-1", "another-owner", "assignment-1", "content-v1", sha256
        ));

        verify(objectStore, never())
                .read("tcep-source", "uploads/upload-1.zip", null);
    }

    @Test
    void validateRejectsExpiredObject() {
        SourceObject expired = new SourceObject(
                sourceObject.uploadId(), sourceObject.ownerId(), sourceObject.assignmentId(),
                sourceObject.contentVersion(), sourceObject.objectBucket(), sourceObject.objectKey(),
                sourceObject.objectVersion(), sourceObject.sha256(), sourceObject.sizeBytes(),
                sourceObject.validationStatus(), TEST_NOW.minusSeconds(1),
                sourceObject.createdAt(), sourceObject.validatedAt()
        );
        when(objectRepository.findByUploadId("upload-1"))
                .thenReturn(Optional.of(expired));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "upload-1", "owner-1", "assignment-1", "content-v1", sha256
        ));

        verify(objectStore, never())
                .read("tcep-source", "uploads/upload-1.zip", null);
    }

    @Test
    void validateRejectsSizeMismatch() {
        when(objectRepository.findByUploadId("upload-1"))
                .thenReturn(Optional.of(sourceObject));
        when(objectStore.head("tcep-source", "uploads/upload-1.zip", null))
                .thenReturn(metadata(content.length + 1));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "upload-1", "owner-1", "assignment-1", "content-v1", sha256
        ));

        verify(objectStore, never())
                .read("tcep-source", "uploads/upload-1.zip", null);
    }

    @Test
    void validateRejectsActualSha256Mismatch() {
        when(objectRepository.findByUploadId("upload-1"))
                .thenReturn(Optional.of(sourceObject));
        when(objectStore.head("tcep-source", "uploads/upload-1.zip", null))
                .thenReturn(metadata(content.length));
        when(objectStore.read("tcep-source", "uploads/upload-1.zip", null))
                .thenReturn(new ByteArrayInputStream("tampered".getBytes(StandardCharsets.UTF_8)));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "upload-1", "owner-1", "assignment-1", "content-v1", sha256
        ));
    }

    private ObjectMetadata metadata(long size) {
        return new ObjectMetadata(
                "tcep-source",
                "uploads/upload-1.zip",
                null,
                size,
                null,
                "application/zip",
                Instant.now()
        );
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)
        );
    }
}
