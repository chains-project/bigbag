package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }

        File sourceDir = new File(args[0]);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            System.err.println("Source directory does not exist: " + sourceDir.getAbsolutePath());
            System.exit(1);
        }

        // Walk through all Java files in the source directory
        walkDirectory(sourceDir);
    }

    private static void walkDirectory(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                walkDirectory(file);
            } else if (file.getName().endsWith(".java")) {
                transformFile(file);
            }
        }
    }

    private static void transformFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        CompilationUnit cu = JavaParser.parse(fis);
        fis.close();

        // Find the server field in the class
        ServerFieldFinder serverFieldFinder = new ServerFieldFinder();
        serverFieldFinder.visit(cu, null);
        String serverFieldName = serverFieldFinder.getServerFieldName();

        // Transform SelectChannelConnector to ServerConnector
        SelectChannelConnectorTransformer transformer = new SelectChannelConnectorTransformer(serverFieldName);
        transformer.visit(cu, null);

        // Update import statement
        ImportDeclarationTransformer importTransformer = new ImportDeclarationTransformer();
        importTransformer.visit(cu, null);

        // Write the transformed file back
        java.nio.file.Files.write(file.toPath(), cu.toString().getBytes());
    }

    // Find the server field in the class
    private static class ServerFieldFinder extends VoidVisitorAdapter<Void> {
        private String serverFieldName = null;

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            super.visit(n, arg);
            for (FieldDeclaration field : n.getFields()) {
                for (com.github.javaparser.ast.body.VariableDeclarator var : field.getVariables()) {
                    if (var.getType().asString().equals("Server")) {
                        serverFieldName = var.getNameAsString();
                        return;
                    }
                }
            }
        }

        public String getServerFieldName() {
            return serverFieldName;
        }
    }

    // Transform SelectChannelConnector to ServerConnector
    private static class SelectChannelConnectorTransformer extends VoidVisitorAdapter<Void> {
        private final String serverFieldName;

        public SelectChannelConnectorTransformer(String serverFieldName) {
            this.serverFieldName = serverFieldName;
        }

        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            if (n.getType().asString().equals("SelectChannelConnector")) {
                // Check if it's the fully qualified name
                if (n.getType().getNameAsString().equals("SelectChannelConnector")) {
                    // Check if the constructor has no arguments
                    if (n.getArguments().isEmpty()) {
                        // Replace with ServerConnector(server)
                        n.setType("ServerConnector");
                        n.addArgument(new ThisExpr());
                    }
                }
            }
        }
    }

    // Update import statement from SelectChannelConnector to ServerConnector
    private static class ImportDeclarationTransformer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            if (n.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                n.setName("org.eclipse.jetty.server.ServerConnector");
            }
        }
    }
}