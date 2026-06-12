package uk.gov.pay.adminusers.resources;
public enum EmailTemplate {

    ON_DEMAND_MANDATE_CREATED,
    ONE_OFF_MANDATE_CREATED,
    MANDATE_CANCELLED,
    MANDATE_FAILED,
    ONE_OFF_PAYMENT_CONFIRMED,
    ON_DEMAND_PAYMENT_CONFIRMED,
    PAYMENT_FAILED;

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.resources.EmailTemplate.class);

    public static uk.gov.pay.adminusers.resources.EmailTemplate fromString(java.lang.String type) {
        for (uk.gov.pay.adminusers.resources.EmailTemplate typeEnum : uk.gov.pay.adminusers.resources.EmailTemplate.values()) {
            if (typeEnum.toString().equalsIgnoreCase(type)) {
                return typeEnum;
            }
        }
        uk.gov.pay.adminusers.resources.EmailTemplate.LOGGER.warn("Unknown email template: {}", type);
        return null;
    }
}
