package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class ServiceUserRemoverTest {
    private uk.gov.pay.adminusers.service.ServiceUserRemover service;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.UserDao mockUserDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.ServiceRoleDao mockServiceRoleDao;

    @org.junit.jupiter.api.BeforeEach
    public void setupServiceUserRemover() {
        service = new uk.gov.pay.adminusers.service.ServiceUserRemover(mockUserDao, mockServiceRoleDao);
    }

    @org.junit.jupiter.api.Test
    public void remove_shouldRemoveAUserFromAService() {
        java.lang.String serviceExternalId = "service-external-id-1";
        java.lang.String removerExternalId = "user-admin-of-service-1";
        java.lang.String userExternalId = "user-to-be-removed-from-service-1";
        uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity userServiceRole = aServiceRole(serviceExternalId, 666);
        uk.gov.pay.adminusers.persistence.entity.UserEntity userToBeRemoved = createUser(userExternalId, userServiceRole);
        uk.gov.pay.adminusers.persistence.entity.UserEntity removerAsAdminOfService = createUser(removerExternalId, aServiceRole(serviceExternalId, uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId()));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(userExternalId)).thenReturn(java.util.Optional.of(userToBeRemoved));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(removerExternalId)).thenReturn(java.util.Optional.of(removerAsAdminOfService));
        service.remove(userExternalId, removerExternalId, serviceExternalId);
        org.mockito.Mockito.verify(mockServiceRoleDao).remove(userServiceRole);
    }

    @org.junit.jupiter.api.Test
    public void should_remove_user_from_service_by_toolbox() {
        java.lang.String serviceExternalId = "service-external-id-1";
        java.lang.String userExternalId = "user-to-be-removed-from-service-1";
        uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity userServiceRole = aServiceRole(serviceExternalId, 666);
        uk.gov.pay.adminusers.persistence.entity.UserEntity userToBeRemoved = createUser(userExternalId, userServiceRole);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(userExternalId)).thenReturn(java.util.Optional.of(userToBeRemoved));
        service.removeWithoutAdminCheck(userExternalId, serviceExternalId);
        org.mockito.Mockito.verify(mockServiceRoleDao).remove(userServiceRole);
    }

    @org.junit.jupiter.api.Test
    public void remove_shouldThrowNotFoundWebApplicationException_whenUserToBeRemovedDoesNotExist() {
        java.lang.String serviceExternalId = "service-external-id-1";
        java.lang.String userExternalId = "user-to-be-removed-from-a-service";
        org.mockito.Mockito.when(mockUserDao.findByExternalId(userExternalId)).thenReturn(java.util.Optional.empty());
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> service.remove(userExternalId, "any-remover-ext-id", serviceExternalId));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 404 Not Found"));
        org.mockito.Mockito.verifyNoInteractions(mockServiceRoleDao);
        org.mockito.Mockito.verify(mockUserDao).findByExternalId(userExternalId);
    }

    @org.junit.jupiter.api.Test
    public void remove_shouldThrowNotFoundWebApplicationException_whenUserDoesNotBelongToTheGivenService() {
        java.lang.String serviceExternalId = "service-external-id-1";
        java.lang.String otherServiceExternalId = "service-external-id-2";
        java.lang.String aRemoverExternalId = "user-admin-of-service-1";
        java.lang.String userExternalId = "user-to-be-removed-from-service-1";
        uk.gov.pay.adminusers.persistence.entity.UserEntity userToRemoveBelongsToOtherService = createUser(userExternalId, aServiceRole(otherServiceExternalId, 666));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(userExternalId)).thenReturn(java.util.Optional.of(userToRemoveBelongsToOtherService));
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> service.remove(userExternalId, aRemoverExternalId, serviceExternalId));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 404 Not Found"));
        org.mockito.Mockito.verifyNoInteractions(mockServiceRoleDao);
        org.mockito.Mockito.verify(mockUserDao).findByExternalId(userExternalId);
    }

    @org.junit.jupiter.api.Test
    public void remove_shouldThrowForbiddenWebApplicationException_whenRemoverDoesNotExist() {
        java.lang.String serviceExternalId = "service-external-id-1";
        java.lang.String removerExternalId = "non-existing-remover";
        java.lang.String userExternalId = "user-to-be-removed-from-service-1";
        uk.gov.pay.adminusers.persistence.entity.UserEntity userToBeRemoved = createUser(userExternalId, aServiceRole(serviceExternalId, 666));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(userExternalId)).thenReturn(java.util.Optional.of(userToBeRemoved));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(removerExternalId)).thenReturn(java.util.Optional.empty());
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> service.remove(userExternalId, removerExternalId, serviceExternalId));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 403 Forbidden"));
        org.mockito.Mockito.verifyNoInteractions(mockServiceRoleDao);
    }

    @org.junit.jupiter.api.Test
    public void remove_shouldThrowForbiddenWebApplicationException_whenRemoverDoesNotBelongToService() {
        java.lang.String serviceExternalId = "service-external-id-1";
        java.lang.String otherServiceExternalId = "service-external-id-2";
        java.lang.String removerExternalId = "user-admin-of-service-1";
        java.lang.String userExternalId = "user-to-be-removed-from-service-1";
        uk.gov.pay.adminusers.persistence.entity.UserEntity userToBeRemoved = createUser(userExternalId, aServiceRole(serviceExternalId, 666));
        uk.gov.pay.adminusers.persistence.entity.UserEntity removerAsAdminOfOtherService = createUser(removerExternalId, aServiceRole(otherServiceExternalId, uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId()));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(userExternalId)).thenReturn(java.util.Optional.of(userToBeRemoved));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(removerExternalId)).thenReturn(java.util.Optional.of(removerAsAdminOfOtherService));
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> service.remove(userExternalId, removerExternalId, serviceExternalId));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 403 Forbidden"));
        org.mockito.Mockito.verifyNoInteractions(mockServiceRoleDao);
    }

    @org.junit.jupiter.api.Test
    public void remove_shouldThrowForbiddenWebApplicationException_whenRemoverHasNotAdminRoleForTheGivenService() {
        java.lang.String serviceExternalId = "service-external-id-1";
        java.lang.String removerExternalId = "user-admin-of-service-1";
        java.lang.String userExternalId = "user-to-be-removed-from-service-1";
        uk.gov.pay.adminusers.persistence.entity.UserEntity userToBeRemoved = createUser(userExternalId, aServiceRole(serviceExternalId, 666));
        uk.gov.pay.adminusers.persistence.entity.UserEntity removerAsNoAdminOfService = createUser(removerExternalId, aServiceRole(serviceExternalId, 999));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(userExternalId)).thenReturn(java.util.Optional.of(userToBeRemoved));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(removerExternalId)).thenReturn(java.util.Optional.of(removerAsNoAdminOfService));
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> service.remove(userExternalId, removerExternalId, serviceExternalId));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 403 Forbidden"));
        org.mockito.Mockito.verifyNoInteractions(mockServiceRoleDao);
    }

    private uk.gov.pay.adminusers.persistence.entity.UserEntity createUser(java.lang.String externalId, uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRole) {
        final uk.gov.pay.adminusers.persistence.entity.UserEntity user = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        user.setExternalId(externalId);
        user.setExternalId(org.apache.commons.lang3.RandomStringUtils.random(10));
        user.addServiceRole(serviceRole);
        return user;
    }

    private uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity aServiceRole(java.lang.String serviceExternalId, int roleId) {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        serviceEntity.setExternalId(serviceExternalId);
        uk.gov.pay.adminusers.persistence.entity.RoleEntity role = new uk.gov.pay.adminusers.persistence.entity.RoleEntity();
        role.setId(roleId);
        return new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(serviceEntity, role);
    }
}
