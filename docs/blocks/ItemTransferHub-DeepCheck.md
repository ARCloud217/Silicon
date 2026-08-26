# 每轮修改结束深度检查再启动 — 固化流程（ItemTransferHub）

> 适用于 Silicon `ItemTransferHub` 相关修改。未通过不得执行 `run-hotreload.bat` 启动。

## 触发时机
每次修改 `src/silicon/world/blocks/distribution/ItemTransferHub*.java` 或 `build.gradle` 后、`deploy` 之前，以及 `deploy` 成功后、覆盖 `data/mods` 并重启之前。

## 固化步骤（按序执行，任一项 ❌ FAIL 即阻断启动）

| 步骤 | 检查项 | 锚点（当前代码） |
|------|--------|------------------|
| S1 | isFactory 委托 HubRouting | `HubRouting.isFactory(b)` |
| S2 | isFactory 含重构工厂 | HubRouting：`Reconstructor.ReconstructorBuild` |
| S3 | 白名单无「有物品栏即连」泛化 | HubRouting 不含 `if (other.items != null) return true` |
| S4 | 推送输入料保护门 | `producer.acceptItem(producer, item)` |
| S5 | 炮台伤害优先 | `ammoTypes.get(b).damage` |
| S6 | push 堵线触发 | `blocked = false` |
| S7 | 核心 75% 门控 | `coreHasRoom = cur < cap * surplusPushAt` |
| S8 | 越界防护 | `item.id >= consumer.items.length()` |
| S9 | 电力门控 | `power == null || power.status <= 0` |
| S10 | 调度节流 | `timer(0, 10)` |
| S11 | chargeOne 单跳计费 | `private void chargeOne(` |
| S12 | 途经计数延迟并入 | `transferCount += transferCountNext` |
| S13 | 存档序列化 v1 | `write.i(network.id)` + `revision < 1` |
| S14 | 核心满回退仓库跨网 BFS | `寻找其它中枢直连的仓库` |
| S15 | 加载期防误删链接 | `world.isGenerating()` |
| S16 | BFS 池化复用 | `bfsInit` |

## 自动化
`powershell -ExecutionPolicy Bypass -File scripts/hub-deep-check.ps1`
返回 16/16 PASS 且 BUILD 成功才允许覆盖游戏模组目录并重启。

## 人工复核
- 放置预览：拖中枢幽灵是否见淡蓝灰细线 + 方框；传送带等纯物流方块不出现可连提示
- 工厂供料：仓库有货时工厂被拉至容量；**重构工厂同样被供料**
- 核心满回退：核心对应物品全满时溢出流向仓库（含跨中枢连接的仓库）
- 跨枢统计：中转枢纽的传输速率与耗电均有读数，耗电 ≈ 10 × 该枢经手速率
- 炮台：多弹种时优先高 damage
