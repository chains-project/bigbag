package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        String baseDir = "/workspace/java-pubsub-group-kafka-connector";
        List<String> filesToProcess = Arrays.asList(
            baseDir + "/src/main/java/com/google/pubsublite/kafka/sink/PublisherFactory.java",
            baseDir + "/src/main/java/com/google/pubsublite/kafka/sink/PublisherFactoryImpl.java",
            baseDir + "/src/main/java/com/google/pubsublite/kafka/sink/PubSubLiteSinkTask.java",
            baseDir + "/src/test/java/com/google/pubsublite/kafka/sink/PubSubLiteSinkTaskTest.java"
        );

        String outputPath = "/workspace/spoon-base-template/target/spooned";

        Launcher launcher = new Launcher();
        for (String file : filesToProcess) {
            launcher.addInputResource(file);
        }
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.setSourceOutputDirectory(outputPath);

        launcher.buildModel();
        CtModel model = launcher.getModel();

        // 1. Rename PublishMetadata to MessageMetadata
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if ("com.google.cloud.pubsublite.PublishMetadata".equals(ref.getQualifiedName())) {
                ref.setSimpleName("MessageMetadata");
            }
        }

        // 2. Remove setContext call
        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if ("setContext".equals(invocation.getExecutable().getSimpleName())) {
                 boolean isTarget = false;
                 for (CtElement arg : invocation.getArguments()) {
                     if (arg.toString().contains("PubsubContext")) {
                         isTarget = true;
                         break;
                     }
                 }
                 if (isTarget) {
                     CtElement target = invocation.getTarget();
                     if (target != null) {
                         invocation.replace(target);
                     }
                 }
            }
        }
        
        // 3. Clean up imports explicitly if possible
        for (CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
             List<CtImport> imports = cu.getImports();
             List<CtImport> toRemove = imports.stream()
                 .filter(i -> i.getReference() != null && "com.google.cloud.pubsublite.PublishMetadata".equals(i.getReference().toString()))
                 .collect(Collectors.toList());
             
             // Also remove by string representation if reference is not fully populated
             // In noclasspath, imports might be parsed differently.
             // But let's try this.
        }

        launcher.prettyprint();
        System.out.println("Spoon transformation completed.");
    }
}