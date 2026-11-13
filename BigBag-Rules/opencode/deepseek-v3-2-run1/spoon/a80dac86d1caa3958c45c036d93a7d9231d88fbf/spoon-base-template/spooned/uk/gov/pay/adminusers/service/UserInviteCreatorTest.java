package uk.gov.pay.adminusers.service;
public class UserInviteCreatorTest {
    private static final java.lang.String SELFSERVICE_URL = "http://selfservice";

    private uk.gov.pay.adminusers.persistence.dao.RoleDao mockRoleDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.RoleDao.class);

    private uk.gov.pay.adminusers.persistence.dao.ServiceDao mockServiceDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.ServiceDao.class);

    private uk.gov.pay.adminusers.persistence.dao.UserDao mockUserDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.UserDao.class);

    private uk.gov.pay.adminusers.persistence.dao.InviteDao mockInviteDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.InviteDao.class);

    private uk.gov.pay.adminusers.app.config.AdminUsersConfig mockConfig = org.mockito.Mockito.mock(uk.gov.pay.adminusers.app.config.AdminUsersConfig.class);

    private uk.gov.pay.adminusers.service.NotificationService mockNotificationService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.NotificationService.class);

    private uk.gov.pay.adminusers.app.config.LinksConfig linksConfig = org.mockito.Mockito.mock(uk.gov.pay.adminusers.app.config.LinksConfig.class);

    private uk.gov.pay.adminusers.service.UserInviteCreator userInviteCreator;

    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.InviteEntity> expectedInvite = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.InviteEntity.class);

    private java.lang.String senderEmail = "sender@example.com";

    private java.lang.String email = "invited@example.com";

    private int serviceId = 1;

    private java.lang.String serviceExternalId = "3453rmeuty87t";

    private java.lang.String senderExternalId = "12345";

    private java.lang.String roleName = "view-only";

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        uk.gov.pay.adminusers.app.config.LinksConfig mockLinks = org.mockito.Mockito.mock(uk.gov.pay.adminusers.app.config.LinksConfig.class);
        org.mockito.Mockito.when(mockLinks.getSelfserviceUrl()).thenReturn(uk.gov.pay.adminusers.service.UserInviteCreatorTest.SELFSERVICE_URL);
        org.mockito.Mockito.when(mockConfig.getLinks()).thenReturn(mockLinks);
        userInviteCreator = new uk.gov.pay.adminusers.service.UserInviteCreator(mockInviteDao, mockUserDao, mockRoleDao, linksConfig, mockNotificationService, mockServiceDao);
    }

    @org.junit.jupiter.api.Test
    public void create_shouldSendNotificationOnSuccessfulInvite() {
        mockInviteSuccessForNonExistingUserNonExistingInvite();
        org.mockito.Mockito.when(mockNotificationService.sendInviteEmail(org.mockito.ArgumentMatchers.eq(senderEmail), org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.matches("^http://selfservice/invites/[0-9a-z]{32}$"))).thenReturn("random-notify-id");
        userInviteCreator.doInvite(inviteRequestFrom(senderExternalId, email, roleName));
        org.mockito.Mockito.verify(mockInviteDao).persist(expectedInvite.capture());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity savedInvite = expectedInvite.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedInvite.getEmail(), org.hamcrest.core.Is.is(email));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.getOtpKey(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.getCode(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnEmpty_ifServiceNotFound() {
        org.mockito.Mockito.when(mockServiceDao.findByExternalId(serviceExternalId)).thenReturn(java.util.Optional.empty());
        uk.gov.pay.adminusers.model.InviteUserRequest inviteUserRequest = inviteRequestFrom(senderEmail, email, roleName);
        java.util.Optional<uk.gov.pay.adminusers.model.Invite> invite = userInviteCreator.doInvite(inviteUserRequest);
        org.junit.jupiter.api.Assertions.assertFalse(invite.isPresent());
    }

    @org.junit.jupiter.api.Test
    public void create_shouldStillCreateTheInviteFailingOnSendingEmail() {
        mockInviteSuccessForNonExistingUserNonExistingInvite();
        org.mockito.Mockito.when(mockNotificationService.sendInviteEmail(org.mockito.ArgumentMatchers.eq(senderEmail), org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.matches("^http://selfservice/invites/[0-9a-z]{32}$"))).thenThrow(uk.gov.pay.adminusers.service.AdminUsersExceptions.userNotificationError(new java.lang.Exception("Cause")));
        userInviteCreator.doInvite(inviteRequestFrom(senderExternalId, email, roleName));
        org.mockito.Mockito.verify(mockInviteDao).persist(expectedInvite.capture());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity savedInvite = expectedInvite.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedInvite.getEmail(), org.hamcrest.core.Is.is(email));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.getOtpKey(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
        org.hamcrest.MatcherAssert.assertThat(savedInvite.getCode(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
    }

    @org.junit.jupiter.api.Test
    public void create_shouldFailWithConflict_WhenValidInviteExistsInvitingUserIsDifferent() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        service.setExternalId(serviceExternalId);
        java.lang.String inviteCode = "code";
        uk.gov.pay.adminusers.persistence.entity.UserEntity someOtherSender = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        java.lang.String someOtherSenderId = "7834ny0t7cr";
        someOtherSender.setExternalId(someOtherSenderId);
        someOtherSender.setEmail(senderEmail);
        uk.gov.pay.adminusers.persistence.entity.RoleEntity role = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(uk.gov.pay.adminusers.model.Role.role(uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId(), "admin", "Admin Role"));
        someOtherSender.addServiceRole(new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(service, role));
        org.mockito.Mockito.when(mockServiceDao.findByExternalId(serviceExternalId)).thenReturn(java.util.Optional.of(service));
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = anInvite(email, inviteCode, "otpKey", someOtherSender, service, role);
        org.mockito.Mockito.when(mockInviteDao.findByEmail(email)).thenReturn(java.util.List.of(anInvite));
        uk.gov.pay.adminusers.model.InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        javax.ws.rs.WebApplicationException webApplicationException = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> userInviteCreator.doInvite(inviteUserRequest));
        org.hamcrest.MatcherAssert.assertThat(webApplicationException.getMessage(), org.hamcrest.core.Is.is("HTTP 409 Conflict"));
    }

    @org.junit.jupiter.api.Test
    public void create_shouldFailWithPreConditionFailed_ifUserAlreadyInService() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        service.setExternalId(serviceExternalId);
        uk.gov.pay.adminusers.persistence.entity.UserEntity existingUser = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        existingUser.setExternalId("7834ny0t7cr");
        existingUser.setEmail(email);
        uk.gov.pay.adminusers.persistence.entity.RoleEntity role = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(uk.gov.pay.adminusers.model.Role.role(uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId(), "admin", "Admin Role"));
        existingUser.addServiceRole(new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(service, role));
        org.mockito.Mockito.when(mockServiceDao.findByExternalId(serviceExternalId)).thenReturn(java.util.Optional.of(service));
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.of(existingUser));
        uk.gov.pay.adminusers.model.InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        javax.ws.rs.WebApplicationException webApplicationException = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> userInviteCreator.doInvite(inviteUserRequest));
        org.hamcrest.MatcherAssert.assertThat(webApplicationException.getMessage(), org.hamcrest.core.Is.is("HTTP 412 Precondition Failed"));
    }

    @org.junit.jupiter.api.Test
    public void create_shouldResendTheSameInviteEmail_ifAValidInviteExistsForTheSameServiceBySameSender_forNewUser() {
        // Given
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = mockInviteSuccessExistingInvite();
        org.mockito.Mockito.when(mockNotificationService.sendInviteEmail(org.mockito.ArgumentMatchers.eq(senderEmail), org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.matches("^http://selfservice/invites/[0-9a-z]{32}$"))).thenReturn("random-notify-id");
        // When
        uk.gov.pay.adminusers.model.InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        java.util.Optional<uk.gov.pay.adminusers.model.Invite> invite = userInviteCreator.doInvite(inviteUserRequest);
        // Then
        org.hamcrest.MatcherAssert.assertThat(invite.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(invite.get().getCode(), org.hamcrest.core.Is.is(anInvite.getCode()));
        org.hamcrest.MatcherAssert.assertThat(invite.get().getEmail(), org.hamcrest.core.Is.is(anInvite.getEmail()));
    }

    @org.junit.jupiter.api.Test
    public void create_shouldErrorForbidden_ifSenderCannotInviteUsersToTheSpecifiedService() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = mockInviteSuccessForNonExistingUserNonExistingInvite();
        inviteEntity.getSender().getServicesRoles().clear();
        uk.gov.pay.adminusers.model.InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        javax.ws.rs.WebApplicationException webApplicationException = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> userInviteCreator.doInvite(inviteUserRequest));
        org.hamcrest.MatcherAssert.assertThat(webApplicationException.getMessage(), org.hamcrest.core.Is.is("HTTP 403 Forbidden"));
    }

    @org.junit.jupiter.api.Test
    public void create_shouldResendTheSameInviteEmail_ifAValidInviteExistsForTheSameServiceBySameSender_forExistingUser() {
        // Given
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.of(uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(email))));
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = mockInviteSuccessExistingInvite();
        org.mockito.Mockito.when(mockNotificationService.sendInviteExistingUserEmail(org.mockito.ArgumentMatchers.eq(senderEmail), org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.matches("^http://selfservice/invites/[0-9a-z]{32}$"), org.mockito.ArgumentMatchers.eq(anInvite.getService().getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName()))).thenReturn("random-notify-id");
        uk.gov.pay.adminusers.model.InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        java.util.Optional<uk.gov.pay.adminusers.model.Invite> invite = userInviteCreator.doInvite(inviteUserRequest);
        org.hamcrest.MatcherAssert.assertThat(invite.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(invite.get().getCode(), org.hamcrest.core.Is.is(anInvite.getCode()));
        org.hamcrest.MatcherAssert.assertThat(invite.get().getEmail(), org.hamcrest.core.Is.is(anInvite.getEmail()));
    }

    @org.junit.jupiter.api.Test
    public void create_shouldResendTheSameInviteEmail_ifAValidInviteExistsForTheSameServiceBySameSender_forExistingUser_evenIfNotifyThrowsAnError() {
        // Given
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.of(uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(email))));
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = mockInviteSuccessExistingInvite();
        org.mockito.Mockito.when(mockNotificationService.sendInviteExistingUserEmail(org.mockito.ArgumentMatchers.eq(senderEmail), org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.matches("^http://selfservice/invites/[0-9a-z]{32}$"), org.mockito.ArgumentMatchers.eq(anInvite.getService().getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName()))).thenThrow(uk.gov.pay.adminusers.service.AdminUsersExceptions.userNotificationError(new java.lang.Exception("Cause")));
        uk.gov.pay.adminusers.model.InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        java.util.Optional<uk.gov.pay.adminusers.model.Invite> invite = userInviteCreator.doInvite(inviteUserRequest);
        org.hamcrest.MatcherAssert.assertThat(invite.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(invite.get().getCode(), org.hamcrest.core.Is.is(anInvite.getCode()));
        org.hamcrest.MatcherAssert.assertThat(invite.get().getEmail(), org.hamcrest.core.Is.is(anInvite.getEmail()));
    }

    @org.junit.jupiter.api.Test
    public void create_shouldOnlyConsider_nonExpiredNonDisabledSameService_whenCheckingForExistingInvite() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity validInvite = mockInviteSuccessExistingInvite();
        uk.gov.pay.adminusers.persistence.entity.InviteEntity expiredInvite = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        expiredInvite.setExpiryDate(java.time.ZonedDateTime.now().minusDays(2));
        expiredInvite.setService(validInvite.getService());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity disabledInvite = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        disabledInvite.setDisabled(true);
        disabledInvite.setExpiryDate(java.time.ZonedDateTime.now().plusDays(1));
        disabledInvite.setService(validInvite.getService());
        uk.gov.pay.adminusers.persistence.entity.InviteEntity emptyServiceInvite = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        emptyServiceInvite.setExpiryDate(java.time.ZonedDateTime.now().plusDays(1));
        uk.gov.pay.adminusers.persistence.entity.InviteEntity nonMatchingServiceInvite = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntity.from(uk.gov.pay.adminusers.model.Service.from(new uk.gov.pay.adminusers.model.ServiceName("another-service")));
        nonMatchingServiceInvite.setService(serviceEntity);
        nonMatchingServiceInvite.setExpiryDate(java.time.ZonedDateTime.now().plusDays(1));
        org.mockito.Mockito.when(mockInviteDao.findByEmail(email)).thenReturn(java.util.List.of(expiredInvite, disabledInvite, emptyServiceInvite, nonMatchingServiceInvite, validInvite));
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.of(uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(email))));
        org.mockito.Mockito.when(mockNotificationService.sendInviteExistingUserEmail(org.mockito.ArgumentMatchers.eq(senderEmail), org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.matches("^http://selfservice/invites/[0-9a-z]{32}$"), org.mockito.ArgumentMatchers.eq(validInvite.getService().getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName()))).thenReturn("random-notify-id");
        uk.gov.pay.adminusers.model.InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        java.util.Optional<uk.gov.pay.adminusers.model.Invite> invite = userInviteCreator.doInvite(inviteUserRequest);
        org.hamcrest.MatcherAssert.assertThat(invite.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(invite.get().getCode(), org.hamcrest.core.Is.is(validInvite.getCode()));
        org.hamcrest.MatcherAssert.assertThat(invite.get().getEmail(), org.hamcrest.core.Is.is(validInvite.getEmail()));
    }

    private uk.gov.pay.adminusers.persistence.entity.InviteEntity mockInviteSuccessExistingInvite() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        service.setId(serviceId);
        service.setExternalId(serviceExternalId);
        uk.gov.pay.adminusers.persistence.entity.UserEntity sameSender = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        sameSender.setExternalId(senderExternalId);
        sameSender.setEmail(senderEmail);
        uk.gov.pay.adminusers.persistence.entity.RoleEntity role = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(uk.gov.pay.adminusers.model.Role.role(uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId(), "admin", "Admin Role"));
        sameSender.addServiceRole(new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(service, role));
        org.mockito.Mockito.when(mockServiceDao.findByExternalId(serviceExternalId)).thenReturn(java.util.Optional.of(service));
        org.mockito.Mockito.when(linksConfig.getSelfserviceInvitesUrl()).thenReturn("http://selfservice/invites");
        java.lang.String inviteCode = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = anInvite(email, inviteCode, "otpKey", sameSender, service, role);
        org.mockito.Mockito.when(mockInviteDao.findByEmail(email)).thenReturn(java.util.List.of(anInvite));
        return anInvite;
    }

    private uk.gov.pay.adminusers.persistence.entity.InviteEntity mockInviteSuccessForNonExistingUserNonExistingInvite() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        service.setExternalId(serviceExternalId);
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(mockInviteDao.findByEmail(email)).thenReturn(java.util.Collections.emptyList());
        org.mockito.Mockito.when(mockServiceDao.findByExternalId(serviceExternalId)).thenReturn(java.util.Optional.of(service));
        org.mockito.Mockito.when(mockRoleDao.findByRoleName(roleName)).thenReturn(java.util.Optional.of(new uk.gov.pay.adminusers.persistence.entity.RoleEntity()));
        org.mockito.Mockito.when(linksConfig.getSelfserviceInvitesUrl()).thenReturn("http://selfservice/invites");
        uk.gov.pay.adminusers.persistence.entity.UserEntity senderUser = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        senderUser.setExternalId(senderExternalId);
        senderUser.setEmail(senderEmail);
        uk.gov.pay.adminusers.persistence.entity.RoleEntity role = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(uk.gov.pay.adminusers.model.Role.role(uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId(), "admin", "Admin Role"));
        senderUser.addServiceRole(new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(service, role));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(senderExternalId)).thenReturn(java.util.Optional.of(senderUser));
        java.lang.String inviteCode = "code";
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = anInvite(email, inviteCode, "otpKey", senderUser, service, role);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        org.mockito.Mockito.doNothing().when(mockInviteDao).persist(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.InviteEntity.class));
        return anInvite;
    }

    private uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite(java.lang.String email, java.lang.String code, java.lang.String otpKey, uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity, uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity, uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity) {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(email, code, otpKey, roleEntity);
        inviteEntity.setSender(userEntity);
        inviteEntity.setService(serviceEntity);
        return inviteEntity;
    }

    private uk.gov.pay.adminusers.model.InviteUserRequest inviteRequestFrom(java.lang.String senderExternalId, java.lang.String email, java.lang.String roleName) {
        com.fasterxml.jackson.databind.node.ObjectNode json = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        json.put(uk.gov.pay.adminusers.model.InviteUserRequest.FIELD_SENDER, senderExternalId);
        json.put(uk.gov.pay.adminusers.model.InviteRequest.FIELD_EMAIL, email);
        json.put(uk.gov.pay.adminusers.model.InviteRequest.FIELD_ROLE_NAME, roleName);
        json.put(uk.gov.pay.adminusers.model.InviteUserRequest.FIELD_SERVICE_EXTERNAL_ID, serviceExternalId);
        return uk.gov.pay.adminusers.model.InviteUserRequest.from(json);
    }

    private uk.gov.pay.adminusers.model.User aUser(java.lang.String email) {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.model.Service.from(serviceId, serviceExternalId, new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        uk.gov.pay.adminusers.model.ServiceRole serviceRole = uk.gov.pay.adminusers.model.ServiceRole.from(service, uk.gov.pay.adminusers.model.Role.role(uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId(), "Admin", "Administrator"));
        return uk.gov.pay.adminusers.model.User.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), "a-username", "random-password", email, "784rh", "8948924", java.util.Collections.singletonList(serviceRole), null, uk.gov.pay.adminusers.model.SecondFactorMethod.SMS, null, null, null);
    }
}
