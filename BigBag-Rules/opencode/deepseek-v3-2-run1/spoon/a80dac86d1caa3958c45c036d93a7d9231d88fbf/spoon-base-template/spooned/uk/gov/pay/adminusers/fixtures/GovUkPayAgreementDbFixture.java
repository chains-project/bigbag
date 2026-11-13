package uk.gov.pay.adminusers.fixtures;
public class GovUkPayAgreementDbFixture {
    private final uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper;

    private java.lang.String email = "someone@exmaple.org";

    private java.time.ZonedDateTime agreementTime = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);

    private int serviceId;

    private GovUkPayAgreementDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper) {
        this.databaseTestHelper = databaseTestHelper;
    }

    public static uk.gov.pay.adminusers.fixtures.GovUkPayAgreementDbFixture govUkPayAgreementDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper) {
        return new uk.gov.pay.adminusers.fixtures.GovUkPayAgreementDbFixture(databaseTestHelper);
    }

    public void insert() {
        databaseTestHelper.insertGovUkPayAgreementEntity(serviceId, email, agreementTime);
    }

    public uk.gov.pay.adminusers.fixtures.GovUkPayAgreementDbFixture withServiceId(int serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.GovUkPayAgreementDbFixture withEmail(java.lang.String email) {
        this.email = email;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.GovUkPayAgreementDbFixture withAgreementTime(java.time.ZonedDateTime agreementTime) {
        this.agreementTime = agreementTime;
        return this;
    }
}
