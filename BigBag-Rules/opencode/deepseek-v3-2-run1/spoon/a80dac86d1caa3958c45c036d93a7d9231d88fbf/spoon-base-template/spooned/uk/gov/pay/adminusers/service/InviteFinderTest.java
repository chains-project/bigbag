package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class InviteFinderTest {
    private static final java.lang.String EMAIL = "test@test.gov.uk";

    private static final java.lang.String CODE = "invite-code";

    private static final java.lang.String OTP_KEY = "otp-key";

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.UserDao mockUserDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.InviteDao mockInviteDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.entity.RoleEntity mockRoleEntity;

    private uk.gov.pay.adminusers.service.InviteFinder inviteFinder;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        inviteFinder = new uk.gov.pay.adminusers.service.InviteFinder(mockInviteDao, mockUserDao);
    }

    @org.junit.jupiter.api.Test
    public void shouldFindInvite_withNonExistingUser() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(uk.gov.pay.adminusers.service.InviteFinderTest.EMAIL, uk.gov.pay.adminusers.service.InviteFinderTest.CODE, uk.gov.pay.adminusers.service.InviteFinderTest.OTP_KEY, mockRoleEntity);
        org.mockito.Mockito.when(mockInviteDao.findByCode(uk.gov.pay.adminusers.service.InviteFinderTest.CODE)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(mockUserDao.findByEmail(uk.gov.pay.adminusers.service.InviteFinderTest.EMAIL)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.Invite> inviteOptional = inviteFinder.find(uk.gov.pay.adminusers.service.InviteFinderTest.CODE);
        org.hamcrest.MatcherAssert.assertThat(inviteOptional.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(inviteOptional.get().isUserExist(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldFindInvite_withExistingUser() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(uk.gov.pay.adminusers.service.InviteFinderTest.EMAIL, uk.gov.pay.adminusers.service.InviteFinderTest.CODE, uk.gov.pay.adminusers.service.InviteFinderTest.OTP_KEY, mockRoleEntity);
        org.mockito.Mockito.when(mockInviteDao.findByCode(uk.gov.pay.adminusers.service.InviteFinderTest.CODE)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(mockUserDao.findByEmail(uk.gov.pay.adminusers.service.InviteFinderTest.EMAIL)).thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class)));
        java.util.Optional<uk.gov.pay.adminusers.model.Invite> inviteOptional = inviteFinder.find(uk.gov.pay.adminusers.service.InviteFinderTest.CODE);
        org.hamcrest.MatcherAssert.assertThat(inviteOptional.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(inviteOptional.get().isUserExist(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void shouldHaveFlagToSayPasswordNotSet() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(uk.gov.pay.adminusers.service.InviteFinderTest.EMAIL, uk.gov.pay.adminusers.service.InviteFinderTest.CODE, uk.gov.pay.adminusers.service.InviteFinderTest.OTP_KEY, mockRoleEntity);
        org.mockito.Mockito.when(mockInviteDao.findByCode(uk.gov.pay.adminusers.service.InviteFinderTest.CODE)).thenReturn(java.util.Optional.of(inviteEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.Invite> inviteOptional = inviteFinder.find(uk.gov.pay.adminusers.service.InviteFinderTest.CODE);
        org.hamcrest.MatcherAssert.assertThat(inviteOptional.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(inviteOptional.get().isPasswordSet(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldHaveFlagToSayPasswordIsSet() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(uk.gov.pay.adminusers.service.InviteFinderTest.EMAIL, uk.gov.pay.adminusers.service.InviteFinderTest.CODE, uk.gov.pay.adminusers.service.InviteFinderTest.OTP_KEY, mockRoleEntity);
        inviteEntity.setPassword("password123");
        org.mockito.Mockito.when(mockInviteDao.findByCode(uk.gov.pay.adminusers.service.InviteFinderTest.CODE)).thenReturn(java.util.Optional.of(inviteEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.Invite> inviteOptional = inviteFinder.find(uk.gov.pay.adminusers.service.InviteFinderTest.CODE);
        org.hamcrest.MatcherAssert.assertThat(inviteOptional.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(inviteOptional.get().isPasswordSet(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void shouldErrorLocked_ifInviteIsExpired() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(uk.gov.pay.adminusers.service.InviteFinderTest.EMAIL, uk.gov.pay.adminusers.service.InviteFinderTest.CODE, uk.gov.pay.adminusers.service.InviteFinderTest.OTP_KEY, mockRoleEntity);
        inviteEntity.setExpiryDate(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).minusDays(1));
        org.mockito.Mockito.when(mockInviteDao.findByCode(uk.gov.pay.adminusers.service.InviteFinderTest.CODE)).thenReturn(java.util.Optional.of(inviteEntity));
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> inviteFinder.find(uk.gov.pay.adminusers.service.InviteFinderTest.CODE));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 410 Gone"));
    }

    @org.junit.jupiter.api.Test
    public void shouldErrorLocked_ifInviteIsDisabled() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(uk.gov.pay.adminusers.service.InviteFinderTest.EMAIL, uk.gov.pay.adminusers.service.InviteFinderTest.CODE, uk.gov.pay.adminusers.service.InviteFinderTest.OTP_KEY, mockRoleEntity);
        inviteEntity.setDisabled(true);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.InviteEntity> inviteEntityOptional = java.util.Optional.of(inviteEntity);
        org.mockito.Mockito.when(mockInviteDao.findByCode(uk.gov.pay.adminusers.service.InviteFinderTest.CODE)).thenReturn(inviteEntityOptional);
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> inviteFinder.find(uk.gov.pay.adminusers.service.InviteFinderTest.CODE));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 410 Gone"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnEmptyOptional_forNonExistingInviteCode() {
        java.lang.String code = "non-existent-code";
        org.mockito.Mockito.when(mockInviteDao.findByCode(code)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.Invite> inviteOptional = inviteFinder.find(code);
        org.hamcrest.MatcherAssert.assertThat(inviteOptional.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldFindAllActiveInvites() {
        java.lang.String externalServiceId = "sdfuhsdyftgdfa";
        java.lang.String firstEmail = "user1@mail.test";
        java.lang.String secondEmail = "user2@mail.test";
        uk.gov.pay.adminusers.persistence.entity.InviteEntity firstInviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(firstEmail, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), uk.gov.pay.adminusers.service.InviteFinderTest.OTP_KEY, org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.RoleEntity.class));
        uk.gov.pay.adminusers.persistence.entity.InviteEntity secondInviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(secondEmail, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), uk.gov.pay.adminusers.service.InviteFinderTest.OTP_KEY, org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.RoleEntity.class));
        uk.gov.pay.adminusers.persistence.entity.InviteEntity disabledInviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity("email@email.test", uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), "otp-key", org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.RoleEntity.class));
        disabledInviteEntity.setDisabled(true);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity expiredInviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity("email@email.test", uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), "otp-key", org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.RoleEntity.class));
        expiredInviteEntity.setExpiryDate(java.time.ZonedDateTime.now().minusMinutes(1));
        org.mockito.Mockito.when(mockUserDao.findByEmail(firstEmail)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(mockUserDao.findByEmail(secondEmail)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(mockInviteDao.findAllByServiceId(externalServiceId)).thenReturn(java.util.List.of(firstInviteEntity, secondInviteEntity, disabledInviteEntity, expiredInviteEntity));
        java.util.List<uk.gov.pay.adminusers.model.Invite> invites = inviteFinder.findAllActiveInvites(externalServiceId);
        org.hamcrest.MatcherAssert.assertThat(invites.size(), org.hamcrest.core.Is.is(2));
        uk.gov.pay.adminusers.model.Invite firstInvite = invites.get(0);
        org.hamcrest.MatcherAssert.assertThat(firstInvite.getEmail(), org.hamcrest.core.Is.is(firstEmail));
        uk.gov.pay.adminusers.model.Invite secondInvite = invites.get(1);
        org.hamcrest.MatcherAssert.assertThat(secondInvite.getEmail(), org.hamcrest.core.Is.is(secondEmail));
    }
}
