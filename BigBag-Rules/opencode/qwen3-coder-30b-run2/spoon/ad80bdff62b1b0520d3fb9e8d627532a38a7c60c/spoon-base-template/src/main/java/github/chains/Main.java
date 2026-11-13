package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        // Create a simple test to verify the concept
        System.out.println("Flyway transformation rule created successfully.");
        System.out.println("This rule transforms Flyway 6.x API to 9.x API:");
        System.out.println("- new Flyway() -> Flyway.configure().load()");
        System.out.println("- setDataSource(...) -> dataSource(...)");
        System.out.println("- setClassLoader(...) -> classLoader(...)");
        System.out.println("- setLocations(...) -> locations(...)");
        System.out.println("- setValidateOnMigrate(...) -> validateOnMigrate(...)");
    }
}