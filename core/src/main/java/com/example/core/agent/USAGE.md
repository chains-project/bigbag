# How to Use Agents

The architecture separates two concepts:
- **AGENT_NAME**: The LLM/AI to use (e.g., "gemini", "copilot")
- **RULE_GENERATOR**: The rule generator (e.g., "spoon", "javaparser", "openrewrite")

This allows combinations like: gemini+spoon, copilot+spoon, gemini+openrewrite, etc.

## Basic Configuration

```bash
# .env
AGENT_NAME=gemini          # LLM to use (gemini, copilot, etc.)
RULE_GENERATOR=spoon       # Rule generator (spoon, javaparser, openrewrite, etc.)
BASE_TEMPLATE=/path/to/base-template     # Used by Spoon and JavaParser
API_DOCS=/path/to/api-docs              # Used by Spoon and JavaParser
LLM_API_KEY=your_api_key_here
```

## Auto-detection

If you don't define `RULE_GENERATOR`, the system will attempt to infer a sensible default:

```bash
# .env (without RULE_GENERATOR)
AGENT_NAME=gemini
BASE_TEMPLATE=/path/to/base-template
API_DOCS=/path/to/api-docs
LLM_API_KEY=your_api_key_here
```

Because both Spoon and JavaParser use `BASE_TEMPLATE` and `API_DOCS`, the factory cannot distinguish them automatically.  
In this situation, the system **defaults to `spoon`** for backward compatibility.  
If you want to use JavaParser, you **must** set `RULE_GENERATOR=javaparser`.

## Default Values

- `AGENT_NAME`: Defaults to `"gemini"` if not specified
- `RULE_GENERATOR`: Defaults to `"spoon"` if not specified or auto-detected

## Required/Optional Environment Variables

### Required:
- `LLM_API_KEY`: API key for the LLM

### Optional but Recommended:
- `AGENT_NAME`: LLM to use (default: "gemini")
- `RULE_GENERATOR`: Rule generator (default: "spoon" or auto-detected)
- `BASE_TEMPLATE`: Path to base template (for Spoon: spoon base project; for JavaParser: javaparser base project)
- `API_DOCS`: Path to API documentation (for Spoon: spoon-core javadoc; for JavaParser: javaparser javadoc)

## Complete .env Example

```bash
# LLM Agent
AGENT_NAME=gemini

# Rule Generator
RULE_GENERATOR=spoon           # or "javaparser"

# Rule Generator Configuration
BASE_TEMPLATE=/home/user/base-template
API_DOCS=/home/user/api-docs

# LLM Configuration
LLM_API_KEY=AIzaSyXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

# Other configurations...
```

## Combination Examples

```bash
# Gemini + Spoon
AGENT_NAME=gemini
RULE_GENERATOR=spoon

# Gemini + JavaParser
AGENT_NAME=gemini
RULE_GENERATOR=javaparser

# Copilot + Spoon
AGENT_NAME=copilot
RULE_GENERATOR=spoon

# Gemini + OpenRewrite (future)
AGENT_NAME=gemini
RULE_GENERATOR=openrewrite
```

## Usage in Code

The pipeline is already configured to use the agent automatically. You don't need to change anything in the code:

```java
// In AgentRepairPipeline - already implemented
BaseAgent agent = AgentFactory.createAgent(envConfig);
AgentExecutionRequest request = new AgentExecutionRequest(...);
AgentExecutionResult result = agent.execute(request);
```

## Verification

To verify which agent is being used, check the logs:

```
INFO  - Using LLM agent: gemini
INFO  - Using rule generator: spoon
```

or

```
INFO  - Auto-detected rule generator: spoon
```

## Docker Image

The Docker image is based on `AGENT_NAME` (LLM):
- `images/{AGENT_NAME}/Dockerfile` (e.g., `images/gemini/Dockerfile`)
- The image will be named: `{AGENT_NAME}:latest` (e.g., `gemini:latest`)

The container contains the LLM (gemini, copilot, etc.) and can use any rule generator (spoon, openrewrite, etc.).

Make sure you have the Dockerfile in the correct location before running.
