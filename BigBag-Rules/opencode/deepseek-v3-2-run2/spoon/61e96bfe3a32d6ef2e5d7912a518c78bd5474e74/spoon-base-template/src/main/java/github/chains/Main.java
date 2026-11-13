package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        // Find all Java files
        List<Path> javaFiles = findJavaFiles(sourceDir);
        
        int transformedCount = 0;
        for (Path file : javaFiles) {
            if (transformFile(file)) {
                transformedCount++;
            }
        }
        
        System.out.println("Transformation complete! Transformed " + transformedCount + " files.");
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(files::add);
        return files;
    }
    
    private static boolean transformFile(Path file) throws IOException {
        String content = Files.readString(file);
        String originalContent = content;
        
        // Check if file contains class extending EncoderBase
        if (!content.contains("extends EncoderBase")) {
            return false;
        }
        
        System.out.println("Transforming file: " + file);
        
        // 1. Remove @Override annotations from old API methods
        content = removeOverrideAnnotations(content);
        
        // 2. Extract type parameter from EncoderBase<E>
        String typeParam = extractTypeParameter(content);
        
        // 3. Check if encode method already exists
        if (!hasMethod(content, "encode")) {
            // 4. Add encode method
            String encodeMethod = createEncodeMethod(typeParam);
            content = insertBeforeClassClosingBrace(content, encodeMethod);
        }
        
        // 5. Add footerBytes method if not exists
        if (!hasMethod(content, "footerBytes")) {
            String footerMethod = "\n    @Override\n    public byte[] footerBytes() {\n        return null;\n    }";
            content = insertBeforeClassClosingBrace(content, footerMethod);
        }
        
        // indexOf6. Add headerBytes method if not exists
        if (!hasMethod(content, "headerBytes")) {
            String headerMethod = "\n    @Override\n    public byte[] headerBytes() {\n        return null;\n    }";
            content = insertBeforeClassClosingBrace(content, headerMethod);
        }
        
        // 7. Fix any syntax errors (like missing closing braces)
        content = fixSyntaxErrors(content);
        
        if (!content.equals(originalContent)) {
            Files.writeString(file, content);
            System.out.println("  - File transformed");
            return true;
        }
        
        return false;
    }
    
    private static String removeOverrideAnnotations(String content) {
        // Remove @Override from init, doEncode, close methods
        // Handle different formatting patterns
        String[] patterns = {
            "@Override\\s+public\\s+(void|byte\\[\\])\\s+(init|doEncode|close)\\s*\\(",
            "@Override\\s+public\\s+(void|byte\\[\\])\\s+(init|doEncode|close)\\s*\\([^)]*\\)\\s*\\{",
            "@Override\\s+public\\s+(void|byte\\[\\])\\s+(init|doEncode|close)\\s*\\([^)]*\\)\\s*throws\\s+[^{]*\\{"
        };
        
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(content);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                // Remove @Override and the whitespace after it
                String replacement = matcher.group().replaceFirst("@Override\\s+", "");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(sb);
            content = sb.toString();
        }
        
        return content;
    }
    
    private static String extractTypeParameter(String content) {
        // Look for "EncoderBase<" followed by type name
        Pattern pattern = Pattern.compile("EncoderBase\\s*<\\s*([^>]+)\\s*>");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "Object";
    }
    
    private static boolean hasMethod(String content, String methodName) {
        // Simple check for method signature
        Pattern pattern = Pattern.compile("public\\s+(?:byte\\[\\]|void|[\\w.<>]+)\\s+" + 
                                         Pattern.quote(methodName) + "\\s*\\([^)]*\\)");
        return pattern.matcher(content).find();
    }
    
    private static String createEncodeMethod(String typeParam) {
        return "\n    @Override\n" +
               "    public byte[] encode(" + typeParam + " event) {\n" +
               "        // Converted from doEncode() for logback 1.2.9+ Encoder API\n" +
               "        // Original doEncode wrote to OutputStream, encode returns byte[]\n" +
               "        // Implement by adapting doEncode logic to write to ByteArrayOutputStream\n" +
               "        try {\n" +
               "            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();\n" +
               "            // TODO: Adapt original doEncode implementation here\n" +
               "            // For example, if doEncode used Thrift: event.write(protocol);\n" +
               "            // Create new transport/protocol for baos instead of existing os\n" +
               "            return baos.toByteArray();\n" +
               "        } catch (Exception e) {\n" +
               "            throw new RuntimeException(e);\n" +
               "        }\n" +
               "    }";
    }
    
    private static String insertBeforeClassClosingBrace(String content, String toInsert) {
        // Find the last closing brace at class level
        // We need to find the closing brace of the class extending EncoderBase
        // Look for the class declaration first
        Pattern classPattern = Pattern.compile("class\\s+\\w+\\s+extends\\s+EncoderBase[^{]*\\{");
        Matcher classMatcher = classPattern.matcher(content);
        
        if (classMatcher.find()) {
            int classStart = classMatcher.end() - 1; // Position of {
            int braceCount = 1;
            int pos = classStart + 1;
            
            while (pos < content.length() && braceCount > 0) {
                char c = content.charAt(pos);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                pos++;
            }
            
            if (braceCount == 0) {
                int classEnd = pos - 1; // Position of matching }
                // Insert before this closing brace
                return content.substring(0, classEnd) + toInsert + content.substring(classEnd);
            }
        }
        
        // Fallback: insert before last closing brace
        int lastBrace = content.lastIndexOf("    }");
        if (lastBrace != -1) {
            return content.substring(0, lastBrace) + toInsert + content.substring(lastBrace);
        }
        
        return content;
    }
    
    private static String fixSyntaxErrors(String content) {
        // Fix common syntax errors like missing closing braces after close() method
        // Look for "close() throws ... {" patterns that might be missing closing brace
        Pattern closePattern = Pattern.compile("(public\\s+void\\s+close\\s*\\([^)]*\\)\\s*throws[^{]*\\{[^}]*)(\\n\\s*@Override\\s+public\\s+byte\\[\\]\\s+encode)");
        Matcher matcher = closePattern.matcher(content);
        
        if (matcher.find()) {
            // Insert closing brace before encode method
            String replacement = matcher.group(1) + "\n    }" + matcher.group(2);
            content = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
        }
        
        return content;
    }
}