package uk.gov.pay.adminusers.service;
public class AdminUsersExceptionsTest {
    @org.junit.jupiter.api.Test
    public void shouldCreateARoleUnavailabeException() {
        javax.ws.rs.WebApplicationException undefinedRoleException = uk.gov.pay.adminusers.service.AdminUsersExceptions.undefinedRoleException("non-existent-role");
        org.hamcrest.MatcherAssert.assertThat(undefinedRoleException.getResponse().getStatus(), org.hamcrest.core.Is.is(400));
        java.util.Map<java.lang.String, java.util.List<java.lang.String>> entity = ((java.util.Map<java.lang.String, java.util.List<java.lang.String>>) (undefinedRoleException.getResponse().getEntity()));
        org.hamcrest.MatcherAssert.assertThat(entity.get("errors").get(0), org.hamcrest.core.Is.is("role [non-existent-role] not recognised"));
    }

    @org.junit.jupiter.api.Test
    public void shouldCreateAConflictingUsernameException() {
        javax.ws.rs.WebApplicationException undefinedRoleException = uk.gov.pay.adminusers.service.AdminUsersExceptions.conflictingUsername("existing-user");
        org.hamcrest.MatcherAssert.assertThat(undefinedRoleException.getResponse().getStatus(), org.hamcrest.core.Is.is(409));
        java.util.Map<java.lang.String, java.util.List<java.lang.String>> entity = ((java.util.Map<java.lang.String, java.util.List<java.lang.String>>) (undefinedRoleException.getResponse().getEntity()));
        org.hamcrest.MatcherAssert.assertThat(entity.get("errors").get(0), org.hamcrest.core.Is.is("username [existing-user] already exists"));
    }

    @org.junit.jupiter.api.Test
    public void shouldCreateAnInternalServerErrorException() {
        javax.ws.rs.WebApplicationException undefinedRoleException = uk.gov.pay.adminusers.service.AdminUsersExceptions.internalServerError("server error");
        org.hamcrest.MatcherAssert.assertThat(undefinedRoleException.getResponse().getStatus(), org.hamcrest.core.Is.is(500));
        java.util.Map<java.lang.String, java.util.List<java.lang.String>> entity = ((java.util.Map<java.lang.String, java.util.List<java.lang.String>>) (undefinedRoleException.getResponse().getEntity()));
        org.hamcrest.MatcherAssert.assertThat(entity.get("errors").get(0), org.hamcrest.core.Is.is("server error"));
    }
}
