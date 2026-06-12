package uk.gov.pay.adminusers.app.config;
public class AdminUsersConfig extends io.dropwizard.Configuration {
    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private io.dropwizard.db.DataSourceFactory dataSourceFactory;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private uk.gov.pay.adminusers.app.config.JPAConfiguration jpaConfiguration;

    @javax.validation.constraints.NotNull
    private java.lang.String graphiteHost;

    @javax.validation.constraints.NotNull
    private java.lang.Integer graphitePort;

    @javax.validation.constraints.NotNull
    private java.lang.String baseUrl;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private final uk.gov.pay.adminusers.app.config.LinksConfig links = new uk.gov.pay.adminusers.app.config.LinksConfig();

    @javax.validation.constraints.NotNull
    private java.lang.Integer loginAttemptCap;

    @javax.validation.constraints.NotNull
    private uk.gov.pay.adminusers.app.config.NotifyConfiguration notifyConfiguration;

    @javax.validation.constraints.NotNull
    private uk.gov.pay.adminusers.app.config.NotifyDirectDebitConfiguration notifyDirectDebitConfiguration;

    @javax.validation.constraints.NotNull
    private java.lang.Integer forgottenPasswordExpiryMinutes;

    @javax.validation.constraints.NotNull
    private uk.gov.pay.adminusers.app.config.SecondFactorAuthConfiguration secondFactorAuthConfiguration;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private uk.gov.pay.adminusers.app.config.SqsConfig sqsConfig;

    @javax.validation.constraints.NotNull
    private uk.gov.pay.adminusers.app.config.EventSubscriberQueueConfig eventSubscriberQueueConfig;

    @javax.validation.constraints.NotNull
    @com.fasterxml.jackson.annotation.JsonProperty("ledgerBaseURL")
    private java.lang.String ledgerBaseUrl;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private uk.gov.pay.adminusers.app.config.RestClientConfig restClientConfig;

    @com.fasterxml.jackson.annotation.JsonProperty("secondFactorAuthentication")
    public uk.gov.pay.adminusers.app.config.SecondFactorAuthConfiguration getSecondFactorAuthConfiguration() {
        return secondFactorAuthConfiguration;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("database")
    public io.dropwizard.db.DataSourceFactory getDataSourceFactory() {
        return dataSourceFactory;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("jpa")
    public uk.gov.pay.adminusers.app.config.JPAConfiguration getJpaConfiguration() {
        return jpaConfiguration;
    }

    public java.lang.String getGraphiteHost() {
        return graphiteHost;
    }

    public java.lang.Integer getGraphitePort() {
        return graphitePort;
    }

    public java.lang.String getBaseUrl() {
        return baseUrl;
    }

    public java.lang.Integer getLoginAttemptCap() {
        return loginAttemptCap;
    }

    public uk.gov.pay.adminusers.app.config.LinksConfig getLinks() {
        return links;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("notify")
    public uk.gov.pay.adminusers.app.config.NotifyConfiguration getNotifyConfiguration() {
        return notifyConfiguration;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("notifyDirectDebit")
    public uk.gov.pay.adminusers.app.config.NotifyDirectDebitConfiguration getNotifyDirectDebitConfiguration() {
        return notifyDirectDebitConfiguration;
    }

    public java.lang.Integer getForgottenPasswordExpiryMinutes() {
        return forgottenPasswordExpiryMinutes;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("sqs")
    public uk.gov.pay.adminusers.app.config.SqsConfig getSqsConfig() {
        return sqsConfig;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("eventSubscriberQueue")
    public uk.gov.pay.adminusers.app.config.EventSubscriberQueueConfig getEventSubscriberQueueConfig() {
        return eventSubscriberQueueConfig;
    }

    public java.lang.String getLedgerBaseUrl() {
        return ledgerBaseUrl;
    }

    public uk.gov.pay.adminusers.app.config.RestClientConfig getRestClientConfig() {
        return restClientConfig;
    }
}
