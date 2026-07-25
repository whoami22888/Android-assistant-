from __future__ import annotations

import ast
import os
import resource
import subprocess
import tempfile
from pathlib import Path


SAFE_IMPORTS = {"math", "statistics", "json", "re", "datetime", "collections", "itertools", "numpy", "pandas", "plotly"}
FORBIDDEN_NAMES = {"open", "exec", "eval", "compile", "__import__", "input", "globals", "locals", "vars", "getattr", "setattr", "delattr", "breakpoint"}


class RestrictedPython(ast.NodeVisitor):
    def visit_Import(self, node: ast.Import) -> None:
        for alias in node.names:
            if alias.name.split(".")[0] not in SAFE_IMPORTS:
                raise ValueError(f"Import is not allowed: {alias.name}")

    def visit_ImportFrom(self, node: ast.ImportFrom) -> None:
        if not node.module or node.module.split(".")[0] not in SAFE_IMPORTS:
            raise ValueError(f"Import is not allowed: {node.module}")

    def visit_Name(self, node: ast.Name) -> None:
        if node.id in FORBIDDEN_NAMES or node.id.startswith("__"):
            raise ValueError(f"Name is not allowed: {node.id}")

    def visit_Attribute(self, node: ast.Attribute) -> None:
        if node.attr.startswith("__"):
            raise ValueError("Dunder attribute access is not allowed")
        self.generic_visit(node)


class PythonSandbox:
    """A limited analysis runner, not a general-purpose shell. Compose hardening is a second boundary."""

    def __init__(self, workspace: str) -> None:
        self.workspace = Path(workspace)
        self.workspace.mkdir(parents=True, exist_ok=True)

    def run(self, code: str) -> dict[str, str | int]:
        if len(code) > 8000:
            raise ValueError("Analysis script exceeds 8,000 characters")
        tree = ast.parse(code, mode="exec")
        RestrictedPython().visit(tree)
        with tempfile.TemporaryDirectory(dir=self.workspace) as directory:
            script = Path(directory) / "analysis.py"
            script.write_text(code, encoding="utf-8")
            result = subprocess.run(
                ["python", "-I", str(script)],
                cwd=directory,
                env={"PATH": "/usr/local/bin:/usr/bin:/bin", "LANG": "C.UTF-8", "PYTHONNOUSERSITE": "1"},
                text=True,
                capture_output=True,
                timeout=12,
                preexec_fn=self._limits,
                check=False,
            )
            return {"exit_code": result.returncode, "stdout": result.stdout[-12000:], "stderr": result.stderr[-4000:]}

    @staticmethod
    def _limits() -> None:
        resource.setrlimit(resource.RLIMIT_CPU, (8, 8))
        resource.setrlimit(resource.RLIMIT_AS, (512 * 1024 * 1024, 512 * 1024 * 1024))
        resource.setrlimit(resource.RLIMIT_FSIZE, (8 * 1024 * 1024, 8 * 1024 * 1024))
