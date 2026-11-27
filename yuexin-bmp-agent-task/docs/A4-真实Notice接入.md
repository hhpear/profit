# A4 真实调用现有 Notice

2026-11-27。用户明确选择：调用现有 Notice、接受卡片格式调整；真实发送，暂缓确认/回读。不改 BI、Notice，不向 task 复制机器人密钥。

## 调用链与卡片

Python → task `/internal/profit-alert/v1/deliveries` → 既有 `com.yuexin.platform.notice.service.FeishuNoticeService.sendCardMessage(CardParams)` → Notice Redis 队列 → Notice 后台调用飞书。

使用原接口，不调用不存在的 ReliableFeishuNoticeService。

Notice Message 不支持原始 Card 2.0。task 转换标题、正文、日期、企业和金额；原布局/走势图无法保留。趋势以“近15日相对走势（0–100，非利润金额）”列出原图表的日期与 trendIndex，不把归一化指数当金额。保留原始 Card 2.0 产物和摘要，实际转换版本标为 notice-legacy-v1。未知元素或正文超过限制时在提交前报错，不静默删内容、不自动拆分/截断。

默认单条正文上限为 20000 UTF-8 字节，可配置 profit.notice.max-content-bytes。它是适配器的保守限制，不等于已确认飞书平台所有限制。

CardParams 使用 `contactIds`，对应 Notice 的飞书 user_id；不支持把 open_id 传入这个字段。公司用户体系 ID、OAuth open_id、飞书 user_id 必须区分，不能仅因字段名同为 user_id 就复用。

## 配置与构建

启用 profit-test，配置独立 HMAC 测试身份与测试白名单：

```sh
PROFIT_AGENT_TEST_HMAC_SECRET=<双方一致的受限密钥>
PROFIT_NOTICE_URL=dubbo://<Notice提供者地址>/com.yuexin.platform.notice.service.FeishuNoticeService
PROFIT_NOTICE_APP=BusinessAlarm
PROFIT_NOTICE_TEST_USER_ID=<已核实的飞书user_id>
```

代码中仅包含空白默认值，不写真实收件人或密钥。配置中的稳定 recipientId 为 test-recipient。生产应独立配置身份与 profit.recipients.production，不回落测试名单。

也可以使用 PROFIT_NOTICE_REGISTRY；需要版本/分组时配置 profit.notice.version/group。两种连接配置至少填写一种。打包须启用 Dubbo：`mvn -Pprofit-dubbo clean package`。

## 提交状态和防重复

新增 V5 的 profit_real_delivery；保留 V3 旧占位表。真实记录包含唯一槽位/幂等别名、绑定摘要、所有者、状态和返回证据。

1. 校验卡片 SHA256、参数、授权接收目标和卡片可转换性。
2. 持久化 PREPARED。
3. 单条条件更新取得发送权，提交 SUBMITTING 后才调用 Notice；网络调用不在数据库事务中。
4. Notice 正常返回后记 QUEUED；异常或断连记 UNKNOWN，不自动重发。

重启后遗留 SUBMITTING 同样不能抢占重发。查询本地记录只证明提交历史，不替代 Notice 的投递账本或飞书回读。旧占位记录不能自动当作真实发送完成，也不自动补发；若相同槽位/别名已被旧记录占用，新提交返回409，需人工决定迁移。

新成功响应示意：

```json
{
  "state":"QUEUED",
  "deliveryMode":"NOTICE_QUEUE",
  "transport":"NOTICE_DUBBO",
  "noticeApp":"BusinessAlarm",
  "noticeAccepted":true,
  "sendAttempted":true,
  "assumedSuccess":false,
  "messageId":null,
  "readbackConfirmed":false
}
```

不再无条件返回 SENT。readback 路径只返回授权范围内的提交证据并标记 readbackSupported=false，不伪造 VERIFIED。

## Python 对接

本轮同时更新 enterprise-profit 的 HTTP 编排，使用显式 `--accept-notice-queue` 接受现有 Notice 的能力边界：

```sh
python3 -m scripts.profit_alert_b1 \
  --state-dir <本地状态目录> --previous-history <合法历史文件> \
  --data-day <已结束日期> --mode test \
  --recipient test-recipient --sender-app BusinessAlarm \
  --accept-notice-queue
```

仍需要有效的国内/国际输入、规则和历史，不绕过 A2 的国内来源问题。完整流程不能仅靠通知接入就声称跑通。

HTTP 客户端校验目标、模式、绑定摘要和 noticeApp。QUEUED 保存为 NOTICE_ACCEPTED_UNCONFIRMED、send_attempted=true、message_readback_confirmed=false；它表示当前明确约定的队列接收验收，不等于已送达。生产使用前仍须确认公司机器人、正式名单、规则迁移和 A1 权限。

旧 --assume-notice-success 仅用于读取历史占位流程，不能与新开关一起使用；新真实发送不需要它。

## 本轮实际联调与未解除缺口

从已知开发注册中心只读发现 Notice 提供者 192.168.12.244:20900。通过真实 Dubbo 调用提交一条标题为“利润通知接入测试（合成数据）”的消息，Notice 正常返回。测试使用仓库外的持久化本地记录防重复，未向其他收件人发送。

首次将网关公司用户体系中的 user_id 当成飞书 user_id 使用，未先完成映射核实；这是接入验证中的错误，不能作为有效收件人验收。用户反馈暂未收到。未继续重发。

之后使用用户提供的本人手机号/企业邮箱进行只读查询：开发环境 BusinessAlarm、BMP 的 batch_get_id 返回成功但均未返回 user_id；BusinessAlarm 的完整通讯录查询另返回权限不足。Notice 队列当前为空，无法据此推断送达或具体失败原因。

待管理员确认：当前开发 Notice 应用的租户、通讯录可见范围、机器人可用范围，以及本人的真实飞书 user_id；再由用户确认可见测试消息。不要通过修改 Notice 代码、硬编码内部 ID、换其他机器人或重复发送绕过。

## 验证范围

离线测试覆盖真实 sender 边界被调用、重复提交/跨实例单次发送、结果未知不重发、历史占位不自动补发、权限、摘要与内容冲突、图表转文本、缺失配置提前拒绝；Python 覆盖队列接受的显式开关、重复运行复用、未知结果不判成功及原回读路径。

真实测试显式启用，普通 mvn test 不发送任何消息。后续只有在确认收件人后才运行；不得删除原本地投递记录以强制重发。
