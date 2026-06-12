package github.chains;

/**
 * Main entry point for the generic Thrift 0.16.0 package relocation transformation.
 * 
 * This transformation fixes the breaking change in Thrift 0.16.0 where transport
 * classes were moved to the 'layered' subpackage:
 * - org.apache.thrift.transport.TFastFramedTransport -> org.apache.thrift.transport.layered.TFastFramedTransport
 * - org.apache.thrift.transport.TFramedTransport -> org.apache.thrift.transport.layered.TFramedTransport
 * 
 * The transformation is GENERIC and REUSABLE - it can be applied to ANY project
 * affected by this breaking change by simply changing the input source directory.
 * 
 * Usage: java -jar spoon-transform.jar <source-directory> <output-directory>
 * Example: java -jar spoon-transform.jar /path/to/project /path/to/transformed
 */
public class Main {
    public static void main(String[] args) {
        // Delegate to the generic transformation
        GenericThriftFix.main(args);
    }
}