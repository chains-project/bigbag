package uk.gov.pay.adminusers.service;
public class ServiceOtpDispatcher extends uk.gov.pay.adminusers.service.InviteOtpDispatcher {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.ServiceOtpDispatcher.class);

    private final uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    private final uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator;

    private final uk.gov.pay.adminusers.service.PasswordHasher passwordHasher;

    private final uk.gov.pay.adminusers.service.NotificationService notificationService;

    @com.google.inject.Inject
    public ServiceOtpDispatcher(uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao, uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator, uk.gov.pay.adminusers.service.PasswordHasher passwordHasher, uk.gov.pay.adminusers.service.NotificationService notificationService) {
        super();
        this.inviteDao = inviteDao;
        this.secondFactorAuthenticator = secondFactorAuthenticator;
        this.passwordHasher = passwordHasher;
        this.notificationService = notificationService;
    }

    @java.lang.Override
    public boolean dispatchOtp(java.lang.String inviteCode) {
        return inviteDao.findByCode(inviteCode).map(inviteEntity -> {
            java.util.Optional.ofNullable(inviteOtpRequest.getTelephoneNumber()).map(uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility::formatToE164).ifPresent(inviteEntity::setTelephoneNumber);
            java.util.Optional.ofNullable(inviteOtpRequest.getPassword()).map(passwordHasher::hash).ifPresent(inviteEntity::setPassword);
            inviteDao.merge(inviteEntity);
            int newPassCode = secondFactorAuthenticator.newPassCode(inviteEntity.getOtpKey());
            java.lang.String passcode = java.lang.String.format(java.util.Locale.ENGLISH, uk.gov.pay.adminusers.service.InviteOtpDispatcher.SIX_DIGITS_WITH_LEADING_ZEROS, newPassCode);
            uk.gov.pay.adminusers.service.ServiceOtpDispatcher.LOGGER.info("New 2FA token generated for invite code [{}]", inviteEntity.getCode());
            try {
                java.lang.String notificationId = notificationService.sendSecondFactorPasscodeSms(inviteEntity.getTelephoneNumber(), passcode, uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SELF_INITIATED_CREATE_NEW_USER_AND_SERVICE);
                uk.gov.pay.adminusers.service.ServiceOtpDispatcher.LOGGER.info("sent 2FA token successfully for invite code [{}], notification id [{}]", inviteEntity.getCode(), notificationId);
            } catch (java.lang.Exception e) {
                uk.gov.pay.adminusers.service.ServiceOtpDispatcher.LOGGER.error(java.lang.String.format("error sending 2FA token for invite code [%s]", inviteEntity.getCode()), e);
            }
            return true;
        }).orElseGet(() -> {
            uk.gov.pay.adminusers.service.ServiceOtpDispatcher.LOGGER.error("Unable to locate invite after validating and reaching to the service otp dispatcher. invite code [{}]", inviteCode);
            return false;
        });
    }
}
