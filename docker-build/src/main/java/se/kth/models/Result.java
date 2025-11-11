package se.kth.models;

import java.util.ArrayList;
import java.util.List;

public class Result {
    private final FailureCategory failureCategory;
    private final List<Attempt> attempts;

    public Result(FailureCategory failureCategory) {
        this.failureCategory = failureCategory;
        this.attempts = new ArrayList<>();
    }

    public FailureCategory getFailureCategory() {
        return failureCategory;
    }

    public List<Attempt> getAttempts() {
        return attempts;
    }
}

