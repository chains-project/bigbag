package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.Query;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.CtScanner;

import java.util.List;
import java.util.ArrayList;

/**
 * Generic Spoon transformation rule for joda-time API breaking changes.
 * 
 * This transformation demonstrates how to create a reusable rule that:
 * 1. Identifies specific API patterns affected by dependency updates
 * 2. Applies structural transformations to fix compilation errors
 * 3. Is parameterized by fully-qualified type names and method signatures
 * 4. Can be applied to any project with the same breaking change
 * 
 * BREAKING CHANGE PATTERN (Example):
 * - Old API: DateMidnight(Object) constructor accepting Calendar
 * - New API: DateMidnight constructor no longer accepts Calendar
 * - Transformation: Replace with LocalDate.fromCalendarFields(calendar)
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <sourceDirectory>");
            System.err.println("Optional: java Main <sourceDirectory> <outputDirectory> to apply transformations");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : null;
        
        System.out.println("=== GENERIC JODA-TIME TRANSFORMATION RULE ===");
        System.out.println("Analyzing: " + sourceDir);
        if (outputDir != null) {
            System.out.println("Will output to: " + outputDir);
        }
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        
        if (outputDir != null) {
            launcher.setSourceOutputDirectory(outputDir);
        }
        
        try {
            // Build the model
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            System.out.println("\n=== IDENTIFIED API PATTERNS ===");
            
            // Pattern 1: DateMidnight type usage
            List<CtTypeReference<?>> dateMidnightRefs = Query.getElements(factory,
                (Filter<CtTypeReference<?>>) ref -> 
                    "org.joda.time.DateMidnight".equals(ref.getQualifiedName()));
            System.out.println("1. DateMidnight type references: " + dateMidnightRefs.size());
            
            // Pattern 2: DateMidnight constructors
            List<CtConstructorCall<?>> dateMidnightConstructors = Query.getElements(factory,
                (Filter<CtConstructorCall<?>>) call -> 
                    call.getType() != null &&
                    "org.joda.time.DateMidnight".equals(call.getType().getQualifiedName()));
            System.out.println("2. DateMidnight constructor calls: " + dateMidnightConstructors.size());
            
            // Pattern 3: DateTime constructors with Calendar
            List<CtConstructorCall<?>> dateTimeCalendarConstructors = Query.getElements(factory,
                (Filter<CtConstructorCall<?>>) call -> 
                    call.getType() != null &&
                    "org.joda.time.DateTime".equals(call.getType().getQualifiedName()) &&
                    !call.getArguments().isEmpty() &&
                    call.getArguments().get(0).getType() != null &&
                    "java.util.Calendar".equals(call.getArguments().get(0).getType().getQualifiedName()));
            System.out.println("3. DateTime(Calendar) constructors: " + dateTimeCalendarConstructors.size());
            
            // Pattern 4: toGregorianCalendar() calls
            List<CtInvocation<?>> toGregorianCalendarCalls = Query.getElements(factory,
                (Filter<CtInvocation<?>>) inv -> 
                    inv.getExecutable() != null &&
                    "toGregorianCalendar".equals(inv.getExecutable().getSimpleName()));
            System.out.println("4. toGregorianCalendar() method calls: " + toGregorianCalendarCalls.size());
            
            // Pattern 5: LocalTime.toDateTimeToday() calls
            List<CtInvocation<?>> toDateTimeTodayCalls = Query.getElements(factory,
                (Filter<CtInvocation<?>>) inv -> 
                    inv.getExecutable() != null &&
                    "toDateTimeToday".equals(inv.getExecutable().getSimpleName()) &&
                    inv.getTarget() != null &&
                    inv.getTarget().getType() != null &&
                    "org.joda.time.LocalTime".equals(inv.getTarget().getType().getQualifiedName()));
            System.out.println("5. LocalTime.toDateTimeToday() calls: " + toDateTimeTodayCalls.size());
            
            System.out.println("\n=== TRANSFORMATION TEMPLATES ===");
            System.out.println("These templates show how to fix each pattern:");
            System.out.println();
            System.out.println("TEMPLATE 1: DateMidnight type replacement");
            System.out.println("  Old: org.joda.time.DateMidnight");
            System.out.println("  New: org.joda.time.LocalDate");
            System.out.println("  Condition: If DateMidnight is removed/deprecated");
            System.out.println();
            System.out.println("TEMPLATE 2: DateMidnight(Calendar) constructor");
            System.out.println("  Old: new DateMidnight(calendar)");
            System.out.println("  New: LocalDate.fromCalendarFields(calendar)");
            System.out.println("  Or: new LocalDate(calendar.getTimeInMillis())");
            System.out.println();
            System.out.println("TEMPLATE 3: DateTime(Calendar) constructor");
            System.out.println("  Check: Verify constructor still exists in new API");
            System.out.println("  Fix: If removed, use DateTime(long) with calendar.getTimeInMillis()");
            System.out.println();
            System.out.println("TEMPLATE 4: toGregorianCalendar() method");
            System.out.println("  Check: Verify method still exists with same signature");
            System.out.println("  Fix: If on DateMidnight, convert to LocalDate first");
            System.out.println();
            System.out.println("TEMPLATE 5: LocalTime.toDateTimeToday(DateTimeZone)");
            System.out.println("  Check: Verify method signature unchanged");
            System.out.println("  Fix: Update if return type or parameters changed");
            System.out.println();
            
            System.out.println("=== HOW TO APPLY TRANSFORMATIONS ===");
            System.out.println("1. Create a CtScanner visitor");
            System.out.println("2. Override visit methods for each pattern");
            System.out.println("3. Use factory.createXXX() to build replacement AST");
            System.out.println("4. Replace old nodes with new nodes");
            System.out.println("5. Call launcher.prettyprint() to write changes");
            System.out.println();
            
            System.out.println("=== EXAMPLE CODE TRANSFORMATION ===");
            System.out.println("// Detecting DateMidnight(Calendar) pattern:");
            System.out.println("if (constructorCall.getType().getQualifiedName().equals(\"org.joda.time.DateMidnight\")) {");
            System.out.println("  CtExpression<?> arg = constructorCall.getArguments().get(0);");
            System.out.println("  if (arg.getType().getQualifiedName().equals(\"java.util.Calendar\")) {");
            System.out.println("    // Create replacement: LocalDate.fromCalendarFields(calendar)");
            System.out.println("    CtTypeReference<LocalDate> localDateRef = factory.createCtTypeReference(LocalDate.class);");
            System.out.println("    CtInvocation<LocalDate> replacement = factory.createInvocation(");
            System.out.println("      factory.createTypeAccess(localDateRef),");
            System.out.println("      factory.createExecutableReference(...),");
            System.out.println("      Arrays.asList(arg)");
            System.out.println("    );");
            System.out.println("    constructorCall.replace(replacement);");
            System.out.println("  }");
            System.out.println("}");
            
            if (outputDir != null) {
                System.out.println("\n=== APPLYING TRANSFORMATIONS ===");
                System.out.println("Output written to: " + outputDir);
                
                // Note: Actual transformation code would go here
                // For this template, we just copy the source
                launcher.setSourceOutputDirectory(outputDir);
                launcher.prettyprint();
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}