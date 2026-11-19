$env:JAVA_HOME = "D:\android\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "C:\Users\wangyi\AppData\Local\Android\Sdk"
Set-Location "D:\KiraCode"
.\gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon 2>&1 | Select-String -Pattern "^e: " | Where-Object { $_ -match "KiraCodeHttpServer" } | Out-File D:\KiraCode\err_srv.txt -Encoding utf8
$c = (Get-Content D:\KiraCode\err_srv.txt | Measure-Object).Count
Write-Output "SRV_err=$c"
Get-Content D:\KiraCode\err_srv.txt
