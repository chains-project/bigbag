package se.kth.models;

import java.util.ArrayList;
import java.util.List;

public class Result {
    private final String processId;
    private final String fullProcessId;
    private final FailureCategory failureCategory;
    private final List<Attempt> attempts;

    public Result(FailureCategory failureCategory) {
        this(null, null, failureCategory);
    }

    public Result(String processId, String fullProcessId, FailureCategory failureCategory) {
        this.processId = processId;
        this.fullProcessId = fullProcessId;
        this.failureCategory = failureCategory;
        this.attempts = new ArrayList<>();
    }

    public String getProcessId() {
        return processId;
    }

    public String getFullProcessId() {
        return fullProcessId;
    }

    public FailureCategory getFailureCategory() {
        return failureCategory;
    }

    public List<Attempt> getAttempts() {
        return attempts;
    }
}

