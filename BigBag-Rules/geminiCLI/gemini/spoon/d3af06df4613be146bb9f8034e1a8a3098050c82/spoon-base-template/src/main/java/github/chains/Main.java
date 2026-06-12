package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.AbstractFilter;
import spoon.reflect.declaration.CtImport;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.buildModel();

        Factory factory = launcher.getFactory();

        // 1. Replace javax.servlet with jakarta.servlet in all type references
        launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)).forEach(ref -> {
            if (ref.getQualifiedName().startsWith("javax.servlet")) {
                if (ref.getPackage() != null) {
                    ref.getPackage().setSimpleName(ref.getPackage().getSimpleName().replace("javax", "jakarta"));
                }
            }
        });
        
        // Remove javax.servlet imports explicitly
        launcher.getModel().getAllTypes().forEach(ctType -> {
            if (ctType.getPosition().getCompilationUnit() != null) {
                ctType.getPosition().getCompilationUnit().getImports().removeIf(imp -> 
                    imp.getReference() != null && imp.getReference().toString().contains("javax.servlet")
                );
            }
        });

        // Also fix the local variable in JadlerHandler
        CtClass<?> jadlerHandler = factory.Class().get("net.jadler.stubbing.server.jetty.JadlerHandler");
        if (jadlerHandler != null) {
            for (CtMethod<?> method : jadlerHandler.getMethods()) {
                if (method.getSimpleName().equals("handle")) {
                    method.getBody().getElements(new AbstractFilter<spoon.reflect.code.CtLocalVariable<?>>() {
                        @Override
                        public boolean matches(spoon.reflect.code.CtLocalVariable<?> var) {
                            return var.getSimpleName().equals("req");
                        }
                    }).forEach(var -> {
                        var.setType(factory.Type().createReference("net.jadler.Request"));
                    });
                }
            }
        }

        // 2. Modify JettyStubHttpServer
        CtClass<?> jettyServer = factory.Class().get("net.jadler.stubbing.server.jetty.JettyStubHttpServer");
        if (jettyServer != null) {
            
            // Remove the import org.eclipse.jetty.server.nio.SelectChannelConnector explicitly
            if (jettyServer.getPosition().getCompilationUnit() != null) {
                jettyServer.getPosition().getCompilationUnit().getImports().removeIf(imp -> 
                    imp.getReference() != null && imp.getReference().toString().contains("SelectChannelConnector")
                );
            }

            for (CtConstructor<?> constructor : jettyServer.getConstructors()) {
                if (constructor.getParameters().size() == 1) {
                    CtBlock<?> block = factory.Core().createBlock();
                    block.addStatement(factory.Code().createCodeSnippetStatement(
                        "this.server = new org.eclipse.jetty.server.Server()"
                    ));
                    block.addStatement(factory.Code().createCodeSnippetStatement(
                        "org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration()"
                    ));
                    block.addStatement(factory.Code().createCodeSnippetStatement(
                        "httpConfig.setSendServerVersion(false)"
                    ));
                    block.addStatement(factory.Code().createCodeSnippetStatement(
                        "httpConfig.setSendDateHeader(true)"
                    ));
                    block.addStatement(factory.Code().createCodeSnippetStatement(
                        "org.eclipse.jetty.server.ServerConnector connector = new org.eclipse.jetty.server.ServerConnector(this.server, new org.eclipse.jetty.server.HttpConnectionFactory(httpConfig))"
                    ));
                    block.addStatement(factory.Code().createCodeSnippetStatement(
                        "connector.setPort(port)"
                    ));
                    block.addStatement(factory.Code().createCodeSnippetStatement(
                        "this.httpConnector = connector"
                    ));
                    block.addStatement(factory.Code().createCodeSnippetStatement(
                        "this.server.addConnector(this.httpConnector)"
                    ));
                    constructor.setBody(block);
                }
            }
            
            for (CtMethod<?> method : jettyServer.getMethods()) {
                if (method.getSimpleName().equals("getPort")) {
                    CtBlock<?> block = factory.Core().createBlock();
                    block.addStatement(factory.Code().createCodeSnippetStatement(
                        "return ((org.eclipse.jetty.server.ServerConnector)this.httpConnector).getLocalPort()"
                    ));
                    method.setBody(block);
                }
            }
        }

        launcher.setSourceOutputDirectory("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.prettyprint();
    }
}