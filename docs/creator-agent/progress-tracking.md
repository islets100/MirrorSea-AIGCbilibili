# 创作辅助 Agent — 完成进度跟踪

> 最后更新：**2026-05-27**  
> 设计基线：`api-design.md` v1.0 · `architecture-design.md` v1.0  
> 构建验证：`mvn -pl search,chat -am clean compile`（SUCCESS）；全量 `mvn clean install -DskipTests` 建议在本地复跑

---

## 1. 总览

| 维度 | 状态 | 说明 |
|------|------|------|
| 后端 MVP（P0–P2） | ✅ 已完成 | search RAG / video ASR·上下文 / chat 编排·REST·WS |
| 前端集成（P3） | ✅ 已完成 | `CreatorSuggestPanel` + `creator.js` + 上传页挂载 |
| 全链路联调（P4） | ⏳ 待运营验证 | 需配置讯飞真密钥 + ES ingest + Gateway 代理 |
| 与 api-design 对齐 | ✅ 代码层对齐 | 12 端点 + 7 种业务码 + WS 事件类型已实现 |
| 工程化加固 | ✅ 本轮完成 | Java 8 BOM、依赖安全升级、YAML/IDE 配置 |

**整体完成度（编码阶段）**：约 **90%** — 剩余主要为密钥联调、埋点与生产级观测。

---

## 2. 实施阶段（对照 architecture §8）

| 阶段 | 内容 | 模块 | 状态 | 交付物摘要 |
|------|------|------|------|------------|
| **P0** | ES 8 向量库 + ingest + retrieve | search | ✅ | **LangChain4j**：`BgeSmallZhQuantizedEmbeddingModel` + `ElasticsearchEmbeddingStore`；`KnowledgeIngestService` / `HotCaseIngestService`（Tika + `DocumentSplitters`）；`RagRetrieveService`（`EmbeddingSearchRequest` + 分数归一化）；Feign/API 契约未变 |
| **P1** | ASR + 上传上下文 API | video | ✅ | `creator/*`：`CreatorContextController`（context / asr submit·poll）、`XunfeiAsrClient`、异步 `AsrTaskServiceImpl` |
| **P2** | Agent + REST/WS + 凭证池 | chat | ✅ | `CreatorController`（4 REST）、`CreatorSuggestServiceImpl` 编排、`CreatorSparkStreamHandler`、`SparkCredentialPool`、`CreatorSuggestLog` + adopt、`WebSocketHandler` 扩展 creator 事件 |
| **P3** | UpVideo UI | vue | ✅ | `components/creator/CreatorSuggestPanel.vue`、`api/creator.js`、`UpVideo.vue` / `upLoad.vue` 集成、`vue.config.js` 代理 |
| **P4** | 联调与性能调优 | 全链路 | ⏳ | 文档见 `config-guide.md`；P95 首 token、采纳率埋点未验收 |

---

## 3. 接口与契约对齐

### 3.1 对外 REST（chat ×4）

| 方法 | 路径 | 状态 |
|------|------|------|
| POST | `/chat/creator/suggest` | ✅ |
| GET | `/chat/creator/suggest/{taskId}` | ✅ |
| GET | `/chat/creator/suggest/{taskId}/references` | ✅ |
| POST | `/chat/creator/suggest/{taskId}/adopt` | ✅ |

### 3.2 对内 Feign（search ×3 + video ×3）

| 服务 | 端点 | 状态 |
|------|------|------|
| search | `POST /search/rag/retrieve` | ✅ |
| search | `POST /search/rag/ingest/knowledge` | ✅ |
| search | `POST /search/rag/ingest/hotcases` | ✅ |
| video | `GET /createCenter/creator/context` | ✅ |
| video | `POST /createCenter/creator/asr/submit` | ✅ |
| video | `GET /createCenter/creator/asr/{asrTaskId}` | ✅ |

### 3.3 WebSocket

| type | 说明 | 状态 |
|------|------|------|
| `creator_suggest_progress` | 阶段进度 | ✅ |
| `creator_suggest_delta` | 流式 token | ✅ |
| `creator_suggest_done` | 完成 / 失败 | ✅ |

### 3.4 业务码（`CreatorErrorCode` + `Result.bizError`）

| code | 含义 | 状态 |
|------|------|------|
| 50001 | 星火失败 | ✅ |
| 50002 | ASR 失败 | ✅ |
| 50003 | Embedding 失败 | ✅ |
| 42901 | 凭证池满 | ✅ |
| 40002 | ASR 超时降级 PARTIAL | ✅ |
| 40003 | RAG 双源皆空 warning | ✅ |
| 600 | 参数/频控等通用 | ✅ |

---

## 4. 数据与配置

| 项 | 状态 | 位置 |
|----|------|------|
| `creator_suggest_log` DDL | ✅ | 根目录 `sql.sql`（`chat_session` 之后） |
| 配置说明 | ✅ | `config-guide.md` |
| 星火 / ASR 配置项 | ✅ 模板 | chat / video `application.yml`（勿提交真密钥） |
| RAG（search） | ✅ | 进程内 BGE ONNX；`embedding-dims: 512`；无需讯飞 Embedding |
| 提示词模板 | ✅ | `chat/src/main/resources/prompts/` |

---

## 5. 公共模块（common）

| 项 | 状态 |
|----|------|
| `client/creator/*` DTO / `CreatorErrorCode` / `CreatorException` | ✅ |
| `CreatorFeignHelper`、`XunfeiAuthUtil` | ✅ |
| `Result.bizError` | ✅ |
| `SearchClient` / `VideoClient` Feign 扩展 | ✅ |
| `mysql-connector-j`（替代 `mysql-connector-java`） | ✅ |
| `spring-boot-starter-validation` | ✅ |

---

## 6. 工程与依赖（2026-05-27）

| 项 | 状态 | 说明 |
|----|------|------|
| Java 8 统一编译 | ✅ | 父 POM `java.version` / `maven-compiler-plugin` |
| Jackson 安全升级 | ✅ | `jackson-bom.version=2.15.4`，显式 `dependencyManagement` |
| Hutool | ✅ | `5.8.44` |
| MySQL Connector/J | ✅ | `8.2.0` |
| Tika（search） | ✅ | `2.9.2`（Java 8 兼容，勿升 3.x） |
| Elasticsearch | ✅ | **8.15.3**（`docker-compose` + `elasticsearch-java`）；HLRC 已移除，search 统一 `ElasticsearchClient` |
| LangChain4j RAG | ✅ | BOM `0.36.2`：`langchain4j-elasticsearch`、`embeddings-bge-small-zh-q`、`document-parser-apache-tika` |
| `video/application.yml` | ✅ | 修复空 `spring.cloud:` 导致 YAML 解析错误 |
| IDE | ✅ | `.idea/misc.xml` JDK 1.8；`.vscode` `java.jdt.ls.java.release=8` |

---

## 7. 验收清单（对照 config-guide §7）

- [x] 12 个端点（REST + Feign + ingest）代码已实现
- [x] 七种业务码 REST + WS 路径已接线
- [ ] 配置真密钥后 ASR → RAG → 星火流式端到端成功（需本地密钥）
- [x] 前端四 API 封装 + 面板组件；断 WS 后轮询逻辑已实现
- [x] adopt 写入 `creator_suggest_log`（Mapper + Service）
- [x] `mvn clean install -DskipTests` 全模块通过（含 search LangChain4j 迁移后）

### LangChain4j RAG 迁移验收（2026-05-27）

| 项 | 状态 |
|----|------|
| `mvn clean install -DskipTests` | ✅ |
| `SearchServiceImpl` / `MysqlToEs` → `ElasticsearchClient` | ✅ |
| Feign `RagRetrieveRequest` / `RagController` 路径未变 | ✅ |
| ES 8 清卷 + re-ingest + retrieve 实测 | ⏳ 需本地起 ES 8.15 后执行（见 `config-guide` §5） |
| chat `CreatorSuggestServiceImpl` 端到端 | ⏳ 依赖上项 + 星火密钥 |

---

## 8. 已知遗留 / 后续

| 优先级 | 项 | 说明 |
|--------|-----|------|
| P4 | 端到端联调 | ES 8 清卷后 **全量 re-ingest** + `mysqlToEs`；Gateway 代理；星火/ASR 真密钥 |
| 低 | `adopt` 补写 `resumable_identifier` | 表字段已预留，写入逻辑可加强 |
| 低 | chat 包结构收敛 | 顶层扁包 + `creator/` 竖切并存，不影响功能 |
| 生产 | 观测与熔断 | Sentinel/指标/任务持久化等 architecture §9 建议 |

---

## 9. 变更日志

| 日期 | 摘要 |
|------|------|
| 2026-05-27 | MVP 编码完成；DDL 并入 `sql.sql`；Java 8 + 依赖安全加固；YAML/IDE 修复；全模块 Maven 构建通过 |
| 2026-05-27 | **RAG 迁移 LangChain4j**：ES 8.15 + `ElasticsearchClient`；BGE-small-zh-q（512 维）；删除讯飞/本地手写 Embedding 与 HLRC；`config-guide` 补充 ES 重建与 re-ingest 步骤 |
| — | 初始设计基线：`api-design.md`、`architecture-design.md`、`config-guide.md` |

---

## 10. 相关文档

- [接口设计](./api-design.md)
- [架构设计](./architecture-design.md)
- [配置与联调](./config-guide.md)
