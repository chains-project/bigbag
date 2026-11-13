package uk.gov.pay.adminusers.app.config;
public class RestClientConfig extends io.dropwizard.Configuration {
    private java.lang.String disabledSecureConnection;

    public RestClientConfig() {
    }

    public java.lang.Boolean isDisabledSecureConnection() {
        return "true".equals(disabledSecureConnection);
    }
}
