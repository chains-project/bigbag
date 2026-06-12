package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Entity
@javax.persistence.Table(name = "user_services_roles")
public class ServiceRoleEntity {
    @javax.persistence.EmbeddedId
    private uk.gov.pay.adminusers.persistence.entity.UserServiceId userServiceId;

    @javax.persistence.ManyToOne
    @javax.persistence.MapsId("userId")
    @javax.persistence.JoinColumn(name = "user_id", referencedColumnName = "id")
    private uk.gov.pay.adminusers.persistence.entity.UserEntity user;

    @javax.persistence.ManyToOne(fetch = javax.persistence.FetchType.LAZY)
    @javax.persistence.MapsId("serviceId")
    @javax.persistence.JoinColumn(name = "service_id", referencedColumnName = "id")
    private uk.gov.pay.adminusers.persistence.entity.ServiceEntity service;

    @javax.persistence.ManyToOne(fetch = javax.persistence.FetchType.LAZY)
    @javax.persistence.JoinColumn(name = "role_id", referencedColumnName = "id")
    private uk.gov.pay.adminusers.persistence.entity.RoleEntity role;

    public ServiceRoleEntity() {
    }

    public ServiceRoleEntity(uk.gov.pay.adminusers.persistence.entity.ServiceEntity service, uk.gov.pay.adminusers.persistence.entity.RoleEntity role) {
        this.service = service;
        this.role = role;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntity getService() {
        return service;
    }

    public void setService(uk.gov.pay.adminusers.persistence.entity.ServiceEntity service) {
        this.service = service;
    }

    public uk.gov.pay.adminusers.persistence.entity.RoleEntity getRole() {
        return role;
    }

    public void setRole(uk.gov.pay.adminusers.persistence.entity.RoleEntity role) {
        this.role = role;
    }

    public uk.gov.pay.adminusers.persistence.entity.UserServiceId getUserServiceId() {
        return userServiceId;
    }

    public void setUserServiceId(uk.gov.pay.adminusers.persistence.entity.UserServiceId userServiceId) {
        this.userServiceId = userServiceId;
    }

    public uk.gov.pay.adminusers.persistence.entity.UserEntity getUser() {
        return user;
    }

    public void setUser(uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity) {
        this.user = userEntity;
    }

    public uk.gov.pay.adminusers.model.ServiceRole toServiceRole() {
        return uk.gov.pay.adminusers.model.ServiceRole.from(getService().toService(), getRole().toRole());
    }
}
