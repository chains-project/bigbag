package uk.gov.pay.adminusers.persistence.dao;
public class UserDaoIT extends uk.gov.pay.adminusers.persistence.dao.DaoTestBase {
    private uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        userDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.UserDao.class);
        serviceDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.ServiceDao.class);
        roleDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.RoleDao.class);
    }

    @org.junit.jupiter.api.Test
    void getAdminUserEmailsForGatewayAccountIds_should_return_empty_map() {
        java.util.Map<java.lang.String, java.util.List<java.lang.String>> map = userDao.getAdminUserEmailsForGatewayAccountIds(java.util.List.of());
        org.hamcrest.MatcherAssert.assertThat(map.isEmpty(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void shouldCreateAUserSuccessfully() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertRole();
        java.lang.String gatewayAccountId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString();
        int serviceId = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withGatewayAccountIds(gatewayAccountId).insertService().getId();
        java.lang.String username = java.lang.String.valueOf(org.apache.commons.lang3.RandomUtils.nextInt());
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        userEntity.setExternalId(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
        userEntity.setUsername(username);
        userEntity.setPassword("password-" + username);
        userEntity.setDisabled(false);
        userEntity.setEmail(username + "@example.com");
        userEntity.setOtpKey(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString());
        userEntity.setTelephoneNumber("+447700900000");
        userEntity.setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS);
        userEntity.setSessionVersion(0);
        java.time.ZonedDateTime timeNow = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));
        userEntity.setCreatedAt(timeNow);
        userEntity.setUpdatedAt(timeNow);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = serviceDao.findByGatewayAccountId(gatewayAccountId).get();
        uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity = roleDao.findByRoleName(role.getName()).get();
        uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRoleEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(serviceEntity, roleEntity);
        serviceRoleEntity.setUser(userEntity);
        userEntity.addServiceRole(serviceRoleEntity);
        userDao.persist(userEntity);
        org.hamcrest.MatcherAssert.assertThat(userEntity.getId(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> savedUserData = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findUser(userEntity.getId());
        org.hamcrest.MatcherAssert.assertThat(savedUserData.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(((java.lang.String) (savedUserData.get(0).get("external_id"))), org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString()));
        org.hamcrest.MatcherAssert.assertThat(((java.lang.String) (savedUserData.get(0).get("external_id"))).length(), org.hamcrest.Matchers.equalTo(32));
        org.hamcrest.MatcherAssert.assertThat(savedUserData.get(0).get("username"), org.hamcrest.core.Is.is(userEntity.getUsername()));
        org.hamcrest.MatcherAssert.assertThat(savedUserData.get(0).get("password"), org.hamcrest.core.Is.is(userEntity.getPassword()));
        org.hamcrest.MatcherAssert.assertThat(savedUserData.get(0).get("email"), org.hamcrest.core.Is.is(userEntity.getEmail()));
        org.hamcrest.MatcherAssert.assertThat(savedUserData.get(0).get("otp_key"), org.hamcrest.core.Is.is(userEntity.getOtpKey()));
        org.hamcrest.MatcherAssert.assertThat(savedUserData.get(0).get("telephone_number"), org.hamcrest.core.Is.is(userEntity.getTelephoneNumber()));
        org.hamcrest.MatcherAssert.assertThat(savedUserData.get(0).get("disabled"), org.hamcrest.core.Is.is(java.lang.Boolean.FALSE));
        org.hamcrest.MatcherAssert.assertThat(savedUserData.get(0).get("session_version"), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(savedUserData.get(0).get("createdat"), org.hamcrest.core.Is.is(java.sql.Timestamp.from(timeNow.toInstant())));
        org.hamcrest.MatcherAssert.assertThat(savedUserData.get(0).get("updatedat"), org.hamcrest.core.Is.is(java.sql.Timestamp.from(timeNow.toInstant())));
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> serviceRolesForUser = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findServiceRoleForUser(userEntity.getId());
        org.hamcrest.MatcherAssert.assertThat(serviceRolesForUser.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(serviceRolesForUser.get(0).get("id"), org.hamcrest.core.Is.is(role.getId()));
        org.hamcrest.MatcherAssert.assertThat(serviceRolesForUser.get(0).get("service_id"), org.hamcrest.core.Is.is(serviceId));
        org.hamcrest.MatcherAssert.assertThat(serviceRolesForUser.get(0).get("name"), org.hamcrest.core.Is.is(role.getName()));
        org.hamcrest.MatcherAssert.assertThat(serviceRolesForUser.get(0).get("description"), org.hamcrest.core.Is.is(role.getDescription()));
    }

    @org.junit.jupiter.api.Test
    public void shouldFindUserBy_ExternalId() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertRole();
        int serviceId1 = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertService().getId();
        int serviceId2 = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertService().getId();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceRole(serviceId1, role.getId()).withServiceRole(serviceId2, role.getId()).withUsername(username).withEmail(email).insertUser();
        java.lang.String externalId = user.getExternalId();
        java.lang.String otpKey = user.getOtpKey();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityMaybe = userDao.findByExternalId(externalId);
        org.junit.jupiter.api.Assertions.assertTrue(userEntityMaybe.isPresent());
        uk.gov.pay.adminusers.persistence.entity.UserEntity foundUser = userEntityMaybe.get();
        org.hamcrest.MatcherAssert.assertThat(foundUser.getExternalId(), org.hamcrest.core.Is.is(externalId));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getUsername(), org.hamcrest.core.Is.is(username));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getEmail(), org.hamcrest.core.Is.is(email));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getOtpKey(), org.hamcrest.core.Is.is(otpKey));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getTelephoneNumber(), org.hamcrest.core.Is.is("+447700900000"));
        org.hamcrest.MatcherAssert.assertThat(foundUser.isDisabled(), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getLoginCounter(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getSessionVersion(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getRoles().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(foundUser.toUser().getServiceRoles().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getRoles().get(0).getId(), org.hamcrest.core.Is.is(role.getId()));
    }

    @org.junit.jupiter.api.Test
    public void shouldFindUsersBy_ExternalIds() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertRole();
        int serviceId1 = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertService().getId();
        int serviceId2 = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertService().getId();
        java.lang.String username1 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email1 = username1 + "@example.com";
        uk.gov.pay.adminusers.model.User user1 = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceRole(serviceId1, role.getId()).withServiceRole(serviceId2, role.getId()).withUsername(username1).withEmail(email1).insertUser();
        java.lang.String username2 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email2 = username2 + "@example.com";
        uk.gov.pay.adminusers.model.User user2 = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceRole(serviceId1, role.getId()).withServiceRole(serviceId2, role.getId()).withUsername(username2).withEmail(email2).insertUser();
        // Add third user to prove we're not just returning all users
        java.lang.String username3 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email3 = username3 + "@example.com";
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceRole(serviceId1, role.getId()).withServiceRole(serviceId2, role.getId()).withUsername(username3).withEmail(email3).insertUser();
        java.util.List<java.lang.String> externalIds = java.util.Arrays.asList(user1.getExternalId(), user2.getExternalId());
        java.util.List<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntities = userDao.findByExternalIds(externalIds);
        org.hamcrest.MatcherAssert.assertThat(userEntities.size(), org.hamcrest.core.Is.is(2));
        uk.gov.pay.adminusers.persistence.entity.UserEntity foundUser1 = userEntities.get(0);
        org.hamcrest.MatcherAssert.assertThat(foundUser1.getExternalId(), org.hamcrest.core.Is.is(user1.getExternalId()));
        org.hamcrest.MatcherAssert.assertThat(foundUser1.getUsername(), org.hamcrest.core.Is.is(user1.getUsername()));
        org.hamcrest.MatcherAssert.assertThat(foundUser1.getEmail(), org.hamcrest.core.Is.is(user1.getEmail()));
        org.hamcrest.MatcherAssert.assertThat(foundUser1.getOtpKey(), org.hamcrest.core.Is.is(user1.getOtpKey()));
        org.hamcrest.MatcherAssert.assertThat(foundUser1.getTelephoneNumber(), org.hamcrest.core.Is.is("+447700900000"));
        org.hamcrest.MatcherAssert.assertThat(foundUser1.isDisabled(), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(foundUser1.getLoginCounter(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(foundUser1.getSessionVersion(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(foundUser1.getRoles().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(foundUser1.toUser().getServiceRoles().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(foundUser1.getRoles().get(0).getId(), org.hamcrest.core.Is.is(role.getId()));
        uk.gov.pay.adminusers.persistence.entity.UserEntity foundUser2 = userEntities.get(1);
        org.hamcrest.MatcherAssert.assertThat(foundUser2.getExternalId(), org.hamcrest.core.Is.is(user2.getExternalId()));
        org.hamcrest.MatcherAssert.assertThat(foundUser2.getUsername(), org.hamcrest.core.Is.is(user2.getUsername()));
        org.hamcrest.MatcherAssert.assertThat(foundUser2.getEmail(), org.hamcrest.core.Is.is(user2.getEmail()));
        org.hamcrest.MatcherAssert.assertThat(foundUser2.getOtpKey(), org.hamcrest.core.Is.is(user2.getOtpKey()));
        org.hamcrest.MatcherAssert.assertThat(foundUser2.getTelephoneNumber(), org.hamcrest.core.Is.is("+447700900000"));
        org.hamcrest.MatcherAssert.assertThat(foundUser2.isDisabled(), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(foundUser2.getLoginCounter(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(foundUser2.getSessionVersion(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(foundUser2.getRoles().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(foundUser2.toUser().getServiceRoles().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(foundUser2.getRoles().get(0).getId(), org.hamcrest.core.Is.is(role.getId()));
    }

    @org.junit.jupiter.api.Test
    public void shouldFindUserBy_Username_caseInsensitive() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertRole();
        int serviceId = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertService().getId();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceRole(serviceId, role.getId()).withUsername(username).withEmail(email).insertUser();
        java.lang.String otpKey = user.getOtpKey();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityMaybe = userDao.findByUsername(username.toUpperCase(java.util.Locale.ENGLISH));
        org.junit.jupiter.api.Assertions.assertTrue(userEntityMaybe.isPresent());
        uk.gov.pay.adminusers.persistence.entity.UserEntity foundUser = userEntityMaybe.get();
        org.hamcrest.MatcherAssert.assertThat(foundUser.getEmail(), org.hamcrest.core.Is.is(email));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getUsername(), org.hamcrest.core.Is.is(username));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getOtpKey(), org.hamcrest.core.Is.is(otpKey));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getTelephoneNumber(), org.hamcrest.core.Is.is("+447700900000"));
        org.hamcrest.MatcherAssert.assertThat(foundUser.isDisabled(), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getLoginCounter(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getSessionVersion(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getRoles().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getRoles().get(0).getId(), org.hamcrest.core.Is.is(role.getId()));
    }

    @org.junit.jupiter.api.Test
    public void shouldFindUser_ByEmail_caseInsensitive() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertRole();
        int serviceId = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertService().getId();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceRole(serviceId, role.getId()).withUsername(username).withEmail(email).insertUser();
        java.lang.String otpKey = user.getOtpKey();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityMaybe = userDao.findByEmail(username + "@EXAMPLE.com");
        org.junit.jupiter.api.Assertions.assertTrue(userEntityMaybe.isPresent());
        uk.gov.pay.adminusers.persistence.entity.UserEntity foundUser = userEntityMaybe.get();
        org.hamcrest.MatcherAssert.assertThat(foundUser.getUsername(), org.hamcrest.core.Is.is(username));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getEmail(), org.hamcrest.core.Is.is(email));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getOtpKey(), org.hamcrest.core.Is.is(otpKey));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getTelephoneNumber(), org.hamcrest.core.Is.is("+447700900000"));
        org.hamcrest.MatcherAssert.assertThat(foundUser.isDisabled(), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getLoginCounter(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getSessionVersion(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getRoles().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(foundUser.getRoles().get(0).getId(), org.hamcrest.core.Is.is(role.getId()));
    }

    @org.junit.jupiter.api.Test
    public void shouldAddServiceRoleOfAnExistingUser_whenSettingANewServiceRole() {
        uk.gov.pay.adminusers.model.Role role1 = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertRole();
        uk.gov.pay.adminusers.model.Role role2 = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertRole();
        java.lang.String gatewayAccountId1 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString();
        java.lang.String gatewayAccountId2 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString();
        uk.gov.pay.adminusers.model.Service service1 = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withGatewayAccountIds(gatewayAccountId1).insertService();
        uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withGatewayAccountIds(gatewayAccountId2).insertService();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceRole(service1, role1.getId()).withUsername(username).withEmail(email).insertUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity existingUser = userDao.findByUsername(username).get();
        org.hamcrest.MatcherAssert.assertThat(existingUser.getGatewayAccountId(), org.hamcrest.core.Is.is(gatewayAccountId1));
        org.hamcrest.MatcherAssert.assertThat(existingUser.getRoles().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(existingUser.getRoles().get(0).getId(), org.hamcrest.core.Is.is(role1.getId()));
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity2 = serviceDao.findByGatewayAccountId(gatewayAccountId2).get();
        uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity2 = roleDao.findByRoleName(role2.getName()).get();
        uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRole = new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(serviceEntity2, roleEntity2);
        serviceRole.setUser(existingUser);
        existingUser.addServiceRole(serviceRole);
        userDao.merge(existingUser);
        uk.gov.pay.adminusers.persistence.entity.UserEntity changedUser = userDao.findByUsername(username).get();
        java.util.List<uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity> servicesRoles = changedUser.getServicesRoles();
        org.hamcrest.MatcherAssert.assertThat(servicesRoles.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(servicesRoles.stream().map(sr -> sr.getService().getExternalId()).collect(java.util.stream.Collectors.toUnmodifiableList()), org.hamcrest.Matchers.hasItems(service1.getExternalId(), serviceEntity2.getExternalId()));
        org.hamcrest.MatcherAssert.assertThat(servicesRoles.stream().map(sr -> sr.getRole().getName()).collect(java.util.stream.Collectors.toUnmodifiableList()), org.hamcrest.Matchers.hasItems(role1.getName(), role2.getName()));
    }

    @org.junit.jupiter.api.Test
    public void shouldFindUsers_ByServiceId_OrderedByUsername() {
        uk.gov.pay.adminusers.model.Role role1 = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withName("view").insertRole();
        uk.gov.pay.adminusers.model.Role role2 = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withName("admin").insertRole();
        int serviceId = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertService().getId();
        java.lang.String username1 = "thomas" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email1 = username1 + "@example.com";
        uk.gov.pay.adminusers.model.User user1 = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withUsername(username1).withEmail(email1).withServiceRole(serviceId, role1.getId()).insertUser();
        java.lang.String username2 = "bob" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email2 = username2 + "@example.com";
        uk.gov.pay.adminusers.model.User user2 = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withUsername(username2).withEmail(email2).withServiceRole(serviceId, role2.getId()).insertUser();
        java.util.List<uk.gov.pay.adminusers.persistence.entity.UserEntity> users = userDao.findByServiceId(serviceId);
        org.hamcrest.MatcherAssert.assertThat(users.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(users.get(0).getId(), org.hamcrest.core.Is.is(user2.getId()));
        org.hamcrest.MatcherAssert.assertThat(users.get(1).getId(), org.hamcrest.core.Is.is(user1.getId()));
    }

    @org.junit.jupiter.api.Test
    public void shouldNotFindAnyUser() {
        int serviceId = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertService().getId();
        java.util.List<uk.gov.pay.adminusers.persistence.entity.UserEntity> users = userDao.findByServiceId(serviceId);
        org.hamcrest.MatcherAssert.assertThat(users.isEmpty(), org.hamcrest.core.Is.is(true));
    }
}
