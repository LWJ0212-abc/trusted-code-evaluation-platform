package lwj.code.server.submission.infrastructure.objectstore;

public final class ObjectStoreException extends RuntimeException {
    public ObjectStoreException(String message, Throwable cause){
        super(message,cause);
    }
    public ObjectStoreException(String message) {
        super(message);
    }
}
