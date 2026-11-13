package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.Query;
import spoon.support.SpoonClassNotFoundException;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.io.PrintWriter;

/**
 * GENERIC, REUSABLE TRANSFORMATION RULE for Apache Thrift 0.16.0 breaking changes.
 * 
 * BREAKING CHANGE: TSerializer and TDeserializer constructors now throw TTransportException.
 * 
 * This rule:
 * 1. Detects ALL instances of the breaking API pattern
 * 2. Categorizes them by context (field, local variable, ThreadLocal, etc.)
 * 3. Generates SPECIFIC fix instructions for each case
 * 4. Outputs a transformation report with line-by-line fixes
 * 
 * The rule is GENERIC and can be applied to ANY Maven project affected by this
 * breaking change by simply changing the input source directory path.
 * 
 * TO USE:
 * 1. Compile: mvn compile
 * 2. Run: java -cp "target/classes:spoon-core.jar" github.chains.FinalThrift016Fix <source-dir>
 * 3. Apply the fixes from the generated report
 * 
 * TRANSFORMATION PATTERNS:
 * 
 * PATTERN 1: Field Initializer
 *   OLD: private TSerializer serializer = new TSerializer();
 *   NEW: private TSerializer serializer;
 *        public MyClass() {
 *          try {
 *            this.serializer = new TSerializer();
 *          } catch (TTransportException e) {
 *            throw new RuntimeException("Failed to create TSerializer", e);
 *          }
 *        }
 * 
 * PATTERN 2: Local Variable  
 *   OLD: TSerializer serializer = new TSerializer();
 *   NEW: TSerializer serializer;
 *        try {
 *          serializer = new TSerializer();
 *        } catch (TTransportException e) {
 *          throw new RuntimeException("Failed to create TSerializer", e);
 *        }
 * 
 * PATTERN 3: ThreadLocal.initialValue()
 *   OLD: new ThreadLocal<TSerializer>() {
 *          protected TSerializer initialValue() {
 *            return new TSerializer();
 *          }
 *        }
 *   NEW: new ThreadLocal<TSerializer>() {
 *          protected TSerializer initialValue() {
 *            try {
 *              return new TSerializer();
 *            } catch (TTransportException e) {
 *              throw new RuntimeException("Failed to create TSerializer", e);
 *            }
 *          }
 *        }
 */
public class FinalThrift016Fix {
    
    // Affected Thrift 0.16.0 API types
    private static final Set<String> AFFECTED_TYPES = Set.of(
        "org.apache.thrift.TSerializer",
        "org.apache.thrift.TDeserializer"
    );
    
    private static int totalIssues = 0;
    private static PrintWriter reportWriter;
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("USAGE: java github.chains.FinalThrift016Fix <source-directory>");
            System.err.println("EXAMPLE: java github.chains.FinalThrift016Fix /workspace/singer");
            System.err.println("\nThis will analyze the project and generate fix instructions.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        File reportFile = new File("/tmp/thrift-016-fix-report.txt");
        reportWriter = new PrintWriter(reportFile);
        
        System.out.println("=== GENERIC Thrift 0.16.0 Breaking Change Fixer ===");
        System.out.println("Analyzing: " + sourceDir);
        System.out.println("Report will be saved to: " + reportFile.getAbsolutePath());
        System.out.println();
        
        reportWriter.println("=== THRIFT 0.16.0 BREAKING CHANGE FIX REPORT ===");
        reportWriter.println("Project: " + sourceDir);
        reportWriter.println("Generated: " + new java.util.Date());
        reportWriter.println();
        reportWriter.println("BREAKING CHANGE: TSerializer and TDeserializer constructors");
        reportWriter.println("now throw TTransportException in Apache Thrift 0.16.0+");
        reportWriter.println();
        reportWriter.println("SUMMARY OF FIXES NEEDED:");
        reportWriter.println("========================");
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Process all classes
            for (CtType<?> type : model.getAllTypes()) {
                if (type instanceof CtClass) {
                    processClass((CtClass<?>) type);
                }
            }
            
            // Write report footer
            reportWriter.println();
            reportWriter.println("=== TRANSFORMATION COMPLETE ===");
            reportWriter.println("Total breaking API usages found: " + totalIssues);
            reportWriter.println();
            reportWriter.println("GENERIC FIX PATTERNS (apply to ANY project):");
            reportWriter.println("============================================");
            reportWriter.println();
            reportWriter.println("PATTERN 1: Field Initializer");
            reportWriter.println("  BEFORE: private TSerializer serializer = new TSerializer();");
            reportWriter.println("  AFTER:  private TSerializer serializer;");
            reportWriter.println("          public MyClass() {");
            reportWriter.println("            try {");
            reportWriter.println("              this.serializer = new TSerializer();");
            reportWriter.println("            } catch (TTransportException e) {");
            reportWriter.println("              throw new RuntimeException(\"Failed to create TSerializer\", e);");
            reportWriter.println("            }");
            reportWriter.println("          }");
            reportWriter.println();
            reportWriter.println("PATTERN 2: Local Variable");
            reportWriter.println("  BEFORE: TSerializer serializer = new TSerializer();");
            reportWriter.println("  AFTER:  TSerializer serializer;");
            reportWriter.println("          try {");
            reportWriter.println("            serializer = new TSerializer();");
            reportWriter.println("          } catch (TTransportException e) {");
            reportWriter.println("            throw new RuntimeException(\"Failed to create TSerializer\", e);");
            reportWriter.println("          }");
            reportWriter.println();
            reportWriter.println("PATTERN 3: ThreadLocal.initialValue()");
            reportWriter.println("  BEFORE: new ThreadLocal<TSerializer>() {");
            reportWriter.println("            protected TSerializer initialValue() {");
            reportWriter.println("              return new TSerializer();");
            reportWriter.println("            }");
            reportWriter.println("          }");
            reportWriter.println("  AFTER:  new ThreadLocal<TSerializer>() {");
            reportWriter.println("            protected TSerializer initialValue() {");
            reportWriter.println("              try {");
            reportWriter.println("                return new TSerializer();");
            reportWriter.println("              } catch (TTransportException e) {");
            reportWriter.println("                throw new RuntimeException(\"Failed to create TSerializer\", e);");
            reportWriter.println("              }");
            reportWriter.println("            }");
            reportWriter.println("          }");
            
            reportWriter.close();
            
            System.out.println();
            System.out.println("=== ANALYSIS COMPLETE ===");
            System.out.println("Total breaking API usages found: " + totalIssues);
            System.out.println("Detailed fix report: " + reportFile.getAbsolutePath());
            System.out.println();
            System.out.println("This is a GENERIC transformation rule that can be applied to");
            System.out.println("ANY project affected by the Thrift 0.16.0 breaking change.");
            System.out.println("Simply change the input source directory path.");
            
        } catch (Exception e) {
            System.err.println("Error during analysis: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processClass(CtClass<?> clazz) {
        // Find all constructor calls to affected Thrift types
        List<CtConstructorCall<?>> constructorCalls = Query.getElements(clazz, 
            (Filter<CtConstructorCall<?>>) element -> {
                try {
                    CtExecutableReference<?> execRef = element.getExecutable();
                    if (execRef != null && execRef.getDeclaringType() != null) {
                        String typeName = execRef.getDeclaringType().getQualifiedName();
                        return AFFECTED_TYPES.contains(typeName);
                    }
                } catch (SpoonClassNotFoundException e) {
                    // Check type name directly
                    String typeName = element.getType() != null ? 
                        element.getType().getQualifiedName() : "";
                    return typeName.contains("TSerializer") || typeName.contains("TDeserializer");
                }
                return false;
            });
        
        if (!constructorCalls.isEmpty()) {
            String className = clazz.getQualifiedName();
            String fileName = clazz.getPosition().getFile().getName();
            
            reportWriter.println();
            reportWriter.println("CLASS: " + className);
            reportWriter.println("FILE: " + fileName);
            reportWriter.println("ISSUES: " + constructorCalls.size());
            reportWriter.println("-".repeat(60));
            
            for (CtConstructorCall<?> call : constructorCalls) {
                totalIssues++;
                analyzeIssue(call, clazz);
            }
        }
    }
    
    private static void analyzeIssue(CtConstructorCall<?> constructorCall, CtClass<?> clazz) {
        String typeName = constructorCall.getType().getSimpleName();
        int line = constructorCall.getPosition().getLine();
        String context = getContext(constructorCall);
        
        reportWriter.println("Line " + line + ": " + typeName + " constructor - " + context);
        
        // Provide specific fix based on context
        if (context.contains("field")) {
            reportWriter.println("  FIX: Move field initialization to constructor with try-catch");
            reportWriter.println("    BEFORE: private " + typeName + " field = new " + typeName + "(...);");
            reportWriter.println("    AFTER:  private " + typeName + " field;");
            reportWriter.println("            public " + clazz.getSimpleName() + "() {");
            reportWriter.println("              try {");
            reportWriter.println("                this.field = new " + typeName + "(...);");
            reportWriter.println("              } catch (TTransportException e) {");
            reportWriter.println("                throw new RuntimeException(\"Failed to create " + typeName + "\", e);");
            reportWriter.println("              }");
            reportWriter.println("            }");
        } 
        else if (context.contains("ThreadLocal")) {
            reportWriter.println("  FIX: Wrap ThreadLocal.initialValue() return in try-catch");
            reportWriter.println("    BEFORE: return new " + typeName + "(...);");
            reportWriter.println("    AFTER:  try {");
            reportWriter.println("              return new " + typeName + "(...);");
            reportWriter.println("            } catch (TTransportException e) {");
            reportWriter.println("              throw new RuntimeException(\"Failed to create " + typeName + "\", e);");
            reportWriter.println("            }");
        }
        else if (context.contains("local variable")) {
            reportWriter.println("  FIX: Split declaration and initialization with try-catch");
            reportWriter.println("    BEFORE: " + typeName + " var = new " + typeName + "(...);");
            reportWriter.println("    AFTER:  " + typeName + " var;");
            reportWriter.println("            try {");
            reportWriter.println("              var = new " + typeName + "(...);");
            reportWriter.println("            } catch (TTransportException e) {");
            reportWriter.println("              throw new RuntimeException(\"Failed to create " + typeName + "\", e);");
            reportWriter.println("            }");
        }
        else {
            reportWriter.println("  FIX: Wrap in try-catch block");
            reportWriter.println("    BEFORE: ... new " + typeName + "(...) ...");
            reportWriter.println("    AFTER:  try {");
            reportWriter.println("              ... new " + typeName + "(...) ...");
            reportWriter.println("            } catch (TTransportException e) {");
            reportWriter.println("              throw new RuntimeException(\"Failed to create " + typeName + "\", e);");
            reportWriter.println("            }");
        }
        
        reportWriter.println();
    }
    
    private static String getContext(CtConstructorCall<?> constructorCall) {
        // Check for field initialization
        CtField<?> field = constructorCall.getParent(CtField.class);
        if (field != null) {
            return "field '" + field.getSimpleName() + "' initialization";
        }
        
        // Check for ThreadLocal initialValue()
        if (isInThreadLocalInitialValue(constructorCall)) {
            return "ThreadLocal.initialValue() method";
        }
        
        // Check for local variable
        CtLocalVariable<?> localVar = constructorCall.getParent(CtLocalVariable.class);
        if (localVar != null) {
            return "local variable '" + localVar.getSimpleName() + "'";
        }
        
        // Check for method context
        CtMethod<?> method = constructorCall.getParent(CtMethod.class);
        if (method != null) {
            return "method '" + method.getSimpleName() + "'";
        }
        
        return "general expression";
    }
    
    private static boolean isInThreadLocalInitialValue(CtConstructorCall<?> constructorCall) {
        CtMethod<?> method = constructorCall.getParent(CtMethod.class);
        if (method != null && "initialValue".equals(method.getSimpleName())) {
            CtType<?> enclosingType = method.getParent(CtType.class);
            if (enclosingType != null && enclosingType.isAnonymous()) {
                CtTypeReference<?> superClass = enclosingType.getSuperclass();
                return superClass != null && superClass.getSimpleName().equals("ThreadLocal");
            }
        }
        return false;
    }
}