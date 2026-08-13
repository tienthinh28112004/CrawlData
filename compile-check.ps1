$ErrorActionPreference = 'Stop'

$guava = Get-ChildItem "$env:USERPROFILE\.m2\repository\com\google\guava\guava\33.4.8-jre" -Filter guava-33.4.8-jre.jar | Select-Object -First 1
$gson = Get-ChildItem "$env:USERPROFILE\.m2\repository\com\google\code\gson\gson\2.13.2" -Filter gson-2.13.2.jar | Select-Object -First 1
$jsoup = Get-ChildItem "$env:USERPROFILE\.m2\repository\org\jsoup\jsoup\1.18.1" -Filter jsoup-1.18.1.jar | Select-Object -First 1
$sqlite = Get-ChildItem "$env:USERPROFILE\.m2\repository\org\xerial\sqlite-jdbc\3.47.1.0" -Filter sqlite-jdbc-3.47.1.0.jar | Select-Object -First 1
$slf4j = Get-ChildItem "$env:USERPROFILE\.m2\repository\org\slf4j\slf4j-api\2.0.16" -Filter slf4j-api-2.0.16.jar | Select-Object -First 1
$logbackClassic = Get-ChildItem "$env:USERPROFILE\.m2\repository\ch\qos\logback\logback-classic\1.5.18" -Filter logback-classic-1.5.18.jar | Select-Object -First 1
$logbackCore = Get-ChildItem "$env:USERPROFILE\.m2\repository\ch\qos\logback\logback-core\1.5.18" -Filter logback-core-1.5.18.jar | Select-Object -First 1

$classpath = (($guava, $gson, $jsoup, $sqlite, $slf4j, $logbackClassic, $logbackCore).FullName -join ';')

Remove-Item -Recurse -Force target\classes -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force target\classes | Out-Null

$sources = Get-ChildItem -Recurse -File src\main\java | Where-Object { $_.Extension -eq '.java' } | Select-Object -ExpandProperty FullName

javac --release 21 -encoding UTF-8 -cp $classpath -d target\classes $sources
Write-Host "Compile OK"
