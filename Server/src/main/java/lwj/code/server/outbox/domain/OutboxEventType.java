package lwj.code.server.outbox.domain;

public enum OutboxEventType {
    EVALUATION_REQUESTED("evaluation.requested");

    private final String value;

    OutboxEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}

