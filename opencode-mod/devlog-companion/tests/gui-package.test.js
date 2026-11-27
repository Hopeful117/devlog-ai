import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import { promisify } from "node:util";
import test from "node:test";

const execFileAsync = promisify(execFile);
const packageManifest = new URL("../package.json", import.meta.url);
const packageShrinkwrap = new URL("../npm-shrinkwrap.json", import.meta.url);
const productionBundle = new URL("../dist/gui-experimental.cjs", import.meta.url);
const tuiEntry = new URL("../src/tui.tsx", import.meta.url);

test("package exposes independent GUI and native TUI plugin parts", async () => {
  const manifest = JSON.parse(await readFile(packageManifest, "utf8"));
  assert.equal(manifest.name, "devlog-companion");
  assert.equal(manifest.version, "0.1.1");
  assert.deepEqual(manifest.exports, {
    "./gui-experimental": "./dist/gui-experimental.cjs",
    "./tui": "./src/tui.tsx",
  });
  assert.deepEqual(manifest.files, [
    "npm-shrinkwrap.json",
    "src/gui.tsx",
    "src/tui.tsx",
    "src/gui-presence.js",
    "src/tui-projection.js",
    "src/context-inspector.js",
    "src/presentation.js",
    "src/devlog-mark.svg",
    "dist",
  ]);
  assert.equal(manifest.dependencies["@opencode/plugin"], "2.4.0");
  assert.deepEqual(manifest.peerDependencies, {
    "@opentui/solid": "^0.9.4",
    "solid-js": "^1.9.12",
  });
  const shrinkwrap = JSON.parse(await readFile(packageShrinkwrap, "utf8"));
  assert.equal(shrinkwrap.lockfileVersion, 3);
  assert.equal(shrinkwrap.packages[""].version, manifest.version);
  assert.deepEqual(
    shrinkwrap.packages[""].peerDependencies,
    manifest.peerDependencies,
  );
  assert.deepEqual(shrinkwrap.packages[""].dependencies, manifest.dependencies);
});

test("packed artifact includes the lock and both independent plugin entrypoints", async () => {
  const { stdout } = await execFileAsync(
    "npm",
    ["pack", "--dry-run", "--json", "--ignore-scripts"],
    { cwd: new URL("..", import.meta.url) },
  );
  const [pack] = JSON.parse(stdout);
  const files = new Set(pack.files.map(({ path }) => path));
  for (const path of [
    "package.json",
    "npm-shrinkwrap.json",
    "dist/gui-experimental.cjs",
    "src/tui.tsx",
    "src/gui.tsx",
    "src/gui-presence.js",
    "src/tui-projection.js",
    "src/context-inspector.js",
    "src/presentation.js",
    "src/devlog-mark.svg",
  ]) {
    assert.ok(files.has(path), `packed artifact is missing ${path}`);
  }
  assert.ok(!files.has("src/server.js"));
  assert.ok(!files.has("package-lock.json"));
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
