package uk.gov.pay.adminusers.fixtures;
public class RoleDbFixture {
    private final uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseHelper;

    private java.lang.String name = "role-name-" + org.apache.commons.lang3.RandomStringUtils.random(5);

    private RoleDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }

    public static uk.gov.pay.adminusers.fixtures.RoleDbFixture roleDbFixture(uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseHelper) {
        return new uk.gov.pay.adminusers.fixtures.RoleDbFixture(databaseHelper);
    }

    private uk.gov.pay.adminusers.model.Permission aPermission() {
        return uk.gov.pay.adminusers.model.Permission.permission(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), "permission-name-" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), "permission-description" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
    }

    public uk.gov.pay.adminusers.model.Role insertRole() {
        return insert(uk.gov.pay.adminusers.model.Role.role(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), name, "role-description" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid()), aPermission(), aPermission());
    }

    public uk.gov.pay.adminusers.model.Role insertAdmin() {
        return insert(uk.gov.pay.adminusers.model.Role.role(uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId(), "admin", "Administrator"), aPermission(), aPermission());
    }

    public uk.gov.pay.adminusers.model.Role insert(uk.gov.pay.adminusers.model.Role role, uk.gov.pay.adminusers.model.Permission... permissions) {
        for (uk.gov.pay.adminusers.model.Permission permission : permissions) {
            databaseHelper.add(permission);
        }
        role.setPermissions(java.util.Set.of(permissions));
        databaseHelper.add(role);
        return role;
    }

    public uk.gov.pay.adminusers.fixtures.RoleDbFixture withName(java.lang.String name) {
        this.name = name;
        return this;
    }
}
