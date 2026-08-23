param()
$ErrorActionPreference = "Continue"
$hub = "src/silicon/world/blocks/distribution/ItemTransferHub.java"
$text = Get-Content $hub -Raw
function ok([bool]$c,[string]$m){ if($c){ Write-Host "✅ PASS $m" -ForegroundColor Green; return 1 } else { Write-Host "❌ FAIL $m" -ForegroundColor Red; return 0 } }
$pass=0; $total=14
$pass += ok ($text.Contains("private boolean isFactory") -and $text.Contains("Drill.DrillBuild")) "isFactory 含 Drill"
$pass += ok ($text.Contains("private boolean isProducer")) "isProducer 存在"
$pass += ok ($text.Contains("isFactoryConsumer") -and $text.Contains("isStorageConsumer")) "pull区分工厂/仓储"
$pass += ok ($text.Contains("0.9f")) "仓储阈值 0.9"
$pass += ok (-not $text.Contains("for (Building b : data.buildings) {\n                if (!isProducer(b)) continue;")) "findNearestSupplier 已放宽"
$pass += ok ($text.Contains("candidates.sort") -and $text.Contains("ammoTypes.get(b).damage")) "炮台伤害优先"
$pass += ok ($text.Contains("hasPendingDemand")) "hasPendingDemand"
$pass += ok ($text.Contains("boolean hasDemand = hasPendingDemand()")) "gate hasDemand"
$pass += ok ($text.Contains("blocked = false")) "push blocked"
$pass += ok ($text.Contains("core.items.get(item) >= core.block.itemCapacity")) "核心满门控"
$pass += ok ($text.Contains("item.id >= consumer.items.length()")) "越界防护"
$pass += ok ($text.Contains("power == null")) "电力门控"
$pass += ok ($text.Contains("chargePath")) "经由计费"
$pass += ok ($text.Contains("bfsInit")) "BFS"
Write-Host "--- $pass/$total ---" -ForegroundColor Cyan
if($pass -ne $total){ exit 1 }
# 编译
$env:JAVA_HOME = "C:\Users\56308\.jdks\jbr-17.0.7"
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"
$env:GRADLE_USER_HOME = "D:\JavaProject\Silicon\.gradle-home"
& "D:\Gradle\wrapper\dists\gradle-9.4.1-all\4rb8wyv1meme7u9gesmslx5da\gradle-9.4.1\bin\gradle.bat" deploy --no-daemon --console=plain
if($LASTEXITCODE -ne 0){ Write-Host "❌ BUILD FAIL" -ForegroundColor Red; exit 1 }
Write-Host "✅ BUILD SUCCESS" -ForegroundColor Green
$b = (Get-Item "build/libs/Silicon-a0.10.1.1-v159.7.jar").Length
$d = (Get-Item "D:\Games\Mindustry-HotReload\data\mods\Silicon-a0.10.1.1-v159.7.jar" -ErrorAction SilentlyContinue)?.Length
if($b -ne $d){ Write-Host "⚠ 部署不一致 build=$b data=$d 需覆盖" -ForegroundColor Yellow } else { Write-Host "✅ 部署一致 $b" -ForegroundColor Green }
