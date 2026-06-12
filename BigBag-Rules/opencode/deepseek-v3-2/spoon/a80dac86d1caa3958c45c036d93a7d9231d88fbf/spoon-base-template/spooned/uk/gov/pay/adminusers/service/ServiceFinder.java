package uk.gov.pay.adminusers.service;
public class ServiceFinder {
    private final uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private final uk.gov.pay.adminusers.service.LinksBuilder linksBuilder;

    @com.google.inject.Inject
    public ServiceFinder(uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao, uk.gov.pay.adminusers.service.LinksBuilder linksBuilder) {
        this.serviceDao = serviceDao;
        this.linksBuilder = linksBuilder;
    }

    public java.util.Optional<uk.gov.pay.adminusers.model.Service> byGatewayAccountId(java.lang.String gatewayAccountId) {
        return serviceDao.findByGatewayAccountId(gatewayAccountId).map(serviceEntity -> linksBuilder.decorate(serviceEntity.toService()));
    }

    public java.util.Optional<uk.gov.pay.adminusers.model.Service> byExternalId(java.lang.String externalId) {
        return serviceDao.findByExternalId(externalId).map(serviceEntity -> linksBuilder.decorate(serviceEntity.toService()));
    }

    public java.util.Map<java.lang.String, java.util.List<?>> bySearchRequest(uk.gov.pay.adminusers.model.ServiceSearchRequest request) {
        var servicesByName = (!org.apache.commons.lang3.StringUtils.isBlank(request.getServiceNameSearchString())) ? streamServiceEntitiesToServices(serviceDao.findByENServiceName(request.getServiceNameSearchString())) : java.util.Collections.emptyList();
        var servicesByMerchantName = (!org.apache.commons.lang3.StringUtils.isBlank(request.getServiceMerchantNameSearchString())) ? streamServiceEntitiesToServices(serviceDao.findByServiceMerchantName(request.getServiceMerchantNameSearchString())) : java.util.Collections.emptyList();
        return java.util.Map.of("name_results", servicesByName, "merchant_results", servicesByMerchantName);
    }

    private java.util.List<uk.gov.pay.adminusers.model.Service> streamServiceEntitiesToServices(java.util.List<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> serviceEntities) {
        return serviceEntities.stream().map(serviceEntity -> linksBuilder.decorate(serviceEntity.toService())).collect(java.util.stream.Collectors.toUnmodifiableList());
    }
}
