# Silicon Mod 开发笔记

> 本文件不提交到 Git，仅用于记录开发经验和注意事项。

---

## 1. 编译与部署流程

```bash
# 编译（仅桌面端）
bash gradlew jar

# 完整部署编译（桌面端 + Android 端，需要 Android SDK）
bash gradlew deploy

# 输出产物
build/libs/siliconDesktop.jar
build/libs/Silicon.jar（deploy 产物，包含桌面 + Android dex）
```

**注意**: 每次修改代码后必须编译验证，确保无编译错误。

### 开发流程

1. 修改代码
2. `bash gradlew jar` 验证编译通过
3. `bash gradlew deploy` 生成完整 jar（需要 Android SDK）
4. 检查 `build/libs/Silicon.jar` 存在且大小正常（>100KB）
5. 复制到 Mod 加载目录测试
6. 提交推送

### 版本号管理

版本号格式：`a<主>.<中>.<小>.<次>`（前缀 `a` 表示 alpha）

| 级别 | 名称 | 何时递增 | 示例 |
|------|------|----------|------|
| 主 | Major | 破坏性重构、大版本跳跃 | a0→a1 |
| 中 | Minor | 新增方块/物品/内容 | a0.8→a0.9 |
| 小 | Patch | 游戏逻辑/平衡性修改 | a0.8.1→a0.8.2 |
| 次 | Sub | 非游戏内容修改（重构、CI、文档、代码质量） | a0.8.2.0→a0.8.2.1 |

版本号定义在 `mod.hjson` 的 `version` 字段。

### 提交消息规范

格式：`[版本号] 类型: 描述`

示例：
```
[a0.8.2.0] feat: 完善物品传输中枢逻辑
[a0.8.2.1] docs: 添加建筑设计文档
[a0.8.1.0] chore: 更新版本系统为4级格式
```

---

## 2. PR/推送经验

### 仓库结构

| 仓库 | 地址 | 说明 |
|------|------|------|
| 上游 | `https://github.com/Xiaobei08/Silicon` | 原始仓库 |
| Fork | `https://github.com/Xiaobei09/Silicon` | 个人 fork |

### 上游默认分支

上游仓库的默认分支是 **`test`**，不是 `master`。创建 PR 时 `--base` 必须指定 `test`。

### 远程仓库配置

```bash
# 添加上游
git remote add upstream https://github.com/Xiaobei08/Silicon.git
git fetch upstream test

# origin 指向自己的 fork
git remote set-url origin https://github.com/Xiaobei09/Silicon.git
```

### Token 权限配置

Fine-grained Token 需要以下权限：
- **Contents**: Read and Write（推送分支）
- **Pull requests**: Read and Write（创建 PR）
- **Metadata**: Read-only（必须项）

Classic Token 直接勾选 `repo` scope 即可。

### 创建 PR 流程

```bash
# 1. 基于上游创建分支
git checkout -b fix/something upstream/test

# 2. cherry-pick 或修改代码
git cherry-pick <commit>

# 3. 推送到 fork
git push -u origin fix/something

# 4. 创建 PR（指定上游仓库 + 基础分支）
gh pr create --repo Xiaobei08/Silicon --head Xiaobei09:fix/something --base test --title "..." --body "..."
```

### 冲突解决

如果本地历史与上游 diverge，不要直接 rebase，而是：
1. 基于 `upstream/test` 创建新分支
2. 只 cherry-pick 需要的 commit
3. 手动解决冲突后推送

---

## 3. 常见坑与注意事项

### import 遗漏

Arc/Mindustry 的类需要手动 import，常见的有：
- `arc.struct.ObjectFloatMap`
- `arc.struct.Seq`
- `arc.util.io.Reads` / `arc.util.io.Writes`
- `mindustry.ui.dialogs.BaseDialog`

编译报错 `cannot find symbol` 时，先检查是否缺少 import。

### 二进制文件冲突

PNG 等二进制文件在 rebase/cherry-pick 时会产生 `CONFLICT (add/add)` 冲突，无法自动合并，需要手动选择保留哪个版本。

### build/.gradle 文件污染

cherry-pick 可能把 `build/` 和 `.gradle/` 目录带入提交，解决方法：

```bash
git reset HEAD~1 --soft
git reset HEAD -- build/ .gradle/
git commit -m "..."
```

### Git 凭据

当前环境的 Git 凭据是 `Xiaobei09`，不能直接 push 到 `Xiaobei08/Silicon`。必须通过 fork 推送。

### GH CLI 权限报错

`gh pr create` 报 `Resource not accessible by personal access token` 时：
- 检查 Token 权限是否包含 `Pull requests: Read and Write`
- Fine-grained Token 需要在 Repository permissions 中单独配置
- 或改用 Classic Token 的 `repo` scope

### CI d8 编译问题

- `--min-api` 必须为 **21**：Mindustry v159+ 的 `minSdkVersion` 为 21（Android 5.0），用 14 会导致 Android 端无法加载
- 必须加 `--no-desugaring`：CI 环境 build-tools 34.0.0 的 d8 版本（R8_8.2.2-dev）与 JDK 17 存在 `java.lang.Record` 类路径冲突，会导致 `CompilationFailedException`
- d8 命令必须检查退出码，否则错误被静默吞掉，CI 显示成功但产物损坏
- 对齐 Anuken 官方模板：https://github.com/Anuken/MindustryJavaModTemplate

---

## 4. Mindustry Mod API 笔记

### 设置面板

在 `init()` 中注册设置项：

```java
Events.on(EventType.ClientLoadEvent.class, e -> {
    ui.settings.addCategory("@settings.silicon.meta.category.name",
        new TextureRegionDrawable(new TextureRegion(Silicon.MOD.iconTexture)), st -> {
            // 复选框
            st.checkPref("pause", false);

            // 滑块
            st.sliderPref("volume", 100, 0, 100, 1, i -> i + "%");

            // 文本输入
            st.textPref("address", "localhost");

            // 自定义按钮（打开对话框）
            st.button("管理权限", Icon.settings, Styles.flatt, () -> {
                BaseDialog dialog = new BaseDialog("权限管理");
                dialog.addCloseButton();
                dialog.cont.label(() -> "内容");
                dialog.show();
            }).width(200f).height(45f).padTop(7f).fillX().left().row();
        });
});
```

本地化键名格式：`setting.<name>.name` / `setting.<name>.description`

### 网络包

```java
// 服务端处理
netServer.addPacketHandler("pause", (player, data) -> {
    // player: PlayerInfo 对象
    // data: 字符串数据
    Call.clientPacketReliable(player.con, "paused", data);
});

// 客户端处理
netClient.addPacketHandler("paused", (data) -> {
    // 收到服务端确认
});

// 客户端发送
Call.serverPacketReliable("pause", time);
```

### 对话框

```java
BaseDialog dialog = new BaseDialog("标题");
dialog.addCloseButton();
dialog.cont.table(Tex.button, t -> {
    t.defaults().size(280f, 60f).left();
    t.button("选项1", Icon.play, Styles.flatt, () -> {}).marginLeft(4);
    t.row();
});
dialog.show();
```

### 其他常用 API

```java
// 判断是否为客户端/服务端
net.client()    // 是否是客户端连接
net.active()    // 网络是否活跃

// 玩家信息
player.admin    // 是否是管理员
state.map.author()  // 地图作者名

// 设置存储
Core.settings.getBool("key")
Core.settings.put("key", value)

// 事件系统
Events.on(EventType.ClientLoadEvent.class, e -> { ... });
Events.run(EventType.Trigger.update, () -> { ... });
```
