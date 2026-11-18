#!/usr/bin/env python3
"""The Quick slider's step sounds, made from nothing but code, so there is nothing to license.

Each one is a handful of damped sine waves, a gliding tone or a burst of filtered noise, written as
16-bit mono WAV to app/src/main/res/raw/step_<id>.wav. The ids are the ones StepSoundLibrary offers
in the app. The first of those, "tick", is the app's original res/raw/slider_tick.wav: it is not
made here, but every sound made here is levelled to it, so switching between them changes the
character of the step and not how loud it is.

    python3 tool/sounds/step_sounds.py            # writes every sound
    python3 tool/sounds/step_sounds.py pop drop   # writes just these

Needs numpy only.
"""
import os
import sys
import wave

import numpy as np

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "raw")
TICK_WAV = os.path.join(RAW, "slider_tick.wav")


# --- Building blocks ---------------------------------------------------------------------------
def t_axis(dur):
    return np.arange(int(round(dur * SR))) / SR


def env(dur, attack, tau):
    """A smooth rise over `attack` seconds, then an exponential fall with time constant `tau`."""
    t = t_axis(dur)
    rise = np.clip(t / attack, 0.0, 1.0) if attack > 0 else np.ones_like(t)
    rise = np.sin(rise * np.pi / 2) ** 2
    return rise * np.exp(-np.maximum(t - attack, 0.0) / tau)


def damped(freq, tau, dur, amp=1.0, phase=0.0, attack=0.0002):
    """One mode of something struck: a sine at `freq` dying away with time constant `tau`."""
    t = t_axis(dur)
    return amp * np.sin(2 * np.pi * freq * t + phase) * env(dur, attack, tau)


def glide(f_start, f_end, tau_f, dur):
    """The phase of a tone that starts at f_start and settles toward f_end, time constant tau_f."""
    t = t_axis(dur)
    f = f_end + (f_start - f_end) * np.exp(-t / tau_f)
    return 2 * np.pi * np.cumsum(f) / SR


def noise(dur, seed):
    """White noise from a fixed seed, so the files come out the same on every run."""
    return np.random.default_rng(seed).standard_normal(int(round(dur * SR)))


def biquad(x, kind, f0, q=0.707):
    """An RBJ-cookbook biquad: "lp", "hp", or "bp" (constant 0 dB peak gain)."""
    w0 = 2 * np.pi * f0 / SR
    cw, sw = np.cos(w0), np.sin(w0)
    alpha = sw / (2 * q)
    if kind == "lp":
        b = [(1 - cw) / 2, 1 - cw, (1 - cw) / 2]
    elif kind == "hp":
        b = [(1 + cw) / 2, -(1 + cw), (1 + cw) / 2]
    elif kind == "bp":
        b = [alpha, 0.0, -alpha]
    else:
        raise ValueError(kind)
    a = [1 + alpha, -2 * cw, 1 - alpha]
    b0, b1, b2 = (v / a[0] for v in b)
    a1, a2 = a[1] / a[0], a[2] / a[0]
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for n, xn in enumerate(x):
        yn = b0 * xn + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, xn
        y2, y1 = y1, yn
        y[n] = yn
    return y


def mix(*parts):
    n = max(len(p) for p in parts)
    return sum(np.pad(p, (0, n - len(p))) for p in parts)


def later(x, seconds):
    return np.concatenate([np.zeros(int(round(seconds * SR))), x])


# --- The sounds --------------------------------------------------------------------------------
def tap():
    """A fingertip on glass: soft, short, mostly midrange."""
    d = 0.035
    body = biquad(noise(d, 1), "lp", 2500) * env(d, 0.0003, 0.0025) * 0.6
    return mix(body, damped(1300, 0.003, d, 0.5), damped(650, 0.006, d, 0.45, 0.4))


def dial():
    """The detent of a rotary dial: a tiny, crisp metallic click."""
    d = 0.03
    click = biquad(noise(0.004, 2), "hp", 3000) * env(0.004, 0.0002, 0.0008) * 0.6
    return mix(
        click,
        damped(2900, 0.0025, d, 1.0),
        damped(4700, 0.0018, d, 0.6, 1.0),
        damped(7300, 0.0012, d, 0.4, 2.0),
    )


def keys():
    """A mechanical keyboard switch: the click of the switch, then the key bottoming out."""
    click = mix(
        biquad(noise(0.006, 3), "bp", 5000, 1.2) * env(0.006, 0.0002, 0.0012) * 0.9,
        damped(4200, 0.0015, 0.01, 0.5),
    )
    thock = mix(
        damped(420, 0.010, 0.05, 0.9),
        damped(980, 0.005, 0.05, 0.4, 0.8),
        biquad(noise(0.05, 4), "bp", 1200, 0.9) * env(0.05, 0.0003, 0.004) * 0.6,
    )
    return mix(click, later(thock, 0.007))


def wood():
    """A wood block: a hollow knock with a quick, bright decay."""
    d = 0.09
    knock = biquad(noise(0.004, 5), "bp", 2000, 0.8) * env(0.004, 0.0002, 0.001) * 0.4
    return mix(
        knock,
        damped(1010, 0.022, d, 1.0),
        damped(2440, 0.009, d, 0.45, 0.5),
        damped(3790, 0.005, d, 0.25, 1.1),
        damped(5200, 0.003, d, 0.12, 2.3),
    )


def pop():
    """Bubble wrap: a quick upward pop with a snap on its front."""
    d = 0.07
    body = np.sin(glide(380, 1100, 0.006, d)) * env(d, 0.0006, 0.014)
    snap = biquad(noise(0.003, 6), "bp", 3500, 0.9) * env(0.003, 0.0001, 0.0008) * 0.35
    return mix(body, snap)


def drop():
    """A water drop: the rising bloop of a bubble closing under the surface."""
    d = 0.11
    ph = glide(650, 1950, 0.018, d)
    return np.sin(ph) * env(d, 0.001, 0.022) + 0.15 * np.sin(2 * ph) * env(d, 0.001, 0.010)


def bubble():
    """A bigger, rounder bubble, lower and slower than the drop."""
    d = 0.16
    ph = glide(260, 780, 0.03, d)
    return np.sin(ph) * env(d, 0.002, 0.035) + 0.2 * np.sin(2 * ph) * env(d, 0.002, 0.02)


def snap():
    """A finger snap: a bright crack of noise with a little body under it."""
    d = 0.045
    crack = biquad(noise(d, 7), "bp", 2200, 1.5) * env(d, 0.0003, 0.0035) * 1.4
    return mix(crack, damped(1850, 0.004, d, 0.4), damped(180, 0.006, d, 0.2))


def thud():
    """A soft, low thud, the sound of a haptic tap; pitched high enough for a phone's speaker."""
    d = 0.09
    ph = glide(350, 210, 0.010, d)
    body = (np.sin(ph) + 0.5 * np.sin(2 * ph)) * env(d, 0.001, 0.018)
    soft = biquad(noise(0.01, 8), "lp", 1200) * env(0.01, 0.0005, 0.002) * 0.2
    return mix(body, soft)


def blip():
    """A clean electronic blip: a band-limited square wave that droops a little as it ends."""
    d = 0.07
    t = t_axis(d)
    f = 1150 + (1174.66 - 1150) * np.exp(-t / 0.02)
    ph = 2 * np.pi * np.cumsum(f) / SR
    square = sum(np.sin(k * ph) / k for k in range(1, 16, 2) if k * 1175 < SR / 2 - 2000) * 4 / np.pi
    return biquad(square * env(d, 0.001, 0.03), "lp", 6000) * 0.6


def marimba():
    """One marimba note, E5: a warm wooden bar with its two-octave overtone."""
    d = 0.38
    f0 = 659.25
    mallet = biquad(noise(0.003, 9), "lp", 3000) * env(0.003, 0.0002, 0.0007) * 0.15
    return mix(
        mallet,
        damped(f0, 0.18, d, 1.0, attack=0.0015),
        damped(f0 * 3.93, 0.045, d, 0.32, 0.6, attack=0.0008),
        damped(f0 * 9.2, 0.012, d, 0.1, 1.4),
    )


def glass():
    """A glass tapped with a spoon: a high ring with a slow shimmer in it."""
    d = 0.42
    f0 = 2093.0
    ting = biquad(noise(0.002, 10), "hp", 4000) * env(0.002, 0.0001, 0.0005) * 0.2
    return mix(
        ting,
        damped(f0, 0.16, d, 0.6, attack=0.0008),
        damped(f0 + 6, 0.16, d, 0.4, 0.7, attack=0.0008),
        damped(f0 * 2.71, 0.06, d, 0.35, 1.3),
        damped(f0 * 5.12, 0.025, d, 0.15, 2.1),
    )


def chime():
    """A soft chime, E6, with a gentle overtone or two."""
    d = 0.6
    f0 = 1318.5
    t = t_axis(d)
    return (
        np.sin(2 * np.pi * f0 * t) * env(d, 0.002, 0.3)
        + 0.25 * np.sin(2 * np.pi * 2 * f0 * t) * env(d, 0.002, 0.12)
        + 0.12 * np.sin(2 * np.pi * 3.01 * f0 * t) * env(d, 0.002, 0.06)
    )


# id -> (maker, trim in dB after levelling). Long, ringing sounds read as louder than a click of the
# same short-term level, so they sit a little lower.
SOUNDS = {
    "tap": (tap, 0.0),
    "dial": (dial, 0.0),
    "keys": (keys, 0.0),
    "wood": (wood, 0.0),
    "pop": (pop, 0.0),
    "drop": (drop, -1.0),
    "bubble": (bubble, -1.0),
    "snap": (snap, 0.0),
    "thud": (thud, 0.0),
    "blip": (blip, -2.0),
    "marimba": (marimba, -3.0),
    "glass": (glass, -4.0),
    "chime": (chime, -4.0),
}

PEAK_LIMIT = 0.93


# --- Levelling and writing -----------------------------------------------------------------------
def loudness(x):
    """The loudest 30 ms of the sound after a 300 Hz high-pass: about what a phone speaker gives."""
    y = biquad(x, "hp", 300)
    win = int(0.03 * SR)
    if len(y) <= win:
        return float(np.sqrt(np.mean(y ** 2)))
    return float(np.sqrt(np.convolve(y ** 2, np.ones(win) / win, mode="valid").max()))


def read_wav(path):
    with wave.open(path, "rb") as w:
        assert w.getsampwidth() == 2 and w.getnchannels() == 1
        data = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2")
        return data.astype(np.float64) / 32768.0, w.getframerate()


def finish(x):
    """A tiny fade at each end, so no sound starts or stops on a click of its own."""
    x = x.copy()
    n_in = max(1, int(0.0002 * SR))
    n_out = max(1, min(int(0.006 * SR), len(x) // 10))
    x[:n_in] *= np.sin(np.linspace(0, np.pi / 2, n_in)) ** 2
    x[-n_out:] *= np.cos(np.linspace(0, np.pi / 2, n_out)) ** 2
    return x


def write_wav(path, x):
    pcm = np.clip(np.round(x * 32767.0), -32768, 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main(names):
    tick, tick_sr = read_wav(TICK_WAV)
    assert tick_sr == SR, tick_sr
    target = loudness(tick)
    print(f"tick: {len(tick) / SR * 1000:.0f} ms, peak {20 * np.log10(np.abs(tick).max()):.1f} dBFS")
    for name in names:
        make, trim_db = SOUNDS[name]
        x = make()
        assert np.all(np.isfinite(x)), name
        x = x * (target / loudness(x)) * 10 ** (trim_db / 20)
        peak = np.abs(x).max()
        if peak > PEAK_LIMIT:
            x *= PEAK_LIMIT / peak
        x = finish(x)
        path = os.path.join(RAW, f"step_{name}.wav")
        write_wav(path, x)
        level = 20 * np.log10(loudness(x) / target)
        print(
            f"{name:8s} {len(x) / SR * 1000:4.0f} ms  peak {20 * np.log10(np.abs(x).max()):5.1f} dBFS"
            f"  level {level:+5.1f} dB vs tick  -> {os.path.relpath(path)}"
        )


if __name__ == "__main__":
    wanted = sys.argv[1:] or list(SOUNDS)
    unknown = [n for n in wanted if n not in SOUNDS]
    if unknown:
        sys.exit(f"unknown: {', '.join(unknown)}; known: {', '.join(SOUNDS)}")
    main(wanted)
