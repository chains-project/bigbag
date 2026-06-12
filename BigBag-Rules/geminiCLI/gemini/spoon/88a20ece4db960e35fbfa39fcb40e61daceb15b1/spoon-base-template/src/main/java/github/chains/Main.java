package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.code.*;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/java-pubsub-group-kafka-connector/src/main/java/com/google/pubsublite/kafka/sink/");
        launcher.addInputResource("/workspace/java-pubsub-group-kafka-connector/src/test/java/com/google/pubsublite/kafka/sink/");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setPrettyPrinterCreator(() -> new spoon.support.sniper.SniperJavaPrettyPrinter(launcher.getEnvironment()));
        launcher.buildModel();

        for (CtTypeReference<?> ref : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if ("PublishMetadata".equals(ref.getSimpleName()) && 
                (ref.getPackage() == null || "com.google.cloud.pubsublite".equals(ref.getPackage().getSimpleName()))) {
                ref.setSimpleName("MessageMetadata");
            }
        }

        // Delete the import explicitly using ctImport.delete()
        for (CtImport ctImport : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            if (ctImport.getReference() != null && "com.google.cloud.pubsublite.PublishMetadata".equals(ctImport.getReference().toString())) {
                ctImport.delete();
            }
        }

        // Replace lambda in PublisherFactoryImpl with anonymous class
        CtClass<?> publisherFactoryImpl = launcher.getFactory().Class().get("com.google.pubsublite.kafka.sink.PublisherFactoryImpl");
        if (publisherFactoryImpl != null) {
            for (CtLambda<?> lambda : publisherFactoryImpl.getElements(new TypeFilter<>(CtLambda.class))) {
                CtCodeSnippetExpression snippet = launcher.getFactory().Core().createCodeSnippetExpression();
                snippet.setValue("new com.google.cloud.pubsublite.internal.wire.PartitionPublisherFactory() {\n" +
                        "            @java.lang.Override\n" +
                        "            public com.google.cloud.pubsublite.internal.Publisher<com.google.cloud.pubsublite.MessageMetadata> newPublisher(com.google.cloud.pubsublite.Partition partition) {\n" +
                        "                return com.google.cloud.pubsublite.internal.wire.SinglePartitionPublisherBuilder.newBuilder()\n" +
                        "                    .setTopic(topic)\n" +
                        "                    .setPartition(partition)\n" +
                        "                    .setContext(com.google.cloud.pubsublite.internal.wire.PubsubContext.of(FRAMEWORK))\n" +
                        "                    .build();\n" +
                        "            }\n" +
                        "            @java.lang.Override\n" +
                        "            public void close() {}\n" +
                        "        }");
                lambda.replace(snippet);
            }
        }

        launcher.setSourceOutputDirectory("/tmp/spooned5");
        launcher.prettyprint();
        System.out.println("Spoon transformation applied with delete().");
    }
}
