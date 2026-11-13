package uk.gov.pay.adminusers.app.config;
public class AdminUsersModule extends com.google.inject.AbstractModule {
    private final uk.gov.pay.adminusers.app.config.AdminUsersConfig configuration;

    private final uk.gov.pay.adminusers.app.config.SecondFactorAuthConfiguration secondFactorAuthConfig;

    private final io.dropwizard.setup.Environment environment;

    public AdminUsersModule(final uk.gov.pay.adminusers.app.config.AdminUsersConfig configuration, final io.dropwizard.setup.Environment environment) {
        super();
        this.configuration = configuration;
        this.secondFactorAuthConfig = configuration.getSecondFactorAuthConfiguration();
        this.environment = environment;
    }

    @java.lang.Override
    protected void configure() {
        bind(uk.gov.pay.adminusers.app.config.AdminUsersConfig.class).toInstance(configuration);
        bind(io.dropwizard.setup.Environment.class).toInstance(environment);
        bind(uk.gov.pay.adminusers.service.LinksBuilder.class).toInstance(new uk.gov.pay.adminusers.service.LinksBuilder(configuration.getBaseUrl()));
        bind(com.warrenstrange.googleauth.GoogleAuthenticatorConfig.class).toInstance(new com.warrenstrange.googleauth.GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder().setWindowSize(secondFactorAuthConfig.getValidTimeWindows()).setTimeStepSizeInMillis(secondFactorAuthConfig.getTimeWindowInMillis()).build());
        bind(uk.gov.pay.adminusers.app.config.LinksConfig.class).toInstance(configuration.getLinks());
        bind(java.time.Clock.class).toInstance(java.time.Clock.systemDefaultZone());
        bind(uk.gov.pay.adminusers.service.PasswordHasher.class).in(com.google.inject.Singleton.class);
        bind(uk.gov.pay.adminusers.utils.CountryConverter.class).in(com.google.inject.Singleton.class);
        bind(uk.gov.pay.adminusers.validations.RequestValidations.class).in(com.google.inject.Singleton.class);
        bind(uk.gov.pay.adminusers.resources.UserRequestValidator.class).in(com.google.inject.Singleton.class);
        bind(uk.gov.pay.adminusers.resources.ResetPasswordValidator.class).in(com.google.inject.Singleton.class);
        bind(java.lang.Integer.class).annotatedWith(com.google.inject.name.Names.named("LOGIN_ATTEMPT_CAP")).toInstance(configuration.getLoginAttemptCap());
        bind(uk.gov.pay.adminusers.service.SecondFactorAuthenticator.class).in(com.google.inject.Singleton.class);
        bind(uk.gov.pay.adminusers.service.UserServices.class).in(com.google.inject.Singleton.class);
        bind(uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher.class).in(com.google.inject.Singleton.class);
        bind(uk.gov.pay.adminusers.service.ForgottenPasswordServices.class).in(com.google.inject.Singleton.class);
        bind(uk.gov.pay.adminusers.service.ResetPasswordService.class).in(com.google.inject.Singleton.class);
        bind(java.lang.Integer.class).annotatedWith(com.google.inject.name.Names.named("FORGOTTEN_PASSWORD_EXPIRY_MINUTES")).toInstance(configuration.getForgottenPasswordExpiryMinutes());
        install(jpaModule(configuration));
        install(new com.google.inject.assistedinject.FactoryModuleBuilder().build(uk.gov.pay.adminusers.service.UserServicesFactory.class));
        install(new com.google.inject.assistedinject.FactoryModuleBuilder().build(uk.gov.pay.adminusers.service.ServiceServicesFactory.class));
        install(new com.google.inject.assistedinject.FactoryModuleBuilder().build(uk.gov.pay.adminusers.service.InviteServiceFactory.class));
    }

    private com.google.inject.persist.jpa.JpaPersistModule jpaModule(uk.gov.pay.adminusers.app.config.AdminUsersConfig configuration) {
        io.dropwizard.db.DataSourceFactory dbConfig = configuration.getDataSourceFactory();
        final java.util.Properties properties = new java.util.Properties();
        properties.put("javax.persistence.jdbc.driver", dbConfig.getDriverClass());
        properties.put("javax.persistence.jdbc.url", dbConfig.getUrl());
        properties.put("javax.persistence.jdbc.user", dbConfig.getUser());
        properties.put("javax.persistence.jdbc.password", dbConfig.getPassword());
        uk.gov.pay.adminusers.app.config.JPAConfiguration jpaConfiguration = configuration.getJpaConfiguration();
        properties.put("eclipselink.logging.level", jpaConfiguration.getJpaLoggingLevel());
        properties.put("eclipselink.logging.level.sql", jpaConfiguration.getSqlLoggingLevel());
        properties.put("eclipselink.query-results-cache", jpaConfiguration.getCacheSharedDefault());
        properties.put("eclipselink.cache.shared.default", jpaConfiguration.getCacheSharedDefault());
        properties.put("eclipselink.ddl-generation.output-mode", jpaConfiguration.getDdlGenerationOutputMode());
        properties.put("eclipselink.session.customizer", "uk.gov.pay.adminusers.app.config.AdminUsersSessionCustomiser");
        final com.google.inject.persist.jpa.JpaPersistModule jpaModule = new com.google.inject.persist.jpa.JpaPersistModule("AdminUsersUnit");
        jpaModule.properties(properties);
        return jpaModule;
    }

    @com.google.inject.Provides
    public uk.gov.pay.adminusers.service.NotificationService provideUserNotificationService() {
        return new uk.gov.pay.adminusers.service.NotificationService(new uk.gov.pay.adminusers.service.NotifyClientProvider(configuration.getNotifyConfiguration()), configuration.getNotifyConfiguration(), configuration.getNotifyDirectDebitConfiguration(), environment.metrics());
    }

    @com.google.inject.Provides
    public com.fasterxml.jackson.databind.ObjectMapper provideObjectMapper() {
        return environment.getObjectMapper();
    }

    @com.google.inject.Provides
    public com.amazonaws.services.sqs.AmazonSQS sqsClient(uk.gov.pay.adminusers.app.config.AdminUsersConfig adminUsersConfig) {
        com.amazonaws.services.sqs.AmazonSQSClientBuilder clientBuilder = com.amazonaws.services.sqs.AmazonSQSClientBuilder.standard();
        if (adminUsersConfig.getSqsConfig().isNonStandardServiceEndpoint()) {
            com.amazonaws.auth.BasicAWSCredentials basicAWSCredentials = new com.amazonaws.auth.BasicAWSCredentials(adminUsersConfig.getSqsConfig().getAccessKey(), adminUsersConfig.getSqsConfig().getSecretKey());
            clientBuilder.withCredentials(new com.amazonaws.auth.AWSStaticCredentialsProvider(basicAWSCredentials)).withEndpointConfiguration(new com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration(adminUsersConfig.getSqsConfig().getEndpoint(), adminUsersConfig.getSqsConfig().getRegion()));
        } else {
            // uses AWS SDK's DefaultAWSCredentialsProviderChain to obtain credentials
            clientBuilder.withRegion(adminUsersConfig.getSqsConfig().getRegion());
        }
        return clientBuilder.build();
    }

    @com.google.inject.Provides
    public uk.gov.service.payments.commons.queue.sqs.SqsQueueService provideSqsQueueService(com.amazonaws.services.sqs.AmazonSQS amazonSQS, uk.gov.pay.adminusers.app.config.AdminUsersConfig adminUsersConfig) {
        return new uk.gov.service.payments.commons.queue.sqs.SqsQueueService(amazonSQS, adminUsersConfig.getSqsConfig().getMessageMaximumWaitTimeInSeconds(), adminUsersConfig.getSqsConfig().getMessageMaximumBatchSize());
    }

    @com.google.inject.Provides
    @javax.inject.Singleton
    public javax.ws.rs.client.Client provideClient() {
        return uk.gov.pay.adminusers.app.RestClientFactory.buildClient(configuration.getRestClientConfig());
    }
}
