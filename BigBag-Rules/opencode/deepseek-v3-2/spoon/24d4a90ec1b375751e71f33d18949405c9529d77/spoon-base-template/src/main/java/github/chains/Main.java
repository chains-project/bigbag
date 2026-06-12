package github.chains;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.*;

/**
 * Generic transformation to fix the breaking change in jcabi-aspects 0.25.1:
 * The Tv class (com.jcabi.aspects.Tv) was removed. This class contained
 * numeric constants like FIVE, HUNDRED, THOUSAND, etc.
 * 
 * This transformation:
 * 1. Removes imports of com.jcabi.aspects.Tv
 * 2. Replaces field accesses like Tv.FIVE with literal values (5)
 * 3. Handles binary operations like Tv.HUNDRED * Tv.THOUSAND
 * 
 * The transformation is generic and can be applied to any project
 * affected by this breaking change by simply changing the source directory.
 */
public class Main {
    private static final String TV_CLASS = "com.jcabi.aspects.Tv";
    private static final Map<String, Integer> CONSTANT_VALUES = new HashMap<>();
    
    // Statistics
    private static int filesProcessed = 0;
    private static int replacementsMade = 0;
    private static int importsRemoved = 0;
    
    static {
        // Map of Tv constant names to their integer values
        // Based on common numeric constant naming patterns
        CONSTANT_VALUES.put("ZERO", 0);
        CONSTANT_VALUES.put("ONE", 1);
        CONSTANT_VALUES.put("TWO", 2);
        CONSTANT_VALUES.put("THREE", 3);
        CONSTANT_VALUES.put("FOUR", 4);
        CONSTANT_VALUES.put("FIVE", 5);
        CONSTANT_VALUES.put("SIX", 6);
        CONSTANT_VALUES.put("SEVEN", 7);
        CONSTANT_VALUES.put("EIGHT", 8);
        CONSTANT_VALUES.put("NINE", 9);
        CONSTANT_VALUES.put("TEN", 10);
        CONSTANT_VALUES.put("ELEVEN", 11);
        CONSTANT_VALUES.put("TWELVE", 12);
        CONSTANT_VALUES.put("THIRTEEN", 13);
        CONSTANT_VALUES.put("FOURTEEN", 14);
        CONSTANT_VALUES.put("FIFTEEN", 15);
        CONSTANT_VALUES.put("SIXTEEN", 16);
        CONSTANT_VALUES.put("SEVENTEEN", 17);
        CONSTANT_VALUES.put("EIGHTEEN", 18);
        CONSTANT_VALUES.put("NINETEEN", 19);
        CONSTANT_VALUES.put("TWENTY", 20);
        CONSTANT_VALUES.put("THIRTY", 30);
        CONSTANT_VALUES.put("FORTY", 40);
        CONSTANT_VALUES.put("FIFTY", 50);
        CONSTANT_VALUES.put("SIXTY", 60);
        CONSTANT_VALUES.put("SEVENTY", 70);
        CONSTANT_VALUES.put("EIGHTY", 80);
        CONSTANT_VALUES.put("NINETY", 90);
        CONSTANT_VALUES.put("HUNDRED", 100);
        CONSTANT_VALUES.put("THOUSAND", 1000);
        CONSTANT_VALUES.put("MILLION", 1000000);
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Applies transformation to fix removal of com.jcabi.aspects.Tv class");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing source directory: " + sourceDir.toAbsolutePath());
        System.out.println("Breaking change: " + TV_CLASS + " class was removed in jcabi-aspects 0.25.1");
        System.out.println("Transformation: Replacing field accesses with integer literals");
        
        // Walk through all Java files
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    filesProcessed++;
                    boolean modified = processJavaFile(file);
                    if (modified) {
                        System.out.println("  Modified: " + sourceDir.relativize(file));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        System.out.println("\nTransformation completed:");
        System.out.println("  Files processed: " + filesProcessed);
        System.out.println("  Replacements made: " + replacementsMade);
        System.out.println("  Imports removed: " + importsRemoved);
        
        System.out.println("\nNote: This transformation replaces " + TV_CLASS + ".FIELD_NAME with integer literals.");
        System.out.println("If you encounter unknown constants, please add them to the CONSTANT_VALUES map.");
    }
    
    private static boolean processJavaFile(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<String> newLines = new ArrayList<>();
        boolean modified = false;
        
        // Pattern to match Tv.FIELD_NAME
        Pattern tvFieldPattern = Pattern.compile("\\bTv\\.(\\w+)\\b");
        
        for (String line : lines) {
            String originalLine = line;
            
            // Check if this is an import of com.jcabi.aspects.Tv
            if (line.trim().equals("import " + TV_CLASS + ";")) {
                // Skip this line (remove import)
                modified = true;
                importsRemoved++;
                continue;
            }
            
            // Replace Tv.FIELD_NAME occurrences
            Matcher matcher = tvFieldPattern.matcher(line);
            StringBuffer sb = new StringBuffer();
            boolean lineModified = false;
            
            while (matcher.find()) {
                String fieldName = matcher.group(1);
                Integer value = CONSTANT_VALUES.get(fieldName);
                
                if (value != null) {
                    matcher.appendReplacement(sb, value.toString());
                    lineModified = true;
                    replacementsMade++;
                } else {
                    // Keep original if we don't know the value
                    matcher.appendReplacement(sb, matcher.group(0));
                    System.err.println("Warning: Unknown Tv constant '" + fieldName + "' in " + file.getFileName());
                }
            }
            matcher.appendTail(sb);
            line = sb.toString();
            
            // Also handle case where Tv might be fully qualified
            Pattern fqTvFieldPattern = Pattern.compile("\\b" + TV_CLASS.replace(".", "\\.") + "\\.(\\w+)\\b");
            matcher = fqTvFieldPattern.matcher(line);
            sb = new StringBuffer();
            
            while (matcher.find()) {
                String fieldName = matcher.group(1);
                Integer value = CONSTANT_VALUES.get(fieldName);
                
                if (value != null) {
                    matcher.appendReplacement(sb, value.toString());
                    lineModified = true;
                    replacementsMade++;
                } else {
                    matcher.appendReplacement(sb, matcher.group(0));
                }
            }
            matcher.appendTail(sb);
            line = sb.toString();
            
            newLines.add(line);
            modified = modified || lineModified;
        }
        
        if (modified) {
            Files.write(file, newLines);
        }
        
        return modified;
    }
}