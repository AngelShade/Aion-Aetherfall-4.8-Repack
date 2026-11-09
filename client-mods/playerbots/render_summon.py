"""Pre-render Companion Recruitment effects over its painted stage.

Glow is blended offline into DXT1 stage frames. DXT5 flash and ember overlays
remain transparent over the native 3D model; the Lua dialog plays both layers.
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy.ndimage import gaussian_filter

WIDTH, HEIGHT = 1056, 660
TEXTURE, OVERLAY = 1024, 512
FPS = 24
BLUE = np.array((.55, .78, 1.0))
GOLD = np.array((1.0, .78, .42))
VIOLET = np.array((.72, .55, 1.0))
WHITE = np.array((1.0, 1.0, 1.0))
# Playback is 20 Hz. The legacy renderer's FPS=24 is intentionally retained:
# regenerating that cohort is outside a tier update and would change its bytes.
TIER_TIMING = {2: (44, 33), 3: (56, 36), 4: (70, 38)}
TIER_COLOUR = {2: np.array((140, 240, 168)) / 255,
               3: np.array((121, 200, 255)) / 255,
               4: np.array((255, 201, 107)) / 255}
SHARED_CHARGE = 18


def screen(base, light):
    """Light adds like real light, and never clips into flat white patches."""
    return 1 - (1 - base) * (1 - np.clip(light, 0, 1))


def tint(mask, colour):
    return mask[..., None] * colour


def rune_ring(size, seed, inner, outer, glyphs):
    """A rune band: fine circles, tick marks and angular glyphs, drawn at twice the size."""
    random = np.random.default_rng(seed)
    scale = 2
    canvas = Image.new('L', (size * scale, size * scale), 0)
    draw = ImageDraw.Draw(canvas)
    middle = size * scale / 2
    r0, r1 = inner * middle, outer * middle
    for radius, width in ((r1, 3), (r1 - 7, 1), (r0, 3), (r0 + 7, 1)):
        draw.ellipse((middle - radius, middle - radius, middle + radius, middle + radius), outline=255, width=width)
    for i in range(glyphs):
        angle = i / glyphs * math.tau
        cos, sin = math.cos(angle), math.sin(angle)
        band = (r0 + r1) / 2
        half = (r1 - r0) * .3
        step = band * math.tau / glyphs * .3
        strokes = random.integers(3, 6)
        points = [(random.uniform(-step, step), random.uniform(-half, half)) for _ in range(strokes)]
        placed = [(middle + cos * (band + y) - sin * x, middle + sin * (band + y) + cos * x) for x, y in points]
        draw.line(placed, fill=255, width=3, joint='curve')
        tick = r1 - 13
        draw.line((middle + cos * tick, middle + sin * tick, middle + cos * (tick + 5), middle + sin * (tick + 5)), fill=255, width=2)
    return canvas.resize((size, size), Image.LANCZOS)


def star_polygon(size, points, step, radius):
    """The inscribed star at a summoning circle's heart."""
    canvas = Image.new('L', (size * 2, size * 2), 0)
    draw = ImageDraw.Draw(canvas)
    middle = size
    corners = [(middle + math.cos(i / points * math.tau) * radius * size, middle + math.sin(i / points * math.tau) * radius * size) for i in range(points)]
    for i in range(points):
        draw.line((corners[i], corners[(i + step) % points]), fill=255, width=3)
    return canvas.resize((size, size), Image.LANCZOS)


class Scene:
    def __init__(self, stage, centre, floor):
        self.base = np.asarray(Image.open(stage).convert('RGB').resize((WIDTH, HEIGHT), Image.LANCZOS), dtype=np.float32) / 255
        self.centre = centre
        self.floor = floor
        y, x = np.mgrid[0:HEIGHT, 0:WIDTH].astype(np.float32)
        self.dx, self.dy = x - centre[0], y - centre[1]
        self.radius = np.hypot(self.dx, self.dy)
        self.angle = np.arctan2(self.dy, self.dx)
        self.rings = [(rune_ring(560, 1, .78, .98, 44), .35), (rune_ring(560, 2, .52, .70, 30), -.55),
                      (star_polygon(560, 7, 3, .50), .22), (rune_ring(560, 3, .24, .36, 18), -.9)]
        random = np.random.default_rng(7)
        self.streaks = gaussian_filter(random.random(720), 3, mode='wrap')
        self.streaks = (self.streaks - self.streaks.min()) / np.ptp(self.streaks)
        self.fine = gaussian_filter(random.random(720), 1, mode='wrap')
        self.fine = (self.fine - self.fine.min()) / np.ptp(self.fine)

    def place(self, image, rotation, centre, squash, zoom):
        size = int(image.size[0] * zoom)
        turned = image.rotate(rotation, Image.BICUBIC).resize((size, max(2, int(size * squash))), Image.BILINEAR)
        out = Image.new('L', (WIDTH, HEIGHT), 0)
        out.paste(turned, (int(centre[0] - turned.size[0] / 2), int(centre[1] - turned.size[1] / 2)))
        return np.asarray(out, dtype=np.float32) / 255

    def circle(self, time, spin, power, centre, squash, zoom, colour):
        light = np.zeros((HEIGHT, WIDTH), dtype=np.float32)
        for image, speed in self.rings:
            light += self.place(image, time * speed * spin * 57.3, centre, squash, zoom)
        light = np.clip(light, 0, 1)
        bloom = gaussian_filter(light, 3) * 1.5 + gaussian_filter(light, 11) * 1.9 + gaussian_filter(light, 30) * 1.5
        return tint((light + bloom) * power, colour)

    def core(self, power, reach):
        glow = np.exp(-(self.radius / reach) ** 2)
        hot = np.exp(-(self.radius / (reach * .28)) ** 2)
        return tint(glow * power, BLUE) + tint(hot * power, WHITE)

    def shafts(self, time, power, reach, spin=.05):
        index = ((self.angle + time * spin) / math.tau * 720).astype(int) % 720
        back = ((self.angle - time * spin * .6) / math.tau * 720).astype(int) % 720
        rays = self.streaks[index] ** 3 * .75 + self.fine[back] ** 5 * .5
        fall = np.exp(-self.radius / reach) * np.clip(self.radius / 40, 0, 1)
        return tint(rays * fall * power, BLUE * .55 + WHITE * .45)

    def arcs(self, time, power):
        """Tilted ribbons of light orbiting the gate, as the storyboard paints them."""
        light = np.zeros((HEIGHT, WIDTH, 3), dtype=np.float32)
        for index, (width, height, tilt, speed, colour) in enumerate(((420, 150, -.42, 1.1, GOLD), (500, 120, .34, -.8, BLUE), (360, 180, .8, 1.5, VIOLET), (460, 96, -.1, -1.3, GOLD))):
            canvas = Image.new('L', (WIDTH, HEIGHT), 0)
            draw = ImageDraw.Draw(canvas)
            steps = 140
            for step in range(steps):
                phase = step / steps
                a = time * speed + phase * 4.2
                x = math.cos(a) * width
                y = math.sin(a) * height
                px = self.centre[0] + x * math.cos(tilt) - y * math.sin(tilt)
                py = self.centre[1] + x * math.sin(tilt) + y * math.cos(tilt)
                if step:
                    draw.line((last, (px, py)), fill=int(255 * phase ** 2), width=2 + int(phase * 3))
                last = (px, py)
            mask = np.asarray(canvas, dtype=np.float32) / 255
            light += tint((mask + gaussian_filter(mask, 5) * 2.2 + gaussian_filter(mask, 16) * 2) * power, colour)
        return light


class Sparks:
    """Hundreds of points with real motion, each drawn as a soft streak along its velocity."""

    def __init__(self, count, seed):
        self.random = np.random.default_rng(seed)
        self.count = count
        self.position = np.zeros((count, 2))
        self.velocity = np.zeros((count, 2))
        self.life = np.zeros(count)
        self.span = np.ones(count)
        self.size = self.random.uniform(.6, 1.9, count)

    def spawn(self, count, position, velocity, span):
        dead = np.where(self.life <= 0)[0][:count]
        count = len(dead)
        if not count:
            return
        self.position[dead] = position(count)
        self.velocity[dead] = velocity(count)
        self.life[dead] = self.span[dead] = span(count)

    def step(self, delta, pull=None, gravity=0, drag=1):
        alive = self.life > 0
        if pull is not None:
            towards = pull - self.position[alive]
            distance = np.linalg.norm(towards, axis=1, keepdims=True) + 1
            self.velocity[alive] += towards / distance * 900 * delta
        self.velocity[alive] *= drag
        self.velocity[alive, 1] += gravity * delta
        self.position[alive] += self.velocity[alive] * delta
        self.life[alive] -= delta

    def draw(self, colour, delta):
        canvas = Image.new('L', (WIDTH, HEIGHT), 0)
        draw = ImageDraw.Draw(canvas)
        for i in np.where(self.life > 0)[0]:
            fade = min(1, self.life[i] / self.span[i] * 2.5)
            x, y = self.position[i]
            tail = self.position[i] - self.velocity[i] * delta * 1.6
            draw.line((tail[0], tail[1], x, y), fill=int(255 * fade), width=max(1, round(self.size[i])))
        mask = np.asarray(canvas, dtype=np.float32) / 255
        return tint(mask * 1.3 + gaussian_filter(mask, 1.6) * 3 + gaussian_filter(mask, 6) * 4, colour)


def out_sparks(sparks, centre, count, speed):
    def position(n):
        return np.tile(centre, (n, 1)) + sparks.random.normal(0, 26, (n, 2))

    def velocity(n):
        angle = sparks.random.uniform(0, math.tau, n)
        pace = sparks.random.uniform(.25, 1, n) ** 2 * speed + 90
        return np.stack((np.cos(angle) * pace, np.sin(angle) * pace), 1)

    sparks.spawn(count, position, velocity, lambda n: sparks.random.uniform(.5, 1.5, n))


def in_sparks(sparks, centre, count):
    def position(n):
        angle = sparks.random.uniform(0, math.tau, n)
        reach = sparks.random.uniform(240, 520, n)
        return np.stack((centre[0] + np.cos(angle) * reach, centre[1] + np.sin(angle) * reach * .8), 1)

    def velocity(n):
        return sparks.random.normal(0, 70, (n, 2))

    sparks.spawn(count, position, velocity, lambda n: sparks.random.uniform(.5, .9, n))


def rise_sparks(sparks, centre, count):
    def position(n):
        return np.stack((centre[0] + sparks.random.normal(0, 200, n), centre[1] + 190 + sparks.random.uniform(-30, 40, n)), 1)

    def velocity(n):
        return np.stack((sparks.random.normal(0, 16, n), -sparks.random.uniform(26, 110, n)), 1)

    sparks.spawn(count, position, velocity, lambda n: sparks.random.uniform(1.8, 4, n))


def render(stage, output, centre, floor, only=None, quality=86):
    scene = Scene(stage, centre, floor)
    delta = 1 / FPS
    centre_array = np.array(centre, dtype=float)
    looks = output / 'looks'
    looks.mkdir(parents=True, exist_ok=True)
    frames = {}

    def write(clip, index, light, dim=1.0):
        image = Image.fromarray((screen(scene.base * dim, light) * 255).astype(np.uint8))
        image.resize((TEXTURE, TEXTURE), Image.LANCZOS).save(output / f'{clip}-{index:03d}.dds', format='DDS', pixel_format='DXT1')
        if index % 8 == 0:
            image.save(looks / f'{clip}-{index:03d}.jpg', quality=quality)
        frames[clip] = index + 1

    def overlay(clip, index, colour, alpha):
        """An effect drawn over the 3D model, so it needs its own transparency."""
        rgba = np.dstack((np.broadcast_to(colour, (HEIGHT, WIDTH, 3)), np.clip(alpha, 0, 1)))
        image = Image.fromarray((rgba * 255).astype(np.uint8), 'RGBA')
        image.resize((OVERLAY, OVERLAY), Image.LANCZOS).save(output / f'{clip}-{index:03d}.dds', format='DDS', pixel_format='DXT5')
        frames[clip] = index + 1

    # Gate awakens: rings spin up, energy streams in and the arcs ignite.
    if not only or 'charge' in only:
        sparks = Sparks(420, 12)
        length = int(FPS * 1.6)
        for index in range(length):
            t = index / length
            ease = t * t * (3 - 2 * t)
            in_sparks(sparks, centre_array, int(8 + 18 * ease)); sparks.step(delta, pull=centre_array, drag=.97)
            time = 2 + ease * ease * 22
            light = scene.circle(time, 1, .32 + .55 * ease, centre, 1, .72 + .03 * ease, BLUE * .6 + WHITE * .4) + scene.core(.12 + .6 * ease, 110 + 110 * ease)
            light += scene.circle(time, -1, .3 + .5 * ease, floor, .3, .98, GOLD) + scene.shafts(time, .12 + .5 * ease, 220 + 200 * ease, .22)
            light += scene.arcs(time * .55, .05 + .42 * ease) + sparks.draw(WHITE * .4 + BLUE * .6, delta)
            write('charge', index, light, 1 - .62 * ease)

    # Burst: full white, a shock ring, sparks thrown out, then the light settles for the reveal.
    if not only or 'burst' in only:
        sparks = Sparks(620, 13)
        length = int(FPS * 1.4)
        out_sparks(sparks, centre_array, 520, 1500)
        for index in range(length):
            t = index / length
            sparks.step(delta, gravity=70, drag=.94)
            if index in (4, 8):
                out_sparks(sparks, centre_array, 50, 900)
            flash = math.exp(-t * 7.5) * 3
            wave = t * 760
            shock = np.exp(-((scene.radius - wave) / (16 + 42 * t)) ** 2) * (1 - t) ** 1.5 * 1.6
            light = tint(np.ones_like(shock) * flash, WHITE) + tint(shock, WHITE * .5 + BLUE * .5) + scene.core(1.5 * (1 - t) ** 2 + .3, 300)
            light += scene.shafts(t * 7, .95 * (1 - t) + .2, 520, .3) + scene.circle(20 + t * 3, 1, .3, centre, 1, .75 + t * .3, BLUE * .5 + WHITE * .5)
            light += scene.circle(20 + t * 3, -1, .4, floor, .3, .98, GOLD) + sparks.draw(WHITE * .6 + GOLD * .4, delta)
            write('burst', index, light, .38 + .3 * t)

    # Held behind the character: a slow shining halo, looping for as long as the result stays up.
    if not only or 'hold' in only:
        sparks = Sparks(150, 14)
        for lead in range(FPS * 3):
            rise_sparks(sparks, centre_array, 3); sparks.step(delta)
        loop = FPS * 4
        for index in range(loop):
            time = 30 + index / loop * math.tau / .05 / 40
            rise_sparks(sparks, centre_array, 2); sparks.step(delta)
            light = scene.core(.22, 240) + scene.shafts(time, .3, 380) + scene.circle(time, 1, .16, centre, 1, .9, BLUE * .6 + WHITE * .4)
            light += scene.circle(time, -1, .42, floor, .3, .98, GOLD) + scene.arcs(time * .35, .12) + sparks.draw(WHITE * .6 + BLUE * .4, delta)
            write('hold', index, light, .68)

    # Over the model: a wash of light that hides its arrival, then embers looping around it.
    if not only or 'overlay' in only:
        wash = np.exp(-(scene.radius / 620) ** 2)
        colour = WHITE * .7 + BLUE * .3
        for index in range(12):
            t = index / 11
            overlay('flash', index, colour, np.clip(wash * 2.2, 0, 1) * (1 - t) ** 1.6)
        random = np.random.default_rng(15)
        count, loop = 150, 24
        start = np.stack((centre[0] + random.normal(0, 190, count), floor[1] + random.uniform(-340, 30, count)), 1)
        offset, rise, size = random.uniform(0, 1, count), random.uniform(60, 180, count), random.choice((1, 1, 2, 2, 3), count)
        for index in range(loop):
            canvas = Image.new('L', (WIDTH, HEIGHT), 0)
            draw = ImageDraw.Draw(canvas)
            for n in range(count):
                phase = (index / loop + offset[n]) % 1
                x, y = start[n, 0] + math.sin(phase * math.tau) * 10, start[n, 1] - phase * rise[n]
                draw.ellipse((x - size[n], y - size[n], x + size[n], y + size[n]), fill=int(255 * math.sin(phase * math.pi)))
            mask = np.asarray(canvas, dtype=np.float32) / 255
            overlay('sparks', index, WHITE * .75 + BLUE * .25, mask + gaussian_filter(mask, 2) * 2.4)

    manifest = output / 'manifest.json'
    known = json.loads(manifest.read_text()) if manifest.exists() else {}
    known.update(frames)
    manifest.write_text(json.dumps(dict(known, fps=FPS, width=WIDTH, height=HEIGHT, centre=centre, floor=floor)))
    return frames


def render_tiers(stage, baseline, output, centre=(533, 264), floor=(533, 469)):
    """Additive authored choreography. Never write or regenerate legacy frames.

    Decode the installed stage plates to retain the painted scene and exact
    shared lead-in. Only the charge tails and bursts need new opaque frames;
    the 96 held plates and most of the arrival wash are reused unchanged.
    """
    output.mkdir(parents=True, exist_ok=True)
    scene = Scene(stage, centre, floor)
    records = {}
    y, x = np.mgrid[:HEIGHT, :WIDTH].astype(np.float32)
    floor_radius = np.hypot(x-floor[0], (y-floor[1])/.30)

    def plate(clip, n):
        return np.asarray(Image.open(baseline/f'{clip}-{n:03d}.dds').convert('RGB')
                          .resize((WIDTH, HEIGHT), Image.Resampling.LANCZOS), dtype=np.float32)/255

    def save(clip, n, image, opaque=False):
        target = output/f'{clip}-{n:03d}.dds'
        image.resize((TEXTURE, TEXTURE) if opaque else (OVERLAY, OVERLAY), Image.Resampling.LANCZOS).save(
            target, format='DDS', pixel_format='DXT1' if opaque else 'DXT5')
        records[clip] = n+1

    def opaque(clip, n, base, light):
        save(clip, n, Image.fromarray(np.uint8(np.clip(screen(base, light), 0, 1)*255)), True)

    def transparent(clip, n, light):
        # Straight alpha, so native source-over adds soft light without black
        # fringes. Keep opaque hot cores white and luminous tails coloured.
        alpha = np.clip(np.max(light, axis=2), 0, 1)
        rgb = np.clip(light/np.maximum(alpha[..., None], .00001), 0, 1)
        save(clip, n, Image.fromarray(np.uint8(np.dstack((rgb, alpha))*255)))

    def bloom(mask, colour, power=1):
        return tint((mask*.85+gaussian_filter(mask, 3)*2.5+gaussian_filter(mask, 13)*2)*power, colour)

    def ring(radius, width, elliptical=False):
        return np.exp(-(((floor_radius if elliptical else scene.radius)-radius)/width)**2)

    def charge_colour(base, index, colour, strength):
        # Recover the light from the existing painted plate before shifting
        # it. Adding colour on top of its white-hot rings alone washes every
        # tier back to white and makes the rarity tell too hard to read.
        t=index/38;ease=t*t*(3-2*t);under=scene.base*(1-.62*ease)
        energy=np.clip((base-under)/np.maximum(1-under,.001),0,1)
        luminance=np.max(energy,axis=2)
        coloured=screen(under,tint(luminance,colour*.90+WHITE*.10))
        area=np.maximum(np.exp(-(scene.radius/355)**4),np.exp(-(floor_radius/295)**4))
        weight=area[...,None]*strength*.88
        return base*(1-weight)+coloured*weight

    def lightning(time, power):
        canvas = Image.new('L', (WIDTH, HEIGHT)); draw = ImageDraw.Draw(canvas)
        rng = np.random.default_rng(300+int(time*12))
        for branch in range(5):
            a = time*.8+branch*math.tau/5
            points = []
            for j in range(19):
                angle = a+j*.095
                r = 170+32*math.sin(j*.7+time)+rng.uniform(-12, 12)
                points.append((centre[0]+math.cos(angle)*r, centre[1]+math.sin(angle)*r))
            draw.line(points, fill=255, width=2)
            if branch%2 == 0:
                px, py = points[9]
                draw.line((px, py, px+22, py-19, px+12, py-40, px+43, py-71), fill=210, width=2)
        return bloom(np.asarray(canvas,dtype=np.float32)/255, TIER_COLOUR[3]*.65+WHITE*.35, power)

    def streams(time, power, colour, gold=False):
        canvas = Image.new('L',(WIDTH,HEIGHT)); draw = ImageDraw.Draw(canvas)
        count = 18 if gold else 9
        for j in range(count):
            a = j*math.tau/count+time*.28
            if gold:
                # Rays rise from the floor into a cathedral of light.
                px = floor[0]+math.cos(a)*(105+j%4*24)
                h = (160+j%5*62)*(.75+.25*math.sin(time*2+j))
                draw.line((px, floor[1], px+(px-floor[0])*.5, floor[1]-h), fill=125+j%4*30, width=2)
            else:
                points = [(centre[0]+math.cos(a+k*.045)*(340-k*7),
                           centre[1]+math.sin(a+k*.045)*(230-k*4.5)) for k in range(30)]
                draw.line(points, fill=200, width=2)
        return bloom(np.asarray(canvas,dtype=np.float32)/255, colour, power)

    def pillar(time, power):
        width = 35+8*math.sin(time*2)
        vertical = np.exp(-((x-floor[0])/width)**2)*np.clip((floor[1]-y)/85,0,1)
        shoulders = np.exp(-((x-floor[0])/115)**2)*np.clip((floor[1]+30-y)/200,0,1)
        fluting = (.65+.35*np.cos((x-floor[0])*.11+time*1.6)**8)
        return tint((vertical*.9+shoulders*.28)*fluting*power, TIER_COLOUR[4])

    for tier,(charge,burst) in TIER_TIMING.items():
        colour = TIER_COLOUR[tier]
        length = charge-SHARED_CHARGE
        for n in range(length):
            t = n/(length-1); build = t*t*(3-2*t); time = n/20
            base = plate('charge', min(SHARED_CHARGE+n,37))
            base = charge_colour(base,min(SHARED_CHARGE+n,37),colour,build)
            mask = scene.place(scene.rings[0][0], 50+time*(45+tier*9), centre, 1, .75+.04*build)
            light = bloom(mask, colour, build*(.35 if tier==2 else .6))
            light += streams(time,build*(.5 if tier==2 else .7),colour,tier==4)
            light += tint(ring(240,18,True)*build*.5,colour)
            if tier==3: light += lightning(time,build*(.4+.3*math.sin(time*7)**2))
            if tier==4:
                light += pillar(time,build*1.65)
                light += tint(ring(180+35*math.sin(time*2),6,True)*build*.65,colour)
                light += tint(np.exp(-(scene.radius/240)**2)*build*.35,colour)
            opaque(f'tier{tier}-charge',n,base,light)
        print(f'tier {tier}: {length} charge tail frames',flush=True)
        sparks=Sparks(800 if tier==4 else 620,130+tier)
        out_sparks(sparks,np.array(centre,dtype=float),700 if tier==4 else 520,1800 if tier==4 else 1450)
        for n in range(burst):
            t=n/(burst-1); time=n/20
            # A short white ignition gives way to distinct coloured geometry.
            # Render against the original painting rather than adding yet more
            # light to the legacy burst's already white, clipped centre.
            base=scene.base*(.38+.30*t)
            sparks.step(1/20,gravity=70,drag=.94)
            light=tint(np.ones_like(scene.radius)*math.exp(-time*10)*2.8,WHITE*.82+colour*.18)
            light+=tint(np.exp(-(scene.radius/(110+170*(1-t)))**2)*(1-t)**2*1.4,colour)
            light+=tint(np.exp(-(scene.radius/45)**2)*(1-t)*1.3,WHITE)
            mask=scene.place(scene.rings[0][0],time*(60+tier*8),centre,1,.75+t*.3)
            light+=bloom(mask,colour,(1-t)*.45)
            light+=sparks.draw(colour*.8+WHITE*.2,1/20)
            light+=tint(ring(40+t*(800 if tier==4 else 600),12+20*t)*(1-t)**1.4*1.6,colour)
            light+=streams(time,(1-t)*(.65 if tier==2 else 1),colour,tier==4)
            if tier==3:
                light+=lightning(time,(1-t)**1.5*.9)
                light+=tint(ring(20+t*1050,9+15*t,True)*(1-t)*1.8,colour)
            if tier==4:
                light+=pillar(time,(1-t)**1.3*2.2)
                light+=tint(np.exp(-(scene.radius/650)**2)*math.exp(-t*8)*1.6,colour*.5+WHITE*.5)
                light+=tint(ring(max(0,t-.1)*1200,24,True)*(1-t)*1.5,colour)
            # Blend to the exact held plate at the end, avoiding an abrupt
            # change of background/halo when the shared hold loop begins.
            composite=screen(base,light)
            transition=max(0,(t-.68)/.32);transition=transition*transition*(3-2*transition)
            composite=composite*(1-transition)+plate('hold',0)*transition
            opaque(f'tier{tier}-burst',n,composite,np.zeros_like(light))
        print(f'tier {tier}: {burst} burst frames',flush=True)

        rng=np.random.default_rng(4800+tier)
        count={2:110,3:160,4:290}[tier]
        starts=rng.uniform(0,1,count); px=rng.normal(centre[0],200,count)
        py=rng.uniform(floor[1]-300,floor[1]+30,count); pace=rng.uniform(65,200,count)
        for n in range(24):
            time=n/24
            canvas=Image.new('L',(WIDTH,HEIGHT)); draw=ImageDraw.Draw(canvas)
            for j in range(count):
                phase=(time+starts[j])%1; fade=math.sin(phase*math.pi)**2
                sx=px[j]+math.sin(phase*math.tau+j)*12; sy=py[j]-phase*pace[j]
                size=1+j%({2:2,3:2,4:3}[tier]); a=round(fade*230)
                draw.line((sx,sy+size*3,sx,sy),fill=a,width=size)
            light=bloom(np.asarray(canvas,dtype=np.float32)/255,colour,.95 if tier==4 else .65)
            # Seamless low halo and floor ring; no opaque field over the model.
            light+=tint(ring(250+3*math.sin(time*math.tau),5,True)*(.10 if tier<4 else .18),colour)
            transparent(f'tier{tier}-sparks',n,light)

    wash=np.exp(-(scene.radius/700)**2)
    for n in range(12):
        t=n/11
        transparent('tier4-flash',n,tint(np.clip(wash*2.6,0,1)*(1-t)**1.6,TIER_COLOUR[4]*.18+WHITE*.82))
    for n in range(8):
        t=n/7; power=math.sin(t*math.pi)**.8
        # Two counter-rotating seven-point seals and a sweeping solar corona.
        seal=scene.place(star_polygon(560,7,3,.83),t*75,centre,1,.62+t*.65)
        seal+=scene.place(star_polygon(560,7,2,.83),-t*75,centre,1,.62+t*.65)
        light=bloom(seal,TIER_COLOUR[4],power*.8)
        light+=tint(ring(140+t*350,5)*power*.9,TIER_COLOUR[4]*.7+WHITE*.3)
        transparent('tier4-double',n,light)
    return records


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--centre', type=int, nargs=2, default=(533, 264))
    parser.add_argument('--floor', type=int, nargs=2, default=(533, 469))
    parser.add_argument('--only', nargs='*')
    parser.add_argument('--tiers-only', action='store_true', help='Only new tier frames; retain the existing baseline byte for byte')
    parser.add_argument('--baseline', type=Path, help='Read-only installed AetherfallSummon texture folder')
    parser.add_argument('--manifest', type=Path, help='Optional render receipt outside the texture delivery folder')
    args = parser.parse_args()
    if args.tiers_only:
        if not args.baseline: parser.error('--tiers-only requires --baseline')
        frames=render_tiers(args.stage,args.baseline,args.output,tuple(args.centre),tuple(args.floor))
        if args.manifest:
            args.manifest.parent.mkdir(parents=True,exist_ok=True)
            args.manifest.write_text(json.dumps(dict(fps=20,sharedChargeFrames=SHARED_CHARGE,clips=frames),indent=2)+'\n')
        print(frames)
    else:
        args.output.mkdir(parents=True,exist_ok=True)
        print(render(args.stage, args.output, tuple(args.centre), tuple(args.floor), args.only))


if __name__ == '__main__':
    main()
