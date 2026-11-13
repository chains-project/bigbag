package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class NotificationServiceTest {
    private static final java.lang.String OTP = "123456";

    private static final java.lang.String PHONE_NUMBER = "07700900000";

    private static final java.lang.String PHONE_NUMBER_E164 = "+447700900000";

    private static final java.util.UUID NOTIFICATION_ID = java.util.UUID.fromString("0E56AABE-E026-4478-8B82-C03D3B31CFC1");

    private static final java.lang.String SECOND_FACTOR_SMS_TEMPLATE_ID = "second-factor-sms-template-id";

    private static final java.lang.String SIGN_IN_OTP_SMS_TEMPLATE_ID = "sign-in-otp-sms-template-id";

    private static final java.lang.String CHANGE_SIGN_IN_2FA_TO_SMS_OTP_SMS_TEMPLATE_ID = "change-sign-in-2fa-to-sms-otp-sms-template-id";

    private static final java.lang.String SELF_INITIATED_CREATE_USER_AND_SERVICE_OTP_SMS_TEMPLATE_ID = "self-initiated-create-user-and-service-otp-sms-template-id";

    private static final java.lang.String CREATE_USER_IN_RESPONSE_TO_INVITATION_TO_SERVICE_OTP_SMS_TEMPLATE_ID = "create-user-in-response-to-invitation-to-service-otp-sms-template-id";

    private static final java.lang.String INVITE_USER_EMAIL_TEMPLATE_ID = "invite-user-email-template-id";

    private static final java.lang.String INVITE_USER_EXISTING_EMAIL_TEMPLATE_ID = "invite-user-existing-email-template-id";

    private static final java.lang.String FORGOTTEN_PASSWORD_EMAIL_TEMPLATE_ID = "forgotten-password-email-template-id";

    private static final java.lang.String STRIPE_DISPUTE_CREATED_EMAIL_TEMPLATE_ID = "stripe-dispute-created-email-template-id";

    private static final java.lang.String STRIPE_DISPUTE_LOST_EMAIL_TEMPLATE_ID = "stripe-dispute-lost-email-template-id";

    private static final java.lang.String STRIPE_DISPUTE_LOST_AND_SERVICE_CHARGED_EMAIL_TEMPLATE_ID = "stripe-dispute-lost-and-service-charged-email-template-id";

    private static final java.lang.String STRIPE_DISPUTE_EVIDENCE_SUBMITTED_EMAIL_TEMPLATE_ID = "stripe-dispute-evidence-submitted-email-template-id";

    private static final java.lang.String STRIPE_DISPUTE_WON_EMAIL_TEMPLATE_ID = "stripe-dispute-won-email-template-id";

    private static final java.lang.String NOTIFY_EMAIL_REPLY_TO_SUPPORT_ID = "notify-email-reply-to-support-id";

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.NotifyClientProvider mockNotifyClientProvider;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.app.config.NotifyConfiguration mockNotifyConfiguration;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.app.config.NotifyDirectDebitConfiguration mockNotifyDirectDebitConfiguration;

    @org.mockito.Mock
    private com.codahale.metrics.MetricRegistry mockMetricRegistry;

    @org.mockito.Mock
    private uk.gov.service.notify.NotificationClient mockNotificationClient;

    @org.mockito.Mock
    private uk.gov.service.notify.SendSmsResponse mockSendSmsResponse;

    @org.mockito.Mock
    private uk.gov.service.notify.SendEmailResponse mockSendEmailResponse;

    private uk.gov.pay.adminusers.service.NotificationService notificationService;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() throws uk.gov.service.notify.NotificationClientException {
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getSignInOtpSmsTemplateId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.SIGN_IN_OTP_SMS_TEMPLATE_ID);
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getChangeSignIn2faToSmsOtpSmsTemplateId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.CHANGE_SIGN_IN_2FA_TO_SMS_OTP_SMS_TEMPLATE_ID);
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getSelfInitiatedCreateUserAndServiceOtpSmsTemplateId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.SELF_INITIATED_CREATE_USER_AND_SERVICE_OTP_SMS_TEMPLATE_ID);
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getCreateUserInResponseToInvitationToServiceOtpSmsTemplateId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.CREATE_USER_IN_RESPONSE_TO_INVITATION_TO_SERVICE_OTP_SMS_TEMPLATE_ID);
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getInviteUserEmailTemplateId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.INVITE_USER_EMAIL_TEMPLATE_ID);
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getInviteUserExistingEmailTemplateId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.INVITE_USER_EXISTING_EMAIL_TEMPLATE_ID);
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getForgottenPasswordEmailTemplateId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.FORGOTTEN_PASSWORD_EMAIL_TEMPLATE_ID);
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getStripeDisputeCreatedEmailTemplateId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_CREATED_EMAIL_TEMPLATE_ID);
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getStripeDisputeLostEmailTemplateId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_LOST_EMAIL_TEMPLATE_ID);
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getStripeDisputeLostAndServiceChargedEmailTemplateId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_LOST_AND_SERVICE_CHARGED_EMAIL_TEMPLATE_ID);
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getStripeDisputeEvidenceSubmittedEmailTemplateId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_EVIDENCE_SUBMITTED_EMAIL_TEMPLATE_ID);
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getStripeDisputeWonEmailTemplateId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_WON_EMAIL_TEMPLATE_ID);
        org.mockito.BDDMockito.given(mockNotifyConfiguration.getNotifyEmailReplyToSupportId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFY_EMAIL_REPLY_TO_SUPPORT_ID);
        org.mockito.BDDMockito.given(mockNotifyClientProvider.get()).willReturn(mockNotificationClient);
        notificationService = new uk.gov.pay.adminusers.service.NotificationService(mockNotifyClientProvider, mockNotifyConfiguration, mockNotifyDirectDebitConfiguration, mockMetricRegistry);
    }

    @org.junit.jupiter.api.Test
    public void sendSecondFactorPasscodeSmsWithSignInTemplate() throws uk.gov.service.notify.NotificationClientException {
        org.mockito.BDDMockito.given(mockMetricRegistry.histogram("notify-operations.sms.response_time")).willReturn(org.mockito.Mockito.mock(com.codahale.metrics.Histogram.class));
        org.mockito.BDDMockito.given(mockNotificationClient.sendSms(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.isNull())).willReturn(mockSendSmsResponse);
        org.mockito.BDDMockito.given(mockSendSmsResponse.getNotificationId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFICATION_ID);
        notificationService.sendSecondFactorPasscodeSms(uk.gov.pay.adminusers.service.NotificationServiceTest.PHONE_NUMBER, uk.gov.pay.adminusers.service.NotificationServiceTest.OTP, uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SIGN_IN);
        org.mockito.Mockito.verify(mockNotificationClient).sendSms(uk.gov.pay.adminusers.service.NotificationServiceTest.SIGN_IN_OTP_SMS_TEMPLATE_ID, uk.gov.pay.adminusers.service.NotificationServiceTest.PHONE_NUMBER_E164, java.util.Map.of("code", uk.gov.pay.adminusers.service.NotificationServiceTest.OTP), null);
    }

    @org.junit.jupiter.api.Test
    public void sendSecondFactorPasscodeSmsWithChangeSignIn2faToSmsTemplate() throws uk.gov.service.notify.NotificationClientException {
        org.mockito.BDDMockito.given(mockMetricRegistry.histogram("notify-operations.sms.response_time")).willReturn(org.mockito.Mockito.mock(com.codahale.metrics.Histogram.class));
        org.mockito.BDDMockito.given(mockNotificationClient.sendSms(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.isNull())).willReturn(mockSendSmsResponse);
        org.mockito.BDDMockito.given(mockSendSmsResponse.getNotificationId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFICATION_ID);
        notificationService.sendSecondFactorPasscodeSms(uk.gov.pay.adminusers.service.NotificationServiceTest.PHONE_NUMBER, uk.gov.pay.adminusers.service.NotificationServiceTest.OTP, uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.CHANGE_SIGN_IN_2FA_TO_SMS);
        org.mockito.Mockito.verify(mockNotificationClient).sendSms(uk.gov.pay.adminusers.service.NotificationServiceTest.CHANGE_SIGN_IN_2FA_TO_SMS_OTP_SMS_TEMPLATE_ID, uk.gov.pay.adminusers.service.NotificationServiceTest.PHONE_NUMBER_E164, java.util.Map.of("code", uk.gov.pay.adminusers.service.NotificationServiceTest.OTP), null);
    }

    @org.junit.jupiter.api.Test
    public void sendSecondFactorPasscodeSmsWithSelfInitiatedCreateNewUserAndServiceTemplate() throws uk.gov.service.notify.NotificationClientException {
        org.mockito.BDDMockito.given(mockMetricRegistry.histogram("notify-operations.sms.response_time")).willReturn(org.mockito.Mockito.mock(com.codahale.metrics.Histogram.class));
        org.mockito.BDDMockito.given(mockNotificationClient.sendSms(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.isNull())).willReturn(mockSendSmsResponse);
        org.mockito.BDDMockito.given(mockSendSmsResponse.getNotificationId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFICATION_ID);
        notificationService.sendSecondFactorPasscodeSms(uk.gov.pay.adminusers.service.NotificationServiceTest.PHONE_NUMBER, uk.gov.pay.adminusers.service.NotificationServiceTest.OTP, uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.SELF_INITIATED_CREATE_NEW_USER_AND_SERVICE);
        org.mockito.Mockito.verify(mockNotificationClient).sendSms(uk.gov.pay.adminusers.service.NotificationServiceTest.SELF_INITIATED_CREATE_USER_AND_SERVICE_OTP_SMS_TEMPLATE_ID, uk.gov.pay.adminusers.service.NotificationServiceTest.PHONE_NUMBER_E164, java.util.Map.of("code", uk.gov.pay.adminusers.service.NotificationServiceTest.OTP), null);
    }

    @org.junit.jupiter.api.Test
    public void sendSecondFactorPasscodeSmsWithCreateUserInResponseToInvitationToServiceTemplate() throws uk.gov.service.notify.NotificationClientException {
        org.mockito.BDDMockito.given(mockMetricRegistry.histogram("notify-operations.sms.response_time")).willReturn(org.mockito.Mockito.mock(com.codahale.metrics.Histogram.class));
        org.mockito.BDDMockito.given(mockNotificationClient.sendSms(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.isNull())).willReturn(mockSendSmsResponse);
        org.mockito.BDDMockito.given(mockSendSmsResponse.getNotificationId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFICATION_ID);
        notificationService.sendSecondFactorPasscodeSms(uk.gov.pay.adminusers.service.NotificationServiceTest.PHONE_NUMBER, uk.gov.pay.adminusers.service.NotificationServiceTest.OTP, uk.gov.pay.adminusers.service.NotificationService.OtpNotifySmsTemplateId.CREATE_USER_IN_RESPONSE_TO_INVITATION_TO_SERVICE);
        org.mockito.Mockito.verify(mockNotificationClient).sendSms(uk.gov.pay.adminusers.service.NotificationServiceTest.CREATE_USER_IN_RESPONSE_TO_INVITATION_TO_SERVICE_OTP_SMS_TEMPLATE_ID, uk.gov.pay.adminusers.service.NotificationServiceTest.PHONE_NUMBER_E164, java.util.Map.of("code", uk.gov.pay.adminusers.service.NotificationServiceTest.OTP), null);
    }

    @org.junit.jupiter.api.Test
    public void sendEmailWithStripeDisputeCreatedEmailTemplateId() throws uk.gov.service.notify.NotificationClientException {
        org.mockito.BDDMockito.given(mockMetricRegistry.histogram("notify-operations.email.response_time")).willReturn(org.mockito.Mockito.mock(com.codahale.metrics.Histogram.class));
        org.mockito.BDDMockito.given(mockNotificationClient.sendEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString())).willReturn(mockSendEmailResponse);
        org.mockito.BDDMockito.given(mockSendEmailResponse.getNotificationId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFICATION_ID);
        var addresses = java.util.stream.Stream.of("email1@service.gov.uk", "email2@service.gov.uk").collect(java.util.stream.Collectors.toSet());
        var personalisation = java.util.stream.Stream.of(new java.lang.String[][]{ new java.lang.String[]{ "k1", "v1" }, new java.lang.String[]{ "k2", "v2" } }).collect(java.util.stream.Collectors.toMap(data -> data[0], data -> data[1]));
        notificationService.sendStripeDisputeCreatedEmail(addresses, personalisation);
        org.mockito.Mockito.verify(mockNotificationClient).sendEmail(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_CREATED_EMAIL_TEMPLATE_ID, "email1@service.gov.uk", personalisation, null, uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFY_EMAIL_REPLY_TO_SUPPORT_ID);
        org.mockito.Mockito.verify(mockNotificationClient).sendEmail(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_CREATED_EMAIL_TEMPLATE_ID, "email2@service.gov.uk", personalisation, null, uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFY_EMAIL_REPLY_TO_SUPPORT_ID);
    }

    @org.junit.jupiter.api.Test
    public void sendEmailWithStripeDisputeLostEmailTemplateId() throws uk.gov.service.notify.NotificationClientException {
        org.mockito.BDDMockito.given(mockMetricRegistry.histogram("notify-operations.email.response_time")).willReturn(org.mockito.Mockito.mock(com.codahale.metrics.Histogram.class));
        org.mockito.BDDMockito.given(mockNotificationClient.sendEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString())).willReturn(mockSendEmailResponse);
        org.mockito.BDDMockito.given(mockSendEmailResponse.getNotificationId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFICATION_ID);
        var addresses = java.util.stream.Stream.of("email1@service.gov.uk", "email2@service.gov.uk").collect(java.util.stream.Collectors.toSet());
        var personalisation = java.util.stream.Stream.of(new java.lang.String[][]{ new java.lang.String[]{ "k1", "v1" }, new java.lang.String[]{ "k2", "v2" } }).collect(java.util.stream.Collectors.toMap(data -> data[0], data -> data[1]));
        notificationService.sendStripeDisputeLostEmail(addresses, personalisation);
        org.mockito.Mockito.verify(mockNotificationClient).sendEmail(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_LOST_EMAIL_TEMPLATE_ID, "email1@service.gov.uk", personalisation, null, uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFY_EMAIL_REPLY_TO_SUPPORT_ID);
        org.mockito.Mockito.verify(mockNotificationClient).sendEmail(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_LOST_EMAIL_TEMPLATE_ID, "email2@service.gov.uk", personalisation, null, uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFY_EMAIL_REPLY_TO_SUPPORT_ID);
    }

    @org.junit.jupiter.api.Test
    public void sendEmailWithStripeDisputeLostAndServiceChargedEmailTemplateId() throws uk.gov.service.notify.NotificationClientException {
        org.mockito.BDDMockito.given(mockMetricRegistry.histogram("notify-operations.email.response_time")).willReturn(org.mockito.Mockito.mock(com.codahale.metrics.Histogram.class));
        org.mockito.BDDMockito.given(mockNotificationClient.sendEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString())).willReturn(mockSendEmailResponse);
        org.mockito.BDDMockito.given(mockSendEmailResponse.getNotificationId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFICATION_ID);
        var addresses = java.util.stream.Stream.of("email1@service.gov.uk", "email2@service.gov.uk").collect(java.util.stream.Collectors.toSet());
        var personalisation = java.util.stream.Stream.of(new java.lang.String[][]{ new java.lang.String[]{ "k1", "v1" }, new java.lang.String[]{ "k2", "v2" }, new java.lang.String[]{ "disputeFee", "v2" }// This causes the "[...] and service charged" template to be used
        // This causes the "[...] and service charged" template to be used
        // This causes the "[...] and service charged" template to be used
         }).collect(java.util.stream.Collectors.toMap(data -> data[0], data -> data[1]));
        notificationService.sendStripeDisputeLostEmail(addresses, personalisation);
        org.mockito.Mockito.verify(mockNotificationClient).sendEmail(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_LOST_AND_SERVICE_CHARGED_EMAIL_TEMPLATE_ID, "email1@service.gov.uk", personalisation, null, uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFY_EMAIL_REPLY_TO_SUPPORT_ID);
        org.mockito.Mockito.verify(mockNotificationClient).sendEmail(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_LOST_AND_SERVICE_CHARGED_EMAIL_TEMPLATE_ID, "email2@service.gov.uk", personalisation, null, uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFY_EMAIL_REPLY_TO_SUPPORT_ID);
    }

    @org.junit.jupiter.api.Test
    public void sendEmailWithStripeDisputeEvidenceSubmittedEmailTemplateId() throws uk.gov.service.notify.NotificationClientException {
        org.mockito.BDDMockito.given(mockMetricRegistry.histogram("notify-operations.email.response_time")).willReturn(org.mockito.Mockito.mock(com.codahale.metrics.Histogram.class));
        org.mockito.BDDMockito.given(mockNotificationClient.sendEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString())).willReturn(mockSendEmailResponse);
        org.mockito.BDDMockito.given(mockSendEmailResponse.getNotificationId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFICATION_ID);
        var addresses = java.util.stream.Stream.of("email1@service.gov.uk", "email2@service.gov.uk").collect(java.util.stream.Collectors.toSet());
        var personalisation = java.util.stream.Stream.of(new java.lang.String[][]{ new java.lang.String[]{ "k1", "v1" }, new java.lang.String[]{ "k2", "v2" } }).collect(java.util.stream.Collectors.toMap(data -> data[0], data -> data[1]));
        notificationService.sendStripeDisputeEvidenceSubmittedEmail(addresses, personalisation);
        org.mockito.Mockito.verify(mockNotificationClient).sendEmail(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_EVIDENCE_SUBMITTED_EMAIL_TEMPLATE_ID, "email1@service.gov.uk", personalisation, null, uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFY_EMAIL_REPLY_TO_SUPPORT_ID);
        org.mockito.Mockito.verify(mockNotificationClient).sendEmail(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_EVIDENCE_SUBMITTED_EMAIL_TEMPLATE_ID, "email2@service.gov.uk", personalisation, null, uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFY_EMAIL_REPLY_TO_SUPPORT_ID);
    }

    @org.junit.jupiter.api.Test
    public void sendEmailWithStripeDisputeWonEmailTemplateId() throws uk.gov.service.notify.NotificationClientException {
        org.mockito.BDDMockito.given(mockMetricRegistry.histogram("notify-operations.email.response_time")).willReturn(org.mockito.Mockito.mock(com.codahale.metrics.Histogram.class));
        org.mockito.BDDMockito.given(mockNotificationClient.sendEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString())).willReturn(mockSendEmailResponse);
        org.mockito.BDDMockito.given(mockSendEmailResponse.getNotificationId()).willReturn(uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFICATION_ID);
        var addresses = java.util.stream.Stream.of("email1@service.gov.uk", "email2@service.gov.uk").collect(java.util.stream.Collectors.toSet());
        var personalisation = java.util.stream.Stream.of(new java.lang.String[][]{ new java.lang.String[]{ "k1", "v1" }, new java.lang.String[]{ "k2", "v2" } }).collect(java.util.stream.Collectors.toMap(data -> data[0], data -> data[1]));
        notificationService.sendStripeDisputeWonEmail(addresses, personalisation);
        org.mockito.Mockito.verify(mockNotificationClient).sendEmail(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_WON_EMAIL_TEMPLATE_ID, "email1@service.gov.uk", personalisation, null, uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFY_EMAIL_REPLY_TO_SUPPORT_ID);
        org.mockito.Mockito.verify(mockNotificationClient).sendEmail(uk.gov.pay.adminusers.service.NotificationServiceTest.STRIPE_DISPUTE_WON_EMAIL_TEMPLATE_ID, "email2@service.gov.uk", personalisation, null, uk.gov.pay.adminusers.service.NotificationServiceTest.NOTIFY_EMAIL_REPLY_TO_SUPPORT_ID);
    }
}
