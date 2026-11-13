package uk.gov.pay.adminusers.service;
public class UserOtpDispatcher extends uk.gov.pay.adminusers.service.InviteOtpDispatcher {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.UserOtpDispatcher.class);

    private final uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao;

    private final uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator;

    private final uk.gov.pay.adminusers.service.PasswordHasher passwordHasher;

    private final uk.gov.pay.adminusers.service.NotificationService notificationService;

    @com.google.inject.Inject
    public UserOtpDispatcher(uk.gov.pay.adminusers.persistence.dao.InviteDao inviteDao, uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator, uk.gov.pay.adminusers.service.PasswordHasher passwordHasher, uk.gov.pay.adminusers.service.NotificationService notificationService) {
        super();
        this.inviteDao = inviteDao;
        this.secondFactorAuthenticator = secondFactorAuthenticator;
        this.passwordHasher = passwordHasher;
        this.notificationService = notificationService;
    }

    @com.google.inject.persist.Transactional
    @java.lang.Override
    public boolean dispatchOtp(java.lang.String inviteCode) {
        return inviteDao.findByCode(inviteCode).map(inviteEntity -> {
            inviteEntity.setTelephoneNumber(uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.formatToE164(inviteOtpRequest.getTelephoneNumber()));
            inviteEntity.setPassword(passwordHasher.hash(inviteOtpRequest.getPassword()));
            inviteDao.merge(inviteEntity);
            int newPassCode = secondFactorAuthenticator.newPassCode(inviteEntity.getOtpKey());
            java.lang.String passcode = java.lang.String.format(java.util.Locale.ENGLISH, uk.gov.pay.adminusers.service.InviteOtpDispatcher.SIX_DIGITS_WITH_LEADING_ZEROS, newPassCode);
            uk.gov.pay.adminusers.service.UserOtpDispatcher.LOGGER.info("New 2FA token generated for invite code [{}]", inviteCode);
            try {
                java.lang.String notificationId = notificationService.sendSecondFactorPasscodeSms(inviteOtpRequest.getTelephoneNumber(), passcode, uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.CREATE_USER_IN_RESPONSE_TO_INVITATION_TO_SERVICE);
                uk.gov.pay.adminusers.service.UserOtpDispatcher.LOGGER.info("sent 2FA token successfully for invite code [{}], notification id [{}]", inviteCode, notificationId);
            } catch (java.lang.Exception e) {
                uk.gov.pay.adminusers.service.UserOtpDispatcher.LOGGER.info(java.lang.String.format("error sending 2FA token for invite code [%s]", inviteCode), e);
            }
            return true;
        }).orElseGet(() -> {
            uk.gov.pay.adminusers.service.UserOtpDispatcher.LOGGER.info("New 2FA token generated for invite code [{}]", inviteCode);
            return false;
        });
    }
}
