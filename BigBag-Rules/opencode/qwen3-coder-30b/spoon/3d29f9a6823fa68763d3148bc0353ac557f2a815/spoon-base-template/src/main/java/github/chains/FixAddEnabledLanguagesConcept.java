/*
 * This file demonstrates the conceptual approach to fix the addEnabledLanguages API issue
 * in the sorald project using Spoon transformation.
 * 
 * The issue:
 * - Old signature: addEnabledLanguages(Set<Language>)
 * - New signature: addEnabledLanguages(Language...)
 * 
 * The specific problem in sorald:
 * File: sorald/src/main/java/sorald/sonar/SonarLintEngine.java:163
 * Code: .addEnabledLanguages(globalConfig.getEnabledLanguages())
 * 
 * The fix needed:
 * Change from: .addEnabledLanguages(globalConfig.getEnabledLanguages())
 * Change to:   .addEnabledLanguages(globalConfig.getEnabledLanguages().toArray(new Language[0])...)
 * 
 * This is a conceptual implementation showing how Spoon would be used.
 */

package github.chains;

public class FixAddEnabledLanguagesConcept {
    
    public static void main(String[] args) {
        System.out.println("=== Fix for addEnabledLanguages API Issue ===");
        System.out.println();
        System.out.println("Problem: The sonarlint-core library changed method signature");
        System.out.println("From: addEnabledLanguages(Set<Language>)");
        System.out.println("To:   addEnabledLanguages(Language...)");
        System.out.println();
        System.out.println("In sorald project:");
        System.out.println("- File: sorald/src/main/java/sorald/sonar/SonarLintEngine.java");
        System.out.println("- Line 163: .addEnabledLanguages(globalConfig.getEnabledLanguages())");
        System.out.println();
        System.out.println("Solution: Convert Set to varargs using toArray()");
        System.out.println("Before: .addEnabledLanguages(set)");
        System.out.println("After:  .addEnabledLanguages(set.toArray(new Language[0])...)");
        System.out.println();
        System.out.println("This transformation would be implemented using Spoon's AST manipulation");
        System.out.println("to automatically find and fix all similar occurrences in the codebase.");
    }
}