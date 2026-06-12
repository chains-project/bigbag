package uk.gov.pay.adminusers.service;
public class ServiceUserRemover {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.ServiceUserRemover.class);

    private static final java.lang.String OPERATION = "remove user";

    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private final uk.gov.pay.adminusers.persistence.dao.ServiceRoleDao serviceRoleDao;

    @javax.inject.Inject
    public ServiceUserRemover(uk.gov.pay.adminusers.persistence.dao.UserDao userDao, uk.gov.pay.adminusers.persistence.dao.ServiceRoleDao serviceRoleDao) {
        this.userDao = userDao;
        this.serviceRoleDao = serviceRoleDao;
    }

    public void remove(java.lang.String userExternalId, java.lang.String removerExternalId, java.lang.String serviceExternalId) {
        uk.gov.pay.adminusers.service.ServiceUserRemover.LOGGER.info("User remove from service requested - serviceId={}, removerId={}, userId={}", serviceExternalId, removerExternalId, userExternalId);
        uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity userServiceRoleToRemove = getServiceRoleEntityOf(userExternalId, serviceExternalId);
        checkRemoverIsAdmin(removerExternalId, serviceExternalId).orElseThrow(() -> uk.gov.pay.adminusers.service.AdminUsersExceptions.forbiddenOperationException(userExternalId, uk.gov.pay.adminusers.service.ServiceUserRemover.OPERATION, serviceExternalId));
        serviceRoleDao.remove(userServiceRoleToRemove);
    }

    public void removeWithoutAdminCheck(java.lang.String userExternalId, java.lang.String serviceExternalId) {
        uk.gov.pay.adminusers.service.ServiceUserRemover.LOGGER.info("User remove from toolbox requested - serviceId={}, userId={}", serviceExternalId, userExternalId);
        uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity userServiceRoleToRemove = getServiceRoleEntityOf(userExternalId, serviceExternalId);
        serviceRoleDao.remove(userServiceRoleToRemove);
    }

    private java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity> checkRemoverIsAdmin(java.lang.String removerExternalId, java.lang.String serviceExternalId) {
        return userDao.findByExternalId(removerExternalId).flatMap(removerEntity -> removerEntity.getServicesRole(serviceExternalId)).filter(isRoleAdmin());
    }

    private uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity getServiceRoleEntityOf(java.lang.String userExternalId, java.lang.String serviceExternalId) {
        return userDao.findByExternalId(userExternalId).map(userEntity -> userEntity.getServicesRole(serviceExternalId).orElseThrow(uk.gov.pay.adminusers.service.AdminUsersExceptions::notFoundException)).orElseThrow(uk.gov.pay.adminusers.service.AdminUsersExceptions::notFoundException);
    }

    private java.util.function.Predicate<uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity> isRoleAdmin() {
        return serviceRoleEntity -> serviceRoleEntity.getRole().isAdmin();
    }
}
