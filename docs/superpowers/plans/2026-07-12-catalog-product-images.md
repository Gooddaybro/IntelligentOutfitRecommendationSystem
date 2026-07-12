# 全量商品目录图片补齐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让全部演示商品的后端 `mainImageUrl` 都能解析为匹配的本地服装图片。

**Architecture:** Java 与 Flyway 中现有的相对图片 URL 保持不变。把从 Unsplash 选取的服装图作为静态 JPG 存放在 Vite 的 `public/images/products` 目录，并以机器可读的来源清单记录每张图片的原始搜索页和许可。

**Tech Stack:** Spring Boot/Flyway 种子 SQL、React/Vite 静态资源、Node.js 内置测试框架、Unsplash 图片搜索。

## Global Constraints

- 不改动 Java API、数据库模型、商品名称、价格、库存或推荐逻辑。
- 仅使用可在 Unsplash License 下使用的图片，并为每一个本地商品图保留对应的 Unsplash 搜索来源。
- 所有图片必须为本地 JPG，路径必须与 `V10__seed_extended_demo_data.sql` 的 `main_image_url` 完全一致。
- 来源清单必须覆盖 37 个 V10 商品；3 个既有 SVG 基础商品继续沿用现有文件。

---

### Task 1: 添加商品图片完整性回归测试

**Files:**
- Create: `frontend/scripts/product-catalog-images.test.mjs`
- Test: `frontend/scripts/product-catalog-images.test.mjs`

**Interfaces:**
- Consumes: `backend/src/main/resources/db/migration/V10__seed_extended_demo_data.sql` 中的 `/images/products/*.jpg` URL。
- Produces: `node --test frontend/scripts/product-catalog-images.test.mjs`，在任何图片缺失、文件不是 JPG、来源记录不全或许可不合规时失败。

- [x] **Step 1: 写入失败测试**

```js
import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { resolve, basename } from "node:path";
import test from "node:test";

const PROJECT_ROOT = resolve(import.meta.dirname, "../..");
const seedFile = resolve(PROJECT_ROOT, "backend/src/main/resources/db/migration/V10__seed_extended_demo_data.sql");
const imageDirectory = resolve(PROJECT_ROOT, "frontend/public/images/products");
const sourcesFile = resolve(PROJECT_ROOT, "docs/assets/product-image-sources.csv");

test("each seeded catalog image is a local JPEG with a permissive source", () => {
  const seedUrls = new Set([...readFileSync(seedFile, "utf8").matchAll(/'(?<url>\/images\/products\/[^']+\.jpg)'/g)].map(({ groups }) => groups.url));
  assert.equal(seedUrls.size, 37);

  const [header, ...rows] = readFileSync(sourcesFile, "utf8").trim().split("\n");
  assert.equal(header, "image_path,product_name,source_page,license");
  const sources = new Map(rows.map((row) => {
    const [imagePath, productName, sourcePage, license] = row.split(",");
    return [imagePath, { productName, sourcePage, license }];
  }));

  assert.deepEqual(new Set(sources.keys()), seedUrls);
  for (const imageUrl of seedUrls) {
    const imagePath = resolve(imageDirectory, basename(imageUrl));
    assert.ok(existsSync(imagePath), `missing static asset: ${imageUrl}`);
    assert.deepEqual(readFileSync(imagePath).subarray(0, 3), Buffer.from([0xff, 0xd8, 0xff]), `not a JPEG: ${imageUrl}`);
    assert.ok(["Unsplash License"].includes(sources.get(imageUrl).license));
    assert.ok(sources.get(imageUrl).sourcePage.startsWith("https://unsplash.com/"));
  }
});
```

- [x] **Step 2: 运行测试，确认它因缺少资源而失败**

Run: `node --test frontend/scripts/product-catalog-images.test.mjs`

Expected: FAIL，指出 `docs/assets/product-image-sources.csv` 或某个静态 JPG 不存在。

### Task 2: 选图、下载并记录来源

**Files:**
- Create: `frontend/public/images/products/{37 个 V10 main_image_url 对应的 .jpg 文件}`
- Create: `docs/assets/product-image-sources.csv`

**Interfaces:**
- Consumes: Task 1 从 SQL 解析出的 37 个文件名。
- Produces: 37 个以 JPEG 魔数开头的静态文件，以及每个图片 URL 对应一行 `image_path,product_name,source_page,license` 的来源记录。

- [x] **Step 1: 通过 Unsplash 搜索并筛选资源**

对每个商品按其品类和用途搜索（例如 `oxford shirt man`, `linen short sleeve shirt`, `hoodie`, `cardigan`, `business blazer`, `puffer jacket`, `straight jeans`, `chino pants`, `cotton shorts`, `A-line skirt`, `sport T-shirt`, `windbreaker`, `sweatshirt`, `striped shirt`, `wool sweater`, `denim jacket`, `polo shirt`, `pleated skirt`, `nylon shorts`, `dress shirt`, `trousers`, `trench coat`）。下载搜索结果中与商品一致的服装图；同一合适图片可以用于同款不同库存/状态的边界商品。

```bash
curl -k -L -sS 'https://unsplash.com/s/photos/polo-shirt'
```

- [x] **Step 2: 把每个已选来源转换为项目要求的 JPG 文件名**

下载选定文件到临时目录；如来源不是 JPEG，以 macOS `sips` 转为 JPEG。每个目标名称由 SQL 路径决定，例如 `polo-commute-minimal-main.jpg`、`tshirt-sport-quickdry-main.jpg`、`skirt-commute-pleated-main.jpg`。压缩品质使用 `sips --setProperty formatOptions 80`，使商品卡片加载保持轻量。

```bash
sips -s format jpeg --setProperty formatOptions 80 /tmp/source-image.png --out frontend/public/images/products/polo-commute-minimal-main.jpg
```

- [x] **Step 3: 写入来源清单**

建立 `docs/assets/product-image-sources.csv`，表头必须为以下四列，并且每个 V10 图片 URL 恰有一行：

```csv
image_path,product_name,source_page,license
/images/products/polo-commute-minimal-main.jpg,极简通勤Polo衫,https://unsplash.com/s/photos/polo-shirt,Unsplash License
```

- [x] **Step 4: 运行完整性测试，确认通过**

Run: `node --test frontend/scripts/product-catalog-images.test.mjs`

Expected: PASS，`test_each_seeded_catalog_image_is_a_local_jpeg_with_a_permissive_source` 成功。

### Task 3: 验证静态资源可参与前端构建

**Files:**
- Test: `frontend/package.json`

**Interfaces:**
- Consumes: Task 2 中本地图片文件与来源清单。
- Produces: Vite 生产构建成功；Task 1 的回归测试已逐一验证全部 37 个 URL 有对应的 JPEG 文件。

- [x] **Step 1: 运行前端生产构建**

Run: `npm run build`

Expected: exit code 0，输出包含 `built in`。

- [x] **Step 2: 运行图片完整性测试与完整前端端到端测试**

Run: `node --test scripts/product-catalog-images.test.mjs && npm run test:e2e`

Expected: 两个命令均以 exit code 0 完成。
