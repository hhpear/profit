# Notice 同步发送接口开发对接说明

日期：2026-11-27｜版本：v1.0 待开发评审稿。

接收方：Notice 开发人员、agent-task 开发人员、Python 利润告警负责人。

本文仅交付接口需求，不表示接口已实现或已验证。本次不修改 Notice 和 BI 源码。

## 1. 给 Notice 团队的需求

请保留现有异步接口 sendCardMessage(CardParams) 的行为，另提供一个单收件人同步发送方法。新方法在同一次 Dubbo 调用中直接请求飞书，等待并解析飞书响应，再将结构化发送结果返回 agent-task，不经过现有 Redis 发送队列。

首期只要求明确返回“飞书已成功创建消息 / 明确拒绝 / 结果未知”。不要求 Notice 本轮建设投递状态查询、消息回读或完整持久幂等账本。

利润告警需要原始 Card 2.0 JSON，不通过旧 Message 对象重建卡片，以免丢失业务内容。公司应用凭据继续由 Notice 保管。

## 2. “同步”是什么意思

当前异步：

```text
agent-task调用 → Notice写Redis → 返回
                              后台稍后取出 → 请求飞书 → 响应写日志
```

新增同步：

```text
agent-task调用 → Notice请求飞书 → 等待飞书响应 → 解析结果 → 返回agent-task
```

同步表示调用方在本次请求中等待结果，不是“同步数据库”、不是“所有人同时发送”，也不表示用户已经阅读消息。

仅修改 FeishuUtil.postGroupMsg 使其返回响应不够：原调用方早在入队后返回，后台线程拿到的结果无法通过已结束的调用传回 agent-task。必须新增一条不经队列的调用路径。

## 3. 接口与调用方式

建议在现有 FeishuNoticeService 中新增方法，保留所有旧方法：

```java
SyncCardSendResult sendCardMessageSync(SyncCardSendParams params);
```

DTO 使用可序列化 Java 类型；类名、包路径、Dubbo group/version 和 API 包版本由 Notice 团队确认后提供。也可新增独立服务，但双方须固定全限定名和参数签名。当前 agent-task 尚未适配此方法，不得假设上线Notice后客户端自动可用。

调用链：Python → agent-task HTTP → Notice Dubbo → 飞书。无需给 Python 新增 Notice HTTP 接口。

一次调用只发一个人，多收件人由 agent-task/Python逐人调用和记录，避免批量部分成功无法解释。

## 4. 请求 SyncCardSendParams

| 字段 | 类型 | 要求 |
|---|---|---|
| requestId | String | 本次调用追踪ID，不代表幂等保证 |
| application | String | 固定 enterprise-profit-alert，便于审计 |
| dataDay | String | yyyy-MM-dd，业务数据日 |
| mode | String | test或production；不得接受dry-run |
| recipientId | String | 公司稳定用户身份，供关联结果 |
| app | NoticeApp | 公司已配置应用；由可信agent-task选择 |
| receiveIdType | String | user_id或open_id，双方固定实际类型 |
| receiveId | String | agent-task完成名单授权和映射后的单人飞书ID |
| card | Map<String,Object> | 原始完整Card 2.0 JSON，schema为2.0 |
| cardSha256 | String | 规范JSON的SHA256；Notice重算核对，响应回传 |

机器人token/appSecret不进入请求，Notice从受控配置获取。Python不得绕过agent-task名单授权指定任意收件人。Dubbo仅允许可信调用者，Notice对应用及允许的调用范围进行校验。

Card摘要沿用既有约定：对象键递归按Unicode码点排序、无多余空白、UTF-8、保留Unicode、数组有序、数值只允许整数，金额使用字符串。参考 [规范摘要样例](notice-contract-v1/06-canonical-sha256-vectors.json)。

## 5. 飞书调用建议与成功定义

建议新同步路径采用单人消息创建接口：

```text
POST /open-apis/im/v1/messages?receive_id_type=<约定类型>
receive_id = receiveId
msg_type = interactive
content = 原始card序列化后的JSON字符串
```

Notice开发人员应按当前飞书官方接口及公司应用权限核对上述参数、响应和Card 2.0支持情况，完成测试后冻结契约。

当前旧卡片发送使用 /message/v4/batch_send/。首期同步接口不要简单复用批量API并把“批量任务受理”认定为“单人消息创建成功”。若团队必须使用批量API，只能返回ACCEPTED，并说明受理语义；在没有最终逐人证据前，不能返回SENT。本稿优先选择单人API，避免新增批量任务查单链路。

状态SENT在本文仅表示：飞书业务响应明确成功，且取得对应messageId。它不表示用户已读、不保证用户设备展示，也不包含后续消息回读核验。

## 6. 响应 SyncCardSendResult

无论是否成功，都应返回可解释的结果；已进入发送区间后发生超时等情况，尽量返回UNKNOWN而不是只抛异常。调用方自身超时收不到该结果时，同样按UNKNOWN处理。

| 字段 | 类型 | 要求 |
|---|---|---|
| requestId | String | 原追踪ID |
| recipientId | String | 原稳定身份 |
| cardSha256 | String | 原卡片摘要 |
| state | String | SENT / REJECTED / UNKNOWN；批量受理才用ACCEPTED |
| messageId | String/null | SENT必填，来自飞书，不得自生成占位值 |
| chatId | String/null | 飞书提供时返回；正常单人接口响应形状需实测 |
| senderAppId | String | 本次实际使用的公司应用ID，不含密钥 |
| httpStatus | Integer/null | 实际收到的HTTP状态 |
| providerCode | String/null | 飞书实际业务码，客户端不猜测含义 |
| providerMessage | String/null | 脱敏说明 |
| providerRequestId | String/null | 飞书提供的请求追踪ID，不使用虚构值 |
| errorCode | String/null | 本地统一分类，如INVALID_ARGUMENT/UPSTREAM_TIMEOUT |
| responseAt | String | 带时区的ISO8601时间，表示结果生成时间 |

成功样例（全部合成值）：

```json
{
  "requestId":"synthetic-request-001",
  "recipientId":"synthetic-user",
  "cardSha256":"<原请求64位摘要>",
  "state":"SENT",
  "messageId":"synthetic-message",
  "chatId":"synthetic-chat",
  "senderAppId":"synthetic-app",
  "httpStatus":200,
  "providerCode":"0",
  "providerMessage":"success",
  "providerRequestId":null,
  "errorCode":null,
  "responseAt":"2026-11-27T08:00:02+08:00"
}
```

其中摘要占位仅用于展示字段，不是可直接发送的JSON测试请求。日志禁止记录token和密钥；原始错误或响应如需留存，只在公司授权存储保留，返回结果脱敏。

## 7. 状态判断规则

| 场景 | 返回状态 | 说明 |
|---|---|---|
| 飞书HTTP和业务结果明确成功，且messageId存在 | SENT | 不能只判断HTTP 200 |
| 参数/权限检查在外部发送前失败 | REJECTED | 明确本次没有发送 |
| 飞书明确返回未受理业务错误 | REJECTED | Notice需明确哪些业务码具有此语义 |
| 飞书响应成功但缺messageId、响应格式异常 | UNKNOWN | 不能证明完整发送结果 |
| 超时、断连、读取响应失败 | UNKNOWN | 可能已发送，不能自动重发 |
| HTTP 5xx或错误语义不明确 | UNKNOWN | 不能一律解释为未发送 |
| 使用批量API，只明确任务受理 | ACCEPTED | 不代表逐人发送成功 |

providerCode=0的语义须与实际采用的接口对应。本地返回的httpStatus、providerCode是证据字段，不取代state判定。

建议只给明确未受理的失败保留修复后重试入口；UNKNOWN无自动重试。令牌获取失败等发送前错误，应标记阶段以区分“没有进入发送”与“结果不确定”。

## 8. 超时、重试与重复发送边界

建议初始预算：Notice连接5秒、外部响应20秒，Dubbo总调用预算大于Notice内部预算，Python HTTP总预算再大于Dubbo预算。需要包含取token、限流等待和响应处理；不能只比较一次HTTP读取超时。最终数值联调确定。

关闭新发送路径的HTTP写重试及Dubbo自动重试。限流等待有上限，不能无限阻塞。若返回UNKNOWN或agent-task自身超时，不自动重放。

本期没有Notice持久幂等账本，requestId不会天然防重，飞书辅助去重字段也不能被假定永久有效。暂不承诺跨实例、灾难恢复或人工重复调用下绝不重复。

agent-task/Python需要在网络调用前持久化提交意图，保存逐人结果。已有SENT不再发送；遗留SUBMITTING/UNKNOWN停止自动发送并由人工查证。Notice保留按requestId检索的脱敏调用结果日志，支持人工核对；日志不能被宣传为可靠查单API。

## 9. agent-task/Python需要同步调整

Notice接口完成后，agent-task开发人员需替换当前不存在的ReliableFeishuNoticeService调用，接入sendCardMessageSync及实际API包签名；不能只修改方法字符串而忽略参数类型。

Python目前按VERIFIED和回读证据验收，且真实投递门禁关闭。采用本稿方案需要明确修改以下内容后才能启用：

1. 真实调用接口、单人请求和响应字段映射。
2. 业务成功判定从“逐人回读VERIFIED”调整为“逐人取得明确SENT响应”。
3. 新规则、机器人及测试/正式名单配置，先开放测试名单。
4. 本地逐人结果持久化；UNKNOWN或ACCEPTED不能计为当日通知成功。
5. 更新上线方案、接口约定和恢复操作单；不得构造readbackConfirmed=true或伪造VERIFIED。

这属于验收标准收敛，需要相关负责人在交接评审中确认。本文件提出新同步方案，不自动宣告旧方案中的回读要求已删除。

## 10. Notice开发验收与交付清单

| 用例 | 预期 |
|---|---|
| 旧异步接口回归 | 保持原行为，不被新方法替换 |
| 同步单人成功 | 返回实际messageId与明确SENT，不入旧Redis队列 |
| HTTP200但飞书业务错误 | 不返回SENT |
| 明确参数/权限错误 | REJECTED，带可解释原因 |
| 超时、断连、成功响应缺ID | UNKNOWN，无内部自动重发 |
| 飞书原始Card2.0 | 原样发送，中文和业务字段不丢失 |
| 卡片摘要不一致 | 发送前拒绝 |
| 多人由调用方逐个提交 | 逐人结果，不把部分成功汇总成全成功 |
| 机器人或收件人越权 | 发送前拒绝 |
| 实际机器人测试 | 返回的messageId与测试消息对应；记录响应形状与业务码语义 |

Notice交付：接口包版本、方法及DTO签名、Dubbo服务配置、应用与权限配置说明、成功/拒绝/未知样例、超时和重试设置、公司构建结果及测试机器人验证记录。

agent-task交付：新方法适配、逐人结果记录、HTTP返回和错误映射、无盲目重发的恢复测试。Python交付：SENT口径校验、测试名单试发和结果留档。

## 11. 与之前文档的关系

[Notice可靠投递接口开发对接说明](05-Notice可靠投递接口开发对接说明.md)描述完整账本、查单和回读方案；本文是本轮讨论提出的较小同步发送方案。若团队采用本文，Notice本期优先实现一个同步方法，不同时要求开发旧文档中的三个新方法。完整方案作为后续演进参考，不能将两种方案的成功语义混用。

需在开发前共同确认：单人飞书API及权限、Card2.0支持、DTO签名、实际成功码与ID字段、端到端超时、UNKNOWN人工处理责任人，以及采用SENT而非VERIFIED的验收结论。
