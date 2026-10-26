# 健身计划 (Fitness_Plan) V3

Android 原生力量训练记录应用：按计划轮换训练日、记录每次的重量/组数/次数，并根据历史数据给出各肌群的力量评分。

## 技术栈

| 组件 | 用途 |
| --- | --- |
| Kotlin + Jetpack Compose (Material 3) | 全部界面，浅色/深色跟随系统 |
| Room + Flow | 本地数据库，界面随数据自动刷新 |
| DataStore | 偏好设置与身体数据 |
| Navigation Compose | 底部四个标签 + 二级页面 |
| sh.calvin.reorderable | 列表拖拽排序 |

图表（重量趋势折线、六边形雷达图）用 Compose Canvas 自绘，不依赖第三方图表库。

## 目录结构

```
app/src/main/java/com/example/fitness_plan/
├── FitnessApp.kt            Application + 依赖容器（数据库、仓库）
├── MainActivity.kt
├── data/
│   ├── db/                  Room 实体、DAO、数据库
│   ├── model/               MuscleGroup、WeightUnit
│   ├── FitnessRepository.kt 全部业务规则（事务内完成多表操作）
│   ├── SettingsRepository.kt
│   ├── BackupManager.kt     JSON 备份/恢复、CSV 报表
│   └── SeedData.kt          首次安装的动作库与推拉腿计划
├── domain/StrengthAnalyzer.kt  1RM 估算与雷达评分（纯 Kotlin，有单元测试）
└── ui/
    ├── workout/  训练（首页）
    ├── history/  历史
    ├── stats/    分析
    ├── settings/ 我的（设置、数据管理）
    ├── plans/    计划与训练日模板编辑
    ├── library/  动作库
    ├── components/ 通用组件、图表、底部弹窗
    └── theme/
```

## 数据模型

- `exercises`：动作库，唯一的名称/肌群来源；删除只做归档，历史仍可显示。
- `plans` → `plan_days` → `plan_day_exercises`：计划、训练日（有顺序）、每天的模板动作及默认组数/次数/重量。`plans.currentDayId` 记录下次练哪一天。
- `active_exercises`：今天训练台上的卡片，`inPlan` 区分模板动作和临时动作。
- `workout_sessions` → `workout_entries`：每次完成的训练及其动作记录，带计划/训练日名称快照。

重量一律以 kg 存储，显示时按设置换算为 lbs。

### 修改表结构

`AppDatabase.version` + 1，编写 `Migration` 并 `addMigrations(...)`。Room 会把每个版本的 schema 导出到 `app/schemas/`，请一并提交。**不要**使用 `fallbackToDestructiveMigration`，否则升级失败时会清空用户数据。

## 核心流程

1. 启用计划后，训练页按当前训练日的模板生成卡片；重量/组数/次数优先沿用该动作最近一次的实际记录。
2. 点卡片标记完成，点数值修改，拖拽手柄排序（模板顺序同步更新）。
3. 结束训练：已完成（或全部）动作存入历史，自动切换到下一个训练日。

## 构建

```bash
./gradlew assembleDebug        # 调试版，包名带 .dev 后缀，可与正式版共存
./gradlew assembleRelease      # 正式版：R8 优化，使用本机调试签名（无需密码）
./gradlew testDebugUnitTest    # 单元测试
```

调试版的 Compose 没有经过优化，动画明显比正式版卡（实测拖拽排序约 6 成帧超时，正式版约 1 成且无严重卡顿）。判断流畅度请用正式版。

正式版使用本机 Android SDK 的调试签名（`~/.android/debug.keystore`）。换一台电脑打包时签名会不同，手机上需要先卸载旧版再安装。

## 力量评分

见 `domain/StrengthAnalyzer.kt` 与 `domain/LiftStandards.kt`：每组先估算 1RM（≤10 次 Brzycki，>10 次 Epley），再除以该动作的满分标准（体重 × 倍数，按动作名关键词识别，哑铃按单只计）。引体/双杠计入体重，不负重的核心动作按次数评分。肌群取最近 90 天表现最好的动作，100 分约为进阶水平。

## 备份格式

设置 → 备份全部数据，生成 `下载/FitnessPlan/FitnessPlan_<时间>.json`（`version: 2`，包含动作库、计划、今日训练和历史）。V2.x 旧版的备份缺少动作库，无法导入。
