package uk.gov.pay.adminusers.infra;
public class SqsTestDocker {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.infra.SqsTestDocker.class);

    private static org.testcontainers.containers.GenericContainer sqsContainer;

    public static com.amazonaws.services.sqs.AmazonSQS initialise(java.util.List<java.lang.String> queueNames) {
        try {
            uk.gov.pay.adminusers.infra.SqsTestDocker.createContainer();
            return uk.gov.pay.adminusers.infra.SqsTestDocker.createQueues(queueNames);
        } catch (java.lang.Exception e) {
            uk.gov.pay.adminusers.infra.SqsTestDocker.logger.error("Exception initialising SQS Container - {}", e.getMessage());
            throw new java.lang.RuntimeException(e);
        }
    }

    private static void createContainer() {
        if (uk.gov.pay.adminusers.infra.SqsTestDocker.sqsContainer == null) {
            uk.gov.pay.adminusers.infra.SqsTestDocker.logger.info("Creating SQS Container");
            uk.gov.pay.adminusers.infra.SqsTestDocker.sqsContainer = new org.testcontainers.containers.GenericContainer("mvisonneau/alpine-sqs:1.2.0").withExposedPorts(9324).waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/?Action=GetQueueUrl&QueueName=default"));
            uk.gov.pay.adminusers.infra.SqsTestDocker.sqsContainer.start();
        }
    }

    public static void stopContainer() {
        uk.gov.pay.adminusers.infra.SqsTestDocker.sqsContainer.stop();
        uk.gov.pay.adminusers.infra.SqsTestDocker.sqsContainer = null;
    }

    private static com.amazonaws.services.sqs.AmazonSQS createQueues(java.util.List<java.lang.String> queueNames) {
        com.amazonaws.services.sqs.AmazonSQS amazonSQS = uk.gov.pay.adminusers.infra.SqsTestDocker.getSqsClient();
        queueNames.forEach(amazonSQS::createQueue);
        return amazonSQS;
    }

    public static java.lang.String getQueueUrl(java.lang.String queueName) {
        return (uk.gov.pay.adminusers.infra.SqsTestDocker.getEndpoint() + "/queue/") + queueName;
    }

    public static java.lang.String getEndpoint() {
        return "http://localhost:" + uk.gov.pay.adminusers.infra.SqsTestDocker.sqsContainer.getMappedPort(9324);
    }

    private static com.amazonaws.services.sqs.AmazonSQS getSqsClient() {
        // random credentials required by AWS SDK to build SQS client
        com.amazonaws.auth.BasicAWSCredentials awsCreds = new com.amazonaws.auth.BasicAWSCredentials("x", "x");
        return com.amazonaws.services.sqs.AmazonSQSClientBuilder.standard().withCredentials(new com.amazonaws.auth.AWSStaticCredentialsProvider(awsCreds)).withEndpointConfiguration(new com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration(uk.gov.pay.adminusers.infra.SqsTestDocker.getEndpoint(), "region-1")).withRequestHandlers().build();
    }
}
