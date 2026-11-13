package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    // Configuration parameters - can be changed for different transformations
    private static final String OLD_TYPE_DATE_MIDNIGHT = "org.joda.time.DateMidnight";
    private static final String OLD_TYPE_YEAR_MONTH_DAY = "org.joda.time.YearMonthDay";
    private static final String NEW_TYPE = "org.joda.time.LocalDate";
    private static final String NEW_TYPE_SIMPLE = "LocalDate";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming deprecated Joda-Time types in: " + sourceDir);
        
        try {
            transformProject(Paths.get(sourceDir));
            System.out.println("Transformation complete!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir) throws Exception {
        List<File> javaFiles = findJavaFiles(sourceDir.toFile());
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser parser = new JavaParser();
        
        for (File javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile.getPath());
            CompilationUnit cu;
            try {
                cu = parser.parse(javaFile).getResult().orElseThrow(() -> 
                    new RuntimeException("Failed to parse " + javaFile));
            } catch (FileNotFoundException e) {
                System.err.println("File not found: " + javaFile);
                continue;
            }
            
            boolean modified = transformCompilationUnit(cu);
            
            if (modified) {
                // Write back the modified file
                javaFile.getParentFile().mkdirs();
                javaFile.createNewFile();
                try (java.io.FileWriter writer = new java.io.FileWriter(javaFile)) {
                    writer.write(cu.toString());
                }
                System.out.println("  -> Modified");
            }
        }
    }
    
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        JodaTimeDeprecationVisitor visitor = new JodaTimeDeprecationVisitor();
        Node transformed = (Node) visitor.visit(cu, null);
        return visitor.wasModified();
    }
    
    private static List<File> findJavaFiles(File dir) {
        List<File> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(dir, javaFiles);
        return javaFiles;
    }
    
    private static void findJavaFilesRecursive(File dir, List<File> javaFiles) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                findJavaFilesRecursive(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }
    
    static class JodaTimeDeprecationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration id, Void arg) {
            String importName = id.getNameAsString();
            
            // Replace imports of deprecated types
            if (importName.equals(OLD_TYPE_DATE_MIDNIGHT) || importName.equals(OLD_TYPE_YEAR_MONTH_DAY)) {
                modified = true;
                // Check if LocalDate is already imported
                boolean hasLocalDateImport = id.findCompilationUnit()
                    .map(cu -> cu.getImports().stream()
                        .anyMatch(imp -> imp.getNameAsString().equals(NEW_TYPE)))
                    .orElse(false);
                
                if (!hasLocalDateImport) {
                    // Replace the import with LocalDate
                    return new ImportDeclaration(NEW_TYPE, id.isStatic(), id.isAsterisk());
                } else {
                    // LocalDate already imported, remove this import
                    return null;
                }
            }
            
            return (Node) super.visit(id, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            // Replace type references in declarations, return types, etc.
            if (typeName.equals("DateMidnight") || typeName.equals("YearMonthDay")) {
                modified = true;
                return new ClassOrInterfaceType(null, NEW_TYPE_SIMPLE);
            }
            
            // Handle qualified types
            if (typeName.equals(OLD_TYPE_DATE_MIDNIGHT) || typeName.equals(OLD_TYPE_YEAR_MONTH_DAY)) {
                modified = true;
                return new ClassOrInterfaceType(null, NEW_TYPE);
            }
            
            return (Node) super.visit(type, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            // Handle constructor calls: new DateMidnight(...) -> new LocalDate(...)
            // Need to adapt constructor arguments for Calendar case
            
            String typeName = expr.getType().asString();
            
            if (typeName.equals("DateMidnight") || typeName.equals(OLD_TYPE_DATE_MIDNIGHT)) {
                modified = true;
                
                // For constructors, just change the type
                // Note: Calendar constructor needs manual adaptation: 
                // new DateMidnight(cal) -> new LocalDate(cal.getTimeInMillis())
                // We'll leave a comment for manual review
                if (expr.getArguments().size() == 1) {
                    String argCode = expr.getArgument(0).toString();
                    if (argCode.toLowerCase().contains("calendar") || argCode.toLowerCase().contains("cal")) {
                        System.out.println("  WARNING: Calendar constructor found at line " + 
                            expr.getRange().map(r -> r.begin.line).orElse(-1) + 
                            ". Needs manual adaptation: new LocalDate(calendar.getTimeInMillis())");
                    }
                }
                
                return new ObjectCreationExpr(null, 
                    new ClassOrInterfaceType(null, NEW_TYPE_SIMPLE),
                    expr.getArguments());
            }
            
            if (typeName.equals("YearMonthDay") || typeName.equals(OLD_TYPE_YEAR_MONTH_DAY)) {
                modified = true;
                
                // Replace with LocalDate constructor
                // YearMonthDay constructors are compatible with LocalDate
                return new ObjectCreationExpr(null, 
                    new ClassOrInterfaceType(null, NEW_TYPE_SIMPLE),
                    expr.getArguments());
            }
            
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            // Handle method calls that need adaptation
            // e.g., dateMidnight.toGregorianCalendar() -> date.toDateTimeAtStartOfDay().toGregorianCalendar()
            
            String methodName = expr.getNameAsString();
            
            if (methodName.equals("toGregorianCalendar")) {
                // Transform: date.toGregorianCalendar() -> date.toDateTimeAtStartOfDay().toGregorianCalendar()
                // This handles the case where LocalDate doesn't have toGregorianCalendar()
                modified = true;
                
                // Create the chained method call
                MethodCallExpr toDateTimeAtStartOfDay = new MethodCallExpr(
                    expr.getScope().orElse(null), "toDateTimeAtStartOfDay");
                return new MethodCallExpr(toDateTimeAtStartOfDay, "toGregorianCalendar");
            }
            
            // Handle toYearMonthDay() method calls from DateTime
            if (methodName.equals("toYearMonthDay")) {
                modified = true;
                // Replace with toLocalDate()
                return new MethodCallExpr(expr.getScope().orElse(null), "toLocalDate");
            }
            
            return (Node) super.visit(expr, arg);
        }
    }
}