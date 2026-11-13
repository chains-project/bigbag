package uk.gov.pay.adminusers.service;
public class ServiceInviteCompleter extends uk.gov.pay.adminusers.service.InviteCompleter {
    private final uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private final uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private final uk.gov.pay.adminusers.service.LinksBuilder linksBuilder;

    @com.google.inject.Inject
    public ServiceInviteCompleter(uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao, uk.gov.pay.adminusers.persistence.dao.UserDao userDao, uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao, uk.gov.pay.adminusers.service.LinksBuilder linksBuilder) {
        super();
        this.inviteDao = inviteDao;
        this.userDao = userDao;
        this.serviceDao = serviceDao;
        this.linksBuilder = linksBuilder;
    }

    /**
     * Completes a service invite.
     * ie. it creates and persists a user from an invite or/and subscribe a user to an existing service
     * and if it is a service invite also creates a default service.
     * It then disables the invite.
     */
    @java.lang.Override
    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.InviteCompleteResponse> complete(java.lang.String inviteCode) {
        return inviteDao.findByCode(inviteCode).map(inviteEntity -> {
            if (inviteEntity.isExpired() || inviteEntity.isDisabled()) {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.inviteLockedException(inviteEntity.getCode());
            }
            if (userDao.findByEmail(inviteEntity.getEmail()).isPresent()) {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.conflictingEmail(inviteEntity.getEmail());
            }
            if (inviteEntity.isServiceType()) {
                uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = inviteEntity.mapToUserEntity();
                uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntity.from(uk.gov.pay.adminusers.model.Service.from());
                if (!data.getGatewayAccountIds().isEmpty()) {
                    serviceEntity.addGatewayAccountIds(data.getGatewayAccountIds().toArray(new java.lang.String[0]));
                }
                serviceDao.persist(serviceEntity);
                uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRoleEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(serviceEntity, inviteEntity.getRole());
                userEntity.addServiceRole(serviceRoleEntity);
                userDao.merge(userEntity);
                inviteEntity.setService(serviceEntity);
                inviteEntity.setDisabled(true);
                inviteDao.merge(inviteEntity);
                uk.gov.pay.adminusers.model.Invite invite = linksBuilder.addUserLink(userEntity.toUser(), inviteEntity.toInvite());
                uk.gov.pay.adminusers.model.InviteCompleteResponse response = new uk.gov.pay.adminusers.model.InviteCompleteResponse(invite);
                response.setServiceExternalId(serviceEntity.getExternalId());
                response.setUserExternalId(userEntity.getExternalId());
                return response;
            } else {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.internalServerError(java.lang.String.format("Attempting to complete a service invite for a non service invite of type. invite-code = %s", inviteEntity.getCode()));
            }
        });
    }
}
