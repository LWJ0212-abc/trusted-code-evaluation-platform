package lwj.code.server.submission.infrastructure.objectstore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.InputStream;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ObjectStoreAdapterTest {

    private static final String SOURCE_BUCKET = "tcep-source";
    private static final String RESULT_BUCKET = "tcep-result";

    @Mock
    private S3Client s3Client;

    @Mock
    private ResponseInputStream<GetObjectResponse> responseStream;

    @Mock
    private DeleteObjectResponse deleteResponse;

    @Mock
    private SdkHttpResponse httpResponse;

    private S3ObjectStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectStoreProperties properties = new ObjectStoreProperties(
                "http://127.0.0.1:19000",
                "us-east-1",
                "access-key",
                "secret-key",
                SOURCE_BUCKET,
                RESULT_BUCKET,
                "staging/",
                "submissions/",
                "results/"
        );
        adapter = new S3ObjectStoreAdapter(s3Client, properties);
    }

    @Test
    void headReturnsMetadataAndRequestsExplicitVersion() {
        Instant lastModified = Instant.parse("2026-11-13T08:00:00Z");
        HeadObjectResponse response = HeadObjectResponse.builder()
                .versionId("source-version-1")
                .contentLength(123L)
                .checksumSHA256("base64-checksum")
                .contentType("application/zip")
                .lastModified(lastModified)
                .build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(response);

        ObjectMetadata result = adapter.head(
                SOURCE_BUCKET,
                "staging/upload-1/source.zip",
                "source-version-1"
        );

        ArgumentCaptor<HeadObjectRequest> request =
                ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(request.capture());
        assertEquals(SOURCE_BUCKET, request.getValue().bucket());
        assertEquals("staging/upload-1/source.zip", request.getValue().key());
        assertEquals("source-version-1", request.getValue().versionId());
        assertEquals(SOURCE_BUCKET, result.bucket());
        assertEquals("staging/upload-1/source.zip", result.key());
        assertEquals("source-version-1", result.version());
        assertEquals(123L, result.size());
        assertEquals("base64-checksum", result.sha256());
        assertEquals("application/zip", result.contentType());
        assertEquals(lastModified, result.lastModified());
    }

    @Test
    void headOmitsVersionIdWhenRequestingCurrentObjectVersion() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(10L).build());

        adapter.head(SOURCE_BUCKET, "staging/upload-1/source.zip", null);

        ArgumentCaptor<HeadObjectRequest> request =
                ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(request.capture());
        assertNull(request.getValue().versionId());
    }

    @Test
    void headWrapsS3Failures() {
        SdkClientException s3Exception = SdkClientException.create("object missing");
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception);

        ObjectStoreException exception = assertThrows(
                ObjectStoreException.class,
                () -> adapter.head(SOURCE_BUCKET, "staging/missing.zip", null)
        );

        assertSame(s3Exception, exception.getCause());
    }

    @Test
    void readReturnsS3ResponseStreamAndRequestsExplicitVersion() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        InputStream result = adapter.read(
                SOURCE_BUCKET,
                "staging/upload-1/source.zip",
                "source-version-1"
        );

        ArgumentCaptor<GetObjectRequest> request =
                ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(request.capture());
        assertEquals(SOURCE_BUCKET, request.getValue().bucket());
        assertEquals("staging/upload-1/source.zip", request.getValue().key());
        assertEquals("source-version-1", request.getValue().versionId());
        assertSame(responseStream, result);
    }

    @Test
    void readOmitsVersionIdWhenRequestingCurrentObjectVersion() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        adapter.read(SOURCE_BUCKET, "staging/upload-1/source.zip", null);

        ArgumentCaptor<GetObjectRequest> request =
                ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(request.capture());
        assertNull(request.getValue().versionId());
    }

    @Test
    void readWrapsS3Failures() {
        SdkClientException s3Exception = SdkClientException.create("storage unavailable");
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(s3Exception);

        ObjectStoreException exception = assertThrows(
                ObjectStoreException.class,
                () -> adapter.read(SOURCE_BUCKET, "staging/upload-1/source.zip", null)
        );

        assertSame(s3Exception, exception.getCause());
    }

    @Test
    void deleteAcceptsSuccessfulResponseAndRequestsVersion() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(deleteResponse);
        when(deleteResponse.sdkHttpResponse()).thenReturn(httpResponse);
        when(httpResponse.isSuccessful()).thenReturn(true);

        adapter.delete(SOURCE_BUCKET, "staging/upload-1/source.zip", "source-version-1");

        ArgumentCaptor<DeleteObjectRequest> request =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertEquals(SOURCE_BUCKET, request.getValue().bucket());
        assertEquals("staging/upload-1/source.zip", request.getValue().key());
        assertEquals("source-version-1", request.getValue().versionId());
    }

    @Test
    void deleteOmitsVersionIdWhenDeletingCurrentObject() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(deleteResponse);
        when(deleteResponse.sdkHttpResponse()).thenReturn(httpResponse);
        when(httpResponse.isSuccessful()).thenReturn(true);

        adapter.delete(SOURCE_BUCKET, "staging/upload-1/source.zip", null);

        ArgumentCaptor<DeleteObjectRequest> request =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertNull(request.getValue().versionId());
    }

    @Test
    void deleteWrapsS3Failures() {
        SdkClientException s3Exception = SdkClientException.create("storage unavailable");
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(s3Exception);

        ObjectStoreException exception = assertThrows(
                ObjectStoreException.class,
                () -> adapter.delete(SOURCE_BUCKET, "staging/upload-1/source.zip", null)
        );

        assertSame(s3Exception, exception.getCause());
    }

    @Test
    void deleteRejectsUnsuccessfulSdkResponse() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(deleteResponse);
        when(deleteResponse.sdkHttpResponse()).thenReturn(httpResponse);
        when(httpResponse.isSuccessful()).thenReturn(false);
        when(httpResponse.statusCode()).thenReturn(500);

        ObjectStoreException exception = assertThrows(
                ObjectStoreException.class,
                () -> adapter.delete(SOURCE_BUCKET, "staging/upload-1/source.zip", null)
        );

        assertEquals("删除对象失败，HTTP 状态码: 500", exception.getMessage());
    }

    @Test
    void allowsConfiguredResultBucket() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(1L).build());

        adapter.head(RESULT_BUCKET, "results/task-1/output.txt", null);

        ArgumentCaptor<HeadObjectRequest> request =
                ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(request.capture());
        assertEquals(RESULT_BUCKET, request.getValue().bucket());
    }

    @Test
    void rejectsBucketsOutsideConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.head("untrusted-bucket", "staging/object.zip", null)
        );
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void rejectsNullOrBlankBucketKeyAndBlankVersionBeforeCallingS3() {
        assertThrows(IllegalArgumentException.class, () -> adapter.head(null, "key", null));
        assertThrows(IllegalArgumentException.class, () -> adapter.head(" ", "key", null));
        assertThrows(IllegalArgumentException.class, () -> adapter.head(SOURCE_BUCKET, null, null));
        assertThrows(IllegalArgumentException.class, () -> adapter.head(SOURCE_BUCKET, " ", null));
        assertThrows(IllegalArgumentException.class, () -> adapter.head(SOURCE_BUCKET, "key", " "));

        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void rejectsUnsafeKeysButAcceptsDoubleDotsInsideFilename() {
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.head(SOURCE_BUCKET, "../secret.zip", null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.head(SOURCE_BUCKET, "staging/../secret.zip", null)
        );

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(1L).build());

        adapter.head(SOURCE_BUCKET, "staging/foo..java", null);

        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }
}
