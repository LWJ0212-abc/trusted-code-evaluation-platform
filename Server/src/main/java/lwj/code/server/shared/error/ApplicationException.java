package lwj.code.server.shared.error;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ApplicationException extends RuntimeException {
    private final ErrorCode code;
    private final String safeMessage;
    private final boolean retryable;
    private final Boolean resourceCreated;
    private final UUID resourceId;

    public ApplicationException(ErrorCode code, String safeMessage, boolean retryable, Boolean resourceCreated, UUID resourceId) {
        this.code = code;
        this.safeMessage = safeMessage;
        this.retryable = retryable;
        this.resourceCreated = resourceCreated;
        this.resourceId = resourceId;
    }
    public ApplicationException( ErrorCode code, String safeMessage, boolean retryable) {

        this(code,safeMessage,retryable,null,null);
    }


}
