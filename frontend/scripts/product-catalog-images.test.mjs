import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { basename, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const PROJECT_ROOT = resolve(fileURLToPath(new URL("../..", import.meta.url)));
const seedFile = resolve(PROJECT_ROOT, "backend/src/main/resources/db/migration/V10__seed_extended_demo_data.sql");
const imageDirectory = resolve(PROJECT_ROOT, "frontend/public/images/products");
const sourcesFile = resolve(PROJECT_ROOT, "docs/assets/product-image-sources.csv");

test("each seeded catalog image is a local JPEG with a permissive source", () => {
  const seedUrls = new Set(
    [...readFileSync(seedFile, "utf8").matchAll(/'(?<url>\/images\/products\/[^']+\.jpg)'/g)].map(({ groups }) => groups.url)
  );
  assert.equal(seedUrls.size, 37);

  const [header, ...rows] = readFileSync(sourcesFile, "utf8").trim().replaceAll("\r", "").split("\n");
  assert.equal(header, "image_path,product_name,source_page,license");
  const sources = new Map(rows.map((row) => {
    const [imagePath, productName, sourcePage, license] = row.split(",");
    return [imagePath, { productName, sourcePage, license }];
  }));

  assert.deepEqual(new Set(sources.keys()), seedUrls);
  for (const imageUrl of seedUrls) {
    const imagePath = resolve(imageDirectory, basename(imageUrl));
    assert.ok(existsSync(imagePath), `missing static asset: ${imageUrl}`);
    assert.deepEqual(
      readFileSync(imagePath).subarray(0, 3),
      Buffer.from([0xff, 0xd8, 0xff]),
      `not a JPEG: ${imageUrl}`
    );
    assert.ok(["Unsplash License"].includes(sources.get(imageUrl).license));
    assert.ok(sources.get(imageUrl).sourcePage.startsWith("https://unsplash.com/"));
  }
});
