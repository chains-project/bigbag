package uk.gov.pay.adminusers.app;
public class RestClientFactory {
    private static final java.lang.String TLSV1_2 = "TLSv1.2";

    public static javax.ws.rs.client.Client buildClient(uk.gov.pay.adminusers.app.config.RestClientConfig clientConfig) {
        javax.ws.rs.client.ClientBuilder clientBuilder = javax.ws.rs.client.ClientBuilder.newBuilder();
        if (!clientConfig.isDisabledSecureConnection()) {
            try {
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance(uk.gov.pay.adminusers.app.RestClientFactory.TLSV1_2);
                sslContext.init(null, null, null);
                clientBuilder = clientBuilder.sslContext(sslContext);
            } catch (java.security.NoSuchAlgorithmException | java.security.KeyManagementException e) {
                throw new java.lang.RuntimeException(java.lang.String.format("Unable to find an SSL context for %s", uk.gov.pay.adminusers.app.RestClientFactory.TLSV1_2), e);
            }
        }
        javax.ws.rs.client.Client client = clientBuilder.build();
        client.register(uk.gov.service.payments.logging.RestClientLoggingFilter.class);
        return client;
    }

    private RestClientFactory() {
    }
}
