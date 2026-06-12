package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class ServiceRoleCreatorTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private uk.gov.pay.adminusers.service.ServiceRoleCreator serviceRoleCreator;

    private static final java.lang.String EXISTING_USER_EXTERNAL_ID = "7d19aff33f8948deb97ed16b2912dcd3";

    private static final java.lang.String EXISTING_SERVICE_EXTERNAL_ID = "8374rgw88934r98c9io";

    private static final java.lang.String EXISTING_ROLE_NAME = "admin";

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        serviceRoleCreator = new uk.gov.pay.adminusers.service.ServiceRoleCreator(userDao, serviceDao, roleDao, new uk.gov.pay.adminusers.service.LinksBuilder("http://localhost"));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenAssignANewServiceRole() {
        org.mockito.Mockito.when(userDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID)).thenReturn(java.util.Optional.of(uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID))));
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID)).thenReturn(java.util.Optional.of(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.from(aService(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID))));
        org.mockito.Mockito.when(roleDao.findByRoleName(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_ROLE_NAME)).thenReturn(java.util.Optional.of(new uk.gov.pay.adminusers.persistence.entity.RoleEntity(aRole(1, uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_ROLE_NAME))));
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = serviceRoleCreator.doCreate(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID, uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID, uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_ROLE_NAME);
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        uk.gov.pay.adminusers.model.User user = userOptional.get();
        org.hamcrest.MatcherAssert.assertThat(user.getServiceRoles().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(user.getServiceRoles().get(0).getRole().getName(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_ROLE_NAME));
        org.hamcrest.MatcherAssert.assertThat(user.getServiceRoles().get(0).getService().getExternalId(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnEmpty_whenAssignANewServiceRole_ifUserNotFound() {
        org.mockito.Mockito.when(userDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = serviceRoleCreator.doCreate(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID, uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID, uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_ROLE_NAME);
        org.junit.jupiter.api.Assertions.assertFalse(userOptional.isPresent());
    }

    @org.junit.jupiter.api.Test
    public void shouldError400_whenAssignANewServiceRole_ifServiceNotFound() {
        org.mockito.Mockito.when(userDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID)).thenReturn(java.util.Optional.of(uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID))));
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID)).thenReturn(java.util.Optional.empty());
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceRoleCreator.doCreate(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID, uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID, uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_ROLE_NAME));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 400 Bad Request"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError400_whenAssignANewServiceRole_ifRoleNotFound() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntity.from(aService(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID));
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID));
        uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(aRole(1, uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_ROLE_NAME));
        userEntity.addServiceRole(new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(serviceEntity, roleEntity));
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(userDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID)).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(roleDao.findByRoleName(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_ROLE_NAME)).thenReturn(java.util.Optional.of(roleEntity));
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceRoleCreator.doCreate(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID, uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID, uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_ROLE_NAME));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 409 Conflict"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError409_whenAssignANewServiceRole_ifRoleForServiceAlreadyExists() {
        org.mockito.Mockito.when(userDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID)).thenReturn(java.util.Optional.of(uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID))));
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID)).thenReturn(java.util.Optional.of(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.from(aService(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID))));
        org.mockito.Mockito.when(roleDao.findByRoleName(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_ROLE_NAME)).thenReturn(java.util.Optional.empty());
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceRoleCreator.doCreate(uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_USER_EXTERNAL_ID, uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_SERVICE_EXTERNAL_ID, uk.gov.pay.adminusers.service.ServiceRoleCreatorTest.EXISTING_ROLE_NAME));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 400 Bad Request"));
    }

    private uk.gov.pay.adminusers.model.Service aService(java.lang.String serviceExternalId) {
        return uk.gov.pay.adminusers.model.Service.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), serviceExternalId, new uk.gov.pay.adminusers.model.ServiceName("random-service"));
    }

    private uk.gov.pay.adminusers.model.Role aRole(int roleId, java.lang.String roleName) {
        return uk.gov.pay.adminusers.model.Role.role(roleId, roleName, roleName + "-description");
    }

    private uk.gov.pay.adminusers.model.User aUser(java.lang.String externalId) {
        return uk.gov.pay.adminusers.model.User.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), externalId, "random-name", "random-password", "random@example.com", "784rh", "8948924", java.util.Collections.emptyList(), null, uk.gov.pay.adminusers.model.SecondFactorMethod.SMS, null, null, null);
    }
}
