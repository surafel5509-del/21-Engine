const test = require('node:test');
const assert = require('node:assert/strict');
const { createWorld, hitTest, color } = require('../../app/src/main/assets/web/runtime.js');

const shape = (id, x, y, width, height, type = 'BOX') => ({
  id, name: id, visible: true, locked: false,
  transform: { x, y, width, height, rotation: 0 },
  visual: { type, color: -1, text: '', assetId: null },
  motion: { type: 'NONE', speed: .6, amplitude: 56 },
  physics: null,
});
const scene = entities => ({
  id: 'scene', name: 'Test', background: -1,
  gravity: { x: 0, y: 720 }, camera: { x: 0, y: 0, zoom: 1 }, entities,
});

test('dynamic ball lands on static floor; tapping jumps; source stays unchanged', () => {
  const floor = shape('floor', 0, 100, 300, 20);
  floor.physics = { type: 'STATIC', velocity: { x: 0, y: 0 }, gravityScale: 1, bounce: 0 };
  const ball = shape('ball', 0, 0, 20, 20, 'CIRCLE');
  ball.physics = { type: 'DYNAMIC', velocity: { x: 0, y: 0 }, gravityScale: 1, bounce: 0 };
  const original = scene([floor, ball]);
  const world = createWorld(original);
  for (let i = 0; i < 180; i++) world.advance(1 / 60);
  assert.ok(Math.abs(world.scene.entities[1].transform.y - 80) < .01);
  assert.equal(world.scene.entities[1].physics.velocity.y, 0);
  assert.equal(original.entities[1].transform.y, 0);
  assert.equal(world.tap(0, 80), true);
  assert.equal(world.scene.entities[1].physics.velocity.y, -470);
  assert.equal(world.tap(3000, 3000), false);
});

test('hit testing follows layer, rotation, ellipse, and visibility', () => {
  const bottom = shape('bottom', 0, 0, 60, 60);
  const rotated = shape('rotated', 0, 0, 80, 20);
  rotated.transform.rotation = 90;
  const world = scene([bottom, rotated]);
  assert.equal(hitTest(world, 0, 30).id, 'rotated');
  assert.equal(hitTest(world, 20, 0).id, 'bottom');
  rotated.locked = true;
  assert.equal(hitTest(world, 0, 0).id, 'bottom');
  assert.equal(hitTest(world, 0, 0, true).id, 'rotated');
  bottom.visible = false;
  assert.equal(hitTest(world, 20, 0), null);
  const ellipse = shape('ellipse', 0, 0, 100, 50, 'CIRCLE');
  assert.equal(hitTest(scene([ellipse]), 48, 23), null);
});

test('motion is deterministic and long pauses are clamped', () => {
  const spinner = shape('spinner', 0, 0, 60, 60);
  spinner.motion = { type: 'SPIN', speed: 1, amplitude: 0 };
  const world = createWorld(scene([spinner]));
  for (let i = 0; i < 60; i++) world.advance(1 / 60);
  assert.ok(Math.abs(world.scene.entities[0].transform.rotation - 360) < .02);
  assert.equal(spinner.transform.rotation, 0);
  world.advance(100);
  assert.ok(world.scene.entities[0].transform.rotation <= 396.01);
});

test('ARGB scene colors convert to browser rgba without swapping channels', () => {
  assert.equal(color(0xFF102030 | 0), 'rgba(16,32,48,1)');
  assert.equal(color(0x8044AA11 | 0), `rgba(68,170,17,${128 / 255})`);
});
