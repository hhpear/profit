# Python 到 agent-task 调用部署

Python 独立运行，沿用 Java Runtime 的 `基础地址 + /internal/...` HTTP 调用和 HMAC 规则。利润基础路径为 `/internal/profit-alert/v1`，不调用账单的 claim/execute，不需要启动 Runtime。Notice 保留工作区现有实现，本轮不改其代码、不切换同步发送。当前工作区的 DubboProfitNoticeGateway 是数据库落账的假定成功实现，不执行外部发送或真实回读；路径联通不能当作投递完成。

## agent-task

从 `yuexin-bmp-agent-task` 构建：

```sh
mvn -Pprofit-dubbo clean package
```

在部署平台注入 `AGENT_TASK_DB_URL`、`AGENT_TASK_DB_USER`、`AGENT_TASK_DB_PASSWORD`、`PROFIT_AGENT_TEST_HMAC_SECRET`，启动时启用测试身份配置：

```sh
java -jar target/yuexin-bmp-agent-task-0.1.0-SNAPSHOT.jar --spring.profiles.active=profit-test
```

`application-profit-test.yml` 登记固定测试身份 `profit-alert-test` 和其独立密钥。若设置了 `AGENT_TASK_AGENT_PRINCIPALS`，该变量必须同时包含利润身份及需要保留的原身份。单独设置旧 `AGENT_TASK_HMAC_SECRET` 不能访问利润接口。正式身份通过外部配置独立登记，不复用测试身份。

来源按现有实现配置 BI 注册中心/接口版本、MCP、经核对的客户范围和企业映射、读取一致性；投递接口还需测试接收人映射。新 profile 只补身份，不提供模拟来源，也不自动开启映射确认。

## Python

使用已有 deploy/Dockerfile 或 systemd 部署，凭据在部署平台注入，不写入镜像。已有环境变量：

```dotenv
PROFIT_API_URL=https://REPLACE_WITH_AGENT_TASK_HOST
PROFIT_API_KEY=profit-alert-test
PROFIT_API_SECRET=REPLACE_WITH_SAME_SECRET_AS_JAVA
PROFIT_API_ALLOW_HTTP=false
```

若与 Java Runtime 一样直接走受信内网，可使用 `PROFIT_API_URL=http://yuexin-bmp-agent-task:8080` 并明确设置 `PROFIT_API_ALLOW_HTTP=true`。服务名必须在 Python 所在网络可解析；同一主机直接运行时可用 `http://127.0.0.1:8080`，容器内的 localhost 指容器自身。HTTP 不加密密钥签名之外的业务数据，仅限受信网络。

基础地址只填协议、主机和端口；不填接口路径、网关前缀或查询参数。代理需原样转发 `/internal/profit-alert/v1/...`，防止签名路径与服务端 requestURI 不同。客户端自动添加五个利润接口路径、X-Request-Id 和四个 HMAC 头，签名使用实际发出的 JSON 字节，时间戳为秒。两端时钟需在默认 300 秒窗口内。

加载环境后，从 Python 项目根目录执行：

```sh
python3 -m scripts.probe_profit_alert_sources --data-day 2026-11-26 --output-dir /var/lib/profit-alert/probe-20261126-01
```

目录须不存在；探针读取真实国内历史及国际日回执，不发送 Notice。取数通过后可使用原部署说明运行 dry-run，准备授权初始历史。dry-run 仍取真实数据。

## 验证

Python 测试：`python3 -m unittest scripts.test_profit_alert_http scripts.test_profit_alert_b1 scripts.test_profit_alert_ops`。

Java 测试：在 agent-task 执行 `mvn test`。其中 PythonHttpContractTest 启动临时 HTTP 端口，让 Python Client 直接发请求到 Java 生产 HMAC 过滤器、Controller 和来源服务。底层来源及 Notice 为合成实现，不证明公司环境连通或真实投递。

401 检查身份密钥、实际请求路径、时间和 nonce；403 检查 agent-principals，投递时再检查身份 mode 及接收人映射；来源错误检查 BI/MCP 配置。现有 Notice 假定成功分支不证明真实发送，真实下游接入另行验证。
