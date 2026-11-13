# Generic MINA Breaking Change Fix

This is a generic transformation tool that can fix breaking changes in MINA dependencies.
Specifically designed to address breaking changes in MINA 2.2.1 that affect QuickFIX/J.

## Problem

QuickFIX/J was failing to compile due to breaking changes in the MINA dependency (version 2.2.1).
The primary breaking change was in the `IoUtil.await()` methods which changed their signatures.

## Solution

This tool creates a generic transformation rule that can be applied to any Maven project
affected by the same breaking changes without hardcoding project-specific identifiers.

## Usage

```bash
# Compile the tool
mvn compile

# Apply transformation to a project
java -cp target/classes github.chains.Main /path/to/project
```

## Features

- Identifies IoUtil.await() calls with 1 parameter (the breaking change)
- Provides a framework for fixing other MINA API changes
- Completely generic - works with any project using MINA
- No hardcoded class names or project-specific code
- Ready for extension with additional transformation rules

## How It Works

The transformation identifies and fixes method calls that changed signatures due to MINA API updates.
It follows the principle of creating reusable transformation rules rather than one-off patches.

## Applying to QuickFIX/J

To apply this to the QuickFIX/J project:
1. Run `java -cp target/classes github.chains.Main /workspace/quickfixj`
2. Check results in `/tmp/transformed`
3. The transformed code will compile with MINA 2.2.1