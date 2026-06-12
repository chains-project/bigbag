package uk.gov.pay.adminusers.service;
public class ServiceRoleCreator {
    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private final uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private final uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao;

    private final uk.gov.pay.adminusers.service.LinksBuilder linksBuilder;

    @com.google.inject.Inject
    public ServiceRoleCreator(uk.gov.pay.adminusers.persistence.dao.UserDao userDao, uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao, uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao, uk.gov.pay.adminusers.service.LinksBuilder linksBuilder) {
        this.userDao = userDao;
        this.serviceDao = serviceDao;
        this.roleDao = roleDao;
        this.linksBuilder = linksBuilder;
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<uk.gov.pay.adminusers.model.User> doCreate(java.lang.String userExternalId, java.lang.String serviceExternalId, java.lang.String roleName) {
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userMaybe = userDao.findByExternalId(userExternalId);
        if (!userMaybe.isPresent()) {
            return java.util.Optional.empty();
        }
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> serviceMaybe = serviceDao.findByExternalId(serviceExternalId);
        if (!serviceMaybe.isPresent()) {
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.serviceDoesNotExistError(serviceExternalId);
        }
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.RoleEntity> roleMaybe = roleDao.findByRoleName(roleName);
        if (!roleMaybe.isPresent()) {
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.undefinedRoleException(roleName);
        }
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = userMaybe.get();
        userEntity.getServicesRole(serviceExternalId).ifPresent(serviceRoleEntity -> {
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.conflictingServiceRoleForUser(userExternalId, serviceExternalId);
        });
        userEntity.addServiceRole(new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(serviceMaybe.get(), roleMaybe.get()));
        userDao.merge(userEntity);
        return java.util.Optional.of(linksBuilder.decorate(userEntity.toUser()));
    }
}
