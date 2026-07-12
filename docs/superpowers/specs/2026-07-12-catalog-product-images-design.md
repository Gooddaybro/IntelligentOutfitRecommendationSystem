# 全量商品目录图片补齐设计

## 目标

为 Java 后端演示商品目录中所有商品补齐真实服装图片，使 `mainImageUrl` 指向的本地静态资源均可被前端加载。

## 范围

- 覆盖 `V10__seed_extended_demo_data.sql` 中的 37 个 `product_spu` 商品，以及现有 3 个基础商品。
- 图片按商品的名称、品类、描述选择公开图库中的对应服装照片。
- 图片保存到 `frontend/public/images/products/`，文件名与数据库中已有的 `/images/products/<name>.jpg` 完全一致。
- 不改变 Java API、数据库字段、商品价格或推荐逻辑。

## 方案

使用来源明确、可在 Unsplash License 下使用的免费图库图片，下载后作为前端静态资源随项目提供。后端继续返回既有相对 URL；Vite 会把 `public` 下的文件映射到同名 URL，因此 Java、前端组件及 Flyway 数据无需修改。

选择本地资源而不是第三方外链，避免网络、跨域、限流或源站下线再次造成图片空白。

## 数据流

1. Flyway 种子数据中的 `product_spu.main_image_url` 返回例如 `/images/products/polo-commute-minimal-main.jpg`。
2. 前端商品卡片将此 URL 放入 `img.src`。
3. Vite 从 `frontend/public/images/products/polo-commute-minimal-main.jpg` 提供对应图片。

## 错误处理

- 下载后逐个检查所有种子数据引用的图片文件存在且可解析为有效图片。
- 不使用远程运行时图片 URL，因此无需新增前端降级或跨域处理。

## 验证

- 脚本比对 SQL 中的所有 `/images/products/*.jpg` 引用与本地文件，确保无缺失。
- 执行前端生产构建，确认静态资源不会影响构建。
- 启动或既有端到端测试中检查推荐卡片的图片 `naturalWidth` 大于 0。

## 非目标

- 不为同一商品新增多角度图片。
- 不修改商品数据模型或图片上传能力。
- 不替换现有 SVG 基础商品图片，除非其数据库 URL 另有引用。
