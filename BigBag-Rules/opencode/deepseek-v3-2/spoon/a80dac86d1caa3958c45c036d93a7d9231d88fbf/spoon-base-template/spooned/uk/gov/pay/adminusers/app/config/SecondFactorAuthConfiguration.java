package uk.gov.pay.adminusers.app.config;
public class SecondFactorAuthConfiguration {
    @javax.validation.constraints.NotNull
    private int timeWindowInSeconds;

    @javax.validation.constraints.NotNull
    private int validTimeWindows;

    public int getTimeWindowInSeconds() {
        return timeWindowInSeconds;
    }

    public int getValidTimeWindows() {
        return validTimeWindows;
    }

    public long getTimeWindowInMillis() {
        return timeWindowInSeconds * 1000L;
    }
}
