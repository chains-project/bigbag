package uk.gov.pay.adminusers.app.config;
public class EventSubscriberQueueConfig {
    private java.lang.Boolean eventSubscriberQueueEnabled;

    private int queueSchedulerNumberOfThreads;

    private int queueSchedulerThreadDelayInMilliseconds;

    private int failedMessageRetryDelayInSeconds;

    private int queueSchedulerShutdownTimeoutInSeconds;

    public java.lang.Boolean getEventSubscriberQueueEnabled() {
        return eventSubscriberQueueEnabled;
    }

    public int getQueueSchedulerNumberOfThreads() {
        return queueSchedulerNumberOfThreads;
    }

    public int getQueueSchedulerThreadDelayInMilliseconds() {
        return queueSchedulerThreadDelayInMilliseconds;
    }

    public int getFailedMessageRetryDelayInSeconds() {
        return failedMessageRetryDelayInSeconds;
    }

    public int getQueueSchedulerShutdownTimeoutInSeconds() {
        return queueSchedulerShutdownTimeoutInSeconds;
    }
}
