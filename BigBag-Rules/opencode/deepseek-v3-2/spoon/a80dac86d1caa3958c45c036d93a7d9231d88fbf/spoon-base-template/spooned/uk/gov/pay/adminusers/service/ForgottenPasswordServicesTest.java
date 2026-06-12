package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class ForgottenPasswordServicesTest {
    private static final java.lang.String SELFSERVICE_URL = "http://selfservice";

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.ForgottenPasswordDao forgottenPasswordDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.app.config.AdminUsersConfig mockConfig;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.NotificationService mockNotificationService;

    private uk.gov.pay.adminusers.service.ForgottenPasswordServices forgottenPasswordServices;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        uk.gov.pay.adminusers.app.config.LinksConfig mockLinks = org.mockito.Mockito.mock(uk.gov.pay.adminusers.app.config.LinksConfig.class);
        org.mockito.Mockito.when(mockLinks.getSelfserviceUrl()).thenReturn(uk.gov.pay.adminusers.service.ForgottenPasswordServicesTest.SELFSERVICE_URL);
        org.mockito.Mockito.when(mockConfig.getLinks()).thenReturn(mockLinks);
        forgottenPasswordServices = new uk.gov.pay.adminusers.service.ForgottenPasswordServices(userDao, forgottenPasswordDao, new uk.gov.pay.adminusers.service.LinksBuilder("http://localhost"), mockNotificationService, mockConfig);
    }

    @org.junit.jupiter.api.Test
    public void shouldSendAForgottenPasswordNotification_whenCreating_ifUserFound() {
        org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity> expectedForgottenPassword = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity.class);
        java.lang.String username = "existing-user";
        java.lang.String email = "existing-user@example.com";
        uk.gov.pay.adminusers.persistence.entity.UserEntity mockUser = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class);
        org.mockito.Mockito.when(mockUser.getEmail()).thenReturn(email);
        org.mockito.Mockito.when(userDao.findByUsername(username)).thenReturn(java.util.Optional.of(mockUser));
        org.mockito.Mockito.when(mockNotificationService.sendForgottenPasswordEmail(org.mockito.Mockito.eq(email), org.mockito.Mockito.matches("^http://selfservice/reset-password/[0-9a-z]{32}$"))).thenReturn("random-notify-id");
        org.mockito.Mockito.doNothing().when(forgottenPasswordDao).persist(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity.class));
        forgottenPasswordServices.create(username);
        org.mockito.Mockito.verify(forgottenPasswordDao).persist(expectedForgottenPassword.capture());
        uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity savedForgottenPassword = expectedForgottenPassword.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedForgottenPassword.getUser(), org.hamcrest.core.Is.is(mockUser));
        org.hamcrest.MatcherAssert.assertThat(savedForgottenPassword.getCode(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
    }

    @org.junit.jupiter.api.Test
    public void shouldStillCreateAForgottenPassword_whenNotificationFails_onCreate_ifUserFound() {
        org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity> expectedForgottenPassword = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity.class);
        java.lang.String username = "existing-user";
        java.lang.String email = "existing-user@example.com";
        uk.gov.pay.adminusers.persistence.entity.UserEntity mockUser = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class);
        org.mockito.Mockito.when(mockUser.getEmail()).thenReturn(email);
        org.mockito.Mockito.when(userDao.findByUsername(username)).thenReturn(java.util.Optional.of(mockUser));
        org.mockito.Mockito.when(mockNotificationService.sendForgottenPasswordEmail(org.mockito.Mockito.eq(email), org.mockito.Mockito.matches("^http://selfservice/reset-password/[0-9a-z]{32}$"))).thenThrow(uk.gov.pay.adminusers.service.AdminUsersExceptions.userNotificationError(new java.lang.Exception("Cause")));
        org.mockito.Mockito.doNothing().when(forgottenPasswordDao).persist(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity.class));
        forgottenPasswordServices.create(username);
        org.mockito.Mockito.verify(forgottenPasswordDao).persist(expectedForgottenPassword.capture());
        uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity savedForgottenPassword = expectedForgottenPassword.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedForgottenPassword.getUser(), org.hamcrest.core.Is.is(mockUser));
        org.hamcrest.MatcherAssert.assertThat(savedForgottenPassword.getCode(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnEmpty_whenCreating_ifUserNotFound() {
        java.lang.String nonExistentUser = "non-existent-user";
        org.mockito.Mockito.when(userDao.findByUsername(nonExistentUser)).thenReturn(java.util.Optional.empty());
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> forgottenPasswordServices.create(nonExistentUser));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 404 Not Found"));
    }

    @org.junit.jupiter.api.Test
    public void shouldFindForgottenPassword_whenFindByCode_ifFound() {
        java.lang.String existingCode = "existing-code";
        uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity forgottenPasswordEntity = mockForgottenPassword(existingCode);
        org.mockito.Mockito.when(forgottenPasswordDao.findNonExpiredByCode(existingCode)).thenReturn(java.util.Optional.of(forgottenPasswordEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.ForgottenPassword> forgottenPasswordOptional = forgottenPasswordServices.findNonExpired(existingCode);
        junit.framework.TestCase.assertTrue(forgottenPasswordOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(forgottenPasswordOptional.get().getCode(), org.hamcrest.core.Is.is(existingCode));
        org.hamcrest.MatcherAssert.assertThat(forgottenPasswordOptional.get().getLinks(), org.hamcrest.collection.IsCollectionWithSize.hasSize(1));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnEmpty_whenFindByCode_ifNotFound() {
        java.lang.String nonExistentCode = "non-existent-code";
        org.mockito.Mockito.when(forgottenPasswordDao.findNonExpiredByCode(nonExistentCode)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.ForgottenPassword> forgottenPasswordOptional = forgottenPasswordServices.findNonExpired(nonExistentCode);
        junit.framework.TestCase.assertFalse(forgottenPasswordOptional.isPresent());
    }

    private uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity mockForgottenPassword(java.lang.String code) {
        uk.gov.pay.adminusers.persistence.entity.UserEntity mockUser = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class);
        return new uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity(code, java.time.ZonedDateTime.now(), mockUser);
    }
}
