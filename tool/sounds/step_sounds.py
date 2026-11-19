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


def sweep_bp(x, f0, q):
    """A band-pass whose centre follows f0, one frequency a sample: for whooshes and swishes."""
    y = np.zeros_like(x)
    f0 = np.broadcast_to(np.asarray(f0, dtype=float), x.shape)
    x1 = x2 = y1 = y2 = 0.0
    for n in range(len(x)):
        w0 = 2 * np.pi * f0[n] / SR
        alpha = np.sin(w0) / (2 * q)
        a0 = 1 + alpha
        b0 = alpha / a0
        b2 = -alpha / a0
        a1 = -2 * np.cos(w0) / a0
        a2 = (1 - alpha) / a0
        yn = b0 * x[n] + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, x[n]
        y2, y1 = y1, yn
        y[n] = yn
    return y


def voice(f0, formants):
    """Harmonics of a pitch that moves (f0, one a sample), shaped by formants [(centre, width, gain)]."""
    n = len(f0)
    out = np.zeros(n)
    for k in range(1, 30):
        fk = k * f0
        if fk.max() > SR / 2 - 2000:
            break
        ph = 2 * np.pi * np.cumsum(fk) / SR
        amp = np.zeros(n)
        for centre, width, gain in formants:
            amp += gain * np.exp(-((fk - centre) / width) ** 2)
        out += amp * np.sin(ph) / (k ** 0.3)
    return out


def twinkle():
    """Three little bell notes, rising: a sparkle."""
    parts = []
    for i, f in enumerate([1318.5, 1661.2, 1975.5]):
        tone = damped(f, 0.16, 0.36, 1.0, attack=0.002) + damped(f * 2.0, 0.07, 0.36, 0.25, attack=0.002) + damped(f * 3.01, 0.03, 0.36, 0.1)
        parts.append(later(tone, i * 0.065))
    return mix(*parts)


def firework():
    """A firework: the pop of the shell, and its stars crackling as they fall."""
    pop = mix(biquad(noise(0.02, 21), "lp", 3000) * env(0.02, 0.0005, 0.006) * 1.2, damped(110, 0.05, 0.2, 0.9), damped(220, 0.03, 0.2, 0.4))
    rng = np.random.default_rng(22)
    crackles = []
    for i in range(16):
        at = 0.12 + rng.random() * 0.55
        amp = 0.6 * (1 - (at - 0.12) / 0.6) + 0.1
        crackles.append(later(biquad(noise(0.006, 30 + i), "hp", 2500) * env(0.006, 0.0002, 0.0015) * amp, at))
    return mix(pop, *crackles)


def rumble():
    """A volcano's rumble, with a fizz of steam at its end."""
    d = 0.75
    t = t_axis(d)
    n = biquad(biquad(noise(d, 25), "lp", 260), "lp", 260) * 3.5
    growl = (np.sin(2 * np.pi * 58 * t) + 0.5 * np.sin(2 * np.pi * 116 * t + 0.3)) * 0.5
    fizz = biquad(noise(d, 26), "hp", 3500) * env(d, 0.25, 0.3) * 0.15
    x = (n * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * t)) + growl * 0.6) * env(d, 0.05, 0.3) + fizz
    return np.tanh(x * 1.8) / np.tanh(1.8)


def launch():
    """A rocket's launch: a thump, and a roar rising in pitch as it goes."""
    d = 0.8
    t = t_axis(d)
    thump = mix(damped(70, 0.06, 0.3, 0.8), biquad(noise(0.03, 28), "lp", 900) * env(0.03, 0.001, 0.01) * 0.6)
    rise = sweep_bp(noise(d, 29), 300 * (2500 / 300) ** (t / d), 1.2) * 2.4 * env(d, 0.15, 0.5) * np.clip((t - 0.04) / 0.1, 0, 1)
    return mix(thump, rise)


def swish():
    """A blade through the air, and the ring of its steel."""
    d = 0.3
    t = t_axis(d)
    air = sweep_bp(noise(d, 31), 3200 * (900 / 3200) ** np.clip(t / 0.22, 0, 1), 2.0) * 3.0 * env(d, 0.03, 0.07)
    ring = damped(3300, 0.12, d, 0.08, attack=0.02) + damped(4950, 0.06, d, 0.04, attack=0.02)
    return mix(air, later(ring, 0.04))


def crack():
    """Glass cracking: a sharp snap, its splinters, and the ping of the pane."""
    d = 0.32
    click = np.zeros(int(round(d * SR)))
    click[0], click[1] = 1.0, -0.6
    burst = biquad(noise(0.05, 32), "hp", 3000) * env(0.05, 0.0002, 0.012) * 0.9
    pings = mix(damped(4500, 0.07, d, 0.25), damped(6200, 0.05, d, 0.2, 1.0), damped(7700, 0.03, d, 0.15, 2.0))
    small = mix(
        later(biquad(noise(0.01, 33), "hp", 3500) * env(0.01, 0.0001, 0.002) * 0.5, 0.05),
        later(biquad(noise(0.01, 34), "hp", 3500) * env(0.01, 0.0001, 0.002) * 0.4, 0.09),
    )
    return mix(click * 0.8, burst, pings, small)


def exhale():
    """A long, soft breath out: vapour."""
    d = 0.7
    t = t_axis(d)
    n = sweep_bp(noise(d, 35), 1400 + 400 * np.sin(2 * np.pi * 1.3 * t), 0.6) * 2.0
    shape = np.clip(t / 0.09, 0, 1) ** 2 * np.exp(-np.maximum(t - 0.09, 0) / 0.28)
    return n * shape * (1 + 0.12 * np.sin(2 * np.pi * 11 * t))


def whoosh():
    """A gust going by: up in pitch and back down."""
    d = 0.6
    t = t_axis(d)
    k = t / d
    n = sweep_bp(noise(d, 36), 500 + 1100 * np.sin(np.pi * k), 1.4) * 3.0
    return n * np.sin(np.pi * np.clip(k * 1.15, 0, 1)) ** 2


def spray():
    """An aerosol: the click of the valve and a steady hiss."""
    d = 0.55
    t = t_axis(d)
    valve = biquad(noise(0.012, 37), "bp", 1800, 1.2) * env(0.012, 0.0003, 0.003) * 0.8
    hiss = biquad(biquad(noise(d, 38), "hp", 3200), "hp", 3200) * 1.2
    shape = np.clip(t / 0.03, 0, 1) * np.where(t < 0.42, 1.0, np.exp(-(t - 0.42) / 0.04))
    return mix(valve, hiss * shape * (1 + 0.08 * np.sin(2 * np.pi * 23 * t)))


def bubbles():
    """A few bubbles popping up through water, one after another."""
    parts = []
    for i, (at, f0, f1) in enumerate([(0, 420, 1250), (0.075, 520, 1500), (0.14, 360, 1100), (0.22, 600, 1700)]):
        d = 0.06
        parts.append(later(np.sin(glide(f0, f1, 0.012, d)) * env(d, 0.001, 0.014) * (1 - i * 0.12), at))
    return mix(*parts)


def smooth(k, points, values):
    """A curve through (points, values) with no corners: smoothstep between each pair."""
    k = np.clip(k, points[0], points[-1])
    out = np.zeros_like(k)
    for i in range(len(points) - 1):
        lo, hi = points[i], points[i + 1]
        inside = (k >= lo) & (k <= hi)
        u = (k[inside] - lo) / (hi - lo)
        u = u * u * (3 - 2 * u)
        out[inside] = values[i] + (values[i + 1] - values[i]) * u
    return out


def meow():
    """A kitten's meow, sweet and clear: "m-ee-ow".

    A voice rather than a buzz: the pitch climbs smoothly to its top a third of the way in and sinks
    back, a little vibrato arrives once the note has settled, and the mouth goes from a closed "m"
    through "ee" to an open "a" and rounds to "ow". The harmonics roll off steeply and the formants
    are narrow, which is what makes it ring clear rather than rasp, and a breath of air under it
    keeps it from sounding like a whistle.
    """
    d = 0.56
    t = t_axis(d)
    k = t / d
    vibrato = 1 + 0.012 * np.sin(2 * np.pi * 5.6 * t) * np.clip((t - 0.16) / 0.1, 0, 1)
    f0 = smooth(k, [0.0, 0.32, 0.78, 1.0], [640, 980, 700, 600]) * vibrato
    f1 = smooth(k, [0.0, 0.12, 0.3, 0.62, 1.0], [320, 520, 950, 820, 430])
    f2 = smooth(k, [0.0, 0.12, 0.3, 0.62, 1.0], [1700, 2350, 1750, 1250, 900])
    n = len(t)
    out = np.zeros(n)
    for h in range(1, 14):
        fk = h * f0
        if fk.max() > SR / 2 - 3000:
            break
        ph = 2 * np.pi * np.cumsum(fk) / SR
        amp = (np.exp(-((fk - f1) / 170) ** 2) + 0.55 * np.exp(-((fk - f2) / 240) ** 2)
               + 0.12 * np.exp(-((fk - 3300) / 420) ** 2))
        out += amp * np.sin(ph) / (h ** 0.9)
    # The "m": the mouth closed, softer and duller for the first few hundredths of a second.
    nasal = smooth(k, [0.0, 0.1, 0.16], [0.35, 0.8, 1.0])
    breath = biquad(noise(d, 71), "bp", 2600, 1.2) * 0.035
    shape = np.clip(t / 0.045, 0, 1) ** 1.5 * smooth(k, [0.0, 0.72, 1.0], [1.0, 0.85, 0.0])
    x = (out * nasal + breath) * shape
    return biquad(x, "lp", 6500)


def monkey():
    """A monkey's call: two quick hoots and a shriek."""
    parts = []
    for at, d, f_from, f_to, c1, c2 in [(0, 0.09, 680, 920, 380, 820), (0.13, 0.09, 720, 980, 380, 820), (0.26, 0.15, 980, 820, 900, 1450)]:
        t = t_axis(d)
        k = t / d
        f0 = f_from + (f_to - f_from) * np.sin(np.pi * 0.5 * k)
        x = voice(f0, [(c1, 160, 1.0), (c2, 240, 0.7)])
        parts.append(later(x * np.sin(np.pi * np.clip(k, 0, 1)) ** 0.7, at))
    return mix(*parts)


def ufo():
    """A flying saucer: a wavering tone, gliding up."""
    d = 0.65
    t = t_axis(d)
    f = (600 + 300 * t / d) + 120 * np.sin(2 * np.pi * 9 * t)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return (np.sin(ph) + 0.25 * np.sin(1.5 * ph)) * np.clip(t / 0.06, 0, 1) * np.clip((d - t) / 0.15, 0, 1)


def buzz():
    """A bee going by."""
    d = 0.42
    t = t_axis(d)
    f = 210 + 8 * np.sin(2 * np.pi * 5 * t)
    ph = 2 * np.pi * np.cumsum(f) / SR
    saw = sum(np.sin(k * ph) / k for k in range(1, 40) if k * 230 < SR / 2 - 2000)
    return biquad(biquad(saw, "hp", 300), "lp", 3000) * (0.75 + 0.25 * np.sin(2 * np.pi * 31 * t)) * env(d, 0.03, 0.2)


def splash():
    """A little splash, and the drops falling back."""
    body = biquad(noise(0.16, 39), "bp", 2500, 0.7) * env(0.16, 0.002, 0.04) * 1.5
    drops = [later(np.sin(glide(f0, f1, 0.012, 0.06)) * env(0.06, 0.001, 0.012) * 0.6, at)
             for at, f0, f1 in [(0.05, 700, 1900), (0.11, 900, 2300), (0.17, 600, 1700)]]
    return mix(body, *drops)


def cosmic():
    """Space: a soft, shimmering chord and a sprinkle of stars."""
    d = 0.75
    t = t_axis(d)
    pad = 0
    for f, a in [(1046.5, 1.0), (1568.0, 0.7), (2637.0, 0.35)]:
        pad = pad + a * (np.sin(2 * np.pi * f * t) + np.sin(2 * np.pi * (f + 2.5) * t + 1.0)) * 0.5
    shape = np.clip(t / 0.06, 0, 1) * np.exp(-np.maximum(t - 0.06, 0) / 0.3)
    sparkles = mix(*[later(damped(f, 0.05, 0.1, 0.25), at) for f, at in [(4186, 0.05), (5274, 0.16), (6272, 0.27)]])
    return mix(pad * shape, sparkles)


def ribbit():
    """A little frog's "rib-bit": two croaks, the second shorter and higher.

    Each croak is a quick train of pulses, as a frog's call is, through a throat that rings at about
    a kilohertz, swelling and closing; small and bright rather than a bullfrog's bellow.
    """
    parts = []
    for (dur, f_lo, f_hi, ring, at) in [(0.12, 105, 125, 1050, 0.0), (0.085, 135, 150, 1350, 0.17)]:
        t = t_axis(dur)
        k = t / dur
        f0 = f_lo + (f_hi - f_lo) * k
        x = voice(f0, [(ring, 260, 1.0), (ring * 2.1, 420, 0.45)])
        # The pulses: each opening of the throat a little burst.
        pulse = (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(f0) / SR)) ** 3
        shape = np.sin(np.pi * np.clip(k, 0, 1)) ** 0.7
        parts.append(later(x * (0.4 + 0.6 * pulse) * shape, at))
    return mix(*parts)


def zip_():
    """A line of silk shot out: a quick, rising zip, and a soft twang as it catches."""
    d = 0.2
    t = t_axis(d)
    k = t / d
    hiss = sweep_bp(noise(d, 83), 900 + 5200 * k ** 1.4, 2.2) * env(d, 0.01, 0.06) * 2.4
    twang = later(damped(880, 0.05, 0.12, 0.35) + damped(1760, 0.03, 0.12, 0.12, 0.7), 0.13)
    return mix(hiss, twang)


def peck():
    """A woodpecker's drumming: a rattle of knocks on a hollow trunk, quickest in the middle."""
    gaps = [0.0, 0.065, 0.118, 0.164, 0.207, 0.25, 0.296]
    parts = []
    for i, at in enumerate(gaps):
        d = 0.06
        knock = biquad(noise(0.003, 90 + i), "bp", 2300, 0.9) * env(0.003, 0.0001, 0.0008) * 0.5
        body = mix(
            knock,
            damped(760, 0.018, d, 1.0),
            damped(1530, 0.009, d, 0.45, 0.4),
            damped(2900, 0.004, d, 0.2, 1.3),
        )
        parts.append(later(body * (1.0 - 0.07 * i), at))
    return mix(*parts)


def boing():
    """A spring: boing."""
    d = 0.5
    t = t_axis(d)
    f = 260 + 140 * np.exp(-t / 0.25) * np.sin(2 * np.pi * 11 * t) + 60 * np.exp(-t / 0.1)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return (np.sin(ph) + 0.3 * np.sin(2 * ph)) * env(d, 0.003, 0.18)


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
    # The button animations' own sounds.
    "twinkle": (twinkle, -3.0),
    "firework": (firework, 0.0),
    "rumble": (rumble, -3.0),
    "launch": (launch, -3.0),
    "swish": (swish, -2.0),
    "crack": (crack, -1.0),
    "exhale": (exhale, -4.0),
    "whoosh": (whoosh, -3.0),
    "spray": (spray, -5.0),
    "bubbles": (bubbles, 0.0),
    "meow": (meow, -2.0),
    "monkey": (monkey, -2.0),
    "ufo": (ufo, -5.0),
    "buzz": (buzz, -4.0),
    "splash": (splash, -1.0),
    "ribbit": (ribbit, -2.0),
    "zip": (zip_, -3.0),
    "peck": (peck, -1.0),
    "cosmic": (cosmic, -5.0),
    "boing": (boing, -3.0),
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
