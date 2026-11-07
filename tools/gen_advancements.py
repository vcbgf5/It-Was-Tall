#!/usr/bin/env python3
"""It Was Tall advancements: the "It Was Tall" tab of the advancements screen (L).

Writes data/itwastall/advancement/*.json and the title/description keys into lang/en_us.json and pl_pl.json.
Everything except the root and the structure ones uses the "minecraft:impossible" trigger and is granted from
code (com.itwastall.advancement.Achievements.grant(player, id)).
Run: python3 tools/gen_advancements.py
"""
import json, os, importlib.util

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src/main/resources/data/itwastall/advancement")
LANG = os.path.join(ROOT, "src/main/resources/assets/itwastall/lang")

STRUCTURES = ["abandoned_cabin", "roadside_shrine", "torch_tunnel", "sign_room", "crystal_grotto",
              "deep_lair", "counting_stones", "guest_house", "dream_well"]

# id, parent, icon, frame, hidden, (en title, en desc), (pl title, pl desc)
# The game shows the ENGLISH names in every language (pl_pl gets the English text too); the Polish ones stay
# here only as notes.
ADV = [
    ("root", None, "icon:journal", "task", False,
     ("It Was Tall", "Something is standing at the edge of the forest."),
     ("It Was Tall", "Coś stoi na skraju lasu.")),
    ("first_sighting", "root", "icon:moon_eye", "task", False,
     ("It Was There", "Look at It, standing at the edge of the forest"),
     ("Ono tam było", "Spójrz na To, gdy stoi na skraju lasu")),
    ("dont_blink", "first_sighting", "icon:eye_closed", "task", False,
     ("Don't Blink", "Look away for a moment and let It vanish"),
     ("Nie mrugaj", "Odwróć wzrok na chwilę i pozwól Mu zniknąć")),
    ("survive_chase", "first_sighting", "icon:footprints", "goal", False,
     ("Run", "Escape a chase by getting under a roof"),
     ("Uciekaj", "Ucieknij przed pościgiem, chowając się pod dachem")),
    ("caught", "survive_chase", "icon:it_face", "task", True,
     ("Face to Face", "See its face. Only for a moment."),
     ("Twarzą w twarz", "Zobacz jego twarz. Tylko przez chwilę.")),
    ("it_joined", "root", "icon:warning", "task", False,
     ("IT JOINED", "Something joined this world"),
     ("IT JOINED", "Coś dołączyło do tego świata")),
    ("knock_knock", "it_joined", "icon:door", "task", False,
     ("Knock Knock", "Something knocks on your door or window"),
     ("Puk puk", "Coś puka do twoich drzwi albo okna")),
    ("in_the_window", "knock_knock", "icon:window_hand", "task", False,
     ("It Saw You", "Look at the figure in the window"),
     ("Widziało cię", "Spójrz na postać w oknie")),
    ("task_done", "it_joined", "icon:quill", "task", False,
     ("Do As It Says", "Complete a task"),
     ("Rób, co każe", "Wykonaj zadanie")),
    ("task_stay_inside", "task_done", "icon:bed", "task", False,
     ("Don't Go Outside", "Complete the task \"Stay inside\""),
     ("Nie wychodź", "Wykonaj zadanie \"Zostań w środku\"")),
    ("task_douse_lights", "task_done", "icon:lantern_dark", "task", False,
     ("Lights Out", "Complete the task \"Douse the lights\""),
     ("Zgaś światło", "Wykonaj zadanie \"Zgaś światła\"")),
    ("task_hide_and_seek", "task_done", "icon:wardrobe", "goal", False,
     ("Not Found", "Survive hide and seek"),
     ("Nie znalazło", "Przetrwaj zabawę w chowanego")),
    ("task_dont_look", "task_done", "icon:dont_look", "task", False,
     ("Eyes Down", "Complete the task \"Don't look\""),
     ("Oczy w dół", "Wykonaj zadanie \"Nie patrz\"")),
    ("task_sacrifice", "task_done", "icon:chest", "task", False,
     ("Offering", "Bring It what It asked for"),
     ("Ofiara", "Przynieś Mu to, o co prosiło")),
    ("task_failed", "task_done", "icon:skull", "task", True,
     ("It Was Not Pleased", "Fail a task"),
     ("Nie było zadowolone", "Nie wykonaj zadania")),
    ("stage_3", "it_joined", "icon:hourglass", "goal", False,
     ("It Gets Closer", "Reach stage 3"),
     ("Coraz bliżej", "Dojdź do etapu 3")),
    ("it_was_tall", "stage_3", "icon:it_figure", "challenge", False,
     ("It Was Tall", "Reach the last stage"),
     ("To było wysokie", "Dojdź do ostatniego etapu")),
    ("counted", "it_joined", "icon:tally", "task", False,
     ("Under a Roof", "Survive The One Who Counts"),
     ("Pod dachem", "Przetrwaj Tego, który liczy")),
    ("deep_one", "it_joined", "icon:spider", "task", False,
     ("From Below", "The Deep One takes a block out of your hand"),
     ("Z głębin", "Głębinowy zabiera ci blok z ręki")),
    ("guest", "it_joined", "icon:guest", "task", False,
     ("A Guest", "Get close to the player who should not be here"),
     ("Gość", "Podejdź do gracza, którego nie powinno tu być")),
    ("hanged", "it_joined", "icon:noose", "task", True,
     ("It Had My Face", "See the one hanging from a tree"),
     ("Miał moją twarz", "Zobacz tego, który wisi na drzewie")),
    ("mimic", "it_joined", "icon:animal_skull", "task", True,
     ("That Was Not a Cow", "Get hit by a Mimic"),
     ("To nie była krowa", "Oberwij od Naśladowcy")),
    ("eyes", "it_joined", "icon:eyes_dark", "task", True,
     ("Something Is Watching", "See the eyes between the trees"),
     ("Coś patrzy", "Zobacz oczy między drzewami")),
    ("blood_moon", "it_joined", "icon:blood_moon", "goal", False,
     ("Red Night", "Live through a blood moon"),
     ("Czerwona noc", "Przeżyj krwawy księżyc")),
    ("voice", "it_joined", "icon:sound", "task", True,
     ("That Wasn't Them", "Hear a friend's voice from the dark when they are somewhere else"),
     ("To nie był on", "Usłysz z ciemności głos przyjaciela, gdy jest gdzie indziej")),
    ("old_one", "root", "icon:grave", "task", False,
     ("The Old One", "Meet a player who is not a player"),
     ("Stary", "Spotkaj gracza, który nie jest graczem")),
    ("old_one_split", "old_one", "icon:bones", "goal", True,
     ("Something Climbed Out", "Get too close to the Old One"),
     ("Coś z niego wyszło", "Podejdź za blisko do Starego")),
    ("outspace", "root", "icon:static_tv", "task", False,
     ("OUTSPACE", "Fall asleep and wake up somewhere else"),
     ("OUTSPACE", "Zaśnij i obudź się gdzie indziej")),
    ("wake_up", "outspace", "icon:candle", "task", False,
     ("Wake Up", "Leave OUTSPACE"),
     ("Pobudka", "Wydostań się z OUTSPACE")),
    ("sleeper", "outspace", "icon:photo", "goal", True,
     ("The One in the Bed", "Wake the one who has your face"),
     ("Ten w łóżku", "Obudź tego, który ma twoją twarz")),
    ("first_book", "root", "icon:books", "task", False,
     ("Read It", "Add a book to the shared journal"),
     ("Przeczytaj", "Dodaj książkę do wspólnego dziennika")),
    ("librarian", "first_book", "icon:lantern_lit", "challenge", False,
     ("Librarian", "Collect 10 books in the shared journal"),
     ("Bibliotekarz", "Zbierz 10 książek we wspólnym dzienniku")),
    ("explorer", "root", "icon:map", "task", False,
     ("Something Built This", "Find an It Was Tall structure"),
     ("Ktoś to zbudował", "Znajdź strukturę It Was Tall")),
    ("cartographer", "explorer", "icon:compass", "challenge", False,
     ("Cartographer of Fear", "Find every It Was Tall structure"),
     ("Kartograf strachu", "Znajdź wszystkie struktury It Was Tall")),
]


def icon_stack(icon):
    """'icon:<name>' = a picture from the icon pack (tools/gen_iconpack.py), otherwise a plain item id."""
    if not icon.startswith("icon:"):
        return {"id": icon}
    spec = importlib.util.spec_from_file_location("iconpack", os.path.join(ROOT, "tools/gen_iconpack.py"))
    pack = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(pack)
    return {"id": "itwastall:icon", "components": {"minecraft:custom_model_data": pack.cmd(icon[5:])}}


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
        pl[key + ".title"], pl[key + ".description"] = et, ed      # English in game, whatever the language
        display = {"icon": icon_stack(icon), "title": {"translate": key + ".title"},
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
