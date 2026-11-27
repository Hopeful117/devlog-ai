import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const packageManifest = new URL("../package.json", import.meta.url);
const productionBundle = new URL("../dist/gui-experimental.cjs", import.meta.url);
const tuiEntry = new URL("../src/tui.tsx", import.meta.url);

test("package exposes independent GUI and native TUI plugin parts", async () => {
  const manifest = JSON.parse(await readFile(packageManifest, "utf8"));
  assert.equal(manifest.name, "devlog-companion");
  assert.deepEqual(manifest.exports, {
    "./gui-experimental": "./dist/gui-experimental.cjs",
    "./tui": "./src/tui.tsx",
  });
  assert.deepEqual(manifest.files, [
    "src/gui.tsx",
    "src/tui.tsx",
    "src/gui-presence.js",
    "src/tui-projection.js",
    "src/context-inspector.js",
    "src/presentation.js",
    "src/devlog-mark.svg",
    "dist",
  ]);
});

test("ordinary GUI bundle does not expose simulated preview controls", async () => {
  const bundle = await readFile(productionBundle, "utf8");
  assert.doesNotMatch(bundle, /Simulated preview/i);
});

test("TUI adapter uses only the local cache and adds no Companion transport", async () => {
  const source = await readFile(tuiEntry, "utf8");
  assert.match(source, /@opencode\/plugin\/tui/);
  assert.match(source, /session\.message\.list\(sessionID\)/);
  assert.doesNotMatch(source, /message\.sync|client\.|fetch\(|storage\.store/);
  assert.doesNotMatch(source, /gui\.tsx|@opencode\/plugin\/gui/);
});
