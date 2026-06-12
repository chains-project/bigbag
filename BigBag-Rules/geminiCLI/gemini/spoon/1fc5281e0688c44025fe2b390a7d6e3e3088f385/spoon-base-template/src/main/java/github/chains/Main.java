package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.*;

import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        launcher.buildModel();
        CtModel model = launcher.getModel();

        // 1. Remove javax.servlet imports and add jakarta.servlet imports
        for (CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            List<CtImport> toRemove = new ArrayList<>();
            List<CtImport> toAdd = new ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                String impRef = imp.getReference() != null ? imp.getReference().toString() : "";
                if (impRef.startsWith("javax.servlet")) {
                    toRemove.add(imp);
                    String newImp = impRef.replace("javax.servlet", "jakarta.servlet");
                    toAdd.add(launcher.getFactory().createUnresolvedImport(newImp, false));
                } else if (imp.toString().contains("javax.servlet")) {
                    // For unresolved imports that might not have a reference
                    toRemove.add(imp);
                    String newImp = imp.toString().replace("import ", "").replace(";", "").trim();
                    newImp = newImp.replace("javax.servlet", "jakarta.servlet");
                    toAdd.add(launcher.getFactory().createUnresolvedImport(newImp, false));
                }
                
                if (impRef.contains("org.eclipse.jetty.server.nio.SelectChannelConnector") || impRef.contains("org.eclipse.jetty.server.Connector")) {
                    toRemove.add(imp);
                } else if (imp.toString().contains("org.eclipse.jetty.server.nio.SelectChannelConnector") || imp.toString().contains("org.eclipse.jetty.server.Connector")) {
                    toRemove.add(imp);
                }
            }
            for (CtImport imp : toRemove) cu.getImports().remove(imp);
            for (CtImport imp : toAdd) cu.getImports().add(imp);
        }
        
        // Fix types in references
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (ref.getQualifiedName().startsWith("javax.servlet.")) {
                String newName = ref.getQualifiedName().replace("javax.servlet.", "jakarta.servlet.");
                ref.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                if (ref.getPackage() != null) {
                    ref.getPackage().setSimpleName(newName.substring(0, newName.lastIndexOf('.')));
                }
            }
        }

        // 2. Modify JettyStubHttpServer
        CtClass<?> jettyServerClass = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class))
                .stream().filter(c -> c.getSimpleName().equals("JettyStubHttpServer")).findFirst().get();

        // Change httpConnector field type
        CtField<?> httpConnectorField = jettyServerClass.getField("httpConnector");
        CtTypeReference connectorRef = launcher.getFactory().Type().createReference("org.eclipse.jetty.server.ServerConnector");
        httpConnectorField.setType(connectorRef);

        // Rewrite the constructor taking an int
        CtConstructor<?> constructor = jettyServerClass.getConstructors().stream()
                .filter(c -> c.getParameters().size() == 1).findFirst().get();

        CtBlock<?> body = constructor.getBody();
        body.getStatements().clear();

        body.addStatement(launcher.getFactory().createCodeSnippetStatement("this.server = new org.eclipse.jetty.server.Server()"));
        body.addStatement(launcher.getFactory().createCodeSnippetStatement("org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration()"));
        body.addStatement(launcher.getFactory().createCodeSnippetStatement("httpConfig.setSendServerVersion(false)"));
        body.addStatement(launcher.getFactory().createCodeSnippetStatement("httpConfig.setSendDateHeader(true)"));
        body.addStatement(launcher.getFactory().createCodeSnippetStatement("this.httpConnector = new org.eclipse.jetty.server.ServerConnector(this.server, new org.eclipse.jetty.server.HttpConnectionFactory(httpConfig))"));
        body.addStatement(launcher.getFactory().createCodeSnippetStatement("this.httpConnector.setPort(port)"));
        body.addStatement(launcher.getFactory().createCodeSnippetStatement("this.server.addConnector(this.httpConnector)"));

        // Fix JadlerHandler
        CtClass<?> jadlerHandlerClass = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class))
                .stream().filter(c -> c.getSimpleName().equals("JadlerHandler")).findFirst().get();
        CtMethod<?> handleMethod = jadlerHandlerClass.getMethodsByName("handle").get(0);
        
        // Find the specific assignment that was broken by autoImports
        List<CtLocalVariable> vars = handleMethod.getElements(new TypeFilter<>(CtLocalVariable.class));
        for (CtLocalVariable var : vars) {
            if (var.getSimpleName().equals("req")) {
                var.setType(launcher.getFactory().Type().createReference("net.jadler.Request"));
            }
        }

        launcher.setSourceOutputDirectory("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.prettyprint();
    }
}