package uk.gov.pay.adminusers.service;
public class UserServices {
    private static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.UserServices.class);

    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private final uk.gov.pay.adminusers.service.PasswordHasher passwordHasher;

    private final uk.gov.pay.adminusers.service.LinksBuilder linksBuilder;

    private final java.lang.Integer loginAttemptCap;

    private final uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator;

    @com.google.inject.Inject
    public UserServices(uk.gov.pay.adminusers.persistence.dao.UserDao userDao, uk.gov.pay.adminusers.service.PasswordHasher passwordHasher, uk.gov.pay.adminusers.service.LinksBuilder linksBuilder, @com.google.inject.name.Named("LOGIN_ATTEMPT_CAP")
    java.lang.Integer loginAttemptCap, com.google.inject.Provider<uk.gov.pay.adminusers.service.NotificationService> userNotificationService, uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator, uk.gov.pay.adminusers.service.ServiceFinder serviceFinder) {
        this.userDao = userDao;
        this.passwordHasher = passwordHasher;
        this.linksBuilder = linksBuilder;
        this.loginAttemptCap = loginAttemptCap;
        this.secondFactorAuthenticator = secondFactorAuthenticator;
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.User> authenticate(java.lang.String username, java.lang.String password) {
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityOptional = userDao.findByUsername(username);
        uk.gov.pay.adminusers.service.UserServices.logger.debug("Login attempt - username={}", username);
        if (userEntityOptional.isPresent()) {
            uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = userEntityOptional.get();
            if (passwordHasher.isEqual(password, userEntity.getPassword())) {
                if (!userEntity.isDisabled()) {
                    userEntity.setLoginCounter(0);
                    userEntity.setUpdatedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));
                    userDao.merge(userEntity);
                }
                uk.gov.pay.adminusers.service.UserServices.logger.info("Successful Login - user_id={}", userEntity.getExternalId());
                return java.util.Optional.of(linksBuilder.decorate(userEntity.toUser()));
            } else {
                userEntity.setLoginCounter(userEntity.getLoginCounter() + 1);
                userEntity.setUpdatedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));
                userEntity.setDisabled(userEntity.getLoginCounter() >= loginAttemptCap);
                uk.gov.pay.adminusers.service.UserServices.logger.info("Failed login attempt - user_id={}, login_counter={}", userEntity.getExternalId(), userEntity.getLoginCounter());
                userDao.merge(userEntity);
                if (userEntity.isDisabled()) {
                    uk.gov.pay.adminusers.service.UserServices.logger.warn("Account locked due to exceeding {} attempts - user_id={}", loginAttemptCap, userEntity.getExternalId());
                }
                return java.util.Optional.empty();
            }
        } else {
            uk.gov.pay.adminusers.service.UserServices.logger.info("Failed login attempt - user_id='Not matched'");
            return java.util.Optional.empty();
        }
    }

    public java.util.Optional<uk.gov.pay.adminusers.model.User> findUserByExternalId(java.lang.String externalId) {
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityOptional = userDao.findByExternalId(externalId);
        return userEntityOptional.map(userEntity -> linksBuilder.decorate(userEntity.toUser()));
    }

    public java.util.List<uk.gov.pay.adminusers.model.User> findUsersByExternalIds(java.util.List<java.lang.String> externalIds) {
        return userDao.findByExternalIds(externalIds).stream().map(uk.gov.pay.adminusers.persistence.entity.UserEntity::toUser).map(linksBuilder::decorate).collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    public java.util.Optional<uk.gov.pay.adminusers.model.User> findUserByUsername(java.lang.String username) {
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntityOptional = userDao.findByUsername(username);
        return userEntityOptional.map(userEntity -> linksBuilder.decorate(userEntity.toUser()));
    }

    public java.util.Map<java.lang.String, java.util.List<java.lang.String>> getAdminUserEmailsForGatewayAccountIds(java.util.List<java.lang.String> gatewayAccountIds) {
        java.util.Map<java.lang.String, java.util.List<java.lang.String>> gatewayAccountIdsToAdminEmails = new java.util.HashMap<>(userDao.getAdminUserEmailsForGatewayAccountIds(gatewayAccountIds));
        gatewayAccountIds.forEach(gatewayAccountId -> gatewayAccountIdsToAdminEmails.putIfAbsent(gatewayAccountId, java.util.List.of()));
        return java.util.Map.copyOf(gatewayAccountIdsToAdminEmails);
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.User> authenticateSecondFactor(java.lang.String externalId, int code) {
        uk.gov.pay.adminusers.service.UserServices.logger.debug("OTP attempt - user_id={}", externalId);
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));
        return userDao.findByExternalId(externalId).map(userEntity -> {
            if (userEntity.isDisabled()) {
                uk.gov.pay.adminusers.service.UserServices.logger.warn("Failed OTP attempt - user_id={}, login_counter={}. Authenticate Second Factor attempted for a disabled User", userEntity.getExternalId(), userEntity.getLoginCounter());
                return java.util.Optional.<uk.gov.pay.adminusers.model.User>empty();
            }
            if (secondFactorAuthenticator.authorize(userEntity.getOtpKey(), code)) {
                userEntity.setLoginCounter(0);
                userEntity.setUpdatedAt(now);
                userEntity.setLastLoggedInAt(now);
                userDao.merge(userEntity);
                uk.gov.pay.adminusers.service.UserServices.logger.info("Successful OTP. user_id={}", userEntity.getExternalId());
                return java.util.Optional.of(linksBuilder.decorate(userEntity.toUser()));
            } else {
                userEntity.setLoginCounter(userEntity.getLoginCounter() + 1);
                userEntity.setUpdatedAt(now);
                userEntity.setDisabled(userEntity.getLoginCounter() > loginAttemptCap);
                userDao.merge(userEntity);
                if (userEntity.isDisabled()) {
                    uk.gov.pay.adminusers.service.UserServices.logger.warn("Failed OTP attempt - user_id={}, login_counter={}. Invalid second factor in an account currently locked", userEntity.getExternalId(), userEntity.getLoginCounter());
                } else {
                    uk.gov.pay.adminusers.service.UserServices.logger.info("Failed OTP attempt - user_id={}, login_counter={}. Invalid second factor attempt.", userEntity.getExternalId(), userEntity.getLoginCounter());
                }
                return java.util.Optional.<uk.gov.pay.adminusers.model.User>empty();
            }
        }).orElseGet(() -> {
            // this cannot happen unless a bug in selfservice
            uk.gov.pay.adminusers.service.UserServices.logger.error("Authenticate 2FA token attempted for non-existent User [{}]", externalId);
            return java.util.Optional.empty();
        });
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.User> provisionNewOtpKey(java.lang.String externalId) {
        return userDao.findByExternalId(externalId).map(userEntity -> {
            if (userEntity.isDisabled()) {
                uk.gov.pay.adminusers.service.UserServices.logger.warn("Attempt to provision a new OTP key for disabled user {}", userEntity.getExternalId());
                return java.util.Optional.<uk.gov.pay.adminusers.model.User>empty();
            }
            uk.gov.pay.adminusers.service.UserServices.logger.info("Provisioning new OTP key for user {}", userEntity.getExternalId());
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));
            userEntity.setProvisionalOtpKey(secondFactorAuthenticator.generateNewBase32EncodedSecret());
            userEntity.setProvisionalOtpKeyCreatedAt(now);
            userEntity.setUpdatedAt(now);
            userDao.merge(userEntity);
            return java.util.Optional.of(linksBuilder.decorate(userEntity.toUser()));
        }).orElseGet(() -> {
            uk.gov.pay.adminusers.service.UserServices.logger.error("Attempt to provision a new OTP key for a non-existent user {}", externalId);
            return java.util.Optional.empty();
        });
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.User> activateNewOtpKey(java.lang.String externalId, uk.gov.pay.adminusers.model.SecondFactorMethod secondFactor, int code) {
        return userDao.findByExternalId(externalId).map(userEntity -> {
            if (userEntity.isDisabled()) {
                uk.gov.pay.adminusers.service.UserServices.logger.error("Attempt to activate a new OTP key for disabled user {}", userEntity.getExternalId());
                return java.util.Optional.<uk.gov.pay.adminusers.model.User>empty();
            }
            if (userEntity.getProvisionalOtpKey() == null) {
                uk.gov.pay.adminusers.service.UserServices.logger.error("Attempt to activate a new OTP key for user {} without a provisional one", userEntity.getExternalId());
                return java.util.Optional.<uk.gov.pay.adminusers.model.User>empty();
            }
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));
            java.time.ZonedDateTime provisionalOtpKeyCreatedAt = userEntity.getProvisionalOtpKeyCreatedAt();
            if ((provisionalOtpKeyCreatedAt == null) || provisionalOtpKeyCreatedAt.plusMinutes(90).isBefore(now)) {
                uk.gov.pay.adminusers.service.UserServices.logger.warn("Attempt to activate a new OTP key for user {} but provisional one was created too long ago at {}", userEntity.getExternalId(), provisionalOtpKeyCreatedAt);
                return java.util.Optional.<uk.gov.pay.adminusers.model.User>empty();
            }
            if (!secondFactorAuthenticator.authorize(userEntity.getProvisionalOtpKey(), code)) {
                uk.gov.pay.adminusers.service.UserServices.logger.info("Attempt to activate a new OTP key for user {} with incorrect code", userEntity.getExternalId());
                return java.util.Optional.<uk.gov.pay.adminusers.model.User>empty();
            }
            uk.gov.pay.adminusers.service.UserServices.logger.info("Activating new OTP key and method {} for user {}", secondFactor.toString(), userEntity.getExternalId());
            userEntity.setOtpKey(userEntity.getProvisionalOtpKey());
            userEntity.setSecondFactor(secondFactor);
            userEntity.setProvisionalOtpKey(null);
            userEntity.setProvisionalOtpKeyCreatedAt(null);
            userEntity.setUpdatedAt(now);
            userDao.merge(userEntity);
            return java.util.Optional.of(linksBuilder.decorate(userEntity.toUser()));
        }).orElseGet(() -> {
            uk.gov.pay.adminusers.service.UserServices.logger.error("Attempt to activate a new OTP key for a non-existent user {}", externalId);
            return java.util.Optional.empty();
        });
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.User> resetSecondFactor(java.lang.String externalId) {
        return userDao.findByExternalId(externalId).map(userEntity -> {
            if (userEntity.getSecondFactor().equals(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS)) {
                uk.gov.pay.adminusers.service.UserServices.logger.info("Second factor method is already SMS, doing nothing");
                return linksBuilder.decorate(userEntity.toUser());
            }
            uk.gov.pay.adminusers.service.UserServices.logger.info("Resetting OTP method to SMS for user {}", userEntity.getExternalId());
            userEntity.setOtpKey(secondFactorAuthenticator.generateNewBase32EncodedSecret());
            userEntity.setUpdatedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));
            userEntity.setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS);
            userDao.merge(userEntity);
            return linksBuilder.decorate(userEntity.toUser());
        });
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.User> patchUser(java.lang.String externalId, uk.gov.pay.adminusers.model.PatchRequest patchRequest) {
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userOptional = userDao.findByExternalId(externalId);
        if (userOptional.isEmpty()) {
            return java.util.Optional.empty();
        }
        uk.gov.pay.adminusers.persistence.entity.UserEntity user = userOptional.get();
        switch (patchRequest.getPath()) {
            case uk.gov.pay.adminusers.model.PatchRequest.PATH_SESSION_VERSION :
                incrementSessionVersion(user, java.lang.Integer.parseInt(patchRequest.getValue()));
                break;
            case uk.gov.pay.adminusers.model.PatchRequest.PATH_DISABLED :
                changeUserDisabled(user, java.lang.Boolean.parseBoolean(patchRequest.getValue()));
                break;
            case uk.gov.pay.adminusers.model.PatchRequest.PATH_TELEPHONE_NUMBER :
                changeUserTelephoneNumber(user, patchRequest.getValue());
                break;
            case uk.gov.pay.adminusers.model.PatchRequest.PATH_EMAIL :
                changeUserEmail(user, patchRequest.getValue());
                break;
            case uk.gov.pay.adminusers.model.PatchRequest.PATH_FEATURES :
                changeUserFeatures(user, patchRequest.getValue());
                break;
            default :
                java.lang.String error = java.lang.String.format("Invalid patch request with path [%s]", patchRequest.getPath());
                uk.gov.pay.adminusers.service.UserServices.logger.error(error);
                throw new java.lang.RuntimeException(error);
        }
        return java.util.Optional.of(linksBuilder.decorate(user.toUser()));
    }

    public java.util.List<uk.gov.pay.adminusers.persistence.entity.UserEntity> getAdminUsersForService(java.lang.Integer serviceId) {
        java.util.List<uk.gov.pay.adminusers.persistence.entity.UserEntity> serviceUsers = userDao.findByServiceId(serviceId);
        return serviceUsers.stream().filter(userEntity -> {
            var hasAdminRole = userEntity.getRoles().stream().filter(uk.gov.pay.adminusers.persistence.entity.RoleEntity::isAdmin).count();
            return hasAdminRole > 0;
        }).collect(java.util.stream.Collectors.toList());
    }

    private void changeUserFeatures(uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity, java.lang.String features) {
        userEntity.setFeatures(features);
        userEntity.setUpdatedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));
        userDao.merge(userEntity);
    }

    private void changeUserTelephoneNumber(uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity, java.lang.String telephoneNumber) {
        userEntity.setTelephoneNumber(uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.formatToE164(telephoneNumber));
        userEntity.setUpdatedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));
        userDao.merge(userEntity);
    }

    private void changeUserEmail(uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity, java.lang.String email) {
        userEntity.setEmail(email);
        userEntity.setUsername(email);
        userEntity.setUpdatedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));
        userDao.merge(userEntity);
    }

    private void changeUserDisabled(uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity, java.lang.Boolean value) {
        userEntity.setLoginCounter(0);
        userEntity.setDisabled(value);
        userEntity.setUpdatedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));
        userDao.merge(userEntity);
    }

    private void incrementSessionVersion(uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity, java.lang.Integer value) {
        userEntity.setSessionVersion(userEntity.getSessionVersion() + value);
        userEntity.setUpdatedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));
        userDao.merge(userEntity);
    }
}
