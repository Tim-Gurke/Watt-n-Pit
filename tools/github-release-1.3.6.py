#!/usr/bin/env python3
"""Sign the already checked Watt'n'Pit APK. Private material stays in runner temp."""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import urllib.request
import zipfile

REPOSITORY = "Tim-Gurke/Watt-n-Pit"
SOURCE = "fbe145e09e3bdd0dc699b94a585363cd561b2cf3"
BUILD_SHA = "b39edf8b11649ea8444b83ec01c2eb5c834aa890"
RUN = 39459130059
ARTIFACTS = "artifacts/watt-1.3.6-fbe145e09e3b"
DIGEST = "133060f7e8f96cbebbcfbb196b1b0ffde8217727384e71274e6c4d0d9009efa1"
CERTIFICATE = "0e6e04bc2b50990a8f28bebae31dea596c2cd2f8563f1505aaaafc07277faeb2"
VERSION = "1.3.6"

def require(condition, message):
    if not condition:
        raise SystemExit(message)

def get(path, authenticated=False):
    if authenticated:
        url = "https://api.github.com/repos/" + REPOSITORY + path
        headers = {"Accept": "application/vnd.github+json",
                   "Authorization": "Bearer " + os.environ["GH_TOKEN"]}
    else:
        url = "https://raw.githubusercontent.com/" + REPOSITORY + "/" + ARTIFACTS + "/" + path
        headers = {}
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()

def sdk_tools():
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT", ""))
    candidates = sorted((sdk / "build-tools").glob("*/apksigner"))
    require(bool(candidates), "Android build-tools fehlen auf diesem Runner.")
    return candidates[-1].parent

def check_apk(tools, path):
    result = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(path)], text=True)
    require("name='de.wattpit.app'" in result and "versionName='1.3.6'" in result and "versionCode='26'" in result,
            "Paket oder Version passt nicht zur freigegebenen APK.")

def prepare(directory, tools):
    run = json.loads(get("/actions/runs/" + str(RUN), True))
    require(run["conclusion"] == "success" and run["head_sha"] == BUILD_SHA, "Der freigegebene Build ist nicht erfolgreich.")
    manifest = json.loads(get("build-output/manifest.json"))
    require(manifest["source"] == SOURCE and manifest["jvm_tests"] == 172, "Build-Quelle oder Prüfbericht passt nicht.")
    entry = manifest["files"]["unsigned.apk"]
    require(entry["sha256"] == DIGEST and entry["size"] == 170916, "APK-Manifest hat sich geändert.")
    parts = entry["parts"]
    require(parts == ["build-output/unsigned.apk/" + str(i).zfill(3) + ".txt" for i in range(4)], "Unerwartete Builddateien.")
    encoded = b"".join(get(part).strip() for part in parts)
    data = base64.b64decode(encoded, validate=True)
    require(hashlib.sha256(data).hexdigest() == DIGEST and len(data) == entry["size"], "APK-Prüfsumme stimmt nicht.")
    path = directory / "unsigned.apk"
    path.write_bytes(data)
    check_apk(tools, path)
    print("Freigegebener Build, 172 JVM-Tests, Paket, Version und APK-Prüfsumme bestätigt.")

def sign(directory, tools):
    unsigned = directory / "unsigned.apk"
    require(unsigned.is_file() and hashlib.sha256(unsigned.read_bytes()).hexdigest() == DIGEST, "Zuerst den Build prüfen.")
    encoded = "".join(os.environ.get("SIGNING_BACKUP_BASE64", "").split())
    require(bool(encoded), "Repository-Secret WATTPIT_SIGNING_BACKUP_BASE64 fehlt. Siehe docs/GITHUB-RELEASE.md.")
    require(len(encoded) < 2_000_000, "Die Signierungssicherung ist unerwartet groß.")
    try:
        data = base64.b64decode(encoded, validate=True)
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            require(len(archive.infolist()) <= 30 and sum(x.file_size for x in archive.infolist()) < 1_000_000, "Unerwarteter ZIP-Inhalt.")
            keys = [x for x in archive.namelist() if PurePosixPath(x).name.lower().endswith(".p12") and not x.startswith("__MACOSX/")]
            configs = [x for x in archive.namelist() if PurePosixPath(x).name == "signing.json" and not x.startswith("__MACOSX/")]
            require(len(keys) == 1 and len(configs) == 1, "ZIP muss genau eine P12-Datei und signing.json enthalten.")
            key_data = archive.read(keys[0])
            config = json.loads(archive.read(configs[0]))
    except (ValueError, zipfile.BadZipFile):
        raise SystemExit("Das Secret enthält keine gültige Base64-ZIP-Signierungssicherung.")
    password = config.get("password")
    require(isinstance(password, str) and bool(password) and "\n" not in password and "\r" not in password, "Signierungsdaten sind unvollständig.")
    require(config.get("alias", "wattpit") == "wattpit", "Die Sicherung hat einen anderen Schlüsselalias.")
    print("::add-mask::" + password)
    key = directory / "temporary-release-key.p12"
    signed = directory / ("Watt-n-Pit-v" + VERSION + ".apk")
    try:
        key.write_bytes(key_data)
        key.chmod(0o600)
        environment = dict(os.environ, WATTPIT_EPHEMERAL_PASSWORD=password)
        subprocess.run([str(tools / "apksigner"), "sign", "--ks", str(key), "--ks-type", "PKCS12",
                        "--ks-key-alias", "wattpit", "--ks-pass", "env:WATTPIT_EPHEMERAL_PASSWORD",
                        "--key-pass", "env:WATTPIT_EPHEMERAL_PASSWORD",
                        "--out", str(signed), str(unsigned)], env=environment, check=True)
        certificate = subprocess.check_output([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(signed)], text=True)
        require("certificate SHA-256 digest: " + CERTIFICATE in certificate, "Die Signatur gehört nicht zur bisherigen Watt'n'Pit-App.")
        check_apk(tools, signed)
        checksum = hashlib.sha256(signed.read_bytes()).hexdigest()
        (directory / "SHA256SUMS.txt").write_text(checksum + "  " + signed.name + "\n")
        print("Bisherige Updatesignatur, Paket und Version bestätigt. Signierte APK ist bereit.")
    finally:
        key.unlink(missing_ok=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--sign", action="store_true")
    args = parser.parse_args()
    require(os.environ.get("GITHUB_REPOSITORY") == REPOSITORY, "Dieser Ablauf gehört zu Tim-Gurke/Watt-n-Pit.")
    directory = Path(os.environ["RUNNER_TEMP"]) / "wattpit-release-1.3.6"
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    tools = sdk_tools()
    if args.sign:
        sign(directory, tools)
    else:
        prepare(directory, tools)
