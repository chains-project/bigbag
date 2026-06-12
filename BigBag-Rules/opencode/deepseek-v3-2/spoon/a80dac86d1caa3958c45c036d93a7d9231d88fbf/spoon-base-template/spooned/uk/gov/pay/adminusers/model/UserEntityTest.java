package uk.gov.pay.adminusers.model;
class UserEntityTest {
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.jupiter.api.Test
    void shouldConstructAUser_fromMinimalValidUserJson() throws java.lang.Exception {
        java.lang.String minimumUserJson = (((("{" + "\"username\": \"a-username\",") + "\"telephone_number\": \"+441134960000\",") + "\"gateway_account_ids\": [\"1\", \"2\"],") + "\"email\": \"email@example.com\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.model.UserEntityTest.objectMapper.readTree(minimumUserJson);
        uk.gov.pay.adminusers.model.CreateUserRequest createUserRequest = uk.gov.pay.adminusers.model.CreateUserRequest.from(jsonNode);
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = uk.gov.pay.adminusers.persistence.entity.UserEntity.from(createUserRequest);
        org.junit.jupiter.api.Assertions.assertEquals(createUserRequest.getUsername(), userEntity.getUsername());
        org.junit.jupiter.api.Assertions.assertEquals(createUserRequest.getPassword(), userEntity.getPassword());
        org.junit.jupiter.api.Assertions.assertEquals(createUserRequest.getOtpKey(), userEntity.getOtpKey());
        org.junit.jupiter.api.Assertions.assertEquals(createUserRequest.getTelephoneNumber(), userEntity.getTelephoneNumber());
        org.junit.jupiter.api.Assertions.assertEquals(createUserRequest.getEmail(), userEntity.getEmail());
        org.junit.jupiter.api.Assertions.assertEquals(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS, userEntity.getSecondFactor());
        org.hamcrest.MatcherAssert.assertThat(userEntity.getCreatedAt(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
        org.hamcrest.MatcherAssert.assertThat(userEntity.getUpdatedAt(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
        // Since role and gatewayAccountId will be set up after won't be unit-testing from JSON to entity.
    }

    @org.junit.jupiter.api.Test
    void creatingAUser_shouldSetGatewayAccountAndRole_whenServiceRoleIsSet() {
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        java.lang.String gatewayAccountId = "1";
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity service = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity(java.util.List.of(gatewayAccountId));
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.model.Role.role(1, "role", "hey");
        role.setPermissions(java.util.Set.of(uk.gov.pay.adminusers.model.Permission.permission(1, "perm1", "perm1 desc"), uk.gov.pay.adminusers.model.Permission.permission(2, "perm2", "perm2 desc")));
        uk.gov.pay.adminusers.persistence.entity.RoleEntity roleEntity = new uk.gov.pay.adminusers.persistence.entity.RoleEntity(role);
        uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRole = new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(service, roleEntity);
        userEntity.addServiceRole(serviceRole);
        org.hamcrest.MatcherAssert.assertThat(userEntity.getRoles().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(userEntity.getRoles().get(0).getId(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(userEntity.getGatewayAccountId(), org.hamcrest.core.Is.is(gatewayAccountId));
    }
}
