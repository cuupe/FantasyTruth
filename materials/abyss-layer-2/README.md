# 第二层素材包

配合 StrataCubes 和 Minecraft Java 1.20.1 使用。哭丧鸟素材来自同一工作区的 `abis/entities`。三件结构是以参考场景为依据制作的 Minecraft 样件，尺寸是样件尺寸。

## 可用文件

| 内容 | 文件 |
| --- | --- |
| 哭丧鸟骨骼模型、贴图和动画 | [模型](corpse-weeper/e_weeper.geo.json)、[贴图](corpse-weeper/e_weeper.png)、[动画](corpse-weeper/e_weeper.animation.json)、[清单](corpse-weeper/e_weeper.json) |
| 可继续编辑的原始工程 | [Blockbench 工程](corpse-weeper/e_weeper.bbmodel) |
| 三件结构样件 | [Java 1.20.1 数据包](stratacubes-layer-2-structures.zip)、[生成脚本](build_structures.py) |

骨骼标识为 `geometry.entity/e_weeper`，贴图 128 × 128，共 21 根骨骼。动画使用 `animation.e_weeper.<动作>`，包含 `idle`、`walk`、`fly`、`attack`、`hurt`、`death`。本项目目前没有实体动画运行库。接入时可选择导入现有骨骼动画格式或转成原版模型层。拟声、幼鸟模型和实体行为待制作。

## 在测试世界预览

把 ZIP 放进测试存档的 `datapacks` 目录，执行 `/reload`。找一处空旷位置，开启作弊后执行 `/place template <结构 ID> ~ ~ ~`。也可把结构 ID 填入结构方块的加载模式。样件里的未保存位置会保留原方块，因此预览请用空地，接入地形时按下表对齐并预留空腔。

| 结构 ID | 大小（X × Y × Z） | 接入位置（相对模板原点） |
| --- | --- | --- |
| `stratacubes_l2_materials:weeper_nest` | 25 × 15 × 25 | 窝心 `(12,10,13)`，南侧进巢位置 `(12,11,22)`。后沿靠悬崖，窝口对着林海。 |
| `stratacubes_l2_materials:inverted_traverse` | 35 × 22 × 17 | 根桥从西 `(5,12,8)` 到东 `(30,15,6)`，上端岩棚自 Y=15 起，与洞顶相接。 |
| `stratacubes_l2_materials:heavens_waterfall` | 31 × 27 × 31 | 水池中心 `(15,5,17)`，上升水带中心 `(16,24,17)`。把崖口对准第三层下潜路线，盆地边缘留步行绕行空间。 |

天之瀑布的水带复用 `stratacubes:l2_updraft`。预览不会单独启动新的层间传送逻辑。倒悬峡谷复用现有辉苔和倒悬藤的模型与材质。

## 图像和设定参考

- 哭丧鸟与求救声遭遇见[动画第 5 话](https://miabyss.com/story05.html)。巢区构图取自本地 `abis/参考图/02_实体/e_weeper/ref05_Corpse-WeeperinitsnestAnime.jpg`。
- 悬岩和倒挂树根的关系见本地 `abis/资料库/01_地理/20_倒悬之森/images/01_inverted.jpg`。授权游戏的地狱渡提供根桥、攀爬和绕行的布局参考。[游戏地图记录](https://warewareguide.com/abyss/map2/#toc7)
- 天之瀑布采用授权游戏里通往大断层的地标位置。动画给出奥森指点的下潜竖穴。[第二层游戏地图](https://warewareguide.com/abyss/map2/)、[动画第 9 话](https://miabyss.com/story09.html)
