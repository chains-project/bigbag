package uk.gov.pay.adminusers.app.config;
public class JPAConfiguration extends io.dropwizard.Configuration {
    private java.lang.String jpaLoggingLevel;

    private java.lang.String sqlLoggingLevel;

    private java.lang.String ddlGenerationOutputMode;

    private java.lang.String queryResultsCache;

    private java.lang.String cacheSharedDefault;

    public java.lang.String getJpaLoggingLevel() {
        return jpaLoggingLevel;
    }

    public java.lang.String getSqlLoggingLevel() {
        return sqlLoggingLevel;
    }

    public java.lang.String getDdlGenerationOutputMode() {
        return ddlGenerationOutputMode;
    }

    public java.lang.String getQueryResultsCache() {
        return queryResultsCache;
    }

    public java.lang.String getCacheSharedDefault() {
        return cacheSharedDefault;
    }
}
