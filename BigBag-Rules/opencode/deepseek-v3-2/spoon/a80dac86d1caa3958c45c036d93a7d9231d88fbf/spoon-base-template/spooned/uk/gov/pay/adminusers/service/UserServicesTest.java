package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class UserServicesTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.UserDao mockUserDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.PasswordHasher mockPasswordHasher;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.NotificationService mockNotificationService;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.SecondFactorAuthenticator mockSecondFactorAuthenticator;

    @org.mockito.Captor
    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityArgumentCaptor;

    private uk.gov.pay.adminusers.service.UserServices underTest;

    private static final java.lang.String USER_EXTERNAL_ID = "7d19aff33f8948deb97ed16b2912dcd3";

    private static final java.lang.String USER_USERNAME = "random-name";

    private static final java.lang.String ANOTHER_USER_EXTERNAL_ID = "7d19aff33f8948deb97ed16b2912dcd4";

    private static final java.lang.String ANOTHER_USER_USERNAME = "another-random-name";

    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void before() {
        underTest = new uk.gov.pay.adminusers.service.UserServices(mockUserDao, mockPasswordHasher, new uk.gov.pay.adminusers.service.LinksBuilder("http://localhost"), 3, () -> mockNotificationService, mockSecondFactorAuthenticator, org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.ServiceFinder.class));
    }

    @org.junit.jupiter.api.Test
    void shouldFindAUserByExternalId() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityOptional = java.util.Optional.of(userEntity);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID)).thenReturn(userEntityOptional);
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.findUserByExternalId(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID);
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(userOptional.get().getExternalId(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID));
    }

    @org.junit.jupiter.api.Test
    void shouldFindAUsersByExternalIds() {
        uk.gov.pay.adminusers.model.User user1 = aUser();
        uk.gov.pay.adminusers.model.User user2 = anotherUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity1 = aUserEntityWithTrimmings(user1);
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity2 = aUserEntityWithTrimmings(user2);
        org.mockito.Mockito.when(mockUserDao.findByExternalIds(java.util.List.of(user1.getExternalId(), user2.getExternalId()))).thenReturn(java.util.Arrays.asList(userEntity1, userEntity2));
        java.util.List<uk.gov.pay.adminusers.model.User> users = underTest.findUsersByExternalIds(java.util.List.of(user1.getExternalId(), user2.getExternalId()));
        org.hamcrest.MatcherAssert.assertThat(users.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(users.get(0).getExternalId(), org.hamcrest.core.Is.is(user1.getExternalId()));
        org.hamcrest.MatcherAssert.assertThat(users.get(1).getExternalId(), org.hamcrest.core.Is.is(user2.getExternalId()));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnEmpty_WhenFindByExternalId_ifNotFound() {
        org.mockito.Mockito.when(mockUserDao.findByExternalId(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.findUserByExternalId(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID);
        org.junit.jupiter.api.Assertions.assertFalse(userOptional.isPresent());
    }

    @org.junit.jupiter.api.Test
    void shouldFindAUserByUserName() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityOptional = java.util.Optional.of(userEntity);
        org.mockito.Mockito.when(mockUserDao.findByUsername(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME)).thenReturn(userEntityOptional);
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.findUserByUsername(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME);
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(userOptional.get().getUsername(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnEmpty_WhenFindByUserName_ifNotFound() {
        org.mockito.Mockito.when(mockUserDao.findByUsername(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.findUserByUsername(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME);
        org.junit.jupiter.api.Assertions.assertFalse(userOptional.isPresent());
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUserAndResetLoginCount_ifAuthenticationSuccessfulAndUserNotDisabled() {
        uk.gov.pay.adminusers.model.User user = aUser();
        user.setLoginCounter(2);
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        userEntity.setPassword("hashed-password");
        org.mockito.Mockito.when(mockPasswordHasher.isEqual("random-password", "hashed-password")).thenReturn(true);
        org.mockito.Mockito.when(mockUserDao.findByUsername(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME)).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(mockUserDao.merge(userEntityArgumentCaptor.capture())).thenReturn(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.authenticate(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME, "random-password");
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        uk.gov.pay.adminusers.model.User authenticatedUser = userOptional.get();
        org.hamcrest.MatcherAssert.assertThat(authenticatedUser.getUsername(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME));
        org.hamcrest.MatcherAssert.assertThat(authenticatedUser.getLinks().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(userEntityArgumentCaptor.getValue().getLoginCounter(), org.hamcrest.core.Is.is(0));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUserAndNotResetLoginCount_ifAuthenticationSuccessfulButUserDisabled() {
        uk.gov.pay.adminusers.model.User user = aUser();
        user.setLoginCounter(2);
        user.setDisabled(true);
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        userEntity.setPassword("hashed-password");
        org.mockito.Mockito.when(mockPasswordHasher.isEqual("random-password", "hashed-password")).thenReturn(true);
        org.mockito.Mockito.when(mockUserDao.findByUsername(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME)).thenReturn(java.util.Optional.of(userEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.authenticate(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME, "random-password");
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        uk.gov.pay.adminusers.model.User authenticatedUser = userOptional.get();
        org.hamcrest.MatcherAssert.assertThat(authenticatedUser.getUsername(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME));
        org.hamcrest.MatcherAssert.assertThat(authenticatedUser.isDisabled(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(authenticatedUser.getLinks().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(userEntity.getLoginCounter(), org.hamcrest.core.Is.is(2));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnEmptyAndIncrementLoginCount_ifAuthenticationFail() {
        uk.gov.pay.adminusers.model.User user = aUser();
        user.setLoginCounter(1);
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        userEntity.setPassword("hashed-password");
        org.mockito.Mockito.when(mockUserDao.findByUsername(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME)).thenReturn(java.util.Optional.of(uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user)));
        org.mockito.Mockito.when(mockUserDao.merge(userEntityArgumentCaptor.capture())).thenReturn(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.authenticate(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME, "random-password");
        org.junit.jupiter.api.Assertions.assertFalse(userOptional.isPresent());
        uk.gov.pay.adminusers.persistence.entity.UserEntity savedUser = userEntityArgumentCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertTrue(org.exparity.hamcrest.date.ZonedDateTimeMatchers.within(3, java.time.temporal.ChronoUnit.SECONDS, savedUser.getCreatedAt()).matches(savedUser.getUpdatedAt()));
        org.hamcrest.MatcherAssert.assertThat(savedUser.getLoginCounter(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(savedUser.isDisabled(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    void shouldLockUser_onTooManyAuthFailures() {
        uk.gov.pay.adminusers.model.User user = aUser();
        user.setLoginCounter(2);
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        userEntity.setPassword("hashed-password");
        org.mockito.Mockito.when(mockUserDao.findByUsername(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME)).thenReturn(java.util.Optional.of(uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user)));
        org.mockito.Mockito.when(mockUserDao.merge(userEntityArgumentCaptor.capture())).thenReturn(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
        underTest.authenticate(uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME, "random-password");
        uk.gov.pay.adminusers.persistence.entity.UserEntity savedUser = userEntityArgumentCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertTrue(org.exparity.hamcrest.date.ZonedDateTimeMatchers.within(3, java.time.temporal.ChronoUnit.SECONDS, savedUser.getCreatedAt()).matches(savedUser.getUpdatedAt()));
        org.hamcrest.MatcherAssert.assertThat(savedUser.getLoginCounter(), org.hamcrest.core.Is.is(3));
        org.hamcrest.MatcherAssert.assertThat(savedUser.isDisabled(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUser_whenIncrementingSessionVersion_ifUserFound() {
        uk.gov.pay.adminusers.model.User user = aUser();
        com.fasterxml.jackson.databind.JsonNode node = uk.gov.pay.adminusers.service.UserServicesTest.objectMapper.valueToTree(java.util.Map.of("path", "sessionVersion", "op", "append", "value", "2"));
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityOptional = java.util.Optional.of(userEntity);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID)).thenReturn(userEntityOptional);
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.patchUser(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID, uk.gov.pay.adminusers.model.PatchRequest.from(node));
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(userOptional.get().getExternalId(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID));
        org.hamcrest.MatcherAssert.assertThat(userOptional.get().getSessionVersion(), org.hamcrest.core.Is.is(2));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUser_withDisabled_ifUserFoundDuringPatch() {
        uk.gov.pay.adminusers.model.User user = aUser();
        com.fasterxml.jackson.databind.JsonNode node = uk.gov.pay.adminusers.service.UserServicesTest.objectMapper.valueToTree(java.util.Map.of("path", "disabled", "op", "replace", "value", "true"));
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityOptional = java.util.Optional.of(userEntity);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID)).thenReturn(userEntityOptional);
        org.junit.jupiter.api.Assertions.assertFalse(user.isDisabled());
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.patchUser(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID, uk.gov.pay.adminusers.model.PatchRequest.from(node));
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(userOptional.get().getExternalId(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID));
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.get().isDisabled());
    }

    @org.junit.jupiter.api.Test
    void shouldResetLoginCounter_whenTheUserIsEnabled() {
        uk.gov.pay.adminusers.model.User user = aUser();
        user.setDisabled(java.lang.Boolean.TRUE);
        user.setLoginCounter(11);
        com.fasterxml.jackson.databind.JsonNode node = uk.gov.pay.adminusers.service.UserServicesTest.objectMapper.valueToTree(java.util.Map.of("path", "disabled", "op", "replace", "value", "false"));
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityOptional = java.util.Optional.of(userEntity);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID)).thenReturn(userEntityOptional);
        org.junit.jupiter.api.Assertions.assertTrue(user.isDisabled());
        org.hamcrest.MatcherAssert.assertThat(user.getLoginCounter(), org.hamcrest.core.Is.is(11));
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.patchUser(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID, uk.gov.pay.adminusers.model.PatchRequest.from(node));
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        org.junit.jupiter.api.Assertions.assertFalse(userOptional.get().isDisabled());
        org.hamcrest.MatcherAssert.assertThat(userOptional.get().getLoginCounter(), org.hamcrest.core.Is.is(0));
    }

    @org.junit.jupiter.api.Test
    void shouldUpdateTelephoneNumber_whenReplacingTelephoneNumber_ifUserFound() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        userEntity.setTelephoneNumber("+447700900000");
        java.lang.String newTelephoneNumber = "+441134960000";
        com.fasterxml.jackson.databind.JsonNode node = uk.gov.pay.adminusers.service.UserServicesTest.objectMapper.valueToTree(java.util.Map.of("path", "telephone_number", "op", "replace", "value", newTelephoneNumber));
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityOptional = java.util.Optional.of(userEntity);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID)).thenReturn(userEntityOptional);
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.patchUser(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID, uk.gov.pay.adminusers.model.PatchRequest.from(node));
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.times(1)).merge(userEntityArgumentCaptor.capture());
        uk.gov.pay.adminusers.persistence.entity.UserEntity persistedUser = userEntityArgumentCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(persistedUser.getTelephoneNumber(), org.hamcrest.core.Is.is(newTelephoneNumber));
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(userOptional.get().getTelephoneNumber(), org.hamcrest.core.Is.is(newTelephoneNumber));
    }

    @org.junit.jupiter.api.Test
    void shouldUpdateFeatures_whenPathIsFeatures_ifUserFound() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        userEntity.setFeatures("1");
        java.lang.String newFeature = "1,2,3";
        com.fasterxml.jackson.databind.JsonNode node = uk.gov.pay.adminusers.service.UserServicesTest.objectMapper.valueToTree(java.util.Map.of("path", "features", "op", "replace", "value", newFeature));
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityOptional = java.util.Optional.of(userEntity);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID)).thenReturn(userEntityOptional);
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.patchUser(uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID, uk.gov.pay.adminusers.model.PatchRequest.from(node));
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.times(1)).merge(userEntityArgumentCaptor.capture());
        uk.gov.pay.adminusers.persistence.entity.UserEntity persistedUser = userEntityArgumentCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(persistedUser.getFeatures(), org.hamcrest.core.Is.is(newFeature));
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(userOptional.get().getFeatures(), org.hamcrest.core.Is.is(newFeature));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUser_whenAuthenticate2FA_ifSuccessful() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));
        uk.gov.pay.adminusers.model.User aUser = aUser();
        int newPassCode = 123456;
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(aUser);
        userEntity.setLastLoggedInAt(now);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(aUser.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.authorize(aUser.getOtpKey(), newPassCode)).thenReturn(true);
        java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = underTest.authenticateSecondFactor(aUser.getExternalId(), newPassCode);
        org.junit.jupiter.api.Assertions.assertTrue(userOptional.isPresent());
        uk.gov.pay.adminusers.model.User user = userOptional.get();
        org.hamcrest.MatcherAssert.assertThat(user.getExternalId(), org.hamcrest.core.Is.is(aUser.getExternalId()));
        org.hamcrest.MatcherAssert.assertThat(user.getLoginCounter(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(user.getLastLoggedInAt().isAfter(java.time.ZonedDateTime.now().minusSeconds(10)), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnEmpty_whenAuthenticate2FA_ifUnsuccessful_whenTheUserNeverLoggedIn() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.authorize(user.getOtpKey(), 123456)).thenReturn(false);
        org.mockito.Mockito.when(mockUserDao.merge(userEntityArgumentCaptor.capture())).thenReturn(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
        java.util.Optional<uk.gov.pay.adminusers.model.User> tokenOptional = underTest.authenticateSecondFactor(user.getExternalId(), 123456);
        org.junit.jupiter.api.Assertions.assertFalse(tokenOptional.isPresent());
        uk.gov.pay.adminusers.persistence.entity.UserEntity savedUser = userEntityArgumentCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedUser.getLoginCounter(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(savedUser.isDisabled(), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(savedUser.getLastLoggedInAt(), org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue()));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnEmpty_whenAuthenticate2FA_ifUnsuccessful_whenTheUserLoggedInAtLeastOnce() {
        java.time.ZonedDateTime lastLoggedInDateTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusDays(7);
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        userEntity.setLastLoggedInAt(lastLoggedInDateTime);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.authorize(user.getOtpKey(), 123456)).thenReturn(false);
        org.mockito.Mockito.when(mockUserDao.merge(userEntityArgumentCaptor.capture())).thenReturn(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
        java.util.Optional<uk.gov.pay.adminusers.model.User> tokenOptional = underTest.authenticateSecondFactor(user.getExternalId(), 123456);
        org.junit.jupiter.api.Assertions.assertFalse(tokenOptional.isPresent());
        uk.gov.pay.adminusers.persistence.entity.UserEntity savedUser = userEntityArgumentCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedUser.getLoginCounter(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(savedUser.isDisabled(), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(savedUser.getLastLoggedInAt().equals(lastLoggedInDateTime), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnEmptyAndDisable_whenAuthenticate2FA_ifUnsuccessfulMaxRetry() {
        uk.gov.pay.adminusers.model.User user = aUser();
        user.setLoginCounter(3);
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = aUserEntityWithTrimmings(user);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.authorize(user.getOtpKey(), 123456)).thenReturn(false);
        org.mockito.Mockito.when(mockUserDao.merge(userEntityArgumentCaptor.capture())).thenReturn(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
        java.util.Optional<uk.gov.pay.adminusers.model.User> tokenOptional = underTest.authenticateSecondFactor(user.getExternalId(), 123456);
        org.junit.jupiter.api.Assertions.assertFalse(tokenOptional.isPresent());
        uk.gov.pay.adminusers.persistence.entity.UserEntity savedUser = userEntityArgumentCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedUser.getLoginCounter(), org.hamcrest.core.Is.is(4));
        org.hamcrest.MatcherAssert.assertThat(savedUser.isDisabled(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnEmpty_whenAuthenticate2FA_ifUserNotFound() {
        java.lang.String nonExistentExternalId = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        org.mockito.Mockito.when(mockUserDao.findByExternalId(nonExistentExternalId)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.User> tokenOptional = underTest.authenticateSecondFactor(nonExistentExternalId, 111111);
        org.junit.jupiter.api.Assertions.assertFalse(tokenOptional.isPresent());
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUser_whenProvisionNewOtpKey_ifUserFound() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        userEntity.setOtpKey("Original OTP key");
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.generateNewBase32EncodedSecret()).thenReturn("Provisional OTP key");
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.provisionNewOtpKey(user.getExternalId());
        org.hamcrest.MatcherAssert.assertThat(result.get().getOtpKey(), org.hamcrest.core.Is.is("Original OTP key"));
        org.hamcrest.MatcherAssert.assertThat(result.get().getProvisionalOtpKey(), org.hamcrest.core.Is.is("Provisional OTP key"));
        org.junit.jupiter.api.Assertions.assertTrue(org.exparity.hamcrest.date.ZonedDateTimeMatchers.within(3, java.time.temporal.ChronoUnit.SECONDS, result.get().getProvisionalOtpKeyCreatedAt()).matches(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)));
        org.mockito.Mockito.verify(mockUserDao).merge(userEntityArgumentCaptor.capture());
        uk.gov.pay.adminusers.persistence.entity.UserEntity savedUser = userEntityArgumentCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedUser.getOtpKey(), org.hamcrest.core.Is.is("Original OTP key"));
        org.hamcrest.MatcherAssert.assertThat(savedUser.getProvisionalOtpKey(), org.hamcrest.core.Is.is("Provisional OTP key"));
        org.junit.jupiter.api.Assertions.assertTrue(org.exparity.hamcrest.date.ZonedDateTimeMatchers.within(3, java.time.temporal.ChronoUnit.SECONDS, savedUser.getProvisionalOtpKeyCreatedAt()).matches(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnEmpty_whenProvisionNewOtpKey_ifUserNotFound() {
        java.lang.String nonExistentExternalId = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        org.mockito.Mockito.when(mockUserDao.findByExternalId(nonExistentExternalId)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.provisionNewOtpKey(nonExistentExternalId);
        org.junit.jupiter.api.Assertions.assertFalse(result.isPresent());
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.never()).merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnEmpty_whenProvisionNewOtpKey_ifUserDisabled() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        userEntity.setOtpKey("Original OTP key");
        userEntity.setDisabled(true);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.provisionNewOtpKey(user.getExternalId());
        org.junit.jupiter.api.Assertions.assertFalse(result.isPresent());
        org.hamcrest.MatcherAssert.assertThat(userEntity.getOtpKey(), org.hamcrest.core.Is.is("Original OTP key"));
        org.hamcrest.MatcherAssert.assertThat(userEntity.getProvisionalOtpKey(), org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue()));
        org.hamcrest.MatcherAssert.assertThat(userEntity.getProvisionalOtpKeyCreatedAt(), org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue()));
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.never()).merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUser_whenActivateNewOtpKey_ifUserFound() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        userEntity.setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS);
        userEntity.setOtpKey("Original OTP key");
        userEntity.setProvisionalOtpKey("New OTP key");
        userEntity.setProvisionalOtpKeyCreatedAt(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(89));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.authorize("New OTP key", 123456)).thenReturn(true);
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.activateNewOtpKey(user.getExternalId(), uk.gov.pay.adminusers.model.SecondFactorMethod.APP, 123456);
        org.hamcrest.MatcherAssert.assertThat(result.get().getOtpKey(), org.hamcrest.core.Is.is("New OTP key"));
        org.hamcrest.MatcherAssert.assertThat(result.get().getProvisionalOtpKey(), org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue()));
        org.hamcrest.MatcherAssert.assertThat(result.get().getProvisionalOtpKeyCreatedAt(), org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue()));
        org.mockito.Mockito.verify(mockUserDao).merge(userEntityArgumentCaptor.capture());
        uk.gov.pay.adminusers.persistence.entity.UserEntity savedUser = userEntityArgumentCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(savedUser.getOtpKey(), org.hamcrest.core.Is.is("New OTP key"));
        org.hamcrest.MatcherAssert.assertThat(savedUser.getProvisionalOtpKey(), org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue()));
        org.hamcrest.MatcherAssert.assertThat(savedUser.getProvisionalOtpKey(), org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue()));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUser_whenActivateNewOtpKey_ifCodeIncorrect() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        userEntity.setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS);
        userEntity.setOtpKey("Original OTP key");
        userEntity.setProvisionalOtpKey("New OTP key");
        userEntity.setProvisionalOtpKeyCreatedAt(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(89));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.authorize("New OTP key", 123456)).thenReturn(false);
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.activateNewOtpKey(user.getExternalId(), uk.gov.pay.adminusers.model.SecondFactorMethod.APP, 123456);
        org.junit.jupiter.api.Assertions.assertFalse(result.isPresent());
        org.hamcrest.MatcherAssert.assertThat(userEntity.getOtpKey(), org.hamcrest.core.Is.is("Original OTP key"));
        org.hamcrest.MatcherAssert.assertThat(userEntity.getSecondFactor(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS));
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.never()).merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUser_whenActivateNewOtpKey_ifNoProvisionalOtpCode() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        userEntity.setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS);
        userEntity.setOtpKey("Original OTP key");
        userEntity.setProvisionalOtpKeyCreatedAt(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(89));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.activateNewOtpKey(user.getExternalId(), uk.gov.pay.adminusers.model.SecondFactorMethod.APP, 123456);
        org.junit.jupiter.api.Assertions.assertFalse(result.isPresent());
        org.hamcrest.MatcherAssert.assertThat(userEntity.getOtpKey(), org.hamcrest.core.Is.is("Original OTP key"));
        org.hamcrest.MatcherAssert.assertThat(userEntity.getSecondFactor(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS));
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.never()).merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUser_whenActivateNewOtpKey_ifNoProvisionalOtpCodeCreatedAt() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        userEntity.setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS);
        userEntity.setOtpKey("Original OTP key");
        userEntity.setProvisionalOtpKey("New OTP key");
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.activateNewOtpKey(user.getExternalId(), uk.gov.pay.adminusers.model.SecondFactorMethod.APP, 123456);
        org.junit.jupiter.api.Assertions.assertFalse(result.isPresent());
        org.hamcrest.MatcherAssert.assertThat(userEntity.getOtpKey(), org.hamcrest.core.Is.is("Original OTP key"));
        org.hamcrest.MatcherAssert.assertThat(userEntity.getSecondFactor(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS));
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.never()).merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUser_whenActivateNewOtpKey_ifProvisionalOtpCodeCreatedAtTooLongAgo() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        userEntity.setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS);
        userEntity.setOtpKey("Original OTP key");
        userEntity.setProvisionalOtpKey("New OTP key");
        userEntity.setProvisionalOtpKeyCreatedAt(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(91));
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.activateNewOtpKey(user.getExternalId(), uk.gov.pay.adminusers.model.SecondFactorMethod.APP, 123456);
        org.junit.jupiter.api.Assertions.assertFalse(result.isPresent());
        org.hamcrest.MatcherAssert.assertThat(userEntity.getOtpKey(), org.hamcrest.core.Is.is("Original OTP key"));
        org.hamcrest.MatcherAssert.assertThat(userEntity.getSecondFactor(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS));
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.never()).merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnEmpty_whenActivateNewOtpKey_ifUserNotFound() {
        java.lang.String nonExistentExternalId = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        org.mockito.Mockito.when(mockUserDao.findByExternalId(nonExistentExternalId)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.activateNewOtpKey(nonExistentExternalId, uk.gov.pay.adminusers.model.SecondFactorMethod.SMS, 123456);
        org.junit.jupiter.api.Assertions.assertFalse(result.isPresent());
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.never()).merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUser_whenActivateNewOtpKey_ifUserDisabled() {
        uk.gov.pay.adminusers.model.User user = aUser();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        userEntity.setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS);
        userEntity.setOtpKey("Original OTP key");
        userEntity.setProvisionalOtpKey("New OTP key");
        userEntity.setProvisionalOtpKeyCreatedAt(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(89));
        userEntity.setDisabled(true);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.activateNewOtpKey(user.getExternalId(), uk.gov.pay.adminusers.model.SecondFactorMethod.APP, 123456);
        org.junit.jupiter.api.Assertions.assertFalse(result.isPresent());
        org.hamcrest.MatcherAssert.assertThat(userEntity.getOtpKey(), org.hamcrest.core.Is.is("Original OTP key"));
        org.hamcrest.MatcherAssert.assertThat(userEntity.getSecondFactor(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS));
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.never()).merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
    }

    @org.junit.jupiter.api.Test
    void resetSecondFactor_shouldUpdateOtpMethodAndSetNewOtpKey_ifSecondFactorMethodIsApp() {
        uk.gov.pay.adminusers.model.User user = aUser();
        user.setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod.APP);
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        java.lang.String newOtpKey = "newOtpKey";
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        org.mockito.Mockito.when(mockSecondFactorAuthenticator.generateNewBase32EncodedSecret()).thenReturn(newOtpKey);
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.resetSecondFactor(user.getExternalId());
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.times(1)).merge(userEntityArgumentCaptor.capture());
        uk.gov.pay.adminusers.persistence.entity.UserEntity persistedUser = userEntityArgumentCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(result.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(persistedUser.getOtpKey(), org.hamcrest.core.Is.is(newOtpKey));
        org.hamcrest.MatcherAssert.assertThat(persistedUser.getSecondFactor(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS));
    }

    @org.junit.jupiter.api.Test
    void resetSecondFactor_shouldNotUpdateUser_ifSecondFactorMethodIsSms() {
        uk.gov.pay.adminusers.model.User user = aUser();
        user.setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS);
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        org.mockito.Mockito.when(mockUserDao.findByExternalId(user.getExternalId())).thenReturn(java.util.Optional.of(userEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.resetSecondFactor(user.getExternalId());
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.never()).merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
        org.hamcrest.MatcherAssert.assertThat(result.isPresent(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    void resetSecondFactor_shouldReturnEmptyOptional_ifUserNotFound() {
        java.lang.String externalId = "not-found";
        org.mockito.Mockito.when(mockUserDao.findByExternalId(externalId)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.User> result = underTest.resetSecondFactor(externalId);
        org.mockito.Mockito.verify(mockUserDao, org.mockito.Mockito.never()).merge(org.mockito.ArgumentMatchers.any(uk.gov.pay.adminusers.persistence.entity.UserEntity.class));
        org.hamcrest.MatcherAssert.assertThat(result.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    void getAdminUserEmailsForGatewayAccountIdsReturnsEachInputGatewayAccountIdMappedToPossiblyEmptyListOfAdminEmails() {
        org.mockito.Mockito.when(mockUserDao.getAdminUserEmailsForGatewayAccountIds(java.util.List.of("1", "2", "3", "4", "5"))).thenReturn(java.util.Map.of("1", java.util.List.of("john@beatles.test", "paul@beatles.test"), "3", java.util.List.of("george@beatles.test"), "5", java.util.List.of("ringo@beatles.test")));
        java.util.Map<java.lang.String, java.util.List<java.lang.String>> result = underTest.getAdminUserEmailsForGatewayAccountIds(java.util.List.of("1", "2", "3", "4", "5"));
        org.hamcrest.MatcherAssert.assertThat(result.size(), org.hamcrest.core.Is.is(5));
        org.hamcrest.MatcherAssert.assertThat(result.get("1"), org.hamcrest.core.Is.is(java.util.List.of("john@beatles.test", "paul@beatles.test")));
        org.hamcrest.MatcherAssert.assertThat(result.get("2"), org.hamcrest.core.Is.is(java.util.List.of()));
        org.hamcrest.MatcherAssert.assertThat(result.get("3"), org.hamcrest.core.Is.is(java.util.List.of("george@beatles.test")));
        org.hamcrest.MatcherAssert.assertThat(result.get("4"), org.hamcrest.core.Is.is(java.util.List.of()));
        org.hamcrest.MatcherAssert.assertThat(result.get("5"), org.hamcrest.core.Is.is(java.util.List.of("ringo@beatles.test")));
    }

    @org.junit.jupiter.api.Test
    void getAdminUsersForServiceShouldReturnListOfUserEntitiesWithAdminPermissions() {
        var serv = uk.gov.pay.adminusers.model.Service.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        var users = java.util.Arrays.asList(uk.gov.pay.adminusers.service.UserServicesTest.aUserEntityWithRoleForService(serv, true, "admin1"), uk.gov.pay.adminusers.service.UserServicesTest.aUserEntityWithRoleForService(serv, true, "admin2"), uk.gov.pay.adminusers.service.UserServicesTest.aUserEntityWithRoleForService(serv, false, "user1"));
        org.mockito.Mockito.when(mockUserDao.findByServiceId(serv.getId())).thenReturn(users);
        var adminUsers = underTest.getAdminUsersForService(serv.getId());
        org.hamcrest.MatcherAssert.assertThat(adminUsers.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(adminUsers, org.hamcrest.Matchers.hasItems(users.get(0), users.get(1)));
        org.hamcrest.MatcherAssert.assertThat(adminUsers, org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(users.get(2))));
    }

    private uk.gov.pay.adminusers.model.User aUser() {
        return uk.gov.pay.adminusers.model.User.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), uk.gov.pay.adminusers.service.UserServicesTest.USER_EXTERNAL_ID, uk.gov.pay.adminusers.service.UserServicesTest.USER_USERNAME, "random-password", "email@example.com", "784rh", "8948924", java.util.Collections.emptyList(), null, uk.gov.pay.adminusers.model.SecondFactorMethod.SMS, null, null, null);
    }

    private uk.gov.pay.adminusers.model.User anotherUser() {
        return uk.gov.pay.adminusers.model.User.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), uk.gov.pay.adminusers.service.UserServicesTest.ANOTHER_USER_EXTERNAL_ID, uk.gov.pay.adminusers.service.UserServicesTest.ANOTHER_USER_USERNAME, "random-password", "email@example.com", "784rh", "8948924", java.util.Collections.emptyList(), null, uk.gov.pay.adminusers.model.SecondFactorMethod.SMS, null, null, null);
    }

    private uk.gov.pay.adminusers.model.Role aRole() {
        return uk.gov.pay.adminusers.model.Role.role(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), "role-name-" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), "role-description" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
    }

    private uk.gov.pay.adminusers.model.Permission aPermission() {
        return uk.gov.pay.adminusers.model.Permission.permission(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), "permission-name-" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), "permission-description" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
    }

    private uk.gov.pay.adminusers.persistence.entity.UserEntity aUserEntityWithTrimmings(uk.gov.pay.adminusers.model.User user) {
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(user);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity(java.util.List.of("a-gateway-account"));
        serviceEntity.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        serviceEntity.setId(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt());
        uk.gov.pay.adminusers.model.Role role = aRole();
        role.setPermissions(java.util.Set.of(aPermission()));
        uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRoleEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(serviceEntity, new uk.gov.pay.adminusers.persistence.entity.RoleEntity(role));
        userEntity.addServiceRole(serviceRoleEntity);
        return userEntity;
    }

    public static uk.gov.pay.adminusers.persistence.entity.UserEntity aUserEntityWithRoleForService(uk.gov.pay.adminusers.model.Service service, boolean isAdmin, java.lang.String username) {
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        userEntity.setUsername(username);
        userEntity.setEmail(java.lang.String.format("%s@service.gov.uk", userEntity.getUsername()));
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.model.Role.role(isAdmin ? 2 : 1, "role", "role-desc");
        role.setPermissions(java.util.Set.of(uk.gov.pay.adminusers.model.Permission.permission(1, "perm1", "perm1 desc"), uk.gov.pay.adminusers.model.Permission.permission(2, "perm2", "perm2 desc")));
        var serviceRoleEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.from(service), new uk.gov.pay.adminusers.persistence.entity.RoleEntity(role));
        userEntity.addServiceRole(serviceRoleEntity);
        return userEntity;
    }
}
