# NAS Skills 热修补部署

这个目录用于把 `CordysCRM-skills` 兼容热修补以可重复方式部署到 NAS 上现有的 1Panel `CordysCRM` 安装。

当前脚本针对已经验证过的部署形态：

- CRM 服务容器名: `cordys-crm`
- 1Panel compose 目录: `/vol1/@appdata/1Panel/1panel/apps/cordys-crm/cordys-crm`
- 热修补 jar 挂载目标: `/app/lib/crm-main.jar`
- 宿主机热修补文件: `/vol1/@appdata/1Panel/1panel/apps/cordys-crm/cordys-crm/data/hotfix/crm-main.jar`

## 方案说明

不重打官方 Docker 镜像，也不直接改容器内文件。

正式方案固定为：

1. 本地构建兼容版 [crm-main.jar](C:/Users/Bing/CordysCRM/backend/crm/target/crm-main.jar)
2. 上传到 NAS 的 `data/hotfix/crm-main.jar`
3. 确保 `docker-compose.yml` 中存在挂载
   - `./data/hotfix/crm-main.jar:/app/lib/crm-main.jar`
4. 在 compose 目录执行 `docker compose up -d`
5. 校验容器内 jar 已包含 `SkillCompatibilityController`
6. 如提供 API key，再校验 `/settings/fields?module=account`

这样做的好处是：

- 1Panel 重建容器后仍然保留兼容修补
- 不依赖一次性的 `docker cp`
- 热修补边界清晰，只覆盖 `crm-main.jar`

## 依赖

- Python 3
- `paramiko`

安装：

```bash
pip install paramiko
```

## 使用方式

推荐先设置环境变量，再执行脚本：

```powershell
$env:CORDYS_NAS_HOST='192.168.8.88'
$env:CORDYS_NAS_PORT='22'
$env:CORDYS_NAS_USER='bing'
$env:CORDYS_NAS_PASSWORD='你的 NAS 密码'
$env:CORDYS_ACCESS_KEY='你的 Access Key'
$env:CORDYS_SECRET_KEY='你的 Secret Key'

python .\scripts\nas\deploy_skill_hotfix.py
```

如果只想部署，不做鉴权接口验证：

```powershell
python .\scripts\nas\deploy_skill_hotfix.py --skip-http-verify
```

如果本地 jar 已经构建好，跳过构建阶段：

```powershell
python .\scripts\nas\deploy_skill_hotfix.py --skip-build
```

如果要跳过测试再构建：

```powershell
python .\scripts\nas\deploy_skill_hotfix.py --skip-tests
```

## 默认参数

脚本默认值已经对齐当前 NAS 环境：

- `--host 192.168.8.88`
- `--port 22`
- `--user bing`
- `--service-name cordys-crm`
- `--container-name cordys-crm`
- `--compose-dir /vol1/@appdata/1Panel/1panel/apps/cordys-crm/cordys-crm`
- `--compose-file /vol1/@appdata/1Panel/1panel/apps/cordys-crm/cordys-crm/docker-compose.yml`
- `--remote-hotfix-jar /vol1/@appdata/1Panel/1panel/apps/cordys-crm/cordys-crm/data/hotfix/crm-main.jar`

这些参数都可以按需覆盖。

## 脚本会做什么

1. 本地执行 Maven 构建 `backend/crm`
2. 通过 SSH/SFTP 上传 `crm-main.jar`
3. 自动备份 `docker-compose.yml`
4. 在 compose 中幂等写入热修补挂载
5. 执行 `docker compose up -d`
6. 校验容器状态
7. 校验容器内 jar 含有 `cn/cordys/crm/system/controller/SkillCompatibilityController.class`
8. 可选校验 `GET /settings/fields?module=account`

## 回滚

如果需要回滚：

1. 将 compose 中的挂载
   - `./data/hotfix/crm-main.jar:/app/lib/crm-main.jar`
   删除
2. 恢复脚本生成的 compose 备份文件
3. 重新执行：

```bash
cd /vol1/@appdata/1Panel/1panel/apps/cordys-crm/cordys-crm
docker compose up -d
```

## 已验证的兼容接口

- `GET /settings/fields?module=account`
- `GET /contact/module/form`
- `POST /contact/page`
- `GET /contact/{id}`
- `GET /contact/get/{id}`
- `GET /contact/view/list`

## 已验证的联调结果

在当前 NAS 环境已验证：

- `CordysCRM-skills` 可用 `X-Access-Key` / `X-Secret-Key` 调 CRM
- `crm page account` 可以查到客户 `STERIS`
- `contact/page` 可以查到联系人 `Crystal Barnes`
