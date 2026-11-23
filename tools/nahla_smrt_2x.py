"""Náhlá smrt: první úder zvonu z původního nahla_smrt.wav zahraný 2×, oba nechané doznít.
Původní mix (Church Bells „3 Different“): první zvon udeří v 0,343 s, v 0,93 s nastoupí druhý zvon (F5).
Dozvuk prvního zvonu se zachová tak, že od 0,90 s se z nahrávky vyfiltrují tóny druhého zvonu.
python nahla_smrt_2x.py <původní.wav> <výstup.wav> [rozestup_s]"""
import sys, numpy as np
from scipy.io import wavfile
from scipy.signal import iirnotch, filtfilt
import pyloudnorm as pyln

sr, x = wavfile.read(sys.argv[1]); x = x.astype(float) / 32768
gap = float(sys.argv[3]) if len(sys.argv) > 3 else 0.75
# tóny druhého zvonu (F5 702 Hz + jeho partiály a spodní tón), které první zvon nemá
clean = x.copy()
for f, q in [(351, 25), (702, 20), (1051, 25), (1404, 25), (1447, 25), (1880, 25), (2338, 25)]:
    b, a = iirnotch(f, q, sr)
    clean = filtfilt(b, a, clean, axis=0)
t = np.arange(len(x)) / sr
w = np.clip((t - 0.88) / 0.05, 0, 1)[:, None]          # přechod do vyfiltrované verze 0,88–0,93 s
mixed = (1 - w) * x + w * clean

seg = mixed[int(0.32 * sr):].copy()                    # od nádechu před úderem (0,343 s) do konce
fi = int(0.002 * sr); seg[:fi] *= np.linspace(0, 1, fi)[:, None]
n2 = int(gap * sr)
out = np.zeros((n2 + len(seg), 2))
out[:len(seg)] += seg
out[n2:] += seg
fo = int(0.35 * sr)                                    # delší přirozené doznění
out[-fo:] *= (0.5 * (1 + np.cos(np.linspace(0, np.pi, fo))))[:, None]

m = pyln.Meter(sr); win = int(0.4 * sr)
loud = lambda z: max(m.integrated_loudness(z[i:i+win]) for i in range(0, len(z) - win, int(0.05 * sr)))
out *= 10 ** ((-12.5 - loud(out)) / 20)
c = 10 ** (-1 / 20); out = c * np.tanh(out / c)
print(f'{len(out)/sr:.2f} s, údery v 0,02 a {gap+0.02:.2f} s, {loud(out):.1f} LUFS, špička {20*np.log10(np.abs(out).max()):.1f} dBFS')
wavfile.write(sys.argv[2], sr, (out * 32767).astype(np.int16))
