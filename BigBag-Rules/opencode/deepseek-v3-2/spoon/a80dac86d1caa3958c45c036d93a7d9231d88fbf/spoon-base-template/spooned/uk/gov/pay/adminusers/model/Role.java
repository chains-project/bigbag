package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Role {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.lang.Integer id;

    @io.swagger.v3.oas.annotations.media.Schema(example = "admin")
    private java.lang.String name;// TODO Enum this to admin, view-only, view-and-refund, super-admin


    @io.swagger.v3.oas.annotations.media.Schema(example = "Administrator")
    private java.lang.String description;// TODO Enum this to "Super Admin", "Administrator", "View and Refund", "View only"


    private java.util.Set<uk.gov.pay.adminusers.model.Permission> permissions = new java.util.HashSet<>();

    public static uk.gov.pay.adminusers.model.Role role(java.lang.Integer roleId, java.lang.String name, java.lang.String description) {
        return new uk.gov.pay.adminusers.model.Role(roleId, name, description);
    }

    private Role(java.lang.Integer id, @com.fasterxml.jackson.annotation.JsonProperty("name")
    java.lang.String name, @com.fasterxml.jackson.annotation.JsonProperty("description")
    java.lang.String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public java.lang.Integer getId() {
        return id;
    }

    public java.lang.String getName() {
        return name;
    }

    public java.lang.String getDescription() {
        return description;
    }

    public void setPermissions(java.util.Set<uk.gov.pay.adminusers.model.Permission> permissions) {
        this.permissions = permissions;
    }

    public java.util.Set<uk.gov.pay.adminusers.model.Permission> getPermissions() {
        return permissions;
    }

    @java.lang.Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        uk.gov.pay.adminusers.model.Role role = ((uk.gov.pay.adminusers.model.Role) (o));
        if (!id.equals(role.id)) {
            return false;
        }
        if (!name.equals(role.name)) {
            return false;
        }
        if (!description.equals(role.description)) {
            return false;
        }
        return permissions.equals(role.permissions);
    }

    @java.lang.Override
    public int hashCode() {
        int result = id.hashCode();
        result = (31 * result) + name.hashCode();
        result = (31 * result) + description.hashCode();
        result = (31 * result) + permissions.hashCode();
        return result;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return ((((("Role{" + "id=") + id) + ", name='") + name) + '\'') + '}';
    }
}
