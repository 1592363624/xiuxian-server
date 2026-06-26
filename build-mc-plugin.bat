@echo off
REM ============================================
REM  修仙世界 MC 桥接插件 构建脚本
REM ============================================
REM  用法: 将此脚本放在项目根目录运行
REM  前提: 已有 Minecraft 服务端 jar（用来提供 Bukkit API）
REM        设置 MC_SERVER_JAR 为你的服务端 jar 路径
REM ============================================

setlocal

REM ---- 配置：改为你的 MC 服务端 jar 路径 ----
set MC_SERVER_JAR=mc_server\server.jar

if not exist "%MC_SERVER_JAR%" (
    echo [错误] 找不到 MC 服务端 jar: %MC_SERVER_JAR%
    echo 请修改脚本中的 MC_SERVER_JAR 变量为你的 Minecraft 服务端 jar 路径
    exit /b 1
)

set SRC_DIR=src\main\java\com\mtxgdn\minecraft\plugin
set RES_DIR=src\main\resources
set OUT_DIR=target\mc-plugin-classes
set PLUGIN_JAR=mc_server\plugins\XiuxianBridge.jar

echo [1/3] 编译插件源码...
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

REM 编译插件（需要 gson，用项目自带的）
javac -encoding UTF-8 -cp "%MC_SERVER_JAR%;target\classes;lib\*" ^
      -d "%OUT_DIR%" ^
      "%SRC_DIR%\XiuxianBridgePlugin.java"

if errorlevel 1 (
    echo [错误] 编译失败
    exit /b 1
)

echo [2/3] 打包插件 jar...
if not exist "mc_server\plugins" mkdir "mc_server\plugins"

REM 用 jar 命令打包（自带 plugin.yml）
jar cf "%PLUGIN_JAR%" -C "%RES_DIR%" plugin.yml -C "%OUT_DIR%" .

if errorlevel 1 (
    echo [错误] 打包失败
    exit /b 1
)

echo [3/3] 完成!
echo 插件已生成: %PLUGIN_JAR%
echo 下次启动修仙服务端时会自动加载

endlocal
