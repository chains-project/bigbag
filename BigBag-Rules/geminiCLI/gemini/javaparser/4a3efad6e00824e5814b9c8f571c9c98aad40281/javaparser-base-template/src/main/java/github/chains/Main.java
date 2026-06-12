package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        // Path to the file to be modified
        Path p = Paths.get("/workspace/open-pdf-sign/src/main/java/org/openpdfsign/Signer.java");

        System.out.println("Processing: " + p);

        // Parse the file
        CompilationUnit cu;
        try (FileInputStream in = new FileInputStream(p.toFile())) {
            cu = StaticJavaParser.parse(in);
        }

        // Transformation: Replace the import
        boolean changed = false;
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("eu.europa.esig.dss.pades.CertificationPermission")) {
                importDecl.setName("eu.europa.esig.dss.enumerations.CertificationPermission");
                changed = true;
                System.out.println("Updated import for CertificationPermission");
            }
        }

        // Save the file if changed
        if (changed) {
            try (FileOutputStream out = new FileOutputStream(p.toFile())) {
                out.write(cu.toString().getBytes());
            }
            System.out.println("File modified successfully.");
        } else {
            System.out.println("No changes were made.");
        }
    }
}