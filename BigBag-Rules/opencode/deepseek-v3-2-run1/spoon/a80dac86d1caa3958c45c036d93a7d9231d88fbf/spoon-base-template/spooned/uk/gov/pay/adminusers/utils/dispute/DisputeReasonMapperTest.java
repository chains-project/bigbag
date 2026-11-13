package uk.gov.pay.adminusers.utils.dispute;
class DisputeReasonMapperTest {
    @org.junit.jupiter.api.Test
    void shouldReturnUnrecognised() {
        java.lang.String mappedValue = uk.gov.pay.adminusers.utils.dispute.DisputeReasonMapper.mapToNotifyEmail("unrecognized");
        org.hamcrest.MatcherAssert.assertThat(mappedValue, org.hamcrest.CoreMatchers.is("unrecognised"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnOther() {
        java.lang.String mappedValue = uk.gov.pay.adminusers.utils.dispute.DisputeReasonMapper.mapToNotifyEmail("insufficient_funds");
        org.hamcrest.MatcherAssert.assertThat(mappedValue, org.hamcrest.CoreMatchers.is("other"));
    }

    @org.junit.jupiter.api.Test
    void shouldHandleNullValue() {
        java.lang.String mappedValue = uk.gov.pay.adminusers.utils.dispute.DisputeReasonMapper.mapToNotifyEmail(null);
        org.hamcrest.MatcherAssert.assertThat(mappedValue, org.hamcrest.CoreMatchers.is("unknown"));
    }

    @org.junit.jupiter.api.Test
    void shouldHandleEmptyValue() {
        java.lang.String mappedValue = uk.gov.pay.adminusers.utils.dispute.DisputeReasonMapper.mapToNotifyEmail("");
        org.hamcrest.MatcherAssert.assertThat(mappedValue, org.hamcrest.CoreMatchers.is("unknown"));
    }
}
