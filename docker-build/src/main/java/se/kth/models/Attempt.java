package se.kth.models;

public class Attempt {
    private final int attemptCount;
    private final String processId;
    private final FailureCategory failureCategory;
    private final String logFileParent;
    private final boolean successful;
    private final String containerId;

    public Attempt(int attemptCount, FailureCategory failureCategory, String logFileParent, boolean successful) {
        this(attemptCount, null, failureCategory, logFileParent, successful, null);
    }

    public Attempt(int attemptCount, String processId, FailureCategory failureCategory, String logFileParent, boolean successful) {
        this(attemptCount, processId, failureCategory, logFileParent, successful, null);
    }

    public Attempt(int attemptCount, String processId, FailureCategory failureCategory, String logFileParent, boolean successful, String containerId) {
        this.attemptCount = attemptCount;
        this.processId = processId;
        this.failureCategory = failureCategory;
        this.logFileParent = logFileParent;
        this.successful = successful;
        this.containerId = containerId;
    }

    public int getAttemptCount() { return attemptCount; }
    public String getProcessId() { return processId; }
    public FailureCategory getFailureCategory() { return failureCategory; }
    public String getLogFileParent() { return logFileParent; }
    public boolean isSuccessful() { return successful; }
    public String getContainerId() { return containerId; }
}
