package uk.gov.pay.adminusers.resources;
public class EmailTemplateTest {
    @org.junit.jupiter.api.Test
    public void shouldDeserialiseEnumFromUppercaseString() {
        uk.gov.pay.adminusers.resources.EmailTemplate mandateCancelled = uk.gov.pay.adminusers.resources.EmailTemplate.fromString("MANDATE_CANCELLED");
        uk.gov.pay.adminusers.resources.EmailTemplate mandateFailed = uk.gov.pay.adminusers.resources.EmailTemplate.fromString("MANDATE_FAILED");
        uk.gov.pay.adminusers.resources.EmailTemplate paymentConfirmedOneOff = uk.gov.pay.adminusers.resources.EmailTemplate.fromString("ONE_OFF_PAYMENT_CONFIRMED");
        uk.gov.pay.adminusers.resources.EmailTemplate paymentConfirmedOnDemand = uk.gov.pay.adminusers.resources.EmailTemplate.fromString("ON_DEMAND_PAYMENT_CONFIRMED");
        uk.gov.pay.adminusers.resources.EmailTemplate paymentFailed = uk.gov.pay.adminusers.resources.EmailTemplate.fromString("PAYMENT_FAILED");
        org.hamcrest.MatcherAssert.assertThat(mandateCancelled, org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.resources.EmailTemplate.MANDATE_CANCELLED));
        org.hamcrest.MatcherAssert.assertThat(mandateFailed, org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.resources.EmailTemplate.MANDATE_FAILED));
        org.hamcrest.MatcherAssert.assertThat(paymentConfirmedOneOff, org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.resources.EmailTemplate.ONE_OFF_PAYMENT_CONFIRMED));
        org.hamcrest.MatcherAssert.assertThat(paymentConfirmedOnDemand, org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.resources.EmailTemplate.ON_DEMAND_PAYMENT_CONFIRMED));
        org.hamcrest.MatcherAssert.assertThat(paymentFailed, org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.resources.EmailTemplate.PAYMENT_FAILED));
    }

    @org.junit.jupiter.api.Test
    public void shouldDeserialiseEnumFromLowercaseString() {
        uk.gov.pay.adminusers.resources.EmailTemplate mandateCancelled = uk.gov.pay.adminusers.resources.EmailTemplate.fromString("mandate_cancelled");
        uk.gov.pay.adminusers.resources.EmailTemplate mandateFailed = uk.gov.pay.adminusers.resources.EmailTemplate.fromString("mandate_failed");
        uk.gov.pay.adminusers.resources.EmailTemplate paymentConfirmedOneOff = uk.gov.pay.adminusers.resources.EmailTemplate.fromString("one_off_payment_confirmed");
        uk.gov.pay.adminusers.resources.EmailTemplate paymentConfirmedOnDemand = uk.gov.pay.adminusers.resources.EmailTemplate.fromString("on_demand_payment_confirmed");
        uk.gov.pay.adminusers.resources.EmailTemplate paymentFailed = uk.gov.pay.adminusers.resources.EmailTemplate.fromString("payment_failed");
        org.hamcrest.MatcherAssert.assertThat(mandateCancelled, org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.resources.EmailTemplate.MANDATE_CANCELLED));
        org.hamcrest.MatcherAssert.assertThat(mandateFailed, org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.resources.EmailTemplate.MANDATE_FAILED));
        org.hamcrest.MatcherAssert.assertThat(paymentConfirmedOneOff, org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.resources.EmailTemplate.ONE_OFF_PAYMENT_CONFIRMED));
        org.hamcrest.MatcherAssert.assertThat(paymentConfirmedOnDemand, org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.resources.EmailTemplate.ON_DEMAND_PAYMENT_CONFIRMED));
        org.hamcrest.MatcherAssert.assertThat(paymentFailed, org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.resources.EmailTemplate.PAYMENT_FAILED));
    }
}
