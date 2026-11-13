package uk.gov.pay.adminusers.service;
public abstract class InviteCompleter {
    /* default */
    uk.gov.pay.adminusers.model.InviteCompleteRequest data = null;

    public abstract java.util.Optional<uk.gov.pay.adminusers.model.InviteCompleteResponse> complete(java.lang.String inviteCode);

    public uk.gov.pay.adminusers.service.InviteCompleter withData(uk.gov.pay.adminusers.model.InviteCompleteRequest data) {
        this.data = data;
        return this;
    }
}
