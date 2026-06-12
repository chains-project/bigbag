package uk.gov.pay.adminusers.persistence.dao;
public class InviteDaoIT extends uk.gov.pay.adminusers.persistence.dao.DaoTestBase {
    private uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    private uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao;

    private uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        inviteDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.InviteDao.class);
        roleDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.RoleDao.class);
        serviceDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.ServiceDao.class);
        userDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.UserDao.class);
    }

    @org.junit.jupiter.api.Test
    public void create_shouldCreateAnInvite() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertRole();
        int serviceId = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertService().getId();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User sender = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withUsername(username).withEmail(email).insertUser();
        uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity = roleDao.findByRoleName(role.getName()).get();
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = serviceDao.findById(serviceId).get();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userSenderEntity = userDao.findById(sender.getId()).get();
        java.lang.String code = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(10);
        java.lang.String otpKey = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(10);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity invite = new uk.gov.pay.adminusers.persistence.entity.InviteEntity("USER@example.com", code, otpKey, roleEntity);
        invite.setService(serviceEntity);
        invite.setSender(userSenderEntity);
        inviteDao.persist(invite);
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> savedInvite = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findInviteById(invite.getId());
        org.hamcrest.MatcherAssert.assertThat(savedInvite.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.get(0).get("sender_id"), org.hamcrest.core.Is.is(userSenderEntity.getId()));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.get(0).get("email"), org.hamcrest.core.Is.is("user@example.com"));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.get(0).get("role_id"), org.hamcrest.core.Is.is(roleEntity.getId()));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.get(0).get("service_id"), org.hamcrest.core.Is.is(serviceId));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.get(0).get("code"), org.hamcrest.core.Is.is(code));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.get(0).get("otp_key"), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.get(0).get("otp_key"), org.hamcrest.core.Is.is(invite.getOtpKey()));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.get(0).get("telephone_number"), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.nullValue()));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.get(0).get("date"), org.hamcrest.core.Is.is(java.sql.Timestamp.from(invite.getDate().toInstant())));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.get(0).get("disabled"), org.hamcrest.core.Is.is(java.lang.Boolean.FALSE));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.get(0).get("login_counter"), org.hamcrest.core.Is.is(0));
    }

    @org.junit.jupiter.api.Test
    public void findByCode_shouldFindAnExistingInvite() {
        java.lang.String code = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertInvite();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.InviteEntity> invite = inviteDao.findByCode(code);
        org.hamcrest.MatcherAssert.assertThat(invite.isPresent(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void findByEmail_shouldFindAnExistingInvite() {
        java.lang.String email = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(5) + "@example.com";
        uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withEmail(email).insertInvite();
        java.util.List<uk.gov.pay.adminusers.persistence.entity.InviteEntity> invites = inviteDao.findByEmail(email);
        org.hamcrest.MatcherAssert.assertThat(invites.isEmpty(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void findAllByServiceId_shouldFindAllInvitesForAService() {
        java.lang.String serviceId = "asfkhsjhfskdf";
        uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceExternalId(serviceId).insertInvite();
        java.util.List<uk.gov.pay.adminusers.persistence.entity.InviteEntity> invites = inviteDao.findAllByServiceId(serviceId);
        org.hamcrest.MatcherAssert.assertThat(invites.size(), org.hamcrest.core.Is.is(1));
    }
}
