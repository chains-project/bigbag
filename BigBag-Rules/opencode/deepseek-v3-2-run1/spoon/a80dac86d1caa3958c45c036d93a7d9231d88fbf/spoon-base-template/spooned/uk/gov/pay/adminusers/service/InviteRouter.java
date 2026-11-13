package uk.gov.pay.adminusers.service;
public class InviteRouter {
    private final uk.gov.pay.adminusers.service.InviteServiceFactory inviteServiceFactory;

    private final uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    @com.google.inject.Inject
    public InviteRouter(uk.gov.pay.adminusers.service.InviteServiceFactory inviteServiceFactory, uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao) {
        this.inviteServiceFactory = inviteServiceFactory;
        this.inviteDao = inviteDao;
    }

    public java.util.Optional<org.apache.commons.lang3.tuple.Pair<uk.gov.pay.adminusers.service.InviteCompleter, java.lang.Boolean>> routeComplete(java.lang.String inviteCode) {
        return routeIfExist(inviteCode, inviteEntity -> {
            boolean isServiceType = inviteEntity.isServiceType();
            uk.gov.pay.adminusers.service.InviteCompleter inviteCompleter = (isServiceType) ? inviteServiceFactory.completeServiceInvite() : inviteServiceFactory.completeUserInvite();
            return java.util.Optional.of(org.apache.commons.lang3.tuple.Pair.of(inviteCompleter, isServiceType));
        });
    }

    public java.util.Optional<org.apache.commons.lang3.tuple.Pair<uk.gov.pay.adminusers.service.InviteOtpDispatcher, java.lang.Boolean>> routeOtpDispatch(java.lang.String inviteCode) {
        return routeIfExist(inviteCode, inviteEntity -> {
            boolean isUserType = inviteEntity.isUserType();
            uk.gov.pay.adminusers.service.InviteOtpDispatcher inviteOtpDispatcher = (isUserType) ? inviteServiceFactory.dispatchUserOtp() : inviteServiceFactory.dispatchServiceOtp();
            return java.util.Optional.of(org.apache.commons.lang3.tuple.Pair.of(inviteOtpDispatcher, isUserType));
        });
    }

    private <T> java.util.Optional<org.apache.commons.lang3.tuple.Pair<T, java.lang.Boolean>> routeIfExist(java.lang.String inviteCode, java.util.function.Function<uk.gov.pay.adminusers.persistence.entity.InviteEntity, java.util.Optional<org.apache.commons.lang3.tuple.Pair<T, java.lang.Boolean>>> routeFunction) {
        return inviteDao.findByCode(inviteCode).map(routeFunction).orElseGet(java.util.Optional::empty);
    }
}
