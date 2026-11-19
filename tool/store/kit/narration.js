// The narrator's clips for a video, as vo.py made them. A narrated scene reports the lines it
// wants read (scene.voiceLines: { key, text }); render.mjs has vo.py speak them and opens the
// scene again with the manifest as ?vo=/build/vo/<name>.json. Each line is looked up by its key
// and must still have the same words: one that has changed since is an error, not a mismatch.
export async function loadNarration(url) {
  if (!url) return null;
  const res = await fetch(url, { cache: 'no-store' });
  if (!res.ok) throw new Error(`narration manifest missing: ${url}`);
  const manifest = await res.json();
  const byKey = new Map(manifest.lines.map((l) => [l.key, l]));
  return {
    narrator: `${manifest.engine}, ${manifest.voice}, speed ${manifest.speed}`,
    // { text, say, wav, dur, words: [[word, start, end], ...] }, times from the clip's start.
    line(key, text) {
      const l = byKey.get(key);
      if (!l || l.text !== text) throw new Error(`narration for ${key} is missing or out of date ("${text}"); render.mjs runs vo.py first`);
      return l;
    },
  };
}
