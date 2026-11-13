package uk.gov.pay.adminusers.utils.date;
public class DisputeEvidenceDueByDateUtil {
    public static java.time.ZonedDateTime getPayDueByDate(java.time.ZonedDateTime evidenceDueDate) {
        switch (evidenceDueDate.getDayOfWeek()) {
            case MONDAY :
                return evidenceDueDate.minusDays(3L);
            case TUESDAY :
                return evidenceDueDate.minusDays(4L);
            default :
                return evidenceDueDate.minusDays(2L);
        }
    }
}
