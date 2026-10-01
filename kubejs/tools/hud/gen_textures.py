#!/usr/bin/env python3
"""Regenerates the survival HUD textures in assets/winterheart/textures/gui/:
     wood_border.png   snowy spruce frame tiles (3 snow levels x 8 variants per edge)
     wood_stubs.png    planks poking out past the frame
     plank_bg.png      dark plank panel background

Edit SETTINGS below, run   python3 kubejs/tools/hud/gen_textures.py   (needs Pillow), then check the result with
python3 kubejs/tools/hud/preview.py. No game launch needed; restart the game afterwards to load the new PNGs.
VARIANTS must equal HUD_VARIANTS in startup_scripts/survival_hud_render.js (preview.py warns if they differ).
"""
import random
from pathlib import Path
from PIL import Image

SETTINGS = {
    'SEED': 0,                       # change to reshuffle every random choice
    'VARIANTS': 8,                   # edge tile variants per side
    # colours (r, g, b)
    'WOOD_BASE': [(122, 92, 56), (110, 82, 48), (98, 72, 42), (86, 62, 36)],   # light -> dark grain
    'WOOD_DARK': (52, 36, 22),       # outline
    'WOOD_HI': (140, 108, 68),       # top/left highlight
    'PLANK_BG_BASE': [(58, 41, 26), (52, 37, 23), (46, 33, 20), (40, 28, 17)],
    'PLANK_BG_GAP': (30, 21, 13),
    'PLANK_BG_SEAM': (22, 15, 9),
    'SNOW': [(250, 253, 255), (226, 239, 250), (184, 206, 228)],               # highlight, body, shade
    # per-tile chances
    'SEAM_CHANCE': 0.45,             # butt joint across a bar
    'KNOT_CHANCE': 0.4,
    'CRACK_CHANCE': 0.3,
    'SPRINKLE': [0.03, 0.08, 0.14],  # snow speckles clinging to the wood, per snow level
}
OUT = Path(__file__).resolve().parents[2] / 'assets' / 'winterheart' / 'textures' / 'gui'
OUT.mkdir(parents=True, exist_ok=True)
SEED = SETTINGS['SEED']

# ---- wood_border.png ----# Sheet 64 x 120: three blocks (snow level 0..2), each 64 x 40:
#   rows 0-3: top / bottom / left / right edge tiles, 8 random variants each (columns 0..7)
#   row 4:    corners TL, TR, BL, BR (columns 0..3)
T=8; V=SETTINGS['VARIANTS']
WOOD_TOP=3
WOOD_COLS=(1,5)
def bar_h(x,y): return WOOD_TOP<=y<=7
def bar_v(x,y): return WOOD_COLS[0]<=x<=WOOD_COLS[1]
KINDS={
 'T':  (lambda x,y: bar_h(x,y), {'l':'T','r':'T'}),
 'B':  (lambda x,y: bar_h(x,y), {'l':'T','r':'T'}),
 'L':  (lambda x,y: bar_v(x,y), {'u':'L','d':'L'}),
 'R':  (lambda x,y: bar_v(x,y), {'u':'L','d':'L'}),
 'TL': (lambda x,y: x>=1 and bar_h(x,y) or (bar_v(x,y) and y>=WOOD_TOP), {'r':'T','d':'L'}),
 'TR': (lambda x,y: x<=5 and bar_h(x,y) or (bar_v(x,y) and y>=WOOD_TOP), {'l':'T','d':'L'}),
 'BL': (lambda x,y: x>=1 and bar_h(x,y) or (bar_v(x,y) and y<=7), {'r':'T','u':'L'}),
 'BR': (lambda x,y: x<=5 and bar_h(x,y) or (bar_v(x,y) and y<=7), {'l':'T','u':'L'}),
}
def wood_at(kind,x,y):
    f,nb=KINDS[kind]
    if 0<=x<T and 0<=y<T: return f(x,y)
    d='l' if x<0 else 'r' if x>=T else 'u' if y<0 else 'd'
    if d not in nb: return False
    return KINDS[nb[d]][0]((x+T)%T,(y+T)%T)
HORIZ={'T','B','TL','TR','BL','BR'}
BASE=SETTINGS['WOOD_BASE']
DARK=SETTINGS['WOOD_DARK']; HI=SETTINGS['WOOD_HI']; SEAM=(40,27,16)
KNOT=(66,46,26); KNOT_RING=(90,66,38)
SNOW=SETTINGS['SNOW']
LO=[0,2,3]; SPAN=[2,1,1]            # snow cap depth range per level: LO .. LO+SPAN (clipped by the tile)
OVERHANG=[0.08,0.2,0.35]
SPRINKLE=SETTINGS['SPRINKLE']
def shade(c,f): return tuple(max(0,min(255,int(v*f))) for v in c)

def build(kind,level,variant):
    f,_=KINDS[kind]
    base_seed=(sum(map(ord,kind))*1000+variant)*17+SEED*100003
    rw=random.Random(base_seed)                   # wood: identical for every snow level
    rs=random.Random(base_seed+level*991+5)       # snow: differs per level
    horiz=kind in HORIZ
    run=rw.choice([2,3,4,5]); tint=rw.uniform(0.9,1.1)
    streaks={}
    px={}
    pix=[(x,y) for y in range(T) for x in range(T) if f(x,y)]
    for (x,y) in pix:
        along,across=(x,y) if horiz else (y,x)
        key=(across,along//run)
        if key not in streaks: streaks[key]=rw.randrange(3)
        c=BASE[(across+streaks[key])%4]
        if rw.random()<0.14: c=BASE[min(3,(across+streaks[key])%4+1)]
        edge=any(not wood_at(kind,x+dx,y+dy) for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)))
        if edge: c=DARK
        elif not wood_at(kind,x,y-1) or not wood_at(kind,x-1,y): c=HI
        px[(x,y)]=shade(c,tint)
    interior=[p for p in pix if px[p] not in (DARK,HI)]
    if kind in ('T','B','L','R'):
        # butt joint across the bar
        if rw.random()<SETTINGS['SEAM_CHANCE']:
            cut=rw.randrange(1,T-1)
            for (x,y) in pix:
                a=x if horiz else y
                if a==cut and px[(x,y)]!=DARK: px[(x,y)]=SEAM
                elif a==cut+1 and px[(x,y)] not in (DARK,SEAM): px[(x,y)]=shade(px[(x,y)],1.12)
        # knot
        if rw.random()<SETTINGS['KNOT_CHANCE'] and interior:
            kx,ky=rw.choice(interior)
            for dx,dy in ((0,0),(1,0),(0,1),(1,1)):
                if (kx+dx,ky+dy) in px and px[(kx+dx,ky+dy)] not in (DARK,HI): px[(kx+dx,ky+dy)]=KNOT
            for dx,dy in ((-1,0),(2,0),(0,-1),(0,2),(-1,1),(2,1),(1,-1),(1,2)):
                if (kx+dx,ky+dy) in px and px[(kx+dx,ky+dy)] not in (DARK,HI,KNOT,SEAM) and rw.random()<0.7: px[(kx+dx,ky+dy)]=KNOT_RING
        # crack
        if rw.random()<SETTINGS['CRACK_CHANCE'] and interior:
            cx,cy=rw.choice(interior)
            for i in range(rw.randint(2,3)):
                q=(cx+(i if horiz else 0),cy+(0 if horiz else i)+(i//2 if horiz else 0))
                if q in px and px[q] not in (DARK,HI): px[q]=shade(DARK,1.1)
    # (snow piles on top are drawn in code as one smooth curve, see survival_hud_render.js)
    # snow clinging to the sides
    for p in list(px):
        x,y=p
        if px[p]==DARK: continue
        if rs.random()<SPRINKLE[level]:
            px[p]=SNOW[rs.randrange(2)]
            if level>0 and rs.random()<0.35 and (x,y+1) in px and px[(x,y+1)] not in (DARK,): px[(x,y+1)]=SNOW[1]
    return px

img=Image.new('RGBA',(V*T,3*5*T))
def put(level,col,row,kind,variant):
    ox,oy=col*T,level*5*T+row*T
    for (x,y),c in build(kind,level,variant).items(): img.putpixel((ox+x,oy+y),c+(255,))
for level in range(3):
    for row,kind in enumerate(['T','B','L','R']):
        for v in range(V): put(level,v,row,kind,v)
    for col,kind in enumerate(['TL','TR','BL','BR']): put(level,col,4,kind,0)
img.save(OUT / 'wood_border.png')

# ---- wood_stubs.png ----
# wood_stubs.png, 32x32: 8x8 sprites for planks poking out past the frame.
#   row 0: left stubs, row 1: right stubs, row 2: top stubs, row 3: bottom stubs, 4 variants each (columns 0..3)
BASE=SETTINGS['WOOD_BASE']
DARK=SETTINGS['WOOD_DARK']; HI=SETTINGS['WOOD_HI']; END=(150,118,76); END_RING=(120,90,54)
stub_img=Image.new('RGBA',(32,32))
def stub(side,v):
    r=random.Random(side*100+v+1+SEED*100003)
    length=r.randint(3,6); thick=r.choice([3,4,4])
    off=r.randint(0,8-thick)
    tint=r.uniform(0.9,1.08)
    horiz=side in (0,1)
    px={}
    for a in range(length):          # a: distance from the frame (0) to the free end (length-1)
        for b in range(thick):
            along=a; across=b
            c=BASE[(across+(a//2)+r.randrange(2))%4]
            if b==0 or b==thick-1 or a==length-1: c=DARK
            elif b==1: c=HI
            if a==length-1 and 0<b<thick-1: c=END if (b+a)%2==0 else END_RING
            c=tuple(max(0,min(255,int(v*tint))) for v in c)
            if side==0:   x,y=7-a,off+b          # left: attached on the right of the box
            elif side==1: x,y=a,off+b            # right: attached on the left
            elif side==2: x,y=off+b,7-a          # top: attached at the bottom
            else:         x,y=off+b,a            # bottom: attached at the top
            px[(x,y)]=c
    return px
for side in range(4):
    for v in range(4):
        for (x,y),c in stub(side,v).items(): stub_img.putpixel((v*8+x,side*8+y),c+(255,))
stub_img.save(OUT / 'wood_stubs.png')

# ---- plank_bg.png: 16x8 tile, two boards per tile, tiles in both directions ----
def make_plank_bg():
    W,H=16,8
    base=SETTINGS['PLANK_BG_BASE']; gap=SETTINGS['PLANK_BG_GAP']; seam=SETTINGS['PLANK_BG_SEAM']
    im=Image.new('RGBA',(W,H))
    def h(a,b,s): return random.Random(a*7919+b*104729+s+SEED*100003).random()
    for y in range(H):
        board=y//4; row=y%4
        seam_x=0 if board==0 else 8
        for x in range(W):
            local=(x-seam_x)%W
            streak=int(h(board,local//3,7)*3)
            c=base[(row+streak+board)%4]
            if h(x,y,3)<0.12: c=base[min(3,(row+streak)%4+1)]
            if row==3: c=gap
            if x==seam_x: c=seam
            if x==(seam_x+1)%W and row<3: c=tuple(min(255,v+8) for v in c)
            im.putpixel((x,y),c+(255,))
    im.save(OUT / 'plank_bg.png')
make_plank_bg()
print('wrote', ', '.join(p.name for p in sorted(OUT.glob('*.png')) if p.name in ('wood_border.png','wood_stubs.png','plank_bg.png')), 'to', OUT)
