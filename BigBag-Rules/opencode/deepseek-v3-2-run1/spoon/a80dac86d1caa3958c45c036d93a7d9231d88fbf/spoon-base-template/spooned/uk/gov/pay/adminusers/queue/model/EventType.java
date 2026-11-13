package uk.gov.pay.adminusers.queue.model;
public enum EventType {

    DISPUTE_CREATED,
    DISPUTE_LOST,
    DISPUTE_WON,
    DISPUTE_EVIDENCE_SUBMITTED,
    UNKNOWN;

    public static uk.gov.pay.adminusers.queue.model.EventType byType(java.lang.String type) {
        if (org.apache.commons.lang3.StringUtils.isBlank(type)) {
            return uk.gov.pay.adminusers.queue.model.EventType.UNKNOWN;
        }
        return java.util.Arrays.stream(uk.gov.pay.adminusers.queue.model.EventType.values()).filter(c -> c.name().equals(type.toUpperCase())).findFirst().orElse(uk.gov.pay.adminusers.queue.model.EventType.UNKNOWN);
    }
}
