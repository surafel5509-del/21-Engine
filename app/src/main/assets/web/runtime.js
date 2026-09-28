/* S Engine web player: runs entirely offline after the game ZIP is extracted. */
(function (root) {
  "use strict";
  const STEP = 1 / 60;
  const TAU = 2 * Math.PI;

  function clone(value) { return JSON.parse(JSON.stringify(value)); }
  function overlap(x, y, a, b) {
    return Math.abs(x - b.x) * 2 < a.width + b.width && Math.abs(y - b.y) * 2 < a.height + b.height;
  }
  function hitTest(scene, x, y, includeLocked) {
    for (let i = scene.entities.length - 1; i >= 0; i--) {
      const entity = scene.entities[i];
      if (!entity.visible || (entity.locked && !includeLocked)) continue;
      const t = entity.transform;
      const a = -t.rotation * Math.PI / 180;
      const dx = x - t.x, dy = y - t.y;
      const localX = dx * Math.cos(a) - dy * Math.sin(a);
      const localY = dx * Math.sin(a) + dy * Math.cos(a);
      const contains = entity.visual.type === "CIRCLE"
        ? (localX / (t.width / 2)) ** 2 + (localY / (t.height / 2)) ** 2 <= 1
        : Math.abs(localX) <= t.width / 2 && Math.abs(localY) <= t.height / 2;
      if (contains) return entity;
    }
    return null;
  }

  function createWorld(source) {
    const scene = clone(source);
    const starts = new Map(scene.entities.map(entity => [entity.id, clone(entity.transform)]));
    let elapsed = 0, accumulator = 0;
    function tick(dt) {
      elapsed += dt;
      const colliders = scene.entities.filter(e => e.visible && e.physics && e.physics.type === "STATIC");
      scene.entities = scene.entities.map(entity => {
        const t = { ...entity.transform };
        const original = starts.get(entity.id);
        const motion = entity.motion || { type: "NONE", speed: 0, amplitude: 0 };
        if (motion.type === "SPIN") t.rotation = original.rotation + elapsed * 360 * motion.speed;
        if (motion.type === "FLOAT") t.y = original.y + Math.sin(elapsed * motion.speed * TAU) * motion.amplitude;
        if (motion.type === "PATROL") t.x = original.x + Math.sin(elapsed * motion.speed * TAU) * motion.amplitude;
        const body = entity.physics;
        if (!body || body.type !== "DYNAMIC" || !entity.visible) return { ...entity, transform: t };
        let vx = body.velocity.x + scene.gravity.x * body.gravityScale * dt;
        let vy = body.velocity.y + scene.gravity.y * body.gravityScale * dt;
        let x = t.x + vx * dt, y = t.y;
        for (const wall of colliders) {
          const b = wall.transform;
          if (vx !== 0 && overlap(x, y, t, b)) {
            x = vx > 0 ? b.x - (b.width + t.width) / 2 : b.x + (b.width + t.width) / 2;
            vx = -vx * body.bounce;
          }
        }
        y += vy * dt;
        for (const wall of colliders) {
          const b = wall.transform;
          if (overlap(x, y, t, b)) {
            y = vy >= 0 ? b.y - (b.height + t.height) / 2 : b.y + (b.height + t.height) / 2;
            vy = -vy * body.bounce;
            if (Math.abs(vy) < 15) vy = 0;
          }
        }
        return { ...entity, transform: { ...t, x, y }, physics: { ...body, velocity: { x: vx, y: vy } } };
      });
    }
    return {
      get scene() { return scene; },
      advance(seconds) {
        if (!Number.isFinite(seconds) || seconds <= 0) return scene;
        accumulator += Math.min(seconds, .1);
        let steps = 0;
        while (accumulator >= STEP && steps < 6) { tick(STEP); accumulator -= STEP; steps++; }
        if (steps === 6) accumulator = 0;
        return scene;
      },
      tap(x, y) {
        const hit = hitTest(scene, x, y, true);
        if (!hit || !hit.physics || hit.physics.type !== "DYNAMIC") return false;
        hit.physics.velocity.y = -470;
        return true;
      },
    };
  }

  function color(argb) {
    const n = argb >>> 0;
    return `rgba(${(n >>> 16) & 255},${(n >>> 8) & 255},${n & 255},${(n >>> 24) / 255})`;
  }

  function boot() {
    const project = JSON.parse(document.getElementById("project-data").textContent);
    if (project.formatVersion !== 1 || !project.scenes.length) throw new Error("Unsupported S Engine project");
    const canvas = document.getElementById("game"), ctx = canvas.getContext("2d");
    if (!ctx) return;
    const images = new Map();
    for (const asset of project.assets) {
      const image = new Image();
      image.src = `assets/${encodeURIComponent(asset.id)}.img`;
      images.set(asset.id, image);
    }
    let index = Math.max(0, project.scenes.findIndex(s => s.id === project.activeSceneId));
    let world;
    let cssWidth = 1, cssHeight = 1, dpr = 1;
    document.title = `${project.name} · Made with S Engine`;
    document.getElementById("game-title").textContent = project.name;

    function selectScene(number) {
      index = (number + project.scenes.length) % project.scenes.length;
      world = createWorld(project.scenes[index]);
      document.getElementById("scene-name").textContent = world.scene.name;
      document.getElementById("scene-count").textContent = `Scene ${index + 1} of ${project.scenes.length}`;
    }
    document.getElementById("restart").addEventListener("click", () => selectScene(index));
    document.getElementById("previous").addEventListener("click", () => selectScene(index - 1));
    document.getElementById("next").addEventListener("click", () => selectScene(index + 1));
    selectScene(index);

    function resize() {
      const bounds = canvas.getBoundingClientRect();
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      cssWidth = Math.max(1, bounds.width);
      cssHeight = Math.max(1, bounds.height);
      canvas.width = Math.round(cssWidth * dpr);
      canvas.height = Math.round(cssHeight * dpr);
    }
    window.addEventListener("resize", resize);
    resize();
    canvas.addEventListener("pointerdown", event => {
      const rect = canvas.getBoundingClientRect();
      const camera = world.scene.camera;
      const x = camera.x + (event.clientX - rect.left - cssWidth / 2) / camera.zoom;
      const y = camera.y + (event.clientY - rect.top - cssHeight / 2) / camera.zoom;
      world.tap(x, y);
    });

    function draw() {
      const scene = world.scene;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.fillStyle = color(scene.background);
      ctx.fillRect(0, 0, cssWidth, cssHeight);
      ctx.translate(cssWidth / 2, cssHeight / 2);
      ctx.scale(scene.camera.zoom, scene.camera.zoom);
      ctx.translate(-scene.camera.x, -scene.camera.y);
      const scale = scene.camera.zoom;
      const left = scene.camera.x - cssWidth / (2 * scale), right = scene.camera.x + cssWidth / (2 * scale);
      const top = scene.camera.y - cssHeight / (2 * scale), bottom = scene.camera.y + cssHeight / (2 * scale);
      ctx.strokeStyle = "rgba(202,216,255,.09)";
      ctx.lineWidth = 1 / scale;
      ctx.beginPath();
      for (let x = Math.floor(left / 40) * 40; x <= right; x += 40) { ctx.moveTo(x, top); ctx.lineTo(x, bottom); }
      for (let y = Math.floor(top / 40) * 40; y <= bottom; y += 40) { ctx.moveTo(left, y); ctx.lineTo(right, y); }
      ctx.stroke();
      for (const entity of scene.entities) {
        if (!entity.visible) continue;
        const t = entity.transform;
        ctx.save();
        ctx.translate(t.x, t.y);
        ctx.rotate(t.rotation * Math.PI / 180);
        ctx.fillStyle = color(entity.visual.color);
        const x = -t.width / 2, y = -t.height / 2;
        switch (entity.visual.type) {
          case "BOX": ctx.beginPath(); ctx.roundRect(x, y, t.width, t.height, Math.min(5, t.width / 4, t.height / 4)); ctx.fill(); break;
          case "CIRCLE": ctx.beginPath(); ctx.ellipse(0, 0, t.width / 2, t.height / 2, 0, 0, TAU); ctx.fill(); break;
          case "TEXT":
            ctx.font = `bold ${t.height * .62}px system-ui, sans-serif`;
            ctx.textAlign = "center"; ctx.textBaseline = "middle";
            ctx.fillText(entity.visual.text, 0, 0, t.width);
            break;
          case "IMAGE": {
            const image = images.get(entity.visual.assetId);
            if (image && image.complete && image.naturalWidth) ctx.drawImage(image, x, y, t.width, t.height);
            else { ctx.fillStyle = "#394459"; ctx.fillRect(x, y, t.width, t.height); }
            break;
          }
        }
        ctx.restore();
      }
    }
    let last = performance.now();
    document.addEventListener("visibilitychange", () => { last = performance.now(); });
    function frame(now) {
      world.advance((now - last) / 1000);
      last = now;
      draw();
      requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }

  const api = { createWorld, hitTest, color };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  if (typeof document !== "undefined" && document.getElementById("game")) boot();
})(typeof window !== "undefined" ? window : globalThis);
