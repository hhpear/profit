# 国际利润 MCP 接入（角色 A）

2026-11-27：本说明按最新决策覆盖 docs 01–03 中的 LiteLLM 接入与 Notice 可靠发送前置条件。BI、Notice 均无需修改，Runtime 不参与。

## 服务部署

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

## 飞书临时约定

用户明确要求暂缓未实现接口，默认成功。本版 `/deliveries`、`/deliveries/query`、`/deliveries/readback` 保留 HTTP 路径，但以 task 内持久化占位记录响应，不调用不存在的 ReliableFeishuNoticeService，也不真实发送飞书消息。

返回 `state=SENT`（兼容旧结构）、`assumedSuccess=true`、`deliveryMode=ASSUMED_SUCCESS_NO_SEND`、`messageId=null`、`readbackConfirmed=false`。这里 SENT 仅为本轮假定成功，不能作为真实投递证据；回读不会返回 VERIFIED。调用方应以 assumedSuccess 完成本轮开发流程，保留该标记，不能混入真实投递验收。

保留按数据日/模式/收件人的唯一槽位、内容绑定、权限隔离和重复请求复用。占位表 profit_assumed_delivery 与未来可靠账本分离；未来真实发送须显式迁移，不能把占位记录当成已发记录，也不能自动补发历史日。

B 对接：国际 receipt 格式保持不变，meta.transport 变为 YUEXIN_GATEWAY_OAUTH，tool 变为 billing_mcp__query_daily_stats_summary。发送阶段识别 assumedSuccess，不再等待 VERIFIED；不得为通过旧校验伪造 ID、回读或正式成功证据。原生产校验仍保留，当前版本并不表示具备正式通知能力。

## 国内及其他 A 职责

国内冻结回执、分页、上海日期边界和 HMAC 认证沿用已有实现。真实 BI 调用所需注册中心、授权客户 ID/企业名称映射、字段等价性及稳定读取窗口仍须在公司环境核对；确认前返回 MAPPING_UNCONFIRMED。不得为了跑通而打开未经确认的开关。

数据库启动自动应用 V3 占位表迁移。回退代码应保留该表及现有来源回执/预算，不删除记录。公司环境 BI、MCP 身份、服务部署与 B 联调属于仍需真实环境完成的交付项。

## 本轮验证

真实查询 2026-11-26：利润所需分组返回 185 行，truncated=false；真实响应通过 Java 日回执校验并保存/复用。离线测试覆盖 OAuth 包装解析、来源预算与缓存、占位恢复/幂等冲突/越权与无伪造回读。未发送真实飞书消息，未调用真实 BI。

vendored mcp_gateway_client.py、mcp_gateway_core.py 来自本项目 yuexin-mcp-connect；后续协议升级需同步比对。
