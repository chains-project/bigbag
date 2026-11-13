package github.chains;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration: these could be made configurable via command line or properties
    private static final String OLD_POINT_DISTANCE_FUNCTION = "org.tinspin.index.PointDistanceFunction";
    private static final String NEW_POINT_DISTANCE = "org.tinspin.index.PointDistance";
    
    private static final String OLD_POINT_ENTRY_DIST = "org.tinspin.index.PointEntryDist";
    private static final String NEW_POINT_ENTRY_KNN = "org.tinspin.index.Index.PointEntryKnn";
    
    private static final String OLD_POINT_INDEX = "org.tinspin.index.PointIndex";
    private static final String NEW_POINT_MAP = "org.tinspin.index.PointMap";
    
    private static final String OLD_METHOD_QUERY1NN = "query1NN";
    private static final String NEW_METHOD_QUERY1NN = "query1nn";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        // Find all Java files
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        int modifiedCount = 0;
        for (Path javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile);
            if (transformFile(javaFile)) {
                modifiedCount++;
            }
        }
        
        System.out.println("Transformation complete! Modified " + modifiedCount + " out of " + javaFiles.size() + " files.");
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        String transformed = transformContent(content);
        
        if (!content.equals(transformed)) {
            Files.writeString(javaFile, transformed);
            return true;
        }
        return false;
    }
    
    private static String transformContent(String content) {
        StringBuilder transformed = new StringBuilder(content);
        
        // Apply transformations in a more sophisticated way
        
        // First, handle KDTree.create(dims, distanceFn) -> KDTree.create(dims)
        // This is tricky because we need to remove the lambda parameter
        // Look for patterns like "KDTree.create(2, (p1, p2) ->"
        int pos = 0;
        while ((pos = transformed.indexOf("KDTree.create(", pos)) != -1) {
            int start = pos;
            int end = findMatchingParenthesis(transformed, start + "KDTree.create(".length() - 1);
            if (end == -1) break;
            
            String call = transformed.substring(start, end + 1);
            // Check if it has two parameters (comma after first parameter)
            int firstParamEnd = findEndOfFirstParam(call);
            if (firstParamEnd != -1 && call.substring(firstParamEnd).contains(",")) {
                // Remove everything from the comma to the closing parenthesis
                int commaPos = call.indexOf(',', firstParamEnd);
                String newCall = call.substring(0, commaPos) + ")";
                transformed.replace(start, end + 1, newCall);
                pos = start + newCall.length();
            } else {
                pos = end + 1;
            }
        }
        
        // Also handle CoverTree.create(dims, base, distanceFn) -> CoverTree.create(dims, base, distanceFn)
        // This should still work since CoverTree.create still takes PointDistance
        
        // Now do simple string replacements
        String result = transformed.toString();
        
        // 1. Transform imports
        result = result.replace(
            "import " + OLD_POINT_DISTANCE_FUNCTION + ";",
            "import " + NEW_POINT_DISTANCE + ";");
        
        result = result.replace(
            "import " + OLD_POINT_ENTRY_DIST + ";",
            "import " + NEW_POINT_ENTRY_KNN + ";");
        
        result = result.replace(
            "import " + OLD_POINT_INDEX + ";",
            "import " + NEW_POINT_MAP + ";");
        
        // 2. Transform type references in code - handle generics first
        result = result.replace(
            OLD_POINT_DISTANCE_FUNCTION + "<",
            NEW_POINT_DISTANCE + "<");
        result = result.replace(
            OLD_POINT_ENTRY_DIST + "<",
            NEW_POINT_ENTRY_KNN + "<");
        result = result.replace(
            OLD_POINT_INDEX + "<",
            NEW_POINT_MAP + "<");
        
        // Then handle non-generic cases
        result = result.replace(
            OLD_POINT_DISTANCE_FUNCTION,
            NEW_POINT_DISTANCE);
        
        result = result.replace(
            OLD_POINT_ENTRY_DIST,
            NEW_POINT_ENTRY_KNN);
        
        result = result.replace(
            OLD_POINT_INDEX,
            NEW_POINT_MAP);
        
        // 3. Transform method calls
        result = result.replace(
            "." + OLD_METHOD_QUERY1NN + "(",
            "." + NEW_METHOD_QUERY1NN + "(");
        
        return result;
    }
    
    private static int findMatchingParenthesis(StringBuilder str, int start) {
        int count = 1;
        for (int i = start + 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') count++;
            else if (c == ')') {
                count--;
                if (count == 0) return i;
            }
        }
        return -1;
    }
    
    private static int findEndOfFirstParam(String str) {
        // Skip past "KDTree.create("
        int start = str.indexOf('(') + 1;
        int parenCount = 0;
        for (int i = start; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') parenCount++;
            else if (c == ')') {
                if (parenCount == 0) return i;
                parenCount--;
            } else if (c == ',' && parenCount == 0) {
                return i;
            }
        }
        return -1;
    }
}