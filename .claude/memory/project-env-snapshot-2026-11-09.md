---
name: project-env-snapshot-2026-11-09
description: 本机路径与运行态快照：mvn/python/node 路径、桌面 bat、deploy/ 空骨架、branch test-AI
metadata:
  type: project
---

2026-11-09 复核过的本机事实（`CLAUDE.md` 里没写的部分）：

**工具链绝对路径**

| 工具 | 路径 |
|---|---|
| Maven 3.9.16 | `C:/Program Files/apache-maven-3.9.16-bin/bin/mvn` |
| Maven 3.9.6 | `C:/work/apache-maven-3.9.6/bin/mvn.cmd` |
| Python | `C:/Users/59111/.workbuddy/binaries/python/envs/default/Scripts/python.exe` |
| Node | `C:/Users/59111/.workbuddy/binaries/node/versions/22.22.2-3/`（目录名会变，用前先 ls） |
| JDK16 | `C:\Program Files\Java\jdk-16.0.2` |

**桌面启动脚本**（用户自己点，见 [[feedback-user-starts-services]]）
`C:\Users\59111\Desktop\00-start-all.bat`、`01-eureka-server.bat`、`02-provider-service.bat`、`03-consumer-service.bat`、`04-myvue.bat`、`05-vue-portal.bat`、`06-DaTaVue.bat`、`07-cockpit.bat`

**consumer 端口实际状态（2026-11-09）**
- `03-consumer-service.bat` 用 `--server.port=8006`，`00-start-all.bat` 标题也写 8006
- `MyVue/vite.config.js` 与 `vite.cockpit.config.js` 代理仍指 **8002**
- `MyPortalVue/vue-portal`、`MyDataVue` 代理指 **8006**
- 即桌面脚本跑起来时 3001/3004 的 `/api/consumer` 会连不上 —— 这是既有错配，不是新引入的

**其它**
- 仓库 `deploy/` 是**空骨架**（`config/ data/{logs,report} dist/{myvue,mydata,portal}/assets nginx/ scripts/ jars/`），无 Dockerfile / docker-compose
- 分支 `test-AI`（main 为主分支），remote `git@github.com:jidachuan123/MyTeamWork.git`
- `CLAUDE.md` 与 `provider-service/config/application.yml` 都是 **git 已跟踪**（config 里有明文密码，见 [[reference-known-issues-doc]] A5）

相关：[[project-rds27-datasource]]
