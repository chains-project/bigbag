package uk.gov.pay.adminusers.service;
public class StaticEmailContent {
    private java.lang.String templateId;

    private java.util.Map<java.lang.String, java.lang.String> personalisation;

    public StaticEmailContent(java.lang.String templateId, java.util.Map<java.lang.String, java.lang.String> personalisation) {
        this.templateId = templateId;
        this.personalisation = personalisation;
    }

    public java.lang.String getTemplateId() {
        return templateId;
    }

    public java.util.Map<java.lang.String, java.lang.String> getPersonalisation() {
        return personalisation;
    }
}
