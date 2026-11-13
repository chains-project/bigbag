package github.chains;

/**
 * Main entry point for the MySQL Connector/J migration transformation.
 * 
 * This is a generic, reusable transformation rule that can be applied to
 * ANY Maven project affected by the MySQL Connector/J 8.0 breaking change.
 * 
 * Breaking Change: MySQL Connector/J package structure changed from
 *   com.mysql.jdbc.* -> com.mysql.cj.jdbc.*
 * 
 * The transformation handles:
 * 1. Import statements
 * 2. Type references in code
 * 3. String literals containing driver class names
 * 
 * Usage: java -jar transformation.jar <source-dir> <output-dir>
 */
public class Main {
    public static void main(String[] args) {
        // Delegate to the actual transformation implementation
        MysqlMigrationTransformation.main(args);
    }
}