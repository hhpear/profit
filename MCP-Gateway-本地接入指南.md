# 在本地 Claude Code / Codex 接入阅信 MCP Gateway

> 适用对象：想在自己机器的 Claude Code 或 Codex CLI 里，用**自己的飞书账号**调用阅信 MCP Gateway 下游工具（datacenter / ops / pigeon 等）的开发者。
> 认证方式：**飞书 OAuth 个人登录**。登录一次后，身份就是你本人，权限按你在用户体系里的角色/权限码判定。

---

## 0. 30 秒看懂

1. **先在终端跑一次 `login`**（飞书授权）→ 生成本地凭证文件。
2. 把 MCP server 挂到 Claude Code / Codex。
3. 直接让 CLI 调工具，**不用传任何 operator 参数**——身份已经是你本人。

> ⚠️ 顺序不能反：**必须先终端登录，再挂宿主**。原因见第 1 步的说明。

---

## 1. 前置准备

```bash
# ① 指向脚本所在目录（按你的实际克隆路径改这一行）
export GW_DIR="$HOME/projects/agents-a992569d0b/skills/mcp-gateway-agent"
export GW_PY="$GW_DIR/.venv/bin/python"
export GW_SERVER="$GW_DIR/scripts/mcp_gateway_server.py"
export GW_CLIENT="$GW_DIR/scripts/mcp_gateway_client.py"

# ② 生产网关地址（向平台索取确切地址，OAuth 已在生产配好）
export MCP_GATEWAY_BASE_URL="https://<生产网关地址>"
```

首次需要创建 venv（**Python 必须 ≥ 3.11**，系统自带的 3.9/3.10 会静默失败）：

```bash
python3.11 -m venv "$GW_DIR/.venv"
"$GW_DIR/.venv/bin/pip" install -r "$GW_DIR/requirements-server.txt"
```

---

## 2. 第一步：终端 OAuth 登录（一次性）

```bash
"$GW_PY" "$GW_CLIENT" login \
  --base-url "$MCP_GATEWAY_BASE_URL" \
  --task-key local-dev \
  --port 18765
```

- 终端会**打印飞书授权链接**并尝试自动打开浏览器；如未自动打开，手动复制链接到浏览器，用**你自己的飞书账号**授权。
- 授权后浏览器回调本地 `http://127.0.0.1:18765/callback`，终端显示 `✓ 登录成功！`，凭证写入 `~/.mcp-gateway/credentials.json`（已设权限 600，内含自动续期用的 refresh_token）。

### 为什么必须先在终端登录？

Claude Code / Codex 是以**后台子进程**方式拉起 MCP server 的。在这种环境里：

- server 子进程**无法可靠地打开浏览器**（没有图形会话）；
- server 打印的授权链接会被宿主**吞进日志文件**（如 `~/.claude/logs/`），你在终端里**看不到**。

所以如果你跳过这一步、直接让宿主第一次拉起 server 去触发登录，你会卡在「浏览器不弹、链接也找不到」。**先在普通终端 `login` 把凭证准备好**，宿主之后就只会读凭证、自动续期，不再需要交互。

---

## 3. 第二步：把 server 挂到宿主

凭证默认路径 `~/.mcp-gateway/credentials.json` 与 server 默认读取路径一致，所以下面**不需要**额外指定凭证文件。二选一：

### Claude Code

```bash
claude mcp add yuexin-gateway --scope user \
  --env MCP_GATEWAY_BASE_URL="$MCP_GATEWAY_BASE_URL" \
  -- "$GW_PY" "$GW_SERVER"
```

> `--scope user` = 当前用户所有项目都能用；`--` 后面是启动命令。

或手写 `.mcp.json`（放项目根，可提交 git 给团队共享）：

```json
{
  "mcpServers": {
    "yuexin-gateway": {
      "type": "stdio",
      "command": "/绝对路径/到/mcp-gateway-agent/.venv/bin/python",
      "args": ["/绝对路径/到/mcp-gateway-agent/scripts/mcp_gateway_server.py"],
      "env": {
        "MCP_GATEWAY_BASE_URL": "https://<生产网关地址>"
      }
    }
  }
}
```

> `command` / `args` 一律用**绝对路径**（宿主继承的 PATH 可能找不到 venv 里的 python）。首次启动拉工具目录慢的话，用 `MCP_TIMEOUT=60000 claude` 起会话。

### Codex

```bash
codex mcp add yuexin-gateway \
  --env MCP_GATEWAY_BASE_URL="$MCP_GATEWAY_BASE_URL" \
  -- "$GW_PY" "$GW_SERVER"
```

或手写 `~/.codex/config.toml`（表名是 `mcp_servers`，**带下划线**；写成 `mcp.servers` 会静默不连）：

```toml
[mcp_servers.yuexin-gateway]
command = "/绝对路径/到/mcp-gateway-agent/.venv/bin/python"
args = ["/绝对路径/到/mcp-gateway-agent/scripts/mcp_gateway_server.py"]
startup_timeout_sec = 60

[mcp_servers.yuexin-gateway.env]
MCP_GATEWAY_BASE_URL = "https://<生产网关地址>"
```

---

## 4. 第三步：验证

| 宿主 | 操作 | 期望 |
|---|---|---|
| Claude Code | `claude mcp list` | `✓ Connected  yuexin-gateway (stdio)` |
| Claude Code | 会话内输入 `/mcp` | 列出 `datacenter__* / ops__* / pigeon_mcp__*` 等工具 |
| Codex | 会话内输入 `/mcp` | server 在线、显示工具 |

之后直接用自然语言让 CLI 调工具即可，例如「查今天短信发送量」会路由到 `datacenter__query_sms_analytics`。**不需要传 operator / open_id 之类参数**——身份就是你登录的本人。

---

## 5. 它是怎么工作的

```
Claude Code / Codex ──stdio MCP──► mcp_gateway_server
  列工具  → 启动时从网关拉全量工具目录（无需认证）
  调工具  → 读 ~/.mcp-gateway/credentials.json（你本人的凭证）
          → 短期 session 过期时用 refresh_token 自动续期
          → 带令牌请求网关，网关按【你本人】的权限三层判权
```

- **列出的工具是全量的**，你可能看到自己没权限的工具；真正的权限过滤在**调用时**由网关执行，没权限会返回 `permission_denied`。
- **凭证会自动刷新**，平时无感；只有 refresh_token 整个过期（默认约 30 天）才需要重新 `login`。

---

## 6. 端口与飞书白名单

- 本地回调固定用 `http://127.0.0.1:18765/callback`（终端 `login` 传 `--port 18765`，与 server 默认端口对齐）。
- **飞书开放平台后台需要把这个回调 URL 登记进应用白名单**，否则飞书会拒绝授权。要换端口，需同步改飞书后台和命令里的 `--port`。

---

## 7. 遇到问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 挂到宿主后首次调用就卡住/超时，没有授权弹窗 | 没先在终端 `login`，授权链接被宿主吞进日志 | 回到**第 2 步**先终端登录，再用宿主 |
| `login` 时浏览器没自动打开 | 无头/受限环境 | 手动复制终端打印的授权链接到浏览器 |
| 授权后报「redirect_uri 不在白名单」 | 飞书后台没登记该端口回调 | 在飞书开放平台登记 `http://127.0.0.1:18765/callback`，或改用已登记端口 |
| 工具能列出、一调用就 `permission_denied`（403） | 你本人缺该工具的权限码 | 找平台给你的账号/角色分配对应权限码（配置问题，别反复重试） |
| 用一阵后所有调用要求重新登录 | refresh_token 过期 | 重新跑第 2 步 `login` |
| `claude mcp list` 显示 Failed | python 路径不对 / 用了相对路径 | 改绝对路径；先在终端裸跑 `"$GW_PY" "$GW_SERVER"` 看报错 |
| Codex 里 server 不出现 | 配置表名写成了 `mcp.servers` | 改成 `[mcp_servers.yuexin-gateway]`（下划线） |

---

## 附：需要平台/管理员协助的事

1. 提供**生产网关地址**（填到 `MCP_GATEWAY_BASE_URL`）。
2. 在**飞书开放平台**登记本地回调 `http://127.0.0.1:18765/callback`（若全员统一端口，登记一次即可）。
3. 给你的账号/角色分配所需工具的**权限码**（否则能看到工具但调不动）。
