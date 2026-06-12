package uk.gov.pay.adminusers.queue.model;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class SNSMessage {
    @com.fasterxml.jackson.annotation.JsonProperty("Message")
    private java.lang.String message;

    public SNSMessage() {
        // for deserialization
    }

    public java.lang.String getMessage() {
        return message;
    }
}
