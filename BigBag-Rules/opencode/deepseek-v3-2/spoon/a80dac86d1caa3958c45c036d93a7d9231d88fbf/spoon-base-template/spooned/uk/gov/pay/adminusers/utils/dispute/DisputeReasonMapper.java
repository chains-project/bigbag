package uk.gov.pay.adminusers.utils.dispute;
public class DisputeReasonMapper {
    public static java.lang.String mapToNotifyEmail(java.lang.String stripeReason) {
        if (org.apache.commons.lang3.StringUtils.isBlank(stripeReason)) {
            return "unknown";
        }
        switch (stripeReason) {
            case "duplicate" :
            case "fraudulent" :
            case "general" :
                return stripeReason;
            case "credit_not_processed" :
                return "credit not processed";
            case "product_not_received" :
                return "product not received";
            case "product_unacceptable" :
                return "product unacceptable";
            case "subscription_canceled" :
                return "subscription cancelled";
            case "unrecognized" :
                return "unrecognised";
            default :
                return "other";
        }
    }
}
