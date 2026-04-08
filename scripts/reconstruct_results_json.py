#!/usr/bin/env python3
"""
Reconstructs breaking-updates-results.json from the output directory artifacts.

Usage:
    python3 reconstruct_results_json.py <output_dir> [options]

Examples:
    # Basic usage
    python3 reconstruct_results_json.py local-report/agent/opencode/javaparser

    # With git branch file (run on the other PC first: git branch > branches.txt)
    python3 reconstruct_results_json.py /path/to/output --branches-file branches.txt

    # Use git repo directly to look up branches
    python3 reconstruct_results_json.py /path/to/output --git-repo /path/to/repo

    # Dry run (no files written)
    python3 reconstruct_results_json.py /path/to/output --dry-run

Data sources for each field (in priority order):
  processId:
    1. agent_compile_output.log  → version string: agent_{type}_{processId}-SNAPSHOT
    2. maven_test_output.log     → same version pattern
    3. mavenCompile.log          → same version pattern
    4. git branches file/repo    → branch name: agent-{type}-{processId}
    5. fallback                  → first 8 chars of commit hash (marked with [FALLBACK])

  originalFailureCategory:
    - breaking-classifier-report.json → failureCategory

  successful (attempt):
    - maven_test_output.log → "BUILD SUCCESS" present

  containerId:
    - Not recoverable from output artifacts → null
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path


# ──────────────────────────────────────────────────────────────
# processId extraction helpers
# ──────────────────────────────────────────────────────────────

def _extract_from_log(log_path: Path, agent_type: str) -> str | None:
    """Scan a log file for the Maven version string embedding the processId."""
    pattern = re.compile(rf"agent[_-]{re.escape(agent_type)}[_-]([a-f0-9]{{8}})", re.IGNORECASE)
    try:
        with open(log_path, "r", errors="replace") as f:
            # The version string appears in the first few KB of the log
            content = f.read(100 * 1024)
        match = pattern.search(content)
        return match.group(1) if match else None
    except OSError:
        return None


def extract_process_id(commit_dir: Path, agent_type: str) -> str | None:
    """Try all log sources to find the processId."""
    for name in ["agent_compile_output.log", "maven_test_output.log", "mavenCompile.log"]:
        result = _extract_from_log(commit_dir / name, agent_type)
        if result:
            return result
    return None


# ──────────────────────────────────────────────────────────────
# Git branch → processId map
# ──────────────────────────────────────────────────────────────

def build_branch_map(agent_type: str, branches_file: Path | None, git_repo: Path | None) -> dict[str, str]:
    """
    Return a map of processId → branch_name by looking at branches
    matching agent-{type}-{processId}.

    NOTE: This gives us processId values but NOT a direct mapping to
    specific commit hashes. It's used as a cross-check / lookup pool
    when the log-based extraction fails.
    """
    branch_pattern = re.compile(rf"agent-{re.escape(agent_type)}-([a-f0-9]{{8}})")
    pid_to_branch: dict[str, str] = {}

    lines: list[str] = []

    if branches_file and branches_file.exists():
        with open(branches_file, "r") as f:
            lines = f.readlines()

    elif git_repo and git_repo.exists():
        try:
            result = subprocess.run(
                ["git", "branch"],
                cwd=git_repo,
                capture_output=True,
                text=True,
                timeout=10,
            )
            lines = result.stdout.splitlines()
        except (subprocess.SubprocessError, FileNotFoundError):
            pass

    for line in lines:
        line = line.strip().lstrip("* ")
        m = branch_pattern.search(line)
        if m:
            pid = m.group(1)
            pid_to_branch[pid] = line.strip()

    return pid_to_branch


# ──────────────────────────────────────────────────────────────
# Success / failure detection
# ──────────────────────────────────────────────────────────────

def determine_success(commit_dir: Path) -> bool:
    """Return True if maven_test_output.log contains BUILD SUCCESS."""
    test_log = commit_dir / "maven_test_output.log"
    if not test_log.exists():
        return False
    try:
        with open(test_log, "r", errors="replace") as f:
            content = f.read()
        return "BUILD SUCCESS" in content
    except OSError:
        return False


def get_original_failure_category(commit_dir: Path) -> str:
    """Read failureCategory from breaking-classifier-report.json."""
    report_file = commit_dir / "breaking-classifier-report.json"
    if not report_file.exists():
        return "COMPILATION_FAILURE"
    try:
        with open(report_file, "r") as f:
            data = json.load(f)
        return data.get("failureCategory", "COMPILATION_FAILURE")
    except (OSError, json.JSONDecodeError):
        return "COMPILATION_FAILURE"


def get_prefix_metrics(commit_dir: Path) -> tuple[int, int]:
    """Read prefix metrics (files, errors) from breaking-classifier-report.json.

    Returns (prefixFiles, prefixErrors).
    """
    report_file = commit_dir / "breaking-classifier-report.json"
    if not report_file.exists():
        return 0, 0
    try:
        with open(report_file, "r") as f:
            data = json.load(f)
        errors_by_file = data.get("errorsByFile", [])
        prefix_files = len(errors_by_file)
        prefix_errors = sum(len(fg.get("errors", [])) for fg in errors_by_file)
        return prefix_files, prefix_errors
    except (OSError, json.JSONDecodeError):
        return 0, 0


# ──────────────────────────────────────────────────────────────
# Agent stats extraction
# ──────────────────────────────────────────────────────────────

def _try_parse_gemini_stats(log_path: Path) -> dict | None:
    """Parse Gemini CLI agent stats from a log file (JSON block at end)."""
    if not log_path.exists():
        return None
    try:
        content = log_path.read_text(errors="replace")
        last_brace = content.rfind("\n{")
        if last_brace < 0 and content.startswith("{"):
            last_brace = 0
        if last_brace < 0:
            return None
        json_part = content[last_brace:].strip()
        root = json.loads(json_part)
        if "stats" not in root or "session_id" not in root:
            return None

        stats = root["stats"]
        models = stats.get("models", {})
        model_name = None
        total_requests = total_errors = 0
        total_latency = total_tokens = input_tokens = output_tokens = cached_tokens = 0

        if models:
            model_name = next(iter(models))
            md = models[model_name]
            api = md.get("api", {})
            total_requests = api.get("totalRequests", 0)
            total_errors = api.get("totalErrors", 0)
            total_latency = api.get("totalLatencyMs", 0)
            tokens = md.get("tokens", {})
            input_tokens = tokens.get("input", 0)
            output_tokens = tokens.get("candidates", 0)
            total_tokens = tokens.get("total", 0)
            cached_tokens = tokens.get("cached", 0)

        tools = stats.get("tools", {})
        files = stats.get("files", {})

        result = {
            "modelName": model_name,
            "totalApiRequests": total_requests,
            "totalApiErrors": total_errors,
            "totalLatencyMs": total_latency,
            "totalTokens": total_tokens,
            "inputTokens": input_tokens,
            "outputTokens": output_tokens,
            "cachedTokens": cached_tokens,
            "totalToolCalls": tools.get("totalCalls", 0),
            "totalToolSuccess": tools.get("totalSuccess", 0),
            "totalToolFail": tools.get("totalFail", 0),
            "totalLinesAdded": files.get("totalLinesAdded", 0),
            "totalLinesRemoved": files.get("totalLinesRemoved", 0),
        }
        by_name = tools.get("byName") or {}
        if isinstance(by_name, dict) and by_name:
            tool_counts = {}
            for name, data in by_name.items():
                if isinstance(data, dict):
                    tool_counts[name] = int(data.get("count", 0))
                else:
                    tool_counts[name] = 0
            if tool_counts:
                result["tools"] = tool_counts

        return {k: v for k, v in result.items() if v is not None}
    except (OSError, json.JSONDecodeError, KeyError):
        return None


def _try_parse_opencode_stats(log_path: Path) -> dict | None:
    """Parse OpenCode agent stats from a JSONL log file."""
    if not log_path.exists():
        return None
    try:
        lines = log_path.read_text(errors="replace").splitlines()
        model_name = None
        total_cost = 0.0
        total_tokens = input_tokens = output_tokens = cached_tokens = 0
        total_api_requests = 0
        tool_calls = tool_success = tool_fail = 0
        first_ts = float("inf")
        last_ts = 0
        found_step_finish = False
        tool_by_name: dict[str, int] = {}

        for line in lines:
            line = line.strip()
            if not line.startswith("{"):
                if model_name is None and "service=llm" in line and "modelID=" in line:
                    idx = line.index("modelID=") + 8
                    rest = line[idx:]
                    end = rest.find(" ")
                    model_name = rest[:end] if end > 0 else rest.strip()
                continue
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            etype = event.get("type", "")
            ts = event.get("timestamp", 0)
            if ts:
                first_ts = min(first_ts, ts)
                last_ts = max(last_ts, ts)

            if etype == "step_finish":
                found_step_finish = True
                total_api_requests += 1
                part = event.get("part", {})
                total_cost += part.get("cost", 0)
                tok = part.get("tokens", {})
                total_tokens += tok.get("total", 0)
                input_tokens += tok.get("input", 0)
                output_tokens += tok.get("output", 0)
                cached_tokens += tok.get("cache", {}).get("read", 0)

            elif etype == "tool_use":
                tool_calls += 1
                status = event.get("part", {}).get("state", {}).get("status", "")
                if status == "completed":
                    tool_success += 1
                elif status == "error":
                    tool_fail += 1
                tn = (event.get("part") or {}).get("tool") or ""
                if tn:
                    tool_by_name[tn] = tool_by_name.get(tn, 0) + 1
                if model_name is None:
                    mid = (event.get("part", {}).get("state", {})
                           .get("metadata", {}).get("model", {}).get("modelID"))
                    if mid:
                        model_name = mid

        if not found_step_finish:
            return None

        result = {
            "modelName": model_name,
            "totalApiRequests": total_api_requests,
            "totalApiErrors": 0,
            "totalLatencyMs": (last_ts - int(first_ts)) if first_ts < last_ts else None,
            "totalTokens": total_tokens,
            "inputTokens": input_tokens,
            "outputTokens": output_tokens,
            "cachedTokens": cached_tokens,
            "totalCost": total_cost if total_cost > 0 else None,
            "totalToolCalls": tool_calls,
            "totalToolSuccess": tool_success,
            "totalToolFail": tool_fail,
        }
        if tool_by_name:
            result["tools"] = dict(sorted(tool_by_name.items()))

        return {k: v for k, v in result.items() if v is not None}
    except (OSError, KeyError):
        return None


def parse_agent_stats(commit_dir: Path) -> dict | None:
    """Parse agent stats from available log files, trying Gemini then OpenCode format."""
    compile_log = commit_dir / "agent_compile_output.log"
    exec_log = commit_dir / "agent_execution.log"

    stats = _try_parse_gemini_stats(compile_log)
    if stats is None:
        stats = _try_parse_gemini_stats(exec_log)
    if stats is None:
        stats = _try_parse_opencode_stats(exec_log)
    if stats is None:
        stats = _try_parse_opencode_stats(compile_log)
    return stats


# ──────────────────────────────────────────────────────────────
# Result builder
# ──────────────────────────────────────────────────────────────

def reconstruct_result(
    commit_hash: str,
    commit_dir: Path,
    agent_type: str,
    output_folder: str,
    branch_pool: dict[str, str],
) -> tuple[dict, bool]:
    """
    Build a Result JSON entry. Returns (entry, used_fallback).
    `used_fallback` is True when processId came from the hash fallback.
    """
    original_category = get_original_failure_category(commit_dir)
    process_id = extract_process_id(commit_dir, agent_type)
    used_fallback = False

    if process_id is None:
        used_fallback = True
        process_id = commit_hash[:8]  # fallback: first 8 chars of commit hash

    successful = determine_success(commit_dir)
    attempt_category = "BUILD_SUCCESS" if successful else original_category
    prefix_files, prefix_errors = get_prefix_metrics(commit_dir)
    agent_stats = parse_agent_stats(commit_dir)

    # Determine branch name: prefer the real one from git if processId is genuine
    if not used_fallback:
        branch_name = f"agent-{agent_type}-{process_id}"
    else:
        branch_name = branch_pool.get(process_id)

    return {
        "breakingCommit": commit_hash,
        "originalFailureCategory": original_category,
        "processId": process_id,
        "fullProcessId": None,
        "branches": {
            "main": "main",
            "agent": branch_name,
            "repair": None,
            "attempts": []
        },
        "attempts": [
            {
                "index": 1,
                "processId": process_id,
                "failureCategory": attempt_category,
                "prefixFiles": prefix_files,
                "prefixErrors": prefix_errors,
                "outputFolder": output_folder,
                "successful": successful,
                "containerId": None,
                "agentStats": agent_stats
            }
        ]
    }, used_fallback


# ──────────────────────────────────────────────────────────────
# Main reconstruction logic
# ──────────────────────────────────────────────────────────────

def is_commit_hash(name: str) -> bool:
    return bool(re.fullmatch(r"[0-9a-f]{40}", name))


def reconstruct(
    output_dir: Path,
    agent_type: str,
    dry_run: bool,
    branches_file: Path | None,
    git_repo: Path | None,
) -> None:
    if not output_dir.is_dir():
        print(f"ERROR: output_dir does not exist: {output_dir}", file=sys.stderr)
        sys.exit(1)

    results_file = output_dir / "breaking-updates-results.json"
    output_folder = str(output_dir)

    commit_dirs = sorted(
        [d for d in output_dir.iterdir() if d.is_dir() and is_commit_hash(d.name)]
    )

    if not commit_dirs:
        print(f"ERROR: No commit directories (40-char hex) found in {output_dir}", file=sys.stderr)
        sys.exit(1)

    print(f"Found {len(commit_dirs)} commit directories in {output_dir}")

    branch_pool = build_branch_map(agent_type, branches_file, git_repo)
    if branch_pool:
        print(f"Loaded {len(branch_pool)} branches from git for cross-reference")

    results: dict[str, dict] = {}
    fallbacks: list[str] = []

    for commit_dir in commit_dirs:
        commit_hash = commit_dir.name
        entry, used_fallback = reconstruct_result(
            commit_hash, commit_dir, agent_type, output_folder, branch_pool
        )
        results[commit_hash] = entry

        successful = entry["attempts"][0]["successful"]
        category = entry["attempts"][0]["failureCategory"]
        process_id = entry["processId"]
        status = "SUCCESS" if successful else "FAILED "
        pid_note = f"pid={process_id}" if not used_fallback else f"pid={process_id} [FALLBACK]"

        print(f"  [{status}] {commit_hash[:12]}...  {pid_note}  category={category}")

        if used_fallback:
            fallbacks.append(commit_hash)

    # Summary
    print()
    total = len(results)
    successes = sum(1 for r in results.values() if r["attempts"][0]["successful"])
    print(f"Summary: {total} commits | {successes} successful | {total - successes} failed")

    if fallbacks:
        print(f"\nWARNING: processId extracted via FALLBACK (first 8 chars of commit hash) for {len(fallbacks)} commit(s):")
        for h in fallbacks:
            print(f"  {h}")
        print("  → branches.agent will be null for these entries.")
        print("  → To fix: run 'git branch > branches.txt' on the repo PC and rerun with --branches-file branches.txt")

    if dry_run:
        print(f"\n[DRY RUN] Would write {total} entries to: {results_file}")
        print(json.dumps(results, indent=2)[:3000])
        if total > 3:
            print("  ... (truncated)")
        return

    # Backup existing file if non-empty
    if results_file.exists() and results_file.stat().st_size > 2:
        backup = results_file.with_suffix(".json.bak")
        import shutil
        shutil.copy2(results_file, backup)
        print(f"\nBacked up existing file to: {backup}")

    with open(results_file, "w") as f:
        json.dump(results, f, indent=2)

    print(f"\nWrote {total} entries to: {results_file}")


# ──────────────────────────────────────────────────────────────
# CLI
# ──────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Reconstruct breaking-updates-results.json from output artifacts",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "output_dir",
        type=Path,
        help="Path to the agent output directory (e.g., local-report/agent/opencode/javaparser)"
    )
    parser.add_argument(
        "--agent-type",
        default="javaparser",
        choices=["javaparser", "spoon"],
        help="Rule-generator type (default: javaparser)"
    )
    parser.add_argument(
        "--branches-file",
        type=Path,
        default=None,
        help="Text file with one branch name per line (output of 'git branch'). "
             "Used to cross-reference processIds when log extraction fails."
    )
    parser.add_argument(
        "--git-repo",
        type=Path,
        default=None,
        help="Path to the git repository. Used to list branches when --branches-file is not given."
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print what would be written without modifying any files"
    )
    args = parser.parse_args()

    reconstruct(
        args.output_dir,
        args.agent_type,
        args.dry_run,
        args.branches_file,
        args.git_repo,
    )


if __name__ == "__main__":
    main()
