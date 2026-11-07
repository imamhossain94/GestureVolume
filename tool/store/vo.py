#!/usr/bin/env python3
"""The narrator: one Kokoro-82M clip per line of a video, with the time of every word.

Kokoro-82M (Apache-2.0) runs locally through kokoro-onnx, in the voice af_bella at speed 1.0:
the narrator of the Sonary tutorials on the same YouTube channel, so the channel has one voice.

Setup, once per machine. The model (about 350 MB) stays out of this repo:

    python3.12 -m venv ~/.venvs/kokoro
    ~/.venvs/kokoro/bin/pip install "kokoro-onnx>=0.6" soundfile numpy
    # kokoro-v1.0.onnx and voices-v1.0.bin, from github.com/thewh1teagle/kokoro-onnx releases,
    # into one folder; then point the renderer at both:
    export VO_PYTHON=~/.venvs/kokoro/bin/python VO_MODEL_DIR=/path/to/that/folder

Both default to the copies Sonary's video tools already keep on this machine
(~/FlutterProjects/audio_ai/tool/store/video/.cache). kokoro-onnx 0.6 is the first with
create_timed(), which reports when each phoneme is spoken; the word times come from there.

render.mjs runs this before it renders a narrated video: it asks the scene for its lines, and
this writes the clips and a manifest (each line's text, clip, length and word times) that the
scene reads to time its steps. Clips are cached in build/vo/ by what is said and how.

    $VO_PYTHON vo.py say "Any sentence to try."         # one line: its clip, length, word times
    $VO_PYTHON vo.py phonemes "Gesture Volume, the Deck"  # what the narrator will say, in IPA
    $VO_PYTHON vo.py lines in.json out.json               # a video's lines (render.mjs does this)
"""

from __future__ import annotations

import difflib
import hashlib
import json
import os
import re
import sys
import tempfile
from functools import lru_cache
from pathlib import Path

import numpy as np
import soundfile as sf

HERE = Path(__file__).resolve().parent
SONARY = Path.home() / "FlutterProjects/audio_ai/tool/store/video/.cache"
MODEL = Path(os.environ.get("VO_MODEL_DIR", SONARY / "models" / "kokoro"))
VO_DIR = HERE / "build" / "vo"
VOICE = "af_bella"
SPEED = 1.0
SR = 24000
# Silence topped up after a full stop and a comma (kokoro-onnx's own defaults are 0.25 and 0.1):
# the step's title is read as a sentence of its own, and gets a beat before the rest.
SENTENCE_PAUSE = 0.32
CLAUSE_PAUSE = 0.12

# What a word is spoken as, where espeak reads it badly; the screen keeps the word as it is.
# Each entry maps a word (punctuation aside) to the words said in its place.
SAY: dict[str, str] = {}

# Phoneme fixes after espeak, for a word it spells right but says wrong (none needed yet: every
# line was checked with `vo.py phonemes`; Gesture Volume, Deck, Dock, Quick slider and 100% read
# well as they are).
PRONOUNCE: dict[str, str] = {}

VERSION = 3          # bump to re-synthesize every clip after a change to how they are made


@lru_cache(maxsize=1)
def _kokoro():
    from kokoro_onnx import Kokoro

    onnx, voices = MODEL / "kokoro-v1.0.onnx", MODEL / "voices-v1.0.bin"
    if not onnx.exists() or not voices.exists():
        raise SystemExit(f"vo.py: the Kokoro model is not in {MODEL}; set VO_MODEL_DIR (see the top of vo.py)")
    return Kokoro(str(onnx), str(voices))


@lru_cache(maxsize=4096)
def phonemes(text: str) -> str:
    ph = _kokoro().tokenizer.phonemize(text, "en-us")
    for a, b in PRONOUNCE.items():
        ph = ph.replace(a, b)
    return " ".join(ph.split())


def spoken(text: str) -> tuple[str, list[str]]:
    """The text as said, and for each word on screen the words said for it."""
    words = text.split()
    said = []
    for w in words:
        core = w.strip(".,;:!?’'\"()")
        said.append(w.replace(core, SAY[core]) if core in SAY else w)
    return " ".join(said), said


def _key(text: str) -> str:
    raw = json.dumps([text, VOICE, SPEED, SENTENCE_PAUSE, CLAUSE_PAUSE, SAY, PRONOUNCE, VERSION], ensure_ascii=False)
    return hashlib.sha1(raw.encode()).hexdigest()[:16]


def _write_atomic(path: Path, write) -> None:
    """Writes through a temporary file, so renders running side by side never read half a clip."""
    fd, tmp = tempfile.mkstemp(dir=path.parent, suffix=path.suffix)
    os.close(fd)
    try:
        write(tmp)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.remove(tmp)


def synth(text: str) -> dict:
    """One line: {"text", "say", "wav", "dur", "words": [[word, start, end], ...]}, times in seconds
    from the start of the clip, one entry per word of `text` as it appears on screen."""
    VO_DIR.mkdir(parents=True, exist_ok=True)
    key = _key(text)
    wav, meta = VO_DIR / f"{key}.wav", VO_DIR / f"{key}.json"
    if wav.exists() and meta.exists():
        return json.loads(meta.read_text())

    say, said = spoken(text)
    ph = phonemes(say)
    audio, sr, timing = _kokoro().create_timed(ph, voice=VOICE, speed=SPEED, is_phonemes=True,
                                               sentence_pause=SENTENCE_PAUSE, clause_pause=CLAUSE_PAUSE)
    audio = np.asarray(audio, np.float32)
    # Trim to the speech, keeping 60 ms of air on each side.
    idx = np.where(np.abs(audio) > 0.004)[0]
    head = max(0, idx[0] - int(0.06 * sr)) if len(idx) else 0
    tail = min(len(audio), idx[-1] + int(0.06 * sr)) if len(idx) else len(audio)
    audio = audio[head:tail]
    dur = len(audio) / sr
    words = word_times(text.split(), said, timing, head / sr, dur)
    _write_atomic(wav, lambda p: sf.write(p, audio, sr, subtype="FLOAT", format="WAV"))
    info = {"text": text, "say": say, "voice": VOICE, "speed": SPEED, "wav": str(wav.relative_to(HERE)),
            "dur": round(dur, 3), "words": words}
    _write_atomic(meta, lambda p: Path(p).write_text(json.dumps(info, ensure_ascii=False, indent=1)))
    return info


PHONE = re.compile(r"[^\sˈˌː.,;:!?…—–\-'\"()“”]")


def word_times(words: list[str], said: list[str], timing, shift: float, dur: float) -> list:
    """When each word on screen is said.

    Kokoro times phonemes, and espeak joins small words in its output ("from the" is one
    group, fɹʌmðə), so the groups seldom line up with the words. Each word is phonemized on its
    own and the run of them matched against the line's phonemes; a word's time runs from its
    first matched phoneme to its last. A word nothing matched takes the gap its neighbours leave.
    """
    line = [(t.phoneme, t.start - shift, t.end - shift) for t in timing or [] if PHONE.match(t.phoneme)]
    owner: list[int] = []                       # for each phoneme of the words said alone, its word
    hyp = ""
    for i, w in enumerate(said):
        core = re.sub(r"^[^\w%&]+|[^\w%&]+$", "", w)
        ph = "".join(c for c in phonemes(core) if PHONE.match(c)) if core else ""
        hyp += ph
        owner += [i] * len(ph)
    ref = "".join(c for c, _, _ in line)
    spans: list[list[float] | None] = [None] * len(words)
    sm = difflib.SequenceMatcher(None, ref, hyp, autojunk=False)
    for a, b, n in sm.get_matching_blocks():
        for k in range(n):
            i = owner[b + k]
            _, t0, t1 = line[a + k]
            s = spans[i]
            spans[i] = [t0, t1] if s is None else [min(s[0], t0), max(s[1], t1)]
    # Words with no match (or no timings at all) share out the time between their neighbours.
    out, i = [], 0
    while i < len(words):
        if spans[i] is not None:
            out.append([words[i], round(max(0.0, spans[i][0]), 3), round(min(dur, spans[i][1]), 3)])
            i += 1
            continue
        j = i
        while j < len(words) and spans[j] is None:
            j += 1
        a = out[-1][2] if out else 0.0
        b = spans[j][0] if j < len(words) else dur
        total = sum(len(w) + 1 for w in words[i:j])
        acc = 0
        for w in words[i:j]:
            x0 = a + (b - a) * acc / total
            acc += len(w) + 1
            out.append([w, round(x0, 3), round(a + (b - a) * acc / total, 3)])
        i = j
    # Keep them in order: a word never starts before the one it follows.
    for k in range(1, len(out)):
        if out[k][1] < out[k - 1][1]:
            out[k][1] = out[k - 1][1]
        out[k][2] = max(out[k][2], out[k][1])
    return out


def lines(src: str, dst: str) -> None:
    """[{key, text}, ...] -> {"voice", "speed", "lines": [{key, text, say, wav, dur, words}, ...]}"""
    todo = json.loads(Path(src).read_text())
    out = []
    for ln in todo:
        info = synth(ln["text"])
        out.append({"key": ln["key"], **{k: info[k] for k in ("text", "say", "wav", "dur", "words")}})
        print(f"  {ln['key']:>8}  {info['dur']:5.2f} s  {ln['text'][:70]}", file=sys.stderr)
    manifest = {"engine": "Kokoro-82M (kokoro-onnx)", "voice": VOICE, "speed": SPEED, "lines": out}
    Path(dst).parent.mkdir(parents=True, exist_ok=True)
    _write_atomic(Path(dst), lambda p: Path(p).write_text(json.dumps(manifest, ensure_ascii=False, indent=1)))


if __name__ == "__main__":
    cmd, rest = (sys.argv[1], sys.argv[2:]) if len(sys.argv) > 1 else ("say", [])
    if cmd == "say":
        info = synth(" ".join(rest) or "Meet Gesture Volume: volume and brightness on the edge of your screen.")
        print(json.dumps(info, ensure_ascii=False, indent=1))
    elif cmd == "phonemes":
        text = " ".join(rest)
        print(phonemes(spoken(text)[0]))
    elif cmd == "lines":
        lines(rest[0], rest[1])
    else:
        raise SystemExit(__doc__)
