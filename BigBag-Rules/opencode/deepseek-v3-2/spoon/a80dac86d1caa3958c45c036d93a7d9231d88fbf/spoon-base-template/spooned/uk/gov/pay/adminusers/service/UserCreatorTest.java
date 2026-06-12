package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class UserCreatorTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.UserDao mockUserDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.ServiceDao mockServiceDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.RoleDao mockRoleDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.PasswordHasher mockPasswordHasher;

    private uk.gov.pay.adminusers.service.LinksBuilder linksBuilder = new uk.gov.pay.adminusers.service.LinksBuilder("http://localhost");

    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.UserEntity> expectedUser = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.UserEntity.class);

    private uk.gov.pay.adminusers.service.UserCreator userCreator;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        userCreator = new uk.gov.pay.adminusers.service.UserCreator(mockUserDao, mockRoleDao, mockServiceDao, mockPasswordHasher, linksBuilder);
    }

    @org.junit.jupiter.api.Test
    public void shouldSaveAndReturnUser_forValidUserCreationRequest() {
        java.lang.String validRole = "validRole";
        org.mockito.Mockito.when(mockRoleDao.findByRoleName(validRole)).thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.RoleEntity.class)));
        uk.gov.pay.adminusers.model.CreateUserRequest request = uk.gov.pay.adminusers.model.CreateUserRequest.from("email@example.com", "password", "email@example.com", null, null, "otpKey", "3745838475", null);
        uk.gov.pay.adminusers.model.User user = userCreator.doCreate(request, validRole);
        org.mockito.Mockito.verify(mockUserDao).persist(expectedUser.capture());
        org.hamcrest.MatcherAssert.assertThat(expectedUser.getValue().getEmail(), org.hamcrest.core.Is.is("email@example.com"));
        org.hamcrest.MatcherAssert.assertThat(user.getEmail(), org.hamcrest.core.Is.is("email@example.com"));
        org.hamcrest.MatcherAssert.assertThat(user.getSecondFactor(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS));
        org.hamcrest.MatcherAssert.assertThat(user.getServiceRoles().size(), org.hamcrest.core.Is.is(0));
    }

    @org.junit.jupiter.api.Test
    public void shouldSaveAndReturnUser_forValidUserCreationRequest_withGatewayAccountIds() {
        java.lang.String validRole = "validRole";
        org.mockito.Mockito.when(mockRoleDao.findByRoleName(validRole)).thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.RoleEntity.class)));
        uk.gov.pay.adminusers.model.CreateUserRequest request = uk.gov.pay.adminusers.model.CreateUserRequest.from("email@example.com", "password", "email@example.com", java.util.Arrays.asList("1", "2"), null, "otpKey", "3745838475", null);
        uk.gov.pay.adminusers.model.User user = userCreator.doCreate(request, validRole);
        org.mockito.Mockito.verify(mockUserDao).persist(expectedUser.capture());
        org.hamcrest.MatcherAssert.assertThat(expectedUser.getValue().getEmail(), org.hamcrest.core.Is.is("email@example.com"));
        org.hamcrest.MatcherAssert.assertThat(user.getEmail(), org.hamcrest.core.Is.is("email@example.com"));
        org.hamcrest.MatcherAssert.assertThat(user.getSecondFactor(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS));
        org.hamcrest.MatcherAssert.assertThat(user.getServiceRoles().size(), org.hamcrest.core.Is.is(1));
        org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> serviceEntityArgumentCaptor = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.verify(mockServiceDao).persist(serviceEntityArgumentCaptor.capture());
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = serviceEntityArgumentCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getGatewayAccountIds().get(0).getGatewayAccountId(), org.hamcrest.core.Is.is("1"));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getGatewayAccountIds().get(1).getGatewayAccountId(), org.hamcrest.core.Is.is("2"));
        uk.gov.pay.adminusers.model.Service service = user.getServiceRoles().get(0).getService();
        org.hamcrest.MatcherAssert.assertThat(service.getName(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        org.hamcrest.MatcherAssert.assertThat(service.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH.toString()), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        org.hamcrest.MatcherAssert.assertThat(service.getGatewayAccountIds(), org.hamcrest.core.Is.is(java.util.Arrays.asList("1", "2")));
        org.hamcrest.MatcherAssert.assertThat(service.isRedirectToServiceImmediatelyOnTerminalState(), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(service.isCollectBillingAddress(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(service.getDefaultBillingAddressCountry(), org.hamcrest.core.Is.is("GB"));
    }

    @org.junit.jupiter.api.Test
    public void shouldSaveAndReturnUser_forValidUserCreationRequest_withServiceRoles() {
        java.lang.String validRole = "validRole";
        org.mockito.Mockito.when(mockRoleDao.findByRoleName(validRole)).thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.RoleEntity.class)));
        uk.gov.pay.adminusers.model.CreateUserRequest request = uk.gov.pay.adminusers.model.CreateUserRequest.from("email@example.com", "password", "email@example.com", null, java.util.Arrays.asList("ext-id-1", "ext-id-2"), "otpKey", "3745838475", null);
        org.mockito.Mockito.when(mockServiceDao.findByExternalId("ext-id-1")).thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class)));
        org.mockito.Mockito.when(mockServiceDao.findByExternalId("ext-id-2")).thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class)));
        uk.gov.pay.adminusers.model.User user = userCreator.doCreate(request, validRole);
        org.mockito.Mockito.verify(mockUserDao).persist(expectedUser.capture());
        org.hamcrest.MatcherAssert.assertThat(expectedUser.getValue().getEmail(), org.hamcrest.core.Is.is("email@example.com"));
        org.hamcrest.MatcherAssert.assertThat(user.getEmail(), org.hamcrest.core.Is.is("email@example.com"));
        org.hamcrest.MatcherAssert.assertThat(user.getSecondFactor(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS));
        org.hamcrest.MatcherAssert.assertThat(user.getServiceRoles().size(), org.hamcrest.core.Is.is(2));
    }

    @org.junit.jupiter.api.Test
    public void shouldSaveAndReturnUser_forValidUserCreationRequest_withServiceRoles_evenIfSomeExternalIdsMissing() {
        java.lang.String validRole = "validRole";
        org.mockito.Mockito.when(mockRoleDao.findByRoleName(validRole)).thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.RoleEntity.class)));
        uk.gov.pay.adminusers.model.CreateUserRequest request = uk.gov.pay.adminusers.model.CreateUserRequest.from("email@example.com", "password", "email@example.com", null, java.util.Arrays.asList("ext-id-1", "ext-id-2"), "otpKey", "3745838475", null);
        org.mockito.Mockito.when(mockServiceDao.findByExternalId("ext-id-1")).thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class)));
        org.mockito.Mockito.when(mockServiceDao.findByExternalId("ext-id-2")).thenReturn(java.util.Optional.empty());
        uk.gov.pay.adminusers.model.User user = userCreator.doCreate(request, validRole);
        org.mockito.Mockito.verify(mockUserDao).persist(expectedUser.capture());
        org.hamcrest.MatcherAssert.assertThat(expectedUser.getValue().getEmail(), org.hamcrest.core.Is.is("email@example.com"));
        org.hamcrest.MatcherAssert.assertThat(user.getEmail(), org.hamcrest.core.Is.is("email@example.com"));
        org.hamcrest.MatcherAssert.assertThat(user.getSecondFactor(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS));
        org.hamcrest.MatcherAssert.assertThat(user.getServiceRoles().size(), org.hamcrest.core.Is.is(1));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_ifRoleIsInvalid() {
        java.lang.String validRole = "inValidRole";
        org.mockito.Mockito.when(mockRoleDao.findByRoleName(validRole)).thenReturn(java.util.Optional.empty());
        uk.gov.pay.adminusers.model.CreateUserRequest request = uk.gov.pay.adminusers.model.CreateUserRequest.from("email@example.com", "password", "email@example.com", null, null, "otpKey", "3745838475", null);
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> userCreator.doCreate(request, validRole));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 400 Bad Request"));
    }
}
