// audio.jsx — eerie ambient bed for the OWL teaser.
// Sub-bass drone + filtered wind, a low "Behind you" whisper at the reveal,
// and sub thuds on each cut. Gesture-gated (browsers block autoplay audio).
// Exposes window.OwlAudio engine + <AudioController/> + <SoundToggle/>.

const OwlAudio = {
  ctx: null, enabled: false, master: null,
  drone: [], windGain: null, lfo: null,
  whisperFired: false, hitMarks: [7.6, 9.3, 15.0, 20.6, 27.2, 33.1], lastT: 0,

  enable() {
    if (this.ctx) { this.enabled = true; this._ramp(0.5, 1.2); if (this.ctx.state === 'suspended') this.ctx.resume(); return; }
    const Ctx = window.AudioContext || window.webkitAudioContext;
    const ctx = new Ctx();
    this.ctx = ctx;
    const master = ctx.createGain(); master.gain.value = 0; master.connect(ctx.destination);
    this.master = master;

    // sub-bass drone: detuned sines
    [40.5, 41.0, 61.5].forEach((f, i) => {
      const o = ctx.createOscillator(); o.type = 'sine'; o.frequency.value = f;
      const g = ctx.createGain(); g.gain.value = i === 2 ? 0.14 : 0.30;
      o.connect(g); g.connect(master); o.start();
      this.drone.push({ o, g });
    });
    // slow amplitude shimmer on the drone
    const slow = ctx.createOscillator(); slow.type = 'sine'; slow.frequency.value = 0.07;
    const slowG = ctx.createGain(); slowG.gain.value = 0.10;
    slow.connect(slowG); slowG.connect(this.drone[0].g.gain); slow.start();

    // wind: white noise -> bandpass, LFO on cutoff
    const buf = ctx.createBuffer(1, ctx.sampleRate * 4, ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    const noise = ctx.createBufferSource(); noise.buffer = buf; noise.loop = true;
    const bp = ctx.createBiquadFilter(); bp.type = 'bandpass'; bp.frequency.value = 480; bp.Q.value = 0.7;
    const wg = ctx.createGain(); wg.gain.value = 0.08;
    noise.connect(bp); bp.connect(wg); wg.connect(master);
    const lfo = ctx.createOscillator(); lfo.type = 'sine'; lfo.frequency.value = 0.05;
    const lfoG = ctx.createGain(); lfoG.gain.value = 260;
    lfo.connect(lfoG); lfoG.connect(bp.frequency);
    noise.start(); lfo.start();
    this.windGain = wg;

    this.enabled = true;
    this._ramp(0.5, 1.4);
  },

  _ramp(to, time) {
    if (!this.master) return;
    const now = this.ctx.currentTime;
    this.master.gain.cancelScheduledValues(now);
    this.master.gain.setValueAtTime(this.master.gain.value, now);
    this.master.gain.linearRampToValueAtTime(to, now + time);
  },

  hit(strength = 1) {
    if (!this.ctx || !this.enabled) return;
    const ctx = this.ctx, now = ctx.currentTime;
    const o = ctx.createOscillator(); o.type = 'sine';
    o.frequency.setValueAtTime(70, now); o.frequency.exponentialRampToValueAtTime(28, now + 0.7);
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, now);
    g.gain.exponentialRampToValueAtTime(0.5 * strength, now + 0.02);
    g.gain.exponentialRampToValueAtTime(0.0001, now + 1.1);
    o.connect(g); g.connect(this.master); o.start(now); o.stop(now + 1.2);
  },

  whisper() {
    if (!this.enabled || !window.speechSynthesis) return;
    try {
      const u = new SpeechSynthesisUtterance('behind you');
      u.rate = 0.7; u.pitch = 0.2; u.volume = 0.85;
      window.speechSynthesis.speak(u);
    } catch (e) {}
  },

  // called every frame with the playhead
  tick(t, playing) {
    if (!this.enabled) { this.lastT = t; return; }
    this._ramp(playing ? 0.5 : 0.18, 0.4);
    // reset one-shots when the loop restarts
    if (t < this.lastT - 0.5) { this.whisperFired = false; this._hitDone = {}; }
    // cut thuds
    this._hitDone = this._hitDone || {};
    this.hitMarks.forEach((m) => {
      if (this.lastT < m && t >= m && !this._hitDone[m]) { this.hit(m === 9.3 ? 1 : (m === 33.1 ? 0.4 : 0.82)); this._hitDone[m] = true; }
    });
    // whisper at the player reveal
    if (!this.whisperFired && t >= 22.6) { this.whisper(); this.whisperFired = true; }
    this.lastT = t;
  },
};
window.OwlAudio = OwlAudio;

function AudioController() {
  const t = window.useTime();
  const { playing } = window.useTimeline();
  React.useEffect(() => { OwlAudio.tick(t, playing); }, [t, playing]);
  return null;
}

function SoundToggle() {
  const [on, setOn] = React.useState(false);
  const toggle = () => {
    if (!on) { OwlAudio.enable(); setOn(true); }
    else { OwlAudio.enabled = false; OwlAudio._ramp(0, 0.4); setOn(false); }
  };
  return (
    <button onClick={toggle} title="Sound" style={{
      position: 'absolute', right: 22, top: 20, zIndex: 90,
      display: 'flex', alignItems: 'center', gap: 9,
      padding: '9px 15px', cursor: 'pointer',
      background: 'rgba(10,8,4,0.55)', backdropFilter: 'blur(6px)',
      border: `1px solid oklch(0.8 0.155 74 / ${on ? 0.7 : 0.32})`,
      color: on ? 'oklch(0.85 0.13 78)' : 'oklch(0.7 0.04 80)',
      fontFamily: "'Space Mono', monospace", fontSize: 12, letterSpacing: '0.22em', textTransform: 'uppercase',
      borderRadius: 2, transition: 'all .25s',
    }}>
      <span style={{ display: 'inline-flex', gap: 3, alignItems: 'flex-end', height: 13 }}>
        {[5, 10, 7].map((h, i) => (
          <span key={i} className={on ? 'owl-eq' : ''} style={{ width: 2.5, height: h,
            background: 'currentColor', animationDelay: `${i * 0.15}s` }} />
        ))}
      </span>
      {on ? 'Sound on' : 'Sound off'}
    </button>
  );
}

Object.assign(window, { OwlAudio, AudioController, SoundToggle });
