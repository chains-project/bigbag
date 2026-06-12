package uk.gov.pay.adminusers.service;
public class LinksBuilder {
    private final java.lang.String baseUrl;

    public LinksBuilder(java.lang.String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public uk.gov.pay.adminusers.model.User decorate(uk.gov.pay.adminusers.model.User user) {
        java.net.URI uri = javax.ws.rs.core.UriBuilder.fromUri(baseUrl).path(uk.gov.pay.adminusers.resources.UserResource.USERS_RESOURCE).path(user.getExternalId()).build();
        uk.gov.pay.adminusers.model.Link selfLink = uk.gov.pay.adminusers.model.Link.from(uk.gov.pay.adminusers.model.Link.Rel.SELF, "GET", uri.toString());
        user.setLinks(java.util.List.of(selfLink));
        return user;
    }

    public uk.gov.pay.adminusers.model.Service decorate(uk.gov.pay.adminusers.model.Service service) {
        java.net.URI uri = javax.ws.rs.core.UriBuilder.fromUri(baseUrl).path(uk.gov.pay.adminusers.resources.ServiceResource.SERVICES_RESOURCE).path(java.lang.String.valueOf(service.getExternalId())).build();
        uk.gov.pay.adminusers.model.Link selfLink = uk.gov.pay.adminusers.model.Link.from(uk.gov.pay.adminusers.model.Link.Rel.SELF, "GET", uri.toString());
        service.setLinks(java.util.List.of(selfLink));
        return service;
    }

    public uk.gov.pay.adminusers.model.ForgottenPassword decorate(uk.gov.pay.adminusers.model.ForgottenPassword forgottenPassword) {
        java.net.URI uri = javax.ws.rs.core.UriBuilder.fromUri(baseUrl).path(uk.gov.pay.adminusers.resources.ForgottenPasswordResource.FORGOTTEN_PASSWORDS_RESOURCE).path(forgottenPassword.getCode()).build();
        uk.gov.pay.adminusers.model.Link selfLink = uk.gov.pay.adminusers.model.Link.from(uk.gov.pay.adminusers.model.Link.Rel.SELF, "GET", uri.toString());
        forgottenPassword.setLinks(java.util.List.of(selfLink));
        return forgottenPassword;
    }

    public uk.gov.pay.adminusers.model.Invite decorate(uk.gov.pay.adminusers.model.Invite invite) {
        java.net.URI uri = javax.ws.rs.core.UriBuilder.fromUri(baseUrl).path(uk.gov.pay.adminusers.resources.InviteResource.INVITES_RESOURCE).path(invite.getCode()).build();
        uk.gov.pay.adminusers.model.Link selfLink = uk.gov.pay.adminusers.model.Link.from(uk.gov.pay.adminusers.model.Link.Rel.SELF, "GET", uri.toString());
        invite.getLinks().add(selfLink);
        return invite;
    }

    public uk.gov.pay.adminusers.model.Invite addUserLink(uk.gov.pay.adminusers.model.User user, uk.gov.pay.adminusers.model.Invite invite) {
        java.net.URI uri = javax.ws.rs.core.UriBuilder.fromUri(baseUrl).path(uk.gov.pay.adminusers.resources.UserResource.USERS_RESOURCE).path(user.getExternalId()).build();
        uk.gov.pay.adminusers.model.Link userLink = uk.gov.pay.adminusers.model.Link.from(uk.gov.pay.adminusers.model.Link.Rel.USER, "GET", uri.toString());
        invite.getLinks().add(userLink);
        return invite;
    }
}
