<div align="center">
  <img src="new_logo.png" alt="MyAgent" width="160" />

# MyAgent · 端侧 AI 助手

**Android 端侧 AI Agent —— 理解 → 决策 → 执行 → 反思的完整闭环**

*An on-device AI agent that operates your phone like a human: apps, gestures, and system settings.*

<img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white">
<img alt="Flutter" src="https://img.shields.io/badge/Flutter-UI-02569B?logo=flutter&logoColor=white">
<img alt="Android" src="https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white">
<a href="https://github.com/omnimind-ai/OpenOmniBot"><img alt="Based on OpenOmniBot" src="https://img.shields.io/badge/based%20on-OpenOmniBot-8A2BE2"></a>

</div>

> 本项目是基于开源项目 **[OpenOmniBot](https://github.com/omnimind-ai/OpenOmniBot)** 的**融合开发版**（个人二次开发）：在继承上游端侧 AI Agent 全部能力的基础上，进行了持续的定制开发与功能集成。

## 📖 项目简介

MyAgent 是一个运行在 Android 设备上的端侧 AI Agent。与传统 AI 聊天应用不同，它不止于"对话"，而是关注 **从理解 → 决策 → 执行 → 反思的完整闭环**：理解用户意图，规划任务步骤，直接操作手机界面（App、手势、系统设置），并根据执行结果自我修正。

- **端侧运行**：支持本地模型推理（llama.cpp / MNN 后端），敏感数据不出设备
- **真机操作**：无障碍服务 + 视觉语言模型（VLM）理解屏幕，像真人一样操作手机
- **可扩展**：技能系统、MCP、Alpine 环境、内置终端、浏览器

## ✨ 核心能力

| 能力 | 说明 |
|---|---|
| 🧠 **端侧模型推理** | 本地运行 LLM/VLM（OmniInfer 运行时，llama / MNN 双后端），支持模型下载管理与自动预热 |
| 📱 **手机任务自动化** | 通过无障碍服务与视觉模型理解屏幕内容，自动操作 App、手势与系统设置，支持任务接管与中断 |
| 🛠 **技能系统** | 发送仓库链接即可安装技能（Skill），能力持续扩展；内置技能商店 |
| 🧩 **工具生态** | 文件读写、工作区浏览、浏览器操作、内置 Linux 终端（Alpine）、MCP 集成 |
| 💾 **记忆系统** | 短期 + 长期记忆，支持嵌入（Embedding）检索 |
| ⏰ **任务与调度** | 定时任务、子代理流程、闹钟、日历创建/查询/修改、音频播放控制 |
| 🤖 **Codex 集成** | App 内直接连接 Codex App Server，支持多线程会话与斜杠命令 |
| 🎨 **原生体验融合** | 悬浮球与半屏表面（Overlay）、Flutter 与原生 Kotlin 混合渲染 |

## 📱 界面展示

<p align="center">
  <img src="docs/tutorial/one.png" alt="对话界面" width="240" />
  <img src="docs/tutorial/eight.png" alt="自动化任务执行" width="240" />
  <img src="docs/tutorial/three.png" alt="模型服务配置" width="240" />
  <img src="docs/tutorial/nine.png" alt="本地模型推理" width="240" />
</p>

<p align="center">
  <sub>对话与工具调用 · 手机自动化任务 · 多服务商配置 · 本地模型推理</sub>
</p>

## 🏗 技术架构

<p align="center">
  <img src="docs/pic/architect.svg" alt="Architecture" width="90%" />
</p>

```text
myagent/
├── app/                        # Android 宿主：入口、Agent 编排、系统能力、MCP、服务
├── ui/                         # Flutter UI：对话、设置、任务、记忆与应用界面
├── baselib/                    # 共享基础库：数据库、存储、网络、模型配置、OCR、权限
├── assists/                    # 自动化引擎：任务调度、状态机、视觉检测、执行控制
├── accessibility/              # 无障碍与屏幕感知：无障碍服务、截图、投屏
├── omniintelligence/           # AI 抽象层：模型协议、任务状态、请求/响应模型
├── uikit/                      # 原生悬浮 UI：悬浮球、面板、半屏表面
├── third_party/omniinfer/      # 本地推理运行时（子模块）
└── ReTerminal/core/            # 内嵌终端模块（第三方组件，MIT）
```

## 🚀 构建

### 环境要求

- Flutter SDK `3.9.2+`
- JDK `11+`（推荐 17）
- Android SDK / NDK

### 获取代码

```bash
git clone https://github.com/soyoung5565/myagent.git
cd myagent

# 仅在构建完整 omniinfer 本地推理版时需要初始化子模块
git submodule update --init third_party/omniinfer
git -C third_party/omniinfer submodule update --init framework/mnn
git -C third_party/omniinfer submodule update --init framework/llama.cpp

cd ui && flutter pub get
```

### 构建并安装

```bash
cd ..

# standard 精简版（不含本地推理）
./gradlew :app:installDevelopStandardDebug -Ptarget=lib/main_standard.dart

# omniinfer 完整版（含本地推理）
./gradlew :app:installDevelopOmniinferDebug -Ptarget=lib/main_omniinfer.dart
```

> 如遇 `Could not read script '.../ui/.android/include_flutter.groovy'`，执行 `flutter clean && flutter pub get` 后重试。

## 🔗 与上游项目的关系

本仓库为 **[OpenOmniBot](https://github.com/omnimind-ai/OpenOmniBot)** 的融合开发版。上游是一个由社区共建的端侧 AI Agent 开源项目；本仓库在继承其架构与能力的基础上进行个人二次开发与定制（包括 Codex App Server 集成等扩展），用于学习、研究与个人使用。

## 🙏 致谢

- [OpenOmniBot](https://github.com/omnimind-ai/OpenOmniBot) —— 本项目的基础框架与上游来源
- [ReTerminal](https://github.com/RohitKushvaha01/ReTerminal) —— 内嵌终端模块（MIT License）
- [OpenMinis / MinisSkills](https://github.com/OpenMinis) —— 技能生态

## 📄 License

详见 [LICENSE](LICENSE)。
