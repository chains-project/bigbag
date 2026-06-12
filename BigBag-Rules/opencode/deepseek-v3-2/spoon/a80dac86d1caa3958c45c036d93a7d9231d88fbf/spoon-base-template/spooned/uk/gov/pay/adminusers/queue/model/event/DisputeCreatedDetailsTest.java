package uk.gov.pay.adminusers.queue.model.event;
class DisputeCreatedDetailsTest {
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.jupiter.api.Test
    public void shouldDeserialiseDisputeEvent() throws com.fasterxml.jackson.core.JsonProcessingException {
        var json = objectMapper.readTree(uk.gov.pay.adminusers.TestTemplateResourceLoader.load(uk.gov.pay.adminusers.TestTemplateResourceLoader.DISPUTE_CREATED_EVENT));
        var evt = objectMapper.treeToValue(json, uk.gov.pay.adminusers.queue.model.Event.class);
        var disputeCreatedDetails = objectMapper.treeToValue(evt.getEventDetails(), uk.gov.pay.adminusers.queue.model.event.DisputeCreatedDetails.class);
        org.hamcrest.MatcherAssert.assertThat(evt.getEventType(), org.hamcrest.Matchers.is(uk.gov.pay.adminusers.queue.model.EventType.DISPUTE_CREATED.name()));
        org.hamcrest.MatcherAssert.assertThat(disputeCreatedDetails.getAmount(), org.hamcrest.Matchers.is(125000L));
        org.hamcrest.MatcherAssert.assertThat(disputeCreatedDetails.getEvidenceDueDate().toString(), org.hamcrest.Matchers.is("2022-03-07T13:00:00.001Z"));
        org.hamcrest.MatcherAssert.assertThat(disputeCreatedDetails.getGatewayAccountId(), org.hamcrest.Matchers.is("123"));
        org.hamcrest.MatcherAssert.assertThat(disputeCreatedDetails.getReason(), org.hamcrest.Matchers.is("fraudulent"));
    }
}
