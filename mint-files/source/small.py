# Small sizes drawn on their own grid: the same dark back, white pages, smoked glass front
# with its stepped top edge and a lit rim, simplified so each part survives at 16-32 px.
def small(n):
    k = n / 24
    s = lambda v: round(v * k * 2) / 2  # snap to half pixels
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{n}" height="{n}" viewBox="0 0 {n} {n}">
<defs>
 <linearGradient id="b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a3a3e"/><stop offset=".25" stop-color="#1c1c1f"/><stop offset="1" stop-color="#121214"/></linearGradient>
 <linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#b4b4ba"/><stop offset=".45" stop-color="#7c7c82"/><stop offset="1" stop-color="#2e2e32"/></linearGradient>
 <linearGradient id="p" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#e6e6ea"/></linearGradient>
</defs>
<rect x="{s(1.5)}" y="{s(4.5)}" width="{s(21)}" height="{s(16.5)}" rx="{s(3)}" fill="url(#b)" stroke="#fff" stroke-opacity=".16" stroke-width="{max(0.5, round(0.5*k*2)/2)}"/>
<rect x="{s(4)}" y="{s(3)}" width="{s(9.5)}" height="{s(12)}" rx="{s(1.5)}" fill="url(#p)" transform="rotate(-6 {s(8.75)} {s(9)})"/>
<rect x="{s(12)}" y="{s(4)}" width="{s(8)}" height="{s(11)}" rx="{s(1.5)}" fill="#dedee2" transform="rotate(5 {s(16)} {s(9.5)})"/>
{'' if n < 24 else f'<rect x="{s(5.5)}" y="{s(6)}" width="{s(5)}" height="{max(1,s(0.75))}" rx="{s(0.4)}" fill="#bdbdc2" transform="rotate(-6 {s(8.75)} {s(9)})"/>'}
<path d="M{s(1)} {s(12.5)}Q{s(1)} {s(11)} {s(2.5)} {s(11)}H{s(9.5)}L{s(11.5)} {s(9.5)}H{s(21.5)}Q{s(23)} {s(9.5)} {s(23)} {s(11)}V{s(19.5)}Q{s(23)} {s(21.5)} {s(21)} {s(21.5)}H{s(3)}Q{s(1)} {s(21.5)} {s(1)} {s(19.5)}Z" fill="url(#g)" fill-opacity=".93"/>
<path d="M{s(1)} {s(12.5)}Q{s(1)} {s(11)} {s(2.5)} {s(11)}H{s(9.5)}L{s(11.5)} {s(9.5)}H{s(21.5)}Q{s(23)} {s(9.5)} {s(23)} {s(11)}" fill="none" stroke="#fff" stroke-opacity=".75" stroke-width="{max(0.75, round(1*k*2)/2)}"/>
</svg>'''
for n in (16, 22, 24, 32):
    open(f'small-{n}.svg', 'w').write(small(n))
