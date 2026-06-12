package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class ResetPasswordServiceTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.ForgottenPasswordDao mockForgottenPasswordDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.PasswordHasher mockPasswordHasher;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.UserDao mockUserDao;

    private uk.gov.pay.adminusers.service.ResetPasswordService resetPasswordService;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        mockUserDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.UserDao.class);
        mockForgottenPasswordDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.ForgottenPasswordDao.class);
        mockPasswordHasher = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.PasswordHasher.class);
        resetPasswordService = new uk.gov.pay.adminusers.service.ResetPasswordService(mockUserDao, mockForgottenPasswordDao, mockPasswordHasher);
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnOptionalEmpty_whenForgottenPasswordCode_doesNotExistOrIsExpired() {
        java.lang.String code = "forgottenPasswordCode";
        java.lang.String password = "myNewPassword";
        org.mockito.Mockito.when(mockForgottenPasswordDao.findNonExpiredByCode(code)).thenReturn(java.util.Optional.empty());
        java.util.Optional<java.lang.Integer> userIdOptional = resetPasswordService.updatePassword(code, password);
        org.hamcrest.MatcherAssert.assertThat(userIdOptional.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdatePasswordAsEncrypted_whenCodeIsValid() {
        java.lang.String code = "forgottenPasswordCode";
        java.lang.String plainPassword = "myNewPlainPassword";
        java.lang.String hashedPassword = "hashedPassword";
        int userId = 666;
        org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.UserEntity> argumentCaptor = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.UserEntity.class);
        uk.gov.pay.adminusers.persistence.entity.UserEntity user = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        user.setId(userId);
        user.setLoginCounter(2);
        user.setPassword("whatever");
        uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity forgottenPasswordEntity = new uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity(code, java.time.ZonedDateTime.now(), user);
        org.mockito.Mockito.when(mockForgottenPasswordDao.findNonExpiredByCode(code)).thenReturn(java.util.Optional.of(forgottenPasswordEntity));
        org.mockito.Mockito.when(mockPasswordHasher.hash(plainPassword)).thenReturn(hashedPassword);
        java.util.Optional<java.lang.Integer> userIdOptional = resetPasswordService.updatePassword(code, plainPassword);
        org.hamcrest.MatcherAssert.assertThat(userIdOptional.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(userIdOptional.get(), org.hamcrest.core.Is.is(userId));
        org.mockito.Mockito.verify(mockUserDao).merge(argumentCaptor.capture());
        uk.gov.pay.adminusers.persistence.entity.UserEntity updatedUser = argumentCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(updatedUser.getLoginCounter(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(updatedUser.getPassword(), org.hamcrest.core.Is.is(hashedPassword));
        org.mockito.Mockito.verify(mockForgottenPasswordDao).remove(forgottenPasswordEntity);
    }
}
