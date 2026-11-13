package uk.gov.pay.adminusers.queue.managed;
public class EventSubscriberQueueMessageReceiver implements io.dropwizard.lifecycle.Managed {
    private static final java.lang.String THREAD_NAME = "sqs-message-eventSubscriberQueueMessageReceiver";

    private static final int SCHEDULER_NUMBER_OF_THREADS = 1;

    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

    private final uk.gov.pay.adminusers.queue.event.EventMessageHandler eventMessageHandler;

    private final java.util.concurrent.ScheduledExecutorService scheduledExecutorService;

    private final int queueSchedulerThreadDelayInMilliseconds;

    private final int queueSchedulerShutdownTimeoutInSeconds;

    private boolean queueEnabled;

    @javax.inject.Inject
    public EventSubscriberQueueMessageReceiver(uk.gov.pay.adminusers.queue.event.EventMessageHandler eventMessageHandler, io.dropwizard.setup.Environment environment, uk.gov.pay.adminusers.app.config.AdminUsersConfig adminUsersConfig) {
        this.eventMessageHandler = eventMessageHandler;
        scheduledExecutorService = environment.lifecycle().scheduledExecutorService(uk.gov.pay.adminusers.queue.managed.EventSubscriberQueueMessageReceiver.THREAD_NAME).threads(uk.gov.pay.adminusers.queue.managed.EventSubscriberQueueMessageReceiver.SCHEDULER_NUMBER_OF_THREADS).build();
        uk.gov.pay.adminusers.app.config.EventSubscriberQueueConfig eventSubscriberQueueConfig = adminUsersConfig.getEventSubscriberQueueConfig();
        queueEnabled = eventSubscriberQueueConfig.getEventSubscriberQueueEnabled();
        queueSchedulerThreadDelayInMilliseconds = eventSubscriberQueueConfig.getQueueSchedulerThreadDelayInMilliseconds();
        queueSchedulerShutdownTimeoutInSeconds = eventSubscriberQueueConfig.getQueueSchedulerShutdownTimeoutInSeconds();
    }

    @java.lang.Override
    public void start() {
        if (queueEnabled) {
            int initialDelay = queueSchedulerThreadDelayInMilliseconds;
            scheduledExecutorService.scheduleWithFixedDelay(this::processMessages, initialDelay, queueSchedulerThreadDelayInMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    private void processMessages() {
        logger.info("Queue message receiver thread polling queue");
        try {
            eventMessageHandler.processMessages();
        } catch (java.lang.Exception e) {
            logger.error("Queue message receiver thread exception", e);
        }
    }

    @java.lang.Override
    public void stop() {
        logger.info("Shutting down event subscriber queue message receiver");
        scheduledExecutorService.shutdown();
        try {
            if (scheduledExecutorService.awaitTermination(queueSchedulerShutdownTimeoutInSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                logger.info("Event subscriber queue message receiver shut down cleanly");
            } else {
                logger.error("Event subscriber queue still processing messages after shutdown wait time will now be forcefully stopped");
                scheduledExecutorService.shutdownNow();
                if (!scheduledExecutorService.awaitTermination(12, java.util.concurrent.TimeUnit.SECONDS)) {
                    logger.error("Event subscriber queue receiver could not be forced stopped");
                }
            }
        } catch (java.lang.InterruptedException ex) {
            logger.error("Failed to shutdown event subscriber queue message receiver cleanly as the wait was interrupted.");
            scheduledExecutorService.shutdownNow();
            // Preserve interrupt status
            java.lang.Thread.currentThread().interrupt();
        }
    }
}
