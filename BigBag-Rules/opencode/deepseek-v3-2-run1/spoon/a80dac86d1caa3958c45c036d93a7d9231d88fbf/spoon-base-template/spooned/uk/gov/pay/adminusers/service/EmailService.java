package uk.gov.pay.adminusers.service;
public class EmailService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.EmailService.class);

    private static final java.lang.String SERVICE_NAME_KEY = "service name";

    private static final java.lang.String ORGANISATION_NAME_KEY = "organisation name";

    private static final java.lang.String ORGANISATION_PHONE_NUMBER_KEY = "organisation phone number";

    private static final java.lang.String ORGANISATION_ADDRESS_KEY = "organisation address";

    private static final java.lang.String ORGANISATION_EMAIL_ADDRESS_KEY = "organisation email address";

    private final uk.gov.pay.adminusers.service.NotificationService notificationService;

    private final uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private final uk.gov.pay.adminusers.utils.CountryConverter countryConverter;

    @com.google.inject.Inject
    public EmailService(uk.gov.pay.adminusers.service.NotificationService notificationService, uk.gov.pay.adminusers.utils.CountryConverter countryConverter, uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao) {
        this.serviceDao = serviceDao;
        this.notificationService = notificationService;
        this.countryConverter = countryConverter;
    }

    private java.lang.String formatMerchantAddress(uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails) {
        java.util.StringJoiner merchantAddress = new java.util.StringJoiner(", ", "", "");
        merchantAddress.add(merchantDetails.getAddressLine1());
        if (!org.apache.commons.lang3.StringUtils.isBlank(merchantDetails.getAddressLine2())) {
            merchantAddress.add(merchantDetails.getAddressLine2());
        }
        merchantAddress.add(merchantDetails.getAddressCity());
        merchantAddress.add(merchantDetails.getAddressPostcode());
        countryConverter.getCountryNameFrom(merchantDetails.getAddressCountryCode()).ifPresent(merchantAddress::add);
        return merchantAddress.toString();
    }

    private boolean isMissingMandatoryFields(uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails) {
        return java.util.stream.Stream.of(merchantDetails.getName(), merchantDetails.getTelephoneNumber(), merchantDetails.getAddressLine1(), merchantDetails.getAddressCity(), merchantDetails.getAddressCountryCode(), merchantDetails.getAddressPostcode(), merchantDetails.getEmail()).anyMatch(org.apache.commons.lang3.StringUtils::isBlank);
    }

    private java.util.Map<uk.gov.pay.adminusers.resources.EmailTemplate, uk.gov.pay.adminusers.service.StaticEmailContent> getTemplateMappingsFor(java.lang.String gatewayAccountId) throws uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = getServiceFor(gatewayAccountId);
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails = service.getMerchantDetailsEntity();
        if (merchantDetails == null) {
            uk.gov.pay.adminusers.service.EmailService.LOGGER.info("Merchant details are empty: can't send email for account {}", gatewayAccountId);
            throw new uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException("Merchant details are empty: can't send email for account " + gatewayAccountId);
        } else if (isMissingMandatoryFields(merchantDetails)) {
            uk.gov.pay.adminusers.service.EmailService.LOGGER.info("Merchant details are missing mandatory fields: can't send email for account {}", gatewayAccountId);
            throw new uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException("Merchant details are missing mandatory fields: can't send email for account " + gatewayAccountId);
        }
        final java.util.Map<java.lang.String, java.lang.String> personalisation = java.util.Map.of(uk.gov.pay.adminusers.service.EmailService.SERVICE_NAME_KEY, service.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName(), uk.gov.pay.adminusers.service.EmailService.ORGANISATION_NAME_KEY, merchantDetails.getName(), uk.gov.pay.adminusers.service.EmailService.ORGANISATION_ADDRESS_KEY, formatMerchantAddress(merchantDetails), uk.gov.pay.adminusers.service.EmailService.ORGANISATION_PHONE_NUMBER_KEY, merchantDetails.getTelephoneNumber(), uk.gov.pay.adminusers.service.EmailService.ORGANISATION_EMAIL_ADDRESS_KEY, merchantDetails.getEmail());
        return java.util.Map.of(uk.gov.pay.adminusers.resources.EmailTemplate.ONE_OFF_PAYMENT_CONFIRMED, new uk.gov.pay.adminusers.service.StaticEmailContent(notificationService.getNotifyDirectDebitConfiguration().getOneOffMandateAndPaymentCreatedEmailTemplateId(), personalisation), uk.gov.pay.adminusers.resources.EmailTemplate.ON_DEMAND_PAYMENT_CONFIRMED, new uk.gov.pay.adminusers.service.StaticEmailContent(notificationService.getNotifyDirectDebitConfiguration().getOnDemandPaymentConfirmedEmailTemplateId(), personalisation), uk.gov.pay.adminusers.resources.EmailTemplate.PAYMENT_FAILED, new uk.gov.pay.adminusers.service.StaticEmailContent(notificationService.getNotifyDirectDebitConfiguration().getPaymentFailedEmailTemplateId(), personalisation), uk.gov.pay.adminusers.resources.EmailTemplate.MANDATE_CANCELLED, new uk.gov.pay.adminusers.service.StaticEmailContent(notificationService.getNotifyDirectDebitConfiguration().getMandateCancelledEmailTemplateId(), personalisation), uk.gov.pay.adminusers.resources.EmailTemplate.MANDATE_FAILED, new uk.gov.pay.adminusers.service.StaticEmailContent(notificationService.getNotifyDirectDebitConfiguration().getMandateFailedEmailTemplateId(), personalisation), uk.gov.pay.adminusers.resources.EmailTemplate.ON_DEMAND_MANDATE_CREATED, new uk.gov.pay.adminusers.service.StaticEmailContent(notificationService.getNotifyDirectDebitConfiguration().getOnDemandMandateCreatedEmailTemplateId(), personalisation), uk.gov.pay.adminusers.resources.EmailTemplate.ONE_OFF_MANDATE_CREATED, new uk.gov.pay.adminusers.service.StaticEmailContent(notificationService.getNotifyDirectDebitConfiguration().getOneOffMandateAndPaymentCreatedEmailTemplateId(), personalisation));
    }

    private uk.gov.pay.adminusers.persistence.entity.ServiceEntity getServiceFor(java.lang.String gatewayAccountId) {
        return serviceDao.findByGatewayAccountId(gatewayAccountId).orElseThrow(() -> new uk.gov.pay.adminusers.exception.ServiceNotFoundException("Service not found"));
    }

    public java.lang.String sendEmail(java.lang.String email, java.lang.String gatewayAccountId, uk.gov.pay.adminusers.resources.EmailTemplate template, java.util.Map<java.lang.String, java.lang.String> dynamicContent) throws uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException {
        uk.gov.pay.adminusers.service.StaticEmailContent staticEmailContent = getTemplateMappingsFor(gatewayAccountId).get(template);
        java.util.Map<java.lang.String, java.lang.String> staticContent = new java.util.HashMap<>(staticEmailContent.getPersonalisation());
        staticContent.putAll(dynamicContent);
        uk.gov.pay.adminusers.service.EmailService.LOGGER.info("Sending direct debit email for " + template.toString());
        return notificationService.sendEmail(staticEmailContent.getTemplateId(), email, staticContent);
    }
}
