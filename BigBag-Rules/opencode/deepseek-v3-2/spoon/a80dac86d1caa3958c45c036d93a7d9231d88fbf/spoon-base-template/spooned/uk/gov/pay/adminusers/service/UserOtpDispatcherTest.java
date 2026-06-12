package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class UserOtpDispatcherTest {
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.PasswordHasher passwordHasher;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.NotificationService notificationService;

    private final org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.InviteEntity> expectedInvite = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.InviteEntity.class);

    private uk.gov.pay.adminusers.service.InviteOtpDispatcher userOtpDispatcher;

    @org.junit.jupiter.api.BeforeEach
    void before() {
        userOtpDispatcher = new uk.gov.pay.adminusers.service.UserOtpDispatcher(inviteDao, secondFactorAuthenticator, passwordHasher, notificationService);
    }

    @org.junit.jupiter.api.Test
    void shouldSuccess_whenDispatchUserOtp_ifInviteEntityExist() {
        java.lang.String inviteCode = "valid-invite-code";
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        inviteEntity.setCode(inviteCode);
        inviteEntity.setType(uk.gov.pay.adminusers.model.InviteType.USER);
        inviteEntity.setOtpKey("otp-key");
        java.lang.String telephone = "+441134960000";
        java.lang.String password = "random";// pragma: allowlist secret

        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.service.UserOtpDispatcherTest.objectMapper.valueToTree(java.util.Map.of("telephone_number", telephone, "password", password));
        userOtpDispatcher = userOtpDispatcher.withData(uk.gov.pay.adminusers.model.InviteOtpRequest.from(payload));
        org.mockito.Mockito.when(inviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(passwordHasher.hash(password)).thenReturn("hashed-password");
        org.mockito.Mockito.when(secondFactorAuthenticator.newPassCode("otp-key")).thenReturn(123456);
        org.mockito.Mockito.when(notificationService.sendSecondFactorPasscodeSms(telephone, "123456", uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.CREATE_USER_IN_RESPONSE_TO_INVITATION_TO_SERVICE)).thenReturn("success code from notify");
        boolean dispatched = userOtpDispatcher.dispatchOtp(inviteCode);
        org.mockito.Mockito.verify(inviteDao).merge(expectedInvite.capture());
        org.hamcrest.MatcherAssert.assertThat(dispatched, org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(expectedInvite.getValue().getTelephoneNumber(), org.hamcrest.core.Is.is(telephone));
        org.hamcrest.MatcherAssert.assertThat(expectedInvite.getValue().getPassword(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
    }

    @org.junit.jupiter.api.Test
    void shouldFail_whenDispatchServiceOtp_ifInviteEntityNotFound() {
        java.lang.String inviteCode = "non-existent-code";
        org.mockito.Mockito.when(inviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.empty());
        boolean dispatched = userOtpDispatcher.dispatchOtp(inviteCode);
        org.hamcrest.MatcherAssert.assertThat(dispatched, org.hamcrest.core.Is.is(false));
    }
}
