# 提交评测可靠调度设计

## 1. 目标与范围

本文设计提交记录创建后，如何可靠地把评测工作交给 Worker，并支持并发消费、失败重试、故障恢复、审计和运维。设计目标包括：

- 接受提交后，评测任务不能因 API 进程崩溃而丢失。
- 消息可以重复投递，但同一个评测任务不能产生重复的业务结果。
- Worker 崩溃、网络分区或评测进程超时后，任务能够恢复或进入明确的终态。
- 用户可见的 `submission` 状态与内部任务调度状态各自清晰，并保持最终一致。
- 编译和运行用户代码必须在隔离沙箱中，不能在 API 进程或数据库事务中执行。

本文按 RabbitMQ 作为消息代理、Redis 作为辅助基础设施进行设计，覆盖 Server、Worker 和评测结果存储之间的调度契约，不定义具体编译器、语言运行时和沙箱实现。M0-B 的数据库表、事件名和字段以根目录 `docs/m0-b/` 与 `docs/事件契约.json` 为准；本文中的租约、执行器和运行时扩展属于后续 Worker 阶段，不得覆盖 M0-B 已定契约。MySQL 保存业务事实；RabbitMQ 负责可靠传递；Redis 不承担提交、任务和评测结果的持久化职责。

## 2. 设计决策

### 推荐生产方案

采用 **Transactional Outbox + RabbitMQ + Worker Inbox/任务表 + 隔离执行器**。Redis 按需用于缓存、限流等辅助能力，不加入评测任务可靠性链路：

```text
客户端
  |
  v
Server API
  | 单个 MySQL 事务
  +-- submission
  +-- outbox_event (`evaluation.requested`)
  |
  v
Outbox Relay --Publisher Confirm--> RabbitMQ
                                  |
                                  v
                         评测事件消费者/调度入口
                                    | 按 event_id 去重
                                    +-- 核验已存在的 evaluation_task
                                    |
                                    v
                              Task Dispatcher
                                    |
                                    v
                              隔离执行沙箱
                                    |
                                    v
                         结果持久化 / 状态事件
```

Outbox 解决“提交事务成功但事件未可靠发布”的双写问题。RabbitMQ 负责持久缓冲和跨进程传递；消费端 Inbox 以稳定事件 ID 去重，并核验提交事务中已经创建的 `evaluation_task`。采用至少一次投递，不能因重复消息再次创建 submission 或 evaluation task。RabbitMQ 的 ACK 只表示消费端已可靠接收/处理该事件，不表示评测已经完成。

Redis 不替代 Outbox、RabbitMQ、Inbox 或任务表。缓存丢失、淘汰或故障时，系统仍应能依靠 MySQL 和 RabbitMQ 完成提交评测；Redis 不可用不应导致已接受的评测任务丢失。

### 部署变体

如果未来确认 Server 和 Worker 共用一个 MySQL、吞吐较低且不需要消息代理的部署形态，可以改用数据库 `evaluation_task` 表作为持久队列。此时提交与任务在同一个数据库事务中写入，Worker 原子领取任务，不需要 Outbox 或 RabbitMQ。该方案仍需租约、幂等、重试和监控；不能在同一条链路里同时把数据库轮询和 RabbitMQ 当作两个任务事实来源。

当前采用 RabbitMQ 的理由是 Server 与 Worker 可独立部署、扩缩容和故障恢复，并通过持久队列吸收评测突发流量。M0-B 阶段仍由 Server 事务持久化 `evaluation_task`；后续 Worker 运行尝试和租约可独立演进，但不得重复创建受理任务。

## 3. 名词与职责

| 名称 | 所属方 | 回答的问题 | 不负责的事 |
| --- | --- | --- | --- |
| `submission` | Server | 用户提交了什么、当前对外状态是什么 | 不承担消息投递和 Worker 租约 |
| `outbox_event` | Server | 哪个业务事件尚未可靠发布 | 不表示 Worker 是否完成评测 |
| RabbitMQ 消息 | RabbitMQ | 如何持久传输、缓冲和重投递 | 不作为唯一业务状态来源 |
| Redis | Server/Worker 辅助组件 | 如何缓存可重建数据或做短期流量控制 | 不保存提交次数、幂等事实或评测任务状态 |
| `inbox_event` | 事件消费者 | 这个事件是否已被指定消费者处理 | 不表示评测执行结果 |
| `evaluation_task` | 当前由 Server 受理事务创建 | 哪次提交需要评测及其持久任务事实 | 不保存源代码内容或完整敏感日志 |
| `evaluation_result` | 评测结果域 | 编译、运行、用例和资源使用结果 | 不负责提交受理 |

Outbox 是可靠发布记录，Evaluation Task 是具体执行记录。即使二者由同一个模块管理，也不能把 `PUBLISHED` 当作 `SUCCEEDED`，或用一个状态字段混合“待发送”和“评测完成”。

## 4. 领域状态与状态所有权

### Server 的 `submission.status`

当前 M0-B 受理成功即创建 `QUEUED` submission。后续评测运行期间的用户可见状态建议为：

```text
QUEUED -> RUNNING -> SUCCEEDED
                   \-> FAILED
```

- `QUEUED`：Server 已在本地事务中保存 submission、evaluation task 和 Outbox 事件，提交等待调度。
- `RUNNING`：Worker 已开始执行评测。
- `SUCCEEDED` / `FAILED`：评测得到终态。
- `REJECTED`：提交未通过提交阶段规则；这不是编译失败或答案错误的替代状态。

状态流转必须由拥有该状态的模块执行，并使用版本条件更新或等效的并发控制。当前契约只有 `evaluation.requested`、`result.accepted` 等根目录 Schema 中定义的事件；不要自行假设存在 `EvaluationStarted` / `EvaluationFinished` 事件。未来若需传播运行状态，先扩展事件 Schema 和状态责任，再实现生产消费。

### 持久评测任务与后续运行状态

```text
PENDING -> RUNNING -> SUCCEEDED
                   \-> RETRY_WAIT -> PENDING
                   \-> FAILED
```

- `PENDING`：已持久化，等待领取。
- `RUNNING`：持有有效租约，可能正在执行。
- `RETRY_WAIT`：失败可重试，等待 `available_at`。
- `SUCCEEDED`：已保存终态结果。
- `FAILED`：输入或任务本身不可继续，进入终态；基础设施重试耗尽时使用 `DEAD_LETTER`，并将 submission 设为 `SYSTEM_FAILED`。
- 可选 `CANCELLED`：系统支持用户或管理操作取消尚未运行任务时使用。

重试期间 Worker 任务可处于 `RETRY_WAIT`，而用户提交可继续显示 `RUNNING`。不要为了内部的短暂重试不断改变用户可见状态。

## 5. 数据模型

以下是逻辑字段设计，SQL 类型应遵循项目现有 UUID、时间精度和命名约定。索引及约束需要通过正式迁移创建。

### 5.1 Server `outbox_event`

M0-B 当前表结构以 `docs/m0-b/01-数据库创建指南.md` 为准：`evaluation_task_id`、`submission_id`、`assignment_id`、`student_id`、`content_version`、`status`、`revision`、`attempt_count`、创建/更新时间；`revision` 是任务状态的持久化并发版本，也是事件 `aggregate_revision` 的来源。当前 `submission_id` 唯一约束只支持每次提交创建一个首评任务；人工重评启用前，必须通过迁移改为支持同一 submission 关联多个 task，并定义 task 序号/类型及相应唯一约束。不得直接把下列后续调度字段当成当前表结构：

后续 Worker 阶段若需要完整执行调度，可增加独立的运行尝试/租约模型，或经正式迁移扩展任务表。需要明确 `generation`、attempt、lease 和 M0-B `attempt_count` 的不同语义，不能只根据本文草案直接改表。

后续运行时可考虑的字段：

| 字段 | 用途 |
| --- | --- |
| `event_id` | 稳定且全局唯一的事件 ID，消息代理重投时保持不变 |
| `aggregate_type` | `EVALUATION_TASK` |
| `aggregate_id` | 对 `evaluation.requested` 和 `result.accepted` 使用 `evaluation_task_id` |
| `event_type` | 例如 `evaluation.requested` |
| `schema_version` | 事件结构版本 |
| `payload` | 最小事件数据，建议只放 ID 和必要的不可变快照引用 |
| `status` | `PENDING`、`PUBLISHING`、`PUBLISHED`、`DEAD` |
| `created_at` | 创建时间 |
| `available_at` | 下次允许发布的时间 |
| `publish_attempts` | 发布尝试次数 |
| `locked_by`, `locked_until` | Relay 领取租约 |
| `published_at` | 发布确认时间 |
| `last_error_code` | 安全错误码，不放秘密或完整堆栈 |

约束与索引：`event_id` 主键；索引 `(status, available_at, locked_until)`；可按 `(aggregate_type, aggregate_id, event_type)` 设唯一约束，避免应用重复生成同一逻辑事件。RabbitMQ 重投必须复用同一 `event_id`；人工重评创建不同的 task ID，因此属于不同聚合，不依赖 `generation` 区分。

Outbox 的 payload 不应携带源代码、凭据或临时对象存储签名 URL。Worker 收到 `submission_id` 后，通过有权限的接口读取已验证对象，或根据不可变对象引用获取文件。

Outbox 是 MySQL 中的可靠发布日志，不是 RabbitMQ 的镜像队列。Relay 只有在收到 RabbitMQ Publisher Confirm 且消息未被 `mandatory` 返回后，才能将事件标记为已发布；确认超时按发布结果不确定处理，允许使用相同 `event_id` 重发。

### 5.2 事件消费者 `inbox_event`

建议字段：`consumer_name`、`event_id`、`event_type`、`received_at`、`processed_at`、`processing_status`、`last_error_code`。按 `(consumer_name, event_id)` 建唯一键。消费者在本地事务登记 Inbox 并核对已存在的任务事实；重复事件命中唯一键后幂等忽略，不创建第二个 submission 或 evaluation task。当前 Server 侧路径是 `server.evaluation.infrastructure`。

M0-B 数据库指南已定义 `inbox_event`，当前实现应遵循该表和 `(consumer_name, event_id)` 主键。未来若改用其他持久化去重机制，需先更新架构和迁移设计。

### 5.3 后续 Worker 运行尝试（设计建议）

当前 M0-B 的 `evaluation_task` 列定义见 `docs/m0-b/01-数据库创建指南.md`，受理事务与 submission、outbox 一起创建。下表描述未来 Worker 运行尝试需要考虑的数据，不是当前 `evaluation_task` 表的字段清单，也不是可直接执行的建表定义。

| 字段 | 用途 |
| --- | --- |
| `attempt_id` | 某次 Worker 执行尝试 ID；不是提交 ID、任务 ID 或事件 ID |
| `evaluation_task_id` | 关联 M0-B 持久评测任务 ID |
| `status` | Worker 执行尝试状态，不替代 Server 的 `SubmissionStatus` |
| `priority` | 调度优先级，默认普通优先级 |
| `available_at` | 可领取时间，退避重试使用 |
| `attempt_count` | 已开始的执行次数，领取但未启动不增加 |
| `max_attempts` | 最大执行次数 |
| `lease_owner` | 当前 Worker/执行器实例标识 |
| `lease_token` | 每次领取产生的新 fencing token，防止过期 Worker 写结果 |
| `lease_until` | 租约失效时间 |
| `heartbeat_at` | 最近心跳时间 |
| `created_at`, `updated_at` | 审计时间 |
| `started_at`, `finished_at` | 执行时间 |
| `failure_category` | 失败分类，如 `INFRASTRUCTURE`、`COMPILE`、`TIMEOUT` |
| `last_error_code` | 最近一次安全错误码 |
| `version` | 乐观锁版本或 CAS 更新字段 |

若将运行尝试独立建表，可用唯一键 `(evaluation_task_id, attempt_id)`。人工重评流程及其唯一性需待业务定义后另定。领取索引按目标数据库和实际 claim SQL 设计；本段是后续设计建议，不是 M0-B 的迁移定义。

### 5.4 `evaluation_result`

结果与任务调度信息分表，避免任务行不断承载大型结果。结果以 `evaluation_result_id` 标识，并关联 `evaluation_task_id`；事件 `result.accepted` 携带结果 ID、内容版本、摘要和批次引用。详细结果字段及唯一约束应与根目录事件 Schema 和结果接收设计一起确定，不能把 `generation` 当成当前契约必填字段。详细测试用例输出和日志应设置大小上限、保留周期和访问权限。

## 6. Server 接受提交的事务

在 `SubmissionCreator.createSubmission()` 的本地事务中完成：

1. 校验作业状态、截止时间和内容版本。
2. 校验 `SourceObject` 所有权、状态、大小和 SHA-256。
3. 分配 `attemptNo`。
4. 插入 `submission`，状态设为 `QUEUED`（或按现有契约先 `ACCEPTED`，但必须定义何时转 `QUEUED`）。
5. 插入 `evaluation.requested` Outbox 事件，payload 按根目录 `docs/事件契约.json` 校验。
6. 提交事务。

计数器、提交和 Outbox 必须使用同一个数据库事务管理器。Outbox 写入失败时整个事务回滚；不能先提交提交记录再直接向消息代理发送。

对象存储流式校验可能很耗时，不应把大文件读取放在持有 MySQL 事务的阶段。可在短事务开始前完成内容校验，然后在短事务中重新确认关键数据库约束/作业版本并原子递增计数器、保存提交和 Outbox。作业内容版本需要不可变或用数据库版本/条件更新防止校验与提交之间发生配置竞态。

## 7. RabbitMQ 拓扑、Outbox Relay 与消息契约

### RabbitMQ 拓扑

建议先建立一套版本化的任务事件拓扑：

| 资源 | 建议配置 | 作用 |
| --- | --- | --- |
| Exchange | durable topic exchange，例如 `evaluation.events` | 按事件类型路由并支持后续增加消费者 |
| Routing key | `evaluation.requested` | 与现有事件契约事件名一致 |
| Queue | durable quorum queue，例如 `evaluation.requested.v1` | 持久化、高可用的评测事件缓冲 |
| Binding | `evaluation.requested` -> evaluation queue | 将评测请求路由给契约定义的消费者 |
| DLX/DLQ | 专用 dead-letter exchange 和队列 | 收集无法自动恢复或超过投递上限的消息 |

消息设置 persistent delivery mode，并配置队列长度/字节上限和告警。生产部署由 RabbitMQ 集群承载 quorum queue；不要把 classic queue 的单节点持久化误认为节点级高可用。Exchange、Queue、Binding、DLX 等拓扑应由基础设施配置或启动时幂等声明管理，权限使用独立的最小权限账号，TLS 与凭据轮换按部署环境配置。

### Outbox Relay

Relay 原子领取到期的 `PENDING` 事件，设置数据库租约后，在数据库事务之外向 RabbitMQ 发布。使用 Publisher Confirms，并启用 `mandatory` 检查不可路由消息；只有收到 broker confirm 且未发生 return 后，才以 `event_id` 和租约条件把事件标为 `PUBLISHED`。Confirm 超时、连接中断或进程在确认后标记前崩溃时，发布结果视为不确定并允许重发。因此消息必须使用稳定 `event_id`，Worker 必须去重。若消息被 return 或达到发布重试上限，事件不能伪装成已发布，应进入可告警、可审计的失败状态。

生产环境还应限制单次 Relay 批量、设置连接/确认超时、发布并发和退避抖动，避免 RabbitMQ 故障时快速耗尽数据库连接或形成重试风暴。Outbox 的数据库租约用于多实例 Relay 协调，不能用 Redis 分布式锁替代。

事件建议使用版本化信封：

```json
{
  "event_id": "uuid",
  "event_type": "evaluation.requested",
  "occurred_at": "2026-11-17T10:00:00Z",
  "aggregate_id": "submission-uuid",
  "aggregate_revision": "1",
  "schema_version": 1,
  "trace_id": "trace-id",
  "payload": {
    "submission_id": "submission-uuid",
    "evaluation_task_id": "task-uuid",
    "content_version": "content-version-uuid",
    "batch_id": null
  }
}
```

上述只是示意，完整字段、类型和 `additionalProperties` 规则以根目录 `docs/事件契约.json` 为准。RabbitMQ 的 message ID 设置为 `event_id`，correlation ID 可设置为 `trace_id`；不要把敏感源代码或认证凭证放在消息头或消息体中。

发布失败时采用指数退避加随机抖动；到达发布重试阈值后标记 `DEAD` 并告警。Dead outbox 事件需要有人工重放操作，重放复用同一事件或显式创建新事件，并记录操作者、理由和时间。

## 8. Worker 消费、领取与 fencing

消费者流程：

1. 接收消息并按 `docs/事件契约.json` 校验事件版本和必要字段。
2. 在消费方本地事务登记 `(consumer_name, event_id)` Inbox。
3. 核验 `submission_id`、`evaluation_task_id`、`content_version` 和 `batch_id` 与已持久化事实一致。
4. 若事件已处理，幂等忽略；不得创建第二个 submission 或 evaluation task。
5. 持久化消费处理结果后手动 ACK RabbitMQ 消息。

消费者关闭 auto-ack。Inbox 和业务事实核验提交之前不能 ACK，否则事件可能被确认但未留下消费记录；提交后、ACK 前崩溃会导致重投，由 Inbox 唯一键去重。数据库暂时不可用时不要 ACK；可有限次 NACK/requeue，但不能立即无限 requeue 形成热循环。传输层失败使用有界延迟重试，超过次数后进入 DLQ；评测执行重试由持久任务状态机管理。

任务执行器使用原子领取。可以使用数据库行锁 `FOR UPDATE SKIP LOCKED`，或条件更新/CAS；不同数据库的支持和语法须由目标版本验证。领取成功时生成随机 `lease_token`，设置 `lease_owner`、`lease_until` 和 `heartbeat_at`。

每次续租、完成和失败回写都必须带 `task_id + lease_token` 条件。旧租约 Worker 即使暂停后恢复，也不能用过期 token 覆盖新 Worker 的结果。这是 fencing；只有 `lease_until` 而没有 token，不能防止过期 Worker迟到写入。

租约长度应覆盖正常心跳间隔并留有网络抖动空间。执行任务需定期心跳；租约超时扫描器回收失联任务。领取事务只负责抢占和状态更新，事务提交后才启动沙箱。

## 9. 执行幂等、重试和死信

消息投递和任务调度采用 **at-least-once**。系统不宣称代码只会运行一次；目标是同一任务可重试，但结果写入、状态推进和副作用具备幂等性。

- 当前首个任务由 `evaluation_task.submission_id` 唯一约束防止重复创建；运行尝试应使用自己的 attempt ID 和 fencing token。
- 结果唯一键防止重复终态结果。
- Worker 完成回写必须匹配当前 `lease_token` 和 `RUNNING` 状态。
- 外部副作用（如统计、通知）应通过自身 outbox 或可去重事件处理。
- `attempt_count` 在执行正式启动时递增，而非每次 broker delivery 或领取时递增。

失败分类：

| 分类 | 示例 | 建议处理 |
| --- | --- | --- |
| 暂时基础设施故障 | 容器节点故障、对象存储超时 | 退避重试，计数并限制最大次数 |
| 用户代码结果 | 编译错误、运行超时、答案错误 | 作为评测结果终态，不按基础设施错误无限重跑 |
| 输入/系统错误 | 源对象缺失、摘要不一致、策略快照不存在 | 终止并告警/人工排查，不盲目重试 |
| Worker 丢失 | 心跳停止、租约过期 | 任务可被重新领取；新 token 使旧 Worker 无法提交 |

重试需要区分两层：RabbitMQ 投递重试只处理消息未能可靠落入 Worker Inbox/任务表等传输或基础设施故障；评测任务的执行重试由持久任务的可用时间、attempt 记录和状态机控制，避免每次评测重试都重新制造一条含糊的 broker 消息。两层都应有最大次数和有界退避。传输重试耗尽进入 RabbitMQ DLQ；基础设施执行重试耗尽且没有可信结果时，任务进入 `DEAD_LETTER`、submission 进入 `SYSTEM_FAILED` 并触发告警。查询接口读取这些持久状态；本阶段不发布 `evaluation.failed`。DLQ 需要查询、告警、修复后经审计重放的流程。对源代码错误不能自动无限重试。

重放 DLQ 消息前先排查消费者错误并确认 Inbox/任务状态。重放保留原始 `event_id` 才能被去重；需要重新执行评测时，应通过受控业务操作，而不是篡改或删除 Inbox 记录来绕过幂等。当前契约没有 `generation` 字段，若重评需要代次，必须先更新事件和数据契约。

## 10. Redis 的定位与使用规则

Redis 是可选的辅助组件，不进入提交事务到评测执行的可靠性主链路。推荐用途：

- 缓存可重建的作业只读配置、语言元数据或权限查询结果，设置 TTL，并定义配置变更后的失效/版本策略。
- 对用户、课程或 IP 做短窗口 API 限流，保护提交入口和对象存储校验资源。限流失败策略应按接口重要性定义；Redis 短暂不可用时不能让所有已接受的提交丢失。
- 缓存短期、非权威的查询结果，缓存未命中时回源 MySQL。

不建议把 Redis 用作提交次数计数器、幂等键唯一性判断、Submission/EvaluationTask 状态源、Outbox 替代品或评测任务队列。此类数据需要与 MySQL 事务、唯一约束和审计记录保持一致；缓存锁既不能代替数据库唯一约束，也不能解决数据库与消息代理的双写问题。当前架构已经使用 RabbitMQ，不要再用 Redis List、Pub/Sub 或 Streams 复制一套评测任务队列。

缓存数据应可丢弃并可重建。使用 cache-aside 时要考虑短 TTL、热点 key、击穿保护和失效策略；不要缓存对象存储签名 URL、长期授权结论或敏感源代码。生产 Redis 配置应启用网络隔离、认证/TLS（按环境）、内存上限与淘汰策略、持久化/高可用策略，并监控内存、命中率、延迟、连接数和淘汰量。若未来用 Redis 做分布式并发配额，必须明确它是有 TTL 的协调信号，并定义 Redis 故障时的降级规则；最终的提交上限仍由 MySQL 原子约束保证。

## 11. 结果事件与状态同步

根目录 `docs/事件契约.json` 是当前事件结构的唯一依据。本方案使用其中定义的 `result.accepted`，而不是另造 `EvaluationFinished.v1`。结果生产方先持久化评测结果，再通过 Outbox 发布事件；事件携带 `evaluation_task_id`、`evaluation_result_id`、`content_version`、`result_digest` 和 `batch_id` 等稳定引用。接收方通过 Inbox 去重，并核对任务、版本、摘要和批次事实后再接受结果。

提交状态由 Server 按 `SubmissionStatus` 状态机管理；评测任务和结果按各自的持久化事实管理。Worker 不直接写 Server 数据库。若未来 Server/Worker 使用独立数据库，结果接收方在自己的事务中接受 `result.accepted` 并更新所属记录；重复、延迟或旧版本事件不得覆盖更新事实。需要定期对账：已接收结果但提交状态未更新、提交长期 `QUEUED/RUNNING`、Outbox 发布卡住等，生成指标和告警。

## 12. 评测安全与资源隔离

任务系统只负责调度，不能把安全责任留给队列。每次评测应在一次性或强隔离的执行环境中运行：

- 非特权用户、只读基础镜像、临时工作目录和执行后清理。
- 限制 CPU、内存、进程数、磁盘、输出大小和最长运行时间。
- 默认禁止公网和访问云元数据；按评测需求配置有限网络。
- Worker 管理凭据与被评测进程隔离，绝不注入对象存储密钥或消息代理凭据。
- 源代码使用短期授权读取，落盘加密/访问控制，并设置明确删除期限。
- 日志脱敏，不记录完整密钥、签名 URL 或不必要的源代码片段。
- 沙箱节点和执行器镜像版本可追踪，任务结果记录执行环境版本。

## 13. 可观测性与运维

每条提交事件和任务日志应带 `trace_id`、`submission_id`、`evaluation_task_id`、`event_id` 和（Worker 执行阶段）`attempt_id`/`lease_owner`，禁止把这些 ID 当作授权凭证。

核心指标：

- Outbox pending 数量、最老事件年龄、发布失败/Dead 数量。
- Broker backlog、消费延迟、重复事件比例、消费错误率。
- RabbitMQ quorum queue 可用副本、Publisher Confirm 延迟/失败、不可路由消息数、DLQ 深度和最老消息年龄。
- Redis 可用性、延迟、内存使用、命中率、淘汰量和限流拒绝数；这些指标不得被误当作评测任务完成指标。
- `PENDING` / `RETRY_WAIT` / `RUNNING` 任务数量及最老年龄。
- 租约超时数、重试次数分布、终态失败率、评测耗时 P50/P95/P99。
- submission 从接受到排队、开始、完成的端到端延迟。
- 沙箱资源耗尽、启动失败和清理失败数。

告警应关注“任务卡住”和“积压持续增长”，并设置有界重试与人工处理手册。运维工具应支持按 submission/task/event 查询、查看脱敏错误、取消未运行任务、重放 Dead 事件，并记录审计日志。

## 14. 保留、隐私和容量

- Outbox 已发布事件按审计要求保留一段时间后归档/清理；不能在 Relay 仍可能重试时提前删除。
- Inbox 去重记录至少保留超过消息最大重放窗口；否则老消息重放可能创建重复任务。
- 评测结果和详细输出设置保留期限、大小上限和分层归档策略。
- 对象存储源代码按产品保留策略清理；清理逻辑不能删除仍被重试/重跑任务引用的对象。
- 高频状态表按查询模式建索引，定期检查慢查询、索引膨胀和历史数据增长。

## 15. 测试策略

### 单元测试

- 提交事务产生正确事件和状态，不重复生成任务。
- 同一事件重复消费不会重复创建任务。
- 只有当前租约 token 能续租、完成或失败任务。
- 重试可用时间、次数上限和失败分类符合策略。
- 非法状态转换不能覆盖已完成结果。

### 数据库集成测试

使用真实目标数据库版本验证唯一约束、事务回滚、抢占 SQL、`SKIP LOCKED`/CAS、租约超时回收和并发更新。Mockito 不能验证数据库锁语义。

### 端到端与故障注入

- Server 写入提交后立即退出，Outbox Relay 恢复后仍能发布。
- Relay 在代理确认后、标记已发布前崩溃，重复消息被 Inbox 去重。
- Worker 领取后崩溃，租约超时后另一 Worker 可以接手。
- 旧 Worker 在租约过期后迟到回写，被 fencing token 拒绝。
- 结果生产方完成持久化后、发布 `result.accepted` 前崩溃，Outbox 恢复发布。
- Server 暂时不可用时，结果事件保留并在恢复后更新提交。
- RabbitMQ confirm 丢失但 broker 已接收时 Relay 会重发，消费者不会重复创建或处理受理任务。
- Inbox 事务提交成功但 ACK 前崩溃时，重投消息由 Inbox 去重后 ACK。
- RabbitMQ 不可用时 Outbox 持续积压且提交事务仍不产生“已发布”假象；恢复后可继续投递。
- Redis 缓存清空或不可用时，系统从 MySQL 回源；提交计数和幂等语义不改变。
- 重试耗尽进入可观测终态，人工重放不会篡改历史代次。

## 16. 业务拆分与开发步骤

### 16.1 业务边界

| 模块 | 职责 | 主要数据 |
| --- | --- | --- |
| Server 提交业务 | 校验作业和上传对象、分配提交次数、处理提交幂等、创建提交 | `assignment`、`source_object`、提交计数器、`submission` |
| Server 事件发布 | 保证提交事务成功后，评测事件最终发布到 RabbitMQ | `outbox_event` |
| 评测事件消费 | 校验 `evaluation.requested`、Inbox 去重、核验已创建的任务事实 | `inbox_event` |
| Worker 评测执行 | 领取任务、运行隔离沙箱、保存执行结果和任务状态 | `evaluation_task`、`evaluation_result` |
| 评测结果回传 | 可靠通知 Server 并更新用户可见提交状态 | Worker Outbox、Server Inbox、`submission` |
| Redis 辅助能力 | 缓存可重建数据、入口限流等 | 不作为业务事实来源 |

按当前 M0-B 设计，Server 受理事务创建 `submission`、提交次数、首个 `evaluation_task` 和 Outbox；Server 的 `evaluation.infrastructure` 负责事件 schema、Inbox 去重和任务事实核验。后续 Worker 执行域拥有运行尝试和详细结果，不得重复创建受理任务或直接修改 Server 数据库。RabbitMQ 传递事件；Redis 只提供可丢弃的辅助能力。

### 16.2 开发顺序与验收条件

#### 步骤 1：确定事件契约和状态流转

以根目录 `docs/事件契约.json` 为唯一 JSON Schema 来源，按现有事件名定义 `evaluation.requested` 和 `result.accepted` 的字段、版本规则及消费语义。具体 DTO 放置和契约测试步骤见[提交评测事件契约开发指南](提交评测事件契约开发指南.md)。不要另造 `SubmissionAccepted.v1` / `EvaluationFinished.v1`。事件不携带源代码或凭证。

验收：Server 与 Worker 对事件字段、版本升级、状态转换和失败含义有一致定义；消费者不依赖临时口头约定。

#### 步骤 2：Server 事务写入 Outbox

在现有 `SubmissionCreator.createSubmission()` 事务中保存 `submission` 与 `outbox_event`。提交校验、源对象验证、提交次数分配和幂等仍由 Server 负责。明确新提交采用 `QUEUED` 还是 `ACCEPTED`，并固定其语义。

验收：提交插入失败时 Outbox 回滚；Outbox 插入失败时提交和提交次数也回滚；重复提交不会生成重复逻辑事件。

#### 步骤 3：配置 RabbitMQ 拓扑

建立 durable exchange、routing key、Worker queue、DLX/DLQ 和必要的队列上限/告警。生产环境采用持久消息及满足可用性要求的队列类型。拓扑配置应可重复部署，账号遵循最小权限。

验收：RabbitMQ 重启后未确认的持久消息可恢复；不可路由消息和 DLQ 可观测。

#### 步骤 4：实现 Outbox Relay

Relay 使用数据库租约领取 Outbox 事件，在事务外发布到 RabbitMQ；启用 Publisher Confirms 和不可路由检查。只在确认发布后标记 `PUBLISHED`；超时视为结果不确定并允许以相同 `event_id` 重发。

验收：模拟 RabbitMQ 已接收但 Relay 未标记成功，重启后 Relay 可重发，消费者不产生重复副作用。

#### 步骤 5：实现评测事件消费和 Inbox 去重

按 `docs/m0-b/03-代码实现指南.md` 在 Server `evaluation.infrastructure` 实现事件 DTO、schema 校验、Inbox 和任务事实核验。Worker 执行端后续按同一 JSON Schema 消费事件，但不能重复创建 Server 受理事务已经写入的 `evaluation_task`。事务提交后再手动 ACK RabbitMQ；关闭 auto-ack。

验收：重复投递不重复产生副作用；消费记录与核验结果提交后、ACK 前崩溃时，重投事件由 Inbox 去重；数据库暂不可用时消息不会被提前 ACK。

#### 步骤 6：实现任务领取、租约和重试

Worker 以原子领取方式调度 `PENDING` 任务，维护租约、心跳和 fencing token；增加超时回收、失败分类、退避和最大尝试次数。RabbitMQ 投递重试只处理消息接收故障；评测执行重试由 `evaluation_task` 自己管理。

验收：并发 Worker 不会同时持有同一个有效任务；租约过期后的旧 Worker 不能提交结果；超过重试上限后任务进入明确终态并告警。

#### 步骤 7：实现评测结果和隔离执行

建立 `evaluation_result` 并接入隔离执行器。编译和运行用户代码不能放在 Server 请求线程、数据库事务或具有管理凭据的普通 Worker 进程中。区分用户代码结果与基础设施故障：编译错误、答案错误通常是评测终态，基础设施故障才重试。

验收：CPU、内存、进程数、磁盘、输出和运行时长受限；任务结束后沙箱和临时文件得到清理。

#### 步骤 8：可靠回传评测结果

结果生产方在本地事务中保存结果、任务终态和结果 Outbox；通过 RabbitMQ 发布契约中的 `result.accepted`。接收方使用 Inbox 去重，并按任务 ID、内容版本、摘要和批次引用核验结果。Worker 不直接写 Server 数据库。

验收：重复、延迟或暂时无法处理的结果事件可恢复；非法或过期状态事件不能覆盖更新状态；任务终态与结果唯一约束有效。

#### 步骤 9：按需引入 Redis 和生产治理

只有在有明确需求时引入 Redis 缓存或限流。缓存未命中回源 MySQL；Redis 清空或暂时不可用不改变提交次数、幂等和评测任务语义。之后补齐积压告警、对账、DLQ 审计重放、数据保留、容量管理和故障演练。

验收：Redis 故障时关键业务仍以 MySQL 为准；RabbitMQ 故障恢复后 Outbox 可继续投递；积压和卡住任务能被指标与告警发现。

Redis 不应成为项目初期的阻塞依赖；当前最合适的第一步是确定事件契约，然后实现 Server 的 Outbox 事务写入。完成可靠入队后再开发 Worker 消费和执行，避免同时调试提交、消息投递和沙箱问题。

## 17. 当前代码对应点

- `SubmissionCreator.createSubmission()` 是提交事务入口；对象存储内容校验应尽量移到短数据库事务之外，事务内重新验证会影响一致性的作业版本和计数器条件。
- 当前 `SubmissionStatus` 已有 `ACCEPTED`、`QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`REJECTED`；需要明确是在提交事务里直接创建 `QUEUED`，还是先 `ACCEPTED` 并由事件投递确认后转 `QUEUED`。
- 当前 `EvaluationTask` 是空 record；后续只承载业务状态与领域规则，不应混入 MyBatis Entity 或消息代理 SDK 类型。
- 当前 Service 有事务外捕获幂等重复键并查询胜出提交的逻辑；创建 `submission`、计数器和 Outbox 必须仍由同一个事务 Bean 负责。
- 数据库唯一约束应纳入正式迁移。生产启动不能依赖开发者手工执行一次 SQL。

## 18. 需要在实施前确定的决策

1. Worker 是否拥有独立数据库，还是与 Server 共用 MySQL？
2. RabbitMQ 将使用自建集群还是托管服务？由谁负责 quorum queue、备份、升级和 DLQ 运维？
3. 用户代码对象由 Worker 直读对象存储，还是通过受控下载接口获取？
4. 一个提交是否允许人工重跑；重跑是否生成新的 `generation` 和独立结果？
5. 编译错误、运行超时、答案错误分别如何映射到用户可见状态与结果？
6. 评测结果、源代码、详细日志的权限、保留期限和删除责任是什么？

Redis 的具体用途（缓存、限流或其他）应按产品需求逐项启用；不需要为了“使用 Redis”而把它加入评测主流程。核心接口保持为：提交事务写入稳定 Outbox 事件；Relay 使用 RabbitMQ Publisher Confirm 发布；Worker 将消息幂等落库后 ACK；任务执行用租约和 fencing；结果用唯一键和版本化事件回传。这样 RabbitMQ 负责可靠传递，MySQL 负责业务事实，Redis 负责可丢弃的辅助加速。
