package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtLocalVariable;
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
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        launcher.buildModel();
        CtModel model = launcher.getModel();
        
        // Fix javax.servlet -> jakarta.servlet
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (ref.getQualifiedName().startsWith("javax.servlet")) {
                String newName = ref.getQualifiedName().replace("javax.servlet", "jakarta.servlet");
                ref.setSimpleName(newName.substring(newName.lastIndexOf(".") + 1));
                if (ref.getPackage() != null) {
                    ref.getPackage().setSimpleName(newName.substring(0, newName.lastIndexOf(".")));
                }
            }
        }
        
        // Fix the local variable req in JadlerHandler
        for (CtLocalVariable<?> lv : model.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if ("req".equals(lv.getSimpleName())) {
                lv.setType(launcher.getFactory().Type().createReference("net.jadler.Request"));
            }
        }
        
        // Fix JettyStubHttpServer
        CtClass<?> serverClass = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class))
            .stream().filter(c -> c.getSimpleName().equals("JettyStubHttpServer")).findFirst().get();
            
        CtField<?> connectorField = serverClass.getField("httpConnector");
        connectorField.setType(launcher.getFactory().Type().createReference("org.eclipse.jetty.server.ServerConnector"));
        
        for (CtConstructor<?> constructor : serverClass.getConstructors()) {
            if (constructor.getParameters().size() == 1) {
                CtBlock<?> body = constructor.getBody();
                
                // Clear the body and rewrite it
                body.setStatements(java.util.Collections.emptyList());
                
                body.addStatement(launcher.getFactory().Code().createCodeSnippetStatement("this.server = new org.eclipse.jetty.server.Server()"));
                body.addStatement(launcher.getFactory().Code().createCodeSnippetStatement("org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration()"));
                body.addStatement(launcher.getFactory().Code().createCodeSnippetStatement("httpConfig.setSendServerVersion(false)"));
                body.addStatement(launcher.getFactory().Code().createCodeSnippetStatement("httpConfig.setSendDateHeader(true)"));
                body.addStatement(launcher.getFactory().Code().createCodeSnippetStatement("this.httpConnector = new org.eclipse.jetty.server.ServerConnector(this.server, new org.eclipse.jetty.server.HttpConnectionFactory(httpConfig))"));
                body.addStatement(launcher.getFactory().Code().createCodeSnippetStatement("this.httpConnector.setPort(port)"));
                body.addStatement(launcher.getFactory().Code().createCodeSnippetStatement("server.addConnector(this.httpConnector)"));
            }
        }
        
        // Cleanup imports by removing javax.servlet and SelectChannelConnector
        for (spoon.reflect.declaration.CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            List<CtImport> toRemove = new ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                if (imp.toString().contains("javax.servlet") || imp.toString().contains("SelectChannelConnector")) {
                    toRemove.add(imp);
                }
            }
            for (CtImport imp : toRemove) {
                cu.getImports().remove(imp);
            }
        }
        
        launcher.prettyprint();
    }
}
