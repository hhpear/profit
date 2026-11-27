# 国际利润 MCP 接入（角色 A）

2026-11-27：本说明按最新决策覆盖 docs 01–03 中的 LiteLLM 接入与 Notice 可靠发送前置条件。BI、Notice 均无需修改，Runtime 不参与。

## 服务部署

本地测试配置已写入 agent-task 根目录 `.env.mcp.local`（Git 忽略、权限 0600），使用独立凭据文件 `~/.mcp-gateway/profit-test/credentials.json`，不覆盖默认个人凭据。启动 Java 前，在该目录用 bash 加载：

```sh
set -a
. ./.env.mcp.local
set +a
```

首次授权在 agent-task 根目录使用 `python3 integrations/mcp-gateway/mcp_gateway_client.py login`，指定网关地址、`--task-key profit-test --port 18765` 和同一个 `--credentials-file` 路径。需本人在飞书页面完成授权。完成后可在加载上述环境的终端执行 `"$PROFIT_MCP_PYTHON" "$PROFIT_MCP_BRIDGE" 2026-11-26` 验证国际来源；输出包含真实客户事实，只保存到授权目录。环境模板见本目录 `environment.example`，本地路径不可直接用于服务器。

Java 构建：`mvn -Pprofit-dubbo clean package`。国际适配在 agent-task 容器内启动 Python 3.11+ 短进程，使用同目录内随包提供的网关客户端。不需要安装 MCP SDK，也不依赖开发机绝对路径；部署时把本 integrations 目录一同复制到镜像。

运行环境（实际路径由部署方填写）：

```sh
export PROFIT_MCP_PYTHON=/usr/bin/python3
export PROFIT_MCP_BRIDGE=/app/integrations/mcp-gateway/query_daily.py
export MCP_GATEWAY_CREDENTIALS_FILE=/run/secrets/mcp-gateway/credentials.json
export MCP_GATEWAY_TIMEOUT=100
export PROFIT_SOURCE_MCP_URL=http://106.14.207.126:8000
```

凭据需要事先通过随包 `mcp_gateway_client.py login --base-url http://106.14.207.126:8000 --port 18765` 授权生成。部署时只挂载到受限的凭据目录，目录需允许服务原子更新凭据以自动续期，不提交 Git。服务内不弹浏览器、不自动登录，不读取开发机默认个人凭据。当前真实验证采用本次已授权账号；公司运行身份及其 `billing_stats_view` 权限仍需部署时配置，不能把个人联调成功当成公司身份已验收。

调用链：HTTP `/international/daily` → 持久化每日尝试预算 → Python OAuth bootstrap/自动续期 → 已认证 tools/list → `billing_mcp__query_daily_stats_summary` → 原始响应保存与完整性校验 → 缓存日回执。

固定查询：Pigeon，北京时间全天，按 enterprise_id/account_id/send_channel_id 分组。上游文本 JSON 中的小数先解析为十进制字符串；不改金额口径。错误/截断/字段缺失不补零；成功回执重复请求复用，跨实例最多三次来源尝试。AUTH 失效需重新授权；不会回落个人 CLI 或旧 LiteLLM 入口。

## 飞书接入（已更新）

已按用户最新选择改为真实调用现有 Notice，接受卡片格式转换。新提交不再走 ASSUMED_SUCCESS_NO_SEND。正常返回为 QUEUED / NOTICE_QUEUE，只证明 Notice 入队，不证明飞书实际送达，且无 messageId/回读。

Python 使用 --accept-notice-queue，保存真实发送尝试与 NOTICE_ACCEPTED_UNCONFIRMED。--assume-notice-success 仅保留旧占位兼容，不能用于新真实发送。详情及联调缺口见 [A4 真实 Notice 接入](../../docs/A4-真实Notice接入.md)。

## 国内及其他 A 职责

国内冻结回执、分页、上海日期边界和 HMAC 认证沿用已有实现。真实 BI 调用所需注册中心、授权客户 ID/企业名称映射、字段等价性及稳定读取窗口仍须在公司环境核对；确认前返回 MAPPING_UNCONFIRMED。不得为了跑通而打开未经确认的开关。

数据库启动自动应用来源及通知迁移，真实提交使用 V5 的 profit_real_delivery 表。回退代码应保留该表及现有来源回执/预算，不删除记录。公司环境 BI、MCP 身份、服务部署与 B 联调属于仍需真实环境完成的交付项。

## 本轮验证

真实查询 2026-11-26：利润所需分组返回 185 行，truncated=false；真实响应通过 Java 日回执校验并保存/复用。离线测试覆盖 OAuth 包装解析、来源预算与缓存、占位恢复/幂等冲突/越权与无伪造回读。未发送真实飞书消息，未调用真实 BI。

vendored mcp_gateway_client.py、mcp_gateway_core.py 来自本项目 yuexin-mcp-connect；后续协议升级需同步比对。

## 当前本机授权验证（2026-11-27）

本次独立 profit-test 凭据已完成 OAuth 登录；网关 `http://106.14.207.126:8000` 可达。当前登录账号的 tools/list 返回空列表，指定调用 `billing_mcp__query_daily_stats_summary` 返回 HTTP 403，error=permission_denied，stage=service_permission，trace_id=trace_227ab077a749442d8d8ae4efc20e1bf4。需要网关管理员为该账号配置服务及利润统计工具权限后重测；本次未取得国际日数据，不代表真实来源验收通过。凭据及响应文件仅在本机受限目录，不提交仓库。

A3 恢复与部署更新：见 [国际 MCP 接入与恢复](../../docs/A3-国际MCP接入与恢复.md)，包含 V4 数据库迁移、租约/预算、连续日证据、凭据恢复及多实例限制。
