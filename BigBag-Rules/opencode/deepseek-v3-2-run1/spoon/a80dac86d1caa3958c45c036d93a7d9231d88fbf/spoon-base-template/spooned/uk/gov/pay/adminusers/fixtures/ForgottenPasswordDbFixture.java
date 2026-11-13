package uk.gov.pay.adminusers.fixtures;
public class ForgottenPasswordDbFixture {
    private uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper;

    private int userId;

    private java.time.ZonedDateTime date = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));

    private java.lang.String forgottenPasswordCode = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(100);

    private ForgottenPasswordDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper, int userId) {
        this.databaseTestHelper = databaseTestHelper;
        this.userId = userId;
    }

    public static uk.gov.pay.adminusers.fixtures.ForgottenPasswordDbFixture forgottenPasswordDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseHelper, int userId) {
        return new uk.gov.pay.adminusers.fixtures.ForgottenPasswordDbFixture(databaseHelper, userId);
    }

    public java.lang.String insertForgottenPassword() {
        databaseTestHelper.add(uk.gov.pay.adminusers.model.ForgottenPassword.forgottenPassword(org.apache.commons.lang3.RandomUtils.nextInt(), forgottenPasswordCode, date, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid()), userId);
        return forgottenPasswordCode;
    }

    public uk.gov.pay.adminusers.fixtures.ForgottenPasswordDbFixture expired() {
        date = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minus(91, java.time.temporal.ChronoUnit.MINUTES);
        return this;
    }
}
