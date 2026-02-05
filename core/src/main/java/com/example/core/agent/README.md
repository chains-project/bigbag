# Agent Architecture

This architecture provides a generic and scalable framework for code repair agents, allowing multiple implementations (Spoon, OpenRewrite, etc.) without modifying the main code.

## Structure

```
agent/
├── BaseAgent.java              # Abstract base class with contracts
├── AgentFactory.java           # Factory for dynamic agent selection
├── model/
│   ├── AgentMount.java         # Model for file/volume mounts
│   ├── AgentCommand.java       # Model for execution commands
│   └── AgentSetupResult.java   # Agent setup result
└── impl/
    └── SpoonAgent.java         # Concrete implementation for Spoon
```

## Design Patterns

### Strategy Pattern
- `BaseAgent`: Defines the common contract for all agents
- `SpoonAgent`, `OpenRewriteAgent` (future): Specific implementations

### Factory Pattern
- `AgentFactory`: Dynamically selects the agent based on environment variables

## BaseAgent Contracts

### 1. Environment Validation
```java
void validateEnvironment() throws IllegalStateException
```
Validates that all required environment variables are present.

### 2. Main Execution (Template Method)
```java
AgentExecutionResult execute(AgentExecutionRequest request) throws IOException
```
**This is the main method that each agent implements.**

The agent handles the entire process internally:
- Workspace setup and mounts
- Command preparation
- Docker container execution
- Result and log collection
- Resource cleanup

The pipeline only calls `execute()` and the agent handles everything according to its specific needs.

## Dynamic Agent Selection

The `AgentFactory` determines which agent to instantiate using the following strategy:

1. **Explicit AGENT_NAME**: If `AGENT_NAME` is defined, use that LLM agent
2. **Auto-detection**: Searches for specific environment variables:
   - `BASE_TEMPLATE` or `API_DOCS` → `SpoonAgent`
   - (Future: `OPENREWRITE_*` → `OpenRewriteAgent`)
3. **Fallback**: Defaults to `spoon` rule generator

### Usage Example:

```java
// In AgentRepairPipeline
BaseAgent agent = AgentFactory.createAgent(envConfig);

// Create request with all necessary parameters
AgentExecutionRequest request = new AgentExecutionRequest(
    dockerBuild, projectDir, projectName, dockerImageName, 
    m2Folder, outputBaseDir, commitReportDir, verbose
);

// Execute - the agent handles everything internally
AgentExecutionResult result = agent.execute(request);

// Use results
if (result.success()) {
    // Process logs, copy results, etc.
}
```

## Adding a New Agent

To add a new agent (e.g., OpenRewrite):

1. **Create the agent class**:
```java
public class OpenRewriteAgent extends BaseAgent {
    public OpenRewriteAgent(EnvConfig envConfig, String llmAgentName) {
        super(envConfig, llmAgentName, "openrewrite");
    }
    
    @Override
    public void validateEnvironment() {
        // Validate OPENREWRITE_CONFIG, etc.
    }
    
    @Override
    public AgentExecutionResult execute(AgentExecutionRequest request) throws IOException {
        // Implement the entire execution process:
        // 1. Setup workspace and mounts
        // 2. Prepare OpenRewrite-specific commands
        // 3. Execute in container
        // 4. Collect results
        // 5. Return AgentExecutionResult
        
        Path workspaceDir = setupWorkspace(request);
        // ... rest of OpenRewrite-specific logic
        
        return new AgentExecutionResult(success, workspaceDir, ...);
    }
}
```

2. **Register in AgentFactory**:
```java
// In AgentFactory
static {
    registerRuleGenerator("openrewrite", (envConfig, llmAgentName) -> 
        new OpenRewriteAgent(envConfig, llmAgentName));
}
```

3. **Add automatic detection** (optional):
```java
// In AgentFactory.determineRuleGenerator()
if (envConfig.get("OPENREWRITE_CONFIG").isPresent()) {
    return "openrewrite";
}
```

4. **Configure environment variables**:
```bash
# .env
AGENT_NAME=gemini
RULE_GENERATOR=openrewrite
OPENREWRITE_CONFIG=/path/to/config
```

## Benefits

1. **Decoupling**: `AgentRepairPipeline` is agnostic to the specific agent
2. **Scalability**: Adding new agents does not require modifying existing code
3. **Maintainability**: Each agent's specific logic is encapsulated
4. **Testability**: Easy to mock and test each component separately
