# 每轮修改结束深度检查再启动 — 固化流程（ItemTransferHub）

> 适用于 Silicon `ItemTransferHub` 相关修改。未通过不得执行 `run-hotreload.bat` 启动。

## 触发时机
每次修改 `src/silicon/world/blocks/distribution/ItemTransferHub*.java` 或 `build.gradle` 后、`deploy` 之前，以及 `deploy` 成功后、覆盖 `data/mods` 并重启之前。

## 固化步骤（按序执行，任一项 ❌ FAIL 即阻断启动）

| 步骤 | 检查项 | 方法 | 通过标准 |
|------|--------|------|----------|
| S1 | isFactory 完整 | `Select-String isFactory` 含 Drill/GenericCrafter/MineConverter/ItemTurret | 4类齐全 |
| S2 | isProducer / isPushProducer | 同上 | Drill+GenericCrafter+MineConverter |
| S3 | pullOnDemand 区分工厂/仓储 | 含 isFactoryConsumer/isStorageConsumer | 2分支 |
| S4 | 仓储阈值 0.9*capacity | 含 `0.9f` | 存在 |
| S5 | findNearestSupplier 任意有货可供 | 不含 `if (!isProducer(b)) continue` 的局部 | 已放宽 |
| S6 | 炮台伤害优先 | 含 `candidates.sort` + `ammoTypes.get(b).damage` | 存在 |
| S7 | hasPendingDemand 同判据 | 含该方法且含 isFactory/isStorageConsumer | 存在 |
| S8 | updateTile 门控 | 含 `boolean hasDemand = hasPendingDemand()` + `!pulled && !hasDemand` | 存在 |
| S9 | push 堵线 blocked | 含 blocked 循环 | 存在 |
| S10 | 核心满门控 | 含 `core.items.get(item) >= core.block.itemCapacity` | 存在 |
| S11 | item.id 越界防护 | 含 `item.id >= consumer.items.length()` | 存在 |
| S12 | 电力门控 | 含 `power == null || power.status` | 存在 |
| S13 | 经由计费 chargePath/bfsPath | 含 chargePath | 存在 |
| S14 | BFS 复用池 bfsInit | 含 bfsVisited.clear | 存在 |
| S15 | 部署一致性 | `build/libs` 与 `data/mods` 长度一致 | 一致 |
| S16 | 编译 | `gradlew deploy --no-daemon` EXIT:0 | 成功 |

## 自动化
`powershell -ExecutionPolicy Bypass -File scripts/hub-deep-check.ps1`
返回 14/14 PASS 且 BUILD 成功才允许 `Copy-Item build/libs -> data/mods` + `Start-Process run-hotreload.bat`。

## 人工复核
- 放置预览：拖 ItemTransferHub 幽灵是否见淡绿细实线 + 方框
- 工厂供料：仓库有货时工厂是否被拉至 MaximumAccepted
- 仓库推核心：仓库>=90% 且核心未满是否推
- 炮台：多弹种时优先高 damage
