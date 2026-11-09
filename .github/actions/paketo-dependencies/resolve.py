"""Prints the dependencies the image builds download, read from the buildpack.toml files given, one per line as
<sha256> <uri>, for the CPU architecture the build runs on."""
import argparse
import sys
import tomllib
from urllib.parse import parse_qs, urlparse

# The dependency ids the buildpacks of a Kinotic image download, and for the JRE the Java major version it runs
WANTED_IDS = {"jre", "syft", "thc", "spring-cloud-bindings"}


def arch_of(dependency):
    # the purl carries the architecture as ?arch=<arch>; a dependency without one runs on every architecture
    values = parse_qs(urlparse(dependency["purl"]).query).get("arch")
    return values[0] if values else None


def version_key(version):
    return tuple(int(part) if part.isdigit() else 0 for part in version.split("."))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--arch", required=True, help="amd64 or arm64")
    parser.add_argument("--java-version", required=True, help="the Java major version the images run")
    parser.add_argument("buildpacks", nargs="+", help="buildpack.toml files")
    args = parser.parse_args()

    # the buildpacks pick the newest version on offer, and for the JRE the newest of the Java major version
    chosen = {}
    for path in args.buildpacks:
        with open(path, "rb") as file:
            dependencies = tomllib.load(file).get("metadata", {}).get("dependencies", [])
        for dependency in dependencies:
            id = dependency["id"]
            if id not in WANTED_IDS or arch_of(dependency) not in (None, args.arch):
                continue
            if id == "jre" and dependency["version"].split(".")[0] != args.java_version:
                continue
            current = chosen.get(id)
            if current is None or version_key(dependency["version"]) > version_key(current["version"]):
                chosen[id] = dependency

    # without a JRE mapping the builder downloads it from GitHub again, the failure this action exists to prevent
    if "jre" not in chosen:
        sys.exit(f"No JRE {args.java_version} for {args.arch} in {', '.join(args.buildpacks)}")

    for id in sorted(chosen):
        print(chosen[id]["sha256"], chosen[id]["uri"])


if __name__ == "__main__":
    main()
