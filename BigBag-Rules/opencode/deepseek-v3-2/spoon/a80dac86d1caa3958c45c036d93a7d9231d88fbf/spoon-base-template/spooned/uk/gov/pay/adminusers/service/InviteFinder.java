package uk.gov.pay.adminusers.service;
public class InviteFinder {
    private final uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    @com.google.inject.Inject
    public InviteFinder(uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao, uk.gov.pay.adminusers.persistence.dao.UserDao userDao) {
        this.inviteDao = inviteDao;
        this.userDao = userDao;
    }

    public java.util.Optional<uk.gov.pay.adminusers.model.Invite> find(java.lang.String code) {
        return inviteDao.findByCode(code).map(inviteEntity -> {
            if (inviteEntity.isExpired() || inviteEntity.isDisabled()) {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.inviteLockedException(inviteEntity.getCode());
            }
            uk.gov.pay.adminusers.model.Invite invite = inviteEntity.toInvite();
            return userDao.findByEmail(inviteEntity.getEmail()).map(userEntity -> {
                invite.setUserExist(true);
                return java.util.Optional.of(invite);
            }).orElse(java.util.Optional.of(invite));
        }).orElseGet(java.util.Optional::empty);
    }

    public java.util.List<uk.gov.pay.adminusers.model.Invite> findAllActiveInvites(java.lang.String serviceId) {
        return inviteDao.findAllByServiceId(serviceId).stream().filter(inviteEntity -> !inviteEntity.isDisabled()).filter(inviteEntity -> !inviteEntity.isExpired()).map(inviteEntity -> {
            uk.gov.pay.adminusers.model.Invite invite = inviteEntity.toInvite();
            return userDao.findByEmail(inviteEntity.getEmail()).map(userEntity -> {
                invite.setUserExist(true);
                return invite;
            }).orElse(invite);
        }).collect(java.util.stream.Collectors.toUnmodifiableList());
    }
}
