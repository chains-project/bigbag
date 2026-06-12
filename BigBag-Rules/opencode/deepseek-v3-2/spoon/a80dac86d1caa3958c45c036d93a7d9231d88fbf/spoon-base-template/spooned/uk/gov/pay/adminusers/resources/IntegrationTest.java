package uk.gov.pay.adminusers.resources;
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
public class IntegrationTest {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.resources.IntegrationTest.class);

    /* default */
    static final java.lang.String USERS_RESOURCE_URL = "/v1/api/users";

    /* default */
    static final java.lang.String FIND_RESOURCE_URL = "/v1/api/users/find";

    /* default */
    static final java.lang.String USER_RESOURCE_URL = "/v1/api/users/%s";

    /* default */
    static final java.lang.String USERS_AUTHENTICATE_URL = "/v1/api/users/authenticate";

    /* default */
    static final java.lang.String USER_2FA_URL = "/v1/api/users/%s/second-factor";

    /* default */
    static final java.lang.String USER_SERVICES_RESOURCE = uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL + "/services";

    /* default */
    static final java.lang.String USER_SERVICE_RESOURCE = uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL + "/services/%s";

    /* default */
    static final java.lang.String INVITES_RESOURCE_URL = "/v1/api/invites";

    /* default */
    static final java.lang.String INVITES_GENERATE_OTP_RESOURCE_URL = "/v1/api/invites/%s/otp/generate";

    /* default */
    static final java.lang.String INVITES_RESEND_OTP_RESOURCE_URL = "/v1/api/invites/otp/resend";

    /* default */
    static final java.lang.String INVITES_VALIDATE_OTP_RESOURCE_URL = "/v1/api/invites/otp/validate";

    /* default */
    static final java.lang.String SERVICE_INVITES_VALIDATE_OTP_RESOURCE_URL = "/v1/api/invites/otp/validate/service";

    /* default */
    static final java.lang.String SERVICES_RESOURCE = "/v1/api/services";

    /* default */
    static final java.lang.String SERVICE_RESOURCE = uk.gov.pay.adminusers.resources.IntegrationTest.SERVICES_RESOURCE + "/%s";

    /* default */
    @java.lang.Deprecated
    static final java.lang.String SERVICE_INVITES_RESOURCE_URL = "/v1/api/services/%d/invites";

    /* default */
    static final java.lang.String INVITE_USER_RESOURCE_URL = "/v1/api/invites/user";

    public static final io.dropwizard.testing.junit5.DropwizardClientExtension NOTIFY;

    @org.junit.jupiter.api.extension.RegisterExtension
    public static final uk.gov.pay.adminusers.infra.AppWithPostgresExtension APP;

    protected uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseHelper;

    protected static com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @javax.ws.rs.Path("/v2/notifications")
    public static class NotifyResource {
        @javax.ws.rs.Path("/email")
        @javax.ws.rs.POST
        @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
        @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
        public javax.ws.rs.core.Response sendEmail() {
            java.lang.String response = "{\"id\":\"f1356064-37b6-499c-bec9-a167646255ff\", \"content\": {\"subject\":\"hello\", \"body\":\"bla\"}, \"template\": {\"id\":\"f1356064-37b6-499c-bec9-a167646255ff\", \"version\":0, \"uri\":\"lol\"}}";
            return javax.ws.rs.core.Response.status(201).entity(response).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).build();
        }
    }

    static {
        NOTIFY = new io.dropwizard.testing.junit5.DropwizardClientExtension(new uk.gov.pay.adminusers.resources.IntegrationTest.NotifyResource());
        try {
            // starts dropwizard application. This is required as we don't use DropwizardExtensionsSupport (which actually starts application)
            // due to static initialisation of client extension which is needed for DropwizardAppWithPostgresExtension further
            NOTIFY.before();
        } catch (java.lang.Throwable throwable) {
            LOGGER.error("Exception starting client extension for NotifyResource - {}", throwable.getMessage());
            throw new java.lang.RuntimeException(throwable);
        }
        APP = new uk.gov.pay.adminusers.infra.AppWithPostgresExtension(io.dropwizard.testing.ConfigOverride.config("notify.notificationBaseURL", () -> NOTIFY.baseUri().toString()));
    }

    @org.junit.jupiter.api.BeforeEach
    public void initialise() {
        databaseHelper = uk.gov.pay.adminusers.resources.IntegrationTest.APP.getDatabaseTestHelper();
    }

    protected io.restassured.specification.RequestSpecification givenSetup() {
        return io.restassured.RestAssured.given().port(uk.gov.pay.adminusers.resources.IntegrationTest.APP.getLocalPort()).contentType(io.restassured.http.ContentType.JSON);
    }
}
