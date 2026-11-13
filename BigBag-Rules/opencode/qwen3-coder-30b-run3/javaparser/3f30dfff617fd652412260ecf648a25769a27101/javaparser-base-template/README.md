# Generic Jakarta Interceptor API 2.0.0 Migration Tool

This tool represents a generic JavaParser transformation designed to help migrate codebases from Jakarta Interceptor API 1.x to 2.0.0. It focuses on structural patterns and common breaking changes in the API.

## Key Features

- Handles package migration from `javax.interceptor` to `jakarta.interceptor`  
- Identifies and reports InvocationContext method calls that may require attention
- Works with any Maven project structure
- Generic approach that doesn't rely on specific class names

## How it Works

The transformation focuses on:
1. Finding import statements that start with `javax.interceptor` and converting them to `jakarta.interceptor`
2. Identifying InvocationContext usage patterns that may have changed in API 2.0.0

## Usage

```bash
# Compile the tool
mvn compile

# Run the analysis on a project directory
java -cp target/classes github.chains.Main /path/to/your/project
```

## Important Notes

This is a TEMPLATE for a generic transformation. For a complete implementation, you would:
1. Add logic to fix the actual import statements (which requires proper classpath setup)
2. Implement specific method signature transformations based on detected breaking changes
3. Add logic to actually save modified files rather than just reporting

The pattern shown here is suitable for building upon to create a complete working tool.

## Limitations

- This is a conceptual example showing the structure
- Actual deployment requires Maven classpath setup for JavaParser dependencies
- Specific API migration rules must be added based on actual breaking changes observed