package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.*;
import java.util.*;

public class FlywayTextTransformation {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.FlywayTextTransformation <source-dir> <output-dir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying Flyway API migration transformation (text-based)...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addProcessor(new FlywayTextProcessor());
        launcher.run();
        
        System.out.println("Transformation completed successfully!");
    }
}

class FlywayTextProcessor extends spoon.processing.AbstractProcessor<CtClass<?>> {
    
    @Override
    public void process(CtClass<?> clazz) {
        String source = clazz.getPosition().getCompilationUnit().getOriginalSourceCode();
        if (source == null) return;
        
        if (source.contains("new Flyway()")) {
            System.out.println("Found Flyway usage in: " + clazz.getQualifiedName());
            
            String transformed = transformFlywayCode(source);
            if (!source.equals(transformed)) {
                clazz.getPosition().getCompilationUnit().setOriginalSourceCode(transformed);
                System.out.println("  Updated source code");
            }
        }
    }
    
    private String transformFlywayCode(String source) {
        StringBuilder result = new StringBuilder(source);
        
        int index = 0;
        while ((index = result.indexOf("new Flyway()", index)) != -1) {
            System.out.println("  Found 'new Flyway()' at position: " + index);
            
            int lineStart = result.lastIndexOf("\n", index) + 1;
            int lineEnd = result.indexOf("\n", index);
            if (lineEnd == -1) lineEnd = result.length();
            
            String line = result.substring(lineStart, lineEnd);
            System.out.println("  Line: " + line.trim());
            
            int varStart = findVariableStart(result, index);
            if (varStart == -1) {
                index += 12;
                continue;
            }
            
            String varName = extractVariableName(result, varStart, index);
            if (varName == null) {
                index += 12;
                continue;
            }
            
            System.out.println("  Variable name: " + varName);
            
            int blockEnd = findMethodEnd(result, index);
            if (blockEnd == -1) {
                index += 12;
                continue;
            }
            
            String block = result.substring(index, blockEnd);
            List<String> setters = findSetterCalls(block, varName);
            
            if (setters.isEmpty()) {
                result.replace(index, index + 12, "Flyway.configure().load()");
                System.out.println("  Replaced with simple configure().load()");
                index += 30;
            } else {
                StringBuilder newExpression = new StringBuilder("Flyway.configure()");
                for (String setter : setters) {
                    newExpression.append(".").append(convertToFluent(setter));
                }
                newExpression.append(".load()");
                
                result.replace(index, index + 12, newExpression.toString());
                
                for (String setter : setters) {
                    int setterIndex = result.indexOf(setter, index);
                    if (setterIndex != -1) {
                        int setterLineStart = result.lastIndexOf("\n", setterIndex) + 1;
                        int setterLineEnd = result.indexOf("\n", setterIndex);
                        if (setterLineEnd == -1) setterLineEnd = result.length();
                        
                        String setterLine = result.substring(setterLineStart, setterLineEnd);
                        if (setterLine.trim().startsWith(varName + ".")) {
                            result.delete(setterLineStart, setterLineEnd);
                            if (result.charAt(setterLineStart - 1) == '\n' && 
                                setterLineEnd < result.length() && result.charAt(setterLineEnd) == '\n') {
                                result.deleteCharAt(setterLineEnd);
                            }
                        }
                    }
                }
                
                System.out.println("  Replaced with configure() chain");
                index += newExpression.length();
            }
        }
        
        return result.toString();
    }
    
    private int findVariableStart(StringBuilder source, int flywayIndex) {
        int searchIndex = flywayIndex - 1;
        while (searchIndex >= 0 && Character.isWhitespace(source.charAt(searchIndex))) {
            searchIndex--;
        }
        
        if (searchIndex < 0 || source.charAt(searchIndex) == '=') {
            searchIndex--;
            while (searchIndex >= 0 && Character.isWhitespace(source.charAt(searchIndex))) {
                searchIndex--;
            }
            
            int varEnd = searchIndex + 1;
            while (searchIndex >= 0 && (Character.isJavaIdentifierPart(source.charAt(searchIndex)) || 
                   source.charAt(searchIndex) == '<' || source.charAt(searchIndex) == '>')) {
                searchIndex--;
            }
            
            return searchIndex + 1;
        }
        
        return -1;
    }
    
    private String extractVariableName(StringBuilder source, int varStart, int flywayIndex) {
        int varEnd = flywayIndex;
        while (varEnd > varStart && Character.isWhitespace(source.charAt(varEnd - 1))) {
            varEnd--;
        }
        
        if (varEnd > varStart && source.charAt(varEnd - 1) == '=') {
            varEnd--;
            while (varEnd > varStart && Character.isWhitespace(source.charAt(varEnd - 1))) {
                varEnd--;
            }
            
            String varDecl = source.substring(varStart, varEnd);
            String[] parts = varDecl.split("\\s+");
            if (parts.length > 0) {
                return parts[parts.length - 1];
            }
        }
        
        return null;
    }
    
    private int findMethodEnd(StringBuilder source, int start) {
        int braceCount = 0;
        boolean inMethod = false;
        
        for (int i = start; i >= 0; i--) {
            if (source.charAt(i) == '}') {
                braceCount++;
            } else if (source.charAt(i) == '{') {
                braceCount--;
                if (braceCount == 0) {
                    inMethod = true;
                    break;
                }
            }
        }
        
        if (!inMethod) return -1;
        
        braceCount = 0;
        for (int i = start; i < source.length(); i++) {
            if (source.charAt(i) == '{') {
                braceCount++;
            } else if (source.charAt(i) == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return i + 1;
                }
            }
        }
        
        return -1;
    }
    
    private List<String> findSetterCalls(String block, String varName) {
        List<String> setters = new ArrayList<>();
        String[] lines = block.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(varName + ".set")) {
                setters.add(line);
            }
        }
        
        return setters;
    }
    
    private String convertToFluent(String setterCall) {
        int dotIndex = setterCall.indexOf('.');
        int parenIndex = setterCall.indexOf('(');
        
        if (dotIndex == -1 || parenIndex == -1) return setterCall;
        
        String setterName = setterCall.substring(dotIndex + 1, parenIndex);
        String args = setterCall.substring(parenIndex);
        
        if (setterName.startsWith("set")) {
            String fluentName = setterName.substring(3);
            fluentName = Character.toLowerCase(fluentName.charAt(0)) + fluentName.substring(1);
            return fluentName + args;
        }
        
        return setterCall;
    }
}