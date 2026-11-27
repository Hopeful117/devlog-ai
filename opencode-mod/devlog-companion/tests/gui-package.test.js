import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const packageManifest = new URL("../package.json", import.meta.url);
const productionBundle = new URL("../dist/gui-experimental.cjs", import.meta.url);

test("visual milestone exposes only the experimental GUI plugin part", async () => {
  const manifest = JSON.parse(await readFile(packageManifest, "utf8"));
  assert.equal(manifest.name, "devlog-companion");
  assert.deepEqual(manifest.exports, {
    "./gui-experimental": "./dist/gui-experimental.cjs",
  });
  assert.deepEqual(manifest.files, [
    "src/gui.tsx",
    "src/gui-presence.js",
    "src/presentation.js",
    "src/devlog-mark.svg",
    "dist",
  ]);
});

test("ordinary GUI bundle does not expose simulated preview controls", async () => {
  const bundle = await readFile(productionBundle, "utf8");
  assert.doesNotMatch(bundle, /Simulated preview/i);
});
