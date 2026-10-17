"""Read the standalone Setup build version, independent of the repack version."""
import subprocess
from pathlib import Path


def setup_version(path: Path) -> str:
    literal = str(path.resolve()).replace("'", "''")
    result = subprocess.run(
        ['powershell.exe', '-NoProfile', '-NonInteractive', '-Command',
         "[Diagnostics.FileVersionInfo]::GetVersionInfo('" + literal + "').FileVersion"],
        check=True, capture_output=True, text=True,
    )
    value = result.stdout.strip()
    if not value or not all(part.isdigit() for part in value.split('.')):
        raise ValueError('Setup executable has no numeric build version: ' + str(path))
    return value
