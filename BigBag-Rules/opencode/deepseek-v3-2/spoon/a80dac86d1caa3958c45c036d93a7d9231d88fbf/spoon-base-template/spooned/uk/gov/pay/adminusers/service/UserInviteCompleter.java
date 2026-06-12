package uk.gov.pay.adminusers.service;
public class UserInviteCompleter extends uk.gov.pay.adminusers.service.InviteCompleter {
    private final uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    @com.google.inject.Inject
    public UserInviteCompleter(uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao, uk.gov.pay.adminusers.persistence.dao.UserDao userDao) {
        super();
        this.inviteDao = inviteDao;
        this.userDao = userDao;
    }

    @java.lang.Override
    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.InviteCompleteResponse> complete(java.lang.String inviteCode) {
        return inviteDao.findByCode(inviteCode).map(inviteEntity -> {
            if (inviteEntity.isExpired() || inviteEntity.isDisabled()) {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.inviteLockedException(inviteEntity.getCode());
            }
            return userDao.findByEmail(inviteEntity.getEmail()).map(userEntity -> {
                if ((inviteEntity.getService() != null) && inviteEntity.isUserType()) {
                    uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRole = new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(inviteEntity.getService(), inviteEntity.getRole());
                    userEntity.addServiceRole(serviceRole);
                    userDao.merge(userEntity);
                    inviteEntity.setDisabled(true);
                    inviteDao.merge(inviteEntity);
                    uk.gov.pay.adminusers.model.InviteCompleteResponse response = new uk.gov.pay.adminusers.model.InviteCompleteResponse(inviteEntity.toInvite());
                    response.setUserExternalId(userEntity.getExternalId());
                    response.setServiceExternalId(inviteEntity.getService().getExternalId());
                    return java.util.Optional.of(response);
                } else {
                    throw uk.gov.pay.adminusers.service.AdminUsersExceptions.internalServerError(java.lang.String.format("Attempting to complete user subscription to a service for a non existent service. invite-code = %s", inviteEntity.getCode()));
                }
            }).orElseGet(() -> {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.internalServerError(java.lang.String.format("Attempting to complete user subscription to a service for a non existent user. invite-code = %s", inviteEntity.getCode()));
            });
        }).orElseGet(java.util.Optional::empty);
    }
}
