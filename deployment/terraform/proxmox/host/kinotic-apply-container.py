#!/usr/bin/env python3
"""Brings containers created from OCI images up to their manifests, in the order given, and
starts them. Terraform uploads a manifest per container beside this script and runs it over
SSH after every apply. It converges on the host's actual state, so running it by hand is safe.

For each manifest:

  entrypoint   the command with its arguments, set with pct: the API keeps only the
               command when it creates the container
  env          the entrypoint's environment, as raw `lxc.environment:` lines in
               /etc/pve/lxc/<vmid>.conf: the API validates each key as a word, which
               Elasticsearch's dotted settings are not. A secrets env file the operator
               placed on the host is merged in, so no secret passes through terraform.
  console_log  where LXC writes the entrypoint's stdout and stderr on the host
  dns          the resolvers, written to a host file that is bind-mounted over the
               container's /etc/resolv.conf: Proxmox writes none into an OCI image
  files        config files terraform uploaded, copied into the host directories the
               container bind-mounts, so a container recreated from a newer image finds them
  dirs         the bind-mounted directories, owned by the container's user

A container already in that state and running is left alone; otherwise it is stopped, brought
up to date, and started once `wait_for` succeeds. A started container is marked for
kinotic-keepalive.timer, which restarts one whose entrypoint exits. A `run_once` container
(the migration) runs to completion and `verify` once per configuration, and is not kept alive.

With --prepare, only the files and directories are placed: Proxmox mounts the bind mounts
and unpacks the image over them inside the container's user namespace, which cannot take
ownership of a host directory real root owns, so they are owned before the container exists.

  kinotic-apply-container.py [--prepare] <manifest.json>...
"""
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time

# Unprivileged containers map uid 0 to this host uid
IDMAP_BASE = 100000
STATE_DIR = "/var/lib/kinotic/state"
KEEPALIVE_DIR = os.path.join(STATE_DIR, "keepalive")


def pct(*args, capture=False):
    return subprocess.run(("pct", *args), check=True, text=True,
                          stdout=subprocess.PIPE if capture else None)


def status(vmid):
    return pct("status", str(vmid), capture=True).stdout.split()[-1]


def read_env_file(path):
    env = {}
    if path and os.path.exists(path):
        with open(path) as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, value = line.split("=", 1)
                    env[key.strip()] = value
    return env


# ── the container's config ───────────────────────────────────────────────────

def conf_path(vmid):
    return f"/etc/pve/lxc/{vmid}.conf"


def resolv_conf_path(vmid):
    return os.path.join(STATE_DIR, f"{vmid}.resolv.conf")


def raw_lines(manifest, env):
    """The lines this script owns in the config: the environment, the console log, and the
    resolv.conf mount."""
    lines = [f"lxc.environment: {key}={value}" for key, value in env.items()]
    # Proxmox accepts lxc.console.logfile and no other lxc.console key; logrotate rotates it
    if manifest.get("console_log"):
        lines.append(f"lxc.console.logfile: {manifest['console_log']}")
    if manifest.get("dns"):
        lines.append(f"lxc.mount.entry: {resolv_conf_path(manifest['vmid'])} etc/resolv.conf none bind,ro,create=file 0 0")
    return sorted(lines)


def owned(line):
    """Every lxc.environment line is this script's: Proxmox writes the image's own variables as
    lxc.environment.runtime, so a variable dropped from the manifest is removed too."""
    key, _, value = line.partition(":")
    key, value = key.strip(), value.strip()
    return (key == "lxc.environment" or key.startswith("lxc.console.")
            or (key == "lxc.mount.entry" and " etc/resolv.conf " in value))


def config_in_sync(manifest, env):
    with open(conf_path(manifest["vmid"])) as f:
        lines = f.read().splitlines()
    return sorted(l for l in lines if owned(l)) == raw_lines(manifest, env)


def write_config(manifest, env):
    """Replaces the owned lines and leaves every other line alone."""
    path = conf_path(manifest["vmid"])
    with open(path) as f:
        lines = f.read().splitlines()
    kept = [l for l in lines if not owned(l)]
    with open(path, "w") as f:
        f.write("\n".join(kept + raw_lines(manifest, env)) + "\n")


def entrypoint_in_sync(manifest):
    with open(conf_path(manifest["vmid"])) as f:
        current = [l.partition(":")[2].strip() for l in f if l.startswith("entrypoint:")]
    return not manifest.get("entrypoint") or current == [manifest["entrypoint"]]


def set_entrypoint(manifest):
    if manifest.get("entrypoint"):
        pct("set", str(manifest["vmid"]), "--entrypoint", manifest["entrypoint"])


def resolv_conf(manifest):
    return "".join(f"nameserver {server}\n" for server in manifest.get("dns", []))


def resolv_conf_in_sync(manifest):
    path = resolv_conf_path(manifest["vmid"])
    return not manifest.get("dns") or (os.path.isfile(path) and open(path).read() == resolv_conf(manifest))


def write_resolv_conf(manifest):
    if manifest.get("dns"):
        with open(resolv_conf_path(manifest["vmid"]), "w") as f:
            f.write(resolv_conf(manifest))


# ── files and directories on the host ────────────────────────────────────────

def owner(spec):
    return IDMAP_BASE + spec.get("uid", 0), IDMAP_BASE + spec.get("gid", 0)


def file_in_sync(spec):
    dst = spec["dst"]
    if not os.path.isfile(dst):
        return False
    with open(spec["src"], "rb") as src, open(dst, "rb") as cur:
        if src.read() != cur.read():
            return False
    st = os.stat(dst)
    return (st.st_uid, st.st_gid) == owner(spec) and st.st_mode & 0o777 == int(spec.get("mode", "0644"), 8)


def dir_in_sync(spec):
    if not os.path.isdir(spec["path"]):
        return False
    st = os.stat(spec["path"])
    return (st.st_uid, st.st_gid) == owner(spec)


def place(files, dirs):
    for spec in files:
        os.makedirs(os.path.dirname(spec["dst"]), exist_ok=True)
        shutil.copyfile(spec["src"], spec["dst"])
        os.chmod(spec["dst"], int(spec.get("mode", "0644"), 8))
        os.chown(spec["dst"], *owner(spec))
    for spec in dirs:
        os.makedirs(spec["path"], exist_ok=True)
        os.chown(spec["path"], *owner(spec))


# ── waiting ──────────────────────────────────────────────────────────────────

def wait_for(command, timeout):
    deadline = time.time() + timeout
    while subprocess.run(command, shell=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0:
        if time.time() > deadline:
            sys.exit(f"gave up after {timeout}s waiting for: {command}")
        time.sleep(5)


def wait_until_stopped(vmid, timeout):
    deadline = time.time() + timeout
    while status(vmid) != "stopped":
        if time.time() > deadline:
            return False
        time.sleep(5)
    return True


# ── one manifest ─────────────────────────────────────────────────────────────

def fingerprint(manifest, env):
    digest = hashlib.sha256(json.dumps([manifest, env], sort_keys=True).encode())
    for spec in manifest.get("files", []):
        with open(spec["src"], "rb") as f:
            digest.update(f.read())
    return digest.hexdigest()


def apply(manifest):
    vmid, name = manifest["vmid"], manifest["name"]
    timeout = manifest.get("timeout", 900)
    console_log = manifest.get("console_log")
    files, dirs = manifest.get("files", []), manifest.get("dirs", [])
    env = dict(manifest.get("env", {}))
    env.update(read_env_file(manifest.get("secrets_env")))

    in_sync = (config_in_sync(manifest, env)
               and entrypoint_in_sync(manifest)
               and resolv_conf_in_sync(manifest)
               and all(file_in_sync(spec) for spec in files)
               and all(dir_in_sync(spec) for spec in dirs))
    keepalive = os.path.join(KEEPALIVE_DIR, str(vmid))
    ran = os.path.join(STATE_DIR, f"{vmid}.ran")
    if manifest.get("run_once"):
        done = os.path.exists(ran) and open(ran).read().strip() == fingerprint(manifest, env)
        if in_sync and done:
            print(f"{name} ({vmid}): unchanged")
            return
    elif in_sync and status(vmid) == "running" and os.path.exists(keepalive):
        print(f"{name} ({vmid}): unchanged")
        return

    # The marker goes before the stop, so the keepalive timer does not start the container
    # back up mid-change
    if os.path.exists(keepalive):
        os.remove(keepalive)
    if status(vmid) == "running":
        pct("stop", str(vmid))
    write_config(manifest, env)
    set_entrypoint(manifest)
    write_resolv_conf(manifest)
    place(files, dirs)
    if console_log:
        os.makedirs(os.path.dirname(console_log), exist_ok=True)
    if manifest.get("wait_for"):
        wait_for(manifest["wait_for"], timeout)
    pct("start", str(vmid))

    if manifest.get("run_once"):
        print(f"{name} ({vmid}): running to completion")
        if not wait_until_stopped(vmid, timeout):
            sys.exit(f"{name} ({vmid}) did not finish within {timeout}s; its output is in {console_log}")
        verify = manifest.get("verify")
        if verify and subprocess.run(verify, shell=True).returncode != 0:
            sys.exit(f"{name} ({vmid}) stopped but the verification failed ({verify}); its output is in {console_log}")
        with open(ran, "w") as f:
            f.write(fingerprint(manifest, env))
    else:
        open(keepalive, "w").close()
    print(f"{name} ({vmid}): applied")


def prepare(manifest):
    place(manifest.get("files", []), manifest.get("dirs", []))
    print(f"{manifest['name']} ({manifest['vmid']}): prepared")


if __name__ == "__main__":
    args = sys.argv[1:]
    prepare_only = args[:1] == ["--prepare"]
    paths = args[1:] if prepare_only else args
    if not paths:
        sys.exit(__doc__)
    os.makedirs(KEEPALIVE_DIR, exist_ok=True)
    for path in paths:
        with open(path) as f:
            manifest = json.load(f)
        if prepare_only:
            prepare(manifest)
        else:
            apply(manifest)
