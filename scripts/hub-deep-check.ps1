param()
$ErrorActionPreference = "Continue"
# 固化检查：对齐 a0.11.8.x 当前代码锚点（chargeOne 单跳计费 / 调度节流 / 序列化 v1 / 跨网仓库回退）
$hub     = "src/silicon/world/blocks/distribution/ItemTransferHub.java"
$routing = "src/silicon/world/blocks/distribution/HubRouting.java"
$text    = Get-Content $hub -Raw -Encoding UTF8
$route   = Get-Content $routing -Raw -Encoding UTF8
function ok([bool]$c,[string]$m){ if($c){ Write-Host "PASS $m" -ForegroundColor Green; return 1 } else { Write-Host "FAIL $m" -ForegroundColor Red; return 0 } }
$pass=0; $total=18
$pass += ok ($text.Contains("HubRouting.isFactory(b)")) "isFactory 委托 HubRouting"
$pass += ok ($route.Contains("Reconstructor.ReconstructorBuild")) "isFactory 含重构工厂"
$pass += ok (-not $route.Contains("if (other.items != null) return true")) "白名单无『有物品栏即连』泛化"
$pass += ok ($text.Contains("producer.acceptItem(producer, item)")) "推送输入料保护门"
$pass += ok ($text.Contains("ammoTypes.get(b).damage")) "炮台伤害优先"
$pass += ok ($text.Contains("blocked = false")) "push 堵线触发"
$pass += ok ($text.Contains("coreHasRoom = cur < cap * surplusPushAt")) "核心 75% 门控"
$pass += ok ($text.Contains("item.id >= consumer.items.length()")) "越界防护"
$pass += ok ($text.Contains("power == null || power.status <= 0")) "电力门控"
$pass += ok ($text.Contains("timer(0, 10)")) "调度节流 6Hz"
$pass += ok ($text.Contains("private void chargeOne(")) "chargeOne 单跳计费"
$pass += ok ($text.Contains("powerConsumed = powerConsumedNext") -and -not $text.Contains("powerConsumed += powerConsumedNext")) "电力折叠为赋值语义（防跨帧累加）"
$pass += ok ($text.Contains("transferCount += transferCountNext")) "途经计数延迟并入（累加语义）"
$pass += ok ($route.Contains("linkedCore != null")) "核心旁已合并仓库排除（linkedCore 判据）"
$pass += ok ($text.Contains("write.i(network.id)") -and $text.Contains("revision < 1")) "存档序列化 v1"
$pass += ok ($text.Contains("寻找其它中枢直连的仓库")) "核心满回退仓库跨网 BFS"
$pass += ok ($text.Contains("world.isGenerating()")) "加载期防误删链接"
$pass += ok ($text.Contains("bfsInit")) "BFS 池化复用"
Write-Host "--- $pass/$total ---" -ForegroundColor Cyan
if($pass -ne $total){ exit 1 }
# 编译（JDK17：build-tools 34 d8 需要）
$env:JAVA_HOME = "C:\Users\56308\.jdks\jbr-17.0.7"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
& ".\gradlew.bat" deploy --console=plain "-Dorg.gradle.java.home=$env:JAVA_HOME" | Out-Null
if($LASTEXITCODE -ne 0){ Write-Host "BUILD FAIL" -ForegroundColor Red; Pop-Location; exit 1 }
Write-Host "BUILD SUCCESS" -ForegroundColor Green
# 部署一致性：最新产物 vs 游戏模组目录（目录不存在则跳过）
$jar = Get-ChildItem "build/libs/Silicon-*-v159.7.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Copy-Item $jar.FullName "Silicon.mod.jar" -Force
Write-Host "已部署 Silicon.mod.jar ($((Get-Item 'Silicon.mod.jar').Length) bytes)"
$gameDir = 'D:\Games\Mindustry-HotReload\data\mods'
if (Test-Path $gameDir) {
  # 移除全部旧版 Silicon 包防同模组双载，替换为最新构建
  Get-ChildItem $gameDir -Filter 'Silicon*.jar' | Remove-Item -Force
  Copy-Item $jar.FullName (Join-Path $gameDir 'Silicon.jar') -Force
  Write-Host "已同步游戏目录 Silicon.jar ($((Get-Item (Join-Path $gameDir 'Silicon.jar')).Length) bytes)" -ForegroundColor Green
}
Pop-Location
