"""Reproduce the internal SRG library from the unmodified Forge 0.17 release.
Only JVM constant-pool UTF8 entries are transformed; bytecode and stack maps are preserved.
"""
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
import hashlib, json, struct, sys

here = Path(__file__).resolve().parent
resources = here.parents[1] / 'src/main/resources'
source = Path(sys.argv[1])
assert hashlib.sha256(source.read_bytes()).hexdigest() == '54d5d70997413bc63f8ad61938595c803d8efccdde1101afeb9e1484babfd189'

def relocate(s):
    return s.replace(b'com/qouteall/', b'com/inza/twoh/portalcore/').replace(
        b'com.qouteall.', b'com.inza.twoh.portalcore.').replace(
        b'immersive_portals:', b'twoh_rift:').replace(b'imm_ptl:', b'twoh_rift:')

def bytecode(b):
    i, count = 10, int.from_bytes(b[8:10], 'big') - 1
    out = bytearray(b[:10])
    while count:
        tag = b[i]
        out.append(tag)
        i += 1
        if tag == 1:
            n = int.from_bytes(b[i:i+2], 'big')
            value = relocate(b[i+2:i+n+2])
            if value in (b'immersive_portals', b'imm_ptl'):
                value = b'twoh_rift'
            out.extend(struct.pack('>H', len(value)) + value)
            i += n+2
        else:
            n = {3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,18:4,17:4,19:2,20:2}[tag]
            out.extend(b[i:i+n]); i += n
        count -= 2 if tag in (5,6) else 1
    return out+b[i:]

def put(z,name,data):
    zi = ZipInfo(name); zi.compress_type = ZIP_DEFLATED
    z.writestr(zi,data)

with ZipFile(source) as src, ZipFile(here/'twoh-rift-engine-1.1.jar','w') as lib:
    for n in sorted(src.namelist()):
        if n.endswith('.class') and n.startswith(('com/qouteall/immersive_portals/', 'com/qouteall/hiding_in_the_bushes/', 'com/qouteall/forge_model_data_fix/')):
            name = n.rsplit('/',1)[-1].split('$')[0].split('.')[0]
            if name not in ('ModMainForge','O_O','ConfigClient','ConfigServer','MyMixinConnector','MyClientChunkManager','ModMain','ModMainClient'):
                put(lib,relocate(n.encode()).decode(),bytecode(src.read(n)))
        if n in ('imm_ptl_mixins.json','imm_ptl_mixins_forge.json','imm_ptl_mixins_optifine.json','imm_ptl.refmap.json'):
            data=json.loads(relocate(src.read(n)))
            if 'package' in data:
                data['minVersion']='0.8'
                data['refmap']='twoh-rift.refmap.json'
                data['mixins']=[x for x in data.get('mixins',[]) if x!='common.MixinCommandManager' and '.portal_generation.' not in x]
                data['client']=[x for x in data.get('client',[]) if x!='client.MixinEntity_C']
            target = {'imm_ptl_mixins.json':'mixins.twoh-rift.json','imm_ptl_mixins_forge.json':'mixins.twoh-rift.forge.json','imm_ptl.refmap.json':'twoh-rift.refmap.json','imm_ptl_mixins_optifine.json':'mixins.twoh-rift.optifine.json'}[n]
            (resources/target).write_text(json.dumps(data,indent=2)+'\n',encoding='utf8')
        elif n.startswith('assets/immersive_portals/shaders/') and n.endswith(('.glsl','.txt')) and not n.endswith('todo.txt'):
            path=resources/n.replace('assets/immersive_portals/','assets/twoh_rift/')
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(src.read(n))
        elif n=='META-INF/accesstransformer.cfg':
            (resources/n).write_bytes(src.read(n))

