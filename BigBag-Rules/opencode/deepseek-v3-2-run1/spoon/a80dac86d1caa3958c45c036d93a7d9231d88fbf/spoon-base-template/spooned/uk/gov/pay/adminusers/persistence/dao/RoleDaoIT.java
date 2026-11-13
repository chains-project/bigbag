package uk.gov.pay.adminusers.persistence.dao;
public class RoleDaoIT extends uk.gov.pay.adminusers.persistence.dao.DaoTestBase {
    private uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        roleDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.RoleDao.class);
    }

    @org.junit.jupiter.api.Test
    public void shouldFindARoleByRoleName() {
        uk.gov.pay.adminusers.model.Role role1 = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertRole();
        uk.gov.pay.adminusers.model.Role role2 = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertRole();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.RoleEntity> optionalRole1 = roleDao.findByRoleName(role1.getName());
        junit.framework.TestCase.assertTrue(optionalRole1.isPresent());
        uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity = optionalRole1.get();
        org.hamcrest.MatcherAssert.assertThat(roleEntity.toRole(), org.hamcrest.core.Is.is(role1));
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.RoleEntity> optionalRole2 = roleDao.findByRoleName(role2.getName());
        org.hamcrest.MatcherAssert.assertThat(optionalRole2.get().toRole(), org.hamcrest.core.Is.is(role2));
    }
}
