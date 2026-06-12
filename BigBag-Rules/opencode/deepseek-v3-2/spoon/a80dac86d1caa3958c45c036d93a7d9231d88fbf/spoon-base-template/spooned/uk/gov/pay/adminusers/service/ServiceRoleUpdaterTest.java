package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class ServiceRoleUpdaterTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private uk.gov.pay.adminusers.service.ServiceRoleUpdater serviceRoleUpdater;

    private static final java.lang.String EXISTING_USER_EXTERNAL_ID = "7d19aff33f8948deb97ed16b2912dcd3";

    private static final java.lang.String NON_EXISTENT_USER_EXTERNAL_ID = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        serviceRoleUpdater = new uk.gov.pay.adminusers.service.ServiceRoleUpdater(userDao, serviceDao, roleDao, new uk.gov.pay.adminusers.service.LinksBuilder("http://localhost"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnEmpty_ifUserNotFound_whenUpdatingServiceRole() {
        org.mockito.Mockito.when(userDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.NON_EXISTENT_USER_EXTERNAL_ID)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = serviceRoleUpdater.doUpdate(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.NON_EXISTENT_USER_EXTERNAL_ID, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), "randomRole");
        org.junit.jupiter.api.Assertions.assertFalse(userOptional.isPresent());
    }

    @org.junit.jupiter.api.Test
    public void shouldError_ifRoleNotFound_whenUpdatingServiceRole() {
        java.lang.String randomRole = "randomRole";
        org.mockito.Mockito.when(userDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID)).thenReturn(java.util.Optional.of(uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID))));
        org.mockito.Mockito.when(roleDao.findByRoleName(randomRole)).thenReturn(java.util.Optional.empty());
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceRoleUpdater.doUpdate(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), randomRole));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 400 Bad Request"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_ifServiceNotBelongToUser_whenUpdatingServiceRole() {
        java.lang.String role = "a-role";
        org.mockito.Mockito.when(userDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID)).thenReturn(java.util.Optional.of(uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID))));
        org.mockito.Mockito.when(roleDao.findByRoleName(role)).thenReturn(java.util.Optional.of(new uk.gov.pay.adminusers.persistence.entity.RoleEntity(aRole(1, role))));
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceRoleUpdater.doUpdate(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), role));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 409 Conflict"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_ifCountOfServiceAdminsLessThan1_whenUpdatingServiceRole() {
        java.lang.String role = "a-role";
        java.lang.String serviceExternalId = "sxrdctfvygbuhinj";
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID));
        uk.gov.pay.adminusers.persistence.entity.RoleEntity targetRoleEntity = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(aRole(10, role));
        uk.gov.pay.adminusers.persistence.entity.RoleEntity currentRoleEntity = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(aRole(uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId(), "admin"));
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity(java.util.Collections.singletonList("1"));
        serviceEntity.setExternalId(serviceExternalId);
        userEntity.addServiceRole(new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(serviceEntity, currentRoleEntity));
        org.mockito.Mockito.when(userDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID)).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(roleDao.findByRoleName(role)).thenReturn(java.util.Optional.of(targetRoleEntity));
        org.mockito.Mockito.when(serviceDao.countOfUsersWithRoleForService(serviceExternalId, uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId())).thenReturn(1L);
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceRoleUpdater.doUpdate(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID, serviceExternalId, role));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 412 Precondition Failed"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnUpdatedUser_whenUpdatingServiceRoleSuccess() {
        java.lang.String role = "another-non-admin-role";
        java.lang.String serviceExternalId = "sxrdctfvygbuhinj";
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID));
        uk.gov.pay.adminusers.persistence.entity.RoleEntity targetRoleEntity = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(aRole(10, role));
        uk.gov.pay.adminusers.persistence.entity.RoleEntity currentRoleEntity = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(aRole(9, "non-admin-role"));
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity(java.util.Collections.singletonList("1"));
        serviceEntity.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        serviceEntity.setExternalId(serviceExternalId);
        userEntity.addServiceRole(new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(serviceEntity, currentRoleEntity));
        org.mockito.Mockito.when(userDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID)).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(roleDao.findByRoleName(role)).thenReturn(java.util.Optional.of(targetRoleEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = serviceRoleUpdater.doUpdate(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID, serviceExternalId, role);
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        // TODO: this looks like a bug in the updater, should only be 1 service role
        org.hamcrest.MatcherAssert.assertThat(userOptional.get().getServiceRoles(), org.hamcrest.collection.IsCollectionWithSize.hasSize(2));
        org.hamcrest.MatcherAssert.assertThat(userOptional.get().getServiceRoles().get(0).getRole().getId(), org.hamcrest.core.Is.is(10));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnUpdatedUser_whenDowngradingAdminWhenEnoughAdminsSuccess() {
        java.lang.String role = "non-admin-role";
        java.lang.String serviceExternalId = "sxrdctfvygbuhinj";
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID));
        uk.gov.pay.adminusers.persistence.entity.RoleEntity targetRoleEntity = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(aRole(9, role));
        uk.gov.pay.adminusers.persistence.entity.RoleEntity currentRoleEntity = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(aRole(uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId(), "admin"));
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity(java.util.Collections.singletonList("1"));
        serviceEntity.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        serviceEntity.setExternalId(serviceExternalId);
        userEntity.addServiceRole(new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(serviceEntity, currentRoleEntity));
        org.mockito.Mockito.when(userDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID)).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(roleDao.findByRoleName(role)).thenReturn(java.util.Optional.of(targetRoleEntity));
        org.mockito.Mockito.when(serviceDao.countOfUsersWithRoleForService(serviceExternalId, uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId())).thenReturn(2L);
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = serviceRoleUpdater.doUpdate(uk.gov.pay.adminusers.service.ServiceRoleUpdaterTest.EXISTING_USER_EXTERNAL_ID, serviceExternalId, role);
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        // TODO: this looks like a bug in the updater, should only be 1 service role
        org.hamcrest.MatcherAssert.assertThat(userOptional.get().getServiceRoles(), org.hamcrest.collection.IsCollectionWithSize.hasSize(2));
        org.hamcrest.MatcherAssert.assertThat(userOptional.get().getServiceRoles().get(0).getRole().getId(), org.hamcrest.core.Is.is(9));
    }

    private uk.gov.pay.adminusers.model.Role aRole(int roleId, java.lang.String roleName) {
        return uk.gov.pay.adminusers.model.Role.role(roleId, roleName, roleName + "-description");
    }

    private uk.gov.pay.adminusers.model.User aUser(java.lang.String externalId) {
        return uk.gov.pay.adminusers.model.User.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), externalId, "random-name", "random-password", "random@example.com", "784rh", "8948924", java.util.Collections.emptyList(), null, uk.gov.pay.adminusers.model.SecondFactorMethod.SMS, null, null, null);
    }
}
