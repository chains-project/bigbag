package uk.gov.pay.adminusers.app.config;
public class NotifyConfiguration extends io.dropwizard.Configuration {
    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String cardApiKey;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String notificationBaseURL;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String signInOtpSmsTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String changeSignIn2faToSmsOtpSmsTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String selfInitiatedCreateUserAndServiceOtpSmsTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String createUserInResponseToInvitationToServiceOtpSmsTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String inviteUserEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String inviteUserExistingEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String inviteServiceEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String forgottenPasswordEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String inviteServiceUserExistsEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String inviteServiceUserDisabledEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String liveAccountCreatedEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String stripeDisputeCreatedEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String stripeDisputeLostEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String stripeDisputeLostAndServiceChargedEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String stripeDisputeEvidenceSubmittedEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String stripeDisputeWonEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String notifyEmailReplyToSupportId;

    public java.lang.String getCardApiKey() {
        return cardApiKey;
    }

    public java.lang.String getNotificationBaseURL() {
        return notificationBaseURL;
    }

    public java.lang.String getSignInOtpSmsTemplateId() {
        return signInOtpSmsTemplateId;
    }

    public java.lang.String getChangeSignIn2faToSmsOtpSmsTemplateId() {
        return changeSignIn2faToSmsOtpSmsTemplateId;
    }

    public java.lang.String getSelfInitiatedCreateUserAndServiceOtpSmsTemplateId() {
        return selfInitiatedCreateUserAndServiceOtpSmsTemplateId;
    }

    public java.lang.String getCreateUserInResponseToInvitationToServiceOtpSmsTemplateId() {
        return createUserInResponseToInvitationToServiceOtpSmsTemplateId;
    }

    public java.lang.String getInviteUserEmailTemplateId() {
        return inviteUserEmailTemplateId;
    }

    public java.lang.String getForgottenPasswordEmailTemplateId() {
        return forgottenPasswordEmailTemplateId;
    }

    public java.lang.String getInviteServiceEmailTemplateId() {
        return inviteServiceEmailTemplateId;
    }

    public java.lang.String getInviteServiceUserExistsEmailTemplateId() {
        return inviteServiceUserExistsEmailTemplateId;
    }

    public java.lang.String getInviteServiceUserDisabledEmailTemplateId() {
        return inviteServiceUserDisabledEmailTemplateId;
    }

    public java.lang.String getInviteUserExistingEmailTemplateId() {
        return inviteUserExistingEmailTemplateId;
    }

    public java.lang.String getLiveAccountCreatedEmailTemplateId() {
        return liveAccountCreatedEmailTemplateId;
    }

    public java.lang.String getStripeDisputeCreatedEmailTemplateId() {
        return stripeDisputeCreatedEmailTemplateId;
    }

    public java.lang.String getStripeDisputeLostEmailTemplateId() {
        return stripeDisputeLostEmailTemplateId;
    }

    public java.lang.String getStripeDisputeLostAndServiceChargedEmailTemplateId() {
        return stripeDisputeLostAndServiceChargedEmailTemplateId;
    }

    public java.lang.String getStripeDisputeEvidenceSubmittedEmailTemplateId() {
        return stripeDisputeEvidenceSubmittedEmailTemplateId;
    }

    public java.lang.String getStripeDisputeWonEmailTemplateId() {
        return stripeDisputeWonEmailTemplateId;
    }

    public java.lang.String getNotifyEmailReplyToSupportId() {
        return notifyEmailReplyToSupportId;
    }
}
