# Notice 假定成功临时流程

本轮不修改 yuexin-platform-notice 目录。沿用 agent-task 现有占位落账实现；它不调用外部 Notice、不实际发送飞书消息。Python 在 test 模式显式开启后接受假定成功回执，不等待 VERIFIED 或伪造消息 ID。

配置好取数、身份和服务端测试接收人映射后，在 Python 项目目录运行：

```sh
python3 -m scripts.profit_alert_ops run \
  --state-dir /var/lib/profit-alert \
  --mode test \
  --previous-history /授权目录/history.json \
  --recipient TEST_RECIPIENT_KEY \
  --sender-app CONFIGURED_APP_ID \
  --assume-notice-success
```

接收人键和应用 ID 填实际测试配置。三个 PROFIT_API 环境变量仍需注入。该流程真实取数，当前 MCP 账号仍须先解决服务权限。重复运行复用已登记记录，不重复提交；输入及规则绑定检查继续保留。

仅接受 agent-task 明确返回 assumedSuccess=true、deliveryMode=ASSUMED_SUCCESS_NO_SEND、readbackConfirmed=false、messageId=null 的占位回执。汇总回执保留相同假定标记，send_attempted=false；运行清单写 assumed_notice_success=true。SUCCESS 表示该临时测试流程完成，不代表飞书发送成功。无告警时沿用无需发送状态。

不开启参数仍按原回读验证处理；正式 production 模式不接受该临时参数。运维模板仍默认 dry-run，本轮不启用服务器调度。
