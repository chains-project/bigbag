package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic transformation to fix breaking change in jcabi-aspects 0.25.1
 * where the Tv class was removed.
 * 
 * This transformation:
 * 1. Removes imports of com.jcabi.aspects.Tv
 * 2. Replaces Tv.CONSTANT_NAME with appropriate numeric literals
 * 
 * The transformation is generic and can be applied to any project
 * affected by this breaking change by changing the source directory path.
 */
public class Main {
    
    // Map of common constant names to their numeric values
    private static final Map<String, String> CONSTANT_VALUES = new HashMap<>();
    
    static {
        // Basic numbers
        CONSTANT_VALUES.put("ZERO", "0");
        CONSTANT_VALUES.put("ONE", "1");
        CONSTANT_VALUES.put("TWO", "2");
        CONSTANT_VALUES.put("THREE", "3");
        CONSTANT_VALUES.put("FOUR", "4");
        CONSTANT_VALUES.put("FIVE", "5");
        CONSTANT_VALUES.put("SIX", "6");
        CONSTANT_VALUES.put("SEVEN", "7");
        CONSTANT_VALUES.put("EIGHT", "8");
        CONSTANT_VALUES.put("NINE", "9");
        CONSTANT_VALUES.put("TEN", "10");
        CONSTANT_VALUES.put("ELEVEN", "11");
        CONSTANT_VALUES.put("TWELVE", "12");
        CONSTANT_VALUES.put("THIRTEEN", "13");
        CONSTANT_VALUES.put("FOURTEEN", "14");
        CONSTANT_VALUES.put("FIFTEEN", "15");
        CONSTANT_VALUES.put("SIXTEEN", "16");
        CONSTANT_VALUES.put("SEVENTEEN", "17");
        CONSTANT_VALUES.put("EIGHTEEN", "18");
        CONSTANT_VALUES.put("NINETEEN", "19");
        CONSTANT_VALUES.put("TWENTY", "20");
        CONSTANT_VALUES.put("THIRTY", "30");
        CONSTANT_VALUES.put("FORTY", "40");
        CONSTANT_VALUES.put("FIFTY", "50");
        CONSTANT_VALUES.put("SIXTY", "60");
        CONSTANT_VALUES.put("SEVENTY", "70");
        CONSTANT_VALUES.put("EIGHTY", "80");
        CONSTANT_VALUES.put("NINETY", "90");
        CONSTANT_VALUES.put("HUNDRED", "100");
        CONSTANT_VALUES.put("THOUSAND", "1000");
        CONSTANT_VALUES.put("MILLION", "1000000");
        CONSTANT_VALUES.put("BILLION", "1000000000");
        
        // Common multiples
        CONSTANT_VALUES.put("TWENTY_FIVE", "25");
        CONSTANT_VALUES.put("FIFTY_TWO", "52");
        CONSTANT_VALUES.put("SIXTY_FOUR", "64");
        CONSTANT_VALUES.put("ONE_HUNDRED", "100");
        CONSTANT_VALUES.put("ONE_THOUSAND", "1000");
        CONSTANT_VALUES.put("ONE_MILLION", "1000000");
        CONSTANT_VALUES.put("ONE_BILLION", "1000000000");
        
        // Time-related (common in Tv class based on usage)
        CONSTANT_VALUES.put("SECOND", "1");
        CONSTANT_VALUES.put("MINUTE", "60");
        CONSTANT_VALUES.put("HOUR", "3600");
        CONSTANT_VALUES.put("DAY", "86400");
        CONSTANT_VALUES.put("WEEK", "604800");
        CONSTANT_VALUES.put("MONTH", "2592000"); // 30 days
        CONSTANT_VALUES.put("YEAR", "31536000"); // 365 days
    }
    
    /**
     * Visitor that removes Tv imports and replaces Tv constant references.
     */
    private static class TvConstantReplacer extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            // Remove import of com.jcabi.aspects.Tv
            if (importDecl.getNameAsString().equals("com.jcabi.aspects.Tv")) {
                return null; // Remove this import
            }
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(FieldAccessExpr fieldAccess, Void arg) {
            // Check if this is a Tv.CONSTANT access
            if (fieldAccess.getScope().isNameExpr() && 
                fieldAccess.getScope().asNameExpr().getNameAsString().equals("Tv")) {
                
                String constantName = fieldAccess.getNameAsString();
                String replacement = getReplacementValue(constantName);
                
                if (replacement != null) {
                    // Determine if we need a long literal (for large numbers)
                    try {
                        long value = Long.parseLong(replacement.replace("_", ""));
                        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
                            return new LongLiteralExpr(value + "L");
                        } else {
                            return new IntegerLiteralExpr(replacement);
                        }
                    } catch (NumberFormatException e) {
                        // Fallback to integer literal
                        return new IntegerLiteralExpr(replacement);
                    }
                } else {
                    // If we don't know the constant, replace with 0 and print warning
                    System.err.println("Warning: Unknown Tv constant: " + constantName + ", replacing with 0");
return new IntegerLiteralExpr("0");
                }
            }
            
            return (Node) super.visit(fieldAccess, arg);
        }
        
        private String getReplacementValue(String constantName) {
            // First try exact match
            if (CONSTANT_VALUES.containsKey(constantName)) {
                return CONSTANT_VALUES.get(constantName);
            }
            
            // Try to parse spelled-out numbers
            return parseNumberFromName(constantName);
        }
        
        private String parseNumberFromName(String name) {
            // Convert to uppercase and replace underscores
            String upperName = name.toUpperCase();
            
            // Handle common patterns
            if (upperName.endsWith("_MILLION")) {
                String prefix = upperName.substring(0, upperName.length() - 8);
                String prefixValue = getReplacementValue(prefix);
                if (prefixValue != null) {
                    try {
                        long value = Long.parseLong(prefixValue) * 1000000L;
                        return String.valueOf(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            } else if (upperName.endsWith("_THOUSAND")) {
                String prefix = upperName.substring(0, upperName.length() - 9);
                String prefixValue = getReplacementValue(prefix);
                if (prefixValue != null) {
                    try {
                        long value = Long.parseLong(prefixValue) * 1000L;
                        return String.valueOf(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            } else if (upperName.endsWith("_HUNDRED")) {
                String prefix = upperName.substring(0, upperName.length() - 8);
                String prefixValue = getReplacementValue(prefix);
                if (prefixValue != null) {
                    try {
                        long value = Long.parseLong(prefixValue) * 100L;
                        return String.valueOf(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
            
            return null;
        }
    }
    
    /**
     * Process a single Java file.
     */
    private static void processFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
            () -> new IOException("Failed to parse " + filePath)
        );
        
        TvConstantReplacer replacer = new TvConstantReplacer();
        cu.accept(replacer, null);
        
        // Pretty print with original formatting
        PrinterConfiguration config = new DefaultPrinterConfiguration();
        DefaultPrettyPrinterVisitor printer = new DefaultPrettyPrinterVisitor(config);
        printer.visit(cu, null);
        String transformed = printer.toString();
        Files.write(filePath, transformed.getBytes());
    }
    
    /**
     * Recursively process all Java files in a directory.
     */
    private static void processDirectory(Path dirPath) throws IOException {
        Files.walk(dirPath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    System.out.println("Processing: " + path);
                    processFile(path);
                } catch (IOException e) {
                    System.err.println("Error processing " + path + ": " + e.getMessage());
                }
            });
    }
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp target/javaparser-1.0-SNAPSHOT.jar github.chains.Main <source-directory>");
            System.err.println("");
            System.err.println("This transformation fixes the breaking change in jcabi-aspects 0.25.1");
            System.err.println("where the Tv class was removed. It will:");
            System.err.println("1. Remove imports of com.jcabi.aspects.Tv");
            System.err.println("2. Replace Tv.CONSTANT_NAME with appropriate numeric literals");
            System.err.println("");
            System.err.println("Example: java -cp target/javaparser-1.0-SNAPSHOT.jar github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: " + sourceDir + " is not a valid directory");
            System.exit(1);
        }
        
        try {
            System.out.println("Starting transformation for Tv class removal fix...");
            processDirectory(sourceDir);
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}