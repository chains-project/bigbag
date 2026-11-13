package uk.gov.pay.adminusers.service;
public class UserInviteCreator {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.UserInviteCreator.class);

    private final uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private final uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao;

    private final uk.gov.pay.adminusers.app.config.LinksConfig linksConfig;

    private final uk.gov.pay.adminusers.service.NotificationService notificationService;

    private final uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    @com.google.inject.Inject
    public UserInviteCreator(uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao, uk.gov.pay.adminusers.persistence.dao.UserDao userDao, uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao, uk.gov.pay.adminusers.app.config.LinksConfig linksConfig, uk.gov.pay.adminusers.service.NotificationService notificationService, uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao) {
        this.inviteDao = inviteDao;
        this.userDao = userDao;
        this.roleDao = roleDao;
        this.linksConfig = linksConfig;
        this.notificationService = notificationService;
        this.serviceDao = serviceDao;
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.Invite> doInvite(uk.gov.pay.adminusers.model.InviteUserRequest inviteUserRequest) {
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> serviceEntityOptional = serviceDao.findByExternalId(inviteUserRequest.getServiceExternalId());
        if (!serviceEntityOptional.isPresent()) {
            return java.util.Optional.empty();
        }
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> existingUser = userDao.findByEmail(inviteUserRequest.getEmail());
        existingUser.ifPresent(userEntity -> {
            if (userEntity.getServicesRole(inviteUserRequest.getServiceExternalId()).isPresent()) {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.userAlreadyInService(userEntity.getExternalId(), inviteUserRequest.getServiceExternalId());
            }
        });
        java.util.List<uk.gov.pay.adminusers.persistence.entity.InviteEntity> existingInvites = inviteDao.findByEmail(inviteUserRequest.getEmail());
        java.util.List<uk.gov.pay.adminusers.persistence.entity.InviteEntity> validInvitesToTheSameService = existingInvites.stream().filter(inviteEntity -> (!inviteEntity.isDisabled()) && (!inviteEntity.isExpired())).filter(inviteEntity -> (inviteEntity.getService() != null) && inviteUserRequest.getServiceExternalId().equals(inviteEntity.getService().getExternalId())).collect(java.util.stream.Collectors.toUnmodifiableList());
        if (!validInvitesToTheSameService.isEmpty()) {
            uk.gov.pay.adminusers.persistence.entity.InviteEntity existingInvite = validInvitesToTheSameService.get(0);
            if (inviteUserRequest.getSender().equals(existingInvite.getSender().getExternalId())) {
                java.lang.String inviteUrl = javax.ws.rs.core.UriBuilder.fromUri(linksConfig.getSelfserviceInvitesUrl()).path(existingInvite.getCode()).build().toString();
                sendUserInviteNotification(existingInvite, inviteUrl, existingInvite.getService(), existingUser);
                uk.gov.pay.adminusers.model.Invite invite = existingInvite.toInvite();
                invite.setInviteLink(inviteUrl);
                return java.util.Optional.of(invite);
            } else {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.conflictingInvite(inviteUserRequest.getEmail());
            }
        }
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = serviceEntityOptional.get();
        return roleDao.findByRoleName(inviteUserRequest.getRoleName()).map(role -> {
            java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userSender = userDao.findByExternalId(inviteUserRequest.getSender());
            if (userSender.isPresent() && userSender.get().canInviteUsersTo(serviceEntity.getId())) {
                uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(inviteUserRequest.getEmail(), uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), inviteUserRequest.getOtpKey(), role);
                inviteEntity.setSender(userSender.get());
                inviteEntity.setService(serviceEntity);
                inviteEntity.setType(uk.gov.pay.adminusers.model.InviteType.USER);
                inviteDao.persist(inviteEntity);
                java.lang.String inviteUrl = javax.ws.rs.core.UriBuilder.fromUri(linksConfig.getSelfserviceInvitesUrl()).path(inviteEntity.getCode()).build().toString();
                sendUserInviteNotification(inviteEntity, inviteUrl, serviceEntity, existingUser);
                uk.gov.pay.adminusers.model.Invite invite = inviteEntity.toInvite();
                invite.setInviteLink(inviteUrl);
                return java.util.Optional.of(invite);
            } else {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.forbiddenOperationException(inviteUserRequest.getSender(), "invite", serviceEntity.getExternalId());
            }
        }).orElseThrow(() -> uk.gov.pay.adminusers.service.AdminUsersExceptions.undefinedRoleException(inviteUserRequest.getRoleName()));
    }

    private void sendUserInviteNotification(uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity, java.lang.String inviteUrl, uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity, java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> existingUser) {
        uk.gov.pay.adminusers.persistence.entity.UserEntity sender = inviteEntity.getSender();
        uk.gov.pay.adminusers.service.UserInviteCreator.LOGGER.info("New invite created by User [{}]", sender.getExternalId());
        try {
            java.lang.String notificationId;
            if (existingUser.isPresent()) {
                java.lang.String serviceName = serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName();
                notificationId = notificationService.sendInviteExistingUserEmail(inviteEntity.getSender().getEmail(), inviteEntity.getEmail(), inviteUrl, serviceName);
            } else {
                notificationId = notificationService.sendInviteEmail(inviteEntity.getSender().getEmail(), inviteEntity.getEmail(), inviteUrl);
            }
            uk.gov.pay.adminusers.service.UserInviteCreator.LOGGER.info("sent invite email successfully by user [{}], notification id [{}]", sender.getExternalId(), notificationId);
        } catch (java.lang.Exception e) {
            uk.gov.pay.adminusers.service.UserInviteCreator.LOGGER.error(java.lang.String.format("error sending email by user [%s]", sender.getExternalId()), e);
        }
    }
}
