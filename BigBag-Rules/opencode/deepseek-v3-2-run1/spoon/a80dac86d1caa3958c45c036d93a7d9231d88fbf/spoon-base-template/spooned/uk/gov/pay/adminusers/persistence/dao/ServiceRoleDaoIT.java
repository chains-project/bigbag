package uk.gov.pay.adminusers.persistence.dao;
public class ServiceRoleDaoIT extends uk.gov.pay.adminusers.persistence.dao.DaoTestBase {
    private uk.gov.pay.adminusers.persistence.dao.ServiceRoleDao serviceRoleDao;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        serviceRoleDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.ServiceRoleDao.class);
    }

    @org.junit.jupiter.api.Test
    public void shouldRemoveAServiceRoleOfAUserSuccessfully() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertService();
        int roleId = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertRole().getId();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceRole(service, roleId).withUsername(username).withEmail(email).insertUser();
        uk.gov.pay.adminusers.persistence.entity.UserServiceId userServiceId = new uk.gov.pay.adminusers.persistence.entity.UserServiceId();
        userServiceId.setServiceId(user.getServiceRoles().get(0).getService().getId());
        userServiceId.setUserId(user.getId());
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> serviceRoles = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findServiceRoleForUser(user.getId());
        org.hamcrest.MatcherAssert.assertThat(serviceRoles.size(), org.hamcrest.core.Is.is(1));
        uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRoleOfUser = serviceRoleDao.findById(userServiceId).get();
        serviceRoleDao.remove(serviceRoleOfUser);
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> serviceRolesAfterRemove = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findServiceRoleForUser(user.getId());
        org.hamcrest.MatcherAssert.assertThat(serviceRolesAfterRemove.size(), org.hamcrest.core.Is.is(0));
    }
}
