# AI Players

AI Players 是一个基于 Forge `1.21.11` 与 Java `21` 的 Minecraft Java 版模组原型。

它会在游戏世界中生成可交互的 AI 玩家同伴，并提供命令控制、聊天对话、基础任务执行、蓝图建造、长期记忆、GUI 面板以及可选的外部大模型接入能力。

当前版本的默认聊天 AI 已切换为 **千问（Qwen）兼容接口**，默认模型为 `qwen-plus`。

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
- AI 动作：`jump`、`crouch`、`stand`、`look_up`、`look_down`、`look_owner`
- 环境扫描：玩家、敌对生物、原木、裸露矿石、成熟农作物
- 基础战斗、导航、卡住检测与重新寻路
- 聊天控制：支持 `@AI名字 指令` 或 `@AI名字 对话`

### 建造与记忆

- 长期记忆保存到世界存档目录
- 规划摘要与状态摘要查询
- 蓝图系统：`shelter`、`cabin`、`watchtower`
- 多 AI 基础协作建造

### 客户端增强

- 玩家风格渲染
- GUI 控制面板
- 语音链路原型：录音、语音识别、聊天回填、语音播放

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
@Alex 记忆
@Alex 计划
@Alex 停止
```

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
  "timeoutMs": 8000
}
```

### 字段说明

- `enabled`：是否启用外部大模型对话
- `provider`：当前默认提供方标记
- `url`：千问 OpenAI 兼容聊天接口地址
- `apiKey`：阿里云百炼 / DashScope API Key
- `model`：默认模型，当前为 `qwen-plus`
- `timeoutMs`：请求超时毫秒数

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


