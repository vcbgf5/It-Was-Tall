#!/usr/bin/env python3
"""It Was Tall advancements: the "It Was Tall" tab of the advancements screen (L).

Writes data/itwastall/advancement/*.json and the title/description keys into lang/en_us.json and pl_pl.json.
Everything except the root and the structure ones uses the "minecraft:impossible" trigger and is granted from
code (com.itwastall.advancement.Achievements.grant(player, id)).
Run: python3 tools/gen_advancements.py
"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src/main/resources/data/itwastall/advancement")
LANG = os.path.join(ROOT, "src/main/resources/assets/itwastall/lang")

STRUCTURES = ["abandoned_cabin", "roadside_shrine", "torch_tunnel", "sign_room", "crystal_grotto",
              "deep_lair", "counting_stones", "guest_house", "dream_well"]

# id, parent, icon, frame, hidden, (en title, en desc), (pl title, pl desc)
ADV = [
    ("root", None, "itwastall:lore_book", "task", False,
     ("It Was Tall", "Something is standing at the edge of the forest."),
     ("It Was Tall", "Coś stoi na skraju lasu.")),
    ("first_sighting", "root", "minecraft:ender_eye", "task", False,
     ("It Was There", "Look at It, standing at the edge of the forest"),
     ("Ono tam było", "Spójrz na To, gdy stoi na skraju lasu")),
    ("dont_blink", "first_sighting", "minecraft:spyglass", "task", False,
     ("Don't Blink", "Look away for a moment and let It vanish"),
     ("Nie mrugaj", "Odwróć wzrok na chwilę i pozwól Mu zniknąć")),
    ("survive_chase", "first_sighting", "minecraft:leather_boots", "goal", False,
     ("Run", "Escape a chase by getting under a roof"),
     ("Uciekaj", "Ucieknij przed pościgiem, chowając się pod dachem")),
    ("caught", "survive_chase", "minecraft:wither_skeleton_skull", "task", True,
     ("Face to Face", "See its face. Only for a moment."),
     ("Twarzą w twarz", "Zobacz jego twarz. Tylko przez chwilę.")),
    ("it_joined", "root", "itwastall:tall_one_spawn_egg", "task", False,
     ("IT JOINED", "Something joined this world"),
     ("IT JOINED", "Coś dołączyło do tego świata")),
    ("knock_knock", "it_joined", "minecraft:oak_door", "task", False,
     ("Knock Knock", "Something knocks on your door or window"),
     ("Puk puk", "Coś puka do twoich drzwi albo okna")),
    ("in_the_window", "knock_knock", "minecraft:glass_pane", "task", False,
     ("It Saw You", "Look at the figure in the window"),
     ("Widziało cię", "Spójrz na postać w oknie")),
    ("task_done", "it_joined", "minecraft:writable_book", "task", False,
     ("Do As It Says", "Complete a task"),
     ("Rób, co każe", "Wykonaj zadanie")),
    ("task_stay_inside", "task_done", "minecraft:red_bed", "task", False,
     ("Don't Go Outside", "Complete the task \"Stay inside\""),
     ("Nie wychodź", "Wykonaj zadanie \"Zostań w środku\"")),
    ("task_douse_lights", "task_done", "minecraft:torch", "task", False,
     ("Lights Out", "Complete the task \"Douse the lights\""),
     ("Zgaś światło", "Wykonaj zadanie \"Zgaś światła\"")),
    ("task_hide_and_seek", "task_done", "minecraft:barrel", "goal", False,
     ("Not Found", "Survive hide and seek"),
     ("Nie znalazło", "Przetrwaj zabawę w chowanego")),
    ("task_dont_look", "task_done", "minecraft:carved_pumpkin", "task", False,
     ("Eyes Down", "Complete the task \"Don't look\""),
     ("Oczy w dół", "Wykonaj zadanie \"Nie patrz\"")),
    ("task_sacrifice", "task_done", "minecraft:chest", "task", False,
     ("Offering", "Bring It what It asked for"),
     ("Ofiara", "Przynieś Mu to, o co prosiło")),
    ("task_failed", "task_done", "minecraft:redstone", "task", True,
     ("It Was Not Pleased", "Fail a task"),
     ("Nie było zadowolone", "Nie wykonaj zadania")),
    ("stage_3", "it_joined", "minecraft:clock", "goal", False,
     ("It Gets Closer", "Reach stage 3"),
     ("Coraz bliżej", "Dojdź do etapu 3")),
    ("it_was_tall", "stage_3", "minecraft:wither_rose", "challenge", False,
     ("It Was Tall", "Reach the last stage"),
     ("To było wysokie", "Dojdź do ostatniego etapu")),
    ("counted", "it_joined", "minecraft:cobblestone", "task", False,
     ("Under a Roof", "Survive The One Who Counts"),
     ("Pod dachem", "Przetrwaj Tego, który liczy")),
    ("deep_one", "it_joined", "itwastall:deep_one_spawn_egg", "task", False,
     ("From Below", "The Deep One takes a block out of your hand"),
     ("Z głębin", "Głębinowy zabiera ci blok z ręki")),
    ("guest", "it_joined", "minecraft:player_head", "task", False,
     ("A Guest", "Get close to the player who should not be here"),
     ("Gość", "Podejdź do gracza, którego nie powinno tu być")),
    ("hanged", "it_joined", "minecraft:lead", "task", True,
     ("It Had My Face", "See the one hanging from a tree"),
     ("Miał moją twarz", "Zobacz tego, który wisi na drzewie")),
    ("mimic", "it_joined", "minecraft:leather", "task", True,
     ("That Was Not a Cow", "Get hit by a Mimic"),
     ("To nie była krowa", "Oberwij od Naśladowcy")),
    ("eyes", "it_joined", "minecraft:spider_eye", "task", True,
     ("Something Is Watching", "See the eyes between the trees"),
     ("Coś patrzy", "Zobacz oczy między drzewami")),
    ("old_one", "root", "itwastall:old_one_spawn_egg", "task", False,
     ("The Old One", "Meet a player who is not a player"),
     ("Stary", "Spotkaj gracza, który nie jest graczem")),
    ("old_one_split", "old_one", "minecraft:bone", "goal", True,
     ("Something Climbed Out", "Get too close to the Old One"),
     ("Coś z niego wyszło", "Podejdź za blisko do Starego")),
    ("outspace", "root", "minecraft:black_bed", "task", False,
     ("OUTSPACE", "Fall asleep and wake up somewhere else"),
     ("OUTSPACE", "Zaśnij i obudź się gdzie indziej")),
    ("wake_up", "outspace", "minecraft:torch", "task", False,
     ("Wake Up", "Leave OUTSPACE"),
     ("Pobudka", "Wydostań się z OUTSPACE")),
    ("sleeper", "outspace", "itwastall:sleeper_spawn_egg", "goal", True,
     ("The One in the Bed", "Wake the one who has your face"),
     ("Ten w łóżku", "Obudź tego, który ma twoją twarz")),
    ("first_book", "root", "itwastall:lore_book", "task", False,
     ("Read It", "Add a book to the shared journal"),
     ("Przeczytaj", "Dodaj książkę do wspólnego dziennika")),
    ("librarian", "first_book", "minecraft:bookshelf", "challenge", False,
     ("Librarian", "Collect 10 books in the shared journal"),
     ("Bibliotekarz", "Zbierz 10 książek we wspólnym dzienniku")),
    ("explorer", "root", "minecraft:compass", "task", False,
     ("Something Built This", "Find an It Was Tall structure"),
     ("Ktoś to zbudował", "Znajdź strukturę It Was Tall")),
    ("cartographer", "explorer", "minecraft:filled_map", "challenge", False,
     ("Cartographer of Fear", "Find every It Was Tall structure"),
     ("Kartograf strachu", "Znajdź wszystkie struktury It Was Tall")),
]


def location(structure):
    return {"trigger": "minecraft:location", "conditions": {"player": [{
        "condition": "minecraft:entity_properties", "entity": "this",
        "predicate": {"location": {"structures": "itwastall:" + structure}}}]}}


def main():
    os.makedirs(DATA, exist_ok=True)
    for f in os.listdir(DATA):
        if f.endswith(".json"): os.remove(os.path.join(DATA, f))
    en, pl = {}, {}
    for (aid, parent, icon, frame, hidden, (et, ed), (pt, pd)) in ADV:
        key = "advancements.itwastall." + aid
        en[key + ".title"], en[key + ".description"] = et, ed
        pl[key + ".title"], pl[key + ".description"] = pt, pd
        display = {"icon": {"id": icon}, "title": {"translate": key + ".title"},
                   "description": {"translate": key + ".description"}, "frame": frame,
                   "show_toast": aid != "root", "announce_to_chat": aid != "root", "hidden": hidden}
        adv = {}
        if parent: adv["parent"] = "itwastall:" + parent
        if aid == "root":
            display["background"] = "itwastall:textures/gui/advancements/forest.png"
            adv["display"] = display
            adv["criteria"] = {"joined": {"trigger": "minecraft:tick"}}
        elif aid in ("explorer", "cartographer"):
            adv["display"] = display
            adv["criteria"] = {s: location(s) for s in STRUCTURES}
            if aid == "explorer": adv["requirements"] = [STRUCTURES]
        else:
            adv["display"] = display
            adv["criteria"] = {"done": {"trigger": "minecraft:impossible"}}
        with open(os.path.join(DATA, aid + ".json"), "w", encoding="utf-8") as fh:
            json.dump(adv, fh, indent=2, ensure_ascii=False)
    for name, entries in (("en_us.json", en), ("pl_pl.json", pl)):
        p = os.path.join(LANG, name)
        with open(p, encoding="utf-8") as fh: lang = json.load(fh)
        lang = {k: v for k, v in lang.items() if not k.startswith("advancements.itwastall.")}
        lang.update(entries)
        with open(p, "w", encoding="utf-8") as fh:
            json.dump(lang, fh, indent=2, ensure_ascii=False); fh.write("\n")
    print(len(ADV), "advancements written")


if __name__ == "__main__":
    main()
