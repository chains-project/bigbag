package uk.gov.pay.adminusers.fixtures;
public class StripeAgreementDbFixture {
    private final uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper;

    private java.lang.String ipAddress = "192.0.2.0";

    private int serviceId;

    private java.time.ZonedDateTime agreementTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));

    private StripeAgreementDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper) {
        this.databaseTestHelper = databaseTestHelper;
    }

    public static uk.gov.pay.adminusers.fixtures.StripeAgreementDbFixture stripeAgreementDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper) {
        return new uk.gov.pay.adminusers.fixtures.StripeAgreementDbFixture(databaseTestHelper);
    }

    public void insert() {
        databaseTestHelper.insertStripeAgreementEntity(serviceId, agreementTime, ipAddress);
    }

    public uk.gov.pay.adminusers.fixtures.StripeAgreementDbFixture withIpAddress(java.lang.String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.StripeAgreementDbFixture withServiceId(int serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.StripeAgreementDbFixture withAgreementTime(java.time.ZonedDateTime agreementTime) {
        this.agreementTime = agreementTime;
        return this;
    }
}
