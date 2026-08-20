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

物品传输中枢网络管理器，每个 ItemTransferHubBuild 持有一个 network 实例，用于管理网络 ID 和拓扑设置。

### 网络 ID

每个网络实例有一个唯一递增的 `id`，用于 BFS 遍历时去重和 draw 去重。

### HubData 内部类

存储每个 hub 的本地拓扑数据：

| 字段 | 类型 | 说明 |
|------|------|------|
| `buildings` | Seq\<Building\> | 直接连接的非 hub 建筑 |
| `hubs` | Seq\<ItemTransferHubBuild\> | 直接连接的其他 hub 建筑 |

HubData 由 `rebuildData()` 在链接变更时构建，仅反映直接邻居，不含远程拓扑。

### 网络级开关

| 开关 | 默认值 | 说明 |
|------|--------|------|
| `enableDemandPull` | true | 按需拉取模式 |
| `enableSurplusPush` | true | 满产推送模式 |

## 版本历史

| 版本 | 变更 |
|------|------|
| a0.8.1 | 初始创建 |
| a0.8.2.0 | HubData.update() 通用化，添加网络开关 |
| a0.8.5.0 | 删除未使用的 Path/cache 死代码、修正合并策略文档 |
| a0.8.6.1 | 移除死代码（merge/remove/rebuilds/updateBefore/update/needs/costs），简化为纯数据容器 |
