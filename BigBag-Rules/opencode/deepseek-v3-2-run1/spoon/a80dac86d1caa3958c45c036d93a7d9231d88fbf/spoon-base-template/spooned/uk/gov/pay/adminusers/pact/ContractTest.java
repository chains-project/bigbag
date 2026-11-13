package uk.gov.pay.adminusers.pact;
public abstract class ContractTest {
    @org.junit.ClassRule
    public static uk.gov.pay.adminusers.infra.AppWithPostgresAndSqsRule app = new uk.gov.pay.adminusers.infra.AppWithPostgresAndSqsRule();

    @au.com.dius.pact.provider.junit.target.TestTarget
    public static au.com.dius.pact.provider.junit.target.Target target;

    private static final uk.gov.pay.adminusers.service.PasswordHasher PASSWORD_HASHER = new uk.gov.pay.adminusers.service.PasswordHasher();

    private static uk.gov.pay.adminusers.utils.DatabaseTestHelper dbHelper;

    @org.junit.BeforeClass
    public static void setUpService() {
        uk.gov.pay.adminusers.pact.ContractTest.target = new au.com.dius.pact.provider.junit.target.HttpTarget(uk.gov.pay.adminusers.pact.ContractTest.app.getLocalPort());
        uk.gov.pay.adminusers.pact.ContractTest.dbHelper = uk.gov.pay.adminusers.pact.ContractTest.app.getDatabaseTestHelper();
        // make sure we create services(including gateway account ids) before users
        uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withGatewayAccountIds("268").insertService();
    }

    @org.junit.Before
    public void resetDatabase() {
        uk.gov.pay.adminusers.pact.ContractTest.dbHelper.truncateAllData();
    }

    @au.com.dius.pact.provider.junit.State("a valid forgotten password entry and a related user exists")
    public void aUserExistsWithAForgottenPasswordRequest() {
        java.lang.String code = "avalidforgottenpasswordtoken";
        java.lang.String userExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.pact.ContractTest.createUserWithinAService(userExternalId, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), "password", "cp5wa");
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> userByExternalId = uk.gov.pay.adminusers.pact.ContractTest.dbHelper.findUserByExternalId(userExternalId);
        uk.gov.pay.adminusers.pact.ContractTest.dbHelper.add(uk.gov.pay.adminusers.model.ForgottenPassword.forgottenPassword(code, userExternalId), ((java.lang.Integer) (userByExternalId.get(0).get("id"))));
    }

    @au.com.dius.pact.provider.junit.State("a user exists with max login attempts")
    public void aUserExistsWithMaxLoginAttempts() {
        java.lang.String username = "user-login-attempts-max";
        uk.gov.pay.adminusers.pact.ContractTest.createUserWithinAService(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), username, "password", "cp5wa");
        uk.gov.pay.adminusers.pact.ContractTest.dbHelper.updateLoginCount(username, 10);
    }

    @au.com.dius.pact.provider.junit.State("a forgotten password entry exist")
    public void aForgottenPasswordEntryExist() {
        java.lang.String code = "existing-code";
        java.lang.String existingUserExternalId = "7d19aff33f8948deb97ed16b2912dcd3";
        uk.gov.pay.adminusers.pact.ContractTest.createUserWithinAService(existingUserExternalId, "forgotten-password-user", "password", "cp5wa");
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> userByName = uk.gov.pay.adminusers.pact.ContractTest.dbHelper.findUserByExternalId(existingUserExternalId);
        uk.gov.pay.adminusers.pact.ContractTest.dbHelper.add(uk.gov.pay.adminusers.model.ForgottenPassword.forgottenPassword(code, existingUserExternalId), ((java.lang.Integer) (userByName.get(0).get("id"))));
    }

    @au.com.dius.pact.provider.junit.State("a user and user admin exists in service with the given ids before a delete operation")
    public void aUserAndUserAdminExistBeforeADelete() {
        java.lang.String existingUserExternalId = "pact-delete-user-id";
        java.lang.String existingUserRemoverExternalId = "pact-delete-remover-id";
        java.lang.String existingServiceExternalId = "pact-delete-service-id";
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withExternalId(existingServiceExternalId).insertService();
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).insertAdmin();
        java.lang.String username1 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email1 = username1 + "@example.com";
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withExternalId(existingUserExternalId).withServiceRole(service, role.getId()).withUsername(username1).withEmail(email1).insertUser();
        java.lang.String username2 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email2 = username2 + "@example.com";
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withExternalId(existingUserRemoverExternalId).withServiceRole(service, role.getId()).withUsername(username2).withEmail(email2).insertUser();
    }

    @au.com.dius.pact.provider.junit.State("a user exists but not the remover before a delete operation")
    public void aUserExistButRemoverBeforeADelete() {
        java.lang.String existingUserExternalId = "pact-user-no-remover-test";
        java.lang.String existingServiceExternalId = "pact-service-no-remover-test";
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withExternalId(existingServiceExternalId).insertService();
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).insertAdmin();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withExternalId(existingUserExternalId).withServiceRole(service, role.getId()).withUsername(username).withEmail(email).insertUser();
    }

    @au.com.dius.pact.provider.junit.State({ "a forgotten password does not exists", "a user does not exist", "no user exits with the given name", "a user exits with the given name", "a valid (non-expired) forgotten password entry does not exist", "default", "a user exist", "a user exists with a given username password", "a user not exists with a given username password", "a user not exists with a given username password", "no user exists with the given external id" })
    public void noSetUp() {
    }

    @au.com.dius.pact.provider.junit.State({ "a user exists with the given external id 7d19aff33f8948deb97ed16b2912dcd3", "a user exists", "a user exists with username existing-user", "a user exists with username existing-user and password password", "a user exists with role for service with id cp5wa", "a user exists with external id 7d19aff33f8948deb97ed16b2912dcd3 with admin role for service with id cp5wa" })
    public void aUserExistsWithGivenExternalId() {
        uk.gov.pay.adminusers.pact.ContractTest.createUserWithinAService("7d19aff33f8948deb97ed16b2912dcd3", "existing-user", "password", "cp5wa");
    }

    @au.com.dius.pact.provider.junit.State("a user exists external id 7d19aff33f8948deb97ed16b2912dcd3 and a service exists with external id cp5wa")
    public void aUserExistsNotAssignedToService() {
        uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withExternalId("cp5wa").insertService();
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withExternalId("7d19aff33f8948deb97ed16b2912dcd3").insertUser();
    }

    @au.com.dius.pact.provider.junit.State({ "a service exists with external id cp5wa and billing address collection enabled", "a service exists with external id cp5wa", "a service exists with external id cp5wa with gateway account with id 111" })
    public void aServiceExists() {
        uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withExternalId("cp5wa").withGatewayAccountIds("111").insertService();
    }

    @au.com.dius.pact.provider.junit.State({ "a service exists with custom branding and a gateway account with id 111" })
    public void aServiceExistsWithCustomBranding() {
        uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withGatewayAccountIds("111").withCustomBranding("https://example.org/mycss", "https://example.org/myimage").insertService();
    }

    @au.com.dius.pact.provider.junit.State("a service exists with external id rtglNotStarted and go live stage equals to NOT_STARTED")
    public void aServiceExistsWithNotStartedGoLiveStage() {
        uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withExternalId("rtglNotStarted").withGoLiveStage(uk.gov.pay.adminusers.model.GoLiveStage.NOT_STARTED).insertService();
    }

    @au.com.dius.pact.provider.junit.State("a service exists with external id cp5wa with multiple admin users")
    public void aServiceExistsWithMultipleAdmins() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withExternalId("cp5wa").insertService();
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.pact.ContractTest.createRole();
        uk.gov.pay.adminusers.pact.ContractTest.createUserWithRoleForService("7d19aff33f8948deb97ed16b2912dcd3", "existing-user", "password", role, service);
        uk.gov.pay.adminusers.pact.ContractTest.createUserWithRoleForService("admin-2-id", "admin-2", "password", role, service);
    }

    private static void createUserWithinAService(java.lang.String externalId, java.lang.String username, java.lang.String password, java.lang.String serviceExternalId) {
        java.lang.String gatewayAccount1 = org.apache.commons.lang3.RandomStringUtils.randomNumeric(5);
        java.lang.String gatewayAccount2 = org.apache.commons.lang3.RandomStringUtils.randomNumeric(5);
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withExternalId(serviceExternalId).withGatewayAccountIds(gatewayAccount1, gatewayAccount2).insertService();
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.pact.ContractTest.createRole();
        uk.gov.pay.adminusers.pact.ContractTest.createUserWithRoleForService(externalId, username, password, role, service);
    }

    private static uk.gov.pay.adminusers.model.Role createRole() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.model.Role.role(2, "admin", "Administrator");
        return uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).insert(role, uk.gov.pay.adminusers.model.Permission.permission(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), "perm-1", "permission-1-description"), uk.gov.pay.adminusers.model.Permission.permission(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), "perm-2", "permission-2-description"), uk.gov.pay.adminusers.model.Permission.permission(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), "perm-3", "permission-3-description"));
    }

    private static void createUserWithRoleForService(java.lang.String externalId, java.lang.String username, java.lang.String password, uk.gov.pay.adminusers.model.Role role, uk.gov.pay.adminusers.model.Service service) {
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.pact.ContractTest.dbHelper).withExternalId(externalId).withUsername(username).withPassword(uk.gov.pay.adminusers.pact.ContractTest.PASSWORD_HASHER.hash(password)).withEmail(("user-" + username) + "@example.com").withTelephoneNumber("45334534634").withOtpKey("34f34").withProvisionalOtpKey("94423").withServiceRole(service.getId(), role.getId()).insertUser();
    }
}
