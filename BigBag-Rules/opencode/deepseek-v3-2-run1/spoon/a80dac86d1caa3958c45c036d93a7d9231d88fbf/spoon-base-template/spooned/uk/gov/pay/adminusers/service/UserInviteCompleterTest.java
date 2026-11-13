package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class UserInviteCompleterTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.UserDao mockUserDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.InviteDao mockInviteDao;

    private uk.gov.pay.adminusers.service.InviteCompleter userInviteCompleter;

    private java.lang.String otpKey = "otpKey";

    private java.lang.String inviteCode = "code";

    private java.lang.String senderEmail = "sender@example.com";

    private java.lang.String email = "invited@example.com";

    private int serviceId = 1;

    private java.lang.String serviceExternalId = "3453rmeuty87t";

    private java.lang.String senderExternalId = "12345";

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        userInviteCompleter = new uk.gov.pay.adminusers.service.UserInviteCompleter(mockInviteDao, mockUserDao);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenSubscribingAServiceToAnExistingUser_forValidInvite() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        service.setExternalId(serviceExternalId);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = createInvite();
        anInvite.setType(uk.gov.pay.adminusers.model.InviteType.USER);
        anInvite.setService(service);
        uk.gov.pay.adminusers.persistence.entity.UserEntity user = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(anInvite.getEmail()));
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.of(user));
        java.util.Optional<uk.gov.pay.adminusers.model.InviteCompleteResponse> completedInvite = userInviteCompleter.complete(inviteCode);
        org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.UserEntity> persistedUser = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.UserEntity.class);
        org.mockito.Mockito.verify(mockUserDao).merge(persistedUser.capture());
        org.hamcrest.MatcherAssert.assertThat(completedInvite.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(completedInvite.get().getInvite().isDisabled(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(persistedUser.getValue().getServicesRole(service.getExternalId()).isPresent(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_whenSubscribingAServiceToAnExistingUser_ifServiceIsNull() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        service.setExternalId(serviceExternalId);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = createInvite();
        anInvite.setType(uk.gov.pay.adminusers.model.InviteType.USER);
        anInvite.setService(null);
        uk.gov.pay.adminusers.persistence.entity.UserEntity user = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(anInvite.getEmail()));
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.of(user));
        javax.ws.rs.WebApplicationException webApplicationException = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> userInviteCompleter.complete(inviteCode));
        org.hamcrest.MatcherAssert.assertThat(webApplicationException.getMessage(), org.hamcrest.core.Is.is("HTTP 500 Internal Server Error"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_whenSubscribingAServiceToAnExistingUser_ifInviteIsNotUserType() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        service.setExternalId(serviceExternalId);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = createInvite();
        anInvite.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        anInvite.setService(service);
        uk.gov.pay.adminusers.persistence.entity.UserEntity user = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(aUser(anInvite.getEmail()));
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.of(user));
        javax.ws.rs.WebApplicationException webApplicationException = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> userInviteCompleter.complete(inviteCode));
        org.hamcrest.MatcherAssert.assertThat(webApplicationException.getMessage(), org.hamcrest.core.Is.is("HTTP 500 Internal Server Error"));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowEmailExistsException_whenPassedInviteCodeWhichIsDisabled() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = createInvite();
        anInvite.setType(uk.gov.pay.adminusers.model.InviteType.USER);
        anInvite.setDisabled(true);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        javax.ws.rs.WebApplicationException webApplicationException = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> userInviteCompleter.complete(inviteCode));
        org.hamcrest.MatcherAssert.assertThat(webApplicationException.getMessage(), org.hamcrest.core.Is.is("HTTP 410 Gone"));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowEmailExistsException_whenPassedInviteCodeWhichIsExpired() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = createInvite();
        anInvite.setType(uk.gov.pay.adminusers.model.InviteType.USER);
        anInvite.setExpiryDate(java.time.ZonedDateTime.now().minusDays(1));
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        javax.ws.rs.WebApplicationException webApplicationException = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> userInviteCompleter.complete(inviteCode));
        org.hamcrest.MatcherAssert.assertThat(webApplicationException.getMessage(), org.hamcrest.core.Is.is("HTTP 410 Gone"));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowInternalError_whenUserWithSpecifiedEmailNotExists() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = createInvite();
        anInvite.setType(uk.gov.pay.adminusers.model.InviteType.USER);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        javax.ws.rs.WebApplicationException webApplicationException = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> userInviteCompleter.complete(inviteCode));
        org.hamcrest.MatcherAssert.assertThat(webApplicationException.getMessage(), org.hamcrest.core.Is.is("HTTP 500 Internal Server Error"));
    }

    private uk.gov.pay.adminusers.persistence.entity.InviteEntity createInvite() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        uk.gov.pay.adminusers.persistence.entity.UserEntity senderUser = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        senderUser.setExternalId(senderExternalId);
        senderUser.setEmail(senderEmail);
        uk.gov.pay.adminusers.persistence.entity.RoleEntity role = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(uk.gov.pay.adminusers.model.Role.role(uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId(), "admin", "Admin Role"));
        senderUser.addServiceRole(new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(service, role));
        return anInvite(email, inviteCode, otpKey, senderUser, service, role);
    }

    private uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite(java.lang.String email, java.lang.String code, java.lang.String otpKey, uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity, uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity, uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity) {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(email, code, otpKey, roleEntity);
        anInvite.setSender(userEntity);
        anInvite.setService(serviceEntity);
        return anInvite;
    }

    private uk.gov.pay.adminusers.model.User aUser(java.lang.String email) {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.model.Service.from(serviceId, serviceExternalId, new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        uk.gov.pay.adminusers.model.ServiceRole serviceRole = uk.gov.pay.adminusers.model.ServiceRole.from(service, uk.gov.pay.adminusers.model.Role.role(uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId(), "Admin", "Administrator"));
        return uk.gov.pay.adminusers.model.User.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), "a-username", "random-password", email, "784rh", "8948924", java.util.Collections.singletonList(serviceRole), null, uk.gov.pay.adminusers.model.SecondFactorMethod.SMS, null, null, null);
    }
}
