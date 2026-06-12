package uk.gov.pay.adminusers.service;
public class ServiceRoleUpdater {
    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private final uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private final uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao;

    private final uk.gov.pay.adminusers.service.LinksBuilder linksBuilder;

    private final java.lang.Integer adminsPerServiceLimit = 1;

    @com.google.inject.Inject
    public ServiceRoleUpdater(uk.gov.pay.adminusers.persistence.dao.UserDao userDao, uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao, uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao, uk.gov.pay.adminusers.service.LinksBuilder linksBuilder) {
        this.userDao = userDao;
        this.serviceDao = serviceDao;
        this.roleDao = roleDao;
        this.linksBuilder = linksBuilder;
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.User> doUpdate(java.lang.String userExternalId, java.lang.String serviceId, java.lang.String roleName) {
        java.lang.String serviceExternalId = serviceId;
        if (org.apache.commons.lang3.StringUtils.isNumeric(serviceId)) {
            serviceExternalId = serviceDao.findById(java.lang.Integer.valueOf(serviceId)).map(uk.gov.pay.adminusers.persistence.entity.ServiceEntity::getExternalId).orElseThrow(() -> uk.gov.pay.adminusers.service.AdminUsersExceptions.serviceDoesNotExistError(serviceId));
        }
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userMaybe = userDao.findByExternalId(userExternalId);
        if (!userMaybe.isPresent()) {
            return java.util.Optional.empty();
        }
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = userMaybe.get();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.RoleEntity> roleMaybe = roleDao.findByRoleName(roleName);
        if (!roleMaybe.isPresent()) {
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.undefinedRoleException(roleName);
        }
        uk.gov.pay.adminusers.persistence.entity.RoleEntity targetRoleEntity = roleMaybe.get();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity> servicesRoleMaybe = userEntity.getServicesRole(serviceExternalId);
        if (!servicesRoleMaybe.isPresent()) {
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.conflictingServiceForUser(userEntity.getExternalId(), serviceExternalId);
        }
        uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRoleEntity = servicesRoleMaybe.get();
        uk.gov.pay.adminusers.persistence.entity.RoleEntity currentRoleEntity = serviceRoleEntity.getRole();
        if (currentRoleEntity.isAdmin() && (!targetRoleEntity.isAdmin())) {
            if (serviceDao.countOfUsersWithRoleForService(serviceExternalId, uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId()) <= adminsPerServiceLimit) {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.adminRoleLimitException(adminsPerServiceLimit);
            }
        }
        serviceRoleEntity.setRole(targetRoleEntity);
        userEntity.addServiceRole(serviceRoleEntity);
        userDao.persist(userEntity);
        return java.util.Optional.of(linksBuilder.decorate(userEntity.toUser()));
    }
}
