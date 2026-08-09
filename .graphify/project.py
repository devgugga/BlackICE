"""BlackICE project adapter for Graphify file detection.

Graphify's native detector deliberately supports a bounded set of formats.
BlackICE also treats tracked operational configuration as architecture
knowledge, so this adapter routes safe text configuration through the semantic
``document`` pipeline. Keep this module small and remove it when upstream
detection covers the same corpus.

Baseline: revalidated against Graphify 0.9.32 on 2026-08-09 — the five private
symbols imported below still exist, ``tests/test_project.py`` passes, and the
native detector still misses the tracked configuration corpus.
"""

from __future__ import annotations

import subprocess
from pathlib import Path

from graphify.detect import (
    _is_sensitive,
    _md5_file,
    detect,
    detect_incremental,
    load_manifest,
)


_CONFIG_SUFFIXES = {
    ".bash",
    ".bat",
    ".cfg",
    ".cmd",
    ".conf",
    ".css",
    ".fish",
    ".ini",
    ".less",
    ".properties",
    ".ps1",
    ".psm1",
    ".sass",
    ".scss",
    ".sh",
    ".toml",
    ".xml",
    ".yaml",
    ".yml",
    ".zsh",
}
_CONFIG_NAMES = {
    ".claudeignore",
    ".dockerignore",
    ".gitattributes",
    ".gitignore",
    ".graphifyignore",
    "Containerfile",
    "Dockerfile",
    "mvnw",
    "mvnw.cmd",
    "nginx.conf",
}
_EXCLUDED_PARTS = {
    ".git",
    ".superpowers",
    ".worktrees",
    "dist",
    "graphify-out",
    "node_modules",
    "target",
}
_MAX_CONFIG_BYTES = 1_000_000


def _tracked_files(root: Path) -> list[Path]:
    completed = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z", "--cached"],
        check=True,
        capture_output=True,
    )
    return [
        (root / raw.decode("utf-8")).resolve()
        for raw in completed.stdout.split(b"\0")
        if raw
    ]


def _is_project_config(path: Path, root: Path) -> bool:
    try:
        relative = path.relative_to(root)
        size = path.stat().st_size
    except (OSError, ValueError):
        return False
    if any(part in _EXCLUDED_PARTS for part in relative.parts):
        return False
    if path.name.startswith(".env") or _is_sensitive(path):
        return False
    if size > _MAX_CONFIG_BYTES:
        return False
    return (
        path.name in _CONFIG_NAMES
        or path.name.startswith("Dockerfile.")
        or path.suffix.lower() in _CONFIG_SUFFIXES
    )


def _is_excluded(path: Path, root: Path) -> bool:
    try:
        relative = path.resolve().relative_to(root)
    except ValueError:
        return True
    return (
        any(part in _EXCLUDED_PARTS for part in relative.parts)
        or path.name.startswith(".env")
        or _is_sensitive(path)
    )


def _sanitize_native_result(result: dict, root: Path) -> None:
    for key in ("files", "new_files"):
        for category, paths in result.get(key, {}).items():
            result[key][category] = [
                path for path in paths if not _is_excluded(Path(path), root)
            ]
    result["deleted_files"] = [
        path
        for path in result.get("deleted_files", [])
        if not _is_excluded(Path(path), root)
    ]


def _additional_configs(root: Path, existing: set[Path]) -> list[Path]:
    return sorted(
        (
            path
            for path in _tracked_files(root)
            if path.is_file()
            and path not in existing
            and _is_project_config(path, root)
        ),
        key=lambda path: path.as_posix(),
    )


def _all_detected_paths(result: dict) -> set[Path]:
    return {
        Path(path).resolve()
        for paths in result.get("files", {}).values()
        for path in paths
    }


def _word_count(paths: list[Path]) -> int:
    total = 0
    for path in paths:
        try:
            total += len(path.read_text(encoding="utf-8", errors="ignore").split())
        except OSError:
            continue
    return total


def detect_project(root: Path) -> dict:
    """Run native detection and add safe, tracked BlackICE configuration."""

    root = root.resolve()
    result = detect(root)
    _sanitize_native_result(result, root)
    configs = _additional_configs(root, _all_detected_paths(result))
    documents = result.setdefault("files", {}).setdefault("document", [])
    documents.extend(str(path) for path in configs)
    documents.sort()
    result["total_files"] = sum(len(paths) for paths in result["files"].values())
    result["total_words"] = result.get("total_words", 0) + _word_count(configs)
    result["project_config_files"] = [str(path) for path in configs]
    return result


def detect_incremental_project(root: Path) -> dict:
    """Run incremental detection with the same project configuration corpus."""

    root = root.resolve()
    result = detect_incremental(root)
    _sanitize_native_result(result, root)
    existing = _all_detected_paths(result)
    configs = _additional_configs(root, existing)
    config_strings = [str(path) for path in configs]

    documents = result.setdefault("files", {}).setdefault("document", [])
    documents.extend(config_strings)
    documents.sort()

    manifest = load_manifest(root=root)
    changed_configs = []
    for path in configs:
        stored = manifest.get(str(path))
        if not isinstance(stored, dict) or stored.get("semantic_hash") != _md5_file(path):
            changed_configs.append(str(path))

    new_documents = result.setdefault("new_files", {}).setdefault("document", [])
    new_documents.extend(path for path in changed_configs if path not in new_documents)
    new_documents.sort()

    current_configs = {str(path.resolve()) for path in configs}
    result["deleted_files"] = [
        path
        for path in result.get("deleted_files", [])
        if str(Path(path).resolve()) not in current_configs
    ]
    result["new_total"] = sum(
        len(paths) for paths in result.get("new_files", {}).values()
    )
    result["total_files"] = sum(len(paths) for paths in result["files"].values())
    result["total_words"] = result.get("total_words", 0) + _word_count(configs)
    result["project_config_files"] = config_strings
    return result
