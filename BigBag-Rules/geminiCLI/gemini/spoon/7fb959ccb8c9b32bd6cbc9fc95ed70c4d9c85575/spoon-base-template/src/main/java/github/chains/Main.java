package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.code.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.*;
import java.util.Iterator;
import spoon.reflect.cu.CompilationUnit;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jadler/jadler-jetty/src/main/java/");
        launcher.setSourceOutputDirectory("/workspace/jadler/jadler-jetty/src/main/java/");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true); 
        
        launcher.buildModel();
        CtModel model = launcher.getModel();
        
        // CLEAR all imports from compilation units so auto-imports can start fresh!
        for (CompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            cu.setImports(new ArrayList<>());
        }
        
        // Update all javax.servlet types
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (ref.getQualifiedName().startsWith("javax.servlet")) {
                String newName = ref.getQualifiedName().replace("javax.servlet", "jakarta.servlet");
                CtTypeReference<?> newRef = launcher.getFactory().Type().createReference(newName);
                ref.replace(newRef);
            }
        }

        // Fix JettyStubHttpServer
        CtClass<?> jettyStubHttpServer = model.getElements(new NamedElementFilter<>(CtClass.class, "JettyStubHttpServer")).get(0);
        
        for (CtField<?> field : jettyStubHttpServer.getFields()) {
            if (field.getSimpleName().equals("httpConnector")) {
                CtTypeReference ref = launcher.getFactory().Core().createTypeReference();
                ref.setSimpleName("ServerConnector");
                CtPackageReference pkg = launcher.getFactory().Core().createPackageReference();
                pkg.setSimpleName("org.eclipse.jetty.server");
                ref.setPackage(pkg);
                field.setType(ref);
            }
        }
        
        CtConstructor<?> constructor = jettyStubHttpServer.getConstructors().stream()
            .filter(c -> c.getParameters().size() == 1).findFirst().get();
            
        CtBlock<?> body = constructor.getBody();
        CtCodeSnippetStatement snippet = launcher.getFactory().Core().createCodeSnippetStatement();
        snippet.setValue("this.server = new org.eclipse.jetty.server.Server();\n" +
            "        org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration();\n" +
            "        httpConfig.setSendServerVersion(false);\n" +
            "        httpConfig.setSendDateHeader(true);\n" +
            "        org.eclipse.jetty.server.HttpConnectionFactory httpFactory = new org.eclipse.jetty.server.HttpConnectionFactory(httpConfig);\n" +
            "        this.httpConnector = new org.eclipse.jetty.server.ServerConnector(server, httpFactory);\n" +
            "        this.httpConnector.setPort(port);\n" +
            "        server.addConnector(this.httpConnector)");
        body.setStatements(java.util.Collections.singletonList(snippet));
        
        CtClass<?> jadlerHandler = model.getElements(new NamedElementFilter<>(CtClass.class, "JadlerHandler")).get(0);
        for (CtLocalVariable<?> localVariable : jadlerHandler.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (localVariable.getSimpleName().equals("req")) {
                CtTypeReference ref = launcher.getFactory().Core().createTypeReference();
                ref.setSimpleName("Request");
                CtPackageReference pkg = launcher.getFactory().Core().createPackageReference();
                pkg.setSimpleName("net.jadler");
                ref.setPackage(pkg);
                localVariable.setType(ref);
            }
        }

        launcher.prettyprint();
    }
}