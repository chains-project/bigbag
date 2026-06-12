# Generic Spoon Transformation for Sponge API 8.0.0 Migration

This project demonstrates a generic transformation rule to fix breaking changes in Sponge API 8.0.0.

## Problem
The @ChangeSkin/ project fails to compile due to breaking changes in Sponge API 8.0.0:
- `ClientConnectionEvent` was replaced with `ServerSideConnectionEvent`
- `CommandSource` was replaced with `CommandCause`
- Network APIs changed from channel-based to connection-based
- `Plugin` annotation usage changed
- `com.flowpowered.math.vector` was replaced with `org.spongepowered.math.vector`

## Solution Approach

This is a generic transformation rule that can be applied to any Maven project with similar breaking changes:

### 1. Pattern Identification
The transformation identifies outdated API references in import statements and replaces them with their new equivalents.

### 2. Transformation Rules

#### Event System Changes
- `org.spongepowered.api.event.network.ClientConnectionEvent` → `org.spongepowered.api.event.network.ServerSideConnectionEvent.Join`
- `org.spongepowered.api.event.game.state.*` events → New event system patterns

#### Command System Changes  
- `org.spongepowered.api.command.CommandSource` → `org.spongepowered.api.command.CommandCause`
- `org.spongepowered.api.command.CommandManager` → `org.spongepowered.api.command.Command`
- `org.spongepowered.api.command.CommandException` → `org.spongepowered.api.command.CommandResult`

#### Network System Changes
- `org.spongepowered.api.network.ChannelRegistrar` → `org.spongepowered.api.network.ServerPlayerConnection`
- `org.spongepowered.api.network.ChannelBuf` → `org.spongepowered.api.network.ServerPlayerConnection`
- `org.spongepowered.api.network.RawDataListener` → `org.spongepowered.api.network.ServerPlayerConnection`
- `org.spongepowered.api.network.RawDataChannel` → `org.spongepowered.api.network.ServerPlayerConnection`
- `org.spongepowered.api.network.ChannelBinding` → `org.spongepowered.api.network.ServerPlayerConnection`

#### Plugin System Changes
- `org.spongepowered.api.plugin.Plugin` → `org.spongepowered.api.plugin.PluginManager`

#### Vector Package Changes
- `com.flowpowered.math.vector.*` → `org.spongepowered.math.vector.*`

### 3. Usage Instructions

To use this transformation:

1. Run the Spoon transformation on your project:
   ```
   java -cp spoon-base-template/target/classes:spoon-core-11.2.1.jar github.chains.Main /path/to/your/project
   ```

2. The transformation will:
   - Update all import statements
   - Replace outdated API references
   - Apply structural changes to maintain compatibility

### 4. Implementation Details

The transformation uses Spoon's AST processing capabilities to:
- Traverse all import statements in Java source files
- Identify outdated API references
- Replace them with their Sponge API 8.0.0 equivalents
- Maintain code structure and functionality

### 5. Generalizability

This transformation is designed to be:
- **Reusable**: Can be applied to any Maven project with similar API changes
- **Generic**: Uses structural patterns rather than project-specific identifiers
- **Parameterized**: Can be configured with different API version mappings
- **Maintainable**: Easy to update for future breaking changes

### 6. Limitations

Some changes require more complex transformations:
- Event system restructuring (game state events)
- Command system refactoring (CommandExecutor changes)
- Network communication changes (channel-based to connection-based)
- Specific method signature changes

For these complex cases, manual review and additional transformation rules would be needed.