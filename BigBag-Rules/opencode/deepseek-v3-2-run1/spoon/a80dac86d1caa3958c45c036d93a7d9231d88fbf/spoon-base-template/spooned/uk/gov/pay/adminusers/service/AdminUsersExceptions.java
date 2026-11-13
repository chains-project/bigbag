package uk.gov.pay.adminusers.service;
public class AdminUsersExceptions {
    public static javax.ws.rs.WebApplicationException undefinedRoleException(java.lang.String roleName) {
        java.lang.String error = java.lang.String.format("role [%s] not recognised", roleName);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException conflictingUsername(java.lang.String username) {
        java.lang.String error = java.lang.String.format("username [%s] already exists", username);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.CONFLICT.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException conflictingEmail(java.lang.String email) {
        java.lang.String error = java.lang.String.format("email [%s] already exists", email);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.CONFLICT.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException conflictingInvite(java.lang.String email) {
        java.lang.String error = java.lang.String.format("invite with email [%s] already exists", email);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.CONFLICT.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException userAlreadyInService(java.lang.String userExternalId, java.lang.String serviceExternalId) {
        java.lang.String error = java.lang.String.format("user [%s] already in service [%s]", userExternalId, serviceExternalId);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.PRECONDITION_FAILED.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException conflictingServiceGatewayAccountsForUser() {
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException("List of gateway accounts not matching one of the existing services", javax.ws.rs.core.Response.Status.CONFLICT.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException conflictingServiceRoleForUser(java.lang.String userExternalId, java.lang.String serviceExternalId) {
        java.lang.String error = java.lang.String.format("Cannot assign service role. user [%s] already got access to service [%s].", userExternalId, serviceExternalId);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.CONFLICT.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException conflictingServiceForUser(java.lang.String userExternalId, java.lang.String serviceExternalId) {
        java.lang.String error = java.lang.String.format("user [%s] does not belong to service [%s]", userExternalId, serviceExternalId);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.CONFLICT.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException serviceDoesNotExistError(java.lang.String serviceId) {
        java.lang.String error = java.lang.String.format("Service %s provided does not exist", serviceId);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException notFoundException() {
        return new javax.ws.rs.WebApplicationException(javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode()).build());
    }

    public static javax.ws.rs.WebApplicationException notFoundInviteException(java.lang.String inviteCode) {
        java.lang.String error = java.lang.String.format("Invite for code %s provided does not exist", inviteCode);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException invalidOtpAuthCodeInviteException(java.lang.String inviteCode) {
        java.lang.String error = java.lang.String.format("Invite for code %s provided invalid otp auth code", inviteCode);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.UNAUTHORIZED.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException inviteLockedException(java.lang.String inviteCode) {
        java.lang.String error = java.lang.String.format("Invite for code %s locked due to too many otp auth attempts", inviteCode);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.GONE.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException forbiddenOperationException(java.lang.String externalId, java.lang.String operation, java.lang.String externalServiceId) {
        java.lang.String error = java.lang.String.format("user [%s] not authorised to perform operation [%s] in service [%s]", externalId, operation, externalServiceId);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.FORBIDDEN.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException internalServerError(java.lang.String message) {
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(message, javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException adminRoleLimitException(int adminLimit) {
        java.lang.String error = java.lang.String.format("Service admin limit reached. At least %d admin(s) required", adminLimit);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.PRECONDITION_FAILED.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException conflictingServiceGatewayAccounts(java.util.List<java.lang.String> gatewayAccountsIds) {
        java.lang.String error = java.lang.String.format("One or more of the following gateway account ids has already assigned to another service: [%s]", java.lang.String.join(",", gatewayAccountsIds));
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.CONFLICT.getStatusCode());
    }

    public static javax.ws.rs.WebApplicationException userNotificationError(java.lang.Exception cause) {
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException("error sending user notification", javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), cause);
    }

    private static javax.ws.rs.WebApplicationException buildWebApplicationException(java.lang.String error, int status) {
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, status, null);
    }

    private static javax.ws.rs.WebApplicationException buildWebApplicationException(java.lang.String error, int status, java.lang.Exception cause) {
        javax.ws.rs.core.Response response = javax.ws.rs.core.Response.status(status).entity(java.util.Map.of("errors", java.util.List.of(error))).build();
        return new javax.ws.rs.WebApplicationException(cause, response);
    }

    public static javax.ws.rs.WebApplicationException invalidPublicSectorEmail(java.lang.String email) {
        java.lang.String error = java.lang.String.format("Email [%s] is not a valid public sector email", email);
        return uk.gov.pay.adminusers.service.AdminUsersExceptions.buildWebApplicationException(error, javax.ws.rs.core.Response.Status.FORBIDDEN.getStatusCode());
    }
}
