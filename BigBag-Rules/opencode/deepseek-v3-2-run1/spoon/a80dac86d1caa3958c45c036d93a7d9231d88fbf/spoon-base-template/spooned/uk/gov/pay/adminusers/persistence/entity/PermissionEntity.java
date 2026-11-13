package uk.gov.pay.adminusers.persistence.entity;
/**
 * Represents a single Permission assignable to a selfservice user (Government department user)
 * <p>
 *     Marked specifically as read-only.
 *     Permissions are only intended to be added manually through migration scripts
 * </p>
 *
 * @see RoleEntity
 */
@org.eclipse.persistence.annotations.ReadOnly
@javax.persistence.Entity
@javax.persistence.Table(name = "permissions")
public class PermissionEntity {
    @javax.persistence.Id
    @javax.persistence.Column(name = "id")
    private java.lang.Integer id;

    @javax.persistence.Column(name = "name")
    private java.lang.String name;

    @javax.persistence.Column(name = "description")
    private java.lang.String description;

    public PermissionEntity() {
        // for jpa
    }

    public PermissionEntity(uk.gov.pay.adminusers.model.Permission permission) {
        this.id = permission.getId();
        this.name = permission.getName();
        this.description = permission.getDescription();
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

    public uk.gov.pay.adminusers.model.Permission toPermission() {
        return uk.gov.pay.adminusers.model.Permission.permission(id, name, description);
    }
}
