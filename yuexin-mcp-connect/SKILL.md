---
name: yuexin-mcp-connect
description: 在本地 Claude Code / Codex CLI 里通过【飞书 OAuth 个人登录】接入阅信 MCP Gateway，以开发者本人身份调用下游工具（datacenter / ops / pigeon 等）。【Python ≥ 3.11】【OAuth 模式：身份即登录者本人，tools/call 不传 _operator_open_id】。当开发者要在自己机器的 CLI 里用网关工具、而非在飞书 Bot/openclaw 多租户环境里调用时使用。与 mcp-gateway-agent（Bot 多租户模式，每次必传 _operator_open_id）互斥，按部署形态二选一。
---

# 本地 CLI 接入 MCP Gateway（OAuth 个人登录）

让开发者在**自己机器的 Claude Code / Codex CLI** 里，用**自己的飞书账号**登录一次，之后像用任何 MCP 一样调用网关下游工具。身份就是登录者本人，权限按本人在用户体系里的角色/权限码判定。

## 何时用本 skill / 何时用 mcp-gateway-agent

| 维度 | 本 skill（OAuth 个人登录） | mcp-gateway-agent（Bot 多租户） |
|---|---|---|
| 场景 | 开发者本地 Claude Code / Codex | 飞书 Bot / openclaw / 共享 server |
| 身份 | 浏览器登录的**本人** | 每次 `tools/call` 传 `_operator_open_id` 指定 |
| `_operator_open_id` | **不传** | **必传** |
| 凭证 | `~/.mcp-gateway/credentials.json`（本人，带 refresh_token） | server 持 `FEISHU_APP_ID/SECRET` 代多人换 union_id |
| 首次准备 | 终端跑一次 `login` 授权 | 配 App 凭证即可 |

> 两套是**互斥的认证形态**，不要混用。本 skill 全程不涉及 `_operator_open_id`。

## ⚠️ 一条铁律：先在终端 `login`，再交给 CLI 宿主

Claude Code / Codex 以子进程方式拉起 MCP server，这种 stdio 宿主下：
- server 子进程里 `webbrowser.open()` **不可靠**（无图形会话）；
- server 打到 stderr 的「授权链接」会被宿主**吞进日志**（如 `~/.claude/logs/`），终端里看不到。

所以**绝不要**让宿主第一次拉起 server 时去触发 OAuth 登录——你会卡在「浏览器不弹、链接也看不到」。

✅ 正确顺序：**先在普通终端手动跑一次 `login`** 完成授权、生成 `~/.mcp-gateway/credentials.json`；之后宿主拉起 server 时凭证已存在，直接复用 + 靠 refresh_token 自动续期，不再触发交互登录。

---

## 一、前置准备

本 skill **完全自包含**：`scripts/` 目录下已内置运行所需的全部脚本，解压/拷贝到任意路径即可用，不依赖同仓库的其它 skill。

```bash
# 改成你实际存放本 skill 的路径（例如解压 zip 后的目录）
export GW_DIR="$HOME/.claude/skills/yuexin-mcp-connect"
export GW_PY="$GW_DIR/.venv/bin/python"
export GW_SERVER="$GW_DIR/scripts/mcp_gateway_server.py"
export GW_CLIENT="$GW_DIR/scripts/mcp_gateway_client.py"

# 生产网关地址（OAuth 已在生产配置好；向平台索取确切地址）
export MCP_GATEWAY_BASE_URL="https://<生产网关地址>"
```

venv 不存在时创建（务必 Python ≥ 3.11）：

```bash
python3.11 -m venv "$GW_DIR/.venv"
"$GW_DIR/.venv/bin/pip" install -r "$GW_DIR/requirements-server.txt"
```

## 二、第一步：终端 OAuth 登录（一次性）

```bash
"$GW_PY" "$GW_CLIENT" login \
  --base-url "$MCP_GATEWAY_BASE_URL" \
  --task-key local-dev \
  --port 18765
```

- 终端会**打印飞书授权链接**并尝试打开浏览器；如未自动打开，手动复制链接到浏览器，用**自己的飞书账号**授权。
- 授权后浏览器回调 `http://127.0.0.1:18765/callback`，终端显示 `✓ 登录成功！` 并把凭证写到 `~/.mcp-gateway/credentials.json`（已 chmod 600，内含 refresh_token）。
- `--port 18765` 与 server 默认回调端口一致、且应与飞书后台登记的 `redirect_uri` 一致（见下方"端口与白名单"）。不传 `--port` 会用随机端口，飞书白名单可能匹配不上，**建议固定 18765**。

> 凭证默认路径 `~/.mcp-gateway/credentials.json` 与 server 默认读取路径**完全一致**——所以下面配置宿主时通常**无需**显式指定 `MCP_GATEWAY_CREDENTIALS_FILE`。

## 三、第二步：在宿主里挂载 server

OAuth 模式是 server 的**默认模式**（只要不把 `MCP_GATEWAY_CREDENTIALS_FILE` 设成空串）。两个宿主二选一：

### Claude Code

命令行添加（`--scope user` = 当前用户所有项目可用；`--` 后是启动命令）：

```bash
claude mcp add yuexin-gateway --scope user \
  --env MCP_GATEWAY_BASE_URL="$MCP_GATEWAY_BASE_URL" \
  -- "$GW_PY" "$GW_SERVER"
```

等价的 `.mcp.json`（项目级，置于项目根，可提交 git 供团队共享）：

```json
{
  "mcpServers": {
    "yuexin-gateway": {
      "type": "stdio",
      "command": "/abs/path/to/yuexin-mcp-connect/.venv/bin/python",
      "args": ["/abs/path/to/yuexin-mcp-connect/scripts/mcp_gateway_server.py"],
      "env": {
        "MCP_GATEWAY_BASE_URL": "https://<生产网关地址>"
      }
    }
  }
}
```

> `command`/`args` 必须用**绝对路径**（宿主继承的 PATH 可能找不到 venv python）。首次启动若拉网关 catalog 慢，可设 `MCP_TIMEOUT=60000`（毫秒）再起 `claude`。

### Codex

命令行添加：

```bash
codex mcp add yuexin-gateway \
  --env MCP_GATEWAY_BASE_URL="$MCP_GATEWAY_BASE_URL" \
  -- "$GW_PY" "$GW_SERVER"
```

等价的 `~/.codex/config.toml`（表名是 `mcp_servers`，**带下划线**，写成 `mcp.servers` 会静默不连）：

```toml
[mcp_servers.yuexin-gateway]
command = "/abs/path/to/yuexin-mcp-connect/.venv/bin/python"
args = ["/abs/path/to/yuexin-mcp-connect/scripts/mcp_gateway_server.py"]
startup_timeout_sec = 60          # 首次拉 catalog 留足时间

[mcp_servers.yuexin-gateway.env]
MCP_GATEWAY_BASE_URL = "https://<生产网关地址>"
```

## 四、第三步：验证

| 宿主 | 命令 | 期望 |
|---|---|---|
| Claude Code | `claude mcp list` | `✓ Connected  yuexin-gateway (stdio)` |
| Claude Code | 会话内 `/mcp` | 列出 `datacenter__* / ops__* / pigeon_mcp__*` 等工具 |
| Codex | 会话内 `/mcp` | 同上，server 在线且有工具 |

随后直接让 CLI 调用工具即可，例如「查今天短信发送量」会路由到 `datacenter__query_sms_analytics`。**不需要也不要传 `_operator_open_id`**——身份已经是你登录的本人。

---

## 工作原理（OAuth 模式）

```
Claude Code / Codex ──stdio MCP──► mcp_gateway_server.py
  tools/list → 启动时拉网关 GET /gateway/registry/tools（无认证，全量）→ 内存缓存
  tools/call → 读 ~/.mcp-gateway/credentials.json（本人凭证）
             → mcp_session 过期则用 feishu_refresh_token 自动续期
             → 携双令牌 POST /mcp，网关按【你本人】的角色/权限码三层判权
```

- **`tools/list` 返回全量工具**，你可能看到自己没权限的工具；真正过滤在 `tools/call` 阶段由网关执行，无权限返回 403 `permission_denied`。
- **凭证自动刷新**：`mcp_session` 短期失效会用 `feishu_refresh_token` 静默续期；refresh_token 本身过期（默认约 30 天）才需重新 `login`。

## 端口与白名单

- 本地回调固定 `http://127.0.0.1:18765/callback`（server 端 `MCP_GATEWAY_OAUTH_CALLBACK_PORT` 默认 18765；终端 `login` 用 `--port 18765` 对齐）。
- 网关侧只校验回调 **host=127.0.0.1**；**飞书开放平台后台需把该 redirect_uri 登记进应用白名单**（否则飞书拒绝授权）。换端口要同步改飞书后台与两侧参数。

## 故障速查

| 现象 | 原因 | 处理 |
|---|---|---|
| 配进宿主后首次调用就卡住/超时，无授权弹窗 | 没先终端 `login`，宿主吞了 stderr 的授权链接 | **回到第二步**先在终端 `login` 生成凭证，再用宿主 |
| `login` 浏览器没自动开 | stdio/无头环境 | 手动复制终端打印的授权链接到浏览器 |
| 授权后报 redirect_uri 不在白名单 | 飞书后台未登记该端口的回调 | 在飞书开放平台登记 `http://127.0.0.1:18765/callback`，或改用已登记端口 |
| 工具能列出、调用却 403 `permission_denied` | 你本人缺该工具权限码 | 去用户体系给你的账号/角色分配对应 privilege_code，非瞬时错误别重试 |
| 一段时间后所有调用 401 / 要求重新登录 | refresh_token 过期 | 重新跑第二步 `login` |
| `claude mcp list` 显示 Failed | venv python 路径不对 / 非绝对路径 | 用绝对路径；先 `"$GW_PY" "$GW_SERVER"` 裸跑看 stderr 报错 |
| Codex 里 server 不出现 | 表名写成了 `mcp.servers` | 改成 `[mcp_servers.yuexin-gateway]`（下划线） |

## 打包与分发

本 skill 是独立分发单元，`scripts/` 下四个脚本已内置，接收方拿到目录/zip 后无需再找其它仓库。打包时排除本地生成物：

```bash
cd skills
zip -r yuexin-mcp-connect.zip yuexin-mcp-connect \
  -x "yuexin-mcp-connect/.venv/*" \
  -x "*/__pycache__/*" -x "*.pyc"
```

对方拿到后：解压 → 按「一、前置准备」建 venv → 「二」终端 `login` → 「三」挂进自己的 Claude Code / Codex，即可用自己的飞书身份调网关工具。

## 与 mcp-gateway-agent 的关系

- **脚本已内置（vendored）**：`scripts/` 下四个文件最初复制自 mcp-gateway-agent，为了让本 skill 能脱离该仓库独立分发，**不再是同一份引用**。mcp-gateway-agent 后续若修改认证/协议逻辑，需要人工比对后手动同步过来，两边会有漂移风险，修脚本时留意。
- **模式互斥**：本 skill 走 OAuth 个人登录（凭证文件）；mcp-gateway-agent 走 Bot 多租户（`_operator_open_id` + App 凭证）。同一台机器若两种都要，用不同的宿主条目分别配置，互不影响。
