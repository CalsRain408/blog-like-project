# Blog-Like Project

基于 Spring Boot 3 的综合实战项目，围绕**点评/秒杀**场景，集成分布式缓存、消息队列、AI 问答等多项技术。

---

## 技术栈

| 分类 | 技术 |
|------|------|
| 框架 | Spring Boot 3.5 · Spring WebFlux |
| ORM | MyBatis-Plus 3.5 |
| 缓存 | Redis (Lettuce) · Caffeine |
| 消息队列 | Apache Kafka |
| AI | LangChain4j · Alibaba DashScope (qwen-plus) |
| 数据库 | MySQL 8 |
| 其他 | Hutool · Lombok |

---

## 模块功能

### 用户
- 手机号验证码登录，Token 存储于 Redis
- 双拦截器：登录校验 + Token 自动续期

### 店铺
- 店铺信息二级缓存（Caffeine L1 → Redis L2 → DB），防穿透 / 防击穿

### 优惠券 & 秒杀
- 秒杀库存预热至 Redis，Lua 脚本原子校验库存 & 一人一单
- 秒杀成功后订单写入 **Kafka**（`seckill-order` topic），消费者异步落库
- 优惠券详情 / 店铺券列表走**二级缓存**，短 TTL 保证最终一致性

### 订单自动关闭
- `@Scheduled` 每分钟扫描超时未支付订单（默认 15 分钟）
- CAS 关单（`status 1→4`），关单后释放 Redis 库存 key 之外的 DB 库存
- 库存释放失败时发送 Kafka 消息（`stock-release` topic），消费者重试；重试耗尽触发告警
- CAS 解决支付成功和关单操作的并发冲突

### 限流
- 自定义 `@RateLimiter` 注解 + AOP + Redis 滑动窗口 Lua 脚本
- 支持按 **IP / 用户 / 方法** 三种维度限流，秒杀接口默认 10 秒内 100 次

### AI 问答助手
- 基于 LangChain4j `@AiService` + **Function Calling**
- 流式返回（`Flux<String>`），对话记忆存储于 Redis（TTL 1 天）
- 三个工具：查询商家信息、查询优惠券、预约到店消费

---

## 架构概览

```
HTTP 请求
    │
    ├─ 限流（Redis 滑动窗口）
    │
    ├─ 登录校验（Redis 验证码）
    │
    ├─ 秒杀流程
    │     Lua 脚本校验 → KafkaProducer → 立即返回订单ID
    │                          │
    │                    KafkaConsumer (seckill-order)
    │                          └─ DB 扣库存 + 创建订单
    │
    ├─ 优惠券详情读取
    │     Caffeine (5min TTL) → Redis (30min TTL) → DB
    │
    └─ 订单超时关闭（每分钟）
          CAS 关单 → 释放 DB 库存
                └─ 失败 → Kafka (stock-release) → 重试
```

---

## 快速启动

**前置依赖**
- Java 21
- MySQL 8（执行 `src/main/resources/db/hmdp.sql`）
- Redis 6+
- Kafka（端口：`9092`）

**修改配置**

编辑 `src/main/resources/application.yaml`，按需调整数据库密码、Redis 密码、Kafka 地址及 AI API Key：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blog
    password: your_password
  data:
    redis:
      password: your_redis_password
  kafka:
    bootstrap-servers: localhost:9092

langchain4j:
  open-ai:
    chat-model:
      api-key: your_api_key
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
```

**启动**

```bash
mvn spring-boot:run
```

服务默认运行在 `http://localhost:8081`。

---

## 主要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/voucher/seckill` | 新增秒杀优惠券 |
| POST | `/voucher-order/seckill/{id}` | 秒杀下单 |
| GET | `/voucher/{voucherId}` | 优惠券详情（二级缓存） |
| GET | `/voucher/list/{shopId}` | 店铺优惠券列表 |
| GET | `/shop/of/type` | 按类型查附近店铺 |
| GET | `/chat` | AI 问答（流式） |
