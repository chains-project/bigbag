package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class ExistingUserOtpDispatcherTest {
    private static final java.lang.String USER_EXTERNAL_ID = "7d19aff33f8948deb97ed16b2912dcd3";

    private static final java.lang.String USER_USERNAME = "random-name";

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.NotificationService notificationService;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator;

    private uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher existingUserOtpDispatcher;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        existingUserOtpDispatcher = new uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher(() -> notificationService, secondFactorAuthenticator, userDao);
    }

    @org.junit.jupiter.api.Test
    public void shouldSendSignInOtpIfUserFound() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        org.mockito.Mockito.when(userDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(secondFactorAuthenticator.newPassCode(user.getOtpKey())).thenReturn(123456);
        org.mockito.Mockito.when(notificationService.sendSecondFactorPasscodeSms(org.mockito.ArgumentMatchers.any(java.lang.String.class), org.mockito.ArgumentMatchers.eq("123456"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SIGN_IN))).thenReturn("random-notify-id");
        java.util.Optional<uk.gov.pay.adminusers.model.SecondFactorToken> tokenOptional = existingUserOtpDispatcher.sendSignInOtp(user.getExternalId());
        org.junit.jupiter.api.Assertions.assertTrue(tokenOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(tokenOptional.get().getPasscode(), org.hamcrest.core.Is.is("123456"));
    }

    @org.junit.jupiter.api.Test
    public void shouldPadSignInOtpToSixDigits() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        org.mockito.Mockito.when(userDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(secondFactorAuthenticator.newPassCode(user.getOtpKey())).thenReturn(12345);
        org.mockito.Mockito.when(notificationService.sendSecondFactorPasscodeSms(org.mockito.ArgumentMatchers.any(java.lang.String.class), org.mockito.ArgumentMatchers.eq("012345"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SIGN_IN))).thenReturn("random-notify-id");
        java.util.Optional<uk.gov.pay.adminusers.model.SecondFactorToken> tokenOptional = existingUserOtpDispatcher.sendSignInOtp(user.getExternalId());
        org.junit.jupiter.api.Assertions.assertTrue(tokenOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(tokenOptional.get().getPasscode(), org.hamcrest.core.Is.is("012345"));
    }

    @org.junit.jupiter.api.Test
    public void shouldGracefullyHandleNotifyErrorSendingSignInOtp() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        org.mockito.Mockito.when(userDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(secondFactorAuthenticator.newPassCode(user.getOtpKey())).thenReturn(654321);
        org.mockito.Mockito.when(notificationService.sendSecondFactorPasscodeSms(org.mockito.ArgumentMatchers.any(java.lang.String.class), org.mockito.ArgumentMatchers.eq("654321"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SIGN_IN))).thenThrow(uk.gov.pay.adminusers.service.AdminUsersExceptions.userNotificationError(new java.lang.Exception("Cause")));
        java.util.Optional<uk.gov.pay.adminusers.model.SecondFactorToken> tokenOptional = existingUserOtpDispatcher.sendSignInOtp(user.getExternalId());
        org.junit.jupiter.api.Assertions.assertTrue(tokenOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(tokenOptional.get().getPasscode(), org.hamcrest.core.Is.is("654321"));
    }

    @org.junit.jupiter.api.Test
    public void shouldNotSendSignInOtpIfUserDoesNotExist() {
        java.lang.String nonExistentExternalId = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        org.mockito.Mockito.when(userDao.findByExternalId(nonExistentExternalId)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.SecondFactorToken> tokenOptional = existingUserOtpDispatcher.sendSignInOtp(nonExistentExternalId);
        org.junit.jupiter.api.Assertions.assertFalse(tokenOptional.isPresent());
    }

    @org.junit.jupiter.api.Test
    public void shouldSendChangeSignInMethodOtpIfUserFound() {
        uk.gov.pay.adminusers.model.User user = aUserWithProvisionalOtpKey();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        org.mockito.Mockito.when(userDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(secondFactorAuthenticator.newPassCode(user.getProvisionalOtpKey())).thenReturn(654321);
        org.mockito.Mockito.when(notificationService.sendSecondFactorPasscodeSms(org.mockito.ArgumentMatchers.any(java.lang.String.class), org.mockito.ArgumentMatchers.eq("654321"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.CHANGE_SIGN_IN_2FA_TO_SMS))).thenReturn("random-notify-id");
        java.util.Optional<uk.gov.pay.adminusers.model.SecondFactorToken> tokenOptional = existingUserOtpDispatcher.sendChangeSignMethodToSmsOtp(user.getExternalId());
        org.junit.jupiter.api.Assertions.assertTrue(tokenOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(tokenOptional.get().getPasscode(), org.hamcrest.core.Is.is("654321"));
        org.mockito.Mockito.verify(notificationService, org.mockito.Mockito.never()).sendSecondFactorPasscodeSms(org.mockito.ArgumentMatchers.any(java.lang.String.class), org.mockito.ArgumentMatchers.eq(user.getOtpKey()), org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.class));
    }

    @org.junit.jupiter.api.Test
    public void shouldPadChangeSignInMethodOtpToSixDigits() {
        uk.gov.pay.adminusers.model.User user = aUserWithProvisionalOtpKey();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        org.mockito.Mockito.when(userDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(secondFactorAuthenticator.newPassCode(user.getProvisionalOtpKey())).thenReturn(12345);
        org.mockito.Mockito.when(notificationService.sendSecondFactorPasscodeSms(org.mockito.ArgumentMatchers.any(java.lang.String.class), org.mockito.ArgumentMatchers.eq("012345"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.CHANGE_SIGN_IN_2FA_TO_SMS))).thenReturn("random-notify-id");
        java.util.Optional<uk.gov.pay.adminusers.model.SecondFactorToken> tokenOptional = existingUserOtpDispatcher.sendChangeSignMethodToSmsOtp(user.getExternalId());
        org.junit.jupiter.api.Assertions.assertTrue(tokenOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(tokenOptional.get().getPasscode(), org.hamcrest.core.Is.is("012345"));
    }

    @org.junit.jupiter.api.Test
    public void shouldNotSendChangeSignInOtpIfProvisionalOtpKeyNotSet() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        org.mockito.Mockito.when(userDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.SecondFactorToken> tokenOptional = existingUserOtpDispatcher.sendChangeSignMethodToSmsOtp(user.getExternalId());
        org.junit.jupiter.api.Assertions.assertFalse(tokenOptional.isPresent());
        org.mockito.Mockito.verifyNoInteractions(secondFactorAuthenticator);
    }

    @org.junit.jupiter.api.Test
    public void shouldGracefullyHandleNotifyErrorSendingChangeSignInOtp() {
        uk.gov.pay.adminusers.model.User user = aUserWithProvisionalOtpKey();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        org.mockito.Mockito.when(userDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(secondFactorAuthenticator.newPassCode(user.getProvisionalOtpKey())).thenReturn(654321);
        org.mockito.Mockito.when(notificationService.sendSecondFactorPasscodeSms(org.mockito.ArgumentMatchers.any(java.lang.String.class), org.mockito.ArgumentMatchers.eq("654321"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.CHANGE_SIGN_IN_2FA_TO_SMS))).thenThrow(uk.gov.pay.adminusers.service.AdminUsersExceptions.userNotificationError(new java.lang.Exception("Cause")));
        java.util.Optional<uk.gov.pay.adminusers.model.SecondFactorToken> tokenOptional = existingUserOtpDispatcher.sendChangeSignMethodToSmsOtp(user.getExternalId());
        org.junit.jupiter.api.Assertions.assertTrue(tokenOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(tokenOptional.get().getPasscode(), org.hamcrest.core.Is.is("654321"));
    }

    @org.junit.jupiter.api.Test
    public void shouldNotSendChangeSignInOtpIfUserDoesNotExist() {
        java.lang.String nonExistentExternalId = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        org.mockito.Mockito.when(userDao.findByExternalId(nonExistentExternalId)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.SecondFactorToken> tokenOptional = existingUserOtpDispatcher.sendChangeSignMethodToSmsOtp(nonExistentExternalId);
        org.junit.jupiter.api.Assertions.assertFalse(tokenOptional.isPresent());
    }

    private uk.gov.pay.adminusers.model.User aUser() {
        return uk.gov.pay.adminusers.model.User.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), uk.gov.pay.adminusers.service.ExistingUserOtpDispatcherTest.USER_EXTERNAL_ID, uk.gov.pay.adminusers.service.ExistingUserOtpDispatcherTest.USER_USERNAME, "random-password", "user@test.test", "784rh", "07700900000", java.util.Collections.emptyList(), null, uk.gov.pay.adminusers.model.SecondFactorMethod.SMS, null, null, null);
    }

    private uk.gov.pay.adminusers.model.User aUserWithProvisionalOtpKey() {
        return uk.gov.pay.adminusers.model.User.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), uk.gov.pay.adminusers.service.ExistingUserOtpDispatcherTest.USER_EXTERNAL_ID, uk.gov.pay.adminusers.service.ExistingUserOtpDispatcherTest.USER_USERNAME, "random-password", "user@test.test", "784rh", "07700900001", java.util.Collections.emptyList(), null, uk.gov.pay.adminusers.model.SecondFactorMethod.APP, "provisional OTP key", java.time.ZonedDateTime.now(uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.UTC), null);
    }
}
