package uk.gov.pay.adminusers.queue.model.event;
class DisputeLostDetailsTest {
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.jupiter.api.Test
    public void shouldDeserialiseDisputeEvent() throws com.fasterxml.jackson.core.JsonProcessingException {
        var json = objectMapper.readTree(uk.gov.pay.adminusers.TestTemplateResourceLoader.load(uk.gov.pay.adminusers.TestTemplateResourceLoader.DISPUTE_LOST_EVENT));
        var evt = objectMapper.treeToValue(json, uk.gov.pay.adminusers.queue.model.Event.class);
        var disputeLostDetails = objectMapper.treeToValue(evt.getEventDetails(), uk.gov.pay.adminusers.queue.model.event.DisputeLostDetails.class);
        org.hamcrest.MatcherAssert.assertThat(evt.getEventType(), org.hamcrest.Matchers.is(uk.gov.pay.adminusers.queue.model.EventType.DISPUTE_LOST.name()));
        org.hamcrest.MatcherAssert.assertThat(disputeLostDetails.getFee(), org.hamcrest.Matchers.is(1500L));
        org.hamcrest.MatcherAssert.assertThat(disputeLostDetails.getAmount(), org.hamcrest.Matchers.is(2500L));
        org.hamcrest.MatcherAssert.assertThat(disputeLostDetails.getNetAmount(), org.hamcrest.Matchers.is(-4000L));
        org.hamcrest.MatcherAssert.assertThat(disputeLostDetails.getGatewayAccountId(), org.hamcrest.Matchers.is("123"));
    }
}
