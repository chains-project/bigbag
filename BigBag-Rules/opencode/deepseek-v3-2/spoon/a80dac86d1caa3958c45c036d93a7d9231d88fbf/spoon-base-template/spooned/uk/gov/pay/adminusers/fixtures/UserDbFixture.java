package uk.gov.pay.adminusers.fixtures;
public class UserDbFixture {
    private final uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper;

    private java.util.List<org.apache.commons.lang3.tuple.Pair<uk.gov.pay.adminusers.model.Service, uk.gov.pay.adminusers.model.Role>> serviceRolePairs = new java.util.ArrayList<>();

    private java.lang.String externalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();

    private java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();

    private java.lang.String otpKey = org.apache.commons.lang3.RandomStringUtils.randomAlphabetic(10);

    private java.lang.String password = "password-" + username;

    private java.lang.String email = username + "@example.com";

    private java.lang.String telephoneNumber = "+447700900000";

    private java.lang.String features = "FEATURE_1, FEATURE_2";

    private java.lang.String provisionalOtpKey;

    private uk.gov.pay.adminusers.model.SecondFactorMethod secondFactorMethod = uk.gov.pay.adminusers.model.SecondFactorMethod.SMS;

    private UserDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper) {
        this.databaseTestHelper = databaseTestHelper;
    }

    public static uk.gov.pay.adminusers.fixtures.UserDbFixture userDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper) {
        return new uk.gov.pay.adminusers.fixtures.UserDbFixture(databaseTestHelper);
    }

    public uk.gov.pay.adminusers.model.User insertUser() {
        java.util.List<uk.gov.pay.adminusers.model.ServiceRole> serviceRoles = serviceRolePairs.stream().map(servicePair -> uk.gov.pay.adminusers.model.ServiceRole.from(servicePair.getLeft(), servicePair.getRight())).collect(java.util.stream.Collectors.toUnmodifiableList());
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.model.User.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), externalId, username, password, email, otpKey, telephoneNumber, serviceRoles, features, secondFactorMethod, provisionalOtpKey, null, null);
        databaseTestHelper.add(user);
        serviceRoles.forEach(serviceRole -> databaseTestHelper.addUserServiceRole(user.getId(), serviceRole.getService().getId(), serviceRole.getRole().getId()));
        return user;
    }

    public uk.gov.pay.adminusers.fixtures.UserDbFixture withServiceRole(int serviceId, int roleId) {
        this.serviceRolePairs.add(org.apache.commons.lang3.tuple.Pair.of(uk.gov.pay.adminusers.model.Service.from(serviceId, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE)), uk.gov.pay.adminusers.model.Role.role(roleId, "roleName", "roleDescription")));
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.UserDbFixture withServiceRole(uk.gov.pay.adminusers.model.Service service, int roleId) {
        this.serviceRolePairs.add(org.apache.commons.lang3.tuple.Pair.of(service, uk.gov.pay.adminusers.model.Role.role(roleId, "roleName", "roleDescription")));
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.UserDbFixture withExternalId(java.lang.String externalId) {
        this.externalId = externalId;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.UserDbFixture withUsername(java.lang.String username) {
        this.username = username;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.UserDbFixture withPassword(java.lang.String password) {
        this.password = password;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.UserDbFixture withEmail(java.lang.String email) {
        this.email = email;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.UserDbFixture withFeatures(java.lang.String features) {
        this.features = features;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.UserDbFixture withTelephoneNumber(java.lang.String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.UserDbFixture withOtpKey(java.lang.String otpKey) {
        this.otpKey = otpKey;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.UserDbFixture withProvisionalOtpKey(java.lang.String provisionalOtpKey) {
        this.provisionalOtpKey = provisionalOtpKey;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.UserDbFixture withSecondFactorMethod(uk.gov.pay.adminusers.model.SecondFactorMethod secondFactorMethod) {
        this.secondFactorMethod = secondFactorMethod;
        return this;
    }
}
