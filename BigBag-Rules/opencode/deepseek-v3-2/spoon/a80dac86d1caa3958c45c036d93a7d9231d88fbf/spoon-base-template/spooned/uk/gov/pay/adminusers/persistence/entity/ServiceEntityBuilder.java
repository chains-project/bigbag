package uk.gov.pay.adminusers.persistence.entity;
public final class ServiceEntityBuilder {
    private java.lang.Integer id = org.apache.commons.lang3.RandomUtils.nextInt();

    private java.lang.String externalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();

    private java.lang.String name = uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE;

    private uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetailsEntity = uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder.aMerchantDetailsEntity().build();

    private java.util.Map<java.lang.String, java.lang.Object> customBranding = java.util.Map.of("image_url", "image url", "css_url", "css url");

    private java.util.List<uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity> gatewayAccountIds = new java.util.ArrayList<>();

    private java.util.Set<uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity> serviceName = new java.util.HashSet<>();

    private boolean redirectToServiceImmediatelyOnTerminalState = false;

    private boolean collectBillingAddress = true;

    private uk.gov.pay.adminusers.model.GoLiveStage goLiveStage = uk.gov.pay.adminusers.model.GoLiveStage.NOT_STARTED;

    private boolean experimentalFeaturesEnabled = false;

    private java.time.ZonedDateTime createdDate = java.time.ZonedDateTime.parse("2020-06-29T01:16:00Z");

    private java.time.ZonedDateTime wentLiveDate;

    private java.lang.String sector;

    private uk.gov.pay.adminusers.model.PspTestAccountStage pspTestAccountStage = uk.gov.pay.adminusers.model.PspTestAccountStage.NOT_STARTED;

    private ServiceEntityBuilder() {
    }

    public static uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder aServiceEntity() {
        return new uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder();
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withId(java.lang.Integer id) {
        this.id = id;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withExternalId(java.lang.String externalId) {
        this.externalId = externalId;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withMerchantDetailsEntity(uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetailsEntity) {
        this.merchantDetailsEntity = merchantDetailsEntity;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withCustomBranding(java.util.Map<java.lang.String, java.lang.Object> customBranding) {
        this.customBranding = customBranding;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withGatewayAccounts(java.util.List<uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity> gatewayAccountIds) {
        this.gatewayAccountIds = gatewayAccountIds;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withServiceName(java.util.Set<uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity> serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage language, java.lang.String name) {
        uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity entity = new uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity();
        entity.setLanguage(language);
        entity.setName(name);
        this.serviceName.add(entity);
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withRedirectToServiceImmediatelyOnTerminalState(boolean redirectToServiceImmediatelyOnTerminalState) {
        this.redirectToServiceImmediatelyOnTerminalState = redirectToServiceImmediatelyOnTerminalState;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withCollectBillingAddress(boolean collectBillingAddress) {
        this.collectBillingAddress = collectBillingAddress;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withGoLiveStage(uk.gov.pay.adminusers.model.GoLiveStage goLiveStage) {
        this.goLiveStage = goLiveStage;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withExperimentalFeaturesEnabled(boolean experimentalFeaturesEnabled) {
        this.experimentalFeaturesEnabled = experimentalFeaturesEnabled;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withCreatedDate(java.time.ZonedDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withWentLiveDate(java.time.ZonedDateTime wentLiveDate) {
        this.wentLiveDate = wentLiveDate;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withSector(java.lang.String sector) {
        this.sector = sector;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder withPspTestAccountStage(uk.gov.pay.adminusers.model.PspTestAccountStage pspTestAccountStage) {
        this.pspTestAccountStage = pspTestAccountStage;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntity build() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        serviceEntity.setId(id);
        serviceEntity.setExternalId(externalId);
        serviceEntity.setMerchantDetailsEntity(merchantDetailsEntity);
        serviceEntity.setCustomBranding(customBranding);
        serviceEntity.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, name));
        serviceName.forEach(serviceEntity::addOrUpdateServiceName);
        gatewayAccountIds.forEach(g -> serviceEntity.addGatewayAccountIds(g.getGatewayAccountId()));
        serviceEntity.setRedirectToServiceImmediatelyOnTerminalState(redirectToServiceImmediatelyOnTerminalState);
        serviceEntity.setCollectBillingAddress(collectBillingAddress);
        serviceEntity.setCurrentGoLiveStage(goLiveStage);
        serviceEntity.setExperimentalFeaturesEnabled(experimentalFeaturesEnabled);
        serviceEntity.setCreatedDate(createdDate);
        serviceEntity.setWentLiveDate(wentLiveDate);
        serviceEntity.setSector(sector);
        serviceEntity.setCurrentPspTestAccountStage(pspTestAccountStage);
        return serviceEntity;
    }
}
