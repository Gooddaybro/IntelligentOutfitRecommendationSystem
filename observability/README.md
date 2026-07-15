# 本地可观测性

先在宿主机启动 Java 后端（默认 `8080`），再启动 Prometheus 和 Grafana：

```powershell
docker compose --profile observability up -d prometheus grafana
```

- Prometheus：`http://localhost:9090`
- Grafana：`http://localhost:3000`
- 默认本地账号：`admin/admin`，可通过 `GRAFANA_ADMIN_USER`、`GRAFANA_ADMIN_PASSWORD` 覆盖；生产环境禁止使用默认口令。

Grafana 会自动加载：

1. `Java 商城核心`：HTTP 请求、5xx、P95、订单和支付；
2. `AI 导购`：Python 调用、SSE/同步延迟、熔断、Fallback、候选质量和推荐漏斗。

Prometheus 在容器内通过 `host.docker.internal:8080/actuator/prometheus` 抓取本机后端。Linux 环境已通过 Compose 的 `host-gateway` 映射；若后端不在本机，修改 `observability/prometheus.yml` 的 target。
