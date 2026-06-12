package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InviteCompleteResponse {
    private uk.gov.pay.adminusers.model.Invite invite;

    @io.swagger.v3.oas.annotations.media.Schema(example = "287cg75v3737")
    private java.lang.String userExternalId;

    @io.swagger.v3.oas.annotations.media.Schema(example = "89wi6il2364328")
    private java.lang.String serviceExternalId;

    public InviteCompleteResponse(@com.fasterxml.jackson.annotation.JsonProperty("invite")
    uk.gov.pay.adminusers.model.Invite invite) {
        this.invite = invite;
    }

    public uk.gov.pay.adminusers.model.Invite getInvite() {
        return invite;
    }

    public java.lang.String getUserExternalId() {
        return userExternalId;
    }

    public void setUserExternalId(java.lang.String userExternalId) {
        this.userExternalId = userExternalId;
    }

    public java.lang.String getServiceExternalId() {
        return serviceExternalId;
    }

    public void setServiceExternalId(java.lang.String serviceExternalId) {
        this.serviceExternalId = serviceExternalId;
    }
}
