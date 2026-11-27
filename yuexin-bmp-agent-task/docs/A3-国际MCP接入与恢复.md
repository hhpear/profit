# A3 国际 MCP 接入与恢复

2026-11-27。修改范围仅 agent-task；BI、Notice 保持原样。A2 暂缓，A1 身份配置由同学继续处理。

## 本轮完成

- 实际入口为已授权的 Yuexin Gateway，工具 billing_mcp__query_daily_stats_summary，OAuth 登录；不走旧 LiteLLM。
- 固定口径：Pigeon；Asia/Shanghai 全天；enterprise_id/account_id/send_channel_id 分组。保留完整性、身份、金额、日期、来源、截断校验，不补零。
- 国际回执增加获取时间、内容 SHA256；上游没有版本时 sourceVersion=null。摘要不冒充上游版本。
- 网关响应 JSON 小数与文本 JSON 小数在首次解析时转十进制字符串，避免先经过 float 再转回金额。
- 数据库协调每日查询，业务接口调用者不需要增加请求字段。重复请求复用 SUCCESS 回执；并发中的请求返回 503/SOURCE_UNAVAILABLE/retryable=true，稍后读取同日状态，不创建新预算。
- 每日最多三次来源尝试；预算与尝试记录在同一事务中预占。网络调用不置于数据库事务内。
- FETCHING 有数据库时钟计的 180 秒租约。桥接进程总超时 120 秒；旧执行者失去租约后不能覆盖新执行者结果。
- 进程中断且租约过期：有持久化 raw_response 时直接校验/提交，不再取数、不增加预算；无 raw_response 时将原尝试标为 INTERRUPTED，在剩余预算内重新取数。
- 原始响应落盘、日回执提交与尝试状态更新有事务保护。数据库故障后保留预算/现场，恢复时按租约与原始响应接续。
- 401/403/截断或语义错误持久化失败，不因 HTTP 重试重复调用上游。网络异常及 429/502/503/504 可按全日预算恢复。
- 凭据明确指定文件；缺失、损坏或网关地址不匹配则拒绝。服务不打开浏览器，不回落 Bot 身份或开发机默认凭据。
- 同机短进程通过凭据旁的 .lock 文件串行化读写和续期，避免自动刷新时相互覆盖。

## 部署配置

先应用 V4_profit_international_lease.xml（task 自己的数据库）；Liquibase 已加入 master.xml。已有 READY/RETRYABLE/SUCCESS 记录保持；旧 FETCHING 无租约的记录可以按现有预算恢复。

示例（路径按镜像调整，密钥不入仓库）：

```sh
PROFIT_MCP_PYTHON=/usr/bin/python3
PROFIT_MCP_BRIDGE=/app/integrations/mcp-gateway/query_daily.py
PROFIT_SOURCE_MCP_URL=http://106.14.207.126:8000
MCP_GATEWAY_BASE_URL=http://106.14.207.126:8000
MCP_GATEWAY_CREDENTIALS_FILE=/run/secrets/mcp-gateway/credentials.json
MCP_GATEWAY_TIMEOUT=100
```

Python 3.11+；桥接只用标准库，三份脚本需一起部署。凭据目录必须允许服务写入 .lock 和原子替换凭据；凭据及 lock 权限 600。首次 OAuth 授权在受控交互环境完成，服务器运行阶段不自动登录。gateway 地址须与凭据所属 gateway 一致。

Java 所有实例必须使用同一 task 数据库；MySQL 表需支持事务与行锁（InnoDB）。本轮并发/恢复测试使用 H2，不等于已验收生产 MySQL 锁行为。生产环境须用目标数据库再做同日并发演练。

跨主机不能依赖本地文件锁协调共享 refresh token。首期建议单实例；多实例需要分别配置独立授权的凭据，或统一由一个凭据管理服务续期，不能复制同一个可轮换 refresh token 到多台主机后假设安全。每日预算仍由共享数据库控制。

## 恢复操作

- 正常成功日：直接重新请求同日接口，只读缓存。
- 进程中断：等租约过期后重新请求。无需删行、重置 attempts 或手动强占租约。
- 已收到原始回执但未提交完成：从 raw_response 恢复，不额外调用 MCP。
- 三次预算耗尽：停止自动查询，保留记录。需进一步查询时另行审批处理，不删除来源证据或重置每日预算。
- 401/403：先检查云端指定账号、授权与工具权限，必要时重新 login。不会因 HTTP 重试自动重开失败日。
- 修复授权后如需恢复该失败日，应留人工处理记录，只允许在原预算内将对应授权失败记录改回 RETRYABLE；不修改 attempts、raw_response 或已有 SUCCESS，不恢复 SOURCE_INCOMPLETE。

授权恢复 SQL 示例，仅用于已确认修复的目标数据日（替换参数后由运维执行；本轮未执行）：

```sql
UPDATE profit_international_day
SET state = 'RETRYABLE', error_code = NULL, error_status = NULL
WHERE data_day = :data_day
  AND state = 'FAILED'
  AND error_code IN ('UNAUTHENTICATED', 'FORBIDDEN')
  AND raw_response IS NULL AND attempts < 3;
```

云端身份应由公司指定账号授权并具备 billing_stats_view。当前真实查询使用本次用户已授权的本地账号；不把凭据复制进项目，不宣称公司身份已配置。

## 验证证据

真实 Java → Python → MCP → 校验 → task 日回执 → 新服务实例缓存复用：

| 数据日 | 行数 | 截断 | 重复请求 |
|---|---:|---|---|
| 2026-11-24 | 162 | false | 缓存复用 |
| 2026-11-25 | 175 | false | 缓存复用 |
| 2026-11-26 | 185 | false | 缓存复用 |

每个日期只触发一次来源调用。没有发送消息或操作 BI。

Java 故障测试覆盖：双实例竞争、租约到期接续、旧进程写入阻断、原始回执恢复不增加预算、三次上限、事务回滚、语义/权限失败不自动重试。

Python 离线测试使用合成凭据，覆盖：有效会话复用、过期会话刷新及 refresh token 轮换原子保存、refresh token 失效、拒绝错误网关/无效凭据、文件锁、金额精度。未人为篡改真实凭据以制造过期。

测试命令：

```sh
mvn test
python3 -m unittest discover -s integrations/mcp-gateway -p 'test_*.py'
```

真实连续日测试需部署侧配置好上述环境变量，并显式运行：

```sh
mvn -Dtest=ProfitSourceServiceTest#realConsecutiveDaysWhenConfigured -Dprofit.real.consecutive=true test
```

该测试日期固定为本次验收日期，后续验收应选授权且已结束的自然日并更新测试输入。

## 尚需云端验收

公司运行身份及工具权限、目标 MySQL 行锁、真实凭据到期续期、部署目录权限、重启恢复与网络超时。代码与本地真实调用已完成，不代表已部署云端。
