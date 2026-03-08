# AI Players

AI Players 是一个基于 Forge `1.21.11` 与 Java `21` 的 Minecraft Java 版模组原型。

它会在游戏世界中生成可交互的 AI 玩家同伴，并提供命令控制、聊天对话、基础任务执行、蓝图建造、长期记忆、GUI 面板以及可选的外部大模型接入能力。

当前版本的默认聊天 AI 已切换为 **千问（Qwen）兼容接口**，默认模型为 `qwen-plus`。

当前版本还新增了参考 `E:\MC-MOD\minecraft_ai_agent_architecture` 文档实现的 **Agent 行为主循环**，把 AI 行为拆成：

- `Observe`：环境扫描与世界状态整理
- `Think`：结合短期记忆和长期记忆进行本地推理
- `Plan`：生成目标、推荐模式和步骤链
- `Act`：复用现有移动、采集、建造、战斗、交付执行器
- `Learn`：把规划结果与行为结果写回长期记忆

## 项目目标

本项目的目标是逐步实现一个“像玩家一样行动”的 AI 同伴系统，包括：

- 生成 AI 玩家实体
- 感知周围环境与目标
- 接收聊天命令并执行任务
- 跟随、护卫、砍树、挖矿、探索、生存
- 蓝图建造与多人协作
- 长期记忆与行为规划
- 可选的语音输入 / 输出链路

## 当前已实现功能

### 基础玩法

- 通过 `/aiplayers spawn <name>` 生成 AI 玩家
- AI 模式：`idle`、`follow`、`guard`、`gather_wood`、`mine`、`explore`、`build_shelter`、`survive`
- AI 动作：`jump`、`crouch`、`stand`、`look_up`、`look_down`、`look_owner`、`recover`
- 36 格背包持久化、自动整理、基础自动换装与主手工具切换
- 环境扫描：玩家、敌对生物、原木、裸露 / 浅层遮挡矿石、成熟农作物
- 基础战斗、导航、卡住检测、重新寻路与障碍清理
- 聊天控制：支持 `@AI名字 指令` 或 `@AI名字 对话`

### 建造与记忆

- 长期记忆保存到世界存档目录
- 规划摘要与状态摘要查询
- 蓝图系统：`shelter`、`cabin`、`watchtower`
- 多 AI 基础协作建造
- Agent pipeline 会把当前目标和推理结果同步到状态摘要与 GUI

### 客户端增强

- 玩家风格渲染
- GUI 控制面板：查看 AI 状态 / 观测 / 背包摘要，并可直接发指令或索要物品
- 语音链路原型：录音、语音识别、聊天回填、语音播放

## 本次新增行为增强

- **快速跟随**：跟随主人时会根据距离自动加速；距离过远时会尝试安全传送到主人附近
- **脱困恢复**：掉进水里会主动上浮并寻找干燥落脚点；导航长时间卡住时会尝试跳跃或清理前方障碍
- **遮挡采集**：砍树时会优先清理挡住原木的树叶；挖矿时可处理浅层石头 / 泥土 / 砂砾等遮挡方块
- **连锁采集**：砍树后会继续处理相连原木；挖矿后会继续尝试处理相邻矿脉
- **背包经营**：AI 现已使用 36 格完整背包，并会自动整理常用物资顺序
- **装备与合成**：会根据模式切换更合适的主手工具，基础自动穿戴护甲，并能合成面包、火把、石斧、石镐
- **团队协作**：建造模式下会做简单分工；队友之间会共享面包、火把、木板等基础资源
- **环境感知增强**：会记录床位、作物、工作台、熔炉、储物点与附近掉落物，并把这些线索写入长期记忆
- **认知摘要**：AI 会持续生成“当前在担心什么 / 缺什么 / 接下来要做什么”的认知摘要，并同步到状态与 GUI
- **实时环境提示**：当主人附近出现威胁、夜晚来临、食物偏少或发现关键资源时，会主动发消息提醒主人
- **自主活动增强**：长时间待命且没有主人约束时，会自动转入自主生存；夜间会优先回安全点休整；会顺路拾取附近掉落物
- **重生恢复**：AI 死亡后会在主人附近自动重生，并恢复记忆、背包与主要状态
- **背包查看**：支持聊天询问“背包 / 包里有什么”，也支持命令查看完整背包摘要
- **物品交付**：可识别常见物品名与数量，AI 会靠近玩家并把物品直接放入玩家背包，放不下时丢到脚边
- **更多基础理解**：新增“快跟上 / 过来 / 回来 / 脱困 / 背包 / 库存”等自然语言触发词

## Agent 行为流程

当前实现已经按行为流程文档，把 AI 主循环调整为：

```text
Environment Scan -> Memory Retrieve -> Local Reasoning -> Task Planning -> Action Execution -> Memory Update
```

对应代码模块：

- `src/main/java/com/mcmod/aiplayers/system/AIAgentWorldState.java`
- `src/main/java/com/mcmod/aiplayers/system/AIAgentMemorySnapshot.java`
- `src/main/java/com/mcmod/aiplayers/system/AIAgentPlan.java`
- `src/main/java/com/mcmod/aiplayers/system/AIAgentPipeline.java`

当前这套 pipeline 的作用：

- 统一处理交付、自保、敌对威胁、资源采集、建造补材、夜间生存等优先级
- 为 `idle / follow / guard / explore / survive` 提供更稳定的自动模式切换
- 为 `gather_wood / mine / build_shelter` 提供更明确的下一步规划摘要
- 将“当前目标”同步到 GUI 和 `/aiplayers status`

说明：

- 当前 `Think` 阶段默认使用本地规则推理，避免每个周期都调用远程模型导致卡顿
- 外部大模型仍主要用于聊天理解与扩展回复
- 后续可以继续把 `Think / Plan` 升级成可选的远程 LLM 规划器

## 功能清单与路线图

下面把当前项目按模块整理为“已实现 / 原型中 / 规划中”三类，便于继续推进。

### AI实体系统

- `[已实现]` AI 玩家实体、服务器同步、基础装备栏、36 格背包持久化、多个 AI 共存
- `[已实现]` 基础工具切换、常见物品交付、背包明细命令查看、自动整理
- `[原型中]` 玩家风格渲染、模式切换、自动换更强装备、更多物品使用
- `[规划中]` 真实玩家皮肤同步、更完整的装备耐久管理与复杂物品使用

### 生存行为系统

- `[已实现]` 自动恢复、基础战斗、砍树、挖矿、农作、简易避难所建造
- `[已实现]` 水域脱困、导航卡住恢复、快速跟随、浅层遮挡资源处理、连锁采树 / 连锁采矿
- `[已实现]` 基础火把照明、基础生存合成（面包 / 火把 / 石斧 / 石镐）
- `[已实现]` 夜间回安全点休整、附近掉落物自动回收、长期自主生存巡查
- `[原型中]` 基础护甲 / 盾牌装备切换
- `[规划中]` 真实饥饿值、睡眠、自动烹饪、药水 / 远程武器使用

### 探索系统

- `[已实现]` 附近探索、敌对生物发现、木头 / 矿石位置记录、规划摘要
- `[原型中]` 路径重规划、可到达位置搜索
- `[规划中]` 洞穴探索、危险地形规避、出口标记、资源地图、村庄 / 神殿 / 基地记录

### 建造系统

- `[已实现]` 蓝图建造、基础多人协作、避难所 / cabin / watchtower 蓝图
- `[原型中]` 建材不足时自动转采集、蓝图切换
- `[规划中]` 更复杂蓝图、自适应地形、建筑修复、多人分工建造

### AI智能系统

- `[已实现]` 命令意图解析、状态摘要、计划摘要、基础任务驱动
- `[已实现]` 环境感知驱动决策、认知摘要、实时主人提示、长期记忆线索写入
- `[原型中]` 简单任务拆解、外部大模型辅助回复、Hybrid Agent 高层任务规划（goal / mode / subtasks / fallback）
- `[规划中]` 更完整任务树、优先级系统、动态调整、长期自主规划

### 记忆与社交系统

- `[已实现]` 长期记忆存储、聊天控制、文本对话、语音输入 / 输出原型
- `[已实现]` 跨重启记忆、最近事件总结、资源/床位/工作站位置记忆、死亡后记忆恢复
- `[原型中]` AI 回复播报、玩家关系记忆
- `[规划中]` 玩家关系记忆、情绪系统、主动提醒、长期学习与经验优化

### 团队与世界系统

- `[已实现]` 多 AI 基础协作建造与附近队友统计
- `[原型中]` 简单建造分工、基础资源共享
- `[规划中]` 队伍管理、完整分工系统、基地管理、村庄互动、跨维度任务

### 语音系统

- `[已实现]` 按键录音、STT 转文字、自动发消息给 AI、TTS 朗读回复
- `[原型中]` 千问兼容 HTTP 语音链路
- `[规划中]` WebSocket 实时语音识别、持续对话、麦克风实时字幕、低延迟语音控制

## 环境要求

- Minecraft：`1.21.11`
- Forge：`61.1.3`
- Java：`21`

## 构建方法

在项目根目录执行：

```powershell
.\gradlew.bat build
```

构建后的模组文件位于：

```text
build/libs/aiplayers-0.2.1-forge-1.21.11.jar
```

## 安装方法

1. 安装与本模组版本匹配的 Forge。
2. 将 `build/libs` 目录下生成的 jar 文件放入游戏的 `mods` 文件夹。
3. 启动游戏并进入世界。

## 常用命令

### 生成 AI

```mcfunction
/aiplayers spawn Alex
```

### 切换模式

```mcfunction
/aiplayers mode @e[type=aiplayers:ai_player,limit=1,sort=nearest] follow
```

### 执行动作

```mcfunction
/aiplayers action @e[type=aiplayers:ai_player,limit=1,sort=nearest] jump
```

### 查询状态 / 记忆 / 规划

```mcfunction
/aiplayers status @e[type=aiplayers:ai_player,limit=1,sort=nearest]
/aiplayers inventory @e[type=aiplayers:ai_player,limit=1,sort=nearest]
/aiplayers memory @e[type=aiplayers:ai_player,limit=1,sort=nearest]
/aiplayers plan @e[type=aiplayers:ai_player,limit=1,sort=nearest]
```

### 切换蓝图

```mcfunction
/aiplayers blueprint @e[type=aiplayers:ai_player,limit=1,sort=nearest] shelter
```

### AI 接口管理

```mcfunction
/aiplayers api status
/aiplayers api reload
/aiplayers api enable
/aiplayers api disable
```

## 聊天控制示例

可以直接在聊天栏输入：

```text
@Alex 跟随我
@Alex 护卫我
@Alex 砍树
@Alex 挖矿
@Alex 探索附近
@Alex 建造避难所
@Alex 跳一下
@Alex 抬头
@Alex 看看背包
@Alex 把木头给我
@Alex 给我 16 个原木
@Alex 把圆石给我
@Alex 脱困
@Alex 记忆
@Alex 计划
@Alex 停止
```

## GUI 面板说明

按 `H` 打开控制面板后，可以直接：

- 选择附近 AI
- 查看同步过来的状态、观测和背包摘要
- 一键发送跟随 / 护卫 / 砍树 / 挖矿 / 探索 / 建造 / 生存 / 状态 / 背包 / 计划 / 脱困
- 在“取物”输入框中填写物品名与数量，例如：`木头`、`原木`、`圆石`、`面包`
- 点击“取物”后，面板会自动发送类似“给我 16 个原木”或“把木头给我”的请求

当前已支持较常见的交付关键词，包括：

- `木头` / `原木`
- `木板`
- `圆石`
- `煤炭`
- `粗铁` / `铁锭`
- `面包`
- `小麦`
- `木棍`
- `石镐` / `石斧`
- `盾牌` / `铁剑`

## 快捷键

- `H`：打开 AI 控制面板
- `V`：开始 / 停止语音录音，并将识别文本发给当前 AI

## 千问（Qwen）默认聊天配置

模组首次运行后会生成：

```text
config/aiplayers-api.json
```

当前默认配置为：

```json
{
  "enabled": false,
  "provider": "qwen-compatible",
  "url": "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
  "apiKey": "",
  "model": "qwen-plus",
  "timeoutMs": 8000,
  "plannerMode": "llm_primary",
  "conversationEnabled": false,
  "taskPlanningEnabled": false,
  "conversationModel": "qwen-plus",
  "goalModel": "qwen-plus",
  "replanIntervalSeconds": 8,
  "maxRetries": 2,
  "taskAiEnabled": false,
  "taskAiIntervalSeconds": 8
}
```

### 字段说明

- `enabled`：兼容旧版的总开关，会联动对话开关
- `provider`：当前默认提供方标记
- `url`：千问 / OpenAI 兼容聊天接口地址
- `apiKey`：阿里云百炼 / DashScope API Key
- `model`：兼容旧版字段；缺省时会同步到 `conversationModel` 和 `goalModel`
- `plannerMode`：`llm_primary | hybrid | local_only`，决定高层目标是否优先走 LLM
- `conversationEnabled`：是否启用自由对话 LLM
- `taskPlanningEnabled`：是否启用高层目标重规划
- `conversationModel`：聊天回复模型
- `goalModel`：目标规划模型
- `replanIntervalSeconds`：LLM 高层重规划间隔秒数
- `timeoutMs`：请求超时毫秒数
- `maxRetries`：失败重试次数
- `taskAiEnabled` / `taskAiIntervalSeconds`：旧字段兼容别名，会自动映射到新的任务规划配置

### 对话 AI 与任务 AI

- `conversationEnabled=true`：启用外部大模型聊天链路，玩家自由对话会走 LLM 回复
- `taskPlanningEnabled=true`：启用 Hybrid Agent 目标规划链路，`AgentRuntime` 会周期性发送世界状态、记忆、背包与失败摘要给 LLM
- 任务规划返回结构固定为：`goalType`、`goalArgs`、`priority`、`constraints`、`fallbackGoal`、`speechReply`
- 本地 GOAP / 执行器仍负责真正落地：寻路、砍树、挖矿、建造、战斗、交付、脱困
- 当 LLM 不可用、403、超时或返回非法 JSON 时，会回退到本地 `GoalSelector`，不会清空当前进度

### 推荐模型

- `qwen-plus`：默认推荐，综合能力与成本较平衡
- `qwen-turbo`：速度更快、成本更低
- `qwen-max`：能力更强，通常成本更高

### 旧配置迁移

如果你之前已经生成过旧版 `aiplayers-api.json`，模组重新加载时会自动把“OpenAI 默认地址 / 空模型”迁移到新的千问默认值。

## 语音配置说明

客户端首次运行后会生成：

```text
config/aiplayers-voice.json
```

当前语音模块分成两类能力：

1. **当前已经实现的能力**
   - 按 `V` 开始录音
   - 再按一次 `V` 结束录音
   - 将整段语音送去识别
   - 把识别文本自动发给目标 AI
   - 可选地把 AI 回复再做语音合成播放出来

2. **未来可扩展的能力**
   - 真正的实时语音识别
   - “边说边出字”
   - WebSocket 持续推流
   - 中间结果 / 最终结果分段返回

### 当前代码默认使用的语音模型

当前仓库里的 `aiplayers-voice.json` 默认值，已经切换为千问 / 百炼体系下更适合当前实现的模型：

- `sttModel`：`qwen3-asr-flash`
- `ttsModel`：`qwen-tts`
- `ttsVoice`：`Cherry`

这样做的原因是：

- 当前模组语音输入实现还是“录音结束后再识别”的模式
- 这种模式更适合直接调用兼容 HTTP 接口
- `qwen3-asr-flash` 适合作为当前版本的默认语音转文字模型
- `qwen-tts` 适合作为当前版本的默认文字转语音模型

### 当前默认配置示例

```json
{
  "enabled": false,
  "autoSpeakReplies": false,
  "defaultTarget": "ai",
  "sttUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
  "sttApiKey": "",
  "sttModel": "qwen3-asr-flash",
  "sttLanguage": "zh",
  "ttsUrl": "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
  "ttsApiKey": "",
  "ttsModel": "qwen-tts",
  "ttsVoice": "Cherry",
  "timeoutMs": 15000
}
```

### 每个字段怎么填写

- `enabled`
  - 是否启用语音功能
  - `true` 启用，`false` 关闭

- `autoSpeakReplies`
  - 是否把 AI 回复自动朗读出来
  - `true`：自动播放 TTS
  - `false`：只做语音输入，不播报

- `defaultTarget`
  - 默认把语音识别结果发送给哪个 AI
  - 例如：`"Alex"`

- `sttUrl`
  - 当前默认使用千问兼容语音识别接入地址
  - 对应当前代码中的“录音结束后上传识别”逻辑

- `sttApiKey`
  - 百炼 / DashScope API Key

- `sttModel`
  - 当前默认：`qwen3-asr-flash`
  - 这是当前版本最适合直接接入的默认语音识别模型

- `sttLanguage`
  - 当前建议中文填 `zh`

- `ttsUrl`
  - 当前默认使用百炼多模态生成接口
  - 用于文本转语音

- `ttsApiKey`
  - TTS 接口对应的 API Key

- `ttsModel`
  - 当前默认：`qwen-tts`

- `ttsVoice`
  - 当前默认：`Cherry`
  - 这是官方示例中常见的音色之一

- `timeoutMs`
  - 超时时间，单位毫秒
  - 默认 `15000`

### 当前版本推荐配置

如果你想先把“按 `V` 录音 -> 发给 AI -> AI 回复朗读”跑通，推荐直接使用：

```json
{
  "enabled": true,
  "autoSpeakReplies": true,
  "defaultTarget": "Alex",
  "sttUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
  "sttApiKey": "你的百炼API Key",
  "sttModel": "qwen3-asr-flash",
  "sttLanguage": "zh",
  "ttsUrl": "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
  "ttsApiKey": "你的百炼API Key",
  "ttsModel": "qwen-tts",
  "ttsVoice": "Cherry",
  "timeoutMs": 15000
}
```

## 实时语音识别模型怎么选

你给出的官方文档是**实时语音识别服务**文档，这套能力适合做真正的“边说边出字”。

但要注意：

- 当前模组实现 **还不是 WebSocket 持续流式识别**
- 也就是说，下面这些实时模型已经适合写进选型文档，
- 但如果要真正接入，还需要把客户端语音输入改成 WebSocket / SDK 持续推流架构

### 针对本模组场景，推荐这样选

#### 1. 语音命令 / 短对话交互

**推荐模型：`gummy-chat-v1`**

原因：

- 官方文档明确说明它适合“一句话识别”
- 更适合对话聊天、指令控制、语音输入法、语音搜索等短时交互
- 很符合本模组“对 AI 下指令、和 AI 聊天”的场景

#### 2. 长时间讲话 / 连续字幕 / 会议记录

**推荐模型：`fun-asr-realtime`** 或 `paraformer-realtime-v2`

原因：

- 官方文档把它们定位为会议、直播、长语音实时识别
- 适合未来做“边说边出文字”的扩展版语音面板

#### 3. 电话音质 / 8k 场景

**推荐模型：`fun-asr-flash-8k-realtime`**

原因：

- 官方文档明确说明它适合低带宽电话客服类场景

### 本项目当前推荐结论

综合“当前代码已经实现的语音链路”和“你给出的官方实时识别模型文档”，本项目建议这样区分：

- **当前默认配置（已可直接使用）**
  - `sttModel = qwen3-asr-flash`
  - `ttsModel = qwen-tts`
  - `ttsVoice = Cherry`

- **未来真正实时识别扩展的首选模型**
  - **短语音命令 / 语音聊天：`gummy-chat-v1`**
  - **长语音实时字幕 / 连续听写：`fun-asr-realtime`**

### 如果以后要升级成真正实时 ASR

如果你后续要把模组升级成“按住说话 / 一边说一边显示文字”的版本，建议：

- WebSocket 地址使用官方实时语音识别接入点
  - 中国内地：`wss://dashscope.aliyuncs.com/api-ws/v1/inference`
  - 国际站：`wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference`
- 短命令交互优先选 `gummy-chat-v1`
- 长连续语音优先选 `fun-asr-realtime`

### 常见问题

- 为什么 README 里写了 `gummy-chat-v1`，但默认配置却是 `qwen3-asr-flash`？
  - 因为 `gummy-chat-v1` 属于实时语音识别体系，更适合 WebSocket 持续流式接入
  - 当前模组还没有实现完整的实时流式 ASR 客户端，所以默认先使用更适合当前架构的 `qwen3-asr-flash`

- 为什么 TTS 不是实时语音模型？
  - 因为 TTS 负责“文字转语音”，当前默认直接使用官方文档中的 `qwen-tts`

- 能不能把 `sttModel` 直接改成 `fun-asr-realtime` 或 `gummy-chat-v1`？
  - 从“文档选型”角度可以
  - 但从“当前这版代码直接可用”角度，不建议直接改，除非你同时把客户端语音代码改成 WebSocket 实时识别实现
## 开发运行

### 启动客户端开发环境

```powershell
.\gradlew.bat runClient
```

### 修改后强制打包约定

每次代码修改（无论是功能、修复还是重构）后，必须执行一次完整打包并确认 `build/libs` 产物已更新：

```powershell
.\gradlew.bat build
```

默认以最新时间戳的 `build/libs/aiplayers-*.jar` 作为可交付版本。

### 重新加载 AI 配置

```mcfunction
/aiplayers api reload
```

## 参考的官方文档

- 千问模型 API 调用说明（官方）：https://www.alibabacloud.com/help/zh/model-studio/use-qwen-by-calling-api
- DashScope OpenAI 兼容说明（官方）：https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope
- 千问 ASR 文档（官方）：https://www.alibabacloud.com/help/zh/model-studio/qwen-asr
- 千问 TTS 文档（官方）：https://www.alibabacloud.com/help/zh/model-studio/qwen-tts

## 注意事项

- 请不要把真实 API Key 提交到仓库。
- 服务端负责大部分 AI 行为逻辑；GUI、渲染、语音属于客户端增强能力。
- 当前版本仍是持续迭代中的原型版本，不是最终完成版。


