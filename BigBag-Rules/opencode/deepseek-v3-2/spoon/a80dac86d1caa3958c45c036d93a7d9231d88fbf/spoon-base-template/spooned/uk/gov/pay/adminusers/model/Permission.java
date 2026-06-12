package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Permission {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.lang.Integer id;

    @io.swagger.v3.oas.annotations.media.Schema(example = "tokens:delete")
    private java.lang.String name;

    @io.swagger.v3.oas.annotations.media.Schema(example = "Revokekey")
    private java.lang.String description;

    public static uk.gov.pay.adminusers.model.Permission permission(java.lang.Integer permissionId, java.lang.String name, java.lang.String description) {
        return new uk.gov.pay.adminusers.model.Permission(permissionId, name, description);
    }

    private Permission(java.lang.Integer id, @com.fasterxml.jackson.annotation.JsonProperty("name")
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

    @java.lang.Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        uk.gov.pay.adminusers.model.Permission that = ((uk.gov.pay.adminusers.model.Permission) (o));
        if (!id.equals(that.id)) {
            return false;
        }
        if (!name.equals(that.name)) {
            return false;
        }
        return description.equals(that.description);
    }

    @java.lang.Override
    public int hashCode() {
        int result = id.hashCode();
        result = (31 * result) + name.hashCode();
        result = (31 * result) + description.hashCode();
        return result;
    }
}
