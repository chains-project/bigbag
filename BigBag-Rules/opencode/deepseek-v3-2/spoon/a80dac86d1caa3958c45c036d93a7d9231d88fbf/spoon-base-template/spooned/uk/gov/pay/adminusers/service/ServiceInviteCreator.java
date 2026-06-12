package uk.gov.pay.adminusers.service;
public class ServiceInviteCreator {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.ServiceInviteCreator.class);

    private final uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private final uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao;

    private final uk.gov.pay.adminusers.service.LinksBuilder linksBuilder;

    private final uk.gov.pay.adminusers.app.config.LinksConfig linksConfig;

    private final uk.gov.pay.adminusers.service.NotificationService notificationService;

    private final uk.gov.pay.adminusers.service.PasswordHasher passwordHasher;

    @javax.inject.Inject
    public ServiceInviteCreator(uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao, uk.gov.pay.adminusers.persistence.dao.UserDao userDao, uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao, uk.gov.pay.adminusers.service.LinksBuilder linksBuilder, uk.gov.pay.adminusers.app.config.LinksConfig linksConfig, uk.gov.pay.adminusers.service.NotificationService notificationService, uk.gov.pay.adminusers.service.PasswordHasher passwordHasher) {
        this.inviteDao = inviteDao;
        this.userDao = userDao;
        this.roleDao = roleDao;
        this.linksBuilder = linksBuilder;
        this.linksConfig = linksConfig;
        this.notificationService = notificationService;
        this.passwordHasher = passwordHasher;
    }

    @com.google.inject.persist.Transactional
    public uk.gov.pay.adminusers.model.Invite doInvite(uk.gov.pay.adminusers.model.InviteServiceRequest inviteServiceRequest) {
        java.lang.String requestEmail = inviteServiceRequest.getEmail();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> anExistingUser = userDao.findByEmail(requestEmail);
        if (anExistingUser.isPresent()) {
            uk.gov.pay.adminusers.persistence.entity.UserEntity user = anExistingUser.get();
            if (user.isDisabled()) {
                sendUserDisabledNotification(requestEmail, user.getExternalId());
            } else {
                sendUserExistsNotification(requestEmail, user.getExternalId());
            }
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.conflictingEmail(requestEmail);
        }
        java.util.List<uk.gov.pay.adminusers.persistence.entity.InviteEntity> exitingInvites = inviteDao.findByEmail(requestEmail);
        java.util.List<uk.gov.pay.adminusers.persistence.entity.InviteEntity> existingValidServiceInvitesForSameEmail = exitingInvites.stream().filter(inviteEntity -> (!inviteEntity.isDisabled()) && (!inviteEntity.isExpired())).filter(uk.gov.pay.adminusers.persistence.entity.InviteEntity::isServiceType).collect(java.util.stream.Collectors.toUnmodifiableList());
        if (!existingValidServiceInvitesForSameEmail.isEmpty()) {
            uk.gov.pay.adminusers.persistence.entity.InviteEntity foundInvite = existingValidServiceInvitesForSameEmail.get(0);
            return constructInviteAndSendEmail(inviteServiceRequest, foundInvite, inviteEntity -> {
                inviteDao.merge(inviteEntity);
                return null;
            });
        }
        return roleDao.findByRoleName(inviteServiceRequest.getRoleName()).map(roleEntity -> {
            uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity(requestEmail, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), inviteServiceRequest.getOtpKey(), roleEntity);
            inviteEntity.setType(uk.gov.pay.adminusers.model.InviteType.SERVICE);
            return constructInviteAndSendEmail(inviteServiceRequest, inviteEntity, inviteToPersist -> {
                inviteDao.persist(inviteToPersist);
                return null;
            });
        }).orElseThrow(() -> uk.gov.pay.adminusers.service.AdminUsersExceptions.internalServerError(java.lang.String.format("Role [%s] not a valid role for creating a invite service request", inviteServiceRequest.getRoleName())));
    }

    private uk.gov.pay.adminusers.model.Invite constructInviteAndSendEmail(uk.gov.pay.adminusers.model.InviteServiceRequest inviteServiceRequest, uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity, java.util.function.Function<uk.gov.pay.adminusers.persistence.entity.InviteEntity, java.lang.Void> saveOrUpdate) {
        java.lang.String inviteUrl = java.lang.String.format("%s/%s", linksConfig.getSelfserviceInvitesUrl(), inviteEntity.getCode());
        java.util.Optional.ofNullable(inviteServiceRequest.getTelephoneNumber()).map(uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility::formatToE164).ifPresent(inviteEntity::setTelephoneNumber);
        java.util.Optional.ofNullable(inviteServiceRequest.getPassword()).map(passwordHasher::hash).ifPresent(inviteEntity::setPassword);
        saveOrUpdate.apply(inviteEntity);
        sendServiceInviteNotification(inviteEntity, inviteUrl);
        uk.gov.pay.adminusers.model.Invite invite = inviteEntity.toInvite();
        invite.setInviteLink(inviteUrl);
        return linksBuilder.decorate(invite);
    }

    private void sendServiceInviteNotification(uk.gov.pay.adminusers.persistence.entity.InviteEntity invite, java.lang.String targetUrl) {
        uk.gov.pay.adminusers.service.ServiceInviteCreator.LOGGER.info("New service creation invitation created");
        try {
            java.lang.String notificationId = notificationService.sendServiceInviteEmail(invite.getEmail(), targetUrl);
            uk.gov.pay.adminusers.service.ServiceInviteCreator.LOGGER.info("sent create service invitation email successfully, notification id [{}]", notificationId);
        } catch (java.lang.Exception e) {
            uk.gov.pay.adminusers.service.ServiceInviteCreator.LOGGER.error("error sending create service invitation", e);
        }
    }

    private void sendUserDisabledNotification(java.lang.String email, java.lang.String userExternalId) {
        uk.gov.pay.adminusers.service.ServiceInviteCreator.LOGGER.info("Disabled existing user tried to create a service - user_id={}", userExternalId);
        try {
            java.lang.String notificationId = notificationService.sendServiceInviteUserDisabledEmail(email, linksConfig.getSupportUrl());
            uk.gov.pay.adminusers.service.ServiceInviteCreator.LOGGER.info("sent create service, user account disabled email successfully, notification id [{}]", notificationId);
        } catch (java.lang.Exception e) {
            uk.gov.pay.adminusers.service.ServiceInviteCreator.LOGGER.error("error sending service creation, user account disabled email", e);
        }
    }

    private void sendUserExistsNotification(java.lang.String email, java.lang.String userExternalId) {
        uk.gov.pay.adminusers.service.ServiceInviteCreator.LOGGER.info("Existing user tried to create a service - user_id={}", userExternalId);
        try {
            java.lang.String notificationId = notificationService.sendServiceInviteUserExistsEmail(email, linksConfig.getSelfserviceLoginUrl(), linksConfig.getSelfserviceForgottenPasswordUrl(), linksConfig.getSupportUrl());
            uk.gov.pay.adminusers.service.ServiceInviteCreator.LOGGER.info("sent create service, user exists email successfully, notification id [{}]", notificationId);
        } catch (java.lang.Exception e) {
            uk.gov.pay.adminusers.service.ServiceInviteCreator.LOGGER.error("error sending service creation, users exists email", e);
        }
    }
}
