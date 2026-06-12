package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/lithium/src/main/java/com/wire/lithium/Server.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        launcher.buildModel();
        
        CtType<?> server = launcher.getFactory().Type().get("com.wire.lithium.Server");
        CtMethod<?> method = server.getMethodsByName("createHttpClient").get(0);
        
        CtStatement returnStmt = launcher.getFactory().Code().createCodeSnippetStatement(
            "return javax.ws.rs.client.ClientBuilder.newBuilder()\n" +
            "    .register(org.glassfish.jersey.media.multipart.MultiPartFeature.class)\n" +
            "    .register(com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider.class)\n" +
            "    .build()"
        );
        
        method.getBody().setStatements(java.util.Collections.singletonList(returnStmt));
        
        launcher.setSourceOutputDirectory("/workspace/lithium/src/main/java");
        launcher.prettyprint();
    }
}
