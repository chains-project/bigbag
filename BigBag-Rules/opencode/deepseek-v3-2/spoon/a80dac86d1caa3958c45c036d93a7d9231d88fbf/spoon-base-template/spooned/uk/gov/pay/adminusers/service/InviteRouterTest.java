package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class InviteRouterTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.InviteServiceFactory inviteServiceFactory;

    private uk.gov.pay.adminusers.service.InviteRouter inviteRouter;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        inviteRouter = new uk.gov.pay.adminusers.service.InviteRouter(inviteServiceFactory, inviteDao);
    }

    @org.junit.jupiter.api.Test
    public void shouldResolve_serviceInviteCompleter_withValidation() {
        java.lang.String inviteCode = "a-code";
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = anInvite(inviteCode, uk.gov.pay.adminusers.model.InviteType.SERVICE);
        org.mockito.Mockito.when(inviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(inviteServiceFactory.completeServiceInvite()).thenReturn(new uk.gov.pay.adminusers.service.ServiceInviteCompleter(null, null, null, null));
        java.util.Optional<org.apache.commons.lang3.tuple.Pair<uk.gov.pay.adminusers.service.InviteCompleter, java.lang.Boolean>> result = inviteRouter.routeComplete(inviteCode);
        org.hamcrest.MatcherAssert.assertThat(result.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(result.get().getLeft(), org.hamcrest.core.Is.is(org.hamcrest.CoreMatchers.instanceOf(uk.gov.pay.adminusers.service.ServiceInviteCompleter.class)));
        org.hamcrest.MatcherAssert.assertThat(result.get().getRight(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void shouldResolve_userInviteCompleter_withoutValidation() {
        java.lang.String inviteCode = "a-code";
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = anInvite(inviteCode, uk.gov.pay.adminusers.model.InviteType.USER);
        org.mockito.Mockito.when(inviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(inviteServiceFactory.completeUserInvite()).thenReturn(new uk.gov.pay.adminusers.service.UserInviteCompleter(null, null));
        java.util.Optional<org.apache.commons.lang3.tuple.Pair<uk.gov.pay.adminusers.service.InviteCompleter, java.lang.Boolean>> result = inviteRouter.routeComplete(inviteCode);
        org.hamcrest.MatcherAssert.assertThat(result.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(result.get().getLeft(), org.hamcrest.core.Is.is(org.hamcrest.CoreMatchers.instanceOf(uk.gov.pay.adminusers.service.UserInviteCompleter.class)));
        org.hamcrest.MatcherAssert.assertThat(result.get().getRight(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldResolve_userInviteDispatcher_withValidation() {
        java.lang.String inviteCode = "a-code";
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = anInvite(inviteCode, uk.gov.pay.adminusers.model.InviteType.USER);
        org.mockito.Mockito.when(inviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(inviteServiceFactory.dispatchUserOtp()).thenReturn(new uk.gov.pay.adminusers.service.UserOtpDispatcher(null, null, null, null));
        java.util.Optional<org.apache.commons.lang3.tuple.Pair<uk.gov.pay.adminusers.service.InviteOtpDispatcher, java.lang.Boolean>> result = inviteRouter.routeOtpDispatch(inviteCode);
        org.hamcrest.MatcherAssert.assertThat(result.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(result.get().getLeft(), org.hamcrest.core.Is.is(org.hamcrest.CoreMatchers.instanceOf(uk.gov.pay.adminusers.service.UserOtpDispatcher.class)));
        org.hamcrest.MatcherAssert.assertThat(result.get().getRight(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void shouldResolve_serviceInviteDispatcher_withoutValidation() {
        java.lang.String inviteCode = "a-code";
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = anInvite(inviteCode, uk.gov.pay.adminusers.model.InviteType.SERVICE);
        org.mockito.Mockito.when(inviteDao.findByCode(inviteCode)).thenReturn(java.util.Optional.of(inviteEntity));
        org.mockito.Mockito.when(inviteServiceFactory.dispatchServiceOtp()).thenReturn(new uk.gov.pay.adminusers.service.ServiceOtpDispatcher(null, null, null, null));
        java.util.Optional<org.apache.commons.lang3.tuple.Pair<uk.gov.pay.adminusers.service.InviteOtpDispatcher, java.lang.Boolean>> result = inviteRouter.routeOtpDispatch(inviteCode);
        org.hamcrest.MatcherAssert.assertThat(result.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(result.get().getLeft(), org.hamcrest.core.Is.is(org.hamcrest.CoreMatchers.instanceOf(uk.gov.pay.adminusers.service.ServiceOtpDispatcher.class)));
        org.hamcrest.MatcherAssert.assertThat(result.get().getRight(), org.hamcrest.core.Is.is(false));
    }

    private uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite(java.lang.String code, uk.gov.pay.adminusers.model.InviteType inviteType) {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity();
        inviteEntity.setCode(code);
        inviteEntity.setEmail("example@example.com");
        inviteEntity.setTelephoneNumber("+441134960000");
        inviteEntity.setOtpKey("u73t2b7");
        inviteEntity.setType(inviteType);
        return inviteEntity;
    }
}
