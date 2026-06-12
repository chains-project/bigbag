package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class ServiceOtpDispatcherTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.PasswordHasher passwordHasher;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.NotificationService notificationService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.InviteEntity> expectedInvite = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.InviteEntity.class);

    private uk.gov.pay.adminusers.service.InviteOtpDispatcher serviceOtpDispatcher;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        serviceOtpDispatcher = new uk.gov.pay.adminusers.service.ServiceOtpDispatcher(inviteDao, secondFactorAuthenticator, passwordHasher, notificationService);
        serviceOtpDispatcher.withData(uk.gov.pay.adminusers.model.InviteOtpRequest.from(objectMapper.valueToTree(java.util.Map.of())));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenDispatchServiceOtp_ifInviteEntityExist() {
        java.lang.String inviteCode = "valid-invite-code";
        java.lang.String telephone = "+441134960000";
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        inviteEntity.setCode(inviteCode);
        inviteEntity.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        inviteEntity.setOtpKey("otp-key");
        inviteEntity.setTelephoneNumber(telephone);
        org.mockito.Mockito.when(inviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(secondFactorAuthenticator.newPassCode("otp-key")).thenReturn(123456);
        org.mockito.Mockito.when(notificationService.sendSecondFactorPasscodeSms(telephone, "123456", uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SELF_INITIATED_CREATE_NEW_USER_AND_SERVICE)).thenReturn("success code from notify");
        boolean dispatched = serviceOtpDispatcher.dispatchOtp(inviteCode);
        org.hamcrest.MatcherAssert.assertThat(dispatched, org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenDispatchServiceOtp_ifInviteEntityExist_butPhoneAndPasswordOnlyInRequest_andUpdateInviteEntityWithPhoneAndPassword() {
        java.lang.String inviteCode = "valid-invite-code";
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        inviteEntity.setCode(inviteCode);
        inviteEntity.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        inviteEntity.setOtpKey("otp-key");
        java.lang.String telephone = "+447700900000";
        java.lang.String password = "random";// pragma: allowlist secret

        serviceOtpDispatcher.withData(uk.gov.pay.adminusers.model.InviteOtpRequest.from(objectMapper.valueToTree(java.util.Map.of("telephone_number", telephone, "password", "random"))));
        org.mockito.Mockito.when(inviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(passwordHasher.hash(password)).thenReturn("hashed-password");
        org.mockito.Mockito.when(secondFactorAuthenticator.newPassCode("otp-key")).thenReturn(123456);
        org.mockito.Mockito.when(notificationService.sendSecondFactorPasscodeSms(telephone, "123456", uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SELF_INITIATED_CREATE_NEW_USER_AND_SERVICE)).thenReturn("success code from notify");
        boolean dispatched = serviceOtpDispatcher.dispatchOtp(inviteCode);
        org.hamcrest.MatcherAssert.assertThat(dispatched, org.hamcrest.core.Is.is(true));
        org.mockito.Mockito.verify(inviteDao).merge(expectedInvite.capture());
        org.hamcrest.MatcherAssert.assertThat(dispatched, org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(expectedInvite.getValue().getTelephoneNumber(), org.hamcrest.core.Is.is(telephone));
        org.hamcrest.MatcherAssert.assertThat(expectedInvite.getValue().getPassword(), org.hamcrest.core.Is.is("hashed-password"));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenDispatchServiceOtp_ifInviteEntityExistWithPassword_butPhoneOnlyInRequest_andUpdateInviteEntityWithPhone() {
        java.lang.String inviteCode = "valid-invite-code";
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        inviteEntity.setCode(inviteCode);
        inviteEntity.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        inviteEntity.setOtpKey("otp-key");
        inviteEntity.setPassword("hashed-password");
        java.lang.String telephone = "+447700900000";
        serviceOtpDispatcher.withData(uk.gov.pay.adminusers.model.InviteOtpRequest.from(objectMapper.valueToTree(java.util.Map.of("telephone_number", telephone))));
        org.mockito.Mockito.when(inviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(secondFactorAuthenticator.newPassCode("otp-key")).thenReturn(123456);
        org.mockito.Mockito.when(notificationService.sendSecondFactorPasscodeSms(telephone, "123456", uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SELF_INITIATED_CREATE_NEW_USER_AND_SERVICE)).thenReturn("success code from notify");
        boolean dispatched = serviceOtpDispatcher.dispatchOtp(inviteCode);
        org.hamcrest.MatcherAssert.assertThat(dispatched, org.hamcrest.core.Is.is(true));
        org.mockito.Mockito.verify(inviteDao).merge(expectedInvite.capture());
        org.hamcrest.MatcherAssert.assertThat(dispatched, org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(expectedInvite.getValue().getTelephoneNumber(), org.hamcrest.core.Is.is(telephone));
        org.hamcrest.MatcherAssert.assertThat(expectedInvite.getValue().getPassword(), org.hamcrest.core.Is.is("hashed-password"));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_whenDispatchServiceOtp_ifInviteEntityNotFound() {
        java.lang.String inviteCode = "non-existent-code";
        org.mockito.Mockito.when(inviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.empty());
        boolean dispatched = serviceOtpDispatcher.dispatchOtp(inviteCode);
        org.hamcrest.MatcherAssert.assertThat(dispatched, org.hamcrest.core.Is.is(false));
    }
}
