# Atlas 工作台配置表

本表适用于旧版「FantasyTruth：无限主世界（实验）」预设。「FantasyTruth：阿比斯探窟」使用原版世界生成配置，预设定义在 `data/stratacubes/worldgen/world_preset/expedition.json`。主世界高度由 `dimension_type/expedition.json` 定义，0.6.1 的噪声配置为 `worldgen/noise_settings/expedition_coast.json`。

## 入口和批量应用

在 **创建世界 → 世界 → 世界类型「FantasyTruth：无限主世界（实验）」→ 自定义** 打开工作台。地形、群系、矿脉、结构和掉落都属于同一份草稿。可以先改多个表格，再按一次 **应用整套**。工作台会为这个世界生成并启用一个数据包，地形参数同时写入该世界的主世界生成器。

在主菜单的 **模组 → FantasyTruth → 配置** 也可打开工作台，校验、保存或导出预设。**JSON** 页可直接粘贴整份配置。各分类的 **本段 JSON** 可批量新增、替换或删除数组条目。表格筛选接受键名、规则 ID 和中文地形名称。矿脉页的 **批量倍率** 一次调整全部矿脉的概率，结果最大为 1。

| 操作 | 文件或结果 |
| --- | --- |
| 保存 JSON | 当前实例下 `config/stratacubes/studio.json`。可用普通文本编辑器修改，再按「读取 JSON」。 |
| 导出 ZIP | `config/stratacubes/atlas-studio.zip`。在创建世界的数据包界面可一次启用所有设置。ZIP 根目录也保存了完整的 `atlas-studio.json`。 |
| 应用整套 | 把当前草稿装入新世界。重新打开同一个创建界面的工作台时可继续编辑。 |
| 预览 | 默认用当前世界种子采样 Atlas 的地表高度和群系区域。支持切换图层、平移和缩放。预览框里的种子只用于预览，创建世界使用原版世界种子栏。建筑、洞穴、地下矿脉和表面方块细节由游戏分阶段生成。 |

已有 Atlas 存档可以启用导出的数据包并重新进档。`content` 和 `loot_tables` 从启用的数据包读取，内容规则作用于后续生成的区块。已有世界的 `terrain` 和 `biomes` 随主世界生成器保存在存档里。要同时应用新的地形与群系配置，使用工作台创建新世界。已有区块和已开箱库存继续使用存档数据。

## 顶层 JSON

| 键 | 类型 | 内容 |
| --- | --- | --- |
| `format` | 整数 | 工作台格式版本，填 `1`。 |
| `terrain` | 对象 | 地形、气候和整体密度参数。 |
| `biomes` | 对象 | 13 个 Atlas 区域到 Minecraft 群系 ID 的映射。 |
| `content` | 对象 | `deposits`、`landmarks` 和 `fixed_sites` 三组规则。 |
| `loot_tables` | 对象 | 键是带命名空间的战利品表 ID，值是完整原版战利品表。 |

## 地形 `terrain`

长度单位为格。`horizontal_scale` 同时缩放主要地貌与汇流网格。下表的默认值适用于 0.5 新世界。已有存档中缺失的 `runoff_strength` 按 0 读取，缺失的 `shape` 沿用旧地形，两者都保留原生成行为。

| 键 | 类型 | 默认 | 范围 | 作用 |
| --- | --- | ---: | --- | --- |
| `sea_level` | 整数 | 62 | −40000 至 40000 | 全局海平面，也平移陆地基准。 |
| `horizontal_scale` | 小数 | 1 | 0.25–16 | 大地貌横向尺度。增大时山谷和海岸过渡更宽。 |
| `relief` | 小数 | 1 | 0–20 | 相对海平面的起伏倍率。 |
| `shape.summit_limit` | 整数 | 420 | 0–4000 | 山峰相对海平面的平滑上限。0 切回旧地形公式。 |
| `shape.land_bias` | 小数 | 0.24 | −0.35 至 0.65 | 陆海分界偏移。正值增加陆地、减少开阔海洋。 |
| `shape.mountain_frequency` | 小数 | 0.22 | 0–1 | 山脉分区的出现倾向。0 关闭山脉抬升，地形仍保留丘陵和冲沟。 |
| `foothill_detail` | 小数 | 1 | 0–8 | 中小尺度山麓与微地形。 |
| `rock_detail` | 小数 | 1 | 0–8 | 陡坡上的层纹、岩坎和露头。 |
| `ravine_depth` | 小数 | 36 | 0–240 | 局部冲沟和汇流切蚀的基础深度。 |
| `ravine_width` | 小数 | 0.25 | 0.025–0.9 | 冲沟宽度系数，越高越宽。 |
| `runoff_strength` | 小数 | 1 | 0–2 | 下坡汇流附加切蚀强度，0 关闭。 |
| `runoff_spacing` | 整数 | 48 | 16–128 | 汇流采样网格。小值处理更细的谷地。 |
| `runoff_steps` | 整数 | 14 | 4–32 | 一滴样本雨水最多向下游传播的格点数。 |
| `caves` | 小数 | 1 | 0–2 | 三维洞穴强度，0 关闭。 |
| `temperature_offset` | 小数 | 0 | −2–2 | 整体气温偏移。 |
| `moisture_offset` | 小数 | 0 | −2–2 | 整体湿度偏移。 |
| `tree_density` | 小数 | 0.85 | 0–1 | 各生态区域的树木密度倍率。 |
| `settlement_spacing` | 整数 | 144 | 64–4096 | 住宅规则省略 `spacing` 时的候选间距。 |
| `settlement_density` | 小数 | 0.65 | 0–1 | 住宅规则的整体概率倍率。 |

汇流按全球坐标的八邻域最陡坡降累积带衰减的降雨，并沿高汇流量的线段切出谷槽。每个计算分块带足以覆盖最大路径长度的外扩区域，区块接缝不会截断计算。内陆连通水面、洼地溢流和全流域动态水文仍待实现。 [水文地形生成](https://perso.liris.cnrs.fr/apeytavi/website/publication/hal-01339224/) · [构造抬升与河流侵蚀](https://perso.liris.cnrs.fr/apeytavi/website/publication/hal-01262376/)

三个 `shape` 字段在 JSON 里写在 `terrain.shape` 对象下。地形上限只约束生成的地表。Atlas 的建造高度与垂直分区保持全局 Y 寻址。

## 群系 `biomes`

保留所有区域键。每个值可指向当前已启用数据包中的群系 ID，例如把 `mistwood` 改为 `minecraft:forest`。地表材料与 Atlas 树木仍按区域分类决定。

| 区域键 | 默认生态 |
| --- | --- |
| `shelf_sea` | 陆架浅海 |
| `estuary` | 潮汐河口 |
| `floodplain` | 冲积泛滥平原 |
| `steppe` | 大陆草原 |
| `dune_sea` | 沙丘海 |
| `rainshadow_scrub` | 雨影灌丛 |
| `mistwood` | 雾林 |
| `monsoon_forest` | 季风雨林 |
| `boreal_woodland` | 北方针叶林 |
| `alpine_heath` | 高山荒原 |
| `glacial_cirque` | 冰斗 |
| `karst_depths` | 喀斯特深层 |
| `cloud_barrens` | 云上荒域 |

## 矿脉 `content.deposits[]`

| 键 | 类型 | 默认与范围 |
| --- | --- | --- |
| `id` | 字符串 | 必填。同类 ID 唯一，参与种子派生。 |
| `block` | 方块 ID | 必填。矿脉在地面及正 Y 的方块。 |
| `below_zero_block` | 方块 ID | 可选。负 Y 用的变体，省略则使用 `block`。 |
| `min_y`、`max_y` | 整数 | 必填。包含端点的全局 Y，使用 Atlas 的 64 位高度范围。 |
| `attempts` | 整数 | 默认 1，范围 0–32。每个 16³ 候选格的尝试次数。 |
| `chance` | 小数 | 默认 1，范围 0–1。每次尝试的概率。 |
| `radius` | 整数 | 默认 3，范围 1–16。矿脉长半径。 |
| `minimum_cover` | 整数 | 默认 3，范围 0–128。最少覆土厚度。 |
| `replace` | 方块 ID 数组 | 默认石头、深板岩、凝灰岩、安山岩和方解石。 |

## 结构 `content.landmarks[]` 和 `content.fixed_sites[]`

| 地标规则键 | 类型 | 默认与范围 |
| --- | --- | --- |
| `id` | 字符串 | 必填。同类 ID 唯一，可用于 `/strata locate <id>`。 |
| `kind` | 枚举 | 必填。`homestead`、`watchtower`、`ruin` 或 `mine`。 |
| `spacing` | 整数 | 范围 40–4096。省略时住宅继承 `settlement_spacing`，其他地标用 96。 |
| `chance` | 小数 | 默认 0.65，范围 0–1。设为 0 时仅生成对应的固定地点。 |
| `max_slope` | 小数 | 默认 0.8，范围 0–5。自动地点允许的坡度。 |
| `wall` | 方块 ID | 默认 `minecraft:cobblestone`。 |
| `floor` | 方块 ID | 默认 `minecraft:spruce_planks`。 |
| `loot_table` | 战利品表 ID | 可选。省略则没有该结构的战利品箱。 |

| 固定地点键 | 类型 | 作用 |
| --- | --- | --- |
| `landmark` | 字符串 | 必填，引用 `landmarks` 中一条规则的 `id`。 |
| `x`、`z` | 整数 | 必填，范围 −29999950 至 29999950。 |
| `y` | 整数 | 可选，绝对地板高度。省略会贴地。 |

## 战利品 `loot_tables`

工作台保留原版 JSON 结构。常用参数包括 `pools[].rolls` 抽取次数、`entries[].name` 物品 ID、`weight` 权重、`functions` 数量及附加规则。比如同一套预设可同时设置 `stratacubes:chests/watchtower` 和 `minecraft:blocks/diamond_ore`，分别改变瞭望塔箱子和钻石矿挖掘掉落。 [Forge 1.20.1 战利品表](https://docs.minecraftforge.net/en/1.20.1/resources/server/loottables/)
