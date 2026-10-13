import json, sys, copy, os
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../common/src/main/resources/data/proficiency/hearth_designs/designs.json')
LEG = {
 "F":"the fire (the Hearth block itself; row 4, column 4, layer 0 only)",
 ".":"air (smoke column, walkways, doorways)", " ":"anything (not checked)",
 "S":"stone block (#proficiency:hearth/stone)","s":"stone shape: stairs, slabs, walls (#proficiency:hearth/stone_shaped)",
 "W":"log (#minecraft:logs)","P":"planks (#minecraft:planks)","w":"wood shape: stairs, slabs, fences, gates, trapdoors",
 "N":"banner (#minecraft:banners)","i":"iron block","c":"copper block or cut copper, any oxidation, waxed or not",
 "g":"gold block","b":"iron bars","h":"chain","l":"lantern or soul lantern","a":"anvil","u":"cauldron","B":"bell",
 "v":"water source block (a waterlogged block does NOT count)","V":"lava source block (forge and demonic lanes only; never under or beside anything flammable)",
 "f":"flower pot, empty or potted (#minecraft:flower_pots)","x":"storage: chest, trapped chest or barrel",
 "C":"candle, any colour (#minecraft:candles)","m":"carpet (#minecraft:wool_carpets) or moss carpet",
 "G":"glass: any glass, stained or tinted glass block, or pane","z":"skull or head, any",
 "I":"packed ice or blue ice (not plain ice, which melts near the Hearth light)","n":"snow block","q":"moss block",
 "Y":"magma block","O":"obsidian or crying obsidian","L":"leaves, any (player placed, so persistent)",
 "r":"lightning rod","k":"lodestone (stage VI, under the fire)"}
def orbit(x,y):
    o=[];p=(x,y)
    for _ in range(4):
        o.append(p);p=(-p[1],p[0])
    return o
class St:
    def __init__(s,d=None): s.d=dict(d or {})
    def put(s,z,x,y,ch,force=False):
        for p in orbit(x,y):
            s.d[(z,)+p]=ch
    def sym(s,z,x,y,ch):
        s.put(z,x,y,ch); s.put(z,x,-y,ch)
    def ring(s,z,d,ch,axis=True):
        for x in range(-d,d+1):
            for y in range(-d,d+1):
                if max(abs(x),abs(y))==d and (axis or (x!=0 and y!=0)):
                    s.d[(z,x,y)]=ch
def render(st,path_d):
    d=dict(st.d)
    for z in (0,1):
        for k in range(1,path_d+1):
            for p in orbit(k,0):
                assert (z,)+p not in d or d[(z,)+p]=='.',("path blocked",z,p,d.get((z,)+p))
                d[(z,)+p]='.'
    for z in range(1,6): d[(z,0,0)]='.'
    d[(0,0,0)]='F'
    layers={}
    for z in range(-1,6):
        g=[[' ']*9 for _ in range(9)]
        for (zz,x,y),ch in d.items():
            if zz==z: g[4+y][4+x]=ch
        layers[str(z)]=["".join(r) for r in g]
    return layers
def lane(name,ops,paths,palette):
    cur=St(); out=[]
    for i,(f,pd) in enumerate(zip(ops,paths)):
        cur=St(cur.d); f(cur); out.append({"stage":i+1,"layers":render(cur,pd)})
    return {"id":name,"palette":palette,"stages":out}

# ---------- Longhouse: open timber hall, hipped roof in three tiers
def L1(s): s.sym(-1,0,0,'S'); s.ring(-1,1,'S'); s.sym(0,1,1,'W'); s.sym(1,1,1,'l')
def L2(s): s.ring(-1,2,'S'); s.sym(0,2,2,'S'); s.sym(1,2,2,'W'); s.sym(2,2,2,'l'); s.sym(0,2,1,'w')
def L3(s):
    s.ring(-1,3,'S')
    s.sym(0,3,3,'S'); s.sym(0,3,1,'S')   # stone footings so the pools are stone-lined
    for z in (1,2): s.sym(z,3,3,'W'); s.sym(z,3,1,'W')
    s.sym(0,3,2,'v'); s.sym(0,4,2,'S'); s.ring(3,3,'W')
def L4(s): s.ring(-1,4,'s'); s.ring(3,4,'w'); s.ring(4,3,'w')
def L5(s): s.ring(5,2,'w'); s.ring(4,2,'P'); s.sym(2,4,4,'h'); s.sym(1,4,4,'l'); s.sym(1,4,2,'f')
def L6(s): s.d[(-1,0,0)]='k'; s.ring(5,1,'w'); s.ring(4,1,'P'); s.sym(4,4,4,'c'); s.sym(0,4,1,'N'); s.put(0,4,3,'x')
# ---------- Forge: stone smithy, chimney is the signature, lava pits in the corners
# fire rule: planks only on the floor axes (distance >= 3 from every lava pit)
def F1(s): s.sym(-1,0,0,'S'); s.sym(-1,1,1,'S'); s.sym(-1,1,0,'P'); s.sym(0,1,1,'S'); s.sym(1,1,1,'b')
def F2(s): s.ring(-1,2,'S'); s.sym(0,2,1,'S'); s.put(1,2,1,'u')
def F3(s):
    s.ring(-1,3,'S')
    s.sym(0,2,2,'v')                                         # quench pool in each inner corner, benches and walls hold it
    s.sym(0,3,1,'S'); s.sym(0,3,2,'S')
    s.sym(1,3,3,'b'); s.sym(1,3,1,'S'); s.sym(1,3,2,'b')    # grate over the (still empty) corner pit
    s.sym(2,3,3,'S'); s.sym(2,3,1,'S'); s.sym(2,3,2,'S'); s.sym(2,3,0,'s')
    s.ring(2,1,'S'); s.ring(3,1,'S')
def F4(s):
    s.ring(3,3,'s'); s.ring(3,2,'s'); s.sym(3,3,3,'c'); s.ring(4,1,'S')
    s.sym(-1,4,4,'S'); s.sym(0,4,4,'a')
    s.sym(0,3,3,'V'); s.sym(0,4,3,'b')                       # lava pit in each corner, glowing through bars at ground level
def F5(s): s.ring(4,3,'s'); s.sym(5,1,0,'b'); s.put(0,4,1,'u')
def F6(s): s.d[(-1,0,0)]='k'; s.sym(5,1,1,'c'); s.put(0,4,-1,'S'); s.put(1,4,-1,'S'); s.put(2,4,-1,'s')
# ---------- Shrine: open terraced pavilion, wood pillars and roof, eight curbed pools from III
def H1(s): s.sym(-1,0,0,'S'); s.ring(-1,1,'S'); s.sym(0,1,1,'w'); s.sym(1,1,1,'l')
def H2(s): s.ring(-1,2,'S'); s.sym(0,2,1,'s'); s.sym(0,2,2,'S'); s.sym(1,2,2,'w'); s.sym(2,2,2,'c')
def H3(s):
    s.ring(-1,3,'s'); s.sym(-1,3,2,'S'); s.ring(3,2,'w')
    s.sym(0,3,3,'S'); s.sym(0,3,1,'S'); s.sym(0,3,2,'v'); s.sym(0,4,2,'S')
def H4(s): s.sym(1,3,3,'w'); s.sym(2,3,3,'c'); s.ring(3,3,'w'); s.sym(1,2,1,'f')
def H5(s): s.ring(4,2,'w'); s.ring(4,1,'w'); s.ring(-1,4,'s'); s.put(2,3,1,'h'); s.put(1,3,1,'B'); s.put(1,3,-1,'C')
def H6(s): s.d[(-1,0,0)]='k'; s.ring(0,4,'s',axis=False); s.sym(0,4,2,'S'); s.sym(5,1,1,'g'); s.sym(5,1,0,'c'); s.sym(5,2,2,'r'); s.sym(1,4,1,'N')

# ---------- Demonic: nether citadel, lava pits at raised level behind bars, spiky crown
def lava_cells(): return orbit(3,3)+orbit(3,2)
def D1(s): s.sym(-1,0,0,'S'); s.sym(-1,1,1,'S'); s.sym(-1,1,0,'P'); s.sym(0,1,1,'Y'); s.sym(1,1,1,'l')
def D2(s): s.ring(-1,2,'S'); s.put(-1,2,0,'P'); s.sym(0,2,1,'S'); s.sym(1,2,1,'b')
def D3(s):
    s.ring(-1,3,'S'); s.sym(0,2,2,'v'); s.ring(0,3,'S',axis=False)
    lv=set(lava_cells())
    for x in range(-3,4):
        for y in range(-3,4):
            if max(abs(x),abs(y))==3 and x!=0 and y!=0:
                s.d[(1,x,y)]='S' if (x,y) not in lv else None
                s.d[(2,x,y)]='S' if (x,y) not in lv else 'b'
                if s.d[(1,x,y)] is None: del s.d[(1,x,y)]
    s.sym(2,3,0,'s')
def D4(s):
    s.ring(3,3,'s'); s.ring(3,2,'s'); s.sym(3,3,3,'S'); s.sym(4,3,3,'S'); s.sym(1,2,2,'b')
    lv=lava_cells()
    for c in lv: s.d[(1,)+c]='V'
    for (x,y) in lv:
        for n in ((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
            if max(abs(n[0]),abs(n[1]))==4: s.d[(1,)+n]='b'
def D5(s): s.ring(4,2,'s'); s.sym(0,4,1,'O'); s.sym(1,4,1,'l'); s.sym(2,2,0,'l')
def D6(s): s.d[(-1,0,0)]='k'; s.ring(5,2,'w'); s.sym(5,2,2,'b'); s.sym(5,3,3,'z')

# ---------- Gothic: cathedral chapel, corner spires to layer 5, stained glass
def G1(s): s.sym(-1,0,0,'S'); s.sym(-1,1,1,'S'); s.sym(-1,1,0,'P'); s.sym(0,1,1,'S'); s.sym(1,1,1,'l')
def G2(s): s.ring(-1,2,'S'); s.put(-1,2,0,'P'); s.sym(0,2,1,'S'); s.sym(1,2,1,'C')
def G3(s):
    s.ring(-1,3,'S'); s.sym(0,2,2,'v')
    for z in (0,1,2): s.sym(z,3,3,'S')
    s.sym(3,3,3,'s'); s.sym(0,3,1,'S'); s.sym(1,3,1,'W'); s.sym(2,3,1,'W')
    s.sym(0,3,2,'S'); s.sym(1,3,2,'G'); s.sym(2,3,2,'G'); s.sym(2,3,0,'s')
def G4(s):
    s.ring(3,3,'s'); s.ring(4,2,'s'); s.sym(4,3,3,'S')
    s.sym(3,2,2,'h'); s.sym(2,2,2,'l')
    s.sym(0,4,2,'S'); s.sym(1,4,2,'S'); s.sym(2,4,2,'s')
def G5(s): s.sym(5,3,3,'s'); s.sym(3,2,0,'h'); s.sym(2,2,0,'B')
def G6(s): s.d[(-1,0,0)]='k'; s.sym(4,3,0,'S'); s.sym(5,3,0,'r'); s.sym(0,4,1,'N')

# ---------- Girly: pastel garden pavilion, cherry canopy, glass cupola
def Y1(s): s.sym(-1,0,0,'S'); s.ring(-1,1,'S'); s.sym(0,1,1,'w'); s.sym(1,1,1,'l')
def Y2(s):
    s.ring(-1,2,'S'); s.sym(0,2,2,'S'); s.sym(1,2,2,'w'); s.sym(2,2,2,'l'); s.sym(0,2,1,'P')
    s.put(1,2,1,'f'); s.put(1,2,-1,'m')
def Y3(s):
    s.ring(-1,3,'S'); s.sym(0,3,2,'v'); s.sym(0,3,1,'S'); s.sym(0,3,3,'S'); s.sym(0,4,2,'S')
    s.sym(1,3,3,'w'); s.sym(2,3,3,'w'); s.sym(1,3,1,'w'); s.sym(2,3,1,'w'); s.sym(2,3,0,'w')
    s.ring(3,2,'w'); s.sym(1,4,2,'f')
def Y4(s): s.ring(3,3,'w'); s.ring(4,2,'w'); s.sym(2,3,2,'h'); s.sym(1,3,2,'l')
def Y5(s):
    s.ring(4,1,'G'); s.ring(5,1,'w'); s.ring(-1,4,'s'); s.sym(0,4,4,'w'); s.sym(1,4,4,'l'); s.sym(0,4,3,'C')
def Y6(s): s.d[(-1,0,0)]='k'; s.ring(5,2,'w'); s.sym(5,2,2,'g'); s.sym(0,4,1,'N')

# ---------- Ruin: overgrown reclaimed shrine, stepped broken walls, moss, leaves, weathered copper
def R1(s): s.sym(-1,0,0,'S'); s.ring(-1,1,'S'); s.sym(0,1,1,'W'); s.sym(1,1,1,'b')
def R2(s):
    s.ring(-1,2,'S'); s.put(-1,2,0,'P'); s.sym(0,2,2,'S'); s.sym(1,2,2,'S'); s.sym(0,2,1,'S')
    s.put(1,2,1,'f'); s.put(1,2,-1,'q')
def R3(s):
    s.ring(-1,3,'S'); s.sym(0,3,2,'v'); s.sym(0,3,1,'S'); s.sym(0,3,3,'S'); s.sym(0,4,2,'S')
    s.sym(1,3,3,'S'); s.sym(2,3,3,'S'); s.sym(3,3,3,'L'); s.sym(1,3,1,'S'); s.sym(2,3,1,'s')
    s.sym(2,3,0,'c'); s.sym(2,3,2,'W')
def R4(s): s.sym(3,3,1,'W'); s.sym(2,2,2,'S'); s.sym(2,2,0,'s'); s.sym(3,2,2,'q')
def R5(s):
    s.sym(0,4,1,'S'); s.sym(1,4,1,'S'); s.put(2,4,1,'c'); s.sym(1,4,2,'f'); s.ring(-1,4,'s'); s.put(0,4,3,'m')
    s.sym(4,3,1,'s')                                         # broken arch springers rising beside each door
def R6(s): s.d[(-1,0,0)]='k'; s.sym(5,3,1,'S'); s.sym(4,3,0,'c'); s.sym(4,2,2,'s')

# ---------- Frost: spruce lodge on an ice plinth, snow roofs
def T1(s): s.sym(-1,0,0,'S'); s.ring(-1,1,'I'); s.sym(0,1,1,'W'); s.sym(1,1,1,'l')
def T2(s): s.ring(-1,2,'I'); s.sym(0,2,2,'S'); s.sym(1,2,2,'W'); s.sym(2,2,2,'n')
def T3(s):
    s.ring(-1,3,'I'); s.sym(0,3,2,'v'); s.sym(0,3,1,'S'); s.sym(0,3,3,'S'); s.sym(0,4,2,'S')
    for z in (1,2): s.sym(z,3,3,'W'); s.sym(z,3,1,'W')
    s.sym(1,3,2,'G'); s.sym(2,3,2,'P'); s.sym(2,3,0,'W'); s.ring(3,3,'W')
def T4(s): s.ring(3,4,'w'); s.ring(4,3,'n'); s.sym(2,4,4,'h'); s.sym(1,4,4,'l')
def T5(s): s.ring(5,2,'n'); s.ring(4,2,'P'); s.ring(3,1,'S'); s.ring(4,1,'S'); s.ring(5,1,'S')
def T6(s): s.d[(-1,0,0)]='k'; s.sym(-1,4,1,'I'); s.sym(0,4,1,'W'); s.sym(1,4,1,'W'); s.sym(2,4,1,'W'); s.sym(2,4,3,'h')

PAL=lambda **k: k
def pal(S,s,W,P,w,G,N,m,f,C,**extra):
    d={"S":S,"s":s,"W":W,"P":P,"w":w,"G":G,"N":N,"m":m,"f":f,"C":C}
    d.update(extra); return {k:("minecraft:"+v) for k,v in d.items()}
PALS={
 "longhouse":pal("cobbled_deepslate","cobbled_deepslate_slab","spruce_log","spruce_planks","dark_oak_slab","glass","red_banner","red_carpet","potted_fern","candle",c="weathered_cut_copper",x="barrel",h="chain",l="lantern"),
 "forge":pal("deepslate_tiles","deepslate_tile_slab","crimson_stem","crimson_planks","crimson_slab","glass","black_banner","red_carpet","potted_crimson_roots","black_candle",c="cut_copper",x="barrel"),
 "shrine":pal("quartz_block","quartz_slab","cherry_log","cherry_planks","cherry_slab","glass","white_banner","white_carpet","potted_cherry_sapling","white_candle",c="oxidized_cut_copper"),
 "demonic":pal("polished_blackstone_bricks","polished_blackstone_brick_slab","crimson_stem","crimson_planks","crimson_slab","red_stained_glass","black_banner","red_carpet","potted_crimson_fungus","black_candle",Y="magma_block",O="crying_obsidian",z="wither_skeleton_skull",l="soul_lantern"),
 "gothic":pal("deepslate_tiles","deepslate_tile_slab","dark_oak_log","dark_oak_planks","dark_oak_slab","purple_stained_glass","black_banner","black_carpet","potted_wither_rose","purple_candle",z="skeleton_skull",l="soul_lantern"),
 "girly":pal("polished_diorite","polished_diorite_slab","cherry_log","cherry_planks","cherry_slab","pink_stained_glass","pink_banner","pink_carpet","potted_pink_tulip","pink_candle",c="cut_copper",l="lantern"),
 "ruin":pal("cracked_stone_bricks","stone_brick_slab","dark_oak_log","dark_oak_planks","dark_oak_slab","glass","gray_banner","moss_carpet","potted_fern","candle",q="moss_block",L="oak_leaves",c="weathered_cut_copper"),
 "frost":pal("stone_bricks","stone_brick_slab","spruce_log","spruce_planks","spruce_slab","light_blue_stained_glass","light_blue_banner","light_blue_carpet","potted_azure_bluet","light_blue_candle",I="blue_ice",n="snow_block",l="soul_lantern"),
}
P=[1,2,3,4,4,4]
data={"legend":LEG,"lanes":[
 lane("longhouse",[L1,L2,L3,L4,L5,L6],P,PALS["longhouse"]),
 lane("forge",[F1,F2,F3,F4,F5,F6],P,PALS["forge"]),
 lane("shrine",[H1,H2,H3,H4,H5,H6],[1,2,3,3,4,4],PALS["shrine"]),
 lane("demonic",[D1,D2,D3,D4,D5,D6],P,PALS["demonic"]),
 lane("gothic",[G1,G2,G3,G4,G5,G6],P,PALS["gothic"]),
 lane("girly",[Y1,Y2,Y3,Y4,Y5,Y6],P,PALS["girly"]),
 lane("ruin",[R1,R2,R3,R4,R5,R6],P,PALS["ruin"]),
 lane("frost",[T1,T2,T3,T4,T5,T6],P,PALS["frost"])]}
import re
txt=json.dumps(data,indent=1,ensure_ascii=False)
open(OUT,"w").write(txt+"\n")
