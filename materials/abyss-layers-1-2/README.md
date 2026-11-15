# 第一、二层 Blockbench 素材

[单件模型预览](preview.png) · [场景样件预览](scenes.png) · [占位图标重绘](reworked-icons.png)

| 内容 | 可编辑工程 | 说明 |
| --- | --- | --- |
| 隐秒 | [e_inbyo.bbmodel](inbyo/e_inbyo.bbmodel) | 13 根骨骼，128 × 128 贴图，独立红眼发光遮罩 |
| 驱行兽 | [e_ottobas.bbmodel](ottobas/e_ottobas.bbmodel) | 13 根骨骼，256 × 256 贴图，独立下颌、四肢和五段尾巴 |
| 落花黽 | [e_rohana.bbmodel](rohana/e_rohana.bbmodel) | 6 根骨骼，64 × 64 贴图，独立双翼和紫色腹部发光遮罩 |
| 断裂石化树桩 | [fossil_stump.bbmodel](fossil_stump/fossil_stump.bbmodel) | 断口、矿化年轮和不对称根部 |
| 嵌岩根系 | [fossil_root.bbmodel](fossil_root/fossil_root.bbmodel) | 层岩中露出的分叉根系 |
| 化石露头 | [fossil_outcrop.bbmodel](fossil_outcrop/fossil_outcrop.bbmodel) | 层岩外露的脊椎与肋骨 |
| 泉边莎草 | [spring_sedge.bbmodel](spring_sedge/spring_sedge.bbmodel) | 适用于浅水边缘的交叉面 |
| 附生水苔 | [spring_moss.bbmodel](spring_moss/spring_moss.bbmodel) | 带透明边缘的地面苔垫 |
| 层叠菌盖 | [shelf_fungus.bbmodel](shelf_fungus/shelf_fungus.bbmodel) | 可贴树干或岩面，菌褶使用独立贴图 |
| 树居化石群 | [fossil_grove.bbmodel](fossil_grove/fossil_grove.bbmodel) | 以石化树桩、崖壁化石和碎石露台组合的场景样件 |
| 林下泉池 | [forest_spring.bbmodel](forest_spring/forest_spring.bbmodel) | 带石岸、盘根巨木、菌盖和落花黽光点的场景样件 |

工程内嵌贴图。独立实体的 `.geo.json` 和 PNG、方块的 Java 版模型、方块状态模板与 PNG 位于 `src/main/resources/assets/stratacubes`。嵌岩根系、化石露头和层叠菌盖的方块状态使用水平 `facing`。发光遮罩也用于隐秒与落花黽的游戏渲染。三种实体已添加基础动画和栖息地生成，六种方块已注册。化石群与林下泉池的世界生成以场景样件为参考，经区块地形适配后放置，可在探窟世界的路线指引中查找。已生成区块保留原来的地形。

原作参考以漫画第 2 话化石群、漫画第 12 话隐秒、漫画第 3 卷卷末驱行兽，以及动画第 8 话的林下水域为依据。化石方块细节、岸边植被和样件布局为 Minecraft 改编。 [漫画第 2 话](https://ebookstore.sony.jp/title/00453659/id/BT000045365900200201/) · [隐秒图鉴](https://madeinabyss.fandom.com/wiki/Inbyo) · [驱行兽图鉴](https://madeinabyss.fandom.com/wiki/Ottobas) · [落花黽图鉴](https://madeinabyss.fandom.com/wiki/Rohana) · [动画第 8 话](https://miabyss.com/story08.html)
