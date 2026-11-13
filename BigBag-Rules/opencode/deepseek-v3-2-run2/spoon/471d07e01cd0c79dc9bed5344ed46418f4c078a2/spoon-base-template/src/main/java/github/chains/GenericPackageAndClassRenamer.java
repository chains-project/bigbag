package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenericPackageAndClassRenamer {
    
    // Configuration for package renames
    private static final Map<String, String> PACKAGE_RENAMES = new HashMap<>();
    // Configuration for class renames (fully qualified old name -> fully qualified new name)
    private static final Map<String, String> CLASS_RENAMES = new HashMap<>();
    
    static {
        // Example configuration - would be loaded from config file in real usage
        PACKAGE_RENAMES.put("com.google.api.services.cloudresourcemanager.model", 
                           "com.google.api.services.cloudresourcemanager.v3.model");
        
        // Example class renames if classes were renamed or moved differently
        // CLASS_RENAMES.put("com.google.api.services.cloudresourcemanager.model.OrgPolicy",
        //                   "com.google.api.services.cloudresourcemanager.v3.model.OrgPolicyV3");
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        String configType = args[2];
        
        System.out.println("Starting generic transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        if ("package".equals(configType)) {
            // Simple package rename
            if (args.length < 5) {
                printUsage();
                System.exit(1);
            }
            String oldPackage = args[3];
            String newPackage = args[4];
            PACKAGE_RENAMES.clear();
            PACKAGE_RENAMES.put(oldPackage, newPackage);
        } else if ("config".equals(configType)) {
            // Load from config file (not implemented in this example)
            System.err.println("Config file loading not implemented in this example");
            System.exit(1);
        } else {
            printUsage();
            System.exit(1);
        }
        
        // Create output directory
        Files.createDirectories(Paths.get(outputDir));
        
        // Walk through source files
        Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(sourceFile -> {
                try {
                    transformJavaFile(sourceFile, Paths.get(outputDir));
                } catch (IOException e) {
                    System.err.println("Error processing " + sourceFile + ": " + e.getMessage());
                }
            });
        
        System.out.println("Transformation completed!");
        System.out.println("Summary of transformations:");
        System.out.println("Package renames: " + PACKAGE_RENAMES);
        System.out.println("Class renames: " + CLASS_RENAMES);
    }
    
    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  For package rename:");
        System.err.println("    java GenericPackageAndClassRenamer <source> <output> package <old-pkg> <new-pkg>");
        System.err.println("  Example:");
        System.err.println("    java GenericPackageAndClassRenamer src out package \\");
        System.err.println("      \"com.google.api.services.cloudresourcemanager.model\" \\");
        System.err.println("      \"com.google.api.services.cloudresourcemanager.v3.model\"");
        System.err.println("");
        System.err.println("  For config file (not implemented in example):");
        System.err.println("    java GenericPackageAndClassRenamer <source> <output> config <config-file>");
    }
    
    private static void transformJavaFile(Path sourceFile, Path outputDir) throws IOException {
        String content = Files.readString(sourceFile);
        String transformed = transformJavaContent(content);
        
        if (!content.equals(transformed)) {
            // Create output path preserving directory structure
            Path relativePath = Paths.get(".").relativize(sourceFile);
            Path outputFile = outputDir.resolve(relativePath);
            
            // Create parent directories if needed
            Files.createDirectories(outputFile.getParent());
            
            // Write transformed content
            Files.writeString(outputFile, transformed);
            
            System.out.println("Transformed: " + sourceFile);
        }
    }
    
    private static String transformJavaContent(String content) {
        String transformed = content;
        
        // First apply class renames (these are exact matches)
        for (Map.Entry<String, String> entry : CLASS_RENAMES.entrySet()) {
            String oldClass = entry.getKey();
            String newClass = entry.getValue();
            
            // Replace in imports
            transformed = transformed.replaceAll(
                "import\\s+" + Pattern.quote(oldClass) + "\\s*;",
                "import " + newClass + ";");
            
            // Replace in code (simplified - would need more robust parsing for real usage)
            transformed = transformed.replaceAll(
                "\\b" + Pattern.quote(oldClass) + "\\b",
                newClass);
        }
        
        // Then apply package renames
        for (Map.Entry<String, String> entry : PACKAGE_RENAMES.entrySet()) {
            String oldPackage = entry.getKey();
            String newPackage = entry.getValue();
            
            // Transform import statements
            String importPattern = "import\\s+" + Pattern.quote(oldPackage) + "\\.([^;]+);";
            transformed = transformed.replaceAll(importPattern, "import " + newPackage + ".$1;");
            
            // Transform fully-qualified type names in code
            // This regex tries to match package prefixes followed by class names
            String typePattern = "\\b" + Pattern.quote(oldPackage) + "\\.([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)";
            Pattern p = Pattern.compile(typePattern);
            Matcher m = p.matcher(transformed);
            StringBuffer sb = new StringBuffer();
            
            while (m.find()) {
                String fullMatch = m.group(0);
                String classNamePart = m.group(1);
                String replacement = newPackage + "." + classNamePart;
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            transformed = sb.toString();
        }
        
        return transformed;
    }
}