package uk.gov.pay.adminusers.fixtures;
public class ServiceDbFixture {
    private final uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseHelper;

    private java.util.List<java.lang.String> gatewayAccountIds = java.util.List.of(java.lang.String.valueOf(org.apache.commons.lang3.RandomUtils.nextInt()));

    private java.lang.Integer id;

    private java.lang.String externalId;

    private java.lang.String name = uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE;

    private uk.gov.pay.adminusers.model.MerchantDetails merchantDetails = new uk.gov.pay.adminusers.model.MerchantDetails("name", null, "line1", null, "city", "postcode", "country", null, null);

    private boolean collectBillingAddress = true;

    private boolean experimentalFeaturesEnabled = false;

    private uk.gov.pay.adminusers.model.GoLiveStage goLiveStage = uk.gov.pay.adminusers.model.GoLiveStage.NOT_STARTED;

    private java.util.Map<java.lang.String, java.lang.Object> customBranding;

    private uk.gov.pay.adminusers.model.PspTestAccountStage currentPspTestAccountStage = uk.gov.pay.adminusers.model.PspTestAccountStage.NOT_STARTED;

    private ServiceDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }

    public static uk.gov.pay.adminusers.fixtures.ServiceDbFixture serviceDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper) {
        return new uk.gov.pay.adminusers.fixtures.ServiceDbFixture(databaseTestHelper);
    }

    public uk.gov.pay.adminusers.fixtures.ServiceDbFixture withName(java.lang.String name) {
        this.name = name;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.ServiceDbFixture withGatewayAccountIds(java.lang.String... gatewayAccountIds) {
        this.gatewayAccountIds = java.util.Arrays.asList(gatewayAccountIds);
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.ServiceDbFixture withMerchantDetails(uk.gov.pay.adminusers.model.MerchantDetails merchantDetails) {
        this.merchantDetails = merchantDetails;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.ServiceDbFixture withCollectBillingAddress(boolean collectBillingAddress) {
        this.collectBillingAddress = collectBillingAddress;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.ServiceDbFixture withGoLiveStage(uk.gov.pay.adminusers.model.GoLiveStage goLiveStage) {
        this.goLiveStage = goLiveStage;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.ServiceDbFixture withCustomBranding(java.lang.String cssUrl, java.lang.String imageUrl) {
        this.customBranding = java.util.Map.of("css_url", cssUrl, "image_url", imageUrl);
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.ServiceDbFixture withExperimentalFeaturesEnabled(boolean experimentalFeaturesEnabled) {
        this.experimentalFeaturesEnabled = experimentalFeaturesEnabled;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.ServiceDbFixture withCurrentPspTestAccountStage(uk.gov.pay.adminusers.model.PspTestAccountStage currentPspTestAccountStage) {
        this.currentPspTestAccountStage = currentPspTestAccountStage;
        return this;
    }

    public uk.gov.pay.adminusers.model.Service insertService() {
        int serviceId = (id == null) ? org.apache.commons.lang3.RandomUtils.nextInt() : id;
        java.lang.String extId = (externalId == null) ? uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid() : externalId;
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.model.Service.from(serviceId, extId, new uk.gov.pay.adminusers.model.ServiceName(name));
        service.setMerchantDetails(merchantDetails);
        service.setCollectBillingAddress(collectBillingAddress);
        service.setGoLiveStage(goLiveStage);
        service.setCustomBranding(customBranding);
        service.setExperimentalFeaturesEnabled(experimentalFeaturesEnabled);
        service.setCurrentPspTestAccountStage(currentPspTestAccountStage);
        databaseHelper.addService(service, gatewayAccountIds.toArray(new java.lang.String[0]));
        return service;
    }

    public uk.gov.pay.adminusers.fixtures.ServiceDbFixture withId(int id) {
        this.id = id;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.ServiceDbFixture withExternalId(java.lang.String externalId) {
        this.externalId = externalId;
        return this;
    }
}
