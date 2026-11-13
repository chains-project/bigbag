package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

/**
 * Utility to fix datafaker 1.4.0 breaking changes in DateAndTime subclasses
 * This addresses the specific case in flink-faker where methods were incorrectly overridden
 */
public class DatafakerFixer {
    
    public static void main(String[] args) {
        try {
            fixDateTimeClass("/workspace/flink-faker/src/main/java/com/github/knaufk/flink/faker/DateTime.java");
            System.out.println("Fixed DateTime.java for datafaker 1.4.0 compatibility");
        } catch (IOException e) {
            System.err.println("Error fixing file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void fixDateTimeClass(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        
        // Fix 1: Remove the problematic override that doesn't exist in newer datafaker
        // This removes the method that has incompatible signature
        String fixedContent = content.replaceAll(
            "@Override\\s+public\\s+Timestamp\\s+future\\(int\\s+atMost,\\s+TimeUnit\\s+unit,\\s+Date\\s+referenceDate\\)\\s+\\{[^}]+\\}",
            "");
        
        // Fix 2: Correct the between method to return Timestamp (which is what it should be anyway)
        fixedContent = fixedContent.replaceAll(
            "@Override\\s+public\\s+Date\\s+between\\(Date\\s+from,\\s+Date\\s+to\\)\\s+throws\\s+IllegalArgumentException\\s+\\{[^}]+\\}",
            "  @Override\n  public Timestamp between(Date from, Date to) throws IllegalArgumentException {\n    return new Timestamp(super.between(from, to).getTime());\n  }");
        
        Files.write(Paths.get(filePath), fixedContent.getBytes());
    }
}