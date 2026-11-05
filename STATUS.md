# IT WAS TALL — STAN MODA

Podział: **✅ zrobione w pełni** (jest w kodzie i działa w całości), **🟡 częściowo** (jest, ale brakuje części z MODPLAN), **❌ brak**.
Numery sekcji odnoszą się do `MODPLAN.md`, etapy do `PHASES.md`.

## ✅ Zrobione w pełni

### Postać i główna scena
- **Model, tekstury, 7 animacji GeckoLib** (`idle_stare`, `walk`, `run`, `sink`, `dig`, `knock`, `attack`) + świecące oczy (glowmask), kilof w ręce przy kopaniu. (§2)
- **Scena STARE → CHASE → SINK → jumpscare 0,1 s** (pomiar czasu w nanosekundach, minimum 2 klatki), po niej napis „don't hide. just run.”. (§6)
- **Jeden prawdziwy The Tall One na serwer** (`maxActiveIts = 1`, komenda `spawn` też pilnuje limitu). (§4)
- **Kryształ Odpędzający** (`warding_crystal`): tylko nim można go pokonać; rzadko w skrzyniach podziemnych struktur vanilla.

### Fazy i etapy
- **Faza PRE → AKTYWNA** po 24 000 tickach i w nocy, licznik zapisany w świecie, jednorazowe **IT JOINED** (napis, dźwięk, winieta). (§10, §11)
- **Etapy 0–5 z napięciem 0–100**, awanse tylko w górę, wszystkie systemy czytają etap na żywo; komendy `stage`, `tension`, `phase activate` działają bez restartu. (§5, PHASES)

### Zdarzenia
- **Pukanie w szybę** (`WindowKnocks`): 3 puknięcia słyszane tylko przez gracza; od etapu 4 sylwetka za oknem, po zewnętrznej stronie szyby, głową na wysokości szyby, z animacją `knock`. Spojrzenie przez okno = zadanie. (§12) *Właśnie poprawiony błąd wysokości, patrz niżej.*
- **Kopalnia** (`MineEvents`): kopanie do gracza, dźwięki kopania, gaszenie pochodni, „stoi za tobą”. (§13)
- **Gaszenie świateł w bazie** (`DouseLights`) z mapy bazy, etap 4+. (§14)
- **False presence**: fałszywa sylwetka tylko u jednego gracza, bez hitboxa, znika po spojrzeniu. (§7)
- **Małe straszaki co 20–60 s, ambient zależny od miejsca, 82 własne dźwięki**, napisy na ekranie. (§18)

### Baza
- **BaseTracker / BaseScanner / BaseMap**: kotwica z łóżka lub interakcji, budżet bloków na tick, flood-fill wnętrza, okna (znacznik po zewnętrznej stronie), drzwi, światła, skrzynie, ciemne kąty, historia bloków postawionych przez gracza, zapis w świecie, ponowny skan po wejściu. (§8.1–8.4)

### Lore
- **12 książek** po angielsku, wkładane tylko do wolnych slotów skrzyń bazy, bez duplikatów, zapis w świecie; THE LIST z listą graczy; IT KNOWS dopiero po 11 pozostałych, bez wydarzenia. (§9)
- **Notatki w skrzyniach podziemnych struktur** (MINER'S REPORT, THE BLUE CRYSTAL, LAST EXPEDITION).

### Zadania (§15)
- **Nie wychodź na zewnątrz**, **Zgaś światła**, **Chowany** (przeszukiwanie ciemnych kątów z mapy bazy), **Nie patrz**, **Ofiara**: wszystkie 5, z nagrodą (−8 napięcia) i karą (+5), powiadomieniem i komendą `task`.

### GUI i HUD (§16)
- **Dziennik (J)**: własna tekstura (skórzany notes, pergamin, krew), zakładki-wstążki STATUS / BOOKS / BASE / EVENTS, **bez rozmycia tła**; strona BASE pokazuje prawdziwe dane bazy z serwera, EVENTS pokazuje historię zdarzeń gracza.
- **TaskToast** na teksturze podartego papieru, **FearHud** z dymną winietą (pulsuje przy dużym strachu), **czerwony efekt pościgu** (ChaseHud), ekran WARNING z przełącznikiem straszaków.

### Multiplayer
- Każdy gracz ma własne timery, straszaki, napisy i zadania; dane czyszczone po wyjściu; komendy z wyborem gracza działają też z konsoli. (§20)

### Struktury dynamiczne (§17.1)
- **Cmentarz, kapliczka, chata, czarna kopia bazy**: rzadko, w nocy, 60–100 bloków za plecami gracza, nigdy na oczach innego gracza.

## 🟡 Częściowo

- **AI**: STARE / CHASE / SINK / DIG / PEEK + podchodzenie („stalking”) są, ale osobnych stanów STALK, WATCHED, MOVE_UNSEEN, KNOCK, SEARCH_BASE (§3) nie ma: ich zachowania są wbudowane w STARE albo w zadania.
- **Kopanie** idzie krok po kroku w stronę gracza; prawdziwego **A\* (DigPathfinder)** z §13 nie ma.
- **Debug bazy 3D**: `/itwastall debug view` pokazuje kolorowe ramki w świecie; **obracanej miniatury bazy w GUI** (§8.5) nie ma.
- **Punktacja chunków i łączenie klastrów** w BaseTracker (z notatek MODPLAN) nie są zrobione.
- **NoteScreen** jest gotowy (tekstura pergaminu), ale nic go jeszcze nie otwiera: notatki w strukturach to zwykłe książki.
- **Pukanie w drzwi, cień pod drzwiami, pukanie z wnętrza, rozbijanie szyby** (§12): jest tylko rozbijanie szyby w etapie 5 podczas pościgu.

## ❌ Brak

- **Struktury generowane ze światem** (worldgen: chata z notatkami, kapliczka, tunel, pokój z tabliczkami, §17): brak plików `data/itwastall/worldgen`.
- **DoorEvent, ChestShuffleEvent, SignEvent** (§14).
- **Własna struktura jaskiniowa z gwarantowanym kryształem** (z notatek MODPLAN).

## Ostatnie zmiany (ta sesja), niesprawdzone w grze

- Pukanie: sylwetka przy oknie lądowała na wysokości Y≈−2,5 (`getBodyY()` zwraca wysokość bezwzględną, nie przesunięcie). Teraz oczy są dokładnie na środku szyby, a postać pojawia się od razu na swoim miejscu.
- Dziennik, notatka, TaskToast, winieta, efekt pościgu: nowe tekstury z `tools/gen_gui.py`, wszystkie ekrany bez vanilla-rozmycia (wcześniej rozmycie nakładało się dwa razy).
- Struktury przebudowane: przygotowanie terenu (fundament, czyszczenie z drzew i roślin, płaski teren), chata pusta w środku z dachem dwuspadowym (wcześniej była wypełniona deskami), poprawiona czarna kopia (wcześniej liczyła wymiary ze złych danych). Kopia odwzorowuje teraz bloki postawione przez gracza.
