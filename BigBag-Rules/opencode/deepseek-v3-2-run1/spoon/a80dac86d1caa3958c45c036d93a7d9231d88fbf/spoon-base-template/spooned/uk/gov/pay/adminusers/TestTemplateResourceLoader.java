package uk.gov.pay.adminusers;
public class TestTemplateResourceLoader {
    private static final java.lang.String TEMPLATE_BASE_NAME = "templates";

    public static final java.lang.String DISPUTE_CREATED_EVENT = uk.gov.pay.adminusers.TestTemplateResourceLoader.TEMPLATE_BASE_NAME + "/events/dispute_created_event.json";

    public static final java.lang.String DISPUTE_LOST_EVENT = uk.gov.pay.adminusers.TestTemplateResourceLoader.TEMPLATE_BASE_NAME + "/events/dispute_lost_event.json";

    public static final java.lang.String DISPUTE_WON_EVENT = uk.gov.pay.adminusers.TestTemplateResourceLoader.TEMPLATE_BASE_NAME + "/events/dispute_won_event.json";

    public static final java.lang.String DISPUTE_EVIDENCE_SUBMITTED_EVENT = uk.gov.pay.adminusers.TestTemplateResourceLoader.TEMPLATE_BASE_NAME + "/events/dispute_evidence_submitted_event.json";

    public static final java.lang.String DISPUTE_CREATED_SNS_MESSAGE = uk.gov.pay.adminusers.TestTemplateResourceLoader.TEMPLATE_BASE_NAME + "/sns/dispute_created_sns_message.json";

    public static java.lang.String load(java.lang.String location) {
        return io.dropwizard.testing.FixtureHelpers.fixture(location);
    }
}
