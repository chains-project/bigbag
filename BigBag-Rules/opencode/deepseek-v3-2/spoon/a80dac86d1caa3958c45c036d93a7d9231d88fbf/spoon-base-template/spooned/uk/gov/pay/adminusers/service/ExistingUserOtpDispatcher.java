package uk.gov.pay.adminusers.service;
public class ExistingUserOtpDispatcher {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher.class);

    private final uk.gov.pay.adminusers.service.NotificationService notificationService;

    private final uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator;

    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    @javax.inject.Inject
    public ExistingUserOtpDispatcher(com.google.inject.Provider<uk.gov.pay.adminusers.service.NotificationService> notificationService, uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator, uk.gov.pay.adminusers.persistence.dao.UserDao userDao) {
        this.notificationService = notificationService.get();
        this.secondFactorAuthenticator = secondFactorAuthenticator;
        this.userDao = userDao;
    }

    public java.util.Optional<uk.gov.pay.adminusers.model.SecondFactorToken> sendSignInOtp(java.lang.String externalId) {
        return sendOtp(externalId, false);
    }

    public java.util.Optional<uk.gov.pay.adminusers.model.SecondFactorToken> sendChangeSignMethodToSmsOtp(java.lang.String externalId) {
        return sendOtp(externalId, true);
    }

    private java.util.Optional<uk.gov.pay.adminusers.model.SecondFactorToken> sendOtp(java.lang.String externalId, boolean changingSignInMethodToSms) {
        return userDao.findByExternalId(externalId).map(userEntity -> {
            java.lang.String otpKeyOrProvisionalOtpKey = (changingSignInMethodToSms) ? userEntity.getProvisionalOtpKey() : userEntity.getOtpKey();
            return java.util.Optional.ofNullable(otpKeyOrProvisionalOtpKey).map(otpKey -> {
                int newPassCode = secondFactorAuthenticator.newPassCode(otpKey);
                uk.gov.pay.adminusers.model.SecondFactorToken token = uk.gov.pay.adminusers.model.SecondFactorToken.from(externalId, newPassCode);
                java.lang.String userExternalId = userEntity.getExternalId();
                uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId notifyTemplateId = (changingSignInMethodToSms) ? uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.CHANGE_SIGN_IN_2FA_TO_SMS : uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SIGN_IN;
                try {
                    java.lang.String notificationId = notificationService.sendSecondFactorPasscodeSms(userEntity.getTelephoneNumber(), token.getPasscode(), notifyTemplateId);
                    uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher.LOGGER.info("sent 2FA token successfully to user [{}], notification id [{}]", userExternalId, notificationId);
                } catch (java.lang.Exception e) {
                    uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher.LOGGER.error("error sending 2FA token to user [{}]", userExternalId, e);
                }
                if (changingSignInMethodToSms) {
                    uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher.LOGGER.info("New 2FA token generated for User [{}] from provisional OTP key", userExternalId);
                } else {
                    uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher.LOGGER.info("New 2FA token generated for User [{}]", userExternalId);
                }
                return java.util.Optional.of(token);
            }).orElseGet(() -> {
                if (changingSignInMethodToSms) {
                    uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher.LOGGER.error("New provisional 2FA token attempted for user without a provisional OTP key [{}]", externalId);
                } else {
                    // Realistically, this will never happen
                    uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher.LOGGER.error("New 2FA token attempted for user without an OTP key [{}]", externalId);
                }
                return java.util.Optional.empty();
            });
        }).orElseGet(() -> {
            // this cannot happen unless a bug in selfservice
            uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher.LOGGER.error("New 2FA token attempted for non-existent User [{}]", externalId);
            return java.util.Optional.empty();
        });
    }
}
