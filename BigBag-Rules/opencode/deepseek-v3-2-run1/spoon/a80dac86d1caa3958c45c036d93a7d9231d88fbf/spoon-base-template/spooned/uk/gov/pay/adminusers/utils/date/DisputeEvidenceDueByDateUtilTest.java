package uk.gov.pay.adminusers.utils.date;
class DisputeEvidenceDueByDateUtilTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("epochProvider")
    void shouldCorrectlyCalculatePayDueByDate(java.time.ZonedDateTime dateTime, java.time.DayOfWeek expected) {
        org.hamcrest.MatcherAssert.assertThat(uk.gov.pay.adminusers.utils.date.DisputeEvidenceDueByDateUtil.getPayDueByDate(dateTime).getDayOfWeek(), org.hamcrest.CoreMatchers.is(expected));
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> epochProvider() {
        return java.util.stream.Stream.of(org.junit.jupiter.params.provider.Arguments.of(java.time.ZonedDateTime.parse("2022-03-07T13:00:00.000Z"), java.time.DayOfWeek.FRIDAY), org.junit.jupiter.params.provider.Arguments.of(java.time.ZonedDateTime.parse("2022-03-08T13:00:00.000Z"), java.time.DayOfWeek.FRIDAY), org.junit.jupiter.params.provider.Arguments.of(java.time.ZonedDateTime.parse("2022-03-09T13:00:00.000Z"), java.time.DayOfWeek.MONDAY), org.junit.jupiter.params.provider.Arguments.of(java.time.ZonedDateTime.parse("2022-03-10T13:00:00.000Z"), java.time.DayOfWeek.TUESDAY), org.junit.jupiter.params.provider.Arguments.of(java.time.ZonedDateTime.parse("2022-03-11T13:00:00.000Z"), java.time.DayOfWeek.WEDNESDAY));
    }
}
