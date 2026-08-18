# ItemTransferHubNetwork

## 基本信息

| 属性 | 值 |
|------|----|
| 类名 | `ItemTransferHubNetwork` |
| 父类 | 无（独立工具类） |
| 分类 | - |
| 尺寸 | - |
| 血量 | - |

## 机制说明

### 核心机制

物品传输中枢网络管理器，负责管理多个hub之间的网络连接、拓扑重建和供需计算。

### 网络管理

1. **网络合并**: `merge()` 将两个网络合并，采用"大网络吸收小网络"策略
2. **网络重建**: `remove()` 移除hub后，使用DFS重建连通分量
3. **版本控制**: `version` 字段在拓扑变更时递增，用于缓存失效

### HubData 内部类

存储每个hub的本地数据：

| 字段 | 类型 | 说明 |
|------|------|------|
| `buildings` | Seq<Building> | 相邻的非hub建筑 |
| `hubs` | Seq<ItemTransferHubBuild> | 相邻的hub建筑 |
| `needs` | int[] | 每种物品的需求量 |
| `costs` | int[] | 每种物品的供给量 |
| `cache` | Seq<Path> | 路径缓存 |

### 需求/供给计算 (update)

遍历所有linked buildings计算needs和costs：

1. **ItemTurret**: needs = 弹药容量 - 当前存储
2. **GenericCrafter**: 
   - needs = 每种消耗物品的(容量 - 当前)
   - costs = 输出物品满时的数量
3. **其他建筑**: 通用计算，检查acceptItem和容量

### 网络级开关

| 开关 | 默认值 | 说明 |
|------|--------|------|
| `enableDemandPull` | true | 按需拉取模式 |
| `enableSurplusPush` | true | 满产推送模式 |

### 网络合并策略

两个hub合并网络时，取较大网络的设置：
```java
if (this.hubs.size >= other.hubs.size) {
    result.enableDemandPull = this.enableDemandPull;
    result.enableSurplusPush = this.enableSurplusPush;
} else {
    result.enableDemandPull = other.enableDemandPull;
    result.enableSurplusPush = other.enableSurplusPush;
}
```

### 路径缓存 (Path)

| 字段 | 类型 | 说明 |
|------|------|------|
| `path` | Seq<ItemTransferHubBuild> | 路径上的hub序列 |
| `version` | int | 创建时的网络版本号 |

- `isValid()`: 检查缓存是否与当前网络版本匹配
- `length()`: 返回路径长度（hub跳数）

## 序列化

- 无独立序列化（由ItemTransferHub序列化network设置）

## 版本历史

| 版本 | 变更 |
|------|------|
| a0.8.1 | 初始创建 |
| a0.8.2.0 | HubData.update()通用化，添加网络开关 |
