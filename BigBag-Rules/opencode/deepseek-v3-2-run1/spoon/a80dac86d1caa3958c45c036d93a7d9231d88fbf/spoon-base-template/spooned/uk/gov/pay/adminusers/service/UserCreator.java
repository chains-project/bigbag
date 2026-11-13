package uk.gov.pay.adminusers.service;
public class UserCreator {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.UserCreator.class);

    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private final uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao;

    private final uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private final uk.gov.pay.adminusers.service.PasswordHasher passwordHasher;

    private final uk.gov.pay.adminusers.service.LinksBuilder linksBuilder;

    @com.google.inject.Inject
    public UserCreator(uk.gov.pay.adminusers.persistence.dao.UserDao userDao, uk.gov.pay.adminusers.persistence.dao.RoleDao roleDao, uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao, uk.gov.pay.adminusers.service.PasswordHasher passwordHasher, uk.gov.pay.adminusers.service.LinksBuilder linksBuilder) {
        this.userDao = userDao;
        this.roleDao = roleDao;
        this.serviceDao = serviceDao;
        this.passwordHasher = passwordHasher;
        this.linksBuilder = linksBuilder;
    }

    @com.google.inject.persist.Transactional
    public uk.gov.pay.adminusers.model.User doCreate(uk.gov.pay.adminusers.model.CreateUserRequest userRequest, java.lang.String roleName) {
        return roleDao.findByRoleName(roleName).map(roleEntity -> {
            uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(userRequest);
            userEntity.setPassword(passwordHasher.hash(userRequest.getPassword()));
            if (uk.gov.pay.adminusers.service.UserCreator.hasServiceIds(userRequest)) {
                addServiceRoleToUser(userEntity, roleEntity, userRequest.getServiceExternalIds());
            } else if (uk.gov.pay.adminusers.service.UserCreator.hasGatewayAccountIds(userRequest)) {
                addServiceFromGatewayAccountsToUser(userEntity, roleEntity, userRequest.getGatewayAccountIds());
            }
            userDao.persist(userEntity);
            return linksBuilder.decorate(userEntity.toUser());
        }).orElseThrow(() -> uk.gov.pay.adminusers.service.AdminUsersExceptions.undefinedRoleException(roleName));
    }

    private static boolean hasServiceIds(uk.gov.pay.adminusers.model.CreateUserRequest userRequest) {
        return (userRequest.getServiceExternalIds() != null) && (!userRequest.getServiceExternalIds().isEmpty());
    }

    private static boolean hasGatewayAccountIds(uk.gov.pay.adminusers.model.CreateUserRequest userRequest) {
        return (userRequest.getGatewayAccountIds() != null) && (!userRequest.getGatewayAccountIds().isEmpty());
    }

    private void addServiceRoleToUser(uk.gov.pay.adminusers.persistence.entity.UserEntity user, uk.gov.pay.adminusers.persistence.entity.RoleEntity role, java.util.List<java.lang.String> serviceExternalIds) {
        serviceExternalIds.forEach(serviceExternalId -> serviceDao.findByExternalId(serviceExternalId).map(serviceEntity -> {
            uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRole = new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(serviceEntity, role);
            serviceRole.setUser(user);
            user.addServiceRole(serviceRole);
            return null;
        }).orElseGet(() -> {
            uk.gov.pay.adminusers.service.UserCreator.LOGGER.error("Unable to assign service with external id {} to user, as it does not exist", serviceExternalId);
            return null;
        }));
    }

    private void addServiceFromGatewayAccountsToUser(uk.gov.pay.adminusers.persistence.entity.UserEntity user, uk.gov.pay.adminusers.persistence.entity.RoleEntity role, java.util.List<java.lang.String> gatewayAccountIds) {
        uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRole = getServiceAssignedTo(gatewayAccountIds).map(serviceEntity -> new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(serviceEntity, role)).orElseGet(() -> {
            uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity(gatewayAccountIds);
            service.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
            serviceDao.persist(service);
            return new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(service, role);
        });
        serviceRole.setUser(user);
        user.addServiceRole(serviceRole);
    }

    private java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> getServiceAssignedTo(java.util.List<java.lang.String> gatewayAccountIds) {
        for (java.lang.String gatewayAccountId : gatewayAccountIds) {
            java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> serviceOptional = serviceDao.findByGatewayAccountId(gatewayAccountId);
            if (serviceOptional.isPresent()) {
                if (serviceOptional.get().hasExactGatewayAccountIds(gatewayAccountIds)) {
                    return serviceOptional;
                } else {
                    throw uk.gov.pay.adminusers.service.AdminUsersExceptions.conflictingServiceGatewayAccountsForUser();
                }
            }
        }
        return java.util.Optional.empty();
    }
}
