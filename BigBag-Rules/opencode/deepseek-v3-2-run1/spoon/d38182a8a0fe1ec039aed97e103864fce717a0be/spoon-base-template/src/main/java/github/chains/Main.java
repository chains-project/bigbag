package github.chains;

/**
 * Main entry point for the User->AuthUser transformation.
 * 
 * This is a generic, reusable transformation that fixes the breaking change
 * from Authentication.User to AuthUser in the com.artipie:http dependency.
 * 
 * The transformation handles:
 * 1. Type references: Authentication.User -> AuthUser
 * 2. Constructor calls: new Authentication.User("name") -> new AuthUser("name", "")
 * 3. Type arguments in parameterized types
 * 
 * Usage: java -jar transformation.jar <source-directory>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println();
            System.err.println("Applies Authentication.User -> AuthUser transformation to fix breaking change");
            System.err.println("from com.artipie:http dependency update.");
            System.err.println();
            System.err.println("The transformation:");
            System.err.println("1. Changes type references from Authentication.User to AuthUser");
            System.err.println("2. Transforms constructor calls: new Authentication.User(\"name\") -> new AuthUser(\"name\", \"\")");
            System.err.println("3. Updates type arguments in parameterized types");
            System.err.println("4. Handles anonymous class expressions");
            System.exit(1);
        }
        
        UserToAuthUserTransformation transformer = new UserToAuthUserTransformation();
        transformer.transform(args[0]);
    }
}