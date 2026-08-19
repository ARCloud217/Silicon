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
- `conductivePower`: false
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
| 钻探消耗 | `Drill` | 钻头需要物品才能工作 |
| 纯存储 | `CoreBlock` | 核心，储存是主要功能 |
| 纯存储 | `StorageBlock` | 箱子/仓库，储存是主要功能 |

**拒绝连接**：传送带、路由器、桥接、炮塔、电力节点等所有其他类型。

### 点击连接（电力节点式）

支持手动点击连接/断开，参考 PowerNode 实现：

- **单击有效建筑** → 连接/断开切换（`config(Integer.class, pos)`）
- **双击自身（无连接时）** → 自动连接范围内所有有效建筑
- **双击自身（有连接时）** → 清除所有手动连接
- 手动连接存储在 `links`（IntSeq）中
- 手动连接的建筑会加入网络拓扑

### 连接验证

`linkValid()` 检查：
1. 不是同一建筑
2. 同一队伍
3. 是可连接的建筑类型（`shouldConnect()`）
4. 在连接范围内（`connectionRange * tilesize`）

### 网络检查

连接前通过 `isInSameNetwork()` BFS 检查目标是否已在同一网络中，已在则跳过。

## 传输模式

### 1. 按需拉取 (Demand-Pull)
- 检查网络中每个建筑的物品需求
- BFS搜索全网最近的有该物品的供应商
- 执行proxy转移（直接操作双方inventory）

### 2. 满产推送 (Surplus-Push)
- 检查生产建筑的输出是否已满（≥90%容量）
- BFS搜索全网最近的核心（CoreBlock）
- 将多余物品推送到核心

### 网络级开关

两个模式可独立启用/关闭：
- `enableDemandPull`: 按需拉取（默认true）
- `enableSurplusPush`: 满产推送（默认true）

配置UI中的按钮：
- "拉取" / "Pull": 切换按需拉取
- "推送" / "Push": 切换满产推送

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

## 电力消耗

- 每个物品经过每个中枢消耗10电力
- 无速度限制，电力是唯一的自然限速
- 无电 → 停止工作

## 绘制

### draw() - 常驻绘制
- 手动连接的建筑：蓝色实线（hub↔hub）/ 蓝色虚线（hub↔building）

### drawSelect() - 选中时绘制
- 蓝色虚线范围圈
- 自动扫描范围内的建筑：淡色方框
- 手动连接的建筑：强调色方框
- 已连接的中枢：蓝色方框

### drawConfigure() - 配置模式绘制
- 脉冲圆圈（自身）
- 范围圆圈
- 已链接建筑高亮（蓝色=已连接，强调色=可连接）

## 状态栏 (Bars)

- **health**: 生命值
- **silicon-hub-power**: 电力状态
- **silicon-hub-power-cost**: 每秒电力消耗

## 序列化

保存字段: network.id, network.version, enableDemandPull, enableSurplusPush, links

## 版本历史

| 版本 | 变更 |
|------|------|
| a0.8.1 | 初始创建（框架代码） |
| a0.8.2.0 | 完善传输逻辑：proxy转移、BFS搜索、网络开关 |
| a0.8.2.2 | 修复拓扑/可视化/国际化/按钮显示 |
| a0.8.2.3 | 改为电力节点式操作：自动扫描+点击连接+常驻连线绘制 |
| a0.8.3.0 | 连接过滤白名单（仅生产+存储）、网络内重复连接检查、电力节点式双击逻辑 |
