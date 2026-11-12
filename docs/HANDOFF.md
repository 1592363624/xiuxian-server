# 交接说明

> 写于插件系统三步 + 依赖核实 + 接口抽取 + 服务容器四批改动之后。
> 目的：让接手的人知道**现在是什么状态、为什么是这个状态、下一步该动哪里**。
>
> 仓库：`oooohhhhhhhhhh/xiuxian-server`，分支 `main`，最新提交 `90cdc17`。
> 规模：891 个 Java 文件 / 约 14.6 万行。

---

## 一、先把环境搞对（否则第一步就会卡住）

| 事项 | 结论 |
|---|---|
| JDK | 运行用 **Java 25**，但 `pom.xml` 的 target 是 **23**。别去"顺手升级" |
| Maven | **不在 PATH 上**，一律用 `.\mvnw.cmd`（3.9.9） |
| `clean` | **绝对不要用**。这个项目 clean 一次要重下依赖、重跑全量，代价极高 |
| 内存 | 机器只有约 8 GB。跑测试前设 `MAVEN_OPTS=-Xmx400m`、`_JAVA_OPTIONS=-Xmx400m`，surefire 用 `-DargLine="-Xmx320m"` |
| 并发跑 Maven | **禁止**。两个 Maven 同时跑必有一个 `insufficient memory` 或 `Chunk::new` 崩溃 |
| 崩溃后 | 删掉 `hs_err_pid*.log`、`replay_pid*.log` 再重试。这类失败是环境问题，不是代码问题 |
| 代理 | **已清除**。原来 `~/.gitconfig` 里配了 `http.proxy=socks5h://127.0.0.1:10808`，代理不开时 `git push` 直接失败。现在直连即可 |

### 跑测试的正确姿势

全量必须分三批（一次性跑会 OOM）：

```powershell
$env:MAVEN_OPTS="-Xmx400m"; $env:_JAVA_OPTIONS="-Xmx400m"

# 批 1
.\mvnw.cmd -B -o test "-Dtest=!com.mtxgdn.db.**,!com.mtxgdn.game.**" -DfailIfNoTests=false -DargLine="-Xmx320m"
# 批 2 + 批 3
.\mvnw.cmd -B -o test "-Dtest=com.mtxgdn.db.**,com.mtxgdn.game.**" -DfailIfNoTests=false -DargLine="-Xmx320m"
```

**当前基线：565 + 755 = 1320，0 失败 0 错误 1 跳过。**
数字对不上就是有东西坏了，不要当成"环境抖动"糊过去。

> 注意：偶发的 `BUILD FAILURE` + `Tests run: 0` 是启动期崩溃，重跑一次通常就好。
> 但**先确认是崩溃还是真失败**——看有没有 `Tests run:` 摘要行，有摘要才是真跑完了。

---

## 二、这段时间做了什么（按提交顺序）

| 提交 | 内容 |
|---|---|
| `984a988` | 插件依赖声明 + 拓扑排序 + 环检测 |
| `aae1278` | 插件服务注册表（跨插件调用） |
| `07f4873` | 内置模块依赖显式化 |
| `698ed95` | 逐个核实 27 个模块依赖 + 依赖图守卫测试 |
| `e593cf9` | 核心服务迁入插件服务表 |
| `031c5d5` | 抽取 29 个服务接口 |
| `90cdc17` | 服务构造搬进容器，注册表退化为门面 |

### 2.1 插件依赖（附属关系）

原来加载顺序就是 `pluginsDir.listFiles()` 的文件系统顺序——**这是真隐患**：
依赖别人的插件排在前面，`onEnable` 里查不到对方注册的东西，
表现为"功能没生效但不报错"，换台机器就可能变样。

现在可以声明：

```java
@PluginMeta(name = "宗门战争", depends = {"sect"}, softDepends = {"economy"})
```

`plugin.json` 同样支持 `"depends"` / `"softDepends"`。规则：

- **硬依赖缺失 → 不加载**，且传递（依赖它的也一并拒载）
- **软依赖缺失 → 照常加载**，存在时保证先初始化
- **成环 → 拒载并给出路径**（`a → b → a`），不是只说"有环"

### 2.2 插件联动（服务注册表）

事件总线（`PluginEventManager`）解决"通知"，注册表解决"调用"：

```java
ctx.provideService(SectQueryApi.class, new SectQueryProvider());  // 提供
SectQueryApi sect = ctx.getService(SectQueryApi.class);           // 消费，拿不到返回 null
```

三条约定：键是**接口**不是实现类；拿不到返回 **null 不抛异常**；同键重复登记**拒绝后来者**。

### 2.3 依赖核实结果（27 个模块逐个查过）

**核实方法有个坑**：最初按命令目录聚合，得出"探索 → 秘境"一条边。
查证后发现 `SecretAreasCommand`/`SecretEnterCommand` 虽在 `exploration/` 目录下，
但 `setModule("secret_realm")`——**目录不等于模块归属**。改用 `setModule` 后该边消失。
79 个命令里 58 个声明了 `setModule`，未声明的 21 个全是 admin/debug 命令。

| 模块 | 依赖 | 依据 |
|---|---|---|
| `formation` | **硬** → `cave` | 布阵第一道检查就是"有没有洞府" |
| `experimental_sect_war` | **硬** → `sect` | 没有宗门就没有宗门战争 |
| `economy` | 软 → `cave` | 只有提纯灵石要洞府 |
| `exploration` | 软 → `map` | 地界决定事件权重与倍率 |
| `secret_realm` | 软 → `map`, `title` | 当前地界 / 首领最后一击授称号 |
| `team` | 软 → `secret_realm` | 组队副本直接进秘境 |
| `cultivation` | 软 → `title` | 转世重修授称号 |
| `player` | 软 → `title` | 角色面板读称号 |

其余 19 个模块无跨模块依赖。

**判定标准是"模块整体还能不能用"，不是"有没有调用别的模块"。**
`economy` 调 `cave` 只在提纯那一条路径，写成硬依赖会让一个子功能失效连累整个经济模块。

### 2.4 一个必须知道的真环

`CaveService` 会调 `FormationService` 取三个加成（灵力收集倍率、修炼加成、心魔抗性），
看起来该声明 `cave → formation`。**但不能这么做**——阵法已硬依赖洞府，再补这条就成环，
解析器会把两个模块一起拒载，阵法和洞府同时消失。

不声明也没问题的原因：那三处调用都在**运行时**（收灵气/打坐），不是启用期；
而 `ServiceRegistry` 里的服务与模块开关是两件事，模块停用时服务实例依然存在，
只是阵法表里没活动阵法，三个加成自然返回 0。

**这段结论写在 `CavePlugin` 的类注释里**，并有 `BuiltInPluginGraphTest` 专门钉住这个方向。
如果有人"顺手补齐"，测试会红并指出是哪一对。

### 2.5 接口抽取

29 个服务各一个接口，放在 `com.mtxgdn.api`，**由脚本从实现类机械抽取**，不是手写。
理由：几百个方法签名，泛型与 `throws` 手写几乎不可能全对。

**代价要说清楚**：这是当前实现的**镜像**，不是设计过的稳定契约。
`PlayerApi` 56 个方法，`MapApi` 42 个。收益不是"接口小"，而是插件层不再认识具体类、
可以塞假实现、可以包一层。

### 2.6 8 个嵌套 DTO 被搬出来了

接口若写 `SectService.SuccessionOutcome` 就必须 import 服务实现类，正是要消除的依赖。
所以提到 `com.mtxgdn.game.entity`：

`InventoryEntry`、`CombatResult`、`PendingChallenge`、`SuccessionOutcome`、
`HeartDemonResult`、`TradeListing`、`GuideResult`、`OfflineRewardResult`

搬出去后有三处必须改成 `public`（构造函数、`none()`、`success()`）。
**这一条是 `SectServiceSplitTest` 的黄金清单抓出来的**，清单已同步更新并注明原因。

### 2.7 服务容器

- **`ServiceContainer`**：构造与接线集中在这里，一个实例一套服务
- **`ServiceRegistry`**：静态门面，每个方法一行 `return current.getXxx();`
- **`install()` / `reset()`**：换容器、重建一套
- **`TestBase` 在停完调度器后调 `reset()`**：每个测试类拿干净实例

**顺手修掉一个真 bug**：换库清缓存的回调原本是静态块，注册后永不注销。
容器可替换之后，每换一次容器就多一个指向废弃实例的回调——换库时对着旧实例清缓存，
新实例反而没清。已改成实例字段 + `close()` 注销，并给 `DatabaseManager`
补了 `removeReinitListener`（原来只有 `add`）。

---

## 三、现在的状态：哪些是"做完的"，哪些是"开了头的"

### 做完的

- 插件附属关系、联动关系、内置模块依赖：完整可用，相关测试 68 个守着
- 29 个服务接口：全部 `implements` 成立，编译器保证
- `PluginContext` 20 个 getter 返回接口
- 服务容器可实例化、可替换、可重置，`TestBase` 已用上

### 开了头的（**这是交接的重点**）

#### 1. 450 处 `ServiceRegistry.getXxxService()` 一处都没改

真正"消灭静态单例"要求把服务作为构造参数一路传下去，涉及 140 个文件。
这次只做了前提（容器），没做迁移。

**剩余分布（供排期）**：

| 包 | 调用点 |
|---|---|
| `game.service`（服务间互调） | 132 |
| `rest` | 53 |
| `game.command.economy` | 44 |
| `plugin` | 42（其中 31 处是 `CoreServices`，**合法，别动**） |
| `game.command.admin` | 21 |
| `game.command.item` | 21 |
| `websocket` | 11 |
| 其余 | ~126 |

合计 450。

**建议从 `plugin` 开始**：它本来就该走 `PluginServiceRegistry`。
`CoreServices` 那 31 处是**唯一合法**的（它就是在做登记），别动。
`PluginContext` 的 5 处是回落路径，等 20 个 getter 删掉后自然消失。

#### 2. 8 个服务在 static 块里启动调度器

`BuffService` `CaveService` `EconomyService` `FormationService`
`PlayerService` `SeasonService` `SectService` `TradeService`

这是"换容器不彻底"的最后一块：换容器不会停掉旧容器的调度线程。
`TestBase` 里那一大段 `stopBackgroundSchedulers()` 的注释就是讲这个——
它现在仍然必要，因为**停调度器还得显式调用各自的 `shutdown`**。

要做彻底，得把调度器生命周期挪进容器（构造时启动、`close()` 时停）。

#### 3. 接口是镜像，需要收窄

等有真实插件写出来，按实际用到的收窄。现在收窄等于凭空猜。

---

## 四、还没做的设计决策（等产品判断，不是技术债）

这几件事分析过、方案有了，但**需要拍板**，我没有自作主张动代码：

### 4.1 阵法该不该是"世界设施"

现在的 `Formation` 是：限时、绑玩家、烧灵石，时长 ∝ 1/强度，
升级不延长时长，一人只能一个，拆除不退款——**本质是"带阵法名字的增益"**，
不是世界上的设施。

之前提过三层转换方案（名副其实 → 可争夺的世界设施），等决定。

### 4.2 传送阵

三个方向没选：世界设施（`map_connections` 里的一条边）/ 个人限时阵法 / 两者都要。

已定的部分：应该是玩家自己修的边（费用烧掉、两端必须已探索、不退款、
与 `seedHash` 解耦），复用"玩家自建对象"那套 L3 基础设施（和自建秘境同源）。

### 4.3 玩家自由度的 L3/L4

L1（数值分配）、L2（效果组合）已实现。L3（玩家自建内容）、L4（脚本）没动。
**结论是：永远不要在执行进程里跑玩家代码**，走声明式 DSL。

### 4.4 战斗系统

问过"能不能改成自由战斗"，分析完说先放放。仍是回合制，`CombatService` ~1200 行。

---

## 五、已知但没修的问题

| 问题 | 位置 | 影响 |
|---|---|---|
| 阵法无 `(player_id, active)` 唯一约束 | DB schema | 并发 `placeFormation` 可能插入两行 active |
| `formations` 表没有 `location_id` | DB schema | 阵法无法绑定地点（做世界设施的前置） |
| `EconomyService.getSignInRecord()` 返回 private 嵌套类型 | `EconomyService` | 无法出现在接口里，已从 `EconomyApi` 剔除 |
| `oauth2.client_secret` 是占位值 | 配置 | `xiuxian_oauth_secret_please_change`，上线前必须换 |

---

## 六、约定（避免踩别人的坑）

### 提交

用户明确要求过**自主提交，不要问**。一个改动一个提交，最后一起推。

```powershell
$msg = @'
type(scope): 中文标题

正文……
'@
$msg = $msg -replace "`r`n","`n"
[System.IO.File]::WriteAllText("$PWD\commitmsg.txt", $msg, (New-Object System.Text.UTF8Encoding($false)))
git -c user.name="dnjhy" commit -q -F commitmsg.txt
Remove-Item commitmsg.txt
```

**不要提交**：`target/`、`log/`、`commitmsg.txt`、`hs_err_pid*.log`、
`replay_pid*.log`、`*.bak`、`config/oidc-key.pem`。

### 不要动的地方

- `pom.xml` 的版本号处理 / CI 的 `versions:set`（用户明确说过这块没问题）
- 512MB 内存相关：`start-low-memory.bat`、`pool_max`、`command_threads`
- 不要批量 kill java 进程

### PowerShell 的坑

- **中文字面量在 `pwsh -Command` 的匹配串里会变成乱码**。
  需要按中文定位代码时，改用行号，或者用纯 ASCII 锚点。
- `>` 重定向可能把 PowerShell 进程 OOM，用 `| Select-String` 管道。
- 嵌套 `pwsh -File` 会双倍吃内存，容易崩。脚本用 `. .\script.ps1` 在当前进程里跑。
- 偶发 `Fatal error. Releasing the double mapped memory failed` 是环境问题，
  重跑即可，不是脚本写错了。

### 测试

- `TestBase` 的 `@BeforeAll` 会被**每个测试类**各调一次（59 个类继承它）
- `-Dtest=` 支持逗号列表，但 `Map*Test,*Map*Test` 这类通配符组合行为不可靠
- Mockito **不能 mock 具体类**（Byte Buddy 只到 Java 22，本项目跑在 Java 25）。
  接口可以；或者用 JDK 动态代理
- 有几个"黄金清单"测试会逐字比对公开 API
  （`SectServiceSplitTest`、`MapDaoExtractionTest` 的 `mapServiceKeepsGoldenPublicApi`）。
  它们在你改动公开 API 时会红——**那是它们的工作**，同步更新清单并写明原因，
  不要为了让测试变绿而放宽断言

---

## 七、如果只能记住三件事

1. **`.\mvnw.cmd`、不 clean、不并发、三批跑，基线 1320。**
2. **`cave` 和 `formation` 之间只能声明一个方向的依赖**，否则两个模块一起消失。
   原因在 `CavePlugin` 的类注释里。
3. **`ServiceRegistry` 现在只是门面**，构造在 `ServiceContainer`。
   新代码请通过构造参数或 `PluginServiceRegistry` 拿服务，别再往门面上加东西。
