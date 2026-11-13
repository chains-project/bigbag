package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class InviteServiceTest {
    private static final java.lang.String TELEPHONE_NUMBER = "+441134960000";

    private static final java.lang.String PLAIN_PASSWORD = "my-secure-pass";

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.UserDao mockUserDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.InviteDao mockInviteDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.NotificationService mockNotificationService;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.SecondFactorAuthenticator mockSecondFactorAuthenticator;

    private uk.gov.pay.adminusers.service.InviteService inviteService;

    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.InviteEntity> expectedInvite = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.InviteEntity.class);

    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.UserEntity> expectedInvitedUser = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.UserEntity.class);

    private int passCode = 123456;

    private java.lang.String otpKey = "otpKey";

    private java.lang.String inviteCode = "code";

    private java.lang.String senderEmail = "sender@example.com";

    private java.lang.String email = "invited@example.com";

    private int serviceId = 1;

    private java.lang.String senderExternalId = "12345";

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        inviteService = new uk.gov.pay.adminusers.service.InviteService(mockUserDao, mockInviteDao, mockNotificationService, mockSecondFactorAuthenticator, new uk.gov.pay.adminusers.service.LinksBuilder("http://localhost"), 3);
    }

    private uk.gov.pay.adminusers.persistence.entity.InviteEntity mocksCreateInvite() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        uk.gov.pay.adminusers.persistence.entity.UserEntity senderUser = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        senderUser.setExternalId(senderExternalId);
        senderUser.setEmail(senderEmail);
        uk.gov.pay.adminusers.persistence.entity.RoleEntity role = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(uk.gov.pay.adminusers.model.Role.role(uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId(), "admin", "Admin Role"));
        senderUser.addServiceRole(new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(service, role));
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = anInvite(email, inviteCode, otpKey, role);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        return anInvite;
    }

    @org.junit.jupiter.api.Test
    public void validateOtpAndCreateUser_shouldCreateInvitedUserOnSuccessfulInvite() {
        mocksCreateInvite();
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.authorize(otpKey, passCode)).thenReturn(true);
        uk.gov.pay.adminusers.model.InviteValidateOtpRequest inviteValidateOtpRequest = inviteValidateOtpRequest(inviteCode, passCode);
        inviteService.validateOtpAndCreateUser(inviteValidateOtpRequest);
        org.mockito.Mockito.verify(mockUserDao).persist(expectedInvitedUser.capture());
        uk.gov.pay.adminusers.persistence.entity.UserEntity createdUser = expectedInvitedUser.getValue();
        org.hamcrest.MatcherAssert.assertThat(createdUser.getEmail(), org.hamcrest.core.Is.is(email));
        org.hamcrest.MatcherAssert.assertThat(createdUser.isDisabled(), org.hamcrest.core.Is.is(java.lang.Boolean.FALSE));
    }

    @org.junit.jupiter.api.Test
    public void validateOtpAndCreateUser_shouldDisableInviteOnSuccessfulInvite() {
        mocksCreateInvite();
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.authorize(otpKey, passCode)).thenReturn(true);
        uk.gov.pay.adminusers.model.InviteValidateOtpRequest inviteValidateOtpRequest = inviteValidateOtpRequest(inviteCode, passCode);
        inviteService.validateOtpAndCreateUser(inviteValidateOtpRequest);
        org.mockito.Mockito.verify(mockInviteDao).merge(expectedInvite.capture());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity savedInvite = expectedInvite.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedInvite.getCode(), org.hamcrest.core.Is.is(inviteCode));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.isDisabled(), org.hamcrest.core.Is.is(java.lang.Boolean.TRUE));
    }

    @org.junit.jupiter.api.Test
    public void validateOtpAndCreateUser_shouldErrorWhenDisabled_evenIfOtpValidationIsSuccessful() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = mocksCreateInvite();
        anInvite.setDisabled(java.lang.Boolean.TRUE);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        uk.gov.pay.adminusers.model.InviteValidateOtpRequest inviteValidateOtpRequest = inviteValidateOtpRequest(inviteCode, passCode);
        uk.gov.pay.adminusers.service.ValidateOtpAndCreateUserResult validateOtpAndCreateUserResult = inviteService.validateOtpAndCreateUser(inviteValidateOtpRequest);
        org.hamcrest.MatcherAssert.assertThat(validateOtpAndCreateUserResult.isError(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(validateOtpAndCreateUserResult.getError().getResponse().getStatus(), org.hamcrest.core.Is.is(javax.ws.rs.core.Response.Status.GONE.getStatusCode()));
    }

    @org.junit.jupiter.api.Test
    public void validateOtpAndCreateUser_shouldErrorAndDisableWhenInvalidOtpValidation_ifMaxRetryExceeded() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = mocksCreateInvite();
        anInvite.setLoginCounter(2);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        int invalidPassCode = 1337;
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.authorize(otpKey, invalidPassCode)).thenReturn(false);
        uk.gov.pay.adminusers.model.InviteValidateOtpRequest inviteValidateOtpRequest = inviteValidateOtpRequest(inviteCode, invalidPassCode);
        uk.gov.pay.adminusers.service.ValidateOtpAndCreateUserResult validateOtpAndCreateUserResult = inviteService.validateOtpAndCreateUser(inviteValidateOtpRequest);
        org.hamcrest.MatcherAssert.assertThat(validateOtpAndCreateUserResult.isError(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(validateOtpAndCreateUserResult.getError().getResponse().getStatus(), org.hamcrest.core.Is.is(javax.ws.rs.core.Response.Status.GONE.getStatusCode()));
        org.mockito.Mockito.verify(mockInviteDao).merge(expectedInvite.capture());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity savedInvite = expectedInvite.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedInvite.getLoginCounter(), org.hamcrest.core.Is.is(3));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.isDisabled(), org.hamcrest.core.Is.is(java.lang.Boolean.TRUE));
    }

    @org.junit.jupiter.api.Test
    public void validateOtpAndCreateUser_shouldErrorAndIncrementLoginCounterWhenInvalidOtpValidation() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = mocksCreateInvite();
        anInvite.setLoginCounter(1);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        int invalidPassCode = 1337;
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.authorize(otpKey, invalidPassCode)).thenReturn(false);
        uk.gov.pay.adminusers.model.InviteValidateOtpRequest inviteValidateOtpRequest = inviteValidateOtpRequest(inviteCode, invalidPassCode);
        uk.gov.pay.adminusers.service.ValidateOtpAndCreateUserResult validateOtpAndCreateUserResult = inviteService.validateOtpAndCreateUser(inviteValidateOtpRequest);
        org.hamcrest.MatcherAssert.assertThat(validateOtpAndCreateUserResult.isError(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(validateOtpAndCreateUserResult.getError().getResponse().getStatus(), org.hamcrest.core.Is.is(javax.ws.rs.core.Response.Status.UNAUTHORIZED.getStatusCode()));
        org.mockito.Mockito.verify(mockInviteDao).merge(expectedInvite.capture());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity savedInvite = expectedInvite.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedInvite.getLoginCounter(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.isDisabled(), org.hamcrest.core.Is.is(java.lang.Boolean.FALSE));
    }

    @org.junit.jupiter.api.Test
    public void validateOtpAndCreateUser_shouldCreateInvitedUserAndResetLoginCounterAndDisableInviteWhenValidOtpValidation() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = mocksCreateInvite();
        anInvite.setLoginCounter(2);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.authorize(otpKey, passCode)).thenReturn(true);
        uk.gov.pay.adminusers.model.InviteValidateOtpRequest inviteValidateOtpRequest = inviteValidateOtpRequest(inviteCode, passCode);
        inviteService.validateOtpAndCreateUser(inviteValidateOtpRequest);
        org.mockito.Mockito.verify(mockUserDao).persist(expectedInvitedUser.capture());
        uk.gov.pay.adminusers.persistence.entity.UserEntity createdUser = expectedInvitedUser.getValue();
        org.hamcrest.MatcherAssert.assertThat(createdUser.getEmail(), org.hamcrest.core.Is.is(email));
        org.hamcrest.MatcherAssert.assertThat(createdUser.isDisabled(), org.hamcrest.core.Is.is(java.lang.Boolean.FALSE));
        org.mockito.Mockito.verify(mockInviteDao).merge(expectedInvite.capture());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity savedInvite = expectedInvite.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedInvite.getLoginCounter(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.isDisabled(), org.hamcrest.core.Is.is(java.lang.Boolean.TRUE));
    }

    @org.junit.jupiter.api.Test
    public void validateOtpAndCreateUser_shouldErrorWhenInviteNotFound() {
        java.lang.String notFoundInviteCode = "not-found-invite-code";
        org.mockito.Mockito.when(mockInviteDao.findByCode(notFoundInviteCode)).thenReturn(java.util.Optional.empty());
        uk.gov.pay.adminusers.model.InviteValidateOtpRequest inviteValidateOtpRequest = inviteValidateOtpRequest(notFoundInviteCode, passCode);
        uk.gov.pay.adminusers.service.ValidateOtpAndCreateUserResult validateOtpAndCreateUserResult = inviteService.validateOtpAndCreateUser(inviteValidateOtpRequest);
        org.hamcrest.MatcherAssert.assertThat(validateOtpAndCreateUserResult.isError(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(validateOtpAndCreateUserResult.getError().getResponse().getStatus(), org.hamcrest.core.Is.is(javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode()));
    }

    @org.junit.jupiter.api.Test
    public void generateOtp_shouldSendNotificationOnSuccessfulServiceInviteUpdate() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        inviteEntity.setOtpKey(otpKey);
        inviteEntity.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.newPassCode(otpKey)).thenReturn(passCode);
        org.mockito.Mockito.when(mockInviteDao.merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.InviteEntity.class))).thenReturn(inviteEntity);
        org.mockito.Mockito.when(mockNotificationService.sendSecondFactorPasscodeSms(org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.InviteServiceTest.TELEPHONE_NUMBER), org.mockito.ArgumentMatchers.eq(java.lang.String.valueOf(passCode)), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SELF_INITIATED_CREATE_NEW_USER_AND_SERVICE))).thenReturn("random-notify-id");
        inviteService.reGenerateOtp(inviteOtpRequestFrom(inviteCode, uk.gov.pay.adminusers.service.InviteServiceTest.TELEPHONE_NUMBER, uk.gov.pay.adminusers.service.InviteServiceTest.PLAIN_PASSWORD));
        org.mockito.Mockito.verify(mockInviteDao).merge(expectedInvite.capture());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity updatedInvite = expectedInvite.getValue();
        org.hamcrest.MatcherAssert.assertThat(updatedInvite.getTelephoneNumber(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.InviteServiceTest.TELEPHONE_NUMBER));
    }

    @org.junit.jupiter.api.Test
    public void generateOtp_shouldStillUpdateTheServiceInviteWhen2FAFails() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        inviteEntity.setOtpKey(otpKey);
        inviteEntity.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.newPassCode(otpKey)).thenReturn(passCode);
        org.mockito.Mockito.when(mockInviteDao.merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.InviteEntity.class))).thenReturn(inviteEntity);
        org.mockito.Mockito.when(mockNotificationService.sendSecondFactorPasscodeSms(org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.InviteServiceTest.TELEPHONE_NUMBER), org.mockito.ArgumentMatchers.eq(java.lang.String.valueOf(passCode)), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SELF_INITIATED_CREATE_NEW_USER_AND_SERVICE))).thenThrow(uk.gov.pay.adminusers.service.AdminUsersExceptions.userNotificationError(new java.lang.Exception("Cause")));
        inviteService.reGenerateOtp(inviteOtpRequestFrom(inviteCode, uk.gov.pay.adminusers.service.InviteServiceTest.TELEPHONE_NUMBER, uk.gov.pay.adminusers.service.InviteServiceTest.PLAIN_PASSWORD));
        org.mockito.Mockito.verify(mockInviteDao).merge(expectedInvite.capture());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity updatedInvite = expectedInvite.getValue();
        org.hamcrest.MatcherAssert.assertThat(updatedInvite.getTelephoneNumber(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.InviteServiceTest.TELEPHONE_NUMBER));
    }

    @org.junit.jupiter.api.Test
    public void generateOtp_shouldSendNotificationOnSuccessfulUserInviteUpdate() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        inviteEntity.setOtpKey(otpKey);
        inviteEntity.setType(uk.gov.pay.adminusers.model.InviteType.USER);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.newPassCode(otpKey)).thenReturn(passCode);
        org.mockito.Mockito.when(mockInviteDao.merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.InviteEntity.class))).thenReturn(inviteEntity);
        org.mockito.Mockito.when(mockNotificationService.sendSecondFactorPasscodeSms(org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.InviteServiceTest.TELEPHONE_NUMBER), org.mockito.ArgumentMatchers.eq(java.lang.String.valueOf(passCode)), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.CREATE_USER_IN_RESPONSE_TO_INVITATION_TO_SERVICE))).thenReturn("random-notify-id");
        inviteService.reGenerateOtp(inviteOtpRequestFrom(inviteCode, uk.gov.pay.adminusers.service.InviteServiceTest.TELEPHONE_NUMBER, uk.gov.pay.adminusers.service.InviteServiceTest.PLAIN_PASSWORD));
        org.mockito.Mockito.verify(mockInviteDao).merge(expectedInvite.capture());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity updatedInvite = expectedInvite.getValue();
        org.hamcrest.MatcherAssert.assertThat(updatedInvite.getTelephoneNumber(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.InviteServiceTest.TELEPHONE_NUMBER));
    }

    @org.junit.jupiter.api.Test
    public void generateOtp_shouldStillUpdateTheUserInviteWhen2FAFails() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        inviteEntity.setOtpKey(otpKey);
        inviteEntity.setType(uk.gov.pay.adminusers.model.InviteType.USER);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.newPassCode(otpKey)).thenReturn(passCode);
        org.mockito.Mockito.when(mockInviteDao.merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.InviteEntity.class))).thenReturn(inviteEntity);
        org.mockito.Mockito.when(mockNotificationService.sendSecondFactorPasscodeSms(org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.InviteServiceTest.TELEPHONE_NUMBER), org.mockito.ArgumentMatchers.eq(java.lang.String.valueOf(passCode)), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.CREATE_USER_IN_RESPONSE_TO_INVITATION_TO_SERVICE))).thenThrow(uk.gov.pay.adminusers.service.AdminUsersExceptions.userNotificationError(new java.lang.Exception("Cause")));
        inviteService.reGenerateOtp(inviteOtpRequestFrom(inviteCode, uk.gov.pay.adminusers.service.InviteServiceTest.TELEPHONE_NUMBER, uk.gov.pay.adminusers.service.InviteServiceTest.PLAIN_PASSWORD));
        org.mockito.Mockito.verify(mockInviteDao).merge(expectedInvite.capture());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity updatedInvite = expectedInvite.getValue();
        org.hamcrest.MatcherAssert.assertThat(updatedInvite.getTelephoneNumber(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.InviteServiceTest.TELEPHONE_NUMBER));
    }

    @org.junit.jupiter.api.Test
    public void validateOtp_shouldReturnTrueOnValidInviteAndValidOtp() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        inviteEntity.setOtpKey(otpKey);
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.authorize(otpKey, passCode)).thenReturn(true);
        java.util.Optional<javax.ws.rs.WebApplicationException> validationResult = inviteService.validateOtp(inviteEntity, passCode);
        org.hamcrest.MatcherAssert.assertThat(validationResult.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void validateOtp_shouldReturnFalseOnValidInviteAndInValidOtp() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        inviteEntity.setOtpKey(otpKey);
        java.util.Optional<javax.ws.rs.WebApplicationException> validationResult = inviteService.validateOtp(inviteEntity, passCode);
        org.hamcrest.MatcherAssert.assertThat(validationResult.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(validationResult.get().getResponse().getStatus(), org.hamcrest.core.Is.is(401));
    }

    @org.junit.jupiter.api.Test
    public void validateOtp_shouldReturnFalseOnValidInviteAndValidOtpAndEntityDisabled() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        inviteEntity.setOtpKey(otpKey);
        inviteEntity.setDisabled(true);
        java.util.Optional<javax.ws.rs.WebApplicationException> validationResult = inviteService.validateOtp(inviteEntity, passCode);
        org.hamcrest.MatcherAssert.assertThat(validationResult.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(validationResult.get().getResponse().getStatus(), org.hamcrest.core.Is.is(410));
    }

    private uk.gov.pay.adminusers.model.InviteOtpRequest inviteOtpRequestFrom(java.lang.String code, java.lang.String telephoneNumber, java.lang.String password) {
        com.fasterxml.jackson.databind.node.ObjectNode json = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        json.put(uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_CODE, code);
        json.put(uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_TELEPHONE_NUMBER, telephoneNumber);
        json.put(uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_PASSWORD, password);
        return uk.gov.pay.adminusers.model.InviteOtpRequest.from(json);
    }

    private uk.gov.pay.adminusers.model.InviteValidateOtpRequest inviteValidateOtpRequest(java.lang.String inviteCode, int otpCode) {
        com.fasterxml.jackson.databind.node.ObjectNode json = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        json.put(uk.gov.pay.adminusers.model.InviteValidateOtpRequest.FIELD_CODE, inviteCode);
        json.put(uk.gov.pay.adminusers.model.InviteValidateOtpRequest.FIELD_OTP, otpCode);
        return uk.gov.pay.adminusers.model.InviteValidateOtpRequest.from(json);
    }

    private uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite(java.lang.String email, java.lang.String code, java.lang.String otpKey, uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity) {
        return new uk.gov.pay.adminusers.persistence.entity.InviteEntity(email, code, otpKey, roleEntity);
    }
}
