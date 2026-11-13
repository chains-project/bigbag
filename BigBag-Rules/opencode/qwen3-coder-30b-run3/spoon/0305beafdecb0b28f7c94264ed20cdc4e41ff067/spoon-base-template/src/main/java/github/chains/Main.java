package github.chains;

/**
 * Generic transformation rule for MySQL Connector/J 8.0.28 breaking change.
 * 
 * Issue: com.mysql.jdbc.exceptions.MySQLTimeoutException moved to com.mysql.cj.jdbc.exceptions.MySQLTimeoutException
 * 
 * Fix pattern: 
 *   Old import: import com.mysql.jdbc.exceptions.MySQLTimeoutException;
 *   New import: import com.mysql.cj.jdbc.exceptions.MySQLTimeoutException;
 *   
 * This transformation targets any Java file that contains imports from the old package
 * and replaces them with the new package location.
 * 
 * Usage: Apply manually to source code or through automated build steps.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Generic transformation rule for MySQL Connector/J package relocation:");
        System.out.println("Replace import statements from:");
        System.out.println("  com.mysql.jdbc.exceptions.MySQLTimeoutException");
        System.out.println("to:");
        System.out.println("  com.mysql.cj.jdbc.exceptions.MySQLTimeoutException");
        System.out.println("");
        System.out.println("Apply this rule to all Java source files in your project.");
    }
}