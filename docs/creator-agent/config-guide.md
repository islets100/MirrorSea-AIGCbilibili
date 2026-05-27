# 创作辅助 Agent — 配置与联调指南

## 1. 凭证获取

在 [讯飞开放平台](https://www.xfyun.cn/) 分别创建应用并开通：

| 能力 | 控制台产品 | 用途 |
|------|----------|------|
| 星火 Chat | 星火大模型 | 流式生成标题/简介 |
| 语音转写 LFASR | 录音文件转写 | 视频 ASR |
| Embedding | 文本向量化（按控制台文档） | RAG 向量检索 |

**切勿将真实密钥提交到 Git。** 仅在本地 `application.yml` 或环境变量中配置。

## 2. 各服务配置项

### chat（`java/chat/src/main/resources/application.yml`）

```yaml
creator:
  xunfei:
    spark:
      host-url: wss://spark-api.xf-yun.com/v3.5/chat
      domain: generalv3.5
      credentials:
        - app-id: 你的APPID
          api-key: 你的APIKey
          api-secret: 你的APISecret
        # 可配置多组以缓解 QPS，池满返回 42901
```

### video（`java/video/src/main/resources/application.yml`）

```yaml
creator:
  xunfei:
    asr:
      app-id: 你的APPID
      api-key: 你的APIKey
      api-secret: 你的APISecret
      lfasr-host: https://raasr.xfyun.cn/v2/api
      audio-base-url: http://你的MinIO外网前缀/video/
```

`audio-base-url` 需能被讯飞公网访问；内网 MinIO 请通过 Nginx 反代或临时公网 URL。

### search（`java/search/src/main/resources/application.yml`）

```yaml
creator:
  rag:
    embedding-provider: xunfei   # 开发可用 local
    admin-token: "your-ingest-secret"   # 非空时 ingest 需请求头 X-Admin-Token
  xunfei:
    embedding:
      app-id: 你的APPID
      api-key: 你的APIKey
      api-secret: 你的APISecret
```

## 3. 环境变量（可选）

Spring Boot 支持绑定，例如：

- `CREATOR_XUNFEI_SPARK_CREDENTIALS_0_APP_ID`
- `CREATOR_XUNFEI_ASR_API_KEY`
- `CREATOR_RAG_EMBEDDING_PROVIDER=xunfei`

## 4. 未配置凭证时的行为

| 场景 | 业务码 |
|------|--------|
| 星火未配置 / 调用失败 | 50001 |
| ASR 未配置 / 转写失败 | 50002 |
| Embedding 未配置 / 失败 | 50003 |
| ASR 超时降级 | PARTIAL + warningCode 40002 |
| RAG 双源皆空 | 继续生成 + warningCode 40003 |
| 凭证池满 | 42901 |

不再使用静默 Mock。

## 5. 本地联调步骤

1. 启动 MySQL、Redis、Elasticsearch、Nacos、Gateway(8200)、chat(1688)、video(10201)、search(8201)。
2. 在 `bilibili` 库执行根目录 [`sql.sql`](../../sql.sql) 中 `creator_suggest_log` 段（紧接 `chat_session` 之后）；新环境可直接跑全量 `sql.sql`，已有库仅执行该表 DDL 即可。
3. 填写上述凭证后编译：`mvn -pl common,search,video,chat -am compile`。
4. 首次 RAG：带 `X-Admin-Token` 调用 `POST /search/rag/ingest/knowledge` 与 `ingest/hotcases`。
5. 前端 `vue/labilibili`：`npm run serve`，在 `vue.config.js` 中将 `/api`、`/wschat` 代理到 `http://localhost:8200`（Gateway）。

## 6. 幂等键说明

提交建议任务时幂等键为 `userId + resumableIdentifier`；若仅有 `videoUrl` 则使用 `userId + videoUrl`。

## 7. 验收清单

> 进度总表见 [progress-tracking.md](./progress-tracking.md)

- [x] 12 个端点（REST + Feign + ingest）代码已实现
- [x] 七种业务码 REST + WS 已接线
- [ ] 配置真密钥后 ASR → RAG → 星火流式端到端成功
- [x] 前端四 API 均有调用；断 WS 后轮询仍可拿结果
- [x] adopt 写入 `creator_suggest_log`
- [x] `mvn clean install -DskipTests` 全模块通过
