package uk.gov.pay.adminusers.service;
public class NotificationService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.NotificationService.class);

    private final uk.gov.pay.adminusers.service.NotifyClientProvider notifyClientProvider;

    private final com.codahale.metrics.MetricRegistry metricRegistry;

    private final uk.gov.pay.adminusers.app.config.NotifyConfiguration notifyConfiguration;

    private final uk.gov.pay.adminusers.app.config.NotifyDirectDebitConfiguration notifyDirectDebitConfiguration;

    private final java.lang.String signInOtpSmsTemplateId;

    private final java.lang.String changeSignIn2faToSmsOtpSmsTemplateId;

    private final java.lang.String selfInitiatedCreateUserAndServiceOtpSmsTemplateId;

    private final java.lang.String createUserInResponseToInvitationToServiceOtpSmsTemplateId;

    private final java.lang.String inviteEmailTemplateId;

    private final java.lang.String forgottenPasswordEmailTemplateId;

    private final java.lang.String inviteExistingUserEmailTemplateId;

    private final java.lang.String stripeDisputeCreatedEmailTemplateId;

    private final java.lang.String stripeDisputeLostEmailTemplateId;

    private final java.lang.String stripeDisputeLostAndServiceChargedEmailTemplateId;

    private final java.lang.String stripeDisputeEvidenceSubmittedEmailTemplateId;

    private final java.lang.String stripeDisputeWonEmailTemplateId;

    private final java.lang.String notifyEmailReplyToSupportId;

    public NotificationService(uk.gov.pay.adminusers.service.NotifyClientProvider notifyClientProvider, uk.gov.pay.adminusers.app.config.NotifyConfiguration notifyConfiguration, uk.gov.pay.adminusers.app.config.NotifyDirectDebitConfiguration notifyDirectDebitConfiguration, com.codahale.metrics.MetricRegistry metricRegistry) {
        this.notifyClientProvider = notifyClientProvider;
        this.notifyConfiguration = notifyConfiguration;
        this.notifyDirectDebitConfiguration = notifyDirectDebitConfiguration;
        this.signInOtpSmsTemplateId = notifyConfiguration.getSignInOtpSmsTemplateId();
        this.changeSignIn2faToSmsOtpSmsTemplateId = notifyConfiguration.getChangeSignIn2faToSmsOtpSmsTemplateId();
        this.selfInitiatedCreateUserAndServiceOtpSmsTemplateId = notifyConfiguration.getSelfInitiatedCreateUserAndServiceOtpSmsTemplateId();
        this.createUserInResponseToInvitationToServiceOtpSmsTemplateId = notifyConfiguration.getCreateUserInResponseToInvitationToServiceOtpSmsTemplateId();
        this.inviteEmailTemplateId = notifyConfiguration.getInviteUserEmailTemplateId();
        this.inviteExistingUserEmailTemplateId = notifyConfiguration.getInviteUserExistingEmailTemplateId();
        this.forgottenPasswordEmailTemplateId = notifyConfiguration.getForgottenPasswordEmailTemplateId();
        this.stripeDisputeCreatedEmailTemplateId = notifyConfiguration.getStripeDisputeCreatedEmailTemplateId();
        this.stripeDisputeLostEmailTemplateId = notifyConfiguration.getStripeDisputeLostEmailTemplateId();
        this.stripeDisputeLostAndServiceChargedEmailTemplateId = notifyConfiguration.getStripeDisputeLostAndServiceChargedEmailTemplateId();
        this.stripeDisputeEvidenceSubmittedEmailTemplateId = notifyConfiguration.getStripeDisputeEvidenceSubmittedEmailTemplateId();
        this.stripeDisputeWonEmailTemplateId = notifyConfiguration.getStripeDisputeWonEmailTemplateId();
        this.notifyEmailReplyToSupportId = notifyConfiguration.getNotifyEmailReplyToSupportId();
        this.metricRegistry = metricRegistry;
    }

    public uk.gov.pay.adminusers.app.config.NotifyDirectDebitConfiguration getNotifyDirectDebitConfiguration() {
        return notifyDirectDebitConfiguration;
    }

    public java.lang.String sendSecondFactorPasscodeSms(java.lang.String phoneNumber, java.lang.String passcode, uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId otpNotifySmsTemplateId) {
        com.google.common.base.Stopwatch responseTimeStopwatch = com.google.common.base.Stopwatch.createStarted();
        try {
            uk.gov.service.notify.SendSmsResponse response = notifyClientProvider.get().sendSms(resolveOtpNotifySmsTemplateId(otpNotifySmsTemplateId), uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.formatToE164(phoneNumber), java.util.Map.of("code", passcode), null);
            return response.getNotificationId().toString();
        } catch (uk.gov.service.notify.NotificationClientException e) {
            metricRegistry.counter("notify-operations.sms.failures").inc();
            uk.gov.pay.adminusers.service.NotificationService.LOGGER.info("Error sending Sms: " + e.getMessage());
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.userNotificationError(e);
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram("notify-operations.sms.response_time").update(responseTimeStopwatch.elapsed(java.util.concurrent.TimeUnit.MILLISECONDS));
        }
    }

    public java.lang.String sendInviteEmail(java.lang.String sender, java.lang.String email, java.lang.String inviteUrl) {
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("username", sender, "link", inviteUrl);
        return sendEmail(inviteEmailTemplateId, email, personalisation);
    }

    public java.lang.String sendServiceInviteEmail(java.lang.String email, java.lang.String inviteUrl) {
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("name", email, "link", inviteUrl);
        return sendEmail(notifyConfiguration.getInviteServiceEmailTemplateId(), email, personalisation);
    }

    public java.lang.String sendForgottenPasswordEmail(java.lang.String email, java.lang.String forgottenPasswordUrl) {
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("code", forgottenPasswordUrl);
        return sendEmail(forgottenPasswordEmailTemplateId, email, personalisation);
    }

    public java.lang.String sendServiceInviteUserExistsEmail(java.lang.String email, java.lang.String signInLink, java.lang.String forgottenPasswordLink, java.lang.String feedbackLink) {
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("signin_link", signInLink, "forgotten_password_link", forgottenPasswordLink, "feedback_link", feedbackLink);
        return sendEmail(notifyConfiguration.getInviteServiceUserExistsEmailTemplateId(), email, personalisation);
    }

    public java.lang.String sendServiceInviteUserDisabledEmail(java.lang.String email, java.lang.String supportUrl) {
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("feedback_link", supportUrl);
        return sendEmail(notifyConfiguration.getInviteServiceUserDisabledEmailTemplateId(), email, personalisation);
    }

    public java.lang.String sendInviteExistingUserEmail(java.lang.String sender, java.lang.String email, java.lang.String inviteUrl, java.lang.String serviceName) {
        java.lang.String collaborateServiceNamePart;
        java.lang.String joinServiceNamePart;
        if (serviceName.equals(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE)) {
            collaborateServiceNamePart = "join a new service";
            joinServiceNamePart = "";
        } else {
            collaborateServiceNamePart = java.lang.String.format("collaborate on %s", serviceName);
            joinServiceNamePart = serviceName;
        }
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("username", sender, "link", inviteUrl, "collaborateServiceNamePart", collaborateServiceNamePart, "joinServiceNamePart", joinServiceNamePart);
        return sendEmail(inviteExistingUserEmailTemplateId, email, personalisation);
    }

    public java.lang.String sendLiveAccountCreatedEmail(java.lang.String email, java.lang.String serviceLiveAccountLink) {
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("service_live_account_link", serviceLiveAccountLink);
        return sendEmail(notifyConfiguration.getLiveAccountCreatedEmailTemplateId(), email, personalisation);
    }

    public void sendStripeDisputeCreatedEmail(java.util.Set<java.lang.String> emailAddresses, java.util.Map<java.lang.String, java.lang.String> personalisation) {
        emailAddresses.forEach(email -> sendEmail(stripeDisputeCreatedEmailTemplateId, email, personalisation, notifyEmailReplyToSupportId));
    }

    public void sendStripeDisputeLostEmail(java.util.Set<java.lang.String> emailAddresses, java.util.Map<java.lang.String, java.lang.String> personalisation) {
        boolean hasFee = personalisation.containsKey("disputeFee");
        java.lang.String templateId = (hasFee) ? stripeDisputeLostAndServiceChargedEmailTemplateId : stripeDisputeLostEmailTemplateId;
        emailAddresses.forEach(email -> sendEmail(templateId, email, personalisation, notifyEmailReplyToSupportId));
    }

    public void sendStripeDisputeEvidenceSubmittedEmail(java.util.Set<java.lang.String> emailAddresses, java.util.Map<java.lang.String, java.lang.String> personalisation) {
        emailAddresses.forEach(email -> sendEmail(stripeDisputeEvidenceSubmittedEmailTemplateId, email, personalisation, notifyEmailReplyToSupportId));
    }

    public void sendStripeDisputeWonEmail(java.util.Set<java.lang.String> emailAddresses, java.util.Map<java.lang.String, java.lang.String> personalisation) {
        emailAddresses.forEach(email -> sendEmail(stripeDisputeWonEmailTemplateId, email, personalisation, notifyEmailReplyToSupportId));
    }

    public java.lang.String sendEmail(final java.lang.String templateId, final java.lang.String email, final java.util.Map<java.lang.String, java.lang.String> personalisation) {
        return sendEmail(templateId, email, personalisation, null);
    }

    public java.lang.String sendEmail(final java.lang.String templateId, final java.lang.String email, final java.util.Map<java.lang.String, java.lang.String> personalisation, final java.lang.String emailReplyToId) {
        com.google.common.base.Stopwatch responseTimeStopwatch = com.google.common.base.Stopwatch.createStarted();
        try {
            uk.gov.service.notify.SendEmailResponse response = notifyClientProvider.get().sendEmail(templateId, email, personalisation, null, emailReplyToId);
            return response.getNotificationId().toString();
        } catch (java.lang.Exception e) {
            metricRegistry.counter("notify-operations.email.failures").inc();
            uk.gov.pay.adminusers.service.NotificationService.LOGGER.info("Error sending email: {}", e.getMessage());
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.userNotificationError(e);
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram("notify-operations.email.response_time").update(responseTimeStopwatch.elapsed(java.util.concurrent.TimeUnit.MILLISECONDS));
        }
    }

    private java.lang.String resolveOtpNotifySmsTemplateId(uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId otpNotifySmsTemplateId) {
        switch (otpNotifySmsTemplateId) {
            case SIGN_IN :
                return signInOtpSmsTemplateId;
            case CHANGE_SIGN_IN_2FA_TO_SMS :
                return changeSignIn2faToSmsOtpSmsTemplateId;
            case SELF_INITIATED_CREATE_NEW_USER_AND_SERVICE :
                return selfInitiatedCreateUserAndServiceOtpSmsTemplateId;
            case CREATE_USER_IN_RESPONSE_TO_INVITATION_TO_SERVICE :
                return createUserInResponseToInvitationToServiceOtpSmsTemplateId;
            default :
                throw new java.lang.IllegalArgumentException("Unrecognised OtpNotifySmsTemplateId: " + otpNotifySmsTemplateId.name());
        }
    }

    public enum OtpNotifySmsTemplateId {

        SIGN_IN,
        CHANGE_SIGN_IN_2FA_TO_SMS,
        SELF_INITIATED_CREATE_NEW_USER_AND_SERVICE,
        CREATE_USER_IN_RESPONSE_TO_INVITATION_TO_SERVICE;
    }
}
