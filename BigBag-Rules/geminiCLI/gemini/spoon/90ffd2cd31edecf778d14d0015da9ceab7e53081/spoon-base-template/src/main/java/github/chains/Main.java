package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtType;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Main {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java");
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addProcessor(new EventMessageHandlerTestProcessor());
        
        launcher.run();
        
        System.out.println("Spoon processing complete. Searching for type...");
        
        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            System.out.println("Checking type: " + type.getQualifiedName());
            if (type.getQualifiedName().equals("uk.gov.pay.adminusers.queue.event.EventMessageHandlerTest")) {
                System.out.println("Found type. Writing to file...");
                String content = type.toString();
                // Write to original file
                Files.write(Paths.get("/workspace/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java"), content.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("File written successfully.");
                return;
            }
        }
        System.out.println("Type NOT found in model!");
    }
}