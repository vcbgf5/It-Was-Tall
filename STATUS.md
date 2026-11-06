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

### Dokończone w tej sesji
- **Zdarzenia w domu** (`HouseEvents`, §12 + §14): kroki i 3 puknięcia do drzwi (etap 2+), pukanie od środka z ciemnego kąta, **DoorEvent** (drewniane drzwi otwierają się same, gdy nie patrzysz, w etapie 4 mogą trzasnąć), **ChestShuffleEvent** (zamienia kilka slotów w skrzyni, nic nie zabiera), **SignEvent** (czerwona tabliczka przed bazą), **cień pod drzwiami** (etap 4+). Komendy `event doorknock|shadow|knockinside|door|chest|sign`.
- **Notatki**: przedmiot `itwastall:note`, po kliknięciu prawym otwiera się NoteScreen (podarta kartka). Używają go notatki z jaskiń, dynamicznych struktur i nowych struktur.
- **Struktury generowane ze światem** (§17): opuszczona chata, kapliczka, tunel z pochodniami, pokój z tabliczkami, grota z kryształem (**gwarantowany Kryształ Odpędzający**). Pliki NBT, worldgen JSON, tagi biomów i tabele łupów generuje `tools/gen_structures.py`.
- **DigPathfinder (A\*)** (§13): najtańsza droga przez skałę z uwzględnieniem czasu kopania, omija płyny, bedrock, obsydian i bloki ze skrzyniami/piecami; limit węzłów; stary sposób jako zapas.
- **MOVE_UNSEEN** (§3): daleko stojący It potrafi pojawić się bliżej, gdy nie patrzysz — tylko gdy ani stare, ani nowe miejsce nie jest w polu widzenia.
- **Debug bazy 3D** (§8.5): `/itwastall debug view` otwiera obracaną miniaturę bazy z prawdziwych bloków + kolorowe znaczniki, warstwy 1–5, przekrój [ ], legenda i ostatnie zdarzenia. `/itwastall debug base` pokazuje też ramki w świecie.

- **Old One („Skinwalker Steve”)**: postać wyglądająca jak gracz Steve (tekstura Steve'a z gry + nasza warstwa: puste oczy, rozciągnięte usta, krew, mięso w środku), z nickiem „Old One” nad głową. Pojawia się 8–12 bloków od ciebie, poza polem widzenia, i się gapi; skrada się tylko, gdy nie patrzysz. Gdy podejdziesz (≤ 4,5 bloku) albo go uderzysz: ciało pęka wzdłuż środka od głowy do stóp, dwie połowy upadają na boki (w lewo i w prawo), a spomiędzy nich wychodzi It (nowa animacja `emerge`) i od razu cię goni. Od etapu 2, co 20–40 min na gracza, tylko gdy na serwerze nie ma innego It. Komenda `event oldone`, jajo spawnujące, config `oldOne*`, podgląd `tools/build_old_one_preview.py`.

### Dokończone w tej sesji (2)
- **Stany AI jako osobne stany** (§3): IDLE, STARE, STALK, WATCHED, MOVE_UNSEEN, TREE, FEINT, KNOCK, CHASE, ATTACK, SINK, DIG, PEEK, EMERGE, DISAPPEAR. Nowe stany są dopisane na końcu enuma, więc zapisane światy działają (pilnuje tego GameTest). DISAPPEAR znika dopiero, gdy na niego nie patrzysz (najwyżej 3 s).
- **Fałszywy atak** (FEINT, etap 3+): krzyk, bieg prosto na ciebie, a 3 bloki przed tobą znika w dymie. Nie zadaje obrażeń. Config `feints`, `feintChance`, komenda `event feint`.
- **Za drzewem** (TREE, etap 2+): wychyla się zza pnia (widać głowę i ramię). Kiedy go zauważysz, cofa się za pień i znika. Config `treePeeks`, `treePeekChance`, komenda `event tree`.
- **Prawdziwe pukanie** (KNOCK, etap 5): czasem pod drzwiami stoi prawdziwy It. Otworzysz, to goni. Config `realKnockChance`, komenda `event realknock`.
- **Jeden It — zapis w świecie** (§4): It zapisuje się w danych świata co sekundę. Limit liczy też Ita w niezaładowanym chunku, a duplikat (np. po restarcie) sam się usuwa. Wpis bez odświeżenia przez 10 min przestaje blokować. `debug entity forget` czyści wpis.
- **Config** (§19): `photosensitiveMode` (bez błysków, ciemne przejście zamiast zdjęcia, bez pulsowania winiety i pościgu, bez migającej klatki w IT JOINED), `effectsVolume`, `minEventCooldown` (wspólna przerwa między większymi zdarzeniami jednego gracza), `maxEventRange`, `debugAllowedForNonOps`.
- **Komendy** (§21): `/itwastall debug entity` (gdzie jest It, stan, za kim idzie, wpis w świecie), `/itwastall debug events [gracz]` (wszystkie timery i cooldowny).
- **Punktacja chunków i klastry** (notatki §8): chunk dostaje punkty za postawione bloki (skrzynie, piece, drzwi, szkło, światła ważą więcej) i za czas spędzony w nim. Sąsiednie mocne chunki łączą się w klastry. Bez łóżka najsilniejszy klaster staje się bazą, a baza przenosi się, gdy inny klaster jest wyraźnie silniejszy. Łóżko zawsze wygrywa. `debug base` pokazuje klastry.
- **Inteligentne wykrywanie bazy + skan co 1 s**: bazą jest tylko miejsce z TWOIMI rzeczami (łóżko, drzwi, okna, skrzynie, piec/stół). Liczą się bloki postawione przez ciebie albo stojące poza wygenerowaną strukturą. Dom villagera, loch czy ruiny nigdy nie są bazą: kliknięcie łóżka w wiosce nie ustawia bazy, a skaner nie zaznacza tam okien ani drzwi. Bez łóżka bazą zostaje klaster chunków z „zestawem” (łóżko + 1 rzecz albo 3 z: skrzynia, piec/stół, drzwi, okno), więc sama ziemia i sam czas spędzony w miejscu nie wystarczą. Stare bazy sprzed instalacji moda wyłapuje lekka sonda wokół gracza. Skan kończy się w około 1 s i powtarza co `baseRescanSeconds` (1 s), dopóki jesteś w promieniu 64 bloków. Nowa mapa podmienia starą dopiero po skończeniu skanu, więc nie miga pusta. Config `scanMaxBlocksPerTick` (3000).
- **Mapa okolicy (poza bazą)**: gdy nie masz bazy albo jesteś dalej niż 48 bloków od niej (np. w wiosce), mod bierze chunk w promieniu 3 chunków, w którym spędzasz najwięcej czasu, i co 5 s skanuje wokół niego okna, drzwi, światła, skrzynie i ciemne kąty, także w domach villagerów. Drzwi same się otwierają, puka w okno, gaśnie światło i działają zadania również tam. To nigdy nie staje się twoją bazą. `debug base` pokazuje mapę okolicy.
- **Zadania działają wszystkie**: jeśli w danym miejscu nie da się wykonać wylosowanego zadania (brak lamp, kątów czy skrzyni), mod daje inne, na które pozwala etap. Chowany w jasnej bazie przeszukuje pokoje zamiast samych ciemnych kątów. Komendy `/itwastall task ...` działają na każdym etapie i zastępują trwające zadanie.
- **Spojrzenie na postać w oknie** = zadanie „nie wychodź” na 30 s (`taskWindowLookSeconds`), także w wiosce.
- **„Nie wychodź na zewnątrz”**: zaliczone, gdy jesteś w bazie (w zmapowanym pokoju albo przy łóżku) LUB w zabudowanym miejscu (dach i ściany). Masz 3 s zapasu na przejście przez drzwi. To samo dotyczy fazy chowania w Chowanym.
- **Nasze własne książki + wspólny journal**: wszystkie książki w modzie to teraz nasz przedmiot „Strange Book” (`itwastall:lore_book`, własna ikona) zamiast vanillowej książki. Dotyczy 12 książek lore i książek ze struktur. Klikasz prawym, czytasz, a książka trafia do zakładki BOOKS w journalu WSZYSTKICH graczy na serwerze (z informacją, kto ją znalazł, i wiadomością na czacie). Notatki działają tak samo. W BOOKS klikasz tytuł, żeby przeczytać, X wraca do journala. Licznik „Lore x / 12”, strony z < >. Zapisane w świecie.
- **Historia zadań w journalu**: każde zadanie (przydzielone / zrobione / nieudane / anulowane) zapisuje się w świecie i pokazuje na stronie STATUS („Earlier”).
- **Zakładka NOTES**: tablica ogłoszeń dla całego serwera. Piszesz notatkę (do 200 znaków, Enter albo „pin it”), każdy widzi ją w journalu, a swoją możesz usunąć „x” (operator może usunąć każdą). Zapisane w świecie, z ochroną przed spamem.
- **Multiplayer (też LAN / Essential)**: gracz po wejściu od razu dostaje cały stan (strach, etap, zadanie, journal, książki, notatki), a po 2 s jeszcze raz na wypadek, gdyby klient się jeszcze ładował. Każda wysyłka w obie strony jest zabezpieczona (`canSend`), serwer sprawdza wszystko, co przychodzi od klienta, a kod serwera nie odwołuje się do klas klienta.
- **OUTSPACE — świat snu** (nowy wymiar `itwastall:outspace`):
  - nieskończony labirynt generowany własnym generatorem chunków: pokoje 5×5 i 5 bloków wysokości, podłoga i sufit z bruku (czasem omszałego), ściany z dębowych desek, słupy z dębowego pnia, przejścia szerokości 2, czasem duże sale, pochodnie na ścianach;
  - od etapu 5, gdy ktoś kładzie się spać, raz na noc jedna osoba na serwer (35 %) zostaje wciągnięta na 30 s;
  - nic nie zadaje obrażeń, nie da się umrzeć i nic nie przepada;
  - Old One (dalej ze skinem Steve'a z gry) w trybie snu nie rozrywa się, tylko biegnie (nowa animacja `run`); gdy cię dopadnie: jumpscare i budzisz się w łóżku;
  - po 30 s budzisz się sam (5 s przed końcem napis „wake up.”); w niektórych pokojach skrzynie: książki snu (trafiają do wspólnego journala), pochodnie, rzadko Kryształ Odpędzający, a znalezione rzeczy zostają;
  - kto wyjdzie z gry w trakcie snu, po powrocie budzi się w domu;
  - komendy `/itwastall outspace [gracz]` i `/itwastall outspace wake [gracz]`, config `outspace*`, GameTest `outspaceMazeIsClosed`.
- **Nowe potwory** (każdy ma książkę-podpowiedź w nowej strukturze):
  - **Ten, Który Liczy** (etap 2+, noc, sam na otwartym terenie): na ekranie odliczanie od „ten.” do „one.” z szeptem, a z każdą liczbą sylwetka stoi bliżej. Ratuje dach przed zerem albo bezruch przez całe liczenie. Przy zerze jumpscare, a od etapu 4 może przyjść prawdziwy It. `/itwastall event count`.
  - **Głębinowy** (etap 3+, pod ziemią poniżej Y=0): nowy model GeckoLib (`tools/gen_deep_one.py`). Pełza do góry nogami po suficie jaskini i znika, gdy spojrzysz prosto; widać go tylko kątem oka. Gasi pochodnie za plecami, słyszy bieg i chodzenie, ale nie skradanie. Gdy cię dopadnie, blok z ręki wypada na ziemię. `/itwastall event deepone`, jajo spawnujące.
  - **Gość** (etap 4+): obcy nick dołącza do TWOJEJ listy graczy („guest” albo ktoś z journala, kogo nie ma online) i powtarza na czacie twoje stare wiadomości. Potem stoi daleko jako zwykły gracz, tyłem. Gdy podejdziesz: „left the game”, a w twojej skrzyni leży notatka. `/itwastall event guest`.
  - **Śpiący** (świat snu): w dużych salach labiryntu leży w łóżku postać z twarzą tego, kto patrzy (twój skin). Cicho, na kucaka, przejdziesz obok. Bieg, skok albo otwarcie skrzyni ją budzi, a wtedy Old One ze snu przyspiesza i przeskakuje bliżej. Przy łóżku skrzynia z Kryształem Odpędzającym i dywan z wełny. `/itwastall event sleeper`, jajo spawnujące.
- **4 nowe struktury** (`tools/gen_structures.py`):
  - Głębinowa nora (pod ziemią: sculk, ścieżka z wełny, porzucone bloki);
  - Krąg liczących kamieni (10 słupów z numerami);
  - Dom gościa (dom dla dwóch z listą graczy na ścianie);
  - Studnia snów (sucha studnia z łóżkiem na dnie).
  W każdej skrzynia z naszą książką-podpowiedzią (THE DEEP ONE, THE COUNT, A GUEST, THE SLEEPER).
- **Nowe UI (wbudowany pakiet „It Was Tall UI”)**:
  - wszystkie przyciski, suwaki, pola tekstowe i checkboxy Minecrafta w stylu moda: ciemna skóra z przeszyciem, czerwona obwódka po najechaniu, checkbox z journala;
  - logo „IT WAS TALL” z krwią i podpisem „SOMETHING TALL IS WATCHING.”;
  - menu główne: zamiast panoramy rozmyta nocna polana z Tall One (ostre oczy), powolne „oddychanie”, winieta;
  - ekran WARNING na kartce z checkboxem i przyciskami ACCEPT TERMS / QUIT;
  - pakiet można wyłączyć w Resource Packs, a nowe menu główne w configu (`customTitleScreen`); tekstury generuje `tools/gen_ui.py`.
- **Naprawy (zdarzenia, pościg, struktury)**:
  - It z komendy i jajka zawsze goni (wcześniej przed IT JOINED stał zamrożony);
  - It i wszystkie sylwetki zawsze patrzą na gracza;
  - komendy testowe działają za każdym razem;
  - limit jednego Ita nie blokuje już na długo;
  - luźniejsze limity zdarzeń (config v2 sam aktualizuje stare pliki);
  - struktury generują się częściej, `/itwastall event build <nazwa>` stawia każdą przed graczem, a 3 nowe naziemne pojawiają się też nocą w starych światach.
- **GameTesty** (§24.2): osobny source set `src/gametest` (nie trafia do jara), uruchamiane przez `./gradlew runGametest`. Testują schronienie, zapis mapy bazy, A* omijający obsydian, jeden It, zapis stanu świata, klastry chunków i stałe numery stanów AI.

## 🟡 Częściowo

- **SEARCH_BASE** (It wchodzi do bazy i przeszukuje pokoje, etap 5) — nie zrobione, świadomie pominięte w tej turze.
- **„Stoi na końcu korytarza/tunelu”** (§3.2) — nie zrobione.

## ❌ Brak

- Testy w grze i balans. GameTesty są napisane, ale jeszcze nie były uruchomione.

## Ostatnie zmiany (ta sesja), niesprawdzone w grze

- Pukanie: sylwetka przy oknie lądowała na wysokości Y≈−2,5 (`getBodyY()` zwraca wysokość bezwzględną, nie przesunięcie). Teraz oczy są dokładnie na środku szyby, a postać pojawia się od razu na swoim miejscu.
- Dziennik, notatka, TaskToast, winieta, efekt pościgu: nowe tekstury z `tools/gen_gui.py`, wszystkie ekrany bez vanilla-rozmycia (wcześniej rozmycie nakładało się dwa razy).
- Struktury przebudowane: przygotowanie terenu (fundament, czyszczenie z drzew i roślin, płaski teren), chata pusta w środku z dachem dwuspadowym (wcześniej była wypełniona deskami), poprawiona czarna kopia (wcześniej liczyła wymiary ze złych danych). Kopia odwzorowuje teraz bloki postawione przez gracza.
- UI: guziki bez jasnego szwu, ciemne separatory i tła list, czerwone zaznaczenie wiersza, tło lasu na każdym ekranie poza grą (`ScreenMixin`, `MenuBackground`).
- Osiągnięcia: zakładka „It Was Tall” pod L, 29 osiągnięć (`tools/gen_advancements.py`, `Achievements.grant`), własne okno, zakładki i toast. Przycisk „< Journal” na ekranie L i wstążka AWARDS w dzienniku.
- TASK TIMER w lewym górnym rogu (`TaskTimerHud`, `TaskTimerPayload`, opcja `taskTimerHud`). Skrzynia na ofiarę świeci przez ściany (`TaskTargetRenderer`).
- Ekran ładowania (pasek z krwi i wskazówki) i pauza (napis PAUSED, przycisk Journal, etap i zadanie).
- Zadania: trwające zadanie tickuje się zawsze (także z komendy, w kreatywnym, na niskim etapie; poza Overworldem stoi w pauzie). „Don't go outside” = 120 s na wejście do domu (GET INSIDE), potem 10 s w środku (STAY INSIDE). Chowany: sylwetka It w każdym sprawdzanym rogu, opis „corner N/M”. Spojrzenie na postać w oknie działa przez szybę.
- Ofiara: It żąda losowej rzeczy z ekwipunku gracza (nie więcej, niż ma), skrzynia liczy obie połowy podwójnej skrzyni, nie da się jej zniszczyć w trakcie zadania, ma kłódkę i czerwoną strzałkę nad sobą (widać przez ściany).
- WISIELEC (`HangedEntity`, `HangedTrees`, `HangedRenderer`): nocą od etapu 2 na gałęzi drzewa 20–45 bloków przed graczem wisi postać z jego skinem, kołysze się na linie, gałąź skrzypi, głowa obraca się do gracza tylko gdy ten nie patrzy; wisi na zawsze (znika tylko po ścięciu gałęzi), kolejne nie usuwają poprzednich. Test: `/itwastall event hanged`. Osiągnięcie „It Had My Face”.
- Pieniek do rąbania: nowe bloki `itwastall:chopping_stump` (pień z wbitą siekierą i krwią) i `itwastall:blood` (plama krwi na ziemi, bez kolizji). Scena `DynamicStructures.Kind.CHOPPING_BLOCK` (nocą, 60–100 bloków za graczem): pieniek, krew wokół, ślad w las, obok głowa gracza (player head z jego skinem). Test: `/itwastall event chopping` (przed tobą), `/itwastall event structure choppingblock`.
- Nocne sceny (część nocnych struktur, 60–100 bloków za graczem; test: `/itwastall event scene camp|missing|circle|chopping|footprints`):
  - pusty obóz: palące się ognisko, posłanie, kłoda, skrzynia z kopiami rzeczy gracza, ślady butów (`itwastall:footprint`) w las, które się urywają;
  - kartki „HAVE YOU SEEN <nick>?” na drzewach (woskowane tabliczki), głębiej w las więcej i dziwniejsze;
  - krąg martwych zwierząt (`CarcassEntity`: krowa/owca/świnia na boku, łbami do środka) wokół `itwastall:eye_stone`;
  - wielkie ślady (`TallFootprints`, `itwastall:tall_footprint`): nocą, gdy gracz jest w bazie, ślady z ciemności pod drzwi i pod okna, słychać kroki; znikają za dnia.
- OUTSPACE: ekwipunek sprzed snu wraca po przebudzeniu (postawione bloki i zużyte rzeczy nic nie kosztują), a rzeczy znalezione we śnie zostają z graczem (`DreamInventory`, zapis w `ItWasTallState`, działa też po wyjściu z gry i restarcie).
