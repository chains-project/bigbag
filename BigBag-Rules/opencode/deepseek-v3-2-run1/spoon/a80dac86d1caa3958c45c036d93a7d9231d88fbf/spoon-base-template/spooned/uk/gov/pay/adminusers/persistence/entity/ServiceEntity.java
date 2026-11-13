package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Entity
@javax.persistence.Table(name = "services")
@javax.persistence.SequenceGenerator(name = "services_seq_gen", sequenceName = "services_id_seq", allocationSize = 1)
public class ServiceEntity {
    public static final java.lang.String DEFAULT_BILLING_ADDRESS_COUNTRY = "GB";

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.SEQUENCE, generator = "services_seq_gen")
    private java.lang.Integer id;

    @javax.persistence.Column(name = "external_id")
    private java.lang.String externalId;

    @javax.persistence.Column(name = "redirect_to_service_immediately_on_terminal_state")
    private boolean redirectToServiceImmediatelyOnTerminalState = false;

    @javax.persistence.Column(name = "collect_billing_address")
    private boolean collectBillingAddress = true;

    @javax.persistence.Column(name = "default_billing_address_country")
    private java.lang.String defaultBillingAddressCountry = uk.gov.pay.adminusers.persistence.entity.ServiceEntity.DEFAULT_BILLING_ADDRESS_COUNTRY;

    @javax.persistence.Embedded
    private uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetailsEntity;

    @javax.persistence.Column(name = "custom_branding", columnDefinition = "json")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.CustomBrandingConverter.class)
    private java.util.Map<java.lang.String, java.lang.Object> customBranding;

    @javax.persistence.OneToMany(mappedBy = "service", targetEntity = uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity.class, fetch = javax.persistence.FetchType.EAGER, cascade = javax.persistence.CascadeType.PERSIST)
    private java.util.List<uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity> gatewayAccountIds = new java.util.ArrayList<>();

    @javax.persistence.OneToMany(mappedBy = "service", targetEntity = uk.gov.pay.adminusers.persistence.entity.InviteEntity.class, fetch = javax.persistence.FetchType.LAZY)
    private java.util.List<uk.gov.pay.adminusers.persistence.entity.InviteEntity> invites = new java.util.ArrayList<>();

    @javax.persistence.OneToMany(mappedBy = "service", cascade = javax.persistence.CascadeType.ALL, orphanRemoval = true, fetch = javax.persistence.FetchType.EAGER)
    private java.util.Set<uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity> serviceNames = new java.util.HashSet<>();

    @javax.persistence.Column(name = "current_go_live_stage")
    @javax.persistence.Enumerated(javax.persistence.EnumType.STRING)
    private uk.gov.pay.adminusers.model.GoLiveStage currentGoLiveStage = uk.gov.pay.adminusers.model.GoLiveStage.NOT_STARTED;

    @javax.persistence.Column(name = "experimental_features_enabled")
    private boolean experimentalFeaturesEnabled = false;

    @javax.persistence.Column(name = "agent_initiated_moto_enabled")
    private boolean agentInitiatedMotoEnabled;

    @javax.persistence.Column(name = "sector")
    private java.lang.String sector;

    @javax.persistence.Column(name = "internal")
    private boolean internal;

    @javax.persistence.Column(name = "archived")
    private boolean archived;

    @javax.persistence.Column(name = "created_date")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime createdDate = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);

    @javax.persistence.Column(name = "went_live_date")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime wentLiveDate;

    @javax.persistence.Column(name = "current_psp_test_account_stage")
    @javax.persistence.Enumerated(javax.persistence.EnumType.STRING)
    private uk.gov.pay.adminusers.model.PspTestAccountStage currentPspTestAccountStage = uk.gov.pay.adminusers.model.PspTestAccountStage.NOT_STARTED;

    public ServiceEntity() {
    }

    public ServiceEntity(java.util.List<java.lang.String> gatewayAccountIds) {
        this.gatewayAccountIds.clear();
        this.externalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        this.redirectToServiceImmediatelyOnTerminalState = false;
        this.collectBillingAddress = true;
        populateGatewayAccountIds(gatewayAccountIds);
    }

    public java.lang.String getExternalId() {
        return externalId;
    }

    public void setExternalId(java.lang.String externalId) {
        this.externalId = externalId;
    }

    public java.lang.Integer getId() {
        return id;
    }

    public void setId(java.lang.Integer id) {
        this.id = id;
    }

    public boolean isRedirectToServiceImmediatelyOnTerminalState() {
        return redirectToServiceImmediatelyOnTerminalState;
    }

    public void setRedirectToServiceImmediatelyOnTerminalState(boolean redirectToServiceImmediatelyOnTerminalState) {
        this.redirectToServiceImmediatelyOnTerminalState = redirectToServiceImmediatelyOnTerminalState;
    }

    public boolean isCollectBillingAddress() {
        return collectBillingAddress;
    }

    public void setCollectBillingAddress(boolean collectBillingAddress) {
        this.collectBillingAddress = collectBillingAddress;
    }

    public java.lang.String getDefaultBillingAddressCountry() {
        return defaultBillingAddressCountry;
    }

    public void setDefaultBillingAddressCountry(java.lang.String defaultBillingAddressCountry) {
        this.defaultBillingAddressCountry = defaultBillingAddressCountry;
    }

    public uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity getMerchantDetailsEntity() {
        return merchantDetailsEntity;
    }

    public void setMerchantDetailsEntity(uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetailsEntity) {
        this.merchantDetailsEntity = merchantDetailsEntity;
    }

    public java.util.List<uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity> getGatewayAccountIds() {
        return java.util.List.copyOf(this.gatewayAccountIds);
    }

    public uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity getGatewayAccountId() {
        return gatewayAccountIds.get(0);
    }

    public java.util.List<uk.gov.pay.adminusers.persistence.entity.InviteEntity> getInvites() {
        return invites;
    }

    public void addGatewayAccountIds(java.lang.String... gatewayAccountIds) {
        populateGatewayAccountIds(java.util.Arrays.asList(gatewayAccountIds));
    }

    public java.util.Map<java.lang.String, java.lang.Object> getCustomBranding() {
        return customBranding;
    }

    public void setCustomBranding(java.util.Map<java.lang.String, java.lang.Object> customBranding) {
        this.customBranding = customBranding;
    }

    public uk.gov.pay.adminusers.model.GoLiveStage getCurrentGoLiveStage() {
        return currentGoLiveStage;
    }

    public void setCurrentGoLiveStage(uk.gov.pay.adminusers.model.GoLiveStage currentGoLiveStage) {
        this.currentGoLiveStage = currentGoLiveStage;
    }

    public boolean isExperimentalFeaturesEnabled() {
        return experimentalFeaturesEnabled;
    }

    public void setExperimentalFeaturesEnabled(boolean experimentalFeaturesEnabled) {
        this.experimentalFeaturesEnabled = experimentalFeaturesEnabled;
    }

    public boolean isAgentInitiatedMotoEnabled() {
        return agentInitiatedMotoEnabled;
    }

    public void setAgentInitiatedMotoEnabled(boolean agentInitiatedMotoEnabled) {
        this.agentInitiatedMotoEnabled = agentInitiatedMotoEnabled;
    }

    public java.lang.String getSector() {
        return sector;
    }

    public void setSector(java.lang.String sector) {
        this.sector = sector;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public java.time.ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(java.time.ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public java.time.ZonedDateTime getWentLiveDate() {
        return wentLiveDate;
    }

    public void setWentLiveDate(java.time.ZonedDateTime wentLiveDate) {
        this.wentLiveDate = wentLiveDate;
    }

    public uk.gov.pay.adminusers.model.PspTestAccountStage getCurrentPspTestAccountStage() {
        return currentPspTestAccountStage;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntity setCurrentPspTestAccountStage(uk.gov.pay.adminusers.model.PspTestAccountStage currentPspTestAccountStage) {
        this.currentPspTestAccountStage = currentPspTestAccountStage;
        return this;
    }

    public uk.gov.pay.adminusers.model.Service toService() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.model.Service.from(id, externalId, uk.gov.pay.adminusers.model.ServiceName.from(getServiceNames().values()), this.redirectToServiceImmediatelyOnTerminalState, this.collectBillingAddress, this.defaultBillingAddressCountry, this.currentGoLiveStage, this.experimentalFeaturesEnabled, this.agentInitiatedMotoEnabled, this.sector, this.internal, this.archived, this.createdDate, this.wentLiveDate, this.currentPspTestAccountStage);
        service.setGatewayAccountIds(gatewayAccountIds.stream().map(uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity::getGatewayAccountId).collect(java.util.stream.Collectors.toUnmodifiableList()));
        service.setCustomBranding(this.customBranding);
        if (this.merchantDetailsEntity != null) {
            service.setMerchantDetails(this.merchantDetailsEntity.toMerchantDetails());
        }
        return service;
    }

    public boolean hasExactGatewayAccountIds(java.util.List<java.lang.String> gatewayAccountIds) {
        if (this.gatewayAccountIds.size() != gatewayAccountIds.size()) {
            return false;
        }
        for (uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity gatewayAccountIdEntity : this.gatewayAccountIds) {
            if (!gatewayAccountIds.contains(gatewayAccountIdEntity.getGatewayAccountId())) {
                return false;
            }
        }
        return true;
    }

    public static uk.gov.pay.adminusers.persistence.entity.ServiceEntity from(uk.gov.pay.adminusers.model.Service service) {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        service.getServiceNames().forEach((languageCode, serviceName) -> serviceEntity.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.fromIso639AlphaTwoCode(languageCode), serviceName)));
        serviceEntity.setExternalId(service.getExternalId());
        serviceEntity.setRedirectToServiceImmediatelyOnTerminalState(service.isRedirectToServiceImmediatelyOnTerminalState());
        serviceEntity.setCollectBillingAddress(service.isCollectBillingAddress());
        return serviceEntity;
    }

    public void addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity newServiceName) {
        newServiceName.setService(this);
        serviceNames.stream().filter(serviceName -> serviceName.getLanguage().equals(newServiceName.getLanguage())).findFirst().ifPresentOrElse(existingServiceName -> existingServiceName.setName(newServiceName.getName()), () -> serviceNames.add(newServiceName));
    }

    public java.util.Map<uk.gov.service.payments.commons.model.SupportedLanguage, uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity> getServiceNames() {
        return serviceNames.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity::getLanguage, java.util.function.Function.identity()));
    }

    private void populateGatewayAccountIds(java.util.List<java.lang.String> gatewayAccountIds) {
        for (java.lang.String gatewayAccountId : gatewayAccountIds) {
            this.gatewayAccountIds.add(new uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity(gatewayAccountId, this));
        }
    }
}
