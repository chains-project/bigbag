package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class ServiceInviteCompleterTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.ServiceDao mockServiceDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.UserDao mockUserDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.InviteDao mockInviteDao;

    private uk.gov.pay.adminusers.service.InviteCompleter serviceInviteCompleter;

    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.UserEntity> expectedInvitedUser = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.UserEntity.class);

    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.InviteEntity> expectedInvite = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.InviteEntity.class);

    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> expectedService = org.mockito.ArgumentCaptor.forClass(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);

    private java.lang.String otpKey = "otpKey";

    private java.lang.String inviteCode = "code";

    private java.lang.String senderEmail = "sender@example.com";

    private java.lang.String email = "invited@example.com";

    private int serviceId = 1;

    private java.lang.String senderExternalId = "12345";

    private java.lang.String baseUrl = "http://localhost";

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        serviceInviteCompleter = new uk.gov.pay.adminusers.service.ServiceInviteCompleter(mockInviteDao, mockUserDao, mockServiceDao, new uk.gov.pay.adminusers.service.LinksBuilder(baseUrl));
    }

    @org.junit.jupiter.api.Test
    public void shouldCreateServiceAndUser_withGatewayAccounts_whenPassedValidServiceInviteCode() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = createInvite();
        anInvite.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        uk.gov.pay.adminusers.model.InviteCompleteRequest data = new uk.gov.pay.adminusers.model.InviteCompleteRequest();
        data.setGatewayAccountIds(java.util.Arrays.asList("1", "2"));
        uk.gov.pay.adminusers.model.InviteCompleteResponse inviteResponse = serviceInviteCompleter.withData(data).complete(anInvite.getCode()).get();
        org.mockito.Mockito.verify(mockServiceDao).persist(expectedService.capture());
        org.mockito.Mockito.verify(mockUserDao).merge(expectedInvitedUser.capture());
        org.mockito.Mockito.verify(mockInviteDao).merge(expectedInvite.capture());
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = expectedService.getValue();
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getGatewayAccountIds().stream().map(uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity::getGatewayAccountId).collect(java.util.stream.Collectors.toUnmodifiableList()), org.hamcrest.Matchers.hasItems("2", "1"));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.isRedirectToServiceImmediatelyOnTerminalState(), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.isCollectBillingAddress(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getDefaultBillingAddressCountry(), org.hamcrest.core.Is.is("GB"));
        org.hamcrest.MatcherAssert.assertThat(inviteResponse.getInvite().isDisabled(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(inviteResponse.getInvite().getLinks().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(inviteResponse.getInvite().getLinks().get(0).getRel(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.Link.Rel.USER));
        org.hamcrest.MatcherAssert.assertThat(inviteResponse.getInvite().getLinks().get(0).getHref(), org.hamcrest.text.MatchesPattern.matchesPattern(("^" + baseUrl) + "/v1/api/users/[0-9a-z]{32}$"));
    }

    @org.junit.jupiter.api.Test
    public void shouldCreateServiceAndUser_withoutGatewayAccounts_whenPassedValidServiceInviteCode() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        service.setId(serviceId);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = createInvite();
        anInvite.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        uk.gov.pay.adminusers.model.InviteCompleteResponse inviteResponse = serviceInviteCompleter.withData(new uk.gov.pay.adminusers.model.InviteCompleteRequest()).complete(anInvite.getCode()).get();
        org.mockito.Mockito.verify(mockServiceDao).persist(expectedService.capture());
        org.mockito.Mockito.verify(mockUserDao).merge(expectedInvitedUser.capture());
        org.mockito.Mockito.verify(mockInviteDao).merge(expectedInvite.capture());
        org.hamcrest.MatcherAssert.assertThat(expectedService.getValue().getGatewayAccountIds().isEmpty(), org.hamcrest.core.Is.is(true));
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = expectedService.getValue();
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getGatewayAccountIds().isEmpty(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.isRedirectToServiceImmediatelyOnTerminalState(), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.isCollectBillingAddress(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getDefaultBillingAddressCountry(), org.hamcrest.core.Is.is("GB"));
        org.hamcrest.MatcherAssert.assertThat(inviteResponse.getInvite().isDisabled(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(inviteResponse.getInvite().getLinks().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(inviteResponse.getInvite().getLinks().get(0).getRel(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.Link.Rel.USER));
        org.hamcrest.MatcherAssert.assertThat(inviteResponse.getInvite().getLinks().get(0).getHref(), org.hamcrest.text.MatchesPattern.matchesPattern(("^" + baseUrl) + "/v1/api/users/[0-9a-z]{32}$"));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowConflict_whenPassedInviteEmailAlreadyHasARegisteredUser() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = createInvite();
        anInvite.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        org.mockito.Mockito.when(mockUserDao.findByEmail(anInvite.getEmail())).thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.UserEntity.class)));
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceInviteCompleter.complete(anInvite.getCode()));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 409 Conflict"));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowEmailExistsException_whenPassedInviteCodeWhichIsDisabled() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = createInvite();
        anInvite.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        anInvite.setDisabled(true);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceInviteCompleter.complete(anInvite.getCode()));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 410 Gone"));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowEmailExistsException_whenPassedInviteCodeWhichIsExpired() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = createInvite();
        anInvite.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        anInvite.setExpiryDate(java.time.ZonedDateTime.now().minusDays(1));
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceInviteCompleter.complete(anInvite.getCode()));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 410 Gone"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_whenTryingToCreateServiceAndService_ifInviteIsOfUserType() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.setId(serviceId);
        uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite = createInvite();
        anInvite.setType(uk.gov.pay.adminusers.model.InviteType.USER);
        org.mockito.Mockito.when(mockInviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(anInvite));
        org.mockito.Mockito.when(mockUserDao.findByEmail(email)).thenReturn(java.util.Optional.empty());
        javax.ws.rs.WebApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceInviteCompleter.complete(anInvite.getCode()));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("HTTP 500 Internal Server Error"));
    }

    private uk.gov.pay.adminusers.persistence.entity.InviteEntity createInvite() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
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
}
