package uk.gov.pay.adminusers.service;
public abstract class InviteOtpDispatcher {
    /* default */
    static final java.lang.String SIX_DIGITS_WITH_LEADING_ZEROS = "%06d";

    /* default */
    uk.gov.pay.adminusers.model.InviteOtpRequest inviteOtpRequest = null;

    public abstract boolean dispatchOtp(java.lang.String inviteCode);

    public uk.gov.pay.adminusers.service.InviteOtpDispatcher withData(uk.gov.pay.adminusers.model.InviteOtpRequest data) {
        this.inviteOtpRequest = data;
        return this;
    }
}
