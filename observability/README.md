# 本地可观测性

当前仓库已经保存 Prometheus 配置和 Grafana dashboard JSON，但默认 `docker-compose.yml`
还没有定义 `prometheus` 和 `grafana` 服务。因此下面的 dashboard 资源是“可接入资产”，不是当前一键启动命令的一部分。

先在宿主机启动 Java 后端（默认 `8080`），再用自己的 Prometheus/Grafana 或后续 Compose profile 挂载这些文件：

```powershell
observability/prometheus.yml
observability/grafana/dashboards/
```

如果后续补 `observability` Compose profile，建议暴露：

- Prometheus：`http://localhost:9090`
- Grafana：`http://localhost:3001`，避免和前端 demo 的 `3000` 端口冲突。
- 默认本地账号可用 `admin/admin`，生产环境禁止使用默认口令。

Grafana 会自动加载：

1. `Java 商城核心`：HTTP 请求、5xx、P95、订单和支付；
2. `AI 导购`：Python 调用、SSE/同步延迟、熔断、Fallback、候选质量和推荐漏斗。

Prometheus 配置当前通过 `host.docker.internal:8080/actuator/prometheus` 抓取本机后端。若后端跑在 Docker demo 的
`backend-web` 服务中，后续 profile 应把 target 改成 `backend-web:8080/actuator/prometheus`。
