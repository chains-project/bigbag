package uk.gov.pay.adminusers.service;
public class NotifyClientProvider {
    private uk.gov.pay.adminusers.app.config.NotifyConfiguration configuration;

    public NotifyClientProvider(uk.gov.pay.adminusers.app.config.NotifyConfiguration configuration) {
        this.configuration = configuration;
    }

    public uk.gov.service.notify.NotificationClient get() {
        java.lang.String apiKey = configuration.getCardApiKey();
        return new uk.gov.service.notify.NotificationClient(apiKey, configuration.getNotificationBaseURL(), null);
    }
}
