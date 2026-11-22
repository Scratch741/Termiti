"""Nasadí vybrané varianty z design/regen do drawable (stejný název, formát a rozměr jako starý soubor;
nový soubor 496×496 PNG). Použití: python tools/deploy_regen.py"""
import os, glob
from PIL import Image
GEN = os.path.expanduser('~/Pictures/Generated/karty')
DRAW = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'app/src/main/res/drawable')

def v(batch, n): return lambda art: f'{art}_{batch}_v{n}.png'
G, GB = 'kampan_goblini', 'kampan_goblini_b'
B, BB = 'kampan_baziny', 'kampan_baziny_b'
H, C, D, O = 'kampan_hory', 'kampan_citadela', 'kampan_draci', 'ostatni_b'
PICK = {
 'goblin_pruzkumnik': v(GB,1), 'goblin_lucistnik': v(G,3), 'goblin_saman': v(GB,1), 'goblin_valecnik': v(G,1),
 'goblin_drancovac': v(G,2), 'goblin_berserk': v(GB,1), 'goblin_troll': v(G,2), 'goblin_velitel': v(GB,1),
 'goblin_valecny_nacelnik': v(G,4), 'goblin_kral': v(G,2), 'goblin_kral_profil': v(G,1), 'goblin_tabor': v(G,3),
 'bazina_zaba': v(B,2), 'bazina_pijavice': v(BB,1), 'bazina_carodejka': v(BB,2), 'bazina_zaklinac': v(BB,1),
 'bazina_alchymista': v(BB,2), 'bazina_jezibaba': v(BB,1), 'bazina_had': v(B,4), 'bazina_bludicka': v(B,1),
 'bazina_druid': lambda a: 'bazina_druid_dark_crop_v1.png', 'bazina_pan_mlhy': v(B,3), 'magicke_baziny': v(B,3),
 'hory_hornik': v(H,1), 'hory_tesar': lambda a: 'hory_tesar_sekera_v1.png', 'hory_strazce': v('hory_strazce_novy',1),
 'hory_kovar': v(H,1), 'hory_ranger': v(H,3), 'hory_bojovnik': v(H,1), 'hory_mag': v(H,2), 'hory_general': v(H,2),
 'hory_kolos': v(H,3), 'hory_thane': v(H,3), 'trpaslici_hory': v(H,3),
 'citadela_rytir': v(C,3), 'citadela_lucistnik': v(C,3), 'citadela_carodej': v(C,2), 'citadela_nekromant': v(C,1),
 'citadela_valecnik': v(C,3), 'citadela_strazce': v(C,1), 'citadela_saboter': v(C,2), 'citadela_drak': v(C,1),
 'citadela_general': v('kampan_opravy',1), 'citadela_pan': v(C,1), 'temna_citadela': v(C,1),
 'draci_strazce': v(D,1), 'draci_saman': v(D,4), 'draci_jezdec': v(D,3), 'draci_saboter': v(D,1), 'draci_wyrm': v(D,4),
 'draci_general': v(D,2), 'draci_jedovaty': v(D,1), 'draci_tyran': v(D,1), 'draci_archon': v(D,1), 'draci_cisar': v(D,3),
 'draci_imperium': v(D,1),
 'art_placeholder_magie': v(O,4), 'art_placeholder_utok': v(O,4), 'art_placeholder_kamen': v(O,3),
 'art_placeholder_chaos': v(O,3), 'art_default': v(O,2),
}
for art, f in PICK.items():
    src = os.path.join(GEN, art, f(art))
    assert os.path.exists(src), src
    old = [p for p in glob.glob(os.path.join(DRAW, art + '.*'))]
    if old:
        dest = old[0]; size = Image.open(dest).size
        if abs(size[0] / size[1] - 1) > 0.01:  # staré záložní arty byly 400×560, karty mají art 512×512
            size = (512, 512)
    else:
        dest = os.path.join(DRAW, art + '.png'); size = (496, 496)
    im = Image.open(src).convert('RGB').resize(size, Image.LANCZOS)
    if dest.endswith('.jpg'): im.save(dest, quality=92)
    elif dest.endswith('.webp'): im.save(dest, quality=90)
    else: im.save(dest, optimize=True)
    print(f'{art:26s} {size} <- {os.path.basename(src)}{"  (NOVÝ)" if not old else ""}')


# ── Ostatní grafika (výběr 2026-11-22): (zdroj v GEN, cíl v drawable, rozměr nebo None = beze změny) ──
A, AC = 'ostatni_a', 'ostatni_c'
EXTRA = [
 ('player_icon_1/player_icon_1_ostatni_a_v2.png', 'player_icon_1.png', (400, 400)),
 ('player_icon_2/player_icon_2_ostatni_c_v1.png', 'player_icon_2.png', (400, 400)),
 ('player_icon_5/player_icon_5_ostatni_a_v3.png', 'player_icon_5.png', (400, 400)),
 ('player_icon_7/player_icon_7_ostatni_a_v2.png', 'player_icon_7.png', (400, 400)),
 ('player_icon_10/player_icon_10_ostatni_a_v2.png', 'player_icon_10.png', (400, 400)),
 ('player_icon_12/player_icon_12_ostatni_a_v2.png', 'player_icon_12.png', (400, 400)),
 ('enemy_icon_1/enemy_icon_1_ostatni_a_v2.png', 'enemy_icon_1.png', (400, 400)),
 ('enemy_icon_2/enemy_icon_2_ostatni_a_v4.png', 'enemy_icon_2.png', (400, 400)),
 # zbylé avatary (dávka avatary_zbytek)
 ('player_icon_3/player_icon_3_avatary_zbytek_v2.png', 'player_icon_3.png', (400, 400)),
 ('player_icon_4/player_icon_4_avatary_zbytek_v3.png', 'player_icon_4.png', (400, 400)),
 ('player_icon_6/player_icon_6_avatary_zbytek_v1.png', 'player_icon_6.png', (400, 400)),
 ('player_icon_9/player_icon_9_avatary_zbytek_v1.png', 'player_icon_9.png', (400, 400)),
 ('player_icon_11/player_icon_11_karty_v3_v1.png', 'player_icon_11.png', (400, 400)),   # karty přemalované na herní (Qwen Edit + card_back_frame)
 ('enemy_icon_3/enemy_icon_3_avatary_zbytek_v3.png', 'enemy_icon_3.png', (400, 400)),
 # nové avatary navíc
 ('player_icon_2/player_icon_2_ostatni_a_v3.png', 'player_icon_13.png', (400, 400)),
 ('player_icon_8/player_icon_8_ostatni_c_v2.png', 'player_icon_14.png', (400, 400)),
 ('player_icon_12/player_icon_12_ostatni_c_v2.png', 'player_icon_15.png', (400, 400)),
 ('enemy_icon_1/enemy_icon_1_ostatni_c_v1.png', 'player_icon_16.png', (400, 400)),
 ('player_icon_3/player_icon_3_avatary_zbytek_v3.png', 'player_icon_17.png', (400, 400)),
 ('player_icon_4/player_icon_4_avatary_zbytek_v2.png', 'player_icon_18.png', (400, 400)),
 ('player_icon_9/player_icon_9_avatary_zbytek_v2.png', 'player_icon_19.png', (400, 400)),
 ('player_icon_11/player_icon_11_karty_v2_v1.png', 'player_icon_20.png', (400, 400)),
 # ruby karet: celá karta, vyříznutá z bílého pozadí (tools/cutout_white.py … 512 832 48 0 1)
 ('card_back_goblin/card_back_goblin_cela_v2_cut.png', 'card_back_goblin.png', None),
 ('card_back_citadela/card_back_citadela_cela_v2_cut.png', 'card_back_citadela.png', None),
 ('card_back_drak/card_back_drak_cela_v1_cut.png', 'card_back_drak.png', None),
 ('card_back_frame/card_back_frame_cela_v2_cut.png', 'card_back_frame.png', None),
 ('card_back_baziny/card_back_baziny_ruby_avatary_v3_cut.png', 'card_back_baziny.png', None),
 ('card_back_trpaslik/card_back_trpaslik_ruby_avatary_v1_cut.png', 'card_back_trpaslik.png', None),
 ('player_icon_8/player_icon_8_ruby_avatary_v1.png', 'player_icon_8.png', (400, 400)),
 # balíček (BiRefNet) a ikona aplikace (zaoblená maska podle bronzového rámu)
 ('pack_art/pack_art_final.png', 'pack_art.png', None),
 ('app_icon/app_icon_final.png', 'app_icon.png', None),
]
for src, dst, size in EXTRA:
    s = os.path.join(GEN, src); assert os.path.exists(s), s
    im = Image.open(s)
    im = im.convert('RGBA' if im.mode in ('RGBA', 'LA', 'P') else 'RGB')
    if size: im = im.resize(size, Image.LANCZOS)
    im.save(os.path.join(DRAW, dst), optimize=True)
    print(f'{dst:26s} {im.size} <- {os.path.basename(s)}')
