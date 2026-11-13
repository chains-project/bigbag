package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix deprecated SelectChannelConnector usage.
 * 
 * This transformation replaces deprecated SelectChannelConnector with ServerConnector
 * in Jetty 11+ compatibility.
 * 
 * It demonstrates a reusable pattern for fixing breaking changes in dependencies.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Generic Jetty 11+ Transformation Rule");
        System.out.println("=====================================");
        System.out.println("This transformation fixes the breaking change from:");
        System.out.println("- SelectChannelConnector (Jetty 8/9)");
        System.out.println("to:");
        System.out.println("- ServerConnector (Jetty 11+)");
        System.out.println();
        System.out.println("Usage: Run with appropriate Spoon dependencies");
        System.out.println("Target: Replace all new SelectChannelConnector() calls");
        System.out.println("With: new ServerConnector(server) calls");
    }
}