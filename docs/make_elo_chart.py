"""Regenerates docs/elo_progression.svg (stdlib only: python docs/make_elo_chart.py).

Each point is the cumulative Elo of an engine relative to IterativeDeepeningBot, built by summing the
measured head-to-head gains of consecutive versions (see README, "Results"). The sum assumes Elo is
additive across matches, so treat it as a rough picture, not a rated strength.
"""
from math import sqrt

# (label, elo gain over previous version, 95% CI half-width of that gain)
STEPS = [
    ("Iterative\nDeepening", 0.0, 0.0),
    ("Killers+LMR\n+TT", 322.7, 45.4),
    ("Null move\n(NMPBot)", 91.0, 19.9),
    ("FirstEngine\nhistory+asp.", 31.4, 19.2),
    ("v2.0\nbitboards", 66.8, 16.7),
    ("v2.1\nmobility", 66.1, 12.8),
    ("v2.5\nPVS+pawns", 40.8, 14.5),
    ("v2.6\nTexel", 61.1, 14.5),
    ("v2.7\nSEE", 105.3, 15.4),
    ("v2.8\nking safety", 14.9, 13.5),
    ("v2.9\nRFP", 75.2, 14.1),
    ("v3.0\nQS evasions", 29.6, 13.6),
]

cum, total_sq = [], 0.0
running = 0.0
for _, gain, ci in STEPS:
    running += gain
    total_sq += ci * ci
    cum.append(running)
total, total_ci = cum[-1], sqrt(total_sq)

W, H = 960, 470
L, R, T, B = 70, 20, 70, 90
pw, ph = W - L - R, H - T - B
ymax = 900

out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" '
       f'font-family="Segoe UI, Helvetica, Arial, sans-serif">',
       f'<rect width="{W}" height="{H}" rx="10" fill="#ffffff" stroke="#d0d7de"/>',
       f'<text x="{L}" y="30" font-size="18" font-weight="600" fill="#1f2328">Cumulative Elo vs IterativeDeepeningBot</text>',
       f'<text x="{L}" y="50" font-size="12" fill="#57606a">Sum of measured head-to-head gains between consecutive versions. '
       f'Total about +{total:.0f} Elo (rough 95% interval +/-{total_ci:.0f}).</text>']
for v in range(0, ymax + 1, 150):
    y = T + ph - v / ymax * ph
    out.append(f'<line x1="{L}" y1="{y:.1f}" x2="{W - R}" y2="{y:.1f}" stroke="#eaeef2"/>')
    out.append(f'<text x="{L - 8}" y="{y + 4:.1f}" font-size="11" text-anchor="end" fill="#57606a">{v}</text>')
out.append(f'<text x="16" y="{T + ph / 2:.0f}" font-size="11" fill="#57606a" text-anchor="middle" '
           f'transform="rotate(-90 16 {T + ph / 2:.0f})">Elo</text>')
slot = pw / len(STEPS)
pts = []
for i, c in enumerate(cum):
    pts.append((L + i * slot + slot / 2, T + ph - c / ymax * ph))
out.append(f'<polyline points="{" ".join(f"{x:.1f},{y:.1f}" for x, y in pts)}" fill="none" stroke="#1a7f78" '
           f'stroke-width="3" stroke-linejoin="round" stroke-linecap="round"/>')
for i, ((label, gain, _), c, (x, y)) in enumerate(zip(STEPS, cum, pts)):
    last = i == len(STEPS) - 1
    out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="5" fill="{"#0b4f4a" if last else "#ffffff"}" stroke="#1a7f78" stroke-width="2.5"/>')
    out.append(f'<text x="{x:.1f}" y="{y - 12:.1f}" font-size="12" font-weight="600" text-anchor="middle" fill="#1f2328">{c:.0f}</text>')
    if gain:
        out.append(f'<text x="{x + 9:.1f}" y="{y + 17:.1f}" font-size="10" fill="#57606a">+{gain:.0f}</text>')
    for k, line in enumerate(label.split(chr(10))):
        out.append(f'<text x="{x:.1f}" y="{T + ph + 18 + 14 * k}" font-size="11" text-anchor="middle" fill="#1f2328">{line}</text>')
out.append('</svg>')
open("docs/elo_progression.svg", "w", encoding="utf-8").write("\n".join(out) + "\n")
print(f"total +{total:.1f} +/- {total_ci:.1f}")
