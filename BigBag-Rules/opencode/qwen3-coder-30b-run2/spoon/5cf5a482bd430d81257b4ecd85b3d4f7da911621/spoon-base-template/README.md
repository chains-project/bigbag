# Jakarta MVC Breaking Change Fix Transformation

This is a generic Spoon transformation designed to fix breaking changes in Jakarta MVC dependencies across Maven projects.

## Problem

When upgrading Jakarta MVC dependencies (such as from version 1.x to 2.0.1), developers often encounter breaking changes in the API that require code modifications. This tool provides a reusable solution that can be applied to any Maven project affected by such changes.

## Features

- Generic transformation approach that can be adapted for various breaking changes
- Handles common method signature changes in Jakarta MVC APIs
- Easy to extend for specific breaking changes
- Reusable across multiple projects

## Usage

### Prerequisites

- Java 8 or higher
- Maven project with Jakarta MVC dependencies

### Running the Transformation

```bash
# Compile the transformation
mvn compile

# Run the transformation on a source directory
java -cp target/classes github.chains.Main /path/to/your/project/src
```

The fixed files will be generated in `/path/to/your/project/src-fixed`

### Extending for Specific Changes

To customize for specific breaking changes, modify the `fix*` methods in `Main.java` to implement the exact transformations needed for your situation.

## Supported Patterns

The transformation currently handles:

1. BindingResult method calls (`getAllErrors`, `isFailed`)
2. MvcContext method calls (`uri`, `uriBuilder`) 
3. ViewEngine method calls (`processView`, `supports`)

## Implementation Details

This transformation uses Spoon's AST traversal to:
1. Parse Java source code into an abstract syntax tree
2. Locate method invocations matching known problematic patterns
3. Apply appropriate transformations to fix API calls
4. Generate corrected source code

## Customization

To adapt this transformation for specific breaking changes:

1. Modify the `fix*` methods to implement exact transformations
2. Add new methods to handle additional breaking change patterns
3. Adjust the detection logic to match your specific API changes

## License

MIT