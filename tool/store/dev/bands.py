# python3 dev/bands.py <file.wav> — level per octave band, to check a music bed by numbers
import sys, numpy as np, wave
def read(p):
    w=wave.open(p); n=w.getnframes(); x=np.frombuffer(w.readframes(n),dtype=np.int16).astype(float)/32768
    return x.reshape(-1,w.getnchannels()).mean(1), w.getframerate()
x,sr=read(sys.argv[1])
a,b=float(sys.argv[2]) if len(sys.argv)>2 else 0, float(sys.argv[3]) if len(sys.argv)>3 else len(x)/sr
x=x[int(a*sr):int(b*sr)]
F=np.abs(np.fft.rfft(x*np.hanning(len(x))))**2; f=np.fft.rfftfreq(len(x),1/sr)
cs=[31.5,63,125,250,500,1000,2000,4000,8000,16000]
ref=None; out=[]
for c in cs:
    m=(f>=c/np.sqrt(2))&(f<c*np.sqrt(2)); e=10*np.log10(F[m].sum()+1e-20)
    out.append(e)
ref=out[5]
# pink reference: equal energy per octave => 0 dB everywhere relative to 1k
print(' '.join(f'{c:>6.0f}' for c in cs)); print(' '.join(f'{e-ref:>6.1f}' for e in out))
