package uk.gov.pay.adminusers.service;
public class ServiceInviteCreatorTest {
    private uk.gov.pay.adminusers.service.NotificationService notificationService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.NotificationService.class);

    private uk.gov.pay.adminusers.app.config.LinksConfig linksConfig = org.mockito.Mockito.mock(uk.gov.pay.adminusers.app.config.LinksConfig.class);

    private uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.InviteDao.class);

    private uk.gov.pay.adminusers.persistence.dao.UserDao userDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.UserDao.class);

    private uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.RoleDao.class);

    private uk.gov.pay.adminusers.service.PasswordHasher passwordHasher = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.PasswordHasher.class);

    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.InviteEntity> persistedInviteEntity = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.InviteEntity.class);

    private uk.gov.pay.adminusers.service.ServiceInviteCreator serviceInviteCreator;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        serviceInviteCreator = new uk.gov.pay.adminusers.service.ServiceInviteCreator(inviteDao, userDao, roleDao, new uk.gov.pay.adminusers.service.LinksBuilder("http://localhost/"), linksConfig, notificationService, passwordHasher);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_serviceInvite_IfEmailDoesNotConflict() {
        java.lang.String email = "email@example.gov.uk";
        uk.gov.pay.adminusers.model.InviteServiceRequest request = new uk.gov.pay.adminusers.model.InviteServiceRequest("password", email, "01134960000");
        uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(uk.gov.pay.adminusers.model.Role.role(2, "admin", "Adminstrator"));
        org.mockito.Mockito.when(userDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(inviteDao.findByEmail(email)).thenReturn(java.util.Collections.emptyList());
        org.mockito.Mockito.when(roleDao.findByRoleName("admin")).thenReturn(java.util.Optional.of(roleEntity));
        org.mockito.Mockito.when(notificationService.sendServiceInviteEmail(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString())).thenReturn("done");
        org.mockito.Mockito.when(linksConfig.getSelfserviceInvitesUrl()).thenReturn("http://selfservice/invites");
        org.mockito.Mockito.when(linksConfig.getSelfserviceUrl()).thenReturn("http://selfservice");
        org.mockito.Mockito.when(passwordHasher.hash("password")).thenReturn("encrypted-password");
        uk.gov.pay.adminusers.model.Invite invite = serviceInviteCreator.doInvite(request);
        org.mockito.Mockito.verify(inviteDao, org.mockito.internal.verification.VerificationModeFactory.times(1)).persist(persistedInviteEntity.capture());
        org.hamcrest.MatcherAssert.assertThat(invite.getEmail(), org.hamcrest.core.Is.is(request.getEmail()));
        org.hamcrest.MatcherAssert.assertThat(invite.getTelephoneNumber(), org.hamcrest.core.Is.is("+441134960000"));
        org.hamcrest.MatcherAssert.assertThat(invite.getType(), org.hamcrest.core.Is.is("service"));
        org.hamcrest.MatcherAssert.assertThat(invite.getLinks().get(0).getHref(), org.hamcrest.text.MatchesPattern.matchesPattern("^http://selfservice/invites/[0-9a-z]{32}$"));
        org.hamcrest.MatcherAssert.assertThat(persistedInviteEntity.getValue().getPassword(), org.hamcrest.core.Is.is("encrypted-password"));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_serviceInvite_IfTelephoneNumberAndPasswordNotPresent() {
        java.lang.String email = "email@example.gov.uk";
        uk.gov.pay.adminusers.model.InviteServiceRequest request = new uk.gov.pay.adminusers.model.InviteServiceRequest(email);
        uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(uk.gov.pay.adminusers.model.Role.role(2, "admin", "Adminstrator"));
        org.mockito.Mockito.when(userDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(inviteDao.findByEmail(email)).thenReturn(java.util.Collections.emptyList());
        org.mockito.Mockito.when(roleDao.findByRoleName("admin")).thenReturn(java.util.Optional.of(roleEntity));
        org.mockito.Mockito.when(notificationService.sendServiceInviteEmail(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString())).thenReturn("done");
        org.mockito.Mockito.when(linksConfig.getSelfserviceInvitesUrl()).thenReturn("http://selfservice/invites");
        org.mockito.Mockito.when(linksConfig.getSelfserviceUrl()).thenReturn("http://selfservice");
        uk.gov.pay.adminusers.model.Invite invite = serviceInviteCreator.doInvite(request);
        org.mockito.Mockito.verify(inviteDao, org.mockito.internal.verification.VerificationModeFactory.times(1)).persist(persistedInviteEntity.capture());
        org.hamcrest.MatcherAssert.assertThat(invite.getEmail(), org.hamcrest.core.Is.is(request.getEmail()));
        org.hamcrest.MatcherAssert.assertThat(invite.getTelephoneNumber(), org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue()));
        org.hamcrest.MatcherAssert.assertThat(invite.getType(), org.hamcrest.core.Is.is("service"));
        org.hamcrest.MatcherAssert.assertThat(invite.getLinks().get(0).getHref(), org.hamcrest.text.MatchesPattern.matchesPattern("^http://selfservice/invites/[0-9a-z]{32}$"));
        org.hamcrest.MatcherAssert.assertThat(persistedInviteEntity.getValue().getTelephoneNumber(), org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue()));
        org.hamcrest.MatcherAssert.assertThat(persistedInviteEntity.getValue().getPassword(), org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue()));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_serviceInvite_evenIfNotifyThrowsAnError() {
        java.lang.String email = "email@example.gov.uk";
        uk.gov.pay.adminusers.model.InviteServiceRequest request = new uk.gov.pay.adminusers.model.InviteServiceRequest("password", email, "01134960000");
        uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(uk.gov.pay.adminusers.model.Role.role(2, "admin", "Adminstrator"));
        org.mockito.Mockito.when(userDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(inviteDao.findByEmail(email)).thenReturn(java.util.Collections.emptyList());
        org.mockito.Mockito.when(roleDao.findByRoleName("admin")).thenReturn(java.util.Optional.of(roleEntity));
        org.mockito.Mockito.when(notificationService.sendServiceInviteEmail(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString())).thenThrow(uk.gov.pay.adminusers.service.AdminUsersExceptions.userNotificationError(new java.lang.Exception("Cause")));
        org.mockito.Mockito.when(linksConfig.getSelfserviceUrl()).thenReturn("http://selfservice");
        org.mockito.Mockito.when(linksConfig.getSelfserviceInvitesUrl()).thenReturn("http://selfservice/invites");
        uk.gov.pay.adminusers.model.Invite invite = serviceInviteCreator.doInvite(request);
        org.mockito.Mockito.verify(inviteDao, org.mockito.internal.verification.VerificationModeFactory.times(1)).persist(persistedInviteEntity.capture());
        org.hamcrest.MatcherAssert.assertThat(invite.getEmail(), org.hamcrest.core.Is.is(request.getEmail()));
        org.hamcrest.MatcherAssert.assertThat(invite.getTelephoneNumber(), org.hamcrest.core.Is.is("+441134960000"));
        org.hamcrest.MatcherAssert.assertThat(invite.getType(), org.hamcrest.core.Is.is("service"));
        org.hamcrest.MatcherAssert.assertThat(invite.getLinks().get(0).getHref(), org.hamcrest.text.MatchesPattern.matchesPattern("^http://selfservice/invites/[0-9a-z]{32}$"));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_ifUserAlreadyHasAValidServiceInvitationWithGivenEmail() {
        java.lang.String email = "email@example.gov.uk";
        uk.gov.pay.adminusers.model.InviteServiceRequest request = new uk.gov.pay.adminusers.model.InviteServiceRequest("password", email, "01134960000");
        uk.gov.pay.adminusers.persistence.entity.UserEntity sender = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        uk.gov.pay.adminusers.persistence.entity.RoleEntity role = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.RoleEntity.class);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity validInvite = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(email, "code", "otpKey", role);
        validInvite.setService(service);
        validInvite.setSender(sender);
        validInvite.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        org.mockito.Mockito.when(userDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(sender.getExternalId()).thenReturn("inviter-id");
        org.mockito.Mockito.when(sender.getEmail()).thenReturn("inviter@example.com");
        org.mockito.Mockito.when(inviteDao.findByEmail(email)).thenReturn(java.util.List.of(validInvite));
        org.mockito.Mockito.when(linksConfig.getSelfserviceInvitesUrl()).thenReturn("http://selfservice/invites");
        org.mockito.Mockito.when(notificationService.sendServiceInviteEmail(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString())).thenReturn("done");
        uk.gov.pay.adminusers.model.Invite invite = serviceInviteCreator.doInvite(request);
        org.mockito.Mockito.verify(inviteDao, org.mockito.internal.verification.VerificationModeFactory.times(1)).merge(persistedInviteEntity.capture());
        org.hamcrest.MatcherAssert.assertThat(invite.getEmail(), org.hamcrest.core.Is.is(request.getEmail()));
        org.hamcrest.MatcherAssert.assertThat(invite.getType(), org.hamcrest.core.Is.is("service"));
        org.hamcrest.MatcherAssert.assertThat(invite.getLinks().get(0).getHref(), org.hamcrest.core.Is.is("http://selfservice/invites/code"));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_serviceInvite_evenIfUserAlreadyHasAValidUserInvitationWithGivenEmail() {
        java.lang.String email = "email@example.gov.uk";
        uk.gov.pay.adminusers.model.InviteServiceRequest request = new uk.gov.pay.adminusers.model.InviteServiceRequest("password", email, "01134960000");
        uk.gov.pay.adminusers.persistence.entity.UserEntity sender = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        uk.gov.pay.adminusers.persistence.entity.RoleEntity role = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.RoleEntity.class);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity validInvite = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(email, "code", "otpKey", role);
        validInvite.setSender(sender);
        validInvite.setService(service);
        uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(uk.gov.pay.adminusers.model.Role.role(2, "admin", "Adminstrator"));
        org.mockito.Mockito.when(roleDao.findByRoleName("admin")).thenReturn(java.util.Optional.of(roleEntity));
        org.mockito.Mockito.when(userDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(sender.getExternalId()).thenReturn("inviter-id");
        org.mockito.Mockito.when(sender.getEmail()).thenReturn("inviter@example.com");
        org.mockito.Mockito.when(inviteDao.findByEmail(email)).thenReturn(java.util.List.of(validInvite));
        org.mockito.Mockito.when(linksConfig.getSelfserviceInvitesUrl()).thenReturn("http://selfservice/invites");
        org.mockito.Mockito.when(notificationService.sendServiceInviteEmail(org.mockito.ArgumentMatchers.eq(email), org.mockito.Mockito.matches("^http://selfservice/invites/[0-9a-z]{32}$"))).thenReturn("done");
        uk.gov.pay.adminusers.model.Invite invite = serviceInviteCreator.doInvite(request);
        org.hamcrest.MatcherAssert.assertThat(invite.getEmail(), org.hamcrest.core.Is.is(request.getEmail()));
        org.hamcrest.MatcherAssert.assertThat(invite.getType(), org.hamcrest.core.Is.is("service"));
        org.hamcrest.MatcherAssert.assertThat(invite.getLinks().get(0).getHref().matches("^http://selfservice/invites/[0-9a-z]{32}$"), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_ifUserAlreadyExistsWithGivenEmail() {
        java.lang.String email = "email@example.gov.uk";
        uk.gov.pay.adminusers.model.InviteServiceRequest request = new uk.gov.pay.adminusers.model.InviteServiceRequest("password", email, "01134960000");
        uk.gov.pay.adminusers.persistence.entity.UserEntity existingUserEntity = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        org.mockito.Mockito.when(userDao.findByEmail(email)).thenReturn(java.util.Optional.of(existingUserEntity));
        org.mockito.Mockito.when(linksConfig.getSupportUrl()).thenReturn("http://frontend");
        org.mockito.Mockito.when(linksConfig.getSelfserviceForgottenPasswordUrl()).thenReturn("http://selfservice/forgotten-password");
        org.mockito.Mockito.when(linksConfig.getSelfserviceInvitesUrl()).thenReturn("http://selfservice/invites");
        org.mockito.Mockito.when(linksConfig.getSelfserviceLoginUrl()).thenReturn("http://selfservice/login");
        org.mockito.Mockito.when(linksConfig.getSelfserviceUrl()).thenReturn("http://selfservice");
        org.mockito.Mockito.when(notificationService.sendServiceInviteUserExistsEmail(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn("done");
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceInviteCreator.doInvite(request));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 409 Conflict"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_ifUserAlreadyExistsAndDisabledWithGivenEmail() {
        java.lang.String email = "email@example.gov.uk";
        uk.gov.pay.adminusers.model.InviteServiceRequest request = new uk.gov.pay.adminusers.model.InviteServiceRequest("password", email, "01134960000");
        uk.gov.pay.adminusers.persistence.entity.UserEntity existingUserEntity = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        existingUserEntity.setDisabled(true);
        org.mockito.Mockito.when(userDao.findByEmail(email)).thenReturn(java.util.Optional.of(existingUserEntity));
        org.mockito.Mockito.when(linksConfig.getSupportUrl()).thenReturn("http://frontend");
        org.mockito.Mockito.when(notificationService.sendServiceInviteUserDisabledEmail(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString())).thenReturn("done");
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceInviteCreator.doInvite(request));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 409 Conflict"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_ifRoleDoesNotExists() {
        java.lang.String email = "email@example.gov.uk";
        uk.gov.pay.adminusers.model.InviteServiceRequest request = new uk.gov.pay.adminusers.model.InviteServiceRequest("password", email, "01134960000");
        org.mockito.Mockito.when(userDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(inviteDao.findByEmail(email)).thenReturn(java.util.Collections.emptyList());
        org.mockito.Mockito.when(roleDao.findByRoleName("admin")).thenReturn(java.util.Optional.empty());
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceInviteCreator.doInvite(request));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 500 Internal Server Error"));
    }
}
