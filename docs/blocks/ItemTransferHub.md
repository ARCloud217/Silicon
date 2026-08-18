# ItemTransferHub

## 基本信息

| 属性 | 值 |
|------|----|
| 类名 | `ItemTransferHub` |
| 父类 | `Block` |
| 分类 | Category.distribution |
| 尺寸 | 2x2 |
| 血量 | 默认 |

## 合成配方

| 材料 | 数量 |
|------|------|
| Copper | 40 |
| Lead | 20 |
| Metaglass | 10 |

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

## 机制说明

### 核心机制

物品传输中枢，是整个物品物流网络的核心节点。采用零缓冲设计，不存储任何物品，仅作为路由器协调建筑间的物品传输。

### 两种传输模式

#### 1. 按需拉取 (Demand-Pull)
- 每个hub检查相邻建筑需要什么物品
- 遍历网络中所有建筑，找到需要某物品的消费者
- BFS搜索全网最近的有该物品的供应商
- 执行proxy转移（直接操作双方inventory）

#### 2. 满产推送 (Surplus-Push)
- 检查生产建筑的输出是否已满（≥90%容量）
- BFS搜索全网最近的核心（CoreBlock）
- 将多余物品推送到核心

### 网络级开关

两个模式可独立启用/关闭，设置存储在 `ItemTransferHubNetwork` 上，全网共享：
- `enableDemandPull`: 按需拉取（默认true）
- `enableSurplusPush`: 满产推送（默认true）

玩家可通过右键点击hub切换这两个开关。

### Proxy转移机制

零缓冲的核心实现 - 不经过hub中转，直接操作双方inventory：
```java
consumer.handleItem(supplier, item);  // 交付
supplier.items.remove(item, 1);       // 扣除
```

Mindustry的`acceptItem()`和`handleItem()`默认实现不检查source邻接关系，因此proxy转移完全可行。

### BFS最近搜索

从本hub开始BFS遍历网络，找到第一个有目标物品的建筑即为最近：
- 本地（距离0）: 10电力/物品
- 1跳（距离1）: 20电力/物品
- 2跳（距离2）: 30电力/物品
- ...

距离越近耗电越少，BFS保证找到最近的供应商/核心。

### 拓扑更新

覆写 `onProximityUpdate()`，engine自动维护proximity列表：
- 扫描proximity，更新data.buildings / data.hubs
- 移除已移除的建筑（`!isValid()`）
- Hub连接变更时触发network.merge()

### 电力消耗

- 每个物品经过每个中枢消耗10电力
- 用 `consumePowerDynamic` 实现
- 无速度限制，能传多少传多少，电力自然限速
- 无电 → 停止工作

## 电力系统

- **消耗方式**: `consumePowerDynamic` 动态消耗
- **公式**: `powerConsumed = 10 * 物品数`
- **无速度限制**: 电力是唯一的自然限速

## 物品处理

- **输入**: 不接受任何物品（acceptItem返回false）
- **输出**: 通过proxy转移协调网络中的物品流动
- **缓冲**: 无（零缓冲设计）

## 配置

- **Pull按钮**: 切换按需拉取模式
- **Push按钮**: 切换满产推送模式

## 状态栏 (Bars)

- **Power**: 显示当前电力状态

## 序列化

- 保存字段: network.id, network.version, enableDemandPull, enableSurplusPush

## 版本历史

| 版本 | 变更 |
|------|------|
| a0.8.1 | 初始创建（框架代码） |
| a0.8.2.0 | 完善传输逻辑：proxy转移、BFS搜索、网络开关 |
