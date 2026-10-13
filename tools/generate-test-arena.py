"""Generate the small GameTest NBT fixture with Python's standard library."""
import gzip
import struct
from pathlib import Path

def name(s):
    data = s.encode('utf-8')
    return struct.pack('>H', len(data)) + data
def tag(kind, key, data):
    return bytes([kind]) + name(key) + data
def integer(n):
    return struct.pack('>i', n)
def ints(values):
    return bytes([3]) + integer(len(values)) + b''.join(map(integer, values))
def compound_list(values):
    return bytes([10]) + integer(len(values)) + b''.join(values)

blocks = []
for x in range(16):
    for z in range(16):
        for y in range(7):
            state = 0 if y == 0 or x in (0, 15) or z in (0, 15) else 1
            blocks.append(tag(9, 'pos', ints([x, y, z])) + tag(3, 'state', integer(state)) + b'\0')
root = (tag(3, 'DataVersion', integer(3465)) + tag(9, 'size', ints([16, 7, 16])) +
        tag(9, 'palette', compound_list([tag(8, 'Name', name('minecraft:stone')) + b'\0',
                                       tag(8, 'Name', name('minecraft:air')) + b'\0'])) +
        tag(9, 'blocks', compound_list(blocks)) + tag(9, 'entities', compound_list([])) + b'\0')
out = Path(__file__).resolve().parents[1] / 'forge-addon/src/gametest/resources/data/tacticalnpcai/structures/arena.nbt'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(gzip.compress(b'\x0a\0\0' + root, mtime=0))
print(out)

# A separate original fixture for real weapon-distance navigation, beyond the tiny smoke box.
wide=[]
for x in range(64):
    for z in range(64):
        for y in range(7):
            state=0 if y==0 or x in (0,63) or z in (0,63) else 1
            wide.append(tag(9,'pos',ints([x,y,z]))+tag(3,'state',integer(state))+b'\0')
wide_root=(tag(3,'DataVersion',integer(3465))+tag(9,'size',ints([64,7,64]))+
           tag(9,'palette',compound_list([tag(8,'Name',name('minecraft:stone'))+b'\0',tag(8,'Name',name('minecraft:air'))+b'\0']))+
           tag(9,'blocks',compound_list(wide))+tag(9,'entities',compound_list([]))+b'\0')
out.with_name('wide_arena.nbt').write_bytes(gzip.compress(b'\x0a\0\0'+wide_root,mtime=0))
