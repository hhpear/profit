# 企业客户利润异常告警

企业客户利润日历基线（中位数 / MAD）、连续下降判断、飞书卡片及投递门禁的源码快照。

## 校验

Python 3.11+，安装 `requirements.txt` 后在仓库根目录执行：

```sh
python3 scripts/validate_enterprise_profit_alert_governance.py
python3 -m unittest scripts.test_enterprise_profit_alert scripts.test_enterprise_profit_alert_domestic scripts.test_enterprise_profit_alert_deerflow
```

## 运行边界

- 当前规则为 `2026-11-06.1`，规则与治理说明见 `docs/企业客户利润异常告警.md`。
- `SOURCE_MANIFEST.json` 记录本地源码快照的 SHA-256；本仓库不包含生产回读或部署状态。
- 国内取数依赖部署机 `~/.codex/skills/_dashboard_shared/runtime.py` 提供的 `bmp_post` 和已授权身份。
- 国际取数依赖部署机 `~/.codex/auth.json` 的 LiteLLM 凭据及 canonical MCP 客户端。
- 飞书投递依赖已配置的 `lark` CLI、应用身份、收件人解析和消息回读权限。
- DeerFlow 快照登记所指向的客户历史数据未上传，缺失或哈希不匹配时应失败关闭。
- 本仓库不包含凭据、客户明细、运行输出或定时调度安装；本次未执行取数、发送或生产变更。

同步完成不表示可脱离上述受控运行环境直接生产运行。

The runner still reads the source workspace operating-dashboard rules pointer for international processing. Included operating rules are the original dependency snapshot, not a declaration of current dashboard production rules. Source files remain unchanged.

## 已知验证限制

核心 50 项测试与静态治理校验通过。DeerFlow 的 4 项测试依赖本地客户历史快照；独立仓库不提供这些数据，因此 2 项会因数据缺失报错。原工作区另有旧测试将已登记的 2026-09-01 当作未登记日期，导致 1 项断言失败。本次按源码同步保留该现状，未修改业务或测试。

## 研发交接补充资料

- [研发接管与生产交付说明](docs/研发接管与生产交付说明.md)：分工、接管流程与验收要求。
- [当前运行方式、个人依赖及已知问题](docs/当前运行说明.md)：参数形状、历史续接、失败处理及机器人所有人核查。
- [最近30个完整数据日及脱敏样例](docs/examples/recent-30-days/README.md)：2026-10-20至2026-11-18，区分异常、零告警与无成功记录。

SOURCE_MANIFEST.json仍是首次源码快照的校验清单，不涵盖本轮新增交接文档；业务源码和规则保持原内容。
