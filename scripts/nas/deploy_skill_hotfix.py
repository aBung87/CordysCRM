#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import posixpath
import re
import shlex
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

try:
    import paramiko
except ImportError as exc:  # pragma: no cover
    raise SystemExit(
        "缺少依赖 paramiko，请先执行: pip install paramiko"
    ) from exc


REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LOCAL_JAR = REPO_ROOT / "backend" / "crm" / "target" / "crm-main.jar"
DEFAULT_COMPOSE_DIR = "/vol1/@appdata/1Panel/1panel/apps/cordys-crm/cordys-crm"
DEFAULT_COMPOSE_FILE = f"{DEFAULT_COMPOSE_DIR}/docker-compose.yml"
DEFAULT_HOTFIX_JAR = f"{DEFAULT_COMPOSE_DIR}/data/hotfix/crm-main.jar"
DEFAULT_SERVICE_NAME = "cordys-crm"
DEFAULT_VERIFY_PATH = "/get-key"
HOTFIX_VOLUME = "./data/hotfix/crm-main.jar:/app/lib/crm-main.jar"
SKILL_CLASS = "cn/cordys/crm/system/controller/SkillCompatibilityController.class"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="构建并部署 CordysCRM skills 兼容热修补到 NAS 的 1Panel Docker 安装。"
    )
    parser.add_argument("--host", default=os.environ.get("CORDYS_NAS_HOST", "192.168.8.88"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("CORDYS_NAS_PORT", "22")))
    parser.add_argument("--user", default=os.environ.get("CORDYS_NAS_USER", "bing"))
    parser.add_argument("--password", default=os.environ.get("CORDYS_NAS_PASSWORD"))
    parser.add_argument("--service-name", default=os.environ.get("CORDYS_NAS_SERVICE", DEFAULT_SERVICE_NAME))
    parser.add_argument("--container-name", default=os.environ.get("CORDYS_NAS_CONTAINER", DEFAULT_SERVICE_NAME))
    parser.add_argument("--compose-dir", default=os.environ.get("CORDYS_NAS_COMPOSE_DIR", DEFAULT_COMPOSE_DIR))
    parser.add_argument("--compose-file", default=os.environ.get("CORDYS_NAS_COMPOSE_FILE", DEFAULT_COMPOSE_FILE))
    parser.add_argument("--remote-hotfix-jar", default=os.environ.get("CORDYS_NAS_HOTFIX_JAR", DEFAULT_HOTFIX_JAR))
    parser.add_argument("--local-jar", default=str(DEFAULT_LOCAL_JAR))
    parser.add_argument("--verify-url", default=os.environ.get("CORDYS_VERIFY_URL"))
    parser.add_argument("--access-key", default=os.environ.get("CORDYS_ACCESS_KEY"))
    parser.add_argument("--secret-key", default=os.environ.get("CORDYS_SECRET_KEY"))
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--skip-tests", action="store_true")
    parser.add_argument("--skip-http-verify", action="store_true")
    parser.add_argument("--ssh-timeout", type=int, default=20)
    return parser.parse_args()


def log(step: str, message: str) -> None:
    print(f"[{step}] {message}")


def require_password(password: str | None) -> str:
    if password:
        return password
    raise SystemExit("缺少 NAS 密码，请通过 --password 或环境变量 CORDYS_NAS_PASSWORD 提供。")


def build_hotfix_jar(skip_tests: bool) -> None:
    mvnw = REPO_ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    command = [str(mvnw), "-pl", "backend/crm", "-am", "package"]
    if skip_tests:
        command.append("-DskipTests")
    log("BUILD", "开始构建 backend/crm 模块")
    subprocess.run(command, cwd=REPO_ROOT, check=True)


def ensure_local_jar(path: Path) -> Path:
    if not path.exists():
        raise SystemExit(f"未找到本地热修补产物: {path}")
    return path


class RemoteSession:
    def __init__(self, host: str, port: int, user: str, password: str, timeout: int) -> None:
        self.password = password
        self.client = paramiko.SSHClient()
        self.client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self.client.connect(host, port=port, username=user, password=password, timeout=timeout)
        self.sftp = self.client.open_sftp()

    def close(self) -> None:
        try:
            self.sftp.close()
        finally:
            self.client.close()

    def run(self, command: str, sudo: bool = False, check: bool = True) -> tuple[int, str, str]:
        wrapped = command
        if sudo:
            wrapped = f"sudo -S -p '' sh -lc {shlex.quote(command)}"
        stdin, stdout, stderr = self.client.exec_command(wrapped, get_pty=True)
        if sudo:
            stdin.write(self.password + "\n")
            stdin.flush()
        out = stdout.read().decode("utf-8", "ignore")
        err = stderr.read().decode("utf-8", "ignore")
        code = stdout.channel.recv_exit_status()
        if check and code != 0:
            raise RuntimeError(f"远程命令失败({code}): {command}\nSTDOUT:\n{out}\nSTDERR:\n{err}")
        return code, out, err

    def put_file(self, local_path: Path, remote_path: str) -> None:
        self.sftp.put(str(local_path), remote_path)


def patch_compose_text(compose_text: str, service_name: str) -> tuple[str, bool]:
    if HOTFIX_VOLUME in compose_text:
        return compose_text, False

    lines = compose_text.splitlines()
    service_line = f"    {service_name}:"
    try:
        service_start = lines.index(service_line)
    except ValueError as exc:
        raise SystemExit(f"在 compose 文件中找不到服务 {service_name}") from exc

    service_end = len(lines)
    for idx in range(service_start + 1, len(lines)):
        if re.match(r"^    [^ ].*:\s*$", lines[idx]):
            service_end = idx
            break

    volumes_idx = None
    for idx in range(service_start + 1, service_end):
        if lines[idx] == "        volumes:":
            volumes_idx = idx
            break

    if volumes_idx is not None:
        insert_at = volumes_idx + 1
        while insert_at < service_end and (
            lines[insert_at].startswith("            - ")
            or lines[insert_at].strip() == ""
        ):
            insert_at += 1
        lines.insert(insert_at, f"            - {HOTFIX_VOLUME}")
    else:
        insert_at = service_end
        for idx in range(service_start + 1, service_end):
            if re.match(r"^        (ports|restart|networks|labels):", lines[idx]):
                insert_at = idx
                break
        lines.insert(insert_at, "        volumes:")
        lines.insert(insert_at + 1, f"            - {HOTFIX_VOLUME}")

    return "\n".join(lines) + "\n", True


def remote_cat(session: RemoteSession, remote_path: str) -> str:
    _, out, _ = session.run(f"cat {shlex.quote(remote_path)}", sudo=True)
    return out


def remote_install_file(session: RemoteSession, local_path: Path, remote_path: str) -> None:
    temp_remote = posixpath.join("/tmp", f"cordys-hotfix-{int(time.time())}-{local_path.name}")
    session.put_file(local_path, temp_remote)
    remote_dir = posixpath.dirname(remote_path)
    session.run(f"mkdir -p {shlex.quote(remote_dir)}", sudo=True)
    session.run(f"install -m 0644 {shlex.quote(temp_remote)} {shlex.quote(remote_path)}", sudo=True)
    session.run(f"rm -f {shlex.quote(temp_remote)}", sudo=True)


def remote_backup_file(session: RemoteSession, remote_path: str) -> str:
    timestamp = time.strftime("%Y%m%d%H%M%S")
    backup_path = f"{remote_path}.bak.{timestamp}"
    session.run(f"cp {shlex.quote(remote_path)} {shlex.quote(backup_path)}", sudo=True)
    return backup_path


def verify_remote_jar(session: RemoteSession, container_name: str) -> None:
    command = (
        f"docker exec {shlex.quote(container_name)} sh -lc "
        "\"if command -v jar >/dev/null 2>&1; "
        f"then jar tf /app/lib/crm-main.jar; else unzip -l /app/lib/crm-main.jar; fi | grep -F {shlex.quote(SKILL_CLASS)}\""
    )
    _, out, _ = session.run(command, sudo=True)
    if SKILL_CLASS not in out:
        raise SystemExit("容器内未发现 SkillCompatibilityController，热修补 jar 可能未生效。")


def verify_http(base_url: str, access_key: str | None, secret_key: str | None) -> None:
    get_key_url = base_url.rstrip("/") + DEFAULT_VERIFY_PATH
    try:
        with urlopen(Request(get_key_url, method="GET"), timeout=15) as response:
            response.read()
    except (HTTPError, URLError) as exc:
        raise SystemExit(f"HTTP 健康检查失败: {get_key_url} -> {exc}") from exc

    if access_key and secret_key:
        request = Request(
            base_url.rstrip("/") + "/settings/fields?module=account",
            method="GET",
            headers={
                "X-Access-Key": access_key,
                "X-Secret-Key": secret_key,
            },
        )
        try:
            with urlopen(request, timeout=15) as response:
                body = response.read().decode("utf-8", "ignore")
        except (HTTPError, URLError) as exc:
            raise SystemExit(f"skills 兼容接口验证失败: {exc}") from exc
        if "customerName" not in body:
            raise SystemExit("skills 兼容接口返回异常，未检测到 account 字段。")
    else:
        log("VERIFY", "未提供 access/secret key，跳过鉴权接口验证。")


def main() -> int:
    args = parse_args()
    password = require_password(args.password)
    local_jar = Path(args.local_jar).resolve()

    if not args.skip_build:
        build_hotfix_jar(skip_tests=args.skip_tests)

    ensure_local_jar(local_jar)
    log("LOCAL", f"使用热修补产物: {local_jar}")

    verify_url = args.verify_url or f"http://{args.host}:8081"
    session = RemoteSession(args.host, args.port, args.user, password, args.ssh_timeout)
    try:
        log("UPLOAD", f"上传热修补 jar 到 {args.remote_hotfix_jar}")
        remote_install_file(session, local_jar, args.remote_hotfix_jar)

        compose_text = remote_cat(session, args.compose_file)
        patched_text, changed = patch_compose_text(compose_text, args.service_name)
        if changed:
            backup_path = remote_backup_file(session, args.compose_file)
            log("COMPOSE", f"已备份 compose: {backup_path}")
            with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False, suffix=".yml") as tmp:
                tmp.write(patched_text)
                temp_path = Path(tmp.name)
            try:
                remote_install_file(session, temp_path, args.compose_file)
            finally:
                temp_path.unlink(missing_ok=True)
            log("COMPOSE", f"已写入热修补挂载: {HOTFIX_VOLUME}")
        else:
            log("COMPOSE", "compose 中已存在热修补挂载，无需再次修改。")

        log("DEPLOY", "执行 docker compose up -d")
        session.run(
            f"cd {shlex.quote(args.compose_dir)} && docker compose --ansi never up -d",
            sudo=True,
        )

        log("VERIFY", "检查容器状态")
        session.run(
            "docker ps --format '{{.Names}}\t{{.Status}}' | grep -F "
            + shlex.quote(args.container_name),
            sudo=True,
        )
        verify_remote_jar(session, args.container_name)

        if not args.skip_http_verify:
            verify_http(verify_url, args.access_key, args.secret_key)
            log("VERIFY", f"HTTP 验证通过: {verify_url}")

        log("DONE", "NAS 热修补部署完成。")
        return 0
    finally:
        session.close()


if __name__ == "__main__":
    raise SystemExit(main())
