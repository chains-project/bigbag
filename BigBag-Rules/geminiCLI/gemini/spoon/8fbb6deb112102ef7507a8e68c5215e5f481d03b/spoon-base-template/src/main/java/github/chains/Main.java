package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        
        launcher.buildModel();
        CtModel model = launcher.getModel();

        // 1. Replace javax.servlet with jakarta.servlet
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (ref.getQualifiedName().startsWith("javax.servlet")) {
                String newQName = ref.getQualifiedName().replace("javax.servlet", "jakarta.servlet");
                ref.replace(launcher.getFactory().Type().createReference(newQName));
            }
            if (ref.getQualifiedName().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                ref.replace(launcher.getFactory().Type().createReference("org.eclipse.jetty.server.ServerConnector"));
            }
            if (ref.getQualifiedName().equals("org.eclipse.jetty.server.Connector")) {
                ref.replace(launcher.getFactory().Type().createReference("org.eclipse.jetty.server.ServerConnector"));
            }
        }

        // Fix the local variable `req` in JadlerHandler.java
        for (spoon.reflect.code.CtLocalVariable<?> local : model.getElements(new TypeFilter<>(spoon.reflect.code.CtLocalVariable.class))) {
            if (local.getSimpleName().equals("req")) {
                local.setType(launcher.getFactory().Type().createReference("net.jadler.Request"));
            }
        }

        // Fix imports
        for (spoon.reflect.cu.CompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            List<CtImport> importsToRemove = new ArrayList<>();
            List<CtImport> importsToAdd = new ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                if (imp.getReference() != null) {
                    String impStr = imp.getReference().toString();
                    if (impStr.startsWith("javax.servlet")) {
                        importsToRemove.add(imp);
                        importsToAdd.add(launcher.getFactory().createImport(launcher.getFactory().Type().createReference(impStr.replace("javax.servlet", "jakarta.servlet"))));
                    } else if (impStr.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                        importsToRemove.add(imp);
                    } else if (impStr.equals("org.eclipse.jetty.server.Connector")) {
                        importsToRemove.add(imp);
                    }
                }
            }
            cu.getImports().removeAll(importsToRemove);
            cu.getImports().addAll(importsToAdd);
        }

        // 2. Update JettyStubHttpServer
        CtClass<?> serverClass = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                return element.getSimpleName().equals("JettyStubHttpServer");
            }
        }).get(0);

        // Change type of httpConnector from Connector to ServerConnector
        CtField<?> httpConnectorField = serverClass.getField("httpConnector");
        httpConnectorField.setType(launcher.getFactory().Type().createReference("org.eclipse.jetty.server.ServerConnector"));

        // Replace constructor body
        CtConstructor<?> constructor = serverClass.getConstructor(launcher.getFactory().Type().integerPrimitiveType());
        CtBlock<?> newBody = launcher.getFactory().Core().createBlock();
        CtCodeSnippetStatement snippet = launcher.getFactory().Core().createCodeSnippetStatement();
        snippet.setValue(
                "this.server = new org.eclipse.jetty.server.Server();\n" +
                "org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration();\n" +
                "httpConfig.setSendServerVersion(false);\n" +
                "httpConfig.setSendDateHeader(true);\n" +
                "org.eclipse.jetty.server.HttpConnectionFactory httpFactory = new org.eclipse.jetty.server.HttpConnectionFactory(httpConfig);\n" +
                "this.httpConnector = new org.eclipse.jetty.server.ServerConnector(this.server, httpFactory);\n" +
                "this.httpConnector.setPort(port);\n" +
                "this.server.addConnector(this.httpConnector);"
        );
        newBody.addStatement(snippet);
        constructor.setBody(newBody);
        
        launcher.prettyprint();
    }
}
