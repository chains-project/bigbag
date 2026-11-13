package uk.gov.pay.adminusers.queue.model;
class EventTypeTest {
    @org.junit.jupiter.api.Test
    void shouldGetCorrectEventTypeForValidValue() {
        org.hamcrest.MatcherAssert.assertThat(uk.gov.pay.adminusers.queue.model.EventType.byType("DISPUTE_CREATED"), org.hamcrest.Matchers.is(uk.gov.pay.adminusers.queue.model.EventType.DISPUTE_CREATED));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "", "some-random-string" })
    @org.junit.jupiter.params.provider.NullSource
    void shouldReturnUnknownForEmptyValue(java.lang.String value) {
        org.hamcrest.MatcherAssert.assertThat(uk.gov.pay.adminusers.queue.model.EventType.byType(value), org.hamcrest.Matchers.is(uk.gov.pay.adminusers.queue.model.EventType.UNKNOWN));
    }
}
