#!/usr/bin/env python3
"""Verify shipped ART profiles and R8's actual startup DEX layout, not just source files."""
import hashlib
import json
from pathlib import Path
import zipfile


def main():
    with zipfile.ZipFile("app/build/outputs/apk/performance/app-performance.apk") as apk:
        profile = apk.read("assets/dexopt/baseline.prof")
        metadata = apk.read("assets/dexopt/baseline.profm")
        assert profile.startswith(b"pro\x00") and 0 < len(profile) < 1_500_000
        assert metadata.startswith(b"prm\x00")
    with zipfile.ZipFile("app/build/outputs/bundle/performance/app-performance.aab") as bundle:
        r8 = json.loads(bundle.read("BUNDLE-METADATA/com.android.tools/r8.json"))
        startup_dex = [dex for dex in r8["dexFiles"] if dex.get("startup")]
        assert startup_dex, "R8 did not consume the Startup Profile"
        checksums = {hashlib.sha256(bundle.read(name)).hexdigest()
                     for name in bundle.namelist() if name.startswith("base/dex/") and name.endswith(".dex")}
        assert all(dex["checksum"] in checksums for dex in startup_dex)
    report = {"baseline_profile_bytes": len(profile), "baseline_metadata_bytes": len(metadata),
              "startup_dex_files": len(startup_dex), "r8": r8}
    out = Path("app/build/startup-profiles")
    out.mkdir(parents=True, exist_ok=True)
    out.joinpath("packaging.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()

