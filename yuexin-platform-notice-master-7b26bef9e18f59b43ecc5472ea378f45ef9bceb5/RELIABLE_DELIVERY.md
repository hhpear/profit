# 利润告警可靠投递接入

新增 Dubbo 服务 `com.yuexin.platform.notice.service.ReliableFeishuNoticeService`，旧 FeishuNoticeService 保持兼容。方法为 submit(Map)、query(String)、readbackOwned(Map)。普通 readback 仅供可信服务内部调用；对外网关必须用 readbackOwned 并传认证上下文 ownerPrincipal。Dubbo 只允许公司可信网关调用，不能暴露公网。

## 发布

1. 在公司 Maven 环境构建 notice-api 后构建服务；公共父 POM 及版本由内部仓库提供。新增 JDBC、MySQL 驱动、测试依赖 H2。
2. 使用独立持久数据库连接，执行 `yuexin-platform-notice-service/src/main/resources/db/notice_delivery.sql` 一次；此 SQL 不会由启动自动执行。DDL 先行，旧接口不依赖新表。
3. 配置 spring.datasource.url、username、password；复用 app-config.feishu-apps 的 NoticeApp 与应用身份。密钥不放版本库。
4. 发布 API 包和服务，agent-task 注册此 Dubbo 接口并限制可信调用身份。
5. 在测试名单验证 Card 2.0、senderAppId、chatId 及原始内容回读。现实现只接受原始卡片 EXACT 内容；如飞书返回渲染结构，会失败关闭，不会假装 VERIFIED。需要公司环境确认支持的响应形状，若不支持 EXACT，再实现经过业务审核的标记验证器。

## 请求与账本

submit 使用 docs/03 的字段，另外由可信 agent-task 注入 ownerPrincipal、app、receiveIdType、receiveId。公司稳定 recipientId 与实际 Feishu ID 的映射及名单授权由 agent-task 完成，客户端不能控制这些注入字段。card 必须为 Map，金额为字符串，数值只允许整数；digest 为规范 JSON UTF-8 SHA256。

唯一槽为 application、data_day、mode、recipient_id；别名唯一。所有请求绑定摘要，包括规则、输入、卡片、身份和别名。同槽位换别名也拒绝。跨实例领取由数据库条件更新完成；每次状态更新独立提交，网络调用不占数据库事务。

状态为 PREPARED → SUBMITTING → SENT → VERIFIED；明确飞书业务拒绝为 REJECTED，网络/响应不确定为 UNKNOWN。SUBMITTING 和 UNKNOWN 绝不自动重新发送。query 返回实际账本，不把 HTTP 成功或无异常当消息成功。

## 核查与恢复

保持账本独立备份，不随代码回滚删除。遗留 SUBMITTING 按可能已发送核查；不能清空 messageId 或删除整行来重试。UNKNOWN 必须由公司运维查询飞书权威证据；已发送则核对 messageId/chatId 后受控更新为 SENT 并回读；明确未发生方可审计后回到 PREPARED，继续使用原槽与原请求。SQL 操作至少双人复核并保存旧行、证据、操作者、变更单，首期没有自动重发或绕过参数。REJECTED 修复权限后同样经受控操作恢复，不修改绑定内容。

## 验证边界

新增 ReliableDeliveryTests 覆盖规范 JSON、拒绝浮点、UNKNOWN 不重发、同槽异内容冲突和跨 owner 回读拒绝。公司私有父 POM、真实 Dubbo、真实数据库迁移和飞书投递必须在公司构建与联调环境验证。现有源码的旧队列通知不受新通道影响。
