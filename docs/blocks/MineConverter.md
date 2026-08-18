# MineConverter

## 基本信息

| 属性 | 值 |
|------|----|
| 类名 | `MineConverter` |
| 父类 | `FrameBlock` |
| 分类 | Category.crafting |
| 尺寸 | 3x3 |
| 血量 | 默认 |

## 合成配方

| 材料 | 数量 |
|------|------|
| Graphite | 200 |
| Silicon | 250 |
| Thorium | 250 |
| Plastanium | 100 |

## Block 属性

- `hasItems`: true
- `hasPower`: true
- `consumesPower`: true（200/60）
- `outputsPower`: false
- `conductivePower`: false
- `update`: true
- `solid`: true
- `configurable`: true
- `sync`: true
- `drawArrow`: false
- `saveConfig`: true

## 机制说明

### 核心机制

矿物转换器，将世界矿物稀有度转化为生产价值。通过消耗矿物来生产选定的输出物品。

### 特殊行为

1. **世界成本计算**: `countWorldCosts()` 扫描整个世界地图，计算每种矿物的稀有度成本
2. **消耗阶段**: 选择库存中最丰富的矿物，每周期消耗1单位，转换为 `mineValue`
3. **制作阶段**: 将 `mineValue` 转换为 `craftValue`，达到目标成本后产出1个输出物品
4. **稀有度缩放**: 稀有矿物成本更低（更容易产出）

### 配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `craftTime` | float | 60 | 制作一个输出物品的时间 |
| `consumeTime` | float | 60 | 消耗一个输入物品的时间 |
| `consumptionMultiples` | float | 0.1 | 制作成本附加百分比 |
| `scaled` | TreeMap | - | 稀有度缩放映射 |

## 电力系统

- **消耗方式**: `consumePower(200/60)` 静态消耗

## 物品处理

- **输入**: 从玩家配置选择的矿物
- **输出**: 玩家配置的输出物品
- **拒绝**: 当前选中的制作物品和其他MineConverter的物品

## 状态栏 (Bars)

1. **Consume Progress**: 显示消耗进度
2. **Craft Progress**: 显示制作进度（craftValue/cost比值）

## 配置

- `ItemSelection.buildTable()`: 选择输出物品

## 序列化

- 版本: 2
- 保存字段: mineValue, craftValue, consumeProgress, warmup, craft(物品ID), consume(物品ID)

## 版本历史

| 版本 | 变更 |
|------|------|
| a0.8.0 | 初始创建 |
