package uk.gov.pay.adminusers.app.config;
public class AdminUsersSessionCustomiser implements org.eclipse.persistence.config.SessionCustomizer {
    private static final int QUERY_RETRY_ATTEMPT_COUNT_ZERO_BASED_INDEX = 0;

    private static final int DELAY_BETWEEN_CONNECTION_ATTEMPTS_MILLIS = 2000;

    @java.lang.Override
    public void customize(org.eclipse.persistence.sessions.Session session) {
        org.eclipse.persistence.sessions.DatabaseLogin datasourceLogin = ((org.eclipse.persistence.sessions.DatabaseLogin) (session.getDatasourceLogin()));
        datasourceLogin.setQueryRetryAttemptCount(uk.gov.pay.adminusers.app.config.AdminUsersSessionCustomiser.QUERY_RETRY_ATTEMPT_COUNT_ZERO_BASED_INDEX);
        datasourceLogin.setDelayBetweenConnectionAttempts(uk.gov.pay.adminusers.app.config.AdminUsersSessionCustomiser.DELAY_BETWEEN_CONNECTION_ATTEMPTS_MILLIS);
    }
}
