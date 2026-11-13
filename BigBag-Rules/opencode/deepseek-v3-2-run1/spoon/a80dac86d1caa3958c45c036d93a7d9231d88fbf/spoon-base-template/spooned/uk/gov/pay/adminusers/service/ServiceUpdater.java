package uk.gov.pay.adminusers.service;
public class ServiceUpdater {
    public static final java.lang.String FIELD_GATEWAY_ACCOUNT_IDS = "gateway_account_ids";

    public static final java.lang.String FIELD_CUSTOM_BRANDING = "custom_branding";

    public static final java.lang.String FIELD_SERVICE_NAME_PREFIX = "service_name";

    public static final java.lang.String FIELD_REDIRECT_NAME = "redirect_to_service_immediately_on_terminal_state";

    public static final java.lang.String FIELD_EXPERIMENTAL_FEATURES_ENABLED = "experimental_features_enabled";

    public static final java.lang.String FIELD_AGENT_INITIATED_MOTO_ENABLED = "agent_initiated_moto_enabled";

    public static final java.lang.String FIELD_COLLECT_BILLING_ADDRESS = "collect_billing_address";

    public static final java.lang.String FIELD_DEFAULT_BILLING_ADDRESS_COUNTRY = "default_billing_address_country";

    public static final java.lang.String FIELD_CURRENT_GO_LIVE_STAGE = "current_go_live_stage";

    public static final java.lang.String FIELD_CURRENT_PSP_TEST_ACCOUNT_STAGE = "current_psp_test_account_stage";

    public static final java.lang.String FIELD_SECTOR = "sector";

    public static final java.lang.String FIELD_INTERNAL = "internal";

    public static final java.lang.String FIELD_ARCHIVED = "archived";

    public static final java.lang.String FIELD_WENT_LIVE_DATE = "went_live_date";

    public static final java.lang.String FIELD_MERCHANT_DETAILS_NAME = "merchant_details/name";

    public static final java.lang.String FIELD_MERCHANT_DETAILS_ADDRESS_LINE_1 = "merchant_details/address_line1";

    public static final java.lang.String FIELD_MERCHANT_DETAILS_ADDRESS_LINE_2 = "merchant_details/address_line2";

    public static final java.lang.String FIELD_MERCHANT_DETAILS_ADDRESS_CITY = "merchant_details/address_city";

    public static final java.lang.String FIELD_MERCHANT_DETAILS_ADDRESS_COUNRTY = "merchant_details/address_country";

    public static final java.lang.String FIELD_MERCHANT_DETAILS_ADDRESS_POSTCODE = "merchant_details/address_postcode";

    public static final java.lang.String FIELD_MERCHANT_DETAILS_EMAIL = "merchant_details/email";

    public static final java.lang.String FIELD_MERCHANT_DETAILS_TELEPHONE_NUMBER = "merchant_details/telephone_number";

    public static final java.lang.String FIELD_MERCHANT_DETAILS_URL = "merchant_details/url";

    private final uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private final java.util.Map<java.lang.String, java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity>> attributeUpdaters;

    @com.google.inject.Inject
    public ServiceUpdater(uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao) {
        java.util.Map<java.lang.String, java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity>> attributeUpdaters = new java.util.HashMap<>(java.util.Map.ofEntries(java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_GATEWAY_ACCOUNT_IDS, assignGatewayAccounts()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_CUSTOM_BRANDING, updateCustomBranding()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_REDIRECT_NAME, updateRedirectImmediately()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_EXPERIMENTAL_FEATURES_ENABLED, updateExperimentalFeaturesEnabled()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_AGENT_INITIATED_MOTO_ENABLED, updateAgentInitiatedMotoEnabled()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_COLLECT_BILLING_ADDRESS, updateCollectBillingAddress()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_DEFAULT_BILLING_ADDRESS_COUNTRY, updateDefaultBillingAddressCountry()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_CURRENT_GO_LIVE_STAGE, updateCurrentGoLiveStage()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_CURRENT_PSP_TEST_ACCOUNT_STAGE, updateCurrentPspTestAccountStage()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_SECTOR, updateSector()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_INTERNAL, updateInternal()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_ARCHIVED, updateArchived()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_WENT_LIVE_DATE, updateWentLiveDate()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_NAME, updateMerchantDetailsName()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_LINE_1, updateMerchantDetailsAddressLine1()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_LINE_2, updateMerchantDetailsAddressLine2()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_CITY, updateMerchantDetailsAddressCity()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_COUNRTY, updateMerchantDetailsAddressCountry()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_POSTCODE, updateMerchantDetailsAddressPostcode()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_EMAIL, updateMerchantDetailsEmail()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_TELEPHONE_NUMBER, updateMerchantDetailsPhone()), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_URL, updateMerchantDetailsUrl())));
        java.util.Arrays.stream(uk.gov.service.payments.commons.model.SupportedLanguage.values()).forEach(language -> attributeUpdaters.put((uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_SERVICE_NAME_PREFIX + '/') + language.toString(), updateServiceName()));
        this.attributeUpdaters = java.util.Map.copyOf(attributeUpdaters);
        this.serviceDao = serviceDao;
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.Service> doUpdate(java.lang.String serviceExternalId, uk.gov.pay.adminusers.model.ServiceUpdateRequest updateRequests) {
        return doUpdate(serviceExternalId, java.util.Collections.singletonList(updateRequests));
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.Service> doUpdate(java.lang.String serviceExternalId, java.util.List<uk.gov.pay.adminusers.model.ServiceUpdateRequest> updateRequests) {
        return serviceDao.findByExternalId(serviceExternalId).map(serviceEntity -> {
            updateRequests.forEach(req -> {
                attributeUpdaters.get(req.getPath()).accept(req, serviceEntity);
                serviceDao.merge(serviceEntity);
            });
            return serviceEntity.toService();
        });
    }

    @com.google.inject.persist.Transactional
    public uk.gov.pay.adminusers.model.Service doUpdateMerchantDetails(java.lang.String serviceExternalId, uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest updateMerchantDetailsRequest) throws uk.gov.pay.adminusers.exception.ServiceNotFoundException {
        return serviceDao.findByExternalId(serviceExternalId).map(serviceEntity -> {
            uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantEntity = uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity.from(updateMerchantDetailsRequest);
            serviceEntity.setMerchantDetailsEntity(merchantEntity);
            serviceDao.merge(serviceEntity);
            return serviceEntity.toService();
        }).orElseThrow(() -> new uk.gov.pay.adminusers.exception.ServiceNotFoundException(serviceExternalId));
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> assignGatewayAccounts() {
        return (serviceUpdateRequest, serviceEntity) -> {
            java.util.List<java.lang.String> gatewayAccountIds = serviceUpdateRequest.valueAsList();
            if (serviceDao.checkIfGatewayAccountsUsed(gatewayAccountIds)) {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.conflictingServiceGatewayAccounts(gatewayAccountIds);
            } else {
                serviceEntity.addGatewayAccountIds(gatewayAccountIds.toArray(new java.lang.String[0]));
            }
        };
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateCustomBranding() {
        return (serviceUpdateRequest, serviceEntity) -> serviceEntity.setCustomBranding(serviceUpdateRequest.valueAsObject());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateServiceName() {
        return (serviceUpdateRequest, serviceEntity) -> {
            java.lang.String path = serviceUpdateRequest.getPath();
            assert path.matches(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_SERVICE_NAME_PREFIX + "/[a-z]+") : "Path must be 'service_name/en' etc.";
            uk.gov.service.payments.commons.model.SupportedLanguage language = uk.gov.service.payments.commons.model.SupportedLanguage.fromIso639AlphaTwoCode(serviceUpdateRequest.getPath().substring(path.indexOf('/') + 1));
            uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity serviceNameEntity = uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(language, serviceUpdateRequest.valueAsString());
            serviceEntity.addOrUpdateServiceName(serviceNameEntity);
        };
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateRedirectImmediately() {
        return (serviceUpdateRequest, serviceEntity) -> serviceEntity.setRedirectToServiceImmediatelyOnTerminalState(serviceUpdateRequest.valueAsBoolean());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateExperimentalFeaturesEnabled() {
        return (serviceUpdateRequest, serviceEntity) -> serviceEntity.setExperimentalFeaturesEnabled(serviceUpdateRequest.valueAsBoolean());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateAgentInitiatedMotoEnabled() {
        return (serviceUpdateRequest, serviceEntity) -> serviceEntity.setAgentInitiatedMotoEnabled(serviceUpdateRequest.valueAsBoolean());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateCollectBillingAddress() {
        return (serviceUpdateRequest, serviceEntity) -> serviceEntity.setCollectBillingAddress(serviceUpdateRequest.valueAsBoolean());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateDefaultBillingAddressCountry() {
        return (serviceUpdateRequest, serviceEntity) -> serviceEntity.setDefaultBillingAddressCountry(serviceUpdateRequest.valueAsString());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateCurrentGoLiveStage() {
        return (serviceUpdateRequest, serviceEntity) -> serviceEntity.setCurrentGoLiveStage(uk.gov.pay.adminusers.model.GoLiveStage.valueOf(serviceUpdateRequest.valueAsString()));
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateCurrentPspTestAccountStage() {
        return (serviceUpdateRequest, serviceEntity) -> serviceEntity.setCurrentPspTestAccountStage(uk.gov.pay.adminusers.model.PspTestAccountStage.valueOf(serviceUpdateRequest.valueAsString()));
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateSector() {
        return (serviceUpdateRequest, serviceEntity) -> serviceEntity.setSector(serviceUpdateRequest.valueAsString());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateInternal() {
        return (serviceUpdateRequest, serviceEntity) -> serviceEntity.setInternal(serviceUpdateRequest.valueAsBoolean());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateArchived() {
        return (serviceUpdateRequest, serviceEntity) -> serviceEntity.setArchived(serviceUpdateRequest.valueAsBoolean());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateWentLiveDate() {
        return (serviceUpdateRequest, serviceEntity) -> serviceEntity.setWentLiveDate(serviceUpdateRequest.valueAsDateTime());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateMerchantDetailsName() {
        return (serviceUpdateRequest, serviceEntity) -> getOrCreateMerchantDetails(serviceEntity).setName(serviceUpdateRequest.valueAsString());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateMerchantDetailsAddressLine1() {
        return (serviceUpdateRequest, serviceEntity) -> getOrCreateMerchantDetails(serviceEntity).setAddressLine1(serviceUpdateRequest.valueAsString());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateMerchantDetailsAddressLine2() {
        return (serviceUpdateRequest, serviceEntity) -> getOrCreateMerchantDetails(serviceEntity).setAddressLine2(serviceUpdateRequest.valueAsString());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateMerchantDetailsAddressCity() {
        return (serviceUpdateRequest, serviceEntity) -> getOrCreateMerchantDetails(serviceEntity).setAddressCity(serviceUpdateRequest.valueAsString());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateMerchantDetailsAddressCountry() {
        return (serviceUpdateRequest, serviceEntity) -> getOrCreateMerchantDetails(serviceEntity).setAddressCountryCode(serviceUpdateRequest.valueAsString());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateMerchantDetailsAddressPostcode() {
        return (serviceUpdateRequest, serviceEntity) -> getOrCreateMerchantDetails(serviceEntity).setAddressPostcode(serviceUpdateRequest.valueAsString());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateMerchantDetailsEmail() {
        return (serviceUpdateRequest, serviceEntity) -> getOrCreateMerchantDetails(serviceEntity).setEmail(serviceUpdateRequest.valueAsString());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateMerchantDetailsPhone() {
        return (serviceUpdateRequest, serviceEntity) -> getOrCreateMerchantDetails(serviceEntity).setTelephoneNumber(serviceUpdateRequest.valueAsString());
    }

    private java.util.function.BiConsumer<uk.gov.pay.adminusers.model.ServiceUpdateRequest, uk.gov.pay.adminusers.persistence.entity.ServiceEntity> updateMerchantDetailsUrl() {
        return (serviceUpdateRequest, serviceEntity) -> getOrCreateMerchantDetails(serviceEntity).setUrl(serviceUpdateRequest.valueAsString());
    }

    private uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity getOrCreateMerchantDetails(uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity) {
        if (serviceEntity.getMerchantDetailsEntity() != null) {
            return serviceEntity.getMerchantDetailsEntity();
        }
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetailsEntity = new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity();
        serviceEntity.setMerchantDetailsEntity(merchantDetailsEntity);
        return merchantDetailsEntity;
    }
}
