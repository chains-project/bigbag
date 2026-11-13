package uk.gov.pay.adminusers.service;
public class ServiceCreator {
    private final uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private final uk.gov.pay.adminusers.service.LinksBuilder linksBuilder;

    @com.google.inject.Inject
    public ServiceCreator(uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao, uk.gov.pay.adminusers.service.LinksBuilder linksBuilder) {
        this.serviceDao = serviceDao;
        this.linksBuilder = linksBuilder;
    }

    @com.google.inject.persist.Transactional
    public uk.gov.pay.adminusers.model.Service doCreate(java.util.List<java.lang.String> gatewayAccountIds, java.util.Map<uk.gov.service.payments.commons.model.SupportedLanguage, java.lang.String> serviceName) {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntity.from(uk.gov.pay.adminusers.model.Service.from());
        serviceName.forEach((language, name) -> serviceEntity.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(language, name)));
        if (!gatewayAccountIds.isEmpty()) {
            if (serviceDao.checkIfGatewayAccountsUsed(gatewayAccountIds)) {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.conflictingServiceGatewayAccounts(gatewayAccountIds);
            }
            serviceEntity.addGatewayAccountIds(gatewayAccountIds.toArray(new java.lang.String[0]));
        }
        serviceDao.persist(serviceEntity);
        return linksBuilder.decorate(serviceEntity.toService());
    }
}
