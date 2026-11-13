package uk.gov.pay.adminusers.app.config;
public class NotifyDirectDebitConfiguration extends io.dropwizard.Configuration {
    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String mandateCancelledEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String mandateFailedEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String paymentFailedEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String oneOffMandateAndPaymentCreatedEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String onDemandMandateCreatedEmailTemplateId;

    @javax.validation.Valid
    @javax.validation.constraints.NotNull
    private java.lang.String onDemandPaymentConfirmedEmailTemplateId;

    public java.lang.String getMandateCancelledEmailTemplateId() {
        return mandateCancelledEmailTemplateId;
    }

    public java.lang.String getMandateFailedEmailTemplateId() {
        return mandateFailedEmailTemplateId;
    }

    public java.lang.String getPaymentFailedEmailTemplateId() {
        return paymentFailedEmailTemplateId;
    }

    public java.lang.String getOneOffMandateAndPaymentCreatedEmailTemplateId() {
        return oneOffMandateAndPaymentCreatedEmailTemplateId;
    }

    public java.lang.String getOnDemandMandateCreatedEmailTemplateId() {
        return onDemandMandateCreatedEmailTemplateId;
    }

    public java.lang.String getOnDemandPaymentConfirmedEmailTemplateId() {
        return onDemandPaymentConfirmedEmailTemplateId;
    }
}
