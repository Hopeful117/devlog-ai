import assert from "node:assert/strict";
import test from "node:test";

import {
  PRESENTATION_STATES,
  READY_PREVIEW_MS,
  normalizePresentationState,
  presentationDescriptor,
  transitionPresentationState,
} from "../src/presentation.js";

test("presentation states are the supported simulated inputs", () => {
  assert.deepEqual(PRESENTATION_STATES, [
    "IDLE",
    "WORKING",
    "READY",
    "ERROR",
    "DISCONNECTED",
  ]);
});

test("missing and unsupported state inputs safely normalize to IDLE", () => {
  for (const input of [undefined, null, "", "UNKNOWN", 4, {}]) {
    assert.equal(normalizePresentationState(input), "IDLE");
    assert.equal(presentationDescriptor(input).state, "IDLE");
  }
});

test("each supported presentation state has a distinct visual descriptor", () => {
  const descriptors = PRESENTATION_STATES.map((state) =>
    presentationDescriptor(state),
  );

  assert.deepEqual(
    descriptors.map((descriptor) => descriptor.state),
    PRESENTATION_STATES,
  );
  assert.equal(new Set(descriptors.map((descriptor) => descriptor.color)).size, 5);

  const error = presentationDescriptor("ERROR");
  const disconnected = presentationDescriptor("DISCONNECTED");
  assert.notEqual(error.color, disconnected.color);
  assert.equal(error.disconnected, false);
  assert.equal(disconnected.disconnected, true);
  assert.equal(disconnected.motion, "none");
});

test("reduced motion suppresses animation without removing state distinction", () => {
  const animated = PRESENTATION_STATES.map((state) =>
    presentationDescriptor(state),
  );
  const reduced = PRESENTATION_STATES.map((state) =>
    presentationDescriptor(state, true),
  );

  assert.ok(animated.some((descriptor) => descriptor.motion !== "none"));
  assert.ok(reduced.every((descriptor) => descriptor.motion === "none"));
  assert.deepEqual(
    reduced.map((descriptor) => descriptor.color),
    animated.map((descriptor) => descriptor.color),
  );
  assert.deepEqual(
    reduced.map((descriptor) => descriptor.disconnected),
    animated.map((descriptor) => descriptor.disconnected),
  );
});

test("preview selection, reset, and READY timeout are deterministic", () => {
  assert.equal(READY_PREVIEW_MS, 3000);
  let state = "IDLE";
  for (const selected of PRESENTATION_STATES) {
    state = transitionPresentationState(state, {
      type: "SELECT",
      state: selected,
    });
    assert.equal(state, selected);
  }

  assert.equal(transitionPresentationState("READY", { type: "READY_TIMEOUT" }), "IDLE");
  assert.equal(
    transitionPresentationState("WORKING", { type: "READY_TIMEOUT" }),
    "WORKING",
  );
  assert.equal(transitionPresentationState("ERROR", { type: "RESET" }), "IDLE");
  assert.equal(
    transitionPresentationState("IDLE", { type: "SELECT", state: "INVALID" }),
    "IDLE",
  );
  assert.equal(transitionPresentationState("WORKING", { type: "UNKNOWN" }), "WORKING");
});
