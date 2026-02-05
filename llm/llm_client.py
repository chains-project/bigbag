#!/usr/bin/env python3
"""
Generic LLM client for transformer-agent.

Responsibilities:
  - Read a prompt from a file.
  - Call the configured LLM provider/model (via environment variables).
  - Write the raw completion to an output file.
  - Write a rich JSON metadata file with usage, timing, and cost information.

Environment configuration (inspired by Bacardi, but simplified and hardened):

  LLM_PROVIDER   : one of: openai, anthropic, google, openrouter, dummy
  LLM_MODEL      : provider-specific model id (e.g. gpt-4o-mini, gemini-2.0-flash-001, claude-3.5-sonnet)

  # OpenAI
  OPENAI_API_KEY : API key (or use LLM_API_KEY as a fallback)

  # Anthropic
  ANTHROPIC_API_KEY : API key

  # Google Gemini
  GOOGLE_API_KEY : API key

  # OpenRouter
  OPENROUTER_API_KEY : API key

  # Generic fallback
  LLM_API_KEY   : used if provider-specific key is not set (for simple setups)

CLI:
  python llm/llm_client.py \
      --prompt-file PROMPT.txt \
      --output-file COMPLETION.txt \
      --meta-file META.json

The script is intentionally self-contained; it does not assume any specific
SDK versions. If a provider SDK is missing, it will fail gracefully with a
clear error message in the metadata file.
"""

import argparse
import json
import os
import time
from pathlib import Path
from typing import Any, Dict, Optional


_DOTENV_LOADED = False


def load_dotenv() -> None:
    """
    Minimal .env loader:
      - Resolves the *project root* by walking up from this file until it finds
        a directory containing a Maven pom.xml (repo root heuristic).
      - Loads the .env found in that root (if any).
      - Populates os.environ ONLY if the key is not already set.

    This guarantees we use the .env at the root of the project, regardless of
    the current working directory.
    """
    global _DOTENV_LOADED
    if _DOTENV_LOADED:
        return

    # Start from the directory containing this script, not from cwd
    here = Path(__file__).resolve().parent
    candidate: Optional[Path] = None

    for parent in [here, *here.parents]:
        pom = parent / "pom.xml"
        env_path = parent / ".env"
        if pom.is_file() and env_path.is_file():
            candidate = env_path
            break

    if candidate is None:
        _DOTENV_LOADED = True
        return

    try:
        for line in candidate.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            value = value.strip().strip('"').strip("'")
            if key and key not in os.environ:
                os.environ[key] = value
    except Exception:
        # Fail silently; environment variables may still be provided by the shell
        pass

    _DOTENV_LOADED = True


def read_env(key: str, default: Optional[str] = None) -> Optional[str]:
    value = os.getenv(key)
    if value is None or value.strip() == "":
        return default
    return value.strip()


def call_openai(prompt: str, model: str) -> Dict[str, Any]:
    try:
        import openai
    except ImportError as e:
        raise RuntimeError("openai package is not installed") from e

    api_key = read_env("OPENAI_API_KEY", read_env("LLM_API_KEY"))
    if not api_key:
        raise RuntimeError("Missing OPENAI_API_KEY or LLM_API_KEY for OpenAI provider")

    # Use the new-style client if available; otherwise fallback.
    # We avoid hard-depending on a specific version beyond this.
    if hasattr(openai, "OpenAI"):
        client = openai.OpenAI(api_key=api_key)
        response = client.chat.completions.create(
            model=model,
            messages=[{"role": "user", "content": prompt}],
        )
        return {"provider": "openai", "raw": response}
    else:
        openai.api_key = api_key
        response = openai.ChatCompletion.create(
            model=model,
            messages=[{"role": "user", "content": prompt}],
        )
        return {"provider": "openai", "raw": response}


def extract_openai_completion(resp: Dict[str, Any]) -> (str, Dict[str, Any]):
    raw = resp["raw"]
    # New client or old client both expose choices[0].message.content
    if hasattr(raw, "choices"):
        choice = raw.choices[0]
        content = getattr(choice.message, "content", None) or choice["message"]["content"]
        # Best-effort conversion of the entire response to a JSON-like dict
        if hasattr(raw, "model_dump"):
            info = raw.model_dump()
        elif hasattr(raw, "to_dict"):
            info = raw.to_dict()
        else:
            info = json.loads(raw.model_dump_json()) if hasattr(raw, "model_dump_json") else json.loads(str(raw))
        return content, info
    else:
        # Extremely defensive fallback
        text = str(raw)
        return text, {"raw": text}


def call_anthropic(prompt: str, model: str) -> Dict[str, Any]:
    try:
        import anthropic
    except ImportError as e:
        raise RuntimeError("anthropic package is not installed") from e

    api_key = read_env("ANTHROPIC_API_KEY", read_env("LLM_API_KEY"))
    if not api_key:
        raise RuntimeError("Missing ANTHROPIC_API_KEY or LLM_API_KEY for Anthropic provider")

    client = anthropic.Anthropic(api_key=api_key)
    response = client.messages.create(
        model=model,
        max_tokens=4096,
        messages=[{"role": "user", "content": prompt}],
    )
    return {"provider": "anthropic", "raw": response}


def extract_anthropic_completion(resp: Dict[str, Any]) -> (str, Dict[str, Any]):
    raw = resp["raw"]
    # Claude responses: content is an array of blocks
    if hasattr(raw, "content"):
        parts = []
        for block in raw.content:
            # block may have .text or be a dict
            text = getattr(block, "text", None) or block.get("text") if isinstance(block, dict) else None
            if text:
                parts.append(text)
        content = "\n".join(parts).strip()
        # Convert full response to JSON-like dict
        if hasattr(raw, "model_dump"):
            info = raw.model_dump()
        else:
            info = json.loads(raw.model_dump_json()) if hasattr(raw, "model_dump_json") else {"raw": str(raw)}
        return content, info
    else:
        text = str(raw)
        return text, {"raw": text}


def call_google(prompt: str, model: str) -> Dict[str, Any]:
    try:
        import google.generativeai as genai
    except ImportError as e:
        raise RuntimeError("google-generativeai package is not installed") from e

    api_key = read_env("GOOGLE_API_KEY", read_env("LLM_API_KEY"))
    if not api_key:
        raise RuntimeError("Missing GOOGLE_API_KEY or LLM_API_KEY for Google provider")

    genai.configure(api_key=api_key)
    gmodel = genai.GenerativeModel(model)
    response = gmodel.generate_content(prompt)
    return {"provider": "google", "raw": response}


def extract_google_completion(resp: Dict[str, Any]) -> (str, Dict[str, Any]):
    raw = resp["raw"]
    try:
        text = raw.text  # google-generativeai response
    except Exception:
        text = str(raw)
    try:
        info = raw.to_dict()
    except Exception:
        info = {"raw": str(raw)}
    return text, info


def call_openrouter(prompt: str, model: str) -> Dict[str, Any]:
    """
    Simple OpenRouter client using HTTP requests. We do not depend on any
    heavy SDK here.
    """
    import requests

    api_key = read_env("OPENROUTER_API_KEY", read_env("LLM_API_KEY"))
    if not api_key:
        raise RuntimeError("Missing OPENROUTER_API_KEY or LLM_API_KEY for OpenRouter provider")

    url = "https://openrouter.ai/api/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
    }
    resp = requests.post(url, headers=headers, json=payload, timeout=60)
    if resp.status_code != 200:
        raise RuntimeError(f"OpenRouter HTTP {resp.status_code}: {resp.text[:200]}")
    data = resp.json()
    return {"provider": "openrouter", "raw": data}


def extract_openrouter_completion(resp: Dict[str, Any]) -> (str, Dict[str, Any]):
    data = resp["raw"]
    try:
        choice = data["choices"][0]
        content = choice["message"]["content"]
    except Exception:
        content = json.dumps(data)
    return content, data


def call_dummy(prompt: str, model: str) -> Dict[str, Any]:
    """
    Dummy provider for offline tests – just echoes the prompt.
    """
    completion = "[DUMMY LLM OUTPUT]\n\n" + prompt
    info = {
        "provider": "dummy",
        "model": model,
        "message": "This is a dummy offline response. No real API call was made."
    }
    return {"provider": "dummy", "completion": completion, "raw": info}


def main() -> None:
    import sys
    
    try:
        parser = argparse.ArgumentParser(description="Generic LLM client for transformer-agent.")
        parser.add_argument("--prompt-file", required=True, help="Path to the prompt text file.")
        parser.add_argument("--output-file", required=True, help="Path where the completion will be written.")
        parser.add_argument("--meta-file", required=True, help="Path where the JSON metadata will be written.")
        args = parser.parse_args()

        # Load .env configuration before reading any LLM_* or provider-specific keys
        load_dotenv()

        prompt_path = Path(args.prompt_file)
        output_path = Path(args.output_file)
        meta_path = Path(args.meta_file)

        # Validate that prompt file exists
        if not prompt_path.exists():
            error_msg = f"Prompt file does not exist: {prompt_path}"
            print(error_msg, file=sys.stderr)
            write_error_meta(meta_path, prompt_path, output_path, error_msg)
            sys.exit(2)

        if not prompt_path.is_file():
            error_msg = f"Prompt path is not a file: {prompt_path}"
            print(error_msg, file=sys.stderr)
            write_error_meta(meta_path, prompt_path, output_path, error_msg)
            sys.exit(2)

        try:
            prompt = prompt_path.read_text(encoding="utf-8")
        except Exception as e:
            error_msg = f"Failed to read prompt file {prompt_path}: {e}"
            print(error_msg, file=sys.stderr)
            write_error_meta(meta_path, prompt_path, output_path, error_msg)
            sys.exit(2)
    except SystemExit:
        raise
    except Exception as e:
        error_msg = f"Fatal error in argument parsing or file setup: {e}"
        print(error_msg, file=sys.stderr)
        import traceback
        traceback.print_exc()
        # Try to write error meta if we have the paths
        try:
            if 'meta_path' in locals() and 'prompt_path' in locals() and 'output_path' in locals():
                write_error_meta(meta_path, prompt_path, output_path, error_msg)
            elif 'meta_path' in locals():
                # Fallback: try to extract paths from args if available
                write_error_meta(meta_path, None, None, error_msg)
        except Exception:
            pass
        sys.exit(2)

    provider = read_env("LLM_PROVIDER", "dummy").lower()
    model = read_env("LLM_MODEL", "dummy-model")

    start = time.time()
    success = False
    error_msg: Optional[str] = None
    completion_text = ""
    response_info: Dict[str, Any] = {}

    try:
        if provider == "openai":
            resp = call_openai(prompt, model)
            completion_text, response_info = extract_openai_completion(resp)
        elif provider == "anthropic":
            resp = call_anthropic(prompt, model)
            completion_text, response_info = extract_anthropic_completion(resp)
        elif provider == "google":
            resp = call_google(prompt, model)
            completion_text, response_info = extract_google_completion(resp)
        elif provider == "openrouter":
            resp = call_openrouter(prompt, model)
            completion_text, response_info = extract_openrouter_completion(resp)
        elif provider == "dummy":
            resp = call_dummy(prompt, model)
            completion_text = resp["completion"]
            response_info = resp["raw"]
        else:
            raise RuntimeError(f"Unsupported LLM_PROVIDER '{provider}'")
        success = True
    except Exception as e:
        error_msg = f"{type(e).__name__}: {e}"
        # In case of error, we still write something minimal to the output
        completion_text = f"[LLM ERROR] {error_msg}"

    duration = time.time() - start

    # Write completion
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(completion_text, encoding="utf-8")

    # Write metadata
    # Meta: copia casi directa del JSON de respuesta del proveedor, envuelta con
    # algunos campos adicionales para trazabilidad.
    meta_payload: Dict[str, Any] = {
        "provider": provider,
        "model": model,
        "prompt_file": str(prompt_path.resolve()),
        "output_file": str(output_path.resolve()),
        "timestamp": time.time(),
        "duration_seconds": duration,
        "success": success,
        "error": error_msg,
        "response": response_info,
    }
    meta_path.parent.mkdir(parents=True, exist_ok=True)
    meta_path.write_text(json.dumps(meta_payload, indent=2, default=str), encoding="utf-8")


def write_error_meta(meta_path: Path, prompt_path: Optional[Path], output_path: Optional[Path], error_msg: str) -> None:
    """Write a minimal error metadata file when something goes wrong early."""
    try:
        meta_path.parent.mkdir(parents=True, exist_ok=True)
        error_meta = {
            "provider": "unknown",
            "model": "unknown",
            "prompt_file": str(prompt_path) if prompt_path is not None else "unknown",
            "output_file": str(output_path) if output_path is not None else "unknown",
            "timestamp": time.time(),
            "duration_seconds": 0,
            "success": False,
            "error": error_msg,
            "response": {}
        }
        meta_path.write_text(json.dumps(error_meta, indent=2, default=str), encoding="utf-8")
    except Exception:
        pass  # If we can't write error meta, at least stderr was printed


if __name__ == "__main__":
    import sys
    try:
        main()
    except KeyboardInterrupt:
        print("Interrupted by user", file=sys.stderr)
        sys.exit(130)
    except Exception as e:
        error_msg = f"Unhandled exception in main: {e}"
        print(error_msg, file=sys.stderr)
        import traceback
        traceback.print_exc()
        # Try to write error meta if we can determine the paths
        if len(sys.argv) >= 6 and "--meta-file" in sys.argv:
            meta_idx = sys.argv.index("--meta-file") + 1
            if meta_idx < len(sys.argv):
                try:
                    meta_path = Path(sys.argv[meta_idx])
                    prompt_path = None
                    output_path = None
                    if "--prompt-file" in sys.argv:
                        prompt_idx = sys.argv.index("--prompt-file") + 1
                        if prompt_idx < len(sys.argv):
                            prompt_path = Path(sys.argv[prompt_idx])
                    if "--output-file" in sys.argv:
                        output_idx = sys.argv.index("--output-file") + 1
                        if output_idx < len(sys.argv):
                            output_path = Path(sys.argv[output_idx])
                    write_error_meta(meta_path, prompt_path, output_path, error_msg)
                except Exception:
                    pass
        sys.exit(2)


