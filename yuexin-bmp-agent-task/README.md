# yuexin-bmp-agent-task

BMP Agent 任务控制服务。负责策略、Run、Task、Event、内部 API、HMAC 认证和任务执行。

## Python HTTP 联调测试

相邻目录克隆 `yuexin-profit-runtime` 后运行 `mvn test`。也可用 `-Dprofit.python.project=/absolute/path/to/python-project` 指定 Python 项目位置；兼容原工作区目录名。真实 BI/MCP 测试需要独立环境配置，默认不执行。
