# Silicon Mod 开发流程

## 编译命令
每次修改代码后必须执行编译验证：
```bash
bash /root/projects/silicon/gradlew jar
```

## 编译输出
- 编译产物：`/root/projects/silicon/build/libs/siliconDesktop.jar`
- 部署位置：`/root/projects/silicon/Silicon.mod.jar`

## 部署步骤
1. 编译：`bash gradlew jar`
2. 复制：`cp build/libs/siliconDesktop.jar Silicon.mod.jar`

## Git 仓库
- 项目目录：`/root/projects/silicon/`
- Git 目录：`/root/git/silicon/`
- 提交命令：`cd /root/projects/silicon && git add -A && git commit -m "提交信息"`

## 注意事项
- 修改后必须编译验证
- 不提交 build/ 和 .gradle/ 目录
- 不提交本文件
