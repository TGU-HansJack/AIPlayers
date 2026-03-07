# AI Players

AI Players is a Forge 1.21.11 / Java 21 Minecraft Java Edition mod prototype.
It adds AI companion entities that can chat, follow, guard, gather resources, build structures, and handle basic survival tasks.

## Implemented Features

### Core Gameplay

- Spawn AI companions with `/aiplayers spawn <name>`.
- Full companion stats: health, equipment, backpack, combat, and navigation.
- Environment scanning for players, hostiles, logs, exposed ores, and nearby crops.
- Chat control with `@Name <message>` using local intent parsing plus optional external AI API.
- Task modes: `idle`, `follow`, `guard`, `gather_wood`, `mine`, `explore`, `build_shelter`, `survive`.
- Extra actions: `jump`, `crouch`, `stand`, `look_up`, `look_down`, `look_owner`.
- Stuck detection and automatic path replanning.
- Combat reactions against nearby hostile mobs.

### Building, Planning, and Memory

- Short-term memory and plan summaries exposed through commands and chat.
- Long-term memory persisted to world save data across restarts.
- Blueprint building system with `shelter`, `cabin`, and `watchtower`.
- Multi-AI cooperative building with automatic placement splitting.
- Blueprint selection through command and chat.

### Survival Automation

- Automatic log gathering and nearby exposed ore mining.
- Basic crafting of bread, sticks, stone axes, and stone pickaxes.
- Farm maintenance in survive mode: harvest mature crops and replant nearby farmland.
- Night shelter priority and basic recovery behavior.

### Client Features

- Player-style model rendering instead of zombie rendering.
- Owner skin fallback when skin data is available on the client.
- GUI control panel.
- Voice pipeline: microphone capture, speech-to-text request, chat injection, and text-to-speech playback.

## Commands

### Spawn

- `/aiplayers spawn Alex`

### Mode

- `/aiplayers mode @e[type=aiplayers:ai_player,limit=1,sort=nearest] follow`

Supported modes:

- `idle`
- `follow`
- `guard`
- `gather_wood`
- `mine`
- `explore`
- `build_shelter`
- `survive`

### Action

- `/aiplayers action @e[type=aiplayers:ai_player,limit=1,sort=nearest] jump`

Supported actions:

- `jump`
- `crouch`
- `stand`
- `look_up`
- `look_down`
- `look_owner`

### Status / Memory / Plan

- `/aiplayers status @e[type=aiplayers:ai_player,limit=1,sort=nearest]`
- `/aiplayers memory @e[type=aiplayers:ai_player,limit=1,sort=nearest]`
- `/aiplayers plan @e[type=aiplayers:ai_player,limit=1,sort=nearest]`

### Blueprint

- `/aiplayers blueprint @e[type=aiplayers:ai_player,limit=1,sort=nearest] shelter`

Supported blueprints:

- `shelter`
- `cabin`
- `watchtower`

### AI API

- `/aiplayers api status`
- `/aiplayers api reload`
- `/aiplayers api enable`
- `/aiplayers api disable`

## Chat Control

Examples:

- `@Alex follow me`
- `@Alex protect me`
- `@Alex chop wood`
- `@Alex mine nearby ore`
- `@Alex build a shelter`
- `@Alex build a cabin`
- `@Alex build a watchtower`
- `@Alex survive`
- `@Alex jump`
- `@Alex crouch`
- `@Alex look up`
- `@Alex memory`
- `@Alex plan`
- `@Alex stop`

Chinese natural-language commands are also supported by the local parser.

## Client Hotkeys

- `H`: open the AI control panel
- `V`: start / stop voice recording and send the transcript to the currently selected AI

## AI API Config

The mod creates `config/aiplayers-api.json` after first run.

Default structure:

```json
{
  "enabled": false,
  "provider": "openai-compatible",
  "url": "https://api.openai.com/v1/chat/completions",
  "apiKey": "",
  "model": "",
  "timeoutMs": 4000
}
```

## Voice Config

The client creates `config/aiplayers-voice.json` after first run.

Important fields:

- `enabled`
- `defaultTarget`
- `sttUrl`
- `sttApiKey`
- `sttModel`
- `sttLanguage`
- `ttsUrl`
- `ttsApiKey`
- `ttsModel`
- `ttsVoice`
- `autoSpeakReplies`

The current implementation expects OpenAI-compatible endpoints:

- STT: `/v1/audio/transcriptions`
- TTS: `/v1/audio/speech`

## Notes

- Server-side logic drives core AI behavior.
- Client-side rendering, GUI, and voice features are optional enhancements.
- The project currently builds successfully with Forge 61.1.3 on Minecraft 1.21.11.
- Cross-dimension routing and more advanced long-horizon planning are not finished yet.