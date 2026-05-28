# 创作辅助 Agent — 配置与联调指南

## 1. 凭证获取

在 [讯飞开放平台](https://www.xfyun.cn/) 分别创建应用并开通：

| 能力 | 控制台产品 | 用途 |
|------|----------|------|
| 星火 Chat | 星火大模型 | 流式生成标题/简介 |
| 语音转写 LFASR | 录音文件转写 | 视频 ASR |

> **RAG 向量化**已改为 search 进程内 **LangChain4j + BGE-small-zh-q（ONNX）**，**不再**需要讯飞 Embedding 凭证。

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
elasticsearch-client:
  hosts: http://localhost:9200   # Docker 内服务为 http://elasticsearch:9200

creator:
  rag:
    knowledge-dir: ../知识库      # 相对 search 工作目录，放 .md/.txt 等
    embedding-dims: 512           # BGE-small-zh-q，升级后须全量 re-ingest
    chunk-size: 500
    chunk-overlap: 50
    hot-case-play-threshold: 10000
    admin-token: "your-ingest-secret"   # 非空时 ingest 需请求头 X-Admin-Token
```

RAG 实现：`Langchain4jRagConfig`（`BgeSmallZhQuantizedEmbeddingModel` + `ElasticsearchEmbeddingStore`），索引名 `creator_knowledge`、`creator_video_case`。

## 3. 环境变量（可选）

Spring Boot 支持绑定，例如：

- `CREATOR_XUNFEI_SPARK_CREDENTIALS_0_APP_ID`
- `CREATOR_XUNFEI_ASR_API_KEY`
- `ELASTICSEARCH_CLIENT_HOSTS=http://localhost:9200`

## 4. 未配置凭证时的行为

| 场景 | 业务码 |
|------|--------|
| 星火未配置 / 调用失败 | 50001 |
| ASR 未配置 / 转写失败 | 50002 |
| RAG 检索 / ingest 失败（含 ES、ONNX） | 50003 |
| ASR 超时降级 | PARTIAL + warningCode 40002 |
| RAG 双源皆空 | 继续生成 + warningCode 40003 |
| 凭证池满 | 42901 |

不再使用静默 Mock。

## 5. Elasticsearch 8 与数据重建

项目 `docker-compose.yml` 已使用 **Elasticsearch 8.15.3**（`xpack.security.enabled=false`）。自 ES 7.x 升级或更换向量模型后，**开发环境推荐**：

1. 停止 ES 容器：`docker compose stop elasticsearch`
2. 删除向量与关键词旧数据卷：`docker volume rm <project>_es-data`（或 `docker compose down -v` 仅删 ES 相关卷）
3. 重新拉起：`docker compose up -d elasticsearch`
4. 等待 `http://localhost:9200` 可用后：
   - **RAG**：`POST /search/rag/ingest/knowledge`、`POST /search/rag/ingest/hotcases`（配置 `admin-token` 时加 `X-Admin-Token`）
   - **关键词搜索**：执行 XXL-Job `mysqlToEs` 或调用 search 模块同步接口，重建 `video` / `user` / `history_search` 等索引

> 向量维度由 256 改为 **512**（BGE-small-zh-q），旧 `creator_*` 索引不可复用，必须 re-ingest。

## 6. 本地联调步骤

1. 启动 MySQL、Redis、**Elasticsearch 8.15**、Nacos、Gateway(8200)、chat(1688)、video(10201)、search(8201)。
2. 在 `bilibili` 库执行根目录 [`sql.sql`](../../sql.sql) 中 `creator_suggest_log` 段（紧接 `chat_session` 之后）；新环境可直接跑全量 `sql.sql`，已有库仅执行该表 DDL 即可。
3. 填写星火 / ASR 凭证后编译：`mvn -pl common,search,video,chat -am compile`。
4. **全量 RAG ingest**（ES 8 空集群或清卷后必做）：
   - `POST http://localhost:8201/search/rag/ingest/knowledge`
   - `POST http://localhost:8201/search/rag/ingest/hotcases`
5. 验证检索：`POST /search/rag/retrieve`，body 含 `queryText`、`topKCase`、`topKKnowledge` 等（与 Feign `RagRetrieveRequest` 一致）。
6. 前端 `vue/labilibili`：`npm run serve`，在 `vue.config.js` 中将 `/api`、`/wschat` 代理到 `http://localhost:8200`（Gateway）。

## 7. 幂等键说明

提交建议任务时幂等键为 `userId + resumableIdentifier`；若仅有 `videoUrl` 则使用 `userId + videoUrl`。

## 8. 验收清单

> 进度总表见 [progress-tracking.md](./progress-tracking.md)

- [x] 12 个端点（REST + Feign + ingest）代码已实现
- [x] 七种业务码 REST + WS 已接线
- [ ] 配置真密钥后 ASR → RAG → 星火流式端到端成功
- [x] 前端四 API 均有调用；断 WS 后轮询仍可拿结果
- [x] adopt 写入 `creator_suggest_log`
- [x] `mvn clean install -DskipTests` 全模块通过
