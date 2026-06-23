// scenes.jsx — OWL launch teaser: treatment, eye motif, and the four beats.
// Loads after animations.jsx (provides Sprite, useSprite, useTime, useTimeline,
// Easing, interpolate, clamp on window). Exports scene + treatment comps to window.

const { Sprite, useSprite, useTime, useTimeline, Easing, interpolate, clamp } = window;

// ── palette ──────────────────────────────────────────────────────────────
const AMBER      = 'oklch(0.80 0.155 74)';
const AMBER_HOT  = 'oklch(0.90 0.13 84)';
const AMBER_DEEP = 'oklch(0.52 0.145 60)';
const AMBER_EMBER= 'oklch(0.34 0.11 54)';
const COLD       = 'oklch(0.72 0.03 250)';
const BONE       = 'oklch(0.90 0.012 80)';
const MONO = "'Space Mono', ui-monospace, monospace";
const DISP = "'Space Grotesk', system-ui, sans-serif";

// fade helper: 0 before in, 1 during hold, 0 after out (local-time based)
function fade(localTime, dur, inDur = 1, outDur = 0.9, inEase = Easing.easeOutCubic, outEase = Easing.easeInCubic) {
  if (localTime < inDur) return inEase(clamp(localTime / inDur, 0, 1));
  const outStart = dur - outDur;
  if (localTime > outStart) return 1 - outEase(clamp((localTime - outStart) / outDur, 0, 1));
  return 1;
}
const lerp = (a, b, t) => a + (b - a) * t;

// ══ TREATMENT OVERLAYS ═════════════════════════════════════════════════════

// Animated film grain (CSS-driven shimmer, independent of playhead)
function Grain() {
  const noise = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='180' height='180'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E";
  return (
    <div className="owl-grain" style={{
      position: 'absolute', inset: -60,
      backgroundImage: `url("${noise}")`,
      backgroundSize: '180px 180px',
      opacity: 0.06,
      mixBlendMode: 'overlay',
      pointerEvents: 'none',
      zIndex: 80,
    }} />
  );
}

// Color grade: warm centre glow, cold crushed edges, lifted-black tint
function ColorGrade() {
  return (
    <React.Fragment>
      <div style={{
        position: 'absolute', inset: 0, pointerEvents: 'none', zIndex: 70,
        background: 'radial-gradient(120% 130% at 50% 46%, oklch(0.62 0.10 66 / 0.10) 0%, transparent 42%)',
        mixBlendMode: 'soft-light',
      }} />
      <div style={{
        position: 'absolute', inset: 0, pointerEvents: 'none', zIndex: 71,
        background: 'radial-gradient(130% 120% at 50% 50%, transparent 52%, oklch(0.30 0.04 255 / 0.35) 100%)',
        mixBlendMode: 'multiply',
      }} />
    </React.Fragment>
  );
}

// Heavy cinematic vignette
function Vignette() {
  return (
    <div style={{
      position: 'absolute', inset: 0, pointerEvents: 'none', zIndex: 72,
      background: 'radial-gradient(115% 130% at 50% 48%, transparent 38%, rgba(0,0,0,0.55) 78%, rgba(0,0,0,0.92) 100%)',
    }} />
  );
}

// Black dip that lives above scenes between cuts — each scene fades itself,
// this just guarantees pure black at the very start and very end.
function BookendBlack() {
  const t = useTime();
  const { duration } = useTimeline();
  let o = 0;
  if (t < 0.8) o = 1 - Easing.easeOutCubic(clamp(t / 0.8, 0, 1));
  else if (t > duration - 0.7) o = Easing.easeInCubic(clamp((t - (duration - 0.7)) / 0.7, 0, 1));
  return <div style={{ position: 'absolute', inset: 0, background: '#000', opacity: o, pointerEvents: 'none', zIndex: 75 }} />;
}

// ══ EYE MOTIF (pure geometry) ══════════════════════════════════════════════

// The amber iris — reused at every scale (macro, lens reflection, mark).
function AmberIris({ size = 600, pupil = 0.34, glow = 0.6, reflection = 0 }) {
  const p = size * pupil;
  const ringMask = `radial-gradient(circle, transparent ${pupil * 100 + 3}%, #000 ${pupil * 100 + 9}%, #000 80%, transparent 90%)`;
  return (
    <div style={{ width: size, height: size, position: 'relative', borderRadius: '50%' }}>
      {/* glow halo */}
      <div style={{ position: 'absolute', inset: -size * 0.14, borderRadius: '50%',
        background: `radial-gradient(circle, oklch(0.66 0.16 70 / ${0.45 * glow}) 0%, transparent 62%)`,
        filter: `blur(${size * 0.03}px)` }} />
      {/* iris base */}
      <div style={{ position: 'absolute', inset: 0, borderRadius: '50%',
        background: `radial-gradient(circle at 50% 47%, ${AMBER_HOT} 4%, ${AMBER} 20%, ${AMBER_DEEP} 48%, ${AMBER_EMBER} 66%, oklch(0.14 0.05 48) 84%, #000 100%)`,
        boxShadow: `inset 0 0 ${size * 0.14}px rgba(0,0,0,0.7)` }} />
      {/* coarse striations */}
      <div style={{ position: 'absolute', inset: 0, borderRadius: '50%', mixBlendMode: 'overlay', opacity: 0.6,
        background: `repeating-conic-gradient(from 0deg at 50% 50%, rgba(0,0,0,0.5) 0deg, rgba(255,212,150,0.12) 1.0deg, rgba(0,0,0,0) 2.0deg)`,
        WebkitMaskImage: ringMask, maskImage: ringMask }} />
      {/* fine striations, offset */}
      <div style={{ position: 'absolute', inset: 0, borderRadius: '50%', mixBlendMode: 'overlay', opacity: 0.4,
        background: `repeating-conic-gradient(from 0.9deg at 50% 50%, rgba(0,0,0,0.4) 0deg, rgba(255,190,120,0.10) 0.55deg, rgba(0,0,0,0) 1.1deg)`,
        WebkitMaskImage: ringMask, maskImage: ringMask }} />
      {/* limbal ring */}
      <div style={{ position: 'absolute', inset: 0, borderRadius: '50%', pointerEvents: 'none',
        boxShadow: `inset 0 0 ${size * 0.05}px ${size * 0.012}px rgba(0,0,0,0.88)` }} />
      {/* pupil */}
      <div style={{ position: 'absolute', left: '50%', top: '47%', width: p, height: p, marginLeft: -p / 2, marginTop: -p / 2, borderRadius: '50%', overflow: 'hidden',
        background: `radial-gradient(circle at 50% 42%, #060300 58%, #000 100%)`,
        boxShadow: `0 0 ${size * 0.02}px rgba(0,0,0,0.9), inset 0 0 ${p * 0.18}px oklch(0.55 0.14 60 / 0.45)` }}>
        {/* reflection of the player at their rig, curved across the pupil */}
        {reflection > 0 && (
          <div style={{ position: 'absolute', inset: 0, opacity: 0.62 * reflection, transform: 'scaleY(1.12)', filter: `blur(${p * 0.006}px)` }}>
            <div style={{ position: 'absolute', inset: 0, background: 'radial-gradient(68% 55% at 50% 40%, oklch(0.44 0.06 238 / 0.7), transparent 76%)' }} />
            <div style={{ position: 'absolute', left: '16%', right: '16%', top: '30%', height: '15%', borderRadius: 4, background: 'oklch(0.62 0.08 235 / 0.6)', filter: `blur(${p * 0.012}px)` }} />
            <div style={{ position: 'absolute', left: '32%', top: '34%', width: '6%', height: '9%', borderRadius: '50%', background: 'oklch(0.66 0.07 235 / 0.5)' }} />
            <div style={{ position: 'absolute', left: '50%', bottom: '2%', width: '50%', height: '46%', transform: 'translateX(-50%)', borderRadius: '46% 46% 28% 28%', background: 'oklch(0.09 0.012 250)' }} />
          </div>
        )}
        {/* cold catchlight */}
        <div style={{ position: 'absolute', left: '28%', top: '18%', width: p * 0.24, height: p * 0.17, borderRadius: '50%',
          background: 'oklch(0.93 0.02 250 / 0.85)', filter: `blur(${p * 0.03}px)` }} />
      </div>
    </div>
  );
}
window.AmberIris = AmberIris;

// Macro eye with opening almond aperture + lid shadows.
function MacroEye({ aperture = 1, size = 600, pupil = 0.34, glow = 0.6, reflection = 0 }) {
  const ry = 2.5 + aperture * 46.5;       // ellipse vertical radius %
  const rx = 40 + aperture * 18;          // widen as it opens
  const clip = `ellipse(${rx}% ${ry}% at 50% 50%)`;
  return (
    <div style={{ position: 'absolute', left: '50%', top: '50%', width: size * 1.2, height: size * 1.05, transform: 'translate(-50%,-50%)' }}>
      <div style={{ position: 'absolute', inset: 0, clipPath: clip, WebkitClipPath: clip, overflow: 'hidden',
        display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <AmberIris size={size} pupil={pupil} glow={glow} reflection={reflection} />
        {/* lid curvature shadow */}
        <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none',
          background: 'linear-gradient(to bottom, rgba(0,0,0,0.9) 0%, transparent 22%, transparent 78%, rgba(0,0,0,0.95) 100%)' }} />
      </div>
      {/* feathered fade-to-black around the eye */}
      <div style={{ position: 'absolute', inset: -size * 0.5, pointerEvents: 'none',
        background: `radial-gradient(ellipse 46% 30% at 50% 50%, transparent 40%, rgba(0,0,0,0.6) 70%, #000 100%)` }} />
    </div>
  );
}

// ══ CLIP 1 — THE VANTAGE ═══════════════════════════════════════════════════
// Aerial predator POV over a dark field. Uses the user's attached clip as the
// base plate; stylised field + drifting figure render underneath as a fallback
// so the beat reads even if the plate is dark. Predator reticle + HUD on top.
function VantageScene() {
  return (
    <Sprite start={0} end={8.4}>
      {({ localTime, duration }) => {
        const op = fade(localTime, duration, 1.4, 1.0);
        const push = lerp(1.0, 1.14, Easing.easeInOutSine(clamp(localTime / duration, 0, 1))); // slow downward glide
        // lone figure drifts slowly across open ground, staying distant
        const figX = lerp(38, 58, localTime / duration);
        const figY = lerp(62, 54, Easing.easeInOutSine(clamp(localTime / duration, 0, 1)));
        const hud = clamp((localTime - 1.6) / 1.2, 0, 1) * op;
        const alt = Math.round(lerp(412, 318, localTime / duration));
        return (
          <div style={{ position: 'absolute', inset: 0, opacity: op, overflow: 'hidden', background: '#000' }}>
            {/* stylised dark field plate (fallback / underlay) */}
            <div style={{ position: 'absolute', inset: 0, transform: `scale(${push})`, transformOrigin: '50% 40%' }}>
              <div style={{ position: 'absolute', inset: 0,
                background: 'radial-gradient(140% 120% at 50% 8%, oklch(0.26 0.02 250) 0%, oklch(0.13 0.015 250) 32%, #050507 70%)' }} />
              {/* faint game-world grid / terrain seams */}
              <div style={{ position: 'absolute', inset: 0, opacity: 0.10,
                background: 'repeating-linear-gradient(64deg, transparent 0 78px, oklch(0.6 0.02 250 / 0.5) 78px 79px), repeating-linear-gradient(-64deg, transparent 0 96px, oklch(0.6 0.02 250 / 0.4) 96px 97px)',
                maskImage: 'radial-gradient(80% 70% at 50% 55%, #000 30%, transparent 80%)',
                WebkitMaskImage: 'radial-gradient(80% 70% at 50% 55%, #000 30%, transparent 80%)' }} />
              {/* the user's attached aerial plate, layered over the field */}
              <video className="owl-vantage-video" src="uploads/widescreen_teaser.mp4" muted playsInline preload="auto"
                style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover',
                  opacity: 0.92, mixBlendMode: 'screen' }} />
              {/* lone figure far below + long shadow */}
              <div style={{ position: 'absolute', left: `${figX}%`, top: `${figY}%`, transform: 'translate(-50%,-50%)' }}>
                <div style={{ width: 7, height: 16, borderRadius: '50% 50% 45% 45%', background: 'oklch(0.30 0.01 250)',
                  boxShadow: '0 10px 18px 2px rgba(0,0,0,0.8)' }} />
                <div style={{ position: 'absolute', left: '50%', top: '70%', width: 5, height: 54,
                  background: 'linear-gradient(to bottom, rgba(0,0,0,0.55), transparent)',
                  transform: 'translateX(-50%) rotate(8deg)', transformOrigin: 'top', filter: 'blur(1px)' }} />
              </div>
            </div>
            {/* drifting atmospheric haze */}
            <div className="owl-haze" style={{ position: 'absolute', inset: '-10%',
              background: 'radial-gradient(40% 30% at 30% 70%, oklch(0.5 0.02 250 / 0.18), transparent 70%), radial-gradient(50% 36% at 72% 40%, oklch(0.45 0.02 255 / 0.14), transparent 70%)',
              filter: 'blur(8px)' }} />
            {/* predator reticle loosely tracking the figure */}
            <div style={{ position: 'absolute', left: `${figX}%`, top: `${figY}%`, transform: 'translate(-50%,-50%)', opacity: hud * 0.85 }}>
              <Reticle />
            </div>
            {/* HUD ticks */}
            <div style={{ position: 'absolute', left: 54, top: 44, opacity: hud, fontFamily: MONO, color: AMBER, fontSize: 17, letterSpacing: '0.18em', lineHeight: 1.8 }}>
              <div>OWL // OVERWATCH</div>
              <div style={{ color: COLD, fontSize: 14, opacity: 0.7 }}>ALT {alt}m · LOCK ░░▒▓</div>
            </div>
            <div style={{ position: 'absolute', right: 54, bottom: 40, opacity: hud * 0.7, fontFamily: MONO, color: COLD, fontSize: 13, letterSpacing: '0.2em' }}>
              N 51.4°  ·  W 02.6°
            </div>
          </div>
        );
      }}
    </Sprite>
  );
}

function Reticle() {
  const s = 120;
  return (
    <svg width={s} height={s} viewBox="0 0 120 120" style={{ filter: `drop-shadow(0 0 6px oklch(0.7 0.15 70 / 0.6))` }}>
      {['M12,12 L12,30 M12,12 L30,12', 'M108,12 L108,30 M108,12 L90,12', 'M12,108 L12,90 M12,108 L30,108', 'M108,108 L108,90 M108,108 L90,108'].map((d, i) => (
        <path key={i} d={d} stroke={AMBER} strokeWidth="1.6" fill="none" />
      ))}
      <circle cx="60" cy="60" r="3" fill={AMBER} />
      <circle cx="60" cy="60" r="20" stroke={AMBER} strokeWidth="0.8" fill="none" opacity="0.5" />
    </svg>
  );
}

// ══ CLIP 2 — THE EYE ═══════════════════════════════════════════════════════
function EyeScene() {
  return (
    <Sprite start={7.6} end={15.2}>
      {({ localTime, duration }) => {
        const op = fade(localTime, duration, 0.9, 1.0);
        const aperture = Easing.easeOutCubic(clamp(localTime / 1.7, 0, 1));
        // dilate wide (locking on), then focus as we push toward the pupil
        const pupil = interpolate([0, 1.7, 3.6, 5.0, 7.2], [0.20, 0.26, 0.42, 0.40, 0.46],
          [Easing.easeOutCubic, Easing.easeInOutSine, Easing.easeInOutSine, Easing.easeInOutSine])(localTime);
        // slow push into the pupil — the reflection is the payoff
        const push = interpolate([0, 2.0, 5.2, 7.6], [1.0, 1.08, 1.5, 1.64],
          [Easing.easeInOutSine, Easing.easeInOutCubic, Easing.easeOutSine])(localTime);
        const tremor = Math.sin(localTime * 7) * 0.5 + Math.sin(localTime * 2.3) * 0.9;
        const glow = clamp((localTime - 1.2) / 1.4, 0, 1);
        const reflection = clamp((localTime - 3.0) / 2.4, 0, 1);
        return (
          <div style={{ position: 'absolute', inset: 0, background: '#000', opacity: op, overflow: 'hidden' }}>
            <div style={{ position: 'absolute', inset: 0, transformOrigin: '50% 47%',
              transform: `scale(${push}) translate(${tremor}px, ${tremor * 0.4}px)` }}>
              <MacroEye aperture={aperture} size={560} pupil={pupil} glow={glow} reflection={reflection} />
            </div>
          </div>
        );
      }}
    </Sprite>
  );
}

// ══ CLIP 3 — THE BRAND CARD ════════════════════════════════════════════════
// "Your AI. Your eyes. Your rig."  →  "Nothing leaves your network."
function BrandCardScene() {
  const lines = [['Your ', 'AI.'], ['Your ', 'eyes.'], ['Your ', 'rig.']];
  return (
    <Sprite start={15.0} end={20.8}>
      {({ localTime, duration }) => {
        const op = fade(localTime, duration, 0.9, 0.9);
        const payoff = clamp((localTime - 2.7) / 0.9, 0, 1) * op;
        const ruleW = clamp((localTime - 2.5) / 0.8, 0, 1);
        return (
          <div style={{ position: 'absolute', inset: 0, background: '#000', opacity: op,
            display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
            {/* the eye, still watching */}
            <div style={{ marginBottom: 34, opacity: 0.55 }}><AmberIris size={46} pupil={0.4} glow={0.9} /></div>
            {lines.map((ln, i) => {
              const a = clamp((localTime - (0.5 + i * 0.55)) / 0.6, 0, 1);
              const e = Easing.easeOutCubic(a);
              return (
                <div key={i} style={{ opacity: e, transform: `translateY(${(1 - e) * 14}px)`,
                  fontFamily: DISP, fontWeight: 300, fontSize: 64, letterSpacing: '-0.01em', color: BONE, lineHeight: 1.16 }}>
                  {ln[0]}<span style={{ color: AMBER }}>{ln[1]}</span>
                </div>
              );
            })}
            <div style={{ width: 320 * ruleW, height: 1, background: AMBER, opacity: 0.55 * ruleW, margin: '28px 0 18px' }} />
            <div style={{ opacity: payoff, fontFamily: MONO, fontSize: 19, letterSpacing: '0.34em', color: AMBER, textTransform: 'uppercase' }}>
              Nothing leaves your network
            </div>
          </div>
        );
      }}
    </Sprite>
  );
}

// ══ CLIP 4 — THE PLAYER ════════════════════════════════════════════════════
// The reveal: a player at their rig, watched through the AR lens. "Behind you."
function PlayerScene() {
  return (
    <Sprite start={20.6} end={27.4}>
      {({ localTime, duration }) => {
        const op = fade(localTime, duration, 1.1, 1.0);
        const push = lerp(1.04, 1.0, Easing.easeOutCubic(clamp(localTime / 3, 0, 1)));
        const drift = Math.sin(localTime * 0.8) * 6;
        const lensIn = Easing.easeOutCubic(clamp((localTime - 0.5) / 1.6, 0, 1));
        const reflGlow = clamp((localTime - 1.4) / 1.6, 0, 1);
        const sweep = clamp((localTime - 2.4) / 1.4, 0, 1);
        const textOp = (clamp((localTime - 2.2) / 0.9, 0, 1) - clamp((localTime - 5.0) / 0.7, 0, 1)) * op;
        return (
          <div style={{ position: 'absolute', inset: 0, background: '#020203', opacity: op, overflow: 'hidden' }}>
            {/* dim room behind, heavily blurred */}
            <div style={{ position: 'absolute', inset: 0, transform: `scale(${push}) translateX(${drift}px)`, filter: 'blur(14px)' }}>
              <div style={{ position: 'absolute', inset: 0, background: 'radial-gradient(80% 90% at 64% 38%, oklch(0.20 0.02 250) 0%, oklch(0.08 0.01 250) 45%, #020204 80%)' }} />
              {/* monitor glow */}
              <div className="owl-flicker" style={{ position: 'absolute', left: '60%', top: '30%', width: 460, height: 300,
                background: 'radial-gradient(circle, oklch(0.55 0.06 240 / 0.5), transparent 70%)' }} />
              {/* hunched gamer silhouette */}
              <div style={{ position: 'absolute', left: '58%', top: '46%', width: 230, height: 280,
                background: 'radial-gradient(60% 70% at 50% 30%, oklch(0.16 0.01 250), transparent 72%)',
                borderRadius: '46% 46% 30% 30%' }} />
            </div>
            {/* close-up smart-glasses lens */}
            <div style={{ position: 'absolute', left: '50%', top: '54%', transform: `translate(-50%,-50%) translateX(${drift * 0.3}px)`, opacity: lensIn }}>
              <GlassesLens reflGlow={reflGlow} sweep={sweep} />
            </div>
            {/* "Behind you." */}
            <div style={{ position: 'absolute', left: '50%', bottom: 92, transform: 'translateX(-50%)', opacity: textOp,
              fontFamily: DISP, fontWeight: 300, fontSize: 40, letterSpacing: '0.42em', color: BONE, textIndent: '0.42em',
              textShadow: '0 0 24px oklch(0.7 0.14 70 / 0.4)' }}>
              behind you
            </div>
          </div>
        );
      }}
    </Sprite>
  );
}

function GlassesLens({ reflGlow = 1, sweep = 0 }) {
  const w = 760, h = 360;
  return (
    <div style={{ position: 'relative', width: w, height: h }}>
      {/* lens body */}
      <div style={{ position: 'absolute', inset: 0, borderRadius: '50% / 50%',
        background: 'radial-gradient(70% 80% at 42% 32%, oklch(0.20 0.015 250 / 0.92), oklch(0.07 0.01 250 / 0.96) 70%)',
        boxShadow: 'inset 0 2px 30px rgba(0,0,0,0.7), 0 0 60px rgba(0,0,0,0.8)',
        border: '2px solid oklch(0.32 0.02 250 / 0.7)', overflow: 'hidden' }}>
        {/* top amber rim light */}
        <div style={{ position: 'absolute', inset: 0, borderRadius: '50% / 50%',
          boxShadow: `inset 0 3px 18px oklch(0.7 0.14 72 / ${0.5 * reflGlow})` }} />
        {/* faint AR UI arcs reflected */}
        <div style={{ position: 'absolute', left: '24%', top: '30%', width: 200, height: 200, borderRadius: '50%',
          border: `1px solid oklch(0.7 0.13 72 / ${0.22 * reflGlow})`, opacity: 0.8 }} />
        {/* the reflected amber eye */}
        <div style={{ position: 'absolute', left: '52%', top: '50%', transform: 'translate(-50%,-50%)', opacity: reflGlow }}>
          <AmberIris size={150} pupil={0.36} glow={0.9 * reflGlow} />
        </div>
        {/* highlight sweep */}
        <div style={{ position: 'absolute', top: 0, bottom: 0, left: `${-30 + sweep * 130}%`, width: '40%',
          background: 'linear-gradient(105deg, transparent, oklch(0.85 0.04 250 / 0.18), transparent)',
          transform: 'skewX(-12deg)' }} />
      </div>
    </div>
  );
}

// ══ CLIP 5 — THE END CARD ══════════════════════════════════════════════════
function EndCardScene() {
  return (
    <Sprite start={27.2} end={34.0}>
      {({ localTime, duration }) => {
        const op = fade(localTime, duration, 1.4, 1.6);
        const draw = Easing.easeOutCubic(clamp(localTime / 1.6, 0, 1));
        const ignite = clamp((localTime - 1.2) / 1.0, 0, 1);
        const pulse = 0.78 + 0.22 * Math.sin(localTime * 1.6);
        const nameOp = clamp((localTime - 2.2) / 1.0, 0, 1) * op;
        const ruleW = clamp((localTime - 2.9) / 0.8, 0, 1);
        const featOp = clamp((localTime - 3.1) / 1.0, 0, 1) * op;
        const tagOp = clamp((localTime - 3.9) / 1.1, 0, 1) * op;
        const push = lerp(1.06, 1.0, Easing.easeOutCubic(clamp(localTime / 3, 0, 1)));
        return (
          <div style={{ position: 'absolute', inset: 0, background: '#000', opacity: op,
            display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ transform: `scale(${push})`, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              <div style={{ transform: 'scale(0.6)', marginBottom: -6 }}><OwlMark draw={draw} ignite={ignite} pulse={pulse} /></div>
              <div style={{ opacity: nameOp, whiteSpace: 'nowrap', fontFamily: DISP, fontWeight: 400, fontSize: 46, letterSpacing: '0.30em', textIndent: '0.30em', color: BONE }}>
                LIGHTHOUSE BEACON
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 16, margin: '22px 0 16px', opacity: ruleW }}>
                <div style={{ width: 56 * ruleW, height: 1, background: AMBER, opacity: 0.55 }} />
                <div style={{ fontFamily: MONO, fontSize: 13, letterSpacing: '0.3em', color: AMBER, opacity: featOp }}>NEW</div>
                <div style={{ width: 56 * ruleW, height: 1, background: AMBER, opacity: 0.55 }} />
              </div>
              <div style={{ opacity: featOp, whiteSpace: 'nowrap', fontFamily: DISP, fontWeight: 300, fontSize: 30, letterSpacing: '0.18em', textIndent: '0.18em', color: AMBER }}>
                TRANSFORMER MODE
              </div>
              <div style={{ marginTop: 26, opacity: tagOp, whiteSpace: 'nowrap', fontFamily: DISP, fontWeight: 300, fontStyle: 'italic', fontSize: 22, letterSpacing: '0.04em', color: 'oklch(0.7 0.02 80)' }}>
                If you’re brave enough.
              </div>
            </div>
          </div>
        );
      }}
    </Sprite>
  );
}

// Minimal geometric owl: circles + diamonds only, thin amber monoline,
// head turned with a single glowing eye.
function OwlMark({ draw = 1, ignite = 1, pulse = 1 }) {
  const stroke = AMBER;
  const sw = 3;
  const dash = 900;
  const drawStyle = { strokeDasharray: dash, strokeDashoffset: dash * (1 - draw) };
  return (
    <svg width="300" height="320" viewBox="0 0 300 320" fill="none"
      style={{ filter: `drop-shadow(0 0 18px oklch(0.7 0.15 70 / ${0.35 * ignite}))` }}>
      {/* head circle */}
      <circle cx="150" cy="160" r="104" stroke={stroke} strokeWidth={sw} style={drawStyle} opacity="0.92" />
      {/* facial disc */}
      <circle cx="150" cy="168" r="74" stroke={stroke} strokeWidth={sw * 0.8} style={drawStyle} opacity="0.55" />
      {/* ear tufts — diamonds */}
      <rect x="78" y="58" width="34" height="34" transform="rotate(45 95 75)" stroke={stroke} strokeWidth={sw} style={drawStyle} opacity="0.85" />
      <rect x="188" y="58" width="34" height="34" transform="rotate(45 205 75)" stroke={stroke} strokeWidth={sw} style={drawStyle} opacity="0.85" />
      {/* left eye — dark, closed/turned away */}
      <circle cx="116" cy="150" r="22" stroke={stroke} strokeWidth={sw * 0.9} opacity={0.45} style={drawStyle} />
      <line x1="100" y1="150" x2="132" y2="150" stroke={stroke} strokeWidth={sw} opacity="0.5" style={drawStyle} />
      {/* right eye — the single glowing amber eye */}
      <circle cx="186" cy="150" r="23" fill={AMBER} opacity={ignite * pulse}
        style={{ filter: `drop-shadow(0 0 ${10 * pulse}px ${AMBER_HOT})` }} />
      <circle cx="186" cy="150" r="23" stroke={stroke} strokeWidth={sw} style={drawStyle} />
      <circle cx="186" cy="150" r="8" fill="#000" opacity={ignite} />
      {/* beak — diamond */}
      <rect x="138" y="184" width="24" height="24" transform="rotate(45 150 196)" stroke={stroke} strokeWidth={sw} style={drawStyle} opacity="0.8" />
    </svg>
  );
}

// Fog cloud close — banks roll in over the end card, then a quiet line
// surfaces out of the fog (the warm turn) before everything dissolves to black.
function FogClose() {
  const t = useTime();
  const start = 31.4;
  const env = clamp((t - start) / 2.4, 0, 1);
  if (env <= 0) return null;
  const o = Easing.easeInOutSine(env);
  // the lines, emerging blurred-to-sharp from the fog
  const l1 = Easing.easeOutCubic(clamp((t - 33.0) / 1.3, 0, 1));
  const l2 = Easing.easeOutCubic(clamp((t - 33.9) / 1.2, 0, 1));
  const lOut = clamp((t - 35.9) / 0.8, 0, 1);
  // third beat — the exhale. rises at 36.2, holds, dissolves at 38.8
  const l3 = Easing.easeOutCubic(clamp((t - 36.2) / 1.4, 0, 1));
  const l3Out = clamp((t - 38.8) / 1.1, 0, 1);
  // gentle involuntary wobble — relief breathing out
  const wobble = Math.sin(t * 11.4) * (1 - l3Out) * 1.6;
  const wobbleX = Math.sin(t * 7.3) * (1 - l3Out) * 0.8;
  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 74, pointerEvents: 'none' }}>
      {/* fog banks */}
      <div style={{ position: 'absolute', inset: 0, opacity: o }}>
        <div style={{ position: 'absolute', inset: 0, background: 'radial-gradient(120% 95% at 50% 82%, oklch(0.42 0.02 250 / 0.5), transparent 70%)' }} />
        <div className="owl-fog owl-fog-a" style={{ position: 'absolute', inset: '-22%', background: 'radial-gradient(45% 40% at 30% 70%, oklch(0.55 0.02 250 / 0.5), transparent 65%)', filter: 'blur(34px)' }} />
        <div className="owl-fog owl-fog-b" style={{ position: 'absolute', inset: '-22%', background: 'radial-gradient(50% 42% at 70% 58%, oklch(0.5 0.025 245 / 0.45), transparent 66%)', filter: 'blur(40px)' }} />
        <div className="owl-fog owl-fog-c" style={{ position: 'absolute', inset: '-22%', background: 'radial-gradient(40% 36% at 50% 52%, oklch(0.58 0.05 72 / 0.16), transparent 60%)', filter: 'blur(44px)' }} />
      </div>
      {/* the turn */}
      <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center', gap: 22 }}>
        <div style={{ opacity: Math.max(0, l1 - lOut), filter: `blur(${(1 - l1) * 7}px)`,
          fontFamily: DISP, fontStyle: 'italic', fontWeight: 300, fontSize: 36, lineHeight: 1.45,
          letterSpacing: '0.03em', color: BONE, textAlign: 'center', textShadow: '0 0 34px rgba(0,0,0,0.85)' }}>
          Behind every great man,<br />a <span style={{ color: AMBER }}>warrior&rsquo;s woman.</span>
        </div>
        <div style={{ opacity: Math.max(0, l2 - lOut), filter: `blur(${(1 - l2) * 6}px)`,
          fontFamily: DISP, fontWeight: 300, fontSize: 21, letterSpacing: '0.14em',
          color: 'oklch(0.74 0.02 80)', textAlign: 'center', textShadow: '0 0 28px rgba(0,0,0,0.85)' }}>
          Someone to look forward to getting in the game with.
        </div>
        {/* the exhale */}
        <div style={{
          opacity: Math.max(0, l3 - l3Out),
          filter: `blur(${(1 - l3) * 5}px)`,
          transform: `translate(${wobbleX}px, ${wobble}px)`,
          fontFamily: MONO,
          fontWeight: 400,
          fontSize: 28,
          letterSpacing: '0.55em',
          textIndent: '0.55em',
          color: AMBER,
          textAlign: 'center',
          textShadow: `0 0 22px oklch(0.7 0.14 70 / ${0.5 * l3})`,
        }}>
          aaaahhhhh
        </div>
      </div>
    </div>
  );
}

Object.assign(window, {
  Grain, ColorGrade, Vignette, BookendBlack, FogClose,
  AmberIris, MacroEye, Reticle, GlassesLens, OwlMark,
  VantageScene, EyeScene, BrandCardScene, PlayerScene, EndCardScene,
});
