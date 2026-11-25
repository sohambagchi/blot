// Generates core/src/test/resources/ink/parity.txt: perfect-freehand's own output for the inputs under
// core/src/test/resources/ink/inputs, which PerfectFreehandParityTest compares the Kotlin port against.
//
//   mkdir /tmp/pf && cd /tmp/pf && nix-shell -p nodejs --run 'npm init -y && npm install perfect-freehand@1.2.3'
//   nix-shell -p nodejs --run 'node core/src/test/js/parity.mjs \
//     /tmp/pf/node_modules/perfect-freehand/dist/esm/index.mjs core/src/test/resources/ink/inputs' \
//     > core/src/test/resources/ink/parity.txt
//
// Inputs are floats, as on Android; outputs are rounded to floats, as the Kotlin port returns them.

import { readdirSync, readFileSync } from 'node:fs'
import { basename, join } from 'node:path'
import { pathToFileURL } from 'node:url'

const [, , modulePath, inputsDir] = process.argv
const { getStroke, getStrokePoints } = await import(pathToFileURL(modulePath).href)

const inputs = {}
for (const file of readdirSync(inputsDir).filter((f) => f.endsWith('.txt')).sort()) {
  inputs[basename(file, '.txt')] = readFileSync(join(inputsDir, file), 'utf8')
    .split('\n')
    .filter((l) => l.trim() && !l.startsWith('#'))
    .map((l) => l.trim().split(/\s+/).map((v) => Math.fround(Number(v))))
}

const easings = {
  linear: (t) => t,
  quad: (t) => t * t,
  outQuad: (t) => t * (2 - t),
  outCubic: (t) => --t * t * t + 1,
  outSine: (t) => Math.sin((t * Math.PI) / 2),
}

// "key=value,key=value" → upstream options; absent keys keep upstream's defaults.
function toOptions(spec) {
  const o = {}
  const start = {}
  const end = {}
  for (const kv of spec ? spec.split(',') : []) {
    const [k, v] = kv.split('=')
    const num = Number(v)
    const bool = v === 'true'
    const taper = v === 'full' ? true : v === 'none' ? false : num
    switch (k) {
      case 'size': case 'thinning': case 'smoothing': case 'streamline': o[k] = num; break
      case 'simulatePressure': case 'last': o[k] = bool; break
      case 'easing': o.easing = easings[v]; break
      case 'startCap': start.cap = bool; break
      case 'startTaper': start.taper = taper; break
      case 'startEasing': start.easing = easings[v]; break
      case 'endCap': end.cap = bool; break
      case 'endTaper': end.taper = taper; break
      case 'endEasing': end.easing = easings[v]; break
      default: throw new Error(`unknown option ${k}`)
    }
  }
  if (Object.keys(start).length) o.start = start
  if (Object.keys(end).length) o.end = end
  return o
}

const out = []
// Five decimals are plenty for the 1e-3 tolerance and keep the file small.
const f = (n) => Number(Math.fround(n).toFixed(5)).toString()

function outline(input, spec) {
  const pts = getStroke(inputs[input], toOptions(spec))
  out.push(`outline ${input} ${spec || '-'} ${pts.length}`)
  for (const [x, y] of pts) out.push(`${f(x)} ${f(y)}`)
}

function points(input, spec) {
  const pts = getStrokePoints(inputs[input], toOptions(spec))
  out.push(`points ${input} ${spec || '-'} ${pts.length}`)
  for (const p of pts) {
    out.push([...p.point, p.pressure, ...p.vector, p.distance, p.runningLength].map(f).join(' '))
  }
}

function summary(input, spec) {
  const pts = getStroke(inputs[input], toOptions(spec)).map(([x, y]) => [Math.fround(x), Math.fround(y)])
  let sx = 0
  let sy = 0
  for (const [x, y] of pts) {
    sx += x
    sy += y
  }
  const first = pts[0] ?? [0, 0]
  const last = pts[pts.length - 1] ?? [0, 0]
  out.push(`summary ${input} ${spec || '-'} ${pts.length} ${[sx, sy, ...first, ...last].map(String).join(' ')}`)
}

// Upstream's test RNG (xorshift), so the random cases are reproducible.
function getRng(seed = '') {
  let x = 0, y = 0, z = 0, w = 0
  function next() {
    const t = x ^ (x << 11)
    x = y; y = z; z = w
    w ^= ((w >>> 19) ^ t ^ (t >>> 8)) >>> 0
    return w / 0x100000000
  }
  for (let k = 0; k < seed.length + 64; k++) {
    x ^= seed.charCodeAt(k) | 0
    next()
  }
  return next
}

const all = Object.keys(inputs)
for (const input of all) {
  outline(input, '')
  points(input, '')
}

const variants = [
  'last=true',
  'simulatePressure=false',
  'simulatePressure=false,last=true',
  'startTaper=full,endTaper=full',
  'startTaper=40,endTaper=60',
  'startTaper=40,endTaper=60,last=true',
  'startCap=false,endCap=false',
  'startCap=false,endCap=false,simulatePressure=false',
  'thinning=0',
  'thinning=-0.5',
  'thinning=1,simulatePressure=false',
  'easing=outSine',
  'easing=quad,startTaper=20,startEasing=linear,endTaper=20,endEasing=quad',
  'streamline=0',
  'streamline=1',
  'smoothing=0',
  'smoothing=1',
  'size=4',
  'size=60',
  'size=6,thinning=0.7,streamline=0.3,simulatePressure=false,easing=outSine,last=true',
  'size=6,thinning=0.7,streamline=0.3,simulatePressure=false,easing=outSine,startTaper=10,endTaper=10',
]
for (const input of ['hey', 'corners', 'tablet-1', 'tablet-2', 'numberPairs', 'onePoint', 'twoPoints']) {
  for (const spec of variants) outline(input, spec)
}
points('tablet-2', 'last=true,streamline=0.2,size=8')
points('corners', 'last=true')

// Upstream's generator returns values in [-0.5, 0.5).
const next = getRng('perfect')
const rng = () => next() + 0.5
const easingNames = Object.keys(easings)
const pick = (list) => list[Math.floor(rng() * list.length)]
const taper = () => pick(['none', 'full', String(rng() * 100)])
for (let i = 0; i < 60; i++) {
  for (const input of all) {
    const spec = [
      `size=${rng() * 100}`,
      `thinning=${rng() * 2 - 1}`,
      `streamline=${rng()}`,
      `smoothing=${rng()}`,
      `easing=${pick(easingNames)}`,
      `simulatePressure=${rng() > 0.5}`,
      `last=${rng() > 0.5}`,
      `startCap=${rng() > 0.5}`,
      `startTaper=${taper()}`,
      `startEasing=${pick(easingNames)}`,
      `endCap=${rng() > 0.5}`,
      `endTaper=${taper()}`,
      `endEasing=${pick(easingNames)}`,
    ].join(',')
    summary(input, spec)
  }
}

console.log('# perfect-freehand 1.2.3 output; generated by core/src/test/js/parity.mjs')
console.log(out.join('\n'))
