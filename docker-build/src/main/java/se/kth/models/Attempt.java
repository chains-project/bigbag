package se.kth.models;

public class Attempt {
    private final int attemptCount;
    private final FailureCategory failureCategory;
    private final String logFileParent;
    private final boolean successful;

    public Attempt(int attemptCount, FailureCategory failureCategory, String logFileParent, boolean successful) {
        this.attemptCount = attemptCount;
        this.failureCategory = failureCategory;
        this.logFileParent = logFileParent;
        this.successful = successful;
    }

    public int getAttemptCount() {
        return attemptCount;
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

