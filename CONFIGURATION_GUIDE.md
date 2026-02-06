# 配置说明

## 数据源配置

### sparkle.ranking.database-type

控制用于用户 Swarm 统计的数据源类型。

**值：**
- `clickhouse` - 使用 ClickHouse 进行高性能聚合查询
- `postgres` - 使用 PostgreSQL（默认，如果未配置此项）

## 配置示例

### 方案 1：使用 ClickHouse（推荐用于高负载）

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:postgresql://localhost:5432/sparkle
          username: postgres
          password: password
          driver-class-name: org.postgresql.Driver
        clickhouse:
          url: jdbc:clickhouse://localhost:8123/sparkle3
          username: default
          password: ""
          driver-class-name: com.clickhouse.jdbc.ClickHouseDriver

sparkle:
  ranking:
    database-type: clickhouse  # 使用 ClickHouse
    weight:
      user-swarm-statistics:
        sent-traffic-other-ack: 10
        sent-traffic-self-report: 5
        received-traffic-other-ack: 3
        received-traffic-self-report: 5
```

**启动时日志：**
```
SwarmStatisticsClickHouseServiceImpl loaded
```

### 方案 2：使用 PostgreSQL（默认）

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:postgresql://localhost:5432/sparkle
          username: postgres
          password: password
          driver-class-name: org.postgresql.Driver

sparkle:
  ranking:
    database-type: postgres  # 明确指定 PostgreSQL
    # 或者完全省略 database-type 配置项
    weight:
      user-swarm-statistics:
        sent-traffic-other-ack: 10
        sent-traffic-self-report: 5
        received-traffic-other-ack: 3
        received-traffic-self-report: 5
```

**启动时日志：**
```
SwarmStatisticsPostgresServiceImpl loaded
```

## Bean 注册规则

```
database-type = "clickhouse"  → SwarmStatisticsClickHouseServiceImpl
database-type = "postgres"    → SwarmStatisticsPostgreSQLServiceImpl
database-type 未配置          → SwarmStatisticsPostgreSQLServiceImpl (默认)
database-type = 其他值        → SwarmStatisticsPostgreSQLServiceImpl (默认)
```

## 验证配置

### 1. 检查启动日志

启动应用后，查看日志中是否包含：

- **ClickHouse 模式：** `SwarmStatisticsClickHouseServiceImpl loaded`
- **PostgreSQL 模式：** `SwarmStatisticsPostgresServiceImpl loaded`

### 2. 检查定时任务日志

当 `cronUserSwarmStatisticsUpdate` 执行时，查看日志：

- **ClickHouse 模式：** `Fetching aggregated statistics from ClickHouse for X users`
- **PostgreSQL 模式：** `Fetching aggregated statistics from PostgreSQL for X users`

### 3. 手动触发（调试用）

访问：
```
GET /debug/userSwarmStatistic/executeCronUserSwarmStatisticsUpdate
```

查看日志输出确认使用的数据源。

## 切换数据源

1. 修改 `application.yaml` 中的 `sparkle.ranking.database-type`
2. 重启应用
3. 检查启动日志确认加载了正确的实现

## 注意事项

### ClickHouse 模式前置条件

使用 ClickHouse 模式前，确保：

1. ClickHouse 服务已启动并可访问
2. 数据库 `sparkle3` 已创建
3. 以下表已存在并有数据：
   - `userapp`
   - `swarm_tracker`
   - `userapps_heartbeat`
4. 数据从 PostgreSQL 同步到 ClickHouse（或直接写入 ClickHouse）

### PostgreSQL 模式

- 无特殊要求，使用主数据源
- 这是默认和回退方案
- 即使未配置 ClickHouse 数据源也能正常工作

## 故障排除

### 问题：应用启动失败，提示找不到 Bean

**原因：** 两个实现都未被加载

**解决：**
1. 检查 `application.yaml` 中 `database-type` 拼写
2. 确认配置层级正确（在 `sparkle.ranking` 下）
3. 查看启动日志是否有条件注解相关警告

### 问题：配置了 ClickHouse 但使用了 PostgreSQL

**原因：** 配置值不匹配

**检查：**
```yaml
# 正确
sparkle:
  ranking:
    database-type: clickhouse

# 错误示例
sparkle:
  ranking:
    database-type: "clickhouse"  # 带引号可能导致匹配失败
    database-type: ClickHouse    # 大小写敏感
    databaseType: clickhouse     # 驼峰命名不正确
```

### 问题：ClickHouse 查询失败

**症状：** 定时任务报错，无法查询数据

**可能原因：**
1. ClickHouse 服务不可用
2. 表不存在或结构不匹配
3. 数据未同步

**解决：**
1. 检查 ClickHouse 连接
2. 验证表结构
3. 临时切换到 PostgreSQL 模式维持服务
