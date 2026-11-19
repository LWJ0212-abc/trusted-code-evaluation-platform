package lwj.code.server.submission.application.exception;

import lwj.code.server.shared.error.ApplicationException;
import lwj.code.server.shared.error.ErrorCode;

public class IdempotencyConflictException extends ApplicationException {
    public IdempotencyConflictException(String message) {
        super(ErrorCode.IDEMPOTENCY_CONFLICT, message, false);
    }
}
