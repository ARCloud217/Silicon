# ItemTransferHub 检查清单（每轮结束后执行）

> 覆盖中枢全部已实现行为。任何一项失败即阻断"完成"结论。

## A. 逻辑正确性

### A1. 连接与放置
- [ ] 放置中枢：范围内（20格，**整体占位判定**——大建筑边缘入圈即可）未被服务的工厂/仓库/核心自动连入
- [ ] 核心旁容器（邻接核心的 StorageBlock）**不出现**在可连列表
- [ ] 已被其它中枢服务的建筑**不重复连接**（同网排除）
- [ ] 中枢↔中枢互联不受同网排除限制
- [ ] 长按拖线：拖动路径实时显示淡蓝灰虚线预览；松手后按 Point2[] 配置批量建链
- [ ] 双击自身：无链→全连；有链→清空

### A2. 拉取（工厂/炮台供料）
- [ ] 消费者排序：**炮台(0) > 工厂(1)**；仓储不参与拉取
- [ ] 同级按缺口比例降序（最饿先吃）
- [ ] 多源料工厂：同帧连续补多种输入（不提前 break）
- [ ] 供源三级：①仓库 ②核心 ③矿机/工厂产出
- [ ] 同类工厂输入料互斥：pass0 排除 isInputStockOfFactory；pass1 兜底防饿死
- [ ] 不自拉：supplier != consumer 全程过滤

### A3. 推送（溢出回流）
- [ ] 触发阈值：矿机/工厂任一输出 ≥ `surplusPushAt`(0.75)；仓储 ≥90%
- [ ] 目标优先级：**核心（全网 BFS，无视距离）→ 仓库兜底**
- [ ] 核心该物品满 / acceptItem=false → 自动改投仓库
- [ ] 仓储源：核心满时直接停推（防仓库间乒乓）
- [ ] 调度顺序：拉取 → 推送（同帧顺序执行，互不门控）

### A4. 电力消耗
- [ ] 单价：10 电力 / 件 / 经过的每个中枢（chargeBatch 整批一次计算）
- [ ] 直连同枢：10×件数；跨枢路径 n 枢：10×n×件数均摊到各枢
- [ ] 远端枢计费走 powerConsumedNext 延迟一帧，updateTile 帧首必须并入（powerConsumed = powerConsumedNext）
- [ ] 无电：拉取/推送全停，powerPerSecond 与 transferRate 归零

### A5. 统计口径一致性
- [ ] Power Cost = 10 × 路径枢数 × moved ÷ 秒 —— 与 Transfer Rate 严格 10:1×路径数 对应
- [ ] 两统计共用 timer(3,60) 窗口
- [ ] Transfer Rate 为 **10 秒滑动窗口平均**（rateWindowCounts 容量600桶）

## B. 边界与防护

| 场景 | 预期 |
|---|---|
| 供源中途被拆 | directTransfer 二次 isValid 校验拦截，下tick重试 |
| 收方满 | consumerSpace≤0 → moved=0 → false |
| 旧存档 items 数组短于当前物品表 | item.id >= length 跳过（防 IndexOutOfBounds）|
| 同名 mod 目录+jar 并存 | 加载冲突 → 只保留一种形态 |
| 核心旁容器 | shouldConnect=false，永不入网 |
| 断电恢复 | 延迟计费(powerConsumedNext)不清零，恢复后正常累计 |

## C. 视觉

| 元素 | 规格 |
|---|---|
| 常驻连线 Hub↔Hub | Pal.lightishGray 细实线 stroke(1f) |
| 常驻连线 Hub→建筑 | 同色细实线（不再虚线拉伸） |
| 放置预览线 | 同连线颜色（Pal.lightishGray + laserOpacity），非激光贴图 |
| 预览高亮 | 可连目标 Drawf.square(Pal.place) |
| 范围圈 | drawPlace: Pal.placing 圆；drawSelect: Pal.accent 虚线圈 |
| 透明度 | Renderer.laserOpacity 联动 |

## D. 性能红线

- [ ] BFS 复用池（bfsQueue/bfsDists/bfsVisited）每次用前 bfsInit() 清空
- [ ] 批量直转单次最多 10 件；chargeBatch 整批只跑一次 bfsPath（禁止逐件重算）
- [ ] buildingTree.intersect 替代双层 for 扫描（放置预览/自动连接）
- [ ] 无逐帧 new Seq 分配在热路径（consumers 序列允许，BFS 池必须复用）

## E. 回归锚点（改动后必测）

1. 单工厂+仓库：缺料被补、满仓被排空至核心
2. 双同类工厂+单一矿机：两厂轮流获得补给（缺口比均衡）
3. 炮台+工厂争抢同物品：炮台先得到弹药
4. 跨枢传输（A→B→C）：电力消耗 = 10 × 2 × 件数，两枢 Power Cost 均有读数
5. 断电：全部停转；来电：恢复
6. 核心对应物品全满：溢出流向仓库而非消失
7. 拆除中间中枢：两端各自 updateTopology 剔除陈旧链，网络分裂正常
