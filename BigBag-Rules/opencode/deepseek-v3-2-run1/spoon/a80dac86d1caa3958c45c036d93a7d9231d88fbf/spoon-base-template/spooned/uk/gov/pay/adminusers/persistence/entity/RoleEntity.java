package uk.gov.pay.adminusers.persistence.entity;
/**
 * Represents a Role of a selfservice user (Government department user)
 * <p>
 *     Marked specifically as read-only.
 *     Roles are only intended to be added manually through migration scripts
 * </p>
 *
 * @see PermissionEntity
 */
@org.eclipse.persistence.annotations.ReadOnly
@javax.persistence.Entity
@javax.persistence.Table(name = "roles")
public class RoleEntity {
    @javax.persistence.Id
    @javax.persistence.Column(name = "id")
    private java.lang.Integer id;

    @javax.persistence.Column(name = "name")
    private java.lang.String name;

    @javax.persistence.Column(name = "description")
    private java.lang.String description;

    @javax.persistence.ManyToMany(fetch = javax.persistence.FetchType.EAGER, targetEntity = uk.gov.pay.adminusers.persistence.entity.PermissionEntity.class)
    @javax.persistence.JoinTable(name = "role_permission", joinColumns = @javax.persistence.JoinColumn(name = "role_id", referencedColumnName = "id"), inverseJoinColumns = @javax.persistence.JoinColumn(name = "permission_id", referencedColumnName = "id"))
    private java.util.Set<uk.gov.pay.adminusers.persistence.entity.PermissionEntity> permissions = new java.util.HashSet<>();

    public RoleEntity() {
        // for jpa
    }

    public RoleEntity(uk.gov.pay.adminusers.model.Role role) {
        this.id = role.getId();
        this.name = role.getName();
        this.description = role.getDescription();
        this.permissions = role.getPermissions().stream().map(uk.gov.pay.adminusers.persistence.entity.PermissionEntity::new).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public java.lang.Integer getId() {
        return id;
    }

    public void setId(java.lang.Integer id) {
        this.id = id;
    }

    public java.lang.String getName() {
        return name;
    }

    public void setName(java.lang.String name) {
        this.name = name;
    }

    public java.lang.String getDescription() {
        return description;
    }

    public void setDescription(java.lang.String description) {
        this.description = description;
    }

    public java.util.Set<uk.gov.pay.adminusers.persistence.entity.PermissionEntity> getPermissions() {
        return permissions;
    }

    public void setPermissions(java.util.Set<uk.gov.pay.adminusers.persistence.entity.PermissionEntity> permissions) {
        this.permissions = permissions;
    }

    public uk.gov.pay.adminusers.model.Role toRole() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.model.Role.role(id, name, description);
        role.setPermissions(permissions.stream().map(uk.gov.pay.adminusers.persistence.entity.PermissionEntity::toPermission).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        return role;
    }

    public boolean isAdmin() {
        return this.id.equals(uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId());
    }
}
