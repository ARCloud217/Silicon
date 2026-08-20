# 方块搜索（Block Search）实现文档

> 适用版本：Mindustry **v159.7**（steam build 159.7）
> 实现文件：`src/silicon/ui/BlockSearch.java`（独立模组中为 `src/blocksearch/ui/BlockSearch.java`，逻辑相同）

## 1. 概述

在原版**方块选单**（v159.7 起为 HUD 右下角的 `PlacementFragment`，不再是旧版的 `BlockSelectDialog` 弹窗）顶部注入一个搜索栏，支持：

- 跨**全部分类**搜索所有**合法方块**（与原版菜单判定规则一致）
- 内部名 / 显示名 / **中文拼音模糊搜索**（全拼、首字母、子序列）
- **历史记录**下拉（最多 4 条，持久化，可一键重复搜索）
- 不破坏原版交互：数字键选块、分类切换、鼠标中键取色、悬停信息等均保持原样

## 2. 整体集成方式

游戏不提供任何"向方块选单添加控件"的扩展点，因此采用**反射 + UI 树重组 + 自愈监听**的方案：

1. 通过公开入口 `Vars.ui.hudfrag.blockfrag` 拿到 `PlacementFragment` 实例；
2. 用**反射**访问其私有字段（`blockCatTable`、`blockTable` 等）；
3. 把搜索栏插入原版表格的最上方（重排 `blockCatTable` 的子表格）；
4. 由于原版会在世界加载 / 科技解锁时**整体重建**选单 UI，注册事件监听 + 每帧自愈检查，保证搜索栏始终存在。

## 3. 实现逻辑详解

### 3.1 初始化 `BlockSearch.init()`

在模组 `init()` 中调用，做三件事：

```java
// ① 缓存反射 Field（避免每帧查找）
blockCatTableF   = PlacementFragment.class.getDeclaredField("blockCatTable"); // 方块格+分类按钮所在表格
blockTableF      = PlacementFragment.class.getDeclaredField("blockTable");    // 方块格子表格（搜索结果渲染处）
blockPaneF       = PlacementFragment.class.getDeclaredField("blockPane");     // 格子滚动面板
togglerF         = PlacementFragment.class.getDeclaredField("toggler");       // 铺满屏幕的容器（用于全局点击监听）
selectedBlocksF  = PlacementFragment.class.getDeclaredField("selectedBlocks");// 每个分类记住的选中方块
menuHoverBlockF  = PlacementFragment.class.getDeclaredField("menuHoverBlock");// 菜单内悬停的方块

// ② 原版重建选单的时机：世界加载、解锁新方块 → 重建后重新注入（双重 post 保证顺序）
Events.on(EventType.WorldLoadEvent.class, e -> Core.app.post(() -> Core.app.post(BlockSearch::inject)));
Events.on(EventType.UnlockEvent.class, e -> {
    if(e.content instanceof Block) Core.app.post(() -> Core.app.post(BlockSearch::inject));
});

// ③ 每帧自愈 + 被原版覆盖后的过滤网格恢复
Events.run(Trigger.update, BlockSearch::update);
```

### 3.2 自愈机制（关键）`update()`

原版 `PlacementFragment.rebuild()` 会 `remove()` 整个 `toggler` 并全新 `build()`。此时旧搜索栏虽然还挂在旧 `blockCatTable` 上（`parent != null`），但已**脱离舞台**。单纯判断 `parent == null` 会误以为"搜索栏还在"，导致搜索栏永久消失（早期 bug）。

正确判断：**`searchRow.getScene() == null`**（arc 的 `Element.getScene()` 返回所在舞台，脱离舞台即为 null）：

```java
static void update(){
    PlacementFragment frag = fragment();
    if(frag == null) return;

    // 孤儿检测：原版重建后旧搜索栏脱离舞台 → 向新 UI 重新注入
    if(searchRow == null || searchRow.getScene() == null){
        inject();
        return;
    }

    // 若正在搜索，而原版重建把过滤网格覆盖了（如热键触发了 rebuildCategory），重新应用过滤
    if(searching && field != null && !field.getText().trim().isEmpty()){
        Table blockTable = getField(blockTableF, frag);
        if(blockTable != null && blockTable.find(resultName) == null){
            applyFilter(field.getText());
        }
    }
}
```

### 3.3 UI 注入 `inject()`

原版 `blockCatTable` 布局为一行两列：`[blocksSelect（方块网格区） | categories（分类按钮列）]`。

注入时先把这 2 个子表格取出，清空 `blockCatTable`，再按新顺序放回：

```java
Table blocksSelect = (Table)blockCatTable.getChildren().get(0);
Table categories   = (Table)blockCatTable.getChildren().get(1);
blockCatTable.clearChildren();
blockCatTable.add(searchRow).colspan(2).growX().row();  // 搜索栏独占首行，跨两列
blockCatTable.add(blocksSelect).fillY().bottom().touchable(Touchable.enabled);
blockCatTable.add(categories).fillY().bottom().touchable(Touchable.enabled);
```

搜索栏结构（两行）：

```
[🔍] [输入框(growX)] [✕清除] [▾历史]      ← 第一行
[历史记录下拉列表（Tex.pane2 面板）]        ← 第二行，默认空表（0 高度 = 视觉空白）
```

要点：
- 历史列表是**空表格时高度为 0**，无需 `visible()` 切换（arc Table 布局不因不可见而收缩，空表格才真正不占空间）；
- 在 `toggler`（铺满屏幕的表格）上挂一个 `ClickListener`：点击搜索栏以外的任何地方 → 关闭历史列表 + 释放输入框键盘焦点，保证原版数字键选块热键不受影响；
- `toggler` 每次重建都是新对象，用 `lastToggler` 去重，避免重复挂监听。

### 3.4 搜索流程

```
输入变化 onChanged(text)
├── 空文本  → restore()（恢复原版分类网格）
└── 非空    → closeHistory()（收起历史列表）
           → applyFilter(text)（渲染搜索结果）
```

`applyFilter()` 核心：

1. **查询归一化**：`trim().replaceAll(" +", " ").toLowerCase()`（与官方编辑器搜索一致，压缩连续空格）；
2. **候选收集**：遍历 `Vars.content.blocks()`，过滤条件与原版菜单完全一致：

   ```java
   if(!block.isVisible() || !unlocked(block)) continue;
   if(matches(block, q)) results.add(block);
   ```

   其中 `unlocked()` 复刻原版 `PlacementFragment.unlocked()`（该方法是包私有，无法直接调用）：

   ```java
   return block.unlockedNowHost() && block.placeablePlayer &&
          block.environmentBuildable() && block.supportsEnv(state.rules.env);
   ```

3. **排序**：分类顺序 → 可放置优先 → 显示名排序；
4. **渲染**：清空原版 `blockTable`，按 4 列铺搜索结果按钮（46px、`Styles.selecti`、与原版相同的按钮组、成本染色、悬停联动 `menuHoverBlock`、置顶滚动）。按钮命名 `silicon-search-result`，供自愈检查使用；
5. 无结果时显示一行提示文案（`silicon.blocksearch.noresults`）。

### 3.5 匹配与拼音模糊搜索

```java
static boolean matches(Block block, String q){
    if(block.name.toLowerCase().contains(q)) return true;        // 内部名（英文）
    if(block.localizedName.toLowerCase().contains(q)) return true; // 显示名（本地化语言）

    String[] py = pinyinOf(block); // [全拼, 首字母]
    if(py != null){
        if(py[0].contains(q) || py[1].contains(q)) return true;   // 直接包含
        if(q.length() >= 2 && (isSubsequence(q, py[0]) || isSubsequence(q, py[1])))
            return true;                                          // 子序列模糊（如 jzq → 建造器）
    }
    return false;
}
```

示例（中文界面下）：

| 输入 | 命中依据 | 示例 |
|---|---|---|
| `copper` | 内部名 | 铜 |
| `电` / `drill` | 显示名/内部名 | 机械钻头等 |
| `dian` | 全拼包含 | 电力相关 |
| `dy` | 首字母包含 | 电厂（dian chang → dc…） |
| `jzq` | 首字母子序列 | 加载器（jia zai qi → jzq） |

**拼音数据**：

- 资源文件 `/pinyin.txt`（jar 根目录，约 44,435 行，每行 `HEX pinyin`），来源 mozillazg/pinyin-data，已去声调、ü→v、多音字取首读音；
- 首次使用时懒加载解析为 `String[0x10000]`（BMP 字符直接按下标索引，无装箱开销）；
- 每个方块的 `[全拼, 首字母]` 结果用 `ObjectMap<Block, String[]>` 缓存，避免每次按键重复转换。

### 3.6 历史记录（重复搜索）

- **持久化**：`Core.settings.getJson / putJson`，键 `silicon-blocksearch-history`（独立模组为 `blocksearch-history`），`Seq<String>`，最多 4 条、去重、新条目置顶；
- **记录时机**：在搜索结果上**点击选中方块**时记录当前查询词（而不是每敲一个字符都记录）；
- **展示**：点击搜索栏的 `▾` 按钮弹出下拉列表（`Tex.pane2` 面板 + `Styles.flatBordert` 通栏按钮，避免小按钮重叠、难点击）；
- **交互细节**：
  - 搜索框**为空时不展开任何内容**（`toggleHistory()` 先检查文本）；
  - 悬停 `▾` 按钮显示 tooltip「历史记录」；
  - 点击某条历史 → 填入文本并**显式调用 `applyFilter()`**（arc 的 `TextField.setText()` **不会**触发 change 监听，不能依赖它）；
  - 点击外部 / 开始输入 → 自动收起。

### 3.7 选中与恢复

```java
static void select(PlacementFragment frag, Block block){
    control.input.block = control.input.block == block ? null : block; // 与点击原版格子行为一致
    selectedBlocks.put(block.category, control.input.block);           // 记住该分类的选中
    frag.currentCategory = block.category;                             // 切到该方块所属分类
    addHistory(当前查询词);
    clearSearch();  // 清空输入 + 释放焦点 + restore()
}

static void restore(){
    if(!searching) return;
    searching = false;
    Core.app.post(() -> ui.hudfrag.blockfrag.rebuild()); // 下一帧调用原版公开方法 rebuild()
}
```

`rebuild()` 是 `PlacementFragment` 的**公开**方法，直接调用即可恢复原版分类网格（当前分类 + 高亮选中方块），随后自愈机制（3.2）自动把搜索栏重新注入到新 UI 上。

### 3.8 与键盘热键的共存

- 输入框持有键盘焦点时，原版 `gridUpdate()` 会因 `Core.scene.hasKeyboard()` 直接返回——数字键进入输入框而不是触发选块，符合预期；
- 点击搜索栏以外区域时，`toggler` 上的监听器**释放键盘焦点**，原版热键立即恢复可用；
- 搜索结果按钮与原版格子按钮共用 `ButtonGroup`，选中高亮逻辑一致。

## 4. 版本适配踩坑记录（v159.7）

| 问题 | 结论 |
|---|---|
| 方块选单不是旧版 `BlockSelectDialog` | v159.7 是 HUD 内的 `PlacementFragment`（`ui.hudfrag.blockfrag`） |
| `Category` 不在 `mindustry.world.meta` | 在 `mindustry.type` |
| `TextureRegionDrawable` 不在 `arc.graphics.g2d` | 在 `arc.scene.style` |
| `Element.name` 不是方法 | 是**公开字段**（`table.name = "..."`；方法 `name(String)` 只存在于 `Cell` 上） |
| `ClickListener.touchDown` 签名 | 5 参数：`(InputEvent, float, float, int pointer, KeyCode)`，返回 `boolean` |
| `PlacementFragment.unlocked()` 是包私有 | 在模组内复刻其判定逻辑 |
| arc 无 `StringMap` | 用 `String[0x10000]` 按下标索引拼音 |
| arc `Table` 无 `insert()` / 无 `wrap()` | 用 `clearChildren()` + 重新 `add()` 重排；历史列表用下拉面板而非横向 chips |
| 判断 UI 是否还在舞台 | `Element.getScene()`（不是 libGDX 的 `getStage()`） |
| `TextField.setText()` 不触发 change 监听 | 历史条目点击需显式调用 `applyFilter()` |
| **模组资源必须在 jar 根目录** | `Mods.buildFiles()` 用 `mod.root.child("bundles")` 读取，`assets/` 前缀无效；`icon.png` 也必须在根目录（否则 `iconTexture == null` 启动崩溃） |
| `build.gradle` 中 `from("assets/")` 已包含 icon.png | 不要再在 `from(projectDir)` 里重复 `include "icon.png"`（CI 会报 duplicate entry） |

## 5. 打包与构建

### 5.1 jar 结构（游戏要求的正确结构）

```
Silicon.jar / BlockSearch.jar
├── mod.hjson          （模组元数据）
├── icon.png           （模组图标，根目录！）
├── pinyin.txt         （拼音表，根目录资源，getResourceAsStream("/pinyin.txt") 读取）
├── bundles/           （语言包，根目录！）
├── sprites/           （方块贴图，根目录！）
└── silicon/…          （编译后的类）
```

### 5.2 构建方式

- **一键脚本**：`C:\dsh\build-mods.ps1` —— javac（Java 17 目标）编译 + 按上述结构打包，输出到 `C:\dsh\new\`；
- **gradle**：`gradlew jar` —— `build.gradle` 中 `from("assets/"){ include "**" }` 会自动把 assets 内容平铺到 jar 根目录（Gradle `from(目录)` 不带目录前缀）。

## 6. 已知限制

1. **反射耦合**：字段名与 v159.7 绑定，Mindustry 大版本更新若重命名字段，`init()` 会捕获异常并整体禁用搜索（不会崩溃）；
2. **历史记录仅在输入框非空时可展开**（设计如此：空白时不显示内容）；多音字取首读音，个别词条拼音匹配可能不理想；
3. 拼音表体积约 455KB，随 jar 分发。

## 7. 相关文件清单

| 文件 | 说明 |
|---|---|
| `src/silicon/ui/BlockSearch.java` | 全部实现逻辑（单文件，约 470 行） |
| `pinyin.txt` | 汉字→拼音表（构建时随 jar 打包） |
| `assets/bundles/bundle.properties` | 英文文案（hint / noresults / history / nohistory） |
| `assets/bundles/bundle_zh_CN.properties` | 中文文案 |
| `build.gradle` | jar 打包规则（含 pinyin.txt，不含重复 icon.png） |
| `C:\dsh\build-mods.ps1` | 手动打包脚本（输出到 `C:\dsh\new`） |
