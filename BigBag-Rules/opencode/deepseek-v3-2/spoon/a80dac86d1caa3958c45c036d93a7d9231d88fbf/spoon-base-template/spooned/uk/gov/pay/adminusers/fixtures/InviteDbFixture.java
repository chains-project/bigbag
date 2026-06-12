package uk.gov.pay.adminusers.fixtures;
public class InviteDbFixture {
    private uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper;

    private java.lang.String email = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(5) + "-invite@example.com";

    private java.time.ZonedDateTime date = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));

    private java.time.ZonedDateTime expiryDate = this.date.plus(1, java.time.temporal.ChronoUnit.DAYS);

    private java.lang.String code = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(100);

    private java.lang.String otpKey = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(100);

    private java.lang.String telephoneNumber;

    private java.lang.String password;

    private java.lang.Boolean disabled = java.lang.Boolean.FALSE;

    private java.lang.Integer loginCounter = 0;

    private java.lang.String externalServiceId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();

    private java.lang.Integer serviceId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt();

    private InviteDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper) {
        this.databaseTestHelper = databaseTestHelper;
    }

    public static uk.gov.pay.adminusers.fixtures.InviteDbFixture inviteDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseHelper) {
        return new uk.gov.pay.adminusers.fixtures.InviteDbFixture(databaseHelper);
    }

    public java.lang.String insertInvite() {
        uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseTestHelper).withId(serviceId).withExternalId(externalServiceId).insertService().getId();
        int roleId = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseTestHelper).insertRole().getId();
        java.lang.String userUsername = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String userEmail = userUsername + "@example.com";
        int invitingUserId = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseTestHelper).withUsername(userUsername).withEmail(userEmail).insertUser().getId();
        databaseTestHelper.addInvite(org.apache.commons.lang3.RandomUtils.nextInt(), invitingUserId, serviceId, roleId, email, code, otpKey, date, expiryDate, telephoneNumber, password, disabled, loginCounter);
        return code;
    }

    public java.lang.String insertServiceInvite() {
        int roleId = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseTestHelper).insertRole().getId();
        java.lang.String userUsername = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String userEmail = userUsername + "@example.com";
        int invitingUserId = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseTestHelper).withUsername(userUsername).withEmail(userEmail).insertUser().getId();
        databaseTestHelper.addServiceInvite(org.apache.commons.lang3.RandomUtils.nextInt(), invitingUserId, roleId, email, code, otpKey, date, expiryDate, telephoneNumber, password, disabled, loginCounter);
        return code;
    }

    public uk.gov.pay.adminusers.fixtures.InviteDbFixture expired() {
        this.expiryDate = this.date.minus(1, java.time.temporal.ChronoUnit.SECONDS);
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.InviteDbFixture disabled() {
        this.disabled = java.lang.Boolean.TRUE;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.InviteDbFixture withLoginCounter(java.lang.Integer loginCounter) {
        this.loginCounter = loginCounter;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.InviteDbFixture withTelephoneNumber(java.lang.String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.InviteDbFixture withEmail(java.lang.String email) {
        this.email = email;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.InviteDbFixture withOtpKey(java.lang.String otpKey) {
        this.otpKey = otpKey;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.InviteDbFixture withPassword(java.lang.String password) {
        this.password = password;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.InviteDbFixture withServiceExternalId(java.lang.String serviceExternalId) {
        this.externalServiceId = serviceExternalId;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.InviteDbFixture withServiceId(java.lang.Integer serviceId) {
        this.serviceId = serviceId;
        return this;
    }
}
