from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import project


class ProjectDetectionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        self._write("application.properties", "quarkus.oidc.enabled=true\n")
        self._write("mise.toml", "[tools]\njava = '21'\n")
        self._write("Dockerfile.jvm", "FROM eclipse-temurin:21\n")
        self._write(".gitignore", "target/\n")
        self._write(".env", "SECRET=do-not-index\n")
        self._write("graphify-out/generated.toml", "generated=true\n")
        subprocess.run(
            ["git", "-C", str(self.root), "add", "-f", "."],
            check=True,
        )

    def tearDown(self) -> None:
        self.tempdir.cleanup()

    def _write(self, relative: str, content: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_full_detection_adds_only_safe_tracked_configuration(self) -> None:
        native = {
            "files": {"code": [], "document": []},
            "total_files": 0,
            "total_words": 0,
        }
        with patch.object(project, "detect", return_value=native):
            result = project.detect_project(self.root)

        documents = {Path(path).name for path in result["files"]["document"]}
        self.assertEqual(
            {"application.properties", "mise.toml", "Dockerfile.jvm", ".gitignore"},
            documents,
        )
        self.assertEqual(4, result["total_files"])
        self.assertNotIn(".env", "\n".join(result["files"]["document"]))
        self.assertNotIn("graphify-out", "\n".join(result["files"]["document"]))

    def test_incremental_detection_uses_semantic_hash_for_configs(self) -> None:
        properties = (self.root / "application.properties").resolve()
        native = {
            "files": {"code": [], "document": []},
            "new_files": {
                "code": [],
                "document": [str(self.root / "graphify-out" / "memory.md")],
            },
            "deleted_files": [str(properties)],
            "new_total": 0,
            "total_files": 0,
            "total_words": 0,
        }
        manifest = {
            str(properties): {
                "mtime": properties.stat().st_mtime,
                "ast_hash": "",
                "semantic_hash": "outdated",
            }
        }
        with (
            patch.object(project, "detect_incremental", return_value=native),
            patch.object(project, "load_manifest", return_value=manifest),
        ):
            result = project.detect_incremental_project(self.root)

        self.assertIn(str(properties), result["new_files"]["document"])
        self.assertNotIn("graphify-out", "\n".join(result["new_files"]["document"]))
        self.assertNotIn(str(properties), result["deleted_files"])


if __name__ == "__main__":
    unittest.main()
