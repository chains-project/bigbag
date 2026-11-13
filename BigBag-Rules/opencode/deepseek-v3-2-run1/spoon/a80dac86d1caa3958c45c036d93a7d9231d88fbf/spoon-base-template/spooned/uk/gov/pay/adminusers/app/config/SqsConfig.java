package uk.gov.pay.adminusers.app.config;
public class SqsConfig {
    @javax.validation.constraints.NotNull
    private boolean nonStandardServiceEndpoint;

    private java.lang.String endpoint;

    @javax.validation.constraints.NotNull
    private java.lang.String region;

    private java.lang.String accessKey;

    private java.lang.String secretKey;

    @javax.validation.constraints.NotNull
    private java.lang.String eventSubscriberQueueUrl;

    @javax.validation.constraints.Max(20)
    private int messageMaximumWaitTimeInSeconds;

    @javax.validation.constraints.Max(10)
    private int messageMaximumBatchSize;

    public java.lang.String getEndpoint() {
        return endpoint;
    }

    public java.lang.String getRegion() {
        return region;
    }

    public java.lang.String getAccessKey() {
        return accessKey;
    }

    public java.lang.String getSecretKey() {
        return secretKey;
    }

    public boolean isNonStandardServiceEndpoint() {
        return nonStandardServiceEndpoint;
    }

    public java.lang.String getEventSubscriberQueueUrl() {
        return eventSubscriberQueueUrl;
    }

    public int getMessageMaximumWaitTimeInSeconds() {
        return messageMaximumWaitTimeInSeconds;
    }

    public int getMessageMaximumBatchSize() {
        return messageMaximumBatchSize;
    }
}
