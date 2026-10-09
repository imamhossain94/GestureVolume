#!/usr/bin/env python3
"""The store videos' sound, made from nothing but code, so there is nothing to license.

Two beds of original music (an upbeat one for the promo, a calm one under the tutorials), the
interface's sound effects, and the mix: a scene's cue sheet (JSON written beside its video)
places each effect at the frame its touch lands on. The Quick slider's step sound is the app's
own res/raw/slider_tick.wav.

    python3 audio.py bed promo 31.5 out.wav
    python3 audio.py bed calm 64 out.wav
    python3 audio.py mix video.mp4 cues.json bed.wav out.mp4
"""
import json
import os
import subprocess
import sys
import wave

import numpy as np
from scipy import signal

SR = 48000
HERE = os.path.dirname(os.path.abspath(__file__))
TICK_WAV = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "raw", "slider_tick.wav")


# --- Basics ------------------------------------------------------------------------------------
def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def midi_hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


_TABLES = {}
_TABLE_N = 4096


def _table(kind, n_harm):
    """One period of a band-limited waveform with n_harm harmonics, built once and reused."""
    key = (kind, n_harm)
    if key not in _TABLES:
        ph = np.arange(_TABLE_N) / _TABLE_N * 2 * np.pi
        out = np.zeros(_TABLE_N)
        if kind == "saw":
            for k in range(1, n_harm + 1):
                out += np.sin(k * ph) / k
            out *= 2 / np.pi
        else:  # triangle: odd harmonics, alternating, 1/k^2
            for i, k in enumerate(range(1, 2 * n_harm, 2)):
                out += ((-1) ** i) * np.sin(k * ph) / (k * k)
            out *= 8 / np.pi ** 2
        _TABLES[key] = np.append(out, out[0])
    return _TABLES[key]


def _osc(kind, freq, dur, phase, harmonics):
    """Reads a wavetable at `freq`, holding every harmonic below Nyquist so nothing aliases."""
    n_max = int(min(harmonics, (SR / 2 - 200) / max(freq, 1)))
    table = _table(kind, max(n_max, 1))
    pos = (freq * t_axis(dur) + phase / (2 * np.pi)) % 1.0 * _TABLE_N
    return np.interp(pos, np.arange(_TABLE_N + 1), table)


def saw(freq, dur, phase=0.0, harmonics=40):
    return _osc("saw", freq, dur, phase, harmonics)


def tri(freq, dur):
    return _osc("tri", freq, dur, 0.0, 8)


def sine(freq, dur, phase=0.0):
    return np.sin(2 * np.pi * freq * t_axis(dur) + phase)


def env_adsr(n, a, d, s, r, sustain_len=None):
    a_n, d_n, r_n = int(a * SR), int(d * SR), int(r * SR)
    sus_n = max(0, n - a_n - d_n - r_n) if sustain_len is None else int(sustain_len * SR)
    e = np.concatenate([
        np.linspace(0, 1, max(a_n, 1), endpoint=False),
        np.linspace(1, s, max(d_n, 1), endpoint=False),
        np.full(sus_n, s),
        np.linspace(s, 0, max(r_n, 1)),
    ])
    if len(e) < n:
        e = np.concatenate([e, np.zeros(n - len(e))])
    return e[:n]


def env_exp(n, decay):
    return np.exp(-np.arange(n) / (decay * SR))


def lowpass(x, cutoff, order=2):
    b, a = signal.butter(order, min(cutoff / (SR / 2), 0.99), "low")
    return signal.lfilter(b, a, x)


def highpass(x, cutoff, order=2):
    b, a = signal.butter(order, min(cutoff / (SR / 2), 0.99), "high")
    return signal.lfilter(b, a, x)


def bandpass(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), min(hi / (SR / 2), 0.99)], "band")
    return signal.lfilter(b, a, x)


def place(buf, x, at, gain=1.0):
    """Adds mono or stereo x into stereo buf starting at `at` seconds."""
    i = int(at * SR)
    if i >= buf.shape[0]:
        return
    if x.ndim == 1:
        x = np.stack([x, x], axis=1)
    j = min(buf.shape[0], i + x.shape[0])
    buf[i:j] += x[: j - i] * gain


def pan(x, p):
    """Equal-power pan, p in [-1, 1]."""
    ang = (p + 1) * np.pi / 4
    return np.stack([x * np.cos(ang), x * np.sin(ang)], axis=1)


def comb(x, d, g):
    """y[n] = x[n] + g*y[n-d], a block of d samples at a time (each block needs only the last)."""
    y = x.copy()
    for i in range(d, len(y), d):
        j = min(len(y), i + d)
        y[i:j] += g * y[i - d : j - d]
    return y


def allpass(x, d, g):
    """y[n] = -g*x[n] + x[n-d] + g*y[n-d], block by block like comb()."""
    xd = np.concatenate([np.zeros(d), x])[: len(x)]
    y = -g * x + xd
    for i in range(d, len(y), d):
        j = min(len(y), i + d)
        y[i:j] += g * y[i - d : j - d]
    return y


def reverb(st, mix=0.25, room=0.82, damp=3800):
    """A small Schroeder reverb: four combs and two allpasses per side."""
    out = np.zeros_like(st)
    for ch, spread in ((0, 0), (1, 23)):
        x = lowpass(st[:, ch], damp, 1)
        acc = np.zeros(len(x))
        for d_ms in (29.7, 37.1, 41.1, 43.7):
            acc += comb(x, int((d_ms + spread / 10) * SR / 1000), room)
        acc /= 4
        for d_ms, g in ((5.0, 0.7), (1.7, 0.7)):
            acc = allpass(acc, int(d_ms * SR / 1000), g)
        out[:, ch] = acc * (1 - room)
    return st * (1 - mix) + out * mix * 2.2


def delay(st, time_s, fb=0.35, mix=0.25, pingpong=True):
    d = int(time_s * SR)
    out = np.zeros_like(st)
    wet = []
    for ch in (0, 1):
        delayed = np.concatenate([np.zeros(d), st[:, ch]])[: len(st)]
        wet.append(lowpass(comb(delayed, d, fb), 5000, 1))
    if pingpong:
        wet.reverse()
    out[:, 0], out[:, 1] = wet
    return st + out * mix


def soft_clip(x, drive=1.0):
    return np.tanh(x * drive) / np.tanh(drive)


def sidechain(n, beat_times, depth=0.55, release=0.22):
    """A gain curve that dips at each kick, the classic pumping pad."""
    g = np.ones(n)
    rel = int(release * SR)
    shape = 1 - depth * (1 - np.linspace(0, 1, rel) ** 0.6)
    for bt in beat_times:
        i = int(bt * SR)
        if i >= n:
            continue
        j = min(n, i + rel)
        g[i:j] = np.minimum(g[i:j], shape[: j - i])
    return g


# --- Instruments -------------------------------------------------------------------------------
def pad_chord(notes, dur, cutoff=1900, detune=0.09):
    n = int(dur * SR)
    mixl, mixr = np.zeros(n), np.zeros(n)
    for m in notes:
        f = midi_hz(m)
        for k, (cents, side) in enumerate(((-detune * 100, -0.6), (0, 0), (detune * 100, 0.6))):
            v = saw(f * 2 ** (cents / 1200), dur, phase=k * 1.7, harmonics=24)
            mixl += v * (0.5 - side * 0.35)
            mixr += v * (0.5 + side * 0.35)
    e = env_adsr(n, 0.35, 0.4, 0.8, 0.6)
    st = np.stack([lowpass(mixl, cutoff) * e, lowpass(mixr, cutoff) * e], axis=1)
    return st / (len(notes) * 2.2)


def pluck(m, dur=0.45, bright=4200):
    n = int(dur * SR)
    f = midi_hz(m)
    x = saw(f, dur, harmonics=18) * 0.6 + sine(f * 2, dur) * 0.25 + sine(f, dur) * 0.35
    e = env_exp(n, 0.11) * np.minimum(1, np.arange(n) / (0.004 * SR))
    filt_env = lowpass(x, bright) * e
    return filt_env * 0.5


def keys_note(m, dur=1.6):
    """A soft electric-piano tone: a sine with a decaying bell partial and gentle tremolo."""
    n = int(dur * SR)
    f = midi_hz(m)
    t = t_axis(dur)
    mod = np.sin(2 * np.pi * f * 2 * t) * 1.4 * env_exp(n, 0.18)
    x = np.sin(2 * np.pi * f * t + mod) * 0.7 + np.sin(2 * np.pi * f * 4 * t) * 0.06 * env_exp(n, 0.08)
    trem = 1 - 0.08 * (1 + np.sin(2 * np.pi * 4.2 * t)) / 2
    e = env_adsr(n, 0.006, 0.6, 0.35, 0.5)
    return x * e * trem * 0.35


def bass_note(m, dur):
    n = int(dur * SR)
    f = midi_hz(m)
    x = sine(f, dur) * 0.8 + tri(f, dur) * 0.3
    e = env_adsr(n, 0.008, 0.15, 0.85, 0.06)
    return lowpass(x * e, 900) * 0.55


def kick(dur=0.32):
    n = int(dur * SR)
    t = t_axis(dur)
    f = 45 + 95 * np.exp(-t / 0.035)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) * env_exp(n, 0.085)
    click = highpass(np.random.default_rng(3).normal(0, 1, n), 2500) * env_exp(n, 0.003) * 0.2
    return soft_clip((x + click) * 1.2, 1.4) * 0.8


def clap(dur=0.25, seed=5):
    n = int(dur * SR)
    noise = np.random.default_rng(seed).normal(0, 1, n)
    x = bandpass(noise, 900, 3200)
    e = np.zeros(n)
    for k, off in enumerate((0, 0.009, 0.018)):
        i = int(off * SR)
        e[i:] += env_exp(n - i, 0.012 if k < 2 else 0.07) * (0.7 if k < 2 else 1)
    return x * e * 0.32


def hat(dur=0.06, seed=9, open_=False):
    n = int(dur * SR)
    noise = np.random.default_rng(seed).normal(0, 1, n)
    x = highpass(noise, 7000, 2)
    return x * env_exp(n, 0.05 if open_ else 0.014) * 0.17


def riser(dur=1.4, seed=11):
    n = int(dur * SR)
    noise = np.random.default_rng(seed).normal(0, 1, n)
    out = np.zeros(n)
    hop = 1024
    for i in range(0, n, hop):
        frac = i / n
        lo = 400 + 5000 * frac ** 2
        seg_ = bandpass(noise[i : i + hop * 2], lo, lo * 1.8, 1)[:hop]
        out[i : i + len(seg_)] = seg_
    return out * np.linspace(0, 1, n) ** 2 * 0.25


def shimmer(dur=1.8, root=84):
    """A rising sparkle, for the logo's arrival."""
    n = int(dur * SR)
    out = np.zeros(n)
    for k, m in enumerate((root, root + 4, root + 7, root + 11, root + 12, root + 16)):
        start = int(k * 0.055 * SR)
        x = sine(midi_hz(m), dur) * env_exp(n, 0.5) * 0.12
        out[start:] += x[: n - start]
    return out


# --- Beds --------------------------------------------------------------------------------------
# F major, I - V - vi - IV, voiced around middle C.
PROG = [
    ([53, 57, 60, 64], 41, [65, 69, 72, 76]),   # Fmaj7: F A C E
    ([52, 55, 60, 64], 36, [64, 67, 72, 76]),   # C/E:  E G C E
    ([50, 57, 60, 65], 38, [62, 65, 69, 72]),   # Dm7:  D A C F
    ([50, 53, 58, 62], 34, [62, 65, 70, 74]),   # Bb:   D F Bb D
]


def bed_promo(total, intro_bars=2, end_bar=None):
    """Upbeat, 112 bpm: `intro_bars` of pad and plucks, then the full groove, then from `end_bar`
    a held ending with a final hit and a sparkle, so a video can land its logo on that bar."""
    bpm = 112
    beat = 60 / bpm
    bar = beat * 4
    n = int(total * SR)
    mus = np.zeros((n, 2))
    drums = np.zeros((n, 2))
    kicks = []
    bars = int(np.ceil(total / bar))
    end_bar = end_bar if end_bar is not None else max(intro_bars, bars - 1)
    for b in range(bars):
        chord, root, arp = PROG[b % 4]
        t0 = b * bar
        if t0 >= total:
            break
        last = b >= end_bar
        place(mus, pad_chord(chord, bar + 0.6 if not last else total - t0, cutoff=2600), t0, 0.95)
        if not last:
            for s in range(8):
                note = arp[[0, 1, 2, 3, 2, 1, 3, 2][s]] + (12 if s in (3, 6) and b % 2 else 0)
                place(mus, pan(pluck(note, bright=6500), -0.35 if s % 2 else 0.35), t0 + s * beat / 2, 0.75)
        if b >= intro_bars and not last:
            for q in range(4):
                place(mus, bass_note(root, beat * 0.92), t0 + q * beat, 0.42)
                place(drums, kick(), t0 + q * beat, 0.42)
                kicks.append(t0 + q * beat)
                place(drums, pan(hat(seed=q + b * 4), 0.25), t0 + q * beat + beat / 2, 1)
                if q in (1, 3):
                    place(drums, clap(seed=b * 7 + q), t0 + q * beat, 1)
        if b == intro_bars - 1:
            place(mus, pan(riser(bar * 0.9), 0), t0 + bar * 0.1, 0.8)
        if last:
            place(drums, kick(0.5), t0, 0.5)
            place(mus, pan(shimmer(min(2.2, total - t0)), 0.1), t0 + 0.02, 1.0)
            place(mus, bass_note(root, min(bar, total - t0)), t0, 0.42)
    mus *= sidechain(n, kicks, 0.5)[:, None]
    mus = delay(mus, beat * 0.75, 0.3, 0.18)
    mix = reverb(mus, 0.22) + drums
    return finish(mix, total, fade_in=0.05, fade_out=1.2)


def bed_calm(total):
    """Calm, 84 bpm: soft keys and a light, swung beat; any length, faded at the end."""
    bpm = 84
    beat = 60 / bpm
    bar = beat * 4
    n = int(total * SR)
    mus = np.zeros((n, 2))
    drums = np.zeros((n, 2))
    bars = int(np.ceil(total / bar)) + 1
    for b in range(bars):
        chord, root, arp = PROG[b % 4]
        t0 = b * bar
        if t0 >= total:
            break
        for k, m in enumerate(chord):
            place(mus, pan(keys_note(m + 12, bar * 0.95), (k - 1.5) * 0.25), t0 + k * 0.012, 0.75)
        place(mus, pad_chord(chord, bar + 0.4, cutoff=1400), t0, 0.22)
        for q in range(4):
            place(mus, bass_note(root, beat * 0.8), t0 + q * beat, 0.24 if q % 2 == 0 else 0.15)
        # A sparse melody of chord tones on the off-beats.
        mel = [arp[2], arp[3], arp[1], arp[2]]
        for q, m in enumerate(mel):
            if (b + q) % 3 == 0:
                place(mus, pan(keys_note(m + 12, 0.9) * 0.6, 0.3), t0 + q * beat + beat * 0.5, 0.6)
        if b >= 1:
            for q in range(4):
                if q in (0, 2):
                    place(drums, kick(0.28) * 0.7, t0 + q * beat, 0.4)
                if q in (1, 3):
                    place(drums, clap(seed=b + q) * 0.6, t0 + q * beat, 0.6)
                swing = beat * 0.58
                place(drums, pan(hat(seed=b * 9 + q) * 0.7, 0.2), t0 + q * beat, 0.8)
                place(drums, pan(hat(seed=b * 9 + q + 50) * 0.5, -0.2), t0 + q * beat + swing, 0.7)
    mus = delay(mus, beat * 0.75, 0.25, 0.12)
    mix = reverb(mus, 0.3, 0.86) + lowpass(drums.T, 9000).T * 0.8
    return finish(mix, total, fade_in=1.0, fade_out=2.5)


def finish(mix, total, fade_in, fade_out):
    n = int(total * SR)
    mix = mix[:n]
    mix = np.stack([highpass(mix[:, c], 35) for c in (0, 1)], axis=1)
    low = np.stack([lowpass(mix[:, c], 160) for c in (0, 1)], axis=1)
    mix = mix - low * 0.35
    fi, fo = int(fade_in * SR), int(fade_out * SR)
    mix[:fi] *= np.linspace(0, 1, fi)[:, None]
    mix[n - fo :] *= np.linspace(1, 0, fo)[:, None]
    mix = soft_clip(mix * 0.9, 1.2)
    peak = np.max(np.abs(mix)) or 1
    return mix / peak * 0.8


# --- Sound effects -----------------------------------------------------------------------------
def load_wav(path):
    with wave.open(path) as w:
        sr, n, ch = w.getframerate(), w.getnframes(), w.getnchannels()
        x = np.frombuffer(w.readframes(n), dtype=np.int16).astype(np.float64) / 32768
    if ch == 2:
        x = x.reshape(-1, 2).mean(axis=1)
    if sr != SR:
        x = signal.resample_poly(x, SR, sr)
    return x


def sfx(name, seed=1):
    rng = np.random.default_rng(seed)
    if name == "tap":
        n = int(0.06 * SR)
        x = sine(2100, 0.06) * env_exp(n, 0.008) * 0.5 + highpass(rng.normal(0, 1, n), 3000) * env_exp(n, 0.002) * 0.25
        return x * 0.55
    if name == "press":  # a long press settling in
        n = int(0.12 * SR)
        return (sine(660, 0.12) * 0.5 + sine(990, 0.12) * 0.2) * env_adsr(n, 0.01, 0.05, 0.3, 0.05) * 0.35
    if name == "swipe":
        n = int(0.22 * SR)
        noise = rng.normal(0, 1, n)
        out = np.zeros(n)
        hop = 512
        for i in range(0, n, hop):
            fc = 900 + 2600 * (i / n)
            out[i : i + hop] = bandpass(noise[i : i + hop * 2], fc, fc * 1.6, 1)[:hop][: n - i]
        return out * np.sin(np.linspace(0, np.pi, n)) ** 1.5 * 0.35
    if name == "open":  # a panel sliding out
        n = int(0.32 * SR)
        noise = rng.normal(0, 1, n)
        w = lowpass(noise, 2200) * np.sin(np.linspace(0, np.pi, n)) ** 2 * 0.22
        pop = sine(520, 0.32) * env_exp(n, 0.05) * 0.18 * (np.arange(n) > int(0.12 * SR))
        return w + pop
    if name == "close":
        n = int(0.24 * SR)
        noise = rng.normal(0, 1, n)
        return lowpass(noise, 1500) * np.sin(np.linspace(0, np.pi, n)) ** 2 * 0.15
    if name == "toggle":
        n = int(0.09 * SR)
        a = sine(1400, 0.09) * env_exp(n, 0.01)
        b = np.concatenate([np.zeros(int(0.035 * SR)), sine(1800, 0.09) * env_exp(n, 0.012)])[:n]
        return (a + b) * 0.3
    if name == "tick":
        return load_wav(TICK_WAV) * 0.55
    if name == "success":
        n = int(0.7 * SR)
        x = sine(midi_hz(88), 0.7) * env_exp(n, 0.25) * 0.25
        y = np.concatenate([np.zeros(int(0.09 * SR)), sine(midi_hz(95), 0.7) * env_exp(n, 0.3) * 0.22])[:n]
        return x + y
    if name == "sparkle":
        return shimmer(1.4, 88) * 1.4
    if name == "whoosh":
        n = int(0.5 * SR)
        noise = rng.normal(0, 1, n)
        return lowpass(noise, 3000) * np.sin(np.linspace(0, np.pi, n)) ** 3 * 0.28
    if name == "coin":
        n = int(0.5 * SR)
        x = sum(sine(f, 0.5) * env_exp(n, 0.12 + i * 0.03) for i, f in enumerate((2637, 3520, 4186)))
        return x * 0.08
    if name == "shutter":
        n = int(0.16 * SR)
        noise = rng.normal(0, 1, n)
        e = env_exp(n, 0.01) + np.concatenate([np.zeros(int(0.07 * SR)), env_exp(n, 0.012)])[:n]
        return bandpass(noise, 1500, 6000) * e * 0.35
    raise ValueError("unknown sfx " + name)


# --- Mixing ------------------------------------------------------------------------------------
def write_wav(path, st):
    st = np.clip(st, -1, 1)
    data = (st * 32767).astype(np.int16)
    with wave.open(path, "w") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def read_wav(path):
    with wave.open(path) as w:
        n = w.getnframes()
        x = np.frombuffer(w.readframes(n), dtype=np.int16).astype(np.float64) / 32768
        return x.reshape(-1, w.getnchannels())


def mix_track(bed, cues, total, music_gain=0.55):
    n = int(total * SR)
    out = np.zeros((n, 2))
    b = bed[:n]
    out[: len(b)] += b * music_gain
    for i, c in enumerate(cues):
        x = sfx(c["sfx"], seed=i + 1)
        place(out, pan(x, c.get("pan", 0.0)), c["t"], c.get("gain", 1.0))
    return out


def mux(video, wav, out):
    """Puts the mix under the video, at YouTube's -14 LUFS, as AAC."""
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-i", video, "-i", wav,
        "-filter:a", "loudnorm=I=-14:TP=-1.5:LRA=11", "-ar", "48000",
        "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", out,
    ], check=True)


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "bed":
        kind, total, out = sys.argv[2], float(sys.argv[3]), sys.argv[4]
        # "promo:1:12" = one bar of intro, the ending on bar 12 (bars of 60/112*4 s).
        parts = kind.split(":")
        if parts[0] == "promo":
            args = [int(x) for x in parts[1:]]
            write_wav(out, bed_promo(total, *args))
        else:
            write_wav(out, bed_calm(total))
    elif cmd == "mix":
        video, cues_path, bed_path, out = sys.argv[2:6]
        meta = json.load(open(cues_path))
        bed = read_wav(bed_path)
        track_ = mix_track(bed, meta["cues"], meta["duration"], meta.get("musicGain", 0.55))
        tmp = out + ".wav"
        write_wav(tmp, track_ / max(1e-9, np.max(np.abs(track_))) * 0.9)
        mux(video, tmp, out)
        os.remove(tmp)
    elif cmd == "sfx":
        name, out = sys.argv[2], sys.argv[3]
        x = sfx(name)
        write_wav(out, np.stack([x, x], axis=1))
