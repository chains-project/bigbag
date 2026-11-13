# Generic Guice Dependency Fix Transformation

This is a generic Spoon transformation designed to fix breaking dependency updates in Maven projects that use Guice, specifically addressing API changes in the Binder.addError() method calls.

## Problem Addressed

The transformation fixes compilation errors caused by breaking changes in Guice's Binder API where calls to `binder.addError(...)` have changed in newer versions.

## Usage

To apply this transformation to a project:

1. Compile this project: `mvn compile`
2. Run the transformation with Spoon: `java -cp target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) spoon.SpoonRunner -i /path/to/project/src -o /path/to/output --processors github.chains.Main`

## Structure

The transformation identifies calls to `binder.addError(...)` patterns and can be extended to fix any API breaking change in Guice's Binder API.

## Reusability

This transformation is generic and can be applied to any Maven project with the same breaking API change, not just specific to the Guice project mentioned.