# ItemTransferHub

## 基本信息

| 属性 | 值 |
|------|----|
| 类名 | `ItemTransferHub` |
| 父类 | `Block` |
| 分类 | Category.distribution |
| 尺寸 | 3x3 |
| 连接范围 | 20格 |
| 最大连接数 | 50 |

## 合成配方

| 材料 | 数量 |
|------|------|
| Copper | 80 |
| Lead | 40 |
| Metaglass | 20 |
| Graphite | 30 |
| Silicon | 25 |
| Titanium | 15 |

## Block 属性

- `hasItems`: false（零缓冲，纯路由器）
- `hasPower`: true
- `consumesPower`: true
- `outputsPower`: false
- `conductivePower`: true
- `consumePower`: consumePowerDynamic（每次传输消耗10电力）
- `update`: true
- `solid`: true
- `configurable`: true
- `alwaysUnlocked`: true

## 连接机制

### 可连接的建筑类型（白名单）

`shouldConnect()` 只允许以下类型的建筑：

| 功能 | 具体类 | 说明 |
|------|--------|------|
| 合成消耗 | `GenericCrafter` | 合成器需要原料输入 |
| 矿物转化 | `MineConverter` | 矿物转换器需要输入矿物 |
| 钻探消耗 | `Drill` | 钻头需要物品才能工作 |
| 纯存储 | `CoreBlock` | 核心，储存是主要功能 |
| 纯存储 | `StorageBlock` | 箱子/仓库，储存是主要功能 |
| 弹药消耗 | `ItemTurret` | 炮塔需要弹药输入 |
| 物品传输 | `ItemTransferHub` | 中枢之间手动链接 |

### 点击连接（电力节点式）

完全模仿 PowerNode 的链接行为：

- **单击有效建筑** → 连接/断开切换（`config(Integer.class, pos)`）
- **双击自身（无连接时）** → 自动连接范围内所有有效建筑
- **双击自身（有连接时）** → 清除所有连接
- **单击无效建筑** → 退出配置模式
- **放置时** → 自动连接范围内所有有效建筑
- 手动连接存储在 `links`（IntSeq）中
- 手动连接的建筑会加入网络拓扑

### 连接验证

`linkValid()` 检查：
1. 不是同一建筑
2. 调用方是 ItemTransferHub（防止 ClassCastException）
3. 同一队伍
4. 是可连接的建筑类型（`shouldConnect()`）
5. 在连接范围内（`connectionRange * tilesize`）

## 传输模式

### 1. 按需拉取 (Demand-Pull)
- 检查网络中每个建筑的物品需求
- BFS搜索全网最近的有该物品的供应商
- 执行proxy转移（直接操作双方inventory）

### 2. 满产推送 (Surplus-Push)
推送三级优先级：
1. **工厂/炮台消费者**：需要该原料且未满时优先分流
2. **核心**：该物品存量低于 75% 阈值（`surplusPushAt`）时可推
3. **仓库兜底**：核心满/拒收/≥75% 时，跨全网 BFS（直连 + 跨中枢）找最近仓库强制入库
   - 矿机/工厂：任一输出达到 75% 容量即触发排空
   - 仓储作为推送源：单物品 ≥90% 容量才回运核心

### 网络级开关

两个模式始终启用（无 Pull/Push 切换按钮）：
- `enableDemandPull`: 按需拉取（默认true）
- `enableSurplusPush`: 满产推送（默认true）

## Proxy转移机制

零缓冲的核心实现 - 不经过hub中转，直接操作双方inventory：
```java
consumer.handleItem(supplier, item);  // 交付
supplier.items.remove(item, 1);       // 扣除
```

## BFS最近搜索

从本hub开始BFS遍历网络，找到第一个有目标物品的建筑即为最近：
- 本地（距离0）: 10电力/物品
- 1跳（距离1）: 20电力/物品
- ...

## 电力消耗（经由口径）

- 无空闲功耗：`consumePowerDynamic` 仅按实际传输计费
- 每个物品按 **BFS 最短路** 经过的每个中枢各 10 电力（`chargeBatch`：直连同枢 10，路径上每个中枢均 10）
- **显示口径 = 实际取电量**：电网欠载（status<1）时按满足率折算，满电时等于全额请求
- 禁用/断电 → 瞬时请求清零（无幻影需求）、拉取/推送停止、秒级统计归零
- 跨枢延迟计费/计数在每帧开头并入并清空，禁用期间不累积
- `conductivePower = true` → 可通过邻近电力源或电力节点供电

## 吞吐统计（运输速率）

- 统计口径 = **所有途经本枢的物品**：本枢发起的直连/跨网转移 + 其它枢纽发起、路过本枢的中转
- 远端中转件数通过 `transferCountNext` 延迟一帧并入，与延迟计费同帧同步
- 10 秒滑动窗口（600 tick）求平均，状态栏每 10 tick 刷新一次
- 满电时与耗电显示严格成比例：耗电/秒 ≈ 10 × 速率

## 绘制

### draw() - 常驻绘制
- `super.draw()` 先绘制方块贴图
- 仅在 `Renderer.laserOpacity > 0` 且非 payload 时绘制连线
- 绘制层级 `Draw.z(Layer.power)`（电力层，高于方块层）
- 只遍历 `links`（手动连接），不再有 `data.hubs` 自动发现连线
- Hub↔Hub：蓝色实线（边缘到边缘，`Lines.line`）
- Hub→Building：蓝色虚线（边缘到边缘，`Drawf.dashLine`）
- 去重：Hub对之间只画一次（`other.id >= id` 跳过）
- `linkValid` 验证每个链接有效性

### drawSelect() - 选中时绘制
- 蓝色虚线范围圈（电力节点式）

### drawConfigure() - 配置模式绘制
- 脉冲圆圈（自身）
- 范围圆圈
- 已链接建筑高亮（蓝色=已连接，强调色=可连接）

## 状态栏 (Bars)

- **health**: 生命值
- **silicon-hub-power**: 电力状态（缓冲区电量 / 缓冲区容量）
- **silicon-hub-power-cost**: 每秒实际取电量（如 `Power Cost: 10.0/s`）
- **silicon-hub-connections**: 连接数（当前连接数 / 最大连接数）
- **silicon-hub-transfer-rate**: 传输速率（含途经中转，10 秒平均件/秒）

## 序列化

- 保存字段: `network.id`(int) + `links`(short 数量 + int pos...)
- 格式版本 `version() = 1`
- 读取时 `revision < 1`（未序列化链接的旧构建存档）直接跳过自定义数据，避免错位解析损坏存档流
- 存档实体按长度分块读写，格式不匹配的影响被限制在单个建筑内
- 加载保护：`updateTopology()` 在 `world.isGenerating()` 期间不因"目标不存在"剔除链接
  （加载时邻居建筑可能尚未创建），加载完成后由周期刷新兜底清理失效链接

## 暂停白名单 UI

通过 Silicon 设置 → "管理暂停白名单" 按钮打开白名单管理对话框：
- 显示当前白名单玩家列表
- 支持输入玩家名添加
- 支持点击移除按钮删除
- 多人模式下通过 `pause-grant`/`pause-revoke` 包同步到服务器
- 也可通过聊天命令管理：`!pause grant/revoke/list`

## 版本历史

| 版本 | 变更 |
|------|------|
| a0.8.1 | 初始创建（框架代码） |
| a0.8.2.0 | 完善传输逻辑：proxy转移、BFS搜索、网络开关 |
| a0.8.2.2 | 修复拓扑/可视化/国际化/按钮显示 |
| a0.8.2.3 | 改为电力节点式操作：自动扫描+点击连接+常驻连线绘制 |
| a0.8.3.0 | 连接过滤白名单（仅生产+存储）、网络内重复连接检查、电力节点式双击逻辑 |
| a0.8.3.1 | 修复双击断连 bug、删除 Pull/Push 按钮（两模式始终启用）、修复状态栏 {0}、暂停描述 |
| a0.8.3.2 | 修复连线不可见（edge-to-edge + Layer.power）、修复 pullOnDemand 不传输物品、耗电量显示实际数值、添加暂停白名单 UI |
| a0.8.4.0 | 修复 hub-to-hub 连线不显示（draw 增加 data.hubs 遍历）、修复电力系统接入（conductivePower=true + consumePower(5f)） |
| a0.8.5.0 | 添加 MineConverter 到连接白名单、linkValid 添加 ClassCastException 保护、代码健壮性全面改进 |
| a0.8.5.1 | 修复 timers 数组大小不足导致的崩溃 |
| a0.8.5.2 | 完全模仿电力节点链接行为：hub-to-hub 手动链接、移除自动发现扫描、放置时自动连接、配置模式点击无效目标退出、修复白色连线（Draw.reset 问题）、添加连接数限制显示、添加电力缓冲（50f） |
| a0.8.6.0 | 合并 PR #1 MineConverter 多人同步修复 |
| a0.8.6.1 | 性能优化：BFS 队列/visited 池化为实例字段 + IntSet 替代 Seq，移除 HubData 死代码，修复 rebuildData 双向重建，添加传输速率状态栏 |
| a0.8.7.0 | 同步上游方块搜索功能 |
| a0.8.7.1 | 修复容器类拉取（getMaximumAccepted 替代 block.itemCapacity）、修复耗电不生效（移除被 Block.consume 吞掉的 consumePowerBuffered） |
| a0.11.6.0 | 四项修复：恢复连接存档序列化（v1 格式 + 加载期防误删）；耗电显示对齐实际取电（欠载折算/禁用断电清零/延迟计费不累积）；核心满回退仓库改为跨全网 BFS 查找；运输速率统计含途经中转件数（10s 真实滑动窗口） |