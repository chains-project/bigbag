package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class EmailServiceTest {
    private static final java.lang.String MERCHANT_NAME = "merchant name";

    private static final java.lang.String TELEPHONE_NUMBER = "call me maybe";

    private static final java.lang.String ADDRESS_LINE_1 = "address line 1";

    private static final java.lang.String CITY = "city";

    private static final java.lang.String POSTCODE = "postcode";

    private static final java.lang.String ADDRESS_COUNTRY_CODE = "CK";

    private static final java.lang.String MERCHANT_EMAIL = "dd-merchant@example.com";

    private uk.gov.pay.adminusers.service.EmailService emailService;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.NotificationService mockNotificationService;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.ServiceDao mockServiceDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.app.config.NotifyDirectDebitConfiguration mockNotifyDirectDebitConfiguration;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.entity.ServiceEntity mockServiceEntity;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.utils.CountryConverter mockCountryConverter;

    private static final java.lang.String EMAIL_ADDRESS = "aaa@bbb.test";

    private static final java.lang.String GATEWAY_ACCOUNT_ID = "DIRECT_DEBIT:sfksdjweg45w";

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        org.mockito.BDDMockito.given(mockNotificationService.getNotifyDirectDebitConfiguration()).willReturn(mockNotifyDirectDebitConfiguration);
        org.mockito.BDDMockito.given(mockNotifyDirectDebitConfiguration.getMandateCancelledEmailTemplateId()).willReturn("NOTIFY_MANDATE_CANCELLED_EMAIL_TEMPLATE_ID_VALUE");
        org.mockito.BDDMockito.given(mockNotifyDirectDebitConfiguration.getMandateFailedEmailTemplateId()).willReturn("NOTIFY_MANDATE_FAILED_EMAIL_TEMPLATE_ID_VALUE");
        org.mockito.BDDMockito.given(mockNotifyDirectDebitConfiguration.getPaymentFailedEmailTemplateId()).willReturn("NOTIFY_PAYMENT_FAILED_EMAIL_TEMPLATE_ID_VALUE");
        org.mockito.BDDMockito.given(mockNotifyDirectDebitConfiguration.getOneOffMandateAndPaymentCreatedEmailTemplateId()).willReturn("NOTIFY_ONE_OFF_MANDATE_AND_PAYMENT_CREATED_EMAIL_TEMPLATE_ID_VALUE");
        org.mockito.BDDMockito.given(mockNotifyDirectDebitConfiguration.getOnDemandMandateCreatedEmailTemplateId()).willReturn("NOTIFY_ON_DEMAND_MANDATE_CREATED_EMAIL_TEMPLATE_ID_VALUE");
        org.mockito.BDDMockito.given(mockNotifyDirectDebitConfiguration.getOnDemandPaymentConfirmedEmailTemplateId()).willReturn("NOTIFY_ON_DEMAND_PAYMENT_CONFIRMED_EMAIL_TEMPLATE_ID_VALUE");
        org.mockito.BDDMockito.given(mockServiceDao.findByGatewayAccountId(uk.gov.pay.adminusers.service.EmailServiceTest.GATEWAY_ACCOUNT_ID)).willReturn(java.util.Optional.of(mockServiceEntity));
        org.mockito.BDDMockito.given(mockServiceEntity.getServiceNames()).willReturn(java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, "a service")));
        org.mockito.BDDMockito.given(mockCountryConverter.getCountryNameFrom(uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_COUNTRY_CODE)).willReturn(java.util.Optional.of("Cake Land"));
        emailService = new uk.gov.pay.adminusers.service.EmailService(mockNotificationService, mockCountryConverter, mockServiceDao);
    }

    @org.junit.jupiter.api.Test
    public void shouldSendAnEmailForOneOffPaymentConfirmed() throws uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException {
        uk.gov.pay.adminusers.resources.EmailTemplate template = uk.gov.pay.adminusers.resources.EmailTemplate.ONE_OFF_PAYMENT_CONFIRMED;
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("field 1", "theValueOfField1", "field 2", "theValueOfField2");
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails = new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME, uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_LINE_1, null, uk.gov.pay.adminusers.service.EmailServiceTest.CITY, uk.gov.pay.adminusers.service.EmailServiceTest.POSTCODE, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_COUNTRY_CODE, uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL, null);
        org.mockito.BDDMockito.given(mockServiceEntity.getMerchantDetailsEntity()).willReturn(merchantDetails);
        org.mockito.ArgumentCaptor<java.util.Map<java.lang.String, java.lang.String>> personalisationCaptor = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        emailService.sendEmail(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS, uk.gov.pay.adminusers.service.EmailServiceTest.GATEWAY_ACCOUNT_ID, template, personalisation);
        org.mockito.Mockito.verify(mockNotificationService).sendEmail(org.mockito.ArgumentMatchers.eq("NOTIFY_ONE_OFF_MANDATE_AND_PAYMENT_CREATED_EMAIL_TEMPLATE_ID_VALUE"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS), personalisationCaptor.capture());
        java.util.Map<java.lang.String, java.lang.String> allContent = personalisationCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 1"), org.hamcrest.CoreMatchers.is("theValueOfField1"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 2"), org.hamcrest.CoreMatchers.is("theValueOfField2"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("service name"), org.hamcrest.CoreMatchers.is("a service"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation name"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation address"), org.hamcrest.CoreMatchers.is("address line 1, city, postcode, Cake Land"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation phone number"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation email address"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL));
    }

    @org.junit.jupiter.api.Test
    public void shouldSendAnEmailForOnDemandPaymentConfirmed() throws uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException {
        uk.gov.pay.adminusers.resources.EmailTemplate template = uk.gov.pay.adminusers.resources.EmailTemplate.ON_DEMAND_PAYMENT_CONFIRMED;
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("field 1", "theValueOfField1", "field 2", "theValueOfField2");
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails = new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME, uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_LINE_1, null, uk.gov.pay.adminusers.service.EmailServiceTest.CITY, uk.gov.pay.adminusers.service.EmailServiceTest.POSTCODE, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_COUNTRY_CODE, uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL, null);
        org.mockito.BDDMockito.given(mockServiceEntity.getMerchantDetailsEntity()).willReturn(merchantDetails);
        org.mockito.ArgumentCaptor<java.util.Map<java.lang.String, java.lang.String>> personalisationCaptor = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        emailService.sendEmail(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS, uk.gov.pay.adminusers.service.EmailServiceTest.GATEWAY_ACCOUNT_ID, template, personalisation);
        org.mockito.Mockito.verify(mockNotificationService).sendEmail(org.mockito.ArgumentMatchers.eq("NOTIFY_ON_DEMAND_PAYMENT_CONFIRMED_EMAIL_TEMPLATE_ID_VALUE"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS), personalisationCaptor.capture());
        java.util.Map<java.lang.String, java.lang.String> allContent = personalisationCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 1"), org.hamcrest.CoreMatchers.is("theValueOfField1"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 2"), org.hamcrest.CoreMatchers.is("theValueOfField2"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("service name"), org.hamcrest.CoreMatchers.is("a service"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation name"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation address"), org.hamcrest.CoreMatchers.is("address line 1, city, postcode, Cake Land"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation phone number"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation email address"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL));
    }

    @org.junit.jupiter.api.Test
    public void shouldSendAnEmailForPaymentFailed() throws uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException {
        uk.gov.pay.adminusers.resources.EmailTemplate template = uk.gov.pay.adminusers.resources.EmailTemplate.PAYMENT_FAILED;
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("field 1", "theValueOfField1", "field 2", "theValueOfField2");
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails = new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME, uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_LINE_1, null, uk.gov.pay.adminusers.service.EmailServiceTest.CITY, uk.gov.pay.adminusers.service.EmailServiceTest.POSTCODE, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_COUNTRY_CODE, uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL, null);
        org.mockito.BDDMockito.given(mockServiceEntity.getMerchantDetailsEntity()).willReturn(merchantDetails);
        org.mockito.ArgumentCaptor<java.util.Map<java.lang.String, java.lang.String>> personalisationCaptor = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        emailService.sendEmail(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS, uk.gov.pay.adminusers.service.EmailServiceTest.GATEWAY_ACCOUNT_ID, template, personalisation);
        org.mockito.Mockito.verify(mockNotificationService).sendEmail(org.mockito.ArgumentMatchers.eq("NOTIFY_PAYMENT_FAILED_EMAIL_TEMPLATE_ID_VALUE"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS), personalisationCaptor.capture());
        java.util.Map<java.lang.String, java.lang.String> allContent = personalisationCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 1"), org.hamcrest.CoreMatchers.is("theValueOfField1"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 2"), org.hamcrest.CoreMatchers.is("theValueOfField2"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation name"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation phone number"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER));
    }

    @org.junit.jupiter.api.Test
    public void shouldSendAnEmailForMandateFailed() throws uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException {
        uk.gov.pay.adminusers.resources.EmailTemplate template = uk.gov.pay.adminusers.resources.EmailTemplate.MANDATE_FAILED;
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("field 1", "theValueOfField1", "field 2", "theValueOfField2");
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails = new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME, uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_LINE_1, "address line 2", uk.gov.pay.adminusers.service.EmailServiceTest.CITY, uk.gov.pay.adminusers.service.EmailServiceTest.POSTCODE, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_COUNTRY_CODE, uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL, null);
        org.mockito.BDDMockito.given(mockServiceEntity.getMerchantDetailsEntity()).willReturn(merchantDetails);
        org.mockito.ArgumentCaptor<java.util.Map<java.lang.String, java.lang.String>> personalisationCaptor = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        emailService.sendEmail(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS, uk.gov.pay.adminusers.service.EmailServiceTest.GATEWAY_ACCOUNT_ID, template, personalisation);
        org.mockito.Mockito.verify(mockNotificationService).sendEmail(org.mockito.ArgumentMatchers.eq("NOTIFY_MANDATE_FAILED_EMAIL_TEMPLATE_ID_VALUE"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS), personalisationCaptor.capture());
        java.util.Map<java.lang.String, java.lang.String> allContent = personalisationCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 1"), org.hamcrest.CoreMatchers.is("theValueOfField1"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 2"), org.hamcrest.CoreMatchers.is("theValueOfField2"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation name"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation phone number"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation email address"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL));
    }

    @org.junit.jupiter.api.Test
    public void shouldSendAnEmailForOnDemandMandateCreated() throws uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException {
        uk.gov.pay.adminusers.resources.EmailTemplate template = uk.gov.pay.adminusers.resources.EmailTemplate.ON_DEMAND_MANDATE_CREATED;
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("field 1", "theValueOfField1", "field 2", "theValueOfField2");
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails = new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME, uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_LINE_1, "address line 2", uk.gov.pay.adminusers.service.EmailServiceTest.CITY, uk.gov.pay.adminusers.service.EmailServiceTest.POSTCODE, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_COUNTRY_CODE, uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL, null);
        org.mockito.BDDMockito.given(mockServiceEntity.getMerchantDetailsEntity()).willReturn(merchantDetails);
        org.mockito.ArgumentCaptor<java.util.Map<java.lang.String, java.lang.String>> personalisationCaptor = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        emailService.sendEmail(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS, uk.gov.pay.adminusers.service.EmailServiceTest.GATEWAY_ACCOUNT_ID, template, personalisation);
        org.mockito.Mockito.verify(mockNotificationService).sendEmail(org.mockito.ArgumentMatchers.eq("NOTIFY_ON_DEMAND_MANDATE_CREATED_EMAIL_TEMPLATE_ID_VALUE"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS), personalisationCaptor.capture());
        java.util.Map<java.lang.String, java.lang.String> allContent = personalisationCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 1"), org.hamcrest.CoreMatchers.is("theValueOfField1"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 2"), org.hamcrest.CoreMatchers.is("theValueOfField2"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation name"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation phone number"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation email address"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL));
    }

    @org.junit.jupiter.api.Test
    public void shouldSendAnEmailForOneOffMandateCreated() throws uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException {
        uk.gov.pay.adminusers.resources.EmailTemplate template = uk.gov.pay.adminusers.resources.EmailTemplate.ONE_OFF_MANDATE_CREATED;
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("field 1", "theValueOfField1", "field 2", "theValueOfField2");
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails = new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME, uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_LINE_1, "address line 2", uk.gov.pay.adminusers.service.EmailServiceTest.CITY, uk.gov.pay.adminusers.service.EmailServiceTest.POSTCODE, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_COUNTRY_CODE, uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL, null);
        org.mockito.BDDMockito.given(mockServiceEntity.getMerchantDetailsEntity()).willReturn(merchantDetails);
        org.mockito.ArgumentCaptor<java.util.Map<java.lang.String, java.lang.String>> personalisationCaptor = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        emailService.sendEmail(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS, uk.gov.pay.adminusers.service.EmailServiceTest.GATEWAY_ACCOUNT_ID, template, personalisation);
        org.mockito.Mockito.verify(mockNotificationService).sendEmail(org.mockito.ArgumentMatchers.eq("NOTIFY_ONE_OFF_MANDATE_AND_PAYMENT_CREATED_EMAIL_TEMPLATE_ID_VALUE"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS), personalisationCaptor.capture());
        java.util.Map<java.lang.String, java.lang.String> allContent = personalisationCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 1"), org.hamcrest.CoreMatchers.is("theValueOfField1"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 2"), org.hamcrest.CoreMatchers.is("theValueOfField2"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation name"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation phone number"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation email address"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowAnExceptionIfMerchantDetailsAreMissing() {
        // reset mocks as these are not used here and Mockito can continue enforcing strict stubs
        org.mockito.Mockito.reset(mockNotificationService, mockNotifyDirectDebitConfiguration, mockServiceEntity, mockCountryConverter);
        uk.gov.pay.adminusers.resources.EmailTemplate template = uk.gov.pay.adminusers.resources.EmailTemplate.MANDATE_FAILED;
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("field 1", "theValueOfField1", "field 2", "theValueOfField2");
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails = new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity(null, uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_LINE_1, "address line 2", uk.gov.pay.adminusers.service.EmailServiceTest.CITY, uk.gov.pay.adminusers.service.EmailServiceTest.POSTCODE, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_COUNTRY_CODE, uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL, null);
        org.mockito.BDDMockito.given(mockServiceEntity.getMerchantDetailsEntity()).willReturn(merchantDetails);
        uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException exception = org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException.class, () -> emailService.sendEmail(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS, uk.gov.pay.adminusers.service.EmailServiceTest.GATEWAY_ACCOUNT_ID, template, personalisation));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.CoreMatchers.is("Merchant details are missing mandatory fields: can't send email for account " + uk.gov.pay.adminusers.service.EmailServiceTest.GATEWAY_ACCOUNT_ID));
    }

    @org.junit.jupiter.api.Test
    public void shouldNotDisplayCountryNameForInvalidCountryCode() throws uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException {
        uk.gov.pay.adminusers.resources.EmailTemplate template = uk.gov.pay.adminusers.resources.EmailTemplate.ONE_OFF_PAYMENT_CONFIRMED;
        java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of("field 1", "theValueOfField1", "field 2", "theValueOfField2");
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails = new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME, uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_LINE_1, null, uk.gov.pay.adminusers.service.EmailServiceTest.CITY, uk.gov.pay.adminusers.service.EmailServiceTest.POSTCODE, uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_COUNTRY_CODE, uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL, null);
        org.mockito.BDDMockito.given(mockServiceEntity.getMerchantDetailsEntity()).willReturn(merchantDetails);
        org.mockito.BDDMockito.given(mockCountryConverter.getCountryNameFrom(uk.gov.pay.adminusers.service.EmailServiceTest.ADDRESS_COUNTRY_CODE)).willReturn(java.util.Optional.empty());
        org.mockito.ArgumentCaptor<java.util.Map<java.lang.String, java.lang.String>> personalisationCaptor = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        emailService.sendEmail(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS, uk.gov.pay.adminusers.service.EmailServiceTest.GATEWAY_ACCOUNT_ID, template, personalisation);
        org.mockito.Mockito.verify(mockNotificationService).sendEmail(org.mockito.ArgumentMatchers.eq("NOTIFY_ONE_OFF_MANDATE_AND_PAYMENT_CREATED_EMAIL_TEMPLATE_ID_VALUE"), org.mockito.ArgumentMatchers.eq(uk.gov.pay.adminusers.service.EmailServiceTest.EMAIL_ADDRESS), personalisationCaptor.capture());
        java.util.Map<java.lang.String, java.lang.String> allContent = personalisationCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 1"), org.hamcrest.CoreMatchers.is("theValueOfField1"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("field 2"), org.hamcrest.CoreMatchers.is("theValueOfField2"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("service name"), org.hamcrest.CoreMatchers.is("a service"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation name"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_NAME));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation address"), org.hamcrest.CoreMatchers.is("address line 1, city, postcode"));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation phone number"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.TELEPHONE_NUMBER));
        org.hamcrest.MatcherAssert.assertThat(allContent.get("organisation email address"), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.service.EmailServiceTest.MERCHANT_EMAIL));
    }
}
