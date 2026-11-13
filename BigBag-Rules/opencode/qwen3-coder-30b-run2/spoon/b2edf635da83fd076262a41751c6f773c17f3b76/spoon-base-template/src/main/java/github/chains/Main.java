package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.factory.Factory;

/**
 * Generic Spoon transformation to fix SnakeYAML constructor API changes.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Spoon transformation template for fixing SnakeYAML constructor API changes");
        System.out.println("This would fix cases where Constructor(Class) is used but SnakeYAML 2.0 requires Constructor(Class, LoaderOptions)");
    }
}