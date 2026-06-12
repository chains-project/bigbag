package uk.gov.pay.adminusers.utils;
public class DateTimeUtilsTest {
    @org.junit.jupiter.api.Test
    public void formatsAsIsoInstantWithMillisecondPrecision() {
        java.time.ZonedDateTime timestamp = java.time.ZonedDateTime.parse("2010-12-31T22:59:59.132012345Z");
        final java.lang.String actual = uk.gov.service.payments.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION.format(timestamp);
        final java.lang.String expected = "2010-12-31T22:59:59.132Z";
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
