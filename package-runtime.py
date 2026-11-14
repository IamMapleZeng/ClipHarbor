"""Add the pinned arm64 YouTube runtime to the unsigned APK."""
import hashlib
import io
import stat
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path, PurePosixPath

RUNTIME_URL = ("https://github.com/deniscerri/ytdlnis/releases/download/v1.9.1.1/"
               "YTDLnis-1.9.1.1-arm64-v8a-release-github.apk")
RUNTIME_SHA = "bed6c65b8a565f2301d488cf15a87ee87d6c4d3fe16a5eeaf0227dbfc1b1888e"
WHEELS = {
    ("yt-dlp", "2026.10.26"): "a05c5b5d8562f7e8a62f5334348b406af4930d56d3a65f8021d14af0e22339ae",
    ("yt-dlp-ejs", "0.8.0"): "79300e5fca7f937a1eeede11f0456862c1b41107ce1d726871e0207424f4bdb4",
}


def verified(path, digest):
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != digest:
        raise ValueError(f"SHA-256 mismatch for {path.name}")
    return data


def python_files(source, output):
    for entry in source.infolist():
        name = entry.filename
        if entry.is_dir() or not name.startswith("usr/"):
            continue
        path = PurePosixPath(name)
        if ".." in path.parts:
            raise ValueError(f"Unsafe runtime path: {name}")
        if "/site-packages/" in name or "/__pycache__/" in name:
            continue
        if not (name.startswith("usr/lib/python3.14/") or
                (len(path.parts) == 3 and ".so" in path.name) or
                name == "usr/etc/tls/cert.pem"):
            continue
        data = source.read(entry)
        if stat.S_ISLNK(entry.external_attr >> 16):
            target = PurePosixPath(data.decode())
            visited = {path.name}
            while True:
                if len(target.parts) != 1 or target.name == ".." or target.name in visited:
                    raise ValueError(f"Unexpected runtime symlink: {name}")
                visited.add(target.name)
                linked = source.getinfo(str(path.parent / target))
                data = source.read(linked)
                if not stat.S_ISLNK(linked.external_attr >> 16):
                    break
                target = PurePosixPath(data.decode())
        output.writestr(name, data)


def main():
    apk, dex, cache = map(Path, sys.argv[1:])
    cache.mkdir(parents=True, exist_ok=True)
    runtime_apk = cache / "android-runtime.apk"
    if not runtime_apk.exists():
        temporary = runtime_apk.with_suffix(".download")
        urllib.request.urlretrieve(RUNTIME_URL, temporary)
        verified(temporary, RUNTIME_SHA)
        temporary.replace(runtime_apk)
    runtime = verified(runtime_apk, RUNTIME_SHA)
    wheel_paths = {
        spec: cache / f"{spec[0].replace('-', '_')}-{spec[1]}-py3-none-any.whl"
        for spec in WHEELS
    }
    missing = [f"{name}=={version}" for (name, version), path in wheel_paths.items()
               if not path.exists()]
    if missing:
        subprocess.run([sys.executable, "-m", "pip", "download", "--no-deps",
                        "--dest", str(cache), *missing], check=True)
    with zipfile.ZipFile(io.BytesIO(runtime)) as upstream, zipfile.ZipFile(
        apk, "a", zipfile.ZIP_DEFLATED
    ) as target:
        target.write(dex, "classes.dex")
        for filename in ("libpython.so", "libqjs.so"):
            name = f"lib/arm64-v8a/{filename}"
            target.writestr(name, upstream.read(name))
        data = io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(upstream.read("lib/arm64-v8a/libpython.zip.so"))) as source:
            with zipfile.ZipFile(data, "w", zipfile.ZIP_DEFLATED) as output:
                python_files(source, output)
                for spec, path in wheel_paths.items():
                    output.writestr(f"wheels/{path.name}", verified(path, WHEELS[spec]))
        target.writestr("assets/youtube-runtime.zip", data.getvalue(), zipfile.ZIP_STORED)


if __name__ == "__main__":
    main()
