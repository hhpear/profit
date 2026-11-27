# B3 本地部署包与恢复操作

2026-11-27。当前默认dry-run，真实取数身份、Notice同步发送及业务规则接入未完成。未安装服务器调度，未执行真实取数或发送。

## 部署包

从项目根目录执行 `python3 -m scripts.package_profit_alert --output /新路径/profit-alert.zip`。包内附DEPLOYMENT_MANIFEST.json，可逐文件验证SHA256。白名单包含scripts、config、deploy、必要的兼容入口及治理文档，排除运行输出和凭据。源码包含模拟数据和测试，禁止把它们作为生产历史。运行路径只用标准库，requirements-runtime.lock没有第三方依赖；旧MCP/DeerFlow其他运行路径不在此声明中。

Docker构建：`docker build -f deploy/Dockerfile -t profit-alert:b3 .`。基础标签为python:3.11.14-slim-bookworm，需tzdata；本机无Docker，因此镜像构建未验证。发布时从公司批准仓库固定基础镜像digest，记录构建后镜像ID；当前标签和apt源不能视为完全可重复的系统依赖锁。

容器运行需将公司配置通过受控env-file注入，将授权持久目录挂载为/state，宿主目录归UID/GID 10001所有。示例：

```sh
docker run --rm --env-file /etc/profit-alert/environment -v /var/lib/profit-alert:/state profit-alert:b3 run --state-dir /state --mode dry-run
```

本包不自动启用容器调度。宿主venv方式与容器方式二选一，避免两套入口同时执行。

## Linux主机安装与调度

1. 解包到/opt/profit-alert，Python3.11+创建/opt/profit-alert/venv，安装deploy/requirements-runtime.lock。
2. 管理员创建专用profit用户，以及该用户可读写的/var/lib/profit-alert、/var/backups/profit-alert；SQLite放本地磁盘，不放共享网络盘。备份另复制到公司批准的独立存储，单机目录本身不防磁盘丢失。
3. 根据environment.example创建/etc/profit-alert/environment，权限0600，由systemd管理器读取；密钥不进入源码、镜像或归档。
4. 导入授权初始历史。首跑手动使用ops run的--previous-history参数；此后入口自动选择兼容历史。禁止用空历史或模拟样例启动生产。
5. 将systemd模板安装到/etc/systemd/system，确认Linux版本支持OnCalendar中的Asia/Shanghai；先执行systemd-analyze verify和systemd-analyze calendar，再daemon-reload。08:00执行昨日任务，09:00独立检查，09:15备份。Persistent=true仅补一次漏触发，不逐日补发业务通知。
6. 提供/usr/local/sbin/profit-alert-ops-notify，接公司批准的独立运维通知通道。该程序由管理员配置，输入为失败单元名，密钥不放项目；不能仅写日志冒充通知已接通。未接通时不能验收运维通知。
7. 验证后启用profit-alert.timer、profit-alert-watch.timer、profit-alert-backup.timer。当前模板均为dry-run，不能作为正式业务成功监控。未来调整发送模式时任务和watch配置必须一致。

只允许单主机单实例；入口使用fcntl锁及SQLite唯一约束。服务器进程硬超时1900秒，应用子进程超时1800秒；KillMode=control-group清理同组进程。没有自动Restart，失败交运维处理。

## 退出码与漏跑

- 0：该模式运行/产物检查通过。
- 10：进程失败，但查得既有完成记录；本次不伪报成功。
- 20：目标日缺记录、未完成、FAILED或产物校验失败。
- 30：待核对投递或恢复门禁。
- 40：应用执行超时。
- CLI输入/底层异常可能使用其他非零码；systemd均视为失败通知。

watch独立读SQLite目标日状态，核验SUCCESS manifest和治理产物/源摘要。进程退出0不能单独替代业务检查。所有日志通过journal/stdout收集，读取失败和磁盘错误均不得判成功。

## 备份与恢复

```sh
python3 -m scripts.profit_alert_ops backup --state-dir /var/lib/profit-alert --archive-root /var/backups/profit-alert
python3 -m scripts.profit_alert_ops restore --archive /选定的完整备份 --state-dir /新的恢复目录
```

备份取得与执行相同的锁，运行中不争抢；持锁期间复制产物，用SQLite backup API复制数据库并integrity_check，最后生成所有文件的摘要清单。失败备份不得使用。默认不自动删除历史备份，保留期由公司确认并做磁盘容量监控。

恢复先验证清单，目标目录必须不存在，再复制并检查数据库，生成RESTORE_REQUIRES_RECONCILIATION.json。ops和B1入口都拒绝带此标记的运行。恢复不修改原冻结输入、绝对路径或摘要，避免更换已经绑定投递的内容。

优先停所有调度，将原目录留存后恢复到相同绝对路径。换路径时必须逐一处理并验证来源引用；不能自动改路径和重算绑定摘要继续发送。核对备份时间之后及SUBMITTING/UNKNOWN消息、独立Notice账本或同步发送结果；本地备份不能证明外部未发送。完成核对留档后由运维移除门禁标记，再验证产物，最后启用唯一调度。不能确认外部结果时保持关闭。

回退只回退代码，保留SQLite、来源回执和外部投递事实；不自动重新开启旧个人任务。

## 本地演练结果与限制

Python3.12.14以-S禁用第三方site包，72项测试通过：原64项加8项运维测试。覆盖成功/漏跑、清单缺失、备份完整性、备份损坏拒绝、运行锁、重复执行锁、超时分类、未决状态备份恢复和恢复门禁。同路径恢复后原产物治理校验通过。

运行：`python3 -m unittest scripts.test_profit_alert_ops scripts.test_profit_alert_b1 scripts.test_enterprise_profit_alert scripts.test_enterprise_profit_alert_domestic`。

本机为macOS，没有Docker或systemd；未验证镜像构建、Linux实际定时触发、真实超时杀进程组、物理磁盘故障、外部通知和异地备份。超时退出码通过注入超时测试；这不替代目标主机故障演练。B3当前交付为本地包和自动恢复测试，服务器部署验收仍待上述检查。2027日历和真实通知口径仍需按业务版本发布，不在本包中自动变更。
