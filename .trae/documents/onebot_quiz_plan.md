# OneBot 答题扩展功能 - 实现计划

## 一、需求分析

### 核心功能

1. **自动识别**：OneBot 接收到消息后，自动识别消息中是否包含题目关键词
2. **查询题库**：匹配到题目后从文件题库中查询
3. **回复题目**：发送题目内容 + 4个选项，正确答案标记为 **修仙答X**
4. **文件存储**：题目数据存储在 JSON 文件中，可手动/命令编辑

### 交互示例

**自动识别场景**（群聊中有人说了关键词）：

* **用户消息**：「游戏开始啦，请听题！」

* **机器人回复**：

  ```
  以下哪项属于修仙境界？
  1. 凡人期
  2. 练气期
  3. 打工期
  4. 摸鱼期

  答案：修仙答2
  ```

**命令场景**：

* `/quiz` 或 `/答题` → 随机出一题

* `/quiz random` → 随机出一题

* `/quiz list` → 列出所有题目（编号+题干）

* `/quiz add 题目|A选项|B选项|C选项|D选项|答案字母|关键词1,关键词2` → 添加题目

* `/quiz del <编号>` → 删除题目

***

## 二、现有架构分析

### 关键类关系

```
Main.java (L141)
  └── CommandScanner.scanAndRegister("com.mtxgdn.onebot.command")
          └── 自动扫描所有 Command 子类（递归子包）

OneBotWebSocketServer.java (核心消息处理)
  ├── handleMessageEvent() (L124)  → 区分群聊/私聊
  ├── handleGroupMessage() (L162)  → 处理群聊消息
  ├── handlePrivateMessage() (L140) → 处理私聊消息
  └── dispatchCommand() (L183)     → 分发指令

Command.java (抽象基类, L12)
  └── 所有命令继承，构造函数自动注册到 CommandRegistry

OneBotCommandContext.java (指令上下文, L8)
  ├── reply() / sendGroupMsg() / sendPrivateMsg()
  └── getSenderId(), getGroupId()

CommandScanner.java (L84-L90)
  └── findClassesInDirectory() 支持递归扫描子包 ✓
```

### 命令注册机制

* 所有继承 `Command` 的类，构造函数中调用 `CommandRegistry.register(this)`（父类构造函数已完成）

* `CommandScanner` 扫描 `com.mtxgdn.onebot.command` 包（含子包），反射实例化

* **新增命令只需放在正确包下即可被自动发现，不需要修改 Main.java**

***

## 三、详细实现方案

### 3.1 文件结构

```
src/main/java/com/mtxgdn/onebot/quiz/
  ├── QuizQuestion.java        (题目实体，简单 POJO)
  └── QuizService.java         (题库服务，单例)

src/main/java/com/mtxgdn/onebot/command/quiz/
  └── QuizCommand.java         (答题指令处理器)

data/quiz/
  └── questions.json           (题目数据文件)
```

### 3.2 数据文件 questions.json 格式

```json
{
  "version": 1,
  "questions": [
    {
      "id": 1,
      "question": "以下哪项属于修仙境界？",
      "options": ["凡人期", "练气期", "打工期", "摸鱼期"],
      "answer": "2",
      "keywords": ["修仙", "境界", "练气"]
    },
    {
      "id": 2,
      "question": "修仙中吸收天地灵气的过程叫？",
      "options": ["吐纳", "呼吸", "喘气", "叹气"],
      "answer": "1",
      "keywords": ["灵气", "吸收", "吐纳"]
    }
  ]
}
```

### 3.3 QuizQuestion.java

简单 POJO，字段：`int id`, `String question`, `String[] options`（固定4项）, `String answer`, `String[] keywords`。使用 Gson 序列化/反序列化（项目已有 Gson 依赖）。

### 3.4 QuizService.java（核心服务）

* **单例模式**：`QuizService.getInstance()`

* **文件路径**：`new File("data/quiz/questions.json")`

* **关键方法**：

  * `load()` — 启动时从 JSON 文件加载，目录/文件不存在则创建默认数据

  * `save()` — 增删后写入文件，synchronized 保护

  * `List<QuizQuestion> getAll()` — 获取全部题目

  * `QuizQuestion getById(int id)` — 按编号查询

  * `QuizQuestion findByKeywordMatch(String message)` — 关键词匹配（自动识别核心）

  * `QuizQuestion getRandom()` — 随机一题

  * `int add(String q, String[] opts, String ans, String[] kws)` — 新增，分配新 id

  * `boolean delete(int id)` — 删除

### 3.5 QuizCommand.java（指令处理器）

* **包路径**：`com.mtxgdn.onebot.command.quiz`（CommandScanner 递归扫描子包 ✓）

* **命令名**：`new String[]{"quiz", "答题", "question"}`

* **描述/分类**：答题功能，分类"工具"

* **子命令注册**（使用 `registerSub` 机制）：

  * `random` → 随机出题

  * `list` → 题目列表

  * `add` → 添加题目

  * `del` → 删除题目

* **无参数默认**：直接随机出一题（覆写 `onDefault`）

* **权限**：不需要绑定角色，无需权限

### 3.6 OneBotWebSocketServer.java（自动识别接入）

在 `handleGroupMessage`（第 162 行）和 `handlePrivateMessage`（第 140 行）中：

**原逻辑**：

```java
if (!isCommand(trimmed)) return;   // 非命令消息直接忽略
```

**新逻辑**：

```java
if (!isCommand(trimmed)) {
    // 自动识别：尝试从题库匹配关键词
    QuizQuestion q = QuizService.getInstance().findByKeywordMatch(trimmed);
    if (q != null) {
        // 限流保护（每 QQ 每 30 秒最多一次自动回复）
        if (RateLimiter.allow("quiz_auto:" + senderQq, 1, 30)) {
            replyToSource(socket, selfId, senderQq, groupId, formatQuizMessage(q));
        }
    }
    return;
}
// ... 原命令处理逻辑
```

**回复格式**：

```
【修仙答题】
{题目内容}
1. {optionA}
2. {optionB}
3. {optionC}
4. {optionD}

答案：修仙答{X}
```

***

## 四、需要修改/新增的文件清单

| 类型    | 文件路径                                                            | 改动说明                                              |
| ----- | --------------------------------------------------------------- | ------------------------------------------------- |
| ✨ 新建  | `src/main/java/com/mtxgdn/onebot/quiz/QuizQuestion.java`        | 题目数据类（POJO）                                       |
| ✨ 新建  | `src/main/java/com/mtxgdn/onebot/quiz/QuizService.java`         | 题库服务（增删查+关键词匹配+JSON持久化）                           |
| ✨ 新建  | `src/main/java/com/mtxgdn/onebot/command/quiz/QuizCommand.java` | 答题指令（add/del/list/random）                         |
| ✨ 新建  | `data/quiz/questions.json`                                      | 初始题目数据（3-5道示例题）                                   |
| 🔧 修改 | `src/main/java/com/mtxgdn/onebot/OneBotWebSocketServer.java`    | handleGroupMessage / handlePrivateMessage 中加入自动识别 |

***

## 五、关键实现细节

### 5.1 关键词匹配策略

* 遍历所有题目的 keywords 数组

* 消息文本（忽略大小写）包含任一关键词即视为匹配

* 多题匹配时随机返回一题（避免总是回复同一题）

* 不支持复杂正则，保持简单高效

### 5.2 文件读写策略

* 使用 `com.google.gson.Gson`（项目已有依赖，见 OneBotWebSocketServer L37）

* 文件路径：`new File("data/quiz/questions.json")`

* 首次启动目录/文件不存在时 → 创建默认题目（3道左右）

* 加载失败时：log.warn + 使用空列表，不影响主服务

* 写入：使用 `synchronized (this)` 保护 save 方法

* 文件编码：UTF-8（Main.java L56-57 已强制设置）

### 5.3 添加题目命令解析格式

```
/quiz add 以下哪项属于修仙境界？|凡人期|练气期|打工期|摸鱼期|B|修仙,境界,练气
```

解析规则（以 `|` 分隔）：

* 段1：题目

* 段2-5：4个选项（顺序对应 A/B/C/D）

* 段6：正确答案字母（A/B/C/D，不区分大小写）

* 段7（可选）：关键词，英文逗号分隔

### 5.4 答题回复格式化

将 QuizQuestion 对象格式化为可读字符串：

```
【修仙答题】
以下哪项属于修仙境界？
1. 凡人期
2. 练气期
3. 打工期
4. 摸鱼期

答案：修仙答2
```

### 5.5 RateLimiter 使用

* 项目已有 `com.mtxgdn.util.RateLimiter`（在 OneBotWebSocketServer L155 使用）

* 签名：`RateLimiter.allow(String key, int count, int seconds)`

* 用于自动识别场景的刷屏防护

***

## 六、风险与注意事项

| 风险            | 影响                          | 规避方案                            |
| ------------- | --------------------------- | ------------------------------- |
| JSON 文件格式损坏   | 启动加载失败 → 无法使用答题功能           | try-catch 包裹加载，log.warn，使用空列表降级 |
| 并发写入冲突        | 多人同时 `/quiz add` 可能数据丢失     | `synchronized` 保护 save() 方法     |
| 自动回复刷屏        | 群聊中频繁触发骚扰用户                 | RateLimiter 限流（每 QQ 30秒1次）      |
| 关键词误匹配        | 正常聊天消息被误识别为题目               | 关键词需有辨识度（如"修仙题""答题"等专用词）        |
| Command 需无参构造 | QuizCommand 需提供 public 无参构造 | CommandScanner L49 通过反射调用默认构造   |

***

## 七、实施步骤

1. ✅ 确认 CommandScanner 递归扫描子包能力（已验证：L84-L90 递归逻辑）
2. 创建 QuizQuestion.java — POJO 类
3. 创建 QuizService.java — 单例服务（load/save/增删查/匹配）
4. 创建 data/quiz/questions.json — 初始题目数据
5. 创建 QuizCommand.java — 答题指令处理器
6. 修改 OneBotWebSocketServer.java — 在消息处理中接入自动识别
7. 编译验证（mvn compile）

