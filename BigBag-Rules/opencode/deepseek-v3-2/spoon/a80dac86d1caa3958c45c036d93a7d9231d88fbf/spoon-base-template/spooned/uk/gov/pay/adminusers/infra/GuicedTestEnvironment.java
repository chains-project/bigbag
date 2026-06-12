package uk.gov.pay.adminusers.infra;
public class GuicedTestEnvironment {
    private final com.google.inject.Injector injector;

    private GuicedTestEnvironment(com.google.inject.persist.jpa.JpaPersistModule persistModule) {
        injector = com.google.inject.Guice.createInjector(new uk.gov.pay.adminusers.infra.GuicedTestEnvironment.DataAccessModule(), persistModule);
    }

    public static uk.gov.pay.adminusers.infra.GuicedTestEnvironment from(com.google.inject.persist.jpa.JpaPersistModule persistModule) {
        return new uk.gov.pay.adminusers.infra.GuicedTestEnvironment(persistModule);
    }

    public uk.gov.pay.adminusers.infra.GuicedTestEnvironment start() {
        injector.getInstance(com.google.inject.persist.PersistService.class).start();
        return this;
    }

    public uk.gov.pay.adminusers.infra.GuicedTestEnvironment stop() {
        injector.getInstance(com.google.inject.persist.PersistService.class).stop();
        return this;
    }

    public <T> T getInstance(java.lang.Class<T> daoClass) {
        return injector.getInstance(daoClass);
    }

    public class DataAccessModule extends com.google.inject.AbstractModule {
        @java.lang.Override
        protected void configure() {
            bind(java.lang.Integer.class).annotatedWith(com.google.inject.name.Names.named("FORGOTTEN_PASSWORD_EXPIRY_MINUTES")).toInstance(90);
            bind(uk.gov.pay.adminusers.persistence.dao.UserDao.class).in(com.google.inject.Singleton.class);
            bind(uk.gov.pay.adminusers.persistence.dao.ForgottenPasswordDao.class).in(com.google.inject.Singleton.class);
            bind(uk.gov.pay.adminusers.persistence.dao.RoleDao.class).in(com.google.inject.Singleton.class);
        }
    }
}
