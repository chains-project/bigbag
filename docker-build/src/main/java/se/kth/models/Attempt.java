package se.kth.models;

public class Attempt {
    private final int attemptCount;
    private final String processId;
    private final FailureCategory failureCategory;
    private final String logFileParent;
    private final boolean successful;

    public Attempt(int attemptCount, FailureCategory failureCategory, String logFileParent, boolean successful) {
        this(attemptCount, null, failureCategory, logFileParent, successful);
    }

    public Attempt(int attemptCount, String processId, FailureCategory failureCategory, String logFileParent, boolean successful) {
        this.attemptCount = attemptCount;
        this.processId = processId;
        this.failureCategory = failureCategory;
        this.logFileParent = logFileParent;
        this.successful = successful;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getProcessId() {
        return processId;
    }

    public FailureCategory getFailureCategory() {
        return failureCategory;
    }

    public String getLogFileParent() {
        return logFileParent;
    }

    public boolean isSuccessful() {
        return successful;
    }
}

