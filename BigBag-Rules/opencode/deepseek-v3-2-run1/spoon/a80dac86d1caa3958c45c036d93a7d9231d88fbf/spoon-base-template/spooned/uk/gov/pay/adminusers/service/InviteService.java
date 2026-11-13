package uk.gov.pay.adminusers.service;
public class InviteService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.InviteService.class);

    private static final java.lang.String SIX_DIGITS_WITH_LEADING_ZEROS = "%06d";

    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private final uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    private final uk.gov.pay.adminusers.service.NotificationService notificationService;

    private final uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator;

    private final uk.gov.pay.adminusers.service.LinksBuilder linksBuilder;

    private final java.lang.Integer loginAttemptCap;

    @javax.inject.Inject
    public InviteService(uk.gov.pay.adminusers.persistence.dao.UserDao userDao, uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao, uk.gov.pay.adminusers.service.NotificationService notificationService, uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator, uk.gov.pay.adminusers.service.LinksBuilder linksBuilder, @com.google.inject.name.Named("LOGIN_ATTEMPT_CAP")
    java.lang.Integer loginAttemptCap) {
        this.userDao = userDao;
        this.inviteDao = inviteDao;
        this.notificationService = notificationService;
        this.secondFactorAuthenticator = secondFactorAuthenticator;
        this.linksBuilder = linksBuilder;
        this.loginAttemptCap = loginAttemptCap;
    }

    // Refactor to adopt UserOtpDispatcher. And Avoid using generic InviteOtpRequest object to avoid having to use optional fields
    @java.lang.Deprecated
    @com.google.inject.persist.Transactional
    public void reGenerateOtp(uk.gov.pay.adminusers.model.InviteOtpRequest inviteOtpRequest) {
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.InviteEntity> inviteOptional = inviteDao.findByCode(inviteOtpRequest.getCode());
        if (inviteOptional.isPresent()) {
            uk.gov.pay.adminusers.persistence.entity.InviteEntity invite = inviteOptional.get();
            invite.setTelephoneNumber(uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.formatToE164(inviteOtpRequest.getTelephoneNumber()));
            inviteDao.merge(invite);
            int newPassCode = secondFactorAuthenticator.newPassCode(invite.getOtpKey());
            java.lang.String passcode = java.lang.String.format(java.util.Locale.ENGLISH, uk.gov.pay.adminusers.service.InviteService.SIX_DIGITS_WITH_LEADING_ZEROS, newPassCode);
            uk.gov.pay.adminusers.service.InviteService.LOGGER.info("New 2FA token generated for invite code [{}]", inviteOtpRequest.getCode());
            try {
                java.lang.String notificationId = notificationService.sendSecondFactorPasscodeSms(inviteOtpRequest.getTelephoneNumber(), passcode, uk.gov.pay.adminusers.service.InviteService.mapInviteTypeToOtpNotifySmsTemplateId(invite.getType()));
                uk.gov.pay.adminusers.service.InviteService.LOGGER.info("sent 2FA token successfully for invite code [{}], notification id [{}]", inviteOtpRequest.getCode(), notificationId);
            } catch (java.lang.Exception e) {
                uk.gov.pay.adminusers.service.InviteService.LOGGER.error(java.lang.String.format("error sending 2FA token for invite code [%s]", inviteOtpRequest.getCode()), e);
            }
        } else {
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.notFoundInviteException(inviteOtpRequest.getCode());
        }
    }

    @com.google.inject.persist.Transactional
    public uk.gov.pay.adminusers.service.ValidateOtpAndCreateUserResult validateOtpAndCreateUser(uk.gov.pay.adminusers.model.InviteValidateOtpRequest inviteValidateOtpRequest) {
        return inviteDao.findByCode(inviteValidateOtpRequest.getCode()).map(inviteEntity -> validateOtp(inviteEntity, inviteValidateOtpRequest.getOtpCode()).map(uk.gov.pay.adminusers.service.ValidateOtpAndCreateUserResult::new).orElseGet(() -> {
            inviteEntity.setLoginCounter(0);
            uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = inviteEntity.mapToUserEntity();
            userDao.persist(userEntity);
            inviteEntity.setDisabled(java.lang.Boolean.TRUE);
            inviteDao.merge(inviteEntity);
            return new uk.gov.pay.adminusers.service.ValidateOtpAndCreateUserResult(linksBuilder.decorate(userEntity.toUser()));
        })).orElseGet(() -> new uk.gov.pay.adminusers.service.ValidateOtpAndCreateUserResult(uk.gov.pay.adminusers.service.AdminUsersExceptions.notFoundInviteException(inviteValidateOtpRequest.getCode())));
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<javax.ws.rs.WebApplicationException> validateOtp(uk.gov.pay.adminusers.model.InviteValidateOtpRequest inviteOtpRequest) {
        return inviteDao.findByCode(inviteOtpRequest.getCode()).map(inviteEntity -> validateOtp(inviteEntity, inviteOtpRequest.getOtpCode())).orElseGet(() -> java.util.Optional.of(uk.gov.pay.adminusers.service.AdminUsersExceptions.notFoundInviteException(inviteOtpRequest.getCode())));
    }

    /* default */
    java.util.Optional<javax.ws.rs.WebApplicationException> validateOtp(uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity, int otpCode) {
        if (inviteEntity.isDisabled()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.service.AdminUsersExceptions.inviteLockedException(inviteEntity.getCode()));
        }
        if (!secondFactorAuthenticator.authorize(inviteEntity.getOtpKey(), otpCode)) {
            inviteEntity.setLoginCounter(inviteEntity.getLoginCounter() + 1);
            inviteEntity.setDisabled(inviteEntity.getLoginCounter() >= loginAttemptCap);
            inviteDao.merge(inviteEntity);
            if (inviteEntity.isDisabled()) {
                return java.util.Optional.of(uk.gov.pay.adminusers.service.AdminUsersExceptions.inviteLockedException(inviteEntity.getCode()));
            }
            return java.util.Optional.of(uk.gov.pay.adminusers.service.AdminUsersExceptions.invalidOtpAuthCodeInviteException(inviteEntity.getCode()));
        }
        return java.util.Optional.empty();
    }

    private static uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId mapInviteTypeToOtpNotifySmsTemplateId(uk.gov.pay.adminusers.model.InviteType inviteType) {
        switch (inviteType) {
            case SERVICE :
                return uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SELF_INITIATED_CREATE_NEW_USER_AND_SERVICE;
            case USER :
                return uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.CREATE_USER_IN_RESPONSE_TO_INVITATION_TO_SERVICE;
            default :
                throw new java.lang.IllegalArgumentException("Unrecognised InviteType: " + inviteType.name());
        }
    }
}
