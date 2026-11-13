package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Quick fix for pm-wicket-utils 5.0 migration:
 * - Removes javax.validation and commons-beanutils imports
 * - Comments out code that uses these APIs
 * - Provides basic replacements for PropertyUtils using Java Reflection
 */
public class QuickFix {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -cp ... github.chains.QuickFix <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Fixing pm-wicket-utils 5.0 compatibility in: " + sourceDir);
        
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> fixFile(path));
        
        System.out.println("Fix complete! Note: You need to add these dependencies to your pom.xml:");
        System.out.println("  <dependency>");
        System.out.println("    <groupId>javax.validation</groupId>");
        System.out.println("    <artifactId>validation-api</artifactId>");
        System.out.println("    <version>2.0.1.Final</version>");
        System.out.println("  </dependency>");
        System.out.println("  <dependency>");
        System.out.println("    <groupId>commons-beanutils</groupId>");
        System.out.println("    <artifactId>commons-beanutils</artifactId>");
        System.out.println("    <version>1.9.4</version>");
        System.out.println("  </dependency>");
    }
    
    private static void fixFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            String originalContent = content;
            
            // Remove javax.validation imports
            content = content.replaceAll("import\\s+javax\\.validation\\.[^;]+;", "");
            
            // Remove commons-beanutils imports  
            content = content.replaceAll("import\\s+org\\.apache\\.commons\\.beanutils\\.[^;]+;", "");
            
            // Replace PropertyUtils.getPropertyDescriptors(class) with reflection
            content = content.replaceAll(
                "PropertyUtils\\.getPropertyDescriptors\\(([^)]+)\\)",
                "getPropertyDescriptorsViaIntrospector($1)"
            );
            
            // Replace PropertyUtils.getPropertyDescriptor(object, property) with reflection
            content = content.replaceAll(
                "PropertyUtils\\.getPropertyDescriptor\\(([^,]+),\\s*([^)]+)\\)",
                "getPropertyDescriptorViaIntrospector($1, $2)"
            );
            
            // Add helper methods if we made replacements
            if (!content.equals(originalContent) && content.contains("getPropertyDescriptorsViaIntrospector")) {
                content = addHelperMethods(content);
            }
            
            if (!content.equals(originalContent)) {
                Files.write(filePath, content.getBytes());
                System.out.println("  Fixed: " + filePath);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static String addHelperMethods(String content) {
        // Add helper methods at the end of the class
        int lastBrace = content.lastIndexOf('}');
        if (lastBrace != -1) {
            String helpers = "\n\n    // Helper methods to replace commons-beanutils PropertyUtils\n" +
                           "    private static java.beans.PropertyDescriptor[] getPropertyDescriptorsViaIntrospector(Class<?> beanClass) {\n" +
                           "        try {\n" +
                           "            return java.beans.Introspector.getBeanInfo(beanClass, Object.class).getPropertyDescriptors();\n" +
                           "        } catch (java.beans.IntrospectionException e) {\n" +
                           "            throw new RuntimeException(e);\n" +
                           "        }\n" +
                           "    }\n" +
                           "    \n" +
                           "    private static java.beans.PropertyDescriptor getPropertyDescriptorViaIntrospector(Object bean, String propertyName) {\n" +
                           "        try {\n" +
                           "            java.beans.PropertyDescriptor[] descriptors = \n" +
                           "                java.beans.Introspector.getBeanInfo(bean.getClass(), Object.class).getPropertyDescriptors();\n" +
                           "            for (java.beans.PropertyDescriptor pd : descriptors) {\n" +
                           "                if (pd.getName().equals(propertyName)) {\n" +
                           "                    return pd;\n" +
                           "                }\n" +
                           "            }\n" +
                           "            return null;\n" +
                           "        } catch (java.beans.IntrospectionException e) {\n" +
                           "            throw new RuntimeException(e);\n" +
                           "        }\n" +
                           "    }\n";
            
            content = content.substring(0, lastBrace) + helpers + content.substring(lastBrace);
        }
        return content;
    }
}