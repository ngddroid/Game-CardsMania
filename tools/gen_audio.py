#!/usr/bin/env python3
"""
Cards Mania — procedural audio generator.

Pure Python standard library only (wave, struct, math, random, array).
Writes 16-bit mono WAV files into app/src/main/res/raw/.

    python3 -I tools/gen_audio.py

SFX are rendered at 44.1 kHz, music loops at 22.05 kHz. Music loops are
rendered "circularly" (the release/echo tail past the loop point is folded
back onto the start) so the loop boundary is seamless and click-free.
"""
import array
import math
import os
import random
import sys
import time
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "raw")
TAU = 2.0 * math.pi
SR = 44100
MSR = 22050

rng = random.Random(20261105)


# ---------------------------------------------------------------- basics

def secs(t, sr=SR):
    return int(round(t * sr))


def zeros(n):
    return [0.0] * n


def midi(m):
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


def peak_of(buf):
    return max(max(buf), -min(buf)) if buf else 0.0


def norm(buf, peak=1.0):
    m = peak_of(buf)
    if m <= 0:
        return buf[:]
    g = peak / m
    return [x * g for x in buf]


def scale(buf, g):
    return [x * g for x in buf]


def mul(a, b):
    return [x * y for x, y in zip(a, b)]


def mix(dst, src, off=0, gain=1.0):
    """Add src into dst starting at sample offset off (clipped to dst)."""
    n = min(len(src), len(dst) - off)
    if n <= 0:
        return dst
    if gain == 1.0:
        for i in range(n):
            dst[off + i] += src[i]
    else:
        for i in range(n):
            dst[off + i] += src[i] * gain
    return dst


def mix_at(dst, src, t, gain=1.0, sr=SR):
    return mix(dst, src, secs(t, sr), gain)


def noise(n):
    r = rng.random
    return [r() * 2.0 - 1.0 for _ in range(n)]


# ---------------------------------------------------------------- envelopes

def env_exp(n, sr, tau, attack=0.001):
    """Linear attack then exponential decay with time-constant tau (s)."""
    out = [0.0] * n
    k = math.exp(-1.0 / (tau * sr))
    na = max(1, int(attack * sr))
    a = 1.0
    for i in range(n):
        out[i] = a * (i / na if i < na else 1.0)
        if i >= na:
            a *= k
    return out


def env_bump(n, peak_frac=0.35, power=1.6):
    """Smooth rise to peak_frac of the length then smooth fall to zero."""
    out = [0.0] * n
    pk = max(1, int(n * peak_frac))
    for i in range(n):
        if i < pk:
            v = math.sin(0.5 * math.pi * i / pk)
        else:
            v = math.cos(0.5 * math.pi * (i - pk) / max(1, n - pk))
        out[i] = v ** power
    return out


def adsr(n_hold, sr, a, d, s, r):
    """Gate held for n_hold samples, then release r seconds. Returns envelope list."""
    na = max(1, int(a * sr))
    nd = max(1, int(d * sr))
    nr = max(1, int(r * sr))
    total = n_hold + nr
    out = [0.0] * total
    level = 0.0
    for i in range(n_hold):
        if i < na:
            level = i / na
        elif i < na + nd:
            level = 1.0 - (1.0 - s) * ((i - na) / nd)
        else:
            level = s
        out[i] = level
    start = level
    for j in range(nr):
        # exponential-ish release that ends at exactly zero
        p = j / nr
        out[n_hold + j] = start * (1.0 - p) * (1.0 - p)
    return out


# ---------------------------------------------------------------- filters

def lp1(buf, sr, fc):
    a = 1.0 - math.exp(-TAU * fc / sr)
    y = 0.0
    out = [0.0] * len(buf)
    for i, x in enumerate(buf):
        y += a * (x - y)
        out[i] = y
    return out


def hp1(buf, sr, fc):
    a = 1.0 - math.exp(-TAU * fc / sr)
    y = 0.0
    out = [0.0] * len(buf)
    for i, x in enumerate(buf):
        y += a * (x - y)
        out[i] = x - y
    return out


def lp_curve(buf, sr, fn, poles=2):
    """Time-varying one-pole lowpass (cascaded). fn(p in 0..1) -> cutoff Hz."""
    n = len(buf)
    out = buf
    for _ in range(poles):
        res = [0.0] * n
        y = 0.0
        a = 0.0
        for i in range(n):
            if (i & 15) == 0:
                fc = fn(i / n)
                a = 1.0 - math.exp(-TAU * min(fc, sr * 0.45) / sr)
            y += a * (out[i] - y)
            res[i] = y
        out = res
    return out


def svf(buf, sr, fn, damp=1.0, mode="bp"):
    """Chamberlin state-variable filter. fn(p) -> cutoff Hz. damp = 1/Q."""
    n = len(buf)
    out = [0.0] * n
    low = band = 0.0
    f = 0.0
    lim = sr / 6.5
    sinf = math.sin
    pi = math.pi
    for i in range(n):
        if (i & 15) == 0:
            fc = fn(i / n)
            if fc > lim:
                fc = lim
            f = 2.0 * sinf(pi * fc / sr)
        x = buf[i]
        low += f * band
        high = x - low - damp * band
        band += f * high
        if mode == "bp":
            out[i] = band
        elif mode == "lp":
            out[i] = low
        else:
            out[i] = high
    return out


def const(v):
    return lambda p: v


def expo(a, b):
    r = b / a
    return lambda p: a * (r ** p)


def delay_fx(buf, sr, t, fb, wet):
    d = max(1, int(t * sr))
    n = len(buf)
    line = [0.0] * n
    out = buf[:]
    for i in range(d, n):
        v = buf[i - d] + line[i - d] * fb
        line[i] = v
        out[i] += v * wet
    return out


# ---------------------------------------------------------------- oscillators

TBL_N = 2048


def _make_table(harm):
    t = [0.0] * TBL_N
    for h, a in harm:
        w = TAU * h / TBL_N
        for i in range(TBL_N):
            t[i] += a * math.sin(w * i)
    m = max(abs(x) for x in t)
    return [x / m for x in t]


_TABLES = {}


def table(kind, freq, sr):
    """Band-limited single-cycle table for this pitch (no aliasing)."""
    maxh = max(1, int((sr * 0.45) / max(freq, 1.0)))
    if kind == "sine":
        maxh = 1
    cap = 1
    for c in (1, 2, 3, 4, 6, 8, 12, 16, 24):
        if c <= maxh:
            cap = c
    key = (kind, cap)
    if key not in _TABLES:
        if kind == "saw":
            harm = [(h, 1.0 / h) for h in range(1, cap + 1)]
        elif kind == "square":
            harm = [(h, 1.0 / h) for h in range(1, cap + 1, 2)]
        elif kind == "tri":
            harm = [(h, ((-1) ** ((h - 1) // 2)) / (h * h)) for h in range(1, cap + 1, 2)]
        elif kind == "soft":
            base = [(1, 1.0), (2, 0.42), (3, 0.22), (4, 0.11), (5, 0.06), (6, 0.03)]
            harm = [x for x in base if x[0] <= cap]
        elif kind == "organ":
            base = [(1, 1.0), (2, 0.5), (3, 0.3), (4, 0.18), (6, 0.08), (8, 0.05)]
            harm = [x for x in base if x[0] <= cap]
        else:
            harm = [(1, 1.0)]
        _TABLES[key] = _make_table(harm)
    return _TABLES[key]


def osc(kind, freq, n, sr, vib=0.0, vib_rate=5.5, vib_delay=0.12, f_end=None, phase=0.0):
    """Wavetable oscillator. Optional vibrato (fractional depth) and linear-in-log glide."""
    tbl = table(kind, max(freq, f_end or freq), sr)
    L = TBL_N
    out = [0.0] * n
    ph = phase * L
    if vib == 0.0 and f_end is None:
        inc = freq * L / sr
        for i in range(n):
            out[i] = tbl[int(ph)]
            ph += inc
            if ph >= L:
                ph -= L
        return out
    nd = int(vib_delay * sr)
    vw = TAU * vib_rate / sr
    ratio = (f_end / freq) if f_end else 1.0
    sinf = math.sin
    for i in range(n):
        f = freq * (ratio ** (i / n)) if f_end else freq
        if vib and i > nd:
            depth = vib * min(1.0, (i - nd) / (0.2 * sr))
            f *= 1.0 + depth * sinf(vw * i)
        out[i] = tbl[int(ph)]
        ph += f * L / sr
        while ph >= L:
            ph -= L
    return out


def sweep_sine(dur, sr, f0, f1, curve_tau=None):
    """Sine with exponential pitch glide f0 -> f1 (over curve_tau seconds if given)."""
    n = secs(dur, sr)
    out = [0.0] * n
    ph = 0.0
    sinf = math.sin
    if curve_tau:
        k = math.exp(-1.0 / (curve_tau * sr))
        f = f0
        for i in range(n):
            out[i] = sinf(ph)
            ph += TAU * f / sr
            f = f1 + (f - f1) * k
    else:
        r = f1 / f0
        for i in range(n):
            f = f0 * (r ** (i / n))
            out[i] = sinf(ph)
            ph += TAU * f / sr
    return out


def bell(freq, dur, sr, ratio=3.5, index=3.0, decay=0.6, idecay=None, amp=1.0, attack=0.002):
    """FM bell (carrier sin + modulator) with decaying index."""
    n = secs(dur, sr)
    out = [0.0] * n
    kd = math.exp(-1.0 / (decay * sr))
    ki = math.exp(-1.0 / ((idecay or decay * 0.35) * sr))
    a = amp
    I = index
    pc = pm = 0.0
    dc = TAU * freq / sr
    dm = TAU * freq * ratio / sr
    na = max(1, int(attack * sr))
    sinf = math.sin
    for i in range(n):
        v = a * sinf(pc + I * sinf(pm))
        if i < na:
            v *= i / na
        out[i] = v
        pc += dc
        pm += dm
        a *= kd
        I *= ki
    return out


def partials(freq, dur, sr, parts, attack=0.001):
    """Sum of decaying sine partials: parts = [(ratio, amp, tau)]."""
    n = secs(dur, sr)
    out = [0.0] * n
    na = max(1, int(attack * sr))
    sinf = math.sin
    for ratio, amp, tau in parts:
        w = TAU * freq * ratio / sr
        if freq * ratio >= sr * 0.45:
            continue
        k = math.exp(-1.0 / (tau * sr))
        a = amp
        for i in range(n):
            v = a * sinf(w * i)
            if i < na:
                v *= i / na
            out[i] += v
            a *= k
    return out


def note(kind, m, hold, sr, a=0.01, d=0.1, s=0.7, r=0.12, vib=0.0, detune_cents=0.0, f_end=None):
    f = midi(m) * (2.0 ** (detune_cents / 1200.0))
    env = adsr(secs(hold, sr), sr, a, d, s, r)
    o = osc(kind, f, len(env), sr, vib=vib, f_end=f_end, phase=rng.random())
    return mul(o, env)


# ---------------------------------------------------------------- output

def dc_block(buf, sr, fc=25.0):
    """Gentle DC-blocking highpass (one pole)."""
    return hp1(buf, sr, fc)


def write_wav(name, buf, sr, peak=0.891, fade_in=0.0015, fade_out=0.012, normalize=True,
              rms_cap_db=-15.0):
    """Fade, (optionally) DC-block + normalize, then write 16-bit mono PCM.

    For SFX the gain is the smaller of: peak normalization, and an RMS cap so
    dense sounds (buzzes, chords) don't come out much louder than transients.
    """
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".wav")
    n = len(buf)
    fi = int(fade_in * sr)
    fo = int(fade_out * sr)
    if fi or fo:
        buf = buf[:]
        for i in range(min(fi, n)):
            buf[i] *= i / fi
        for j in range(min(fo, n)):
            buf[n - 1 - j] *= j / fo
    g = 1.0
    if normalize:
        buf = dc_block(buf, sr)
        if fo:
            for j in range(min(fo, n)):
                buf[n - 1 - j] *= j / fo
        m = peak_of(buf)
        g = peak / m if m > 0 else 1.0
        if rms_cap_db is not None:
            rms = math.sqrt(sum(x * x for x in buf) / n) if n else 0.0
            if rms > 0:
                g = min(g, (10.0 ** (rms_cap_db / 20.0)) / rms)
    out = array.array("h", bytes(2 * n))
    for i in range(n):
        v = buf[i] * g
        if v > 0.999:
            v = 0.999
        elif v < -0.999:
            v = -0.999
        out[i] = int(round(v * 32767))
    if sys.byteorder == "big":
        out.byteswap()
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(out.tobytes())
    return path


# ================================================================ SFX

def _snap_grain(fc, thump, n_t=0.13, tail=0.011, gain_thump=0.7):
    n = secs(n_t)
    nz = svf(noise(n), SR, lambda p: fc * (1.0 - 0.35 * p), damp=0.9)
    nz = mul(nz, env_exp(n, SR, tail, 0.0004))
    click = mul(hp1(noise(n), SR, 4000), env_exp(n, SR, 0.0018, 0.0002))
    th = mul(sweep_sine(n_t, SR, thump * 1.8, thump, curve_tau=0.012), env_exp(n, SR, 0.026, 0.001))
    out = zeros(n)
    mix(out, norm(nz), 0, 1.0)
    mix(out, norm(click), 0, 0.35)
    mix(out, th, 0, gain_thump)
    return out


def sfx_card_snap(v):
    fc = (2700.0, 3300.0, 2250.0)[v]
    th = (150.0, 172.0, 132.0)[v]
    return _snap_grain(fc, th)


def _swept_noise(dur, f0, f1, damp=1.2, peak_frac=0.4, power=1.4, shape=None):
    n = secs(dur)
    fn = shape or expo(f0, f1)
    nz = svf(noise(n), SR, fn, damp=damp)
    return mul(norm(nz), env_bump(n, peak_frac, power))


def sfx_card_slide():
    out = zeros(secs(0.3))
    mix(out, _swept_noise(0.26, 1100, 4300, damp=1.3, peak_frac=0.3), 0, 1.0)
    mix_at(out, _snap_grain(2600, 150, n_t=0.06, tail=0.006, gain_thump=0.3), 0.2, 0.35)
    return out


def sfx_deal():
    out = zeros(secs(0.24))
    mix(out, _swept_noise(0.15, 1700, 5200, damp=1.1, peak_frac=0.45), 0, 1.0)
    mix_at(out, _snap_grain(3000, 160, n_t=0.09), 0.12, 0.6)
    return out


def sfx_whoosh():
    dur = 0.46
    shape = lambda p: 450.0 * (5.8 ** math.sin(math.pi * p))
    w = _swept_noise(dur, 0, 0, damp=0.9, peak_frac=0.5, power=1.8, shape=shape)
    body = mul(lp1(noise(secs(dur)), SR, 500), env_bump(secs(dur), 0.5, 2.0))
    out = zeros(secs(dur))
    mix(out, w, 0, 1.0)
    mix(out, norm(body), 0, 0.25)
    return out


def sfx_shuffle():
    dur = 1.05
    out = zeros(secs(dur))
    grains = [_snap_grain(rng.uniform(2400, 3900), rng.uniform(120, 190), n_t=0.05,
                          tail=0.0035, gain_thump=0.25) for _ in range(8)]
    t = 0.05
    k = 0
    while t < 0.82:
        p = (t - 0.05) / 0.77
        amp = (0.45 + 0.55 * math.sin(math.pi * p)) * rng.uniform(0.7, 1.0)
        mix_at(out, grains[k % len(grains)], t, amp)
        k += 1
        t += 0.016 + 0.006 * math.sin(math.pi * p) + rng.uniform(-0.003, 0.003)
    bed = svf(noise(secs(0.8)), SR, const(2200), damp=1.6)
    bed = mul(norm(bed), env_bump(secs(0.8), 0.5, 1.2))
    mix_at(out, bed, 0.04, 0.18)
    # bridge settle
    mix_at(out, _snap_grain(2100, 120, n_t=0.12), 0.88, 0.8)
    mix_at(out, _snap_grain(2500, 140, n_t=0.12), 0.93, 0.55)
    return out


def sfx_draw():
    out = zeros(secs(0.27))
    mix(out, _swept_noise(0.19, 1400, 3600, damp=1.2, peak_frac=0.5), 0, 1.0)
    mix_at(out, _snap_grain(2800, 165, n_t=0.09), 0.16, 0.55)
    return out


def sfx_tap():
    n = secs(0.075)
    pop = mul(sweep_sine(0.075, SR, 1750, 760, curve_tau=0.014), env_exp(n, SR, 0.017, 0.0008))
    click = mul(hp1(noise(n), SR, 3000), env_exp(n, SR, 0.0015, 0.0002))
    out = zeros(n)
    mix(out, pop, 0, 1.0)
    mix(out, norm(click), 0, 0.22)
    return out


def sfx_select():
    out = zeros(secs(0.14))
    for t, f0, f1, g in ((0.0, 820, 1250, 0.8), (0.045, 1250, 1900, 1.0)):
        n = secs(0.08)
        b = mul(sweep_sine(0.08, SR, f0, f1, curve_tau=0.01), env_exp(n, SR, 0.02, 0.001))
        mix_at(out, b, t, g)
    return out


def sfx_toggle():
    n = secs(0.085)
    a = mul(partials(1000, 0.085, SR, [(1, 1.0, 0.013), (1.5, 0.6, 0.009), (3.1, 0.2, 0.004)]),
            [1.0] * n)
    tick = mul(hp1(noise(n), SR, 5000), env_exp(n, SR, 0.0012, 0.0001))
    out = zeros(n)
    mix(out, a, 0, 1.0)
    mix(out, norm(tick), 0, 0.3)
    return out


def sfx_error():
    out = zeros(secs(0.3))
    for t in (0.0, 0.13):
        nh = secs(0.095)
        a = mul(osc("square", 164.8, nh + secs(0.03), SR), adsr(nh, SR, 0.006, 0.03, 0.8, 0.03))
        b = mul(osc("saw", 174.6, nh + secs(0.03), SR), adsr(nh, SR, 0.006, 0.03, 0.8, 0.03))
        buzz = [x + 0.5 * y for x, y in zip(a, b)]
        buzz = lp_curve(buzz, SR, const(950), poles=2)
        mix_at(out, buzz, t, 1.0)
    return out


def sfx_block():
    dur = 0.55
    out = zeros(secs(dur))
    clang = bell(440, dur, SR, ratio=1.41, index=5.0, decay=0.17, idecay=0.06)
    hi = bell(1250, dur, SR, ratio=2.76, index=2.0, decay=0.09, idecay=0.03)
    n = secs(dur)
    thud = mul(sweep_sine(dur, SR, 150, 62, curve_tau=0.03), env_exp(n, SR, 0.05, 0.001))
    burst = mul(hp1(noise(n), SR, 2500), env_exp(n, SR, 0.006, 0.0003))
    mix(out, clang, 0, 0.8)
    mix(out, hi, 0, 0.35)
    mix(out, thud, 0, 0.9)
    mix(out, norm(burst), 0, 0.35)
    return out


def sfx_reverse():
    dur = 0.55
    out = zeros(secs(dur))
    mix(out, _swept_noise(0.27, 600, 3400, damp=0.8, peak_frac=0.6), 0, 0.9)
    mix_at(out, _swept_noise(0.3, 3400, 600, damp=0.8, peak_frac=0.35), 0.22, 0.9)
    n = secs(dur)
    shape = lambda p: 2.0 ** (math.sin(math.pi * p))
    gl = zeros(n)
    ph = 0.0
    tri = table("tri", 1100, SR)
    for i in range(n):
        f = 520.0 * shape(i / n)
        gl[i] = tri[int(ph)]
        ph += f * TBL_N / SR
        while ph >= TBL_N:
            ph -= TBL_N
    gl = mul(gl, env_bump(n, 0.5, 1.5))
    mix(out, gl, 0, 0.28)
    return out


def sfx_plus():
    dur = 0.42
    n = secs(dur)
    out = zeros(n)
    kick = mul(sweep_sine(dur, SR, 175, 52, curve_tau=0.035), env_exp(n, SR, 0.09, 0.001))
    smack = mul(svf(noise(n), SR, const(1300), damp=1.0), env_exp(n, SR, 0.028, 0.0005))
    mix(out, kick, 0, 1.0)
    mix(out, norm(smack), 0, 0.45)
    nb = secs(0.16)
    blip = mul(osc("square", 660, nb, SR, f_end=990), env_exp(nb, SR, 0.06, 0.002))
    blip = lp_curve(blip, SR, const(3200), poles=1)
    mix_at(out, blip, 0.045, 0.32)
    return out


def sfx_stack_tick():
    n = secs(0.1)
    wood = partials(1750, 0.1, SR, [(1, 1.0, 0.018), (1.48, 0.45, 0.01), (2.7, 0.2, 0.005)])
    nz = mul(svf(noise(n), SR, const(3000), damp=1.2), env_exp(n, SR, 0.003, 0.0002))
    out = zeros(n)
    mix(out, wood, 0, 1.0)
    mix(out, norm(nz), 0, 0.3)
    return out


def sfx_prism():
    dur = 1.05
    out = zeros(secs(dur))
    for k, m in enumerate((84, 88, 91, 96)):
        b = bell(midi(m), 0.75, SR, ratio=2.0, index=2.2, decay=0.4, idecay=0.12)
        s = bell(midi(m) * 1.003, 0.75, SR, ratio=3.5, index=0.8, decay=0.28)
        mix_at(out, b, k * 0.06, 0.6)
        mix_at(out, s, k * 0.06, 0.25)
    for _ in range(16):
        f = rng.uniform(3000, 6800)
        t = rng.uniform(0.1, 0.75)
        p = partials(f, 0.18, SR, [(1, 1.0, rng.uniform(0.03, 0.06))])
        mix_at(out, p, t, 0.12 * (1.0 - t))
    return out


def sfx_storm():
    dur = 1.7
    n = secs(dur)
    out = zeros(n)
    crack = mul(hp1(noise(n), SR, 1500), env_exp(n, SR, 0.03, 0.0005))
    crack2 = mul(hp1(noise(n), SR, 2200), env_exp(n, SR, 0.018, 0.0005))
    rumble = lp_curve(noise(n), SR, const(140), poles=3)
    renv = env_exp(n, SR, 0.55, 0.09)
    rumble = mul(norm(rumble), renv)
    mod = [0.75 + 0.25 * math.sin(TAU * 7.0 * i / SR + 2.0 * math.sin(TAU * 1.3 * i / SR)) for i in range(n)]
    rumble = mul(rumble, mod)
    mix(out, norm(crack), 0, 0.9)
    mix_at(out, norm(crack2), 0.075, 0.6)
    mix(out, rumble, 0, 1.0)
    return out


def sfx_eclipse():
    dur = 2.1
    n = secs(dur)
    out = zeros(n)
    whoomp = mul(sweep_sine(dur, SR, 95, 36, curve_tau=0.25), env_exp(n, SR, 0.45, 0.03))
    swell = lp_curve(noise(n), SR, const(320), poles=2)
    swell = mul(norm(swell), env_exp(n, SR, 0.4, 0.12))
    mix(out, whoomp, 0, 1.0)
    mix(out, swell, 0, 0.35)
    # shimmering rising pad
    hold = secs(1.5)
    pad = zeros(hold + secs(0.5))
    for m in (69, 73, 76, 81):
        for dc in (-7, 7):
            v = note("saw", m, 1.5, SR, a=0.6, d=0.2, s=1.0, r=0.5, detune_cents=dc)
            mix(pad, v, 0, 0.25)
    pad = lp_curve(pad, SR, expo(350, 5200), poles=2)
    mix_at(out, norm(pad), 0.08, 0.55)
    np_ = secs(1.6)
    shim = partials(1760, 1.6, SR, [(1, 1.0, 1.2), (1.4983, 0.7, 1.0), (2.0, 0.4, 0.8)])
    trem = [0.5 + 0.5 * math.sin(TAU * 9.0 * i / SR) for i in range(np_)]
    rise = [min(1.0, i / (0.7 * SR)) for i in range(np_)]
    shim = mul(mul(shim, trem), rise)
    mix_at(out, norm(shim), 0.35, 0.18)
    return out


def _brass(m, hold, sr=SR, rel=0.2, vib=0.006, bright=4200):
    f = midi(m)
    env = adsr(secs(hold, sr), sr, 0.025, 0.12, 0.82, rel)
    a = osc("saw", f, len(env), sr, vib=vib, vib_rate=5.8, vib_delay=0.14, phase=rng.random())
    b = osc("saw", f * 1.004, len(env), sr, vib=vib, vib_rate=5.4, vib_delay=0.16, phase=rng.random())
    tone = [x + 0.7 * y for x, y in zip(a, b)]
    tone = mul(tone, env)
    n = len(tone)
    att = 0.06 * sr
    fn = lambda p: 500.0 + (bright - 500.0) * min(1.0, (p * n) / att) * (1.0 - 0.35 * p)
    return lp_curve(tone, sr, fn, poles=2)


def sfx_mania():
    dur = 1.05
    out = zeros(secs(dur))
    for k, m in enumerate((67, 72, 76)):
        mix_at(out, _brass(m, 0.06, rel=0.05), k * 0.07, 0.55)
    t0 = 0.21
    for m in (72, 76, 79, 84):
        mix_at(out, _brass(m, 0.5, rel=0.25, bright=5200), t0, 0.32)
    mix_at(out, bell(midi(96), 0.6, SR, ratio=2.0, index=1.6, decay=0.3), t0, 0.35)
    shine = _swept_noise(0.5, 3000, 9000, damp=1.0, peak_frac=0.3)
    shine = hp1(shine, SR, 3000)
    mix_at(out, norm(shine), t0, 0.12)
    n = secs(0.2)
    punch = mul(sweep_sine(0.2, SR, 140, 60, curve_tau=0.03), env_exp(n, SR, 0.06, 0.001))
    mix_at(out, punch, t0, 0.5)
    return out


def sfx_catch():
    dur = 0.5
    n = secs(dur)
    out = zeros(n)
    thud = mul(sweep_sine(dur, SR, 130, 48, curve_tau=0.03), env_exp(n, SR, 0.08, 0.001))
    smack = mul(svf(noise(n), SR, const(900), damp=1.1), env_exp(n, SR, 0.04, 0.0005))
    mix(out, thud, 0, 1.0)
    mix(out, norm(smack), 0, 0.55)
    for t, m in ((0.07, 64), (0.17, 60)):
        v = note("square", m, 0.08, SR, a=0.004, d=0.04, s=0.6, r=0.05)
        v = lp_curve(v, SR, const(1600), poles=1)
        mix_at(out, v, t, 0.35)
    return out


def sfx_overload():
    dur = 1.05
    n = secs(dur)
    out = zeros(n)
    impact = mul(hp1(noise(n), SR, 2000), env_exp(n, SR, 0.06, 0.0005))
    mix(out, norm(impact), 0, 0.7)
    thud = mul(sweep_sine(dur, SR, 110, 48, curve_tau=0.04), env_exp(n, SR, 0.09, 0.001))
    mix(out, thud, 0, 0.6)
    for _ in range(44):
        on = 0.6 * (rng.random() ** 2)
        f = rng.uniform(2500, 7600)
        p = partials(f, 0.25, SR, [(1, 1.0, rng.uniform(0.025, 0.09)), (1.37, 0.4, 0.02)])
        mix_at(out, p, on, 0.22 * (1.0 - on))
    crunch = mul(svf(noise(n), SR, const(4200), damp=0.7), env_exp(n, SR, 0.12, 0.001))
    crk = [1.0 if rng.random() < 0.06 else 0.25 for _ in range(n)]
    mix(out, norm(mul(crunch, crk)), 0, 0.25)
    return out


def sfx_coin():
    out = zeros(secs(0.55))
    p1 = partials(988, 0.12, SR, [(1, 1.0, 0.05), (2.76, 0.3, 0.02), (5.4, 0.12, 0.01)])
    p2 = partials(1319, 0.48, SR, [(1, 1.0, 0.22), (2.76, 0.28, 0.06), (5.4, 0.1, 0.025), (2.0, 0.15, 0.12)])
    mix(out, p1, 0, 0.8)
    mix_at(out, p2, 0.075, 1.0)
    return out


def sfx_chest():
    dur = 1.55
    n = secs(dur)
    out = zeros(n)
    nc = secs(0.45)
    creak = zeros(nc)
    tbl = table("saw", 200, SR)
    ph = 0.0
    for i in range(nc):
        t = i / SR
        f = 135 + 45 * math.sin(TAU * 2.6 * t) + 8 * math.sin(TAU * 17 * t)
        creak[i] = tbl[int(ph)]
        ph += f * TBL_N / SR
        while ph >= TBL_N:
            ph -= TBL_N
    stick = [(0.5 + 0.5 * math.sin(TAU * 34 * i / SR)) ** 3 for i in range(nc)]
    creak = mul(creak, stick)
    creak = svf(creak, SR, const(950), damp=0.45)
    creak = mul(norm(creak), env_bump(nc, 0.4, 1.0))
    mix(out, creak, 0, 0.5)
    t0 = 0.45
    np_ = secs(0.25)
    pop = mul(sweep_sine(0.25, SR, 180, 70, curve_tau=0.03), env_exp(np_, SR, 0.07, 0.001))
    mix_at(out, pop, t0, 0.8)
    for k, m in enumerate((84, 88, 91, 96, 100)):
        mix_at(out, bell(midi(m), 0.8, SR, ratio=2.0, index=1.8, decay=0.35), t0 + 0.04 + k * 0.05, 0.4)
    sh = _swept_noise(0.9, 4000, 9000, damp=1.0, peak_frac=0.2)
    mix_at(out, norm(hp1(sh, SR, 3500)), t0, 0.1)
    return out


def sfx_win():
    dur = 2.7
    out = zeros(secs(dur))
    seq = [(0.00, 0.10, 67), (0.13, 0.10, 72), (0.26, 0.10, 76), (0.39, 0.22, 79), (0.65, 0.10, 76)]
    for t, h, m in seq:
        mix_at(out, _brass(m, h, rel=0.08, bright=4800), t, 0.5)
    t0 = 0.79
    for m in (60, 64, 67, 72, 76, 84):
        g = 0.42 if m == 84 else 0.26
        mix_at(out, _brass(m, 1.35, rel=0.5, bright=4600), t0, g)
    for k, m in enumerate((84, 88, 91, 96, 100, 103)):
        mix_at(out, bell(midi(m), 1.0, SR, ratio=2.0, index=1.8, decay=0.45), t0 + 0.05 + k * 0.07, 0.22)
    for t in (0.0, t0):
        nt = secs(0.6)
        timp = mul(sweep_sine(0.6, SR, 115, 72, curve_tau=0.05), env_exp(nt, SR, 0.22, 0.002))
        mix_at(out, timp, t, 0.6)
    nc = secs(1.6)
    cym = mul(hp1(hp1(noise(nc), SR, 4500), SR, 4500), env_exp(nc, SR, 0.55, 0.004))
    mix_at(out, norm(cym), t0, 0.16)
    return out


def sfx_lose():
    dur = 1.45
    out = zeros(secs(dur))
    seq = [(0.0, 0.18, 67), (0.22, 0.18, 63), (0.44, 0.18, 60), (0.66, 0.5, 59)]
    for t, h, m in seq:
        vib = 0.012 if h > 0.3 else 0.0
        a = note("square", m, h, SR, a=0.01, d=0.08, s=0.75, r=0.18, vib=vib)
        b = note("tri", m - 12, h, SR, a=0.01, d=0.08, s=0.75, r=0.18, vib=vib)
        v = [x * 0.6 + y for x, y in zip(a, b)]
        v = lp_curve(v, SR, const(1250), poles=2)
        mix_at(out, v, t, 1.0)
    return out


def sfx_swap():
    dur = 0.6
    out = zeros(secs(dur))
    mix(out, _swept_noise(0.36, 500, 3600, damp=0.9, peak_frac=0.65), 0, 0.85)
    mix_at(out, _swept_noise(0.38, 3600, 500, damp=0.9, peak_frac=0.3), 0.15, 0.85)
    mix_at(out, _snap_grain(2700, 150, n_t=0.08), 0.5, 0.4)
    return out


def sfx_purge():
    dur = 0.85
    n = secs(dur)
    out = zeros(n)
    fire = svf(noise(n), SR, expo(280, 2600), damp=0.8)
    fire = mul(norm(fire), env_bump(n, 0.3, 1.3))
    mix(out, fire, 0, 0.8)
    rum = lp_curve(noise(n), SR, const(180), poles=2)
    mix(out, mul(norm(rum), env_bump(n, 0.25, 1.0)), 0, 0.45)
    grain = mul(hp1(noise(secs(0.004)), SR, 1500), env_exp(secs(0.004), SR, 0.0012, 0.0001))
    for _ in range(70):
        t = rng.uniform(0.02, 0.7)
        mix_at(out, grain, t, rng.uniform(0.2, 0.7) * (1.0 - t))
    sizzle = mul(hp1(noise(n), SR, 5000), env_exp(n, SR, 0.25, 0.2))
    mix(out, norm(sizzle), 0, 0.12)
    return out


def sfx_turn():
    out = zeros(secs(0.8))
    mix(out, bell(midi(79), 0.55, SR, ratio=2.0, index=1.1, decay=0.3), 0, 0.7)
    mix_at(out, bell(midi(84), 0.65, SR, ratio=2.0, index=1.1, decay=0.38), 0.12, 0.8)
    return out


SFX = [
    ("sfx_card_slide", sfx_card_slide),
    ("sfx_card_snap1", lambda: sfx_card_snap(0)),
    ("sfx_card_snap2", lambda: sfx_card_snap(1)),
    ("sfx_card_snap3", lambda: sfx_card_snap(2)),
    ("sfx_deal", sfx_deal),
    ("sfx_shuffle", sfx_shuffle),
    ("sfx_draw", sfx_draw),
    ("sfx_tap", sfx_tap),
    ("sfx_toggle", sfx_toggle),
    ("sfx_error", sfx_error),
    ("sfx_block", sfx_block),
    ("sfx_reverse", sfx_reverse),
    ("sfx_plus", sfx_plus),
    ("sfx_stack_tick", sfx_stack_tick),
    ("sfx_prism", sfx_prism),
    ("sfx_storm", sfx_storm),
    ("sfx_eclipse", sfx_eclipse),
    ("sfx_mania", sfx_mania),
    ("sfx_catch", sfx_catch),
    ("sfx_overload", sfx_overload),
    ("sfx_coin", sfx_coin),
    ("sfx_chest", sfx_chest),
    ("sfx_win", sfx_win),
    ("sfx_lose", sfx_lose),
    ("sfx_swap", sfx_swap),
    ("sfx_purge", sfx_purge),
    ("sfx_turn", sfx_turn),
    ("sfx_whoosh", sfx_whoosh),
    ("sfx_select", sfx_select),
]


# ================================================================ MUSIC

def _drums(sr):
    nk = secs(0.4, sr)
    kick = mul(sweep_sine(0.4, sr, 120, 44, curve_tau=0.03), env_exp(nk, sr, 0.13, 0.001))
    click = mul(lp1(noise(nk), sr, 3000), env_exp(nk, sr, 0.002, 0.0002))
    mix(kick, norm(click), 0, 0.15)
    ns = secs(0.25, sr)
    sn_n = svf(noise(ns), sr, const(1900), damp=1.3)
    snare = mul(norm(sn_n), env_exp(ns, sr, 0.075, 0.0008))
    tone = mul(sweep_sine(0.25, sr, 230, 180, curve_tau=0.02), env_exp(ns, sr, 0.04, 0.0008))
    mix(snare, tone, 0, 0.5)
    nh = secs(0.09, sr)
    hat = mul(norm(hp1(hp1(noise(nh), sr, 6000), sr, 6000)), env_exp(nh, sr, 0.018, 0.0005))
    no = secs(0.3, sr)
    ohat = mul(norm(hp1(hp1(noise(no), sr, 5500), sr, 5500)), env_exp(no, sr, 0.09, 0.001))
    nr = secs(0.08, sr)
    rim = mul(norm(svf(noise(nr), sr, const(2400), damp=0.8)), env_exp(nr, sr, 0.012, 0.0003))
    mix(rim, partials(820, 0.08, sr, [(1, 1.0, 0.02)]), 0, 0.6)
    shk = mul(norm(svf(noise(nh), sr, const(4500), damp=1.5)), env_bump(nh, 0.3, 1.0))
    return {"kick": norm(kick), "snare": norm(snare), "hat": hat, "ohat": ohat,
            "rim": norm(rim), "shaker": shk}


class Track:
    def __init__(self, bpm, bars, sr=MSR, tail=4.0):
        self.bpm = bpm
        self.sr = sr
        self.beat = 60.0 / bpm
        self.loop_n = int(round(bars * 4 * self.beat * sr))
        self.n = self.loop_n + secs(tail, sr)
        self.bus = {}

    def buf(self, name):
        if name not in self.bus:
            self.bus[name] = zeros(self.n)
        return self.bus[name]

    def at(self, beat):
        return int(round(beat * self.beat * self.sr))

    def put(self, bus, src, beat, gain=1.0):
        mix(self.buf(bus), src, self.at(beat), gain)

    def finish(self, gains, master_lp=None, peak=0.5):
        out = zeros(self.n)
        for name, g in gains.items():
            if name in self.bus:
                mix(out, self.bus[name], 0, g)
        if master_lp:
            out = lp_curve(out, self.sr, const(master_lp), poles=1)
        # fold the tail back to the start => seamless circular loop
        L = self.loop_n
        loop = out[:L]
        for i in range(L, self.n):
            loop[i - L] += out[i]
        # remove DC so the wrap point sits at the same level
        dc = sum(loop) / L
        loop = [x - dc for x in loop]
        return norm(loop, peak)


def pluck(kind, m, hold, sr, tau=0.18, f_hi=4200, f_lo=700, ftau=0.12, detune=0.0):
    f = midi(m) * (2.0 ** (detune / 1200.0))
    n = secs(hold + 0.25, sr)
    o = osc(kind, f, n, sr, phase=rng.random())
    env = env_exp(n, sr, tau, 0.003)
    gate = secs(hold, sr)
    rel = secs(0.25, sr)
    for i in range(gate, n):
        env[i] *= max(0.0, 1.0 - (i - gate) / rel)
    o = mul(o, env)
    # pluck filter: cutoff decays exponentially from f_hi to f_lo
    def fn(p, _n=n, _sr=sr):
        t = p * _n / _sr
        return f_lo + (f_hi - f_lo) * math.exp(-t / ftau)
    return lp_curve(o, sr, fn, poles=1)


def pad_chord(notes, hold, sr, kind="saw", a=0.25, r=0.6, cutoff=1800, detune=7.0, s=1.0):
    nh = secs(hold, sr)
    env = adsr(nh, sr, a, 0.3, s, r)
    acc = zeros(len(env))
    for m in notes:
        for dc in (-detune, detune):
            f = midi(m) * (2.0 ** (dc / 1200.0))
            mix(acc, osc(kind, f, len(env), sr, phase=rng.random()), 0, 1.0)
    acc = mul(acc, env)
    return lp_curve(acc, sr, const(cutoff), poles=2)


# ---- shared day/night composition (C  G  Am  F  C  G  F  G) x2

DAY_CHORDS = [
    (36, (60, 64, 67)),   # C
    (43, (59, 62, 67)),   # G/B voicing
    (45, (60, 64, 69)),   # Am
    (41, (60, 65, 69)),   # F
    (36, (60, 64, 67)),   # C
    (43, (59, 62, 67)),   # G
    (41, (60, 65, 69)),   # F
    (43, (59, 62, 67)),   # G
]

DAY_MELODY = [
    [(0, 1, 76), (1, .5, 79), (1.5, .5, 76), (2, 1.5, 72), (3.5, .5, 74)],
    [(0, 1, 74), (1, .5, 71), (1.5, .5, 74), (2, 2, 79)],
    [(0, 1, 76), (1, .5, 72), (1.5, .5, 76), (2, 1, 81), (3, 1, 79)],
    [(0, 1.5, 77), (1.5, .5, 76), (2, 1, 74), (3, 1, 72)],
    [(0, .5, 72), (.5, .5, 74), (1, .5, 76), (1.5, .5, 79), (2, 1, 84), (3, 1, 79)],
    [(0, 1, 83), (1, 1, 79), (2, 1, 74), (3, 1, 79)],
    [(0, 1, 81), (1, .5, 79), (1.5, .5, 77), (2, 1, 76), (3, 1, 74)],
    [(0, 2, 79), (2, 1, 74), (3, 1, 71)],
]


def _harm_third_below(m, chord):
    """Pick the nearest chord tone (any octave) 3-4 semitones below m."""
    best = m - 3
    for c in chord:
        for o in (-24, -12, 0, 12):
            cand = c + o
            if 2 <= m - cand <= 5:
                return cand
    return best


def music_day():
    tr = Track(118, 16)
    sr = tr.sr
    d = _drums(sr)
    beat = tr.beat
    for bar in range(16):
        root, chord = DAY_CHORDS[bar % 8]
        b0 = bar * 4
        # bass: driving 8ths with octave jumps
        pattern = [0, 0, 12, 0, 7, 0, 12, 7]
        for k, off in enumerate(pattern):
            v = pluck("saw", root + off, beat * 0.42, sr, tau=0.16, f_hi=1500, f_lo=260, ftau=0.08)
            tr.put("bass", v, b0 + k * 0.5, 0.9 if k % 2 == 0 else 0.7)
        # chord pad
        tr.put("pad", pad_chord(chord, beat * 3.9, sr, a=0.18, r=0.5, cutoff=2000), b0, 1.0)
        # arpeggio pluck (chord tones up an octave)
        tones = sorted(chord)
        arp = [0, 2, 1, 2, 0, 2, 1, 2] if bar % 2 == 0 else [0, 1, 2, 1, 2, 1, 0, 1]
        for k, idx in enumerate(arp):
            v = pluck("square", tones[idx] + 12, beat * 0.3, sr, tau=0.1, f_hi=3800, f_lo=900, ftau=0.06)
            tr.put("arp", v, b0 + k * 0.5, 0.85 if k % 2 == 0 else 0.6)
        # lead melody
        mel = DAY_MELODY[bar % 8]
        for st, du, m in mel:
            hold = du * beat * 0.92
            v = note("square", m, hold, sr, a=0.008, d=0.09, s=0.55, r=0.12, vib=0.005 if du >= 1 else 0.0)
            v = lp_curve(v, sr, const(3600), poles=1)
            tr.put("lead", v, b0 + st, 1.0)
            if 8 <= bar < 12:
                h = _harm_third_below(m, chord)
                v2 = note("tri", h, hold, sr, a=0.01, d=0.09, s=0.6, r=0.12)
                tr.put("lead", v2, b0 + st, 0.45)
        # drums
        for q in range(4):
            if q in (0, 2):
                tr.put("drums", d["kick"], b0 + q, 1.0)
            if q in (1, 3):
                tr.put("drums", d["snare"], b0 + q, 0.62)
        if bar % 4 == 3:
            tr.put("drums", d["kick"], b0 + 3.5, 0.7)
        for e in range(8):
            h = d["ohat"] if e == 7 and bar % 2 == 1 else d["hat"]
            tr.put("drums", h, b0 + e * 0.5, 0.32 if e % 2 else 0.22)
        for s16 in range(16):
            tr.put("drums", d["shaker"], b0 + s16 * 0.25, 0.06 + (0.04 if s16 % 4 == 2 else 0))
    lead = delay_fx(tr.buf("lead"), sr, beat * 0.75, 0.33, 0.28)
    tr.bus["lead"] = lead
    return tr.finish({"bass": 0.55, "pad": 0.10, "arp": 0.16, "lead": 0.26, "drums": 0.5}, peak=0.5)


def music_night():
    tr = Track(118, 16)
    sr = tr.sr
    d = _drums(sr)
    beat = tr.beat
    night_chords = [
        (36, (55, 59, 64, 67)),   # Cmaj7 (G B E G)
        (43, (55, 59, 62, 66)),   # G with F# colour
        (45, (55, 60, 64, 71)),   # Am9-ish
        (41, (53, 57, 60, 64)),   # Fmaj7
        (36, (55, 59, 64, 67)),
        (43, (55, 59, 62, 66)),
        (41, (53, 57, 60, 64)),
        (43, (55, 59, 62, 65)),   # G7
    ]
    for bar in range(16):
        root, chord = night_chords[bar % 8]
        b0 = bar * 4
        # sub bass: long notes on 1 and the "and" of 2
        v = note("sine", root, beat * 1.4, sr, a=0.02, d=0.2, s=0.8, r=0.3)
        tr.put("bass", v, b0, 1.0)
        v = note("tri", root + 7 if bar % 2 else root, beat * 1.6, sr, a=0.02, d=0.2, s=0.7, r=0.3)
        tr.put("bass", lp1(v, sr, 500), b0 + 2.5, 0.75)
        # warm pad, slow attack
        tr.put("pad", pad_chord(chord, beat * 3.95, sr, kind="saw", a=0.7, r=0.9, cutoff=850, detune=9), b0, 1.0)
        tr.put("pad", pad_chord([chord[0] + 12], beat * 3.95, sr, kind="organ", a=0.9, r=0.9, cutoff=1300, detune=4), b0, 0.4)
        # sparse mellow lead: melody notes landing on beats 0 and 2 only, an octave down
        for st, du, m in DAY_MELODY[bar % 8]:
            if st in (0, 2):
                hold = max(du, 1.0) * beat * 0.95
                v = note("soft", m - 12, hold, sr, a=0.04, d=0.2, s=0.6, r=0.35, vib=0.006)
                v = lp_curve(v, sr, const(1500), poles=2)
                tr.put("lead", v, b0 + st, 1.0)
        # music-box sparkle, quiet
        if bar % 2 == 0:
            for k, m in enumerate(sorted(chord)[1:]):
                b = bell(midi(m + 24), 1.2, sr, ratio=2.0, index=0.9, decay=0.55)
                tr.put("sparkle", b, b0 + 1.5 + k * 0.75, 1.0)
        # soft half-time drums
        tr.put("drums", d["kick"], b0, 0.85)
        if bar % 2 == 1:
            tr.put("drums", d["kick"], b0 + 2.5, 0.55)
        tr.put("drums", d["rim"], b0 + 2, 0.55)
        for e in range(8):
            tr.put("drums", d["shaker"], b0 + e * 0.5, 0.1 if e % 2 else 0.06)
    tr.bus["lead"] = delay_fx(tr.buf("lead"), sr, beat * 0.75, 0.45, 0.4)
    tr.bus["sparkle"] = delay_fx(tr.buf("sparkle"), sr, beat * 1.0, 0.4, 0.35)
    drums = lp_curve(tr.buf("drums"), sr, const(3500), poles=1)
    tr.bus["drums"] = drums
    return tr.finish({"bass": 0.55, "pad": 0.14, "lead": 0.32, "sparkle": 0.07, "drums": 0.42},
                     master_lp=5000, peak=0.5)


MENU_CHORDS = [
    (41, (65, 69, 72)),   # F
    (38, (62, 65, 69)),   # Dm
    (46, (62, 65, 70)),   # Bb
    (48, (64, 67, 72)),   # C
    (41, (65, 69, 72)),   # F
    (38, (62, 65, 69)),   # Dm
    (43, (62, 67, 70)),   # Gm
    (48, (64, 67, 72)),   # C
]

MENU_MELODY = [
    [(0, .5, 72), (.5, .5, 77), (1, .5, 81), (1.5, .5, 77), (2, 1, 79), (3, 1, 77)],
    [(0, 1, 74), (1, .5, 77), (1.5, .5, 81), (2, 2, 79)],
    [(0, .5, 77), (.5, .5, 74), (1, 1, 70), (2, .5, 74), (2.5, .5, 77), (3, 1, 82)],
    [(0, 1.5, 81), (1.5, .5, 79), (2, 1, 76), (3, 1, 72)],
    [(0, .5, 77), (.5, .5, 81), (1, .5, 84), (1.5, .5, 81), (2, 1, 79), (3, .5, 77), (3.5, .5, 79)],
    [(0, 1, 81), (1, 1, 77), (2, 1, 74), (3, 1, 77)],
    [(0, 1, 79), (1, .5, 82), (1.5, .5, 79), (2, 1, 74), (3, 1, 76)],
    [(0, 2, 79), (2, .5, 76), (2.5, .5, 74), (3, 1, 72)],
]


def music_menu():
    tr = Track(110, 16)
    sr = tr.sr
    d = _drums(sr)
    beat = tr.beat
    for bar in range(16):
        root, chord = MENU_CHORDS[bar % 8]
        b0 = bar * 4
        # bouncy bass: 1, and-of-2, 3, 4 with octave/fifth
        for st, off, g in ((0, 0, 1.0), (1.5, 7, 0.75), (2, 12, 0.85), (3, 0, 0.8), (3.5, 7, 0.6)):
            v = pluck("soft", root + off, beat * 0.38, sr, tau=0.14, f_hi=1800, f_lo=300, ftau=0.07)
            tr.put("bass", v, b0 + st, g)
        # off-beat chord "chanks"
        for st in (0.5, 1.5, 2.5, 3.5):
            v = zeros(secs(beat * 0.3 + 0.25, sr))
            for m in chord:
                mix(v, pluck("saw", m, beat * 0.22, sr, tau=0.07, f_hi=3200, f_lo=800, ftau=0.04), 0, 0.5)
            tr.put("chank", v, b0 + st, 1.0)
        # soft pad underneath
        tr.put("pad", pad_chord(chord, beat * 3.9, sr, kind="organ", a=0.3, r=0.5, cutoff=1600, detune=5), b0, 1.0)
        # xylophone/bell melody
        mel = MENU_MELODY[bar % 8]
        octave = 12 if 8 <= bar < 12 else 0
        for st, du, m in mel:
            b = bell(midi(m + octave), min(1.4, du * beat + 0.5), sr, ratio=4.0 if octave else 3.0,
                     index=1.4, decay=0.32, idecay=0.05)
            body = partials(midi(m + octave), min(1.4, du * beat + 0.5), sr, [(1, 1.0, 0.35), (2.0, 0.2, 0.1)])
            mix(b, body, 0, 0.6)
            tr.put("lead", b, b0 + st, 1.0)
        # drums (light, bouncy)
        tr.put("drums", d["kick"], b0, 1.0)
        tr.put("drums", d["kick"], b0 + 2, 0.85)
        if bar % 2 == 1:
            tr.put("drums", d["kick"], b0 + 2.75, 0.55)
        tr.put("drums", d["snare"], b0 + 1, 0.45)
        tr.put("drums", d["snare"], b0 + 3, 0.5)
        for e in range(8):
            tr.put("drums", d["hat"], b0 + e * 0.5, 0.26 if e % 2 else 0.16)
        if bar % 4 == 3:
            for k in range(4):
                tr.put("drums", d["rim"], b0 + 3 + k * 0.25, 0.22 + 0.06 * k)
    tr.bus["lead"] = delay_fx(tr.buf("lead"), sr, beat * 0.75, 0.3, 0.25)
    return tr.finish({"bass": 0.55, "chank": 0.13, "pad": 0.08, "lead": 0.42, "drums": 0.45}, peak=0.5)


MUSIC = [
    ("music_menu", music_menu),
    ("music_day", music_day),
    ("music_night", music_night),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    only = set(sys.argv[1:])
    t_all = time.time()
    for name, fn in SFX:
        if only and name not in only:
            continue
        t = time.time()
        buf = fn()
        write_wav(name, buf, SR, peak=0.891)
        print(f"{name:18s} {len(buf) / SR:5.2f}s  ({time.time() - t:4.1f}s)")
    for name, fn in MUSIC:
        if only and name not in only:
            continue
        t = time.time()
        buf = fn()
        write_wav(name, buf, MSR, normalize=False, fade_in=0, fade_out=0)
        print(f"{name:18s} {len(buf) / MSR:5.2f}s  ({time.time() - t:4.1f}s)")
    print(f"done in {time.time() - t_all:.1f}s -> {OUT}")


if __name__ == "__main__":
    main()
