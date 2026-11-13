package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    // Configuration for the transformation
    private static final String OLD_PACKAGE_PREFIX = "org.codehaus.plexus.util.xml";
    private static final String NEW_PACKAGE_PREFIX = "org.apache.maven.shared.utils.xml";
    
    // Map of old fully-qualified names to new ones
    // Note: Some classes like XmlPullParserException only exist in plexus-xml (old package)
    // and don't have direct equivalents in maven-shared-utils
    private static final String[][] CLASS_MAPPINGS = {
        // Classes that moved to maven-shared-utils
        {"org.codehaus.plexus.util.xml.Xpp3Dom", "org.apache.maven.shared.utils.xml.Xpp3Dom"},
        {"org.codehaus.plexus.util.xml.Xpp3DomBuilder", "org.apache.maven.shared.utils.xml.Xpp3DomBuilder"},
        {"org.codehaus.plexus.util.xml.Xpp3DomUtils", "org.apache.maven.shared.utils.xml.Xpp3DomUtils"},
        {"org.codehaus.plexus.util.xml.Xpp3DomWriter", "org.apache.maven.shared.utils.xml.Xpp3DomWriter"},
        {"org.codehaus.plexus.util.xml.XmlStreamReader", "org.apache.maven.shared.utils.xml.XmlStreamReader"},
        {"org.codehaus.plexus.util.xml.XmlStreamWriter", "org.apache.maven.shared.utils.xml.XmlStreamWriter"},
        {"org.codehaus.plexus.util.xml.XmlReader", "org.apache.maven.shared.utils.xml.XmlReader"},
        {"org.codehaus.plexus.util.xml.XmlWriterUtil", "org.apache.maven.shared.utils.xml.XmlWriterUtil"},
        {"org.codehaus.plexus.util.xml.PrettyPrintXMLWriter", "org.apache.maven.shared.utils.xml.PrettyPrintXMLWriter"},
        {"org.codehaus.plexus.util.xml.XMLWriter", "org.apache.maven.shared.utils.xml.XMLWriter"},
        // Classes that might need plexus-xml for compatibility
        // {"org.codehaus.plexus.util.xml.pull.XmlPullParserException", null}, // Only in plexus-xml
        // {"org.codehaus.plexus.util.xml.pull.MXParser", null}, // Only in plexus-xml
    };
    
    // Classes that require plexus-xml for compatibility (no direct equivalent in maven-shared-utils)
    private static final String[] REQUIRES_PLEXUS_XML = {
        "org.codehaus.plexus.util.xml.pull.XmlPullParserException",
        "org.codehaus.plexus.util.xml.pull.MXParser",
        "org.codehaus.plexus.util.xml.CompactXMLWriter",
        "org.codehaus.plexus.util.xml.SerializerXMLWriter",
        "org.codehaus.plexus.util.xml.XmlUtil",
    };
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int filesModified = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    filesModified++;
                }
            }
            
            System.out.println("Successfully modified " + filesModified + " files");
            
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path startDir) throws IOException {
        try (Stream<Path> stream = Files.walk(startDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean processFile(Path filePath) {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        try {
            cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
        } catch (IOException e) {
            throw new RuntimeException("Error reading file: " + filePath, e);
        }
        
        // Enable lexical preservation to keep formatting
        cu = LexicalPreservingPrinter.setup(cu);
        
        boolean modified = false;
        
        // Process imports
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration importDecl : imports) {
            String importedName = importDecl.getNameAsString();
            
            // Check if this import needs to be changed
            for (String[] mapping : CLASS_MAPPINGS) {
                String oldClass = mapping[0];
                String newClass = mapping[1];
                
                if (oldClass.equals(importedName) && newClass != null) {
                    System.out.println("  Replacing import: " + oldClass + " -> " + newClass);
                    importDecl.setName(newClass);
                    modified = true;
                    break;
                }
            }
            
            // Check if this import requires plexus-xml
            for (String requiresPlexusXml : REQUIRES_PLEXUS_XML) {
                if (requiresPlexusXml.equals(importedName)) {
                    System.out.println("  Warning: " + importedName + " requires plexus-xml dependency");
                    // Don't change this import - it should remain pointing to plexus-xml
                }
            }
            
            // Also handle wildcard imports
            if (importedName.startsWith(OLD_PACKAGE_PREFIX) && importedName.endsWith(".*")) {
                String newPackage = importedName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                System.out.println("  Warning: Wildcard import " + importedName + " may contain classes that require plexus-xml");
                // Be cautious with wildcard imports - they might include classes that need plexus-xml
                // For now, we'll replace it but warn
                System.out.println("  Replacing wildcard import: " + importedName + " -> " + newPackage);
                importDecl.setName(newPackage);
                modified = true;
            }
        }
        
        // Process type references in the code
        TypeReferenceVisitor visitor = new TypeReferenceVisitor();
        cu.accept(visitor, null);
        if (visitor.isModified()) {
            modified = true;
        }
        
        if (modified) {
            // Write the modified file back
            String newContent = LexicalPreservingPrinter.print(cu);
            try {
                Files.write(filePath, newContent.getBytes());
                System.out.println("  Updated: " + filePath);
            } catch (Exception e) {
                System.err.println("  Error writing " + filePath + ": " + e.getMessage());
                return false;
            }
        }
        
        return modified;
    }
    
    private static class TypeReferenceVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            // Check if this type needs to be replaced
            for (String[] mapping : CLASS_MAPPINGS) {
                String oldClass = mapping[0];
                String newClass = mapping[1];
                
                if (newClass != null && typeName.equals(getSimpleName(oldClass))) {
                    // Check if this is actually referring to the mapped class
                    // (not a same-named class from a different package)
                    // We'll assume it's the one we want to replace
                    System.out.println("  Replacing type reference: " + typeName + " -> " + getSimpleName(newClass));
                    type.setName(getSimpleName(newClass));
                    modified = true;
                }
            }
            
            return super.visit(type, arg);
        }
        
        @Override
        public Visitable visit(NameExpr nameExpr, Void arg) {
            String name = nameExpr.getNameAsString();
            
            // Handle simple name references (e.g., in method calls or variable declarations)
            // This is more complex and might require scope analysis
            // For now, we'll rely on import replacements
            
            return super.visit(nameExpr, arg);
        }
    }
    
    private static String getSimpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }
}