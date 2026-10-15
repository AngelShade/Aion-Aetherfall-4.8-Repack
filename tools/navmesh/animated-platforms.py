"""Export authored Aion animated physics for every native map, without changing the client.

This is a preparation utility, not a runtime clock or fabricated transport link.
Native NCAion750 / Node823 / Controller826 / Timing918 contracts are retained.
Material collision classification follows the maintained Beyond-Aion geometry format.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct
import sys
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT / 'client-mods/expanded-warehouse'))
from codec import read_pak, binary_xml


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def native_xml(data):
    return binary_xml(data) if data.startswith(b'\x80') else ET.fromstring(data)


def vector(text, default):
    values = [float(v) for v in (text or default).split(',')]
    require(len(values) == 3 and all(math.isfinite(v) for v in values), 'Invalid native entity vector')
    return values


class Model:
    def __init__(self, data, materials):
        require(20 <= len(data) <= 64 * 1024 * 1024, 'CGA size limit')
        require(data[:8] == b'NCAion\0\0', 'CGA signature')
        kind, version, table = struct.unpack_from('<3I', data, 8)
        require(kind == 0xffff0000 and version == 0x750, 'Unsupported CGA header')
        self.data = data
        count = self.unpack('<I', table)[0]
        require(0 < count <= 65536 and table + 4 + count * 16 <= len(data), 'CGA chunk table')
        self.chunks = {}
        ordered = []
        for i in range(count):
            kind, version, offset, ident = self.unpack('<4I', table + 4 + i * 16)
            require(ident not in self.chunks and 20 <= offset < table, 'CGA chunk identity/offset')
            self.chunks[ident] = (kind, version, offset)
            ordered.append((offset, ident))
        ordered.sort()
        self.ends = {ident: ordered[i + 1][0] if i + 1 < len(ordered) else table
                     for i, (offset, ident) in enumerate(ordered)}
        self.materials = {}
        face_materials = []
        for ident, (kind, version, offset) in self.chunks.items():
            if kind != 0xcccc000c:
                continue
            require(version == 0x747, 'Unsupported material encoding')
            name = self.string(offset + 16, 128)
            material_type, children = self.unpack('<2I', offset + 144)
            flags, collision = self.unpack('<If', offset + 2544)
            require(collision in (0, 1), 'Native collision flag')
            material_id = materials.get(name.rsplit('/', 1)[-1], -1)
            links = list(self.unpack('<' + str(children) + 'i', offset + 2560)) if material_type == 2 and children else []
            self.materials[ident] = dict(name=name, materialId=material_id, flags=flags,
                                        collision=bool(collision), children=links)
            if material_type != 2:
                face_materials.append(ident)
        timings = [ident for ident, (kind, version, offset) in self.chunks.items() if kind == 0xcccc000e]
        require(len(timings) == 1, 'Expected one native default animation timing')
        kind, version, offset = self.chunks[timings[0]]
        require(version == 0x918, 'Unsupported native timing encoding')
        seconds_per_tick, ticks_per_frame = self.unpack('<fI', offset + 16)
        start, end = self.unpack('<2i', offset + 56)
        require(math.isfinite(seconds_per_tick) and seconds_per_tick > 0 and ticks_per_frame > 0 and end >= start,
                'Invalid native animation timing')
        self.timing = dict(secondsPerTick=seconds_per_tick, ticksPerFrame=ticks_per_frame,
                           startFrame=start, endFrame=end)
        self.tracks = {}
        self.nodes = []
        for ident, (kind, version, offset) in self.chunks.items():
            if kind == 0xcccc000d:
                require(version == 0x826, 'Unsupported native controller encoding')
                control_type, keys, flags, controller_id = self.unpack('<4I', offset + 16)
                # Native CryBez3, CryTCB3 and CryTCBQ retain their authored tangents/TCB parameters.
                width = {6: 9, 9: 8, 10: 9}.get(control_type)
                require(width is not None and 0 < keys <= 100000, 'Unsupported native controller type/count')
                stride = 4 + width * 4
                require(offset + 32 + keys * stride <= self.ends[ident], 'Truncated native controller')
                rows = [list(self.unpack('<i' + 'f' * width, offset + 32 + i * stride)) for i in range(keys)]
                require(all(math.isfinite(v) for row in rows for v in row[1:]), 'Nonfinite native controller')
                require(all(rows[i][0] < rows[i + 1][0] for i in range(len(rows) - 1)), 'Unordered native controller keys')
                self.tracks[str(ident)] = dict(type=control_type, flags=flags, controllerId=controller_id, keys=rows)
            elif kind == 0xcccc000b:
                require(version == 0x823, 'Unsupported native node encoding')
                object_id, parent_id, child_count, material = self.unpack('<4i', offset + 80)
                obj = self.chunks.get(object_id)
                require(obj is not None, 'Dangling animated object reference')
                node = dict(id=ident, name=self.string(offset + 16, 64), parent=parent_id,
                            position=list(self.unpack('<3f', offset + 164)),
                            rotation=list(self.unpack('<4f', offset + 176)),
                            scale=list(self.unpack('<3f', offset + 192)),
                            controllers=list(self.unpack('<3i', offset + 204)), material=material)
                # NODE823 has two booleans plus alignment before its matrix. The actual
                # packed Aion node positions begin at164; controller IDs at204.
                require(all(math.isfinite(v) for k in ['position', 'rotation', 'scale'] for v in node[k]), 'Nonfinite animated node')
                if obj[0] == 0xcccc0000:
                    node['mesh'] = self.mesh(obj[2], obj[1], face_materials)
                self.nodes.append(node)
        ids = {node['id'] for node in self.nodes}
        for node in self.nodes:
            require(node['parent'] == -1 or node['parent'] in ids, 'Dangling animated parent')
            require(all(c == -1 or str(c) in self.tracks for c in node['controllers']), 'Dangling animation controller')

    def unpack(self, fmt, offset):
        require(0 <= offset and offset + struct.calcsize(fmt) <= len(self.data), 'Truncated CGA field')
        return struct.unpack_from(fmt, self.data, offset)

    def string(self, offset, size):
        require(offset + size <= len(self.data), 'Truncated CGA string')
        return self.data[offset:offset + size].split(b'\0', 1)[0].decode('utf-8')

    def mesh(self, offset, version, face_materials):
        require(version == 0x744, 'Unsupported animated mesh encoding')
        reserved, vertices, uvs, faces, vertex_animation = self.unpack('<5i', offset + 16)
        require(0 < vertices <= 1000000 and 0 <= faces <= 2000000, 'Animated mesh count limit')
        points = [list(self.unpack('<3f', offset + 36 + i * 24)) for i in range(vertices)]
        require(all(math.isfinite(v) for p in points for v in p), 'Nonfinite animated vertex')
        retained = []
        physical_member = False
        for i in range(faces):
            a, b, c, material, smoothing = self.unpack('<5i', offset + 36 + vertices * 24 + i * 20)
            require(all(0 <= v < vertices for v in [a, b, c]) and 0 <= material < len(face_materials), 'Dangling animated face')
            mat = self.materials[face_materials[material]]
            # MAT_ENTITY Dyn_Bounce==1 is converted to native MIF_PHYSIC;
            # this is the field at2548, not the distinct exporter shader flags.
            # Native part1 also admits authored mat_obstruct. Membership is
            # independent of the server floor's WALK-only material exclusion.
            physical_member |= mat['collision'] or 'mat_obstruct' in mat['name'] or 'mat_phys' in mat['name']
            # WALK-only material6..9 cannot supply a physical moving floor.
            if mat['collision'] and not 6 <= mat['materialId'] <= 9:
                retained.append([a, b, c, face_materials[material]])
        return dict(vertices=points, physicalFaces=retained, physicalMember=physical_member)

    def document(self):
        return dict(format='aion-animated-model-1', units='centimetres', timing=self.timing,
                    materials=self.materials, nodes=self.nodes, tracks=self.tracks)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--verify', action='store_true', help='Compare the whole authored scene with current native client inputs')
    args = parser.parse_args()
    client, output = args.client.resolve(), args.output.resolve()
    require(not output.is_relative_to(ROOT) and not output.is_relative_to(client), 'Use external output, separate from source/client')
    output.mkdir(parents=True, exist_ok=True)
    with read_pak(client / 'Data/Material/Material.pak') as archive:
        material_bytes = archive.read('materials.xml')
    materials = {e.findtext('material_name'): int(e.findtext('id')) for e in native_xml(material_bytes)}
    levels = {p.parent.name.casefold(): p for p in (client / 'Levels').glob('*/Level.pak')}
    maps = ET.parse(ROOT / 'game-server/data/static_data/world_maps.xml').getroot()
    entities, models, coverage = [], {}, []
    for native_map in maps:
        path = levels.get(native_map.get('cName', '').casefold())
        if path is None:
            coverage.append(dict(map=int(native_map.get('id')), status='native client level absent'))
            continue
        with read_pak(path) as archive:
            for name in archive.namelist():
                if 'mission' not in name.lower() or not name.lower().endswith('.xml'):
                    continue
                mission = archive.read(name)
                for entity in native_xml(mission).iter('Entity'):
                    prop = entity.find('Properties')
                    model = '' if prop is None else prop.get('object_Model', '')
                    if not model.lower().endswith('.cga') or entity.get('SyncAnimation') != '1':
                        continue
                    key = model.replace('\\', '/').casefold()
                    require(key.startswith('levels/common/'), 'Animated model outside native common archives')
                    animation = prop.find('Animation')
                    require(animation is not None, 'Animated physical entity has no authored animation')
                    entities.append(dict(map=int(native_map.get('id')), mission=name, missionSha256=digest(mission),
                        model=key, entity=entity.attrib, animation=animation.attrib,
                        position=vector(entity.get('Pos'), '0,0,0'), angles=vector(entity.get('Angles'), '0,0,0'),
                        scale=vector(entity.get('Scale'), '1,1,1')))
                    models[key] = None
        coverage.append(dict(map=int(native_map.get('id')), status='native mission inspected'))
    for path in sorted((client / 'Levels/common').glob('Mesh*.pak')):
        with read_pak(path) as archive:
            for name in archive.namelist():
                key = 'levels/common/' + name.replace('\\', '/').casefold()
                if key not in models or models[key] is not None:
                    continue
                data = archive.read(name)
                parsed = Model(data, materials).document()
                parsed.update(sha256=digest(data), archive=str(path), entry=name)
                models[key] = parsed
        if all(v is not None for v in models.values()):
            break
    require(all(v is not None for v in models.values()), 'Missing native animated models: ' + str([k for k, v in models.items() if v is None]))
    document = dict(format='aion-animated-scene-1', materialsSha256=digest(material_bytes),
                    toolSha256=digest(Path(__file__).read_bytes()), entities=entities, models=models, coverage=coverage)
    if args.verify:
        # JSON object keys are strings, including native numeric material/track IDs.
        # Compare the exact serialized representation that generation writes.
        require(json.loads((output / 'animated-scene.json').read_text(encoding='utf-8')) ==
                json.loads(json.dumps(document, allow_nan=False)),
                'Authored scene differs from the native client or maintained exporter')
        print('PASS: complete authored scene matches native client inputs and maintained exporter')
        return
    temporary = output / 'animated-scene.json.tmp'
    temporary.write_text(json.dumps(document, separators=(',', ':'), allow_nan=False), encoding='utf-8')
    temporary.replace(output / 'animated-scene.json')
    print(f'Exported {len(entities)} authored synchronized entities / {len(models)} models; native phase integration remains separate')


if __name__ == '__main__':
    main()
