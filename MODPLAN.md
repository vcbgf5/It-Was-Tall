# IT WAS TALL — KOMPLETNY PLAN MODA
## Pełna specyfikacja dla Claude Code

**Nazwa:** It Was Tall  
**Mod ID:** `itwastall`  
**Pakiet Java:** `com.itwastall`  
**Katalog projektu:** `/home/user/it-was-tall`  
**Minecraft:** Fabric 1.21.1  
**Java:** 21  
**Loader:** Fabric  
**Biblioteka modelu i animacji:** GeckoLib 4.x  
**Główna postać:** The Tall One (nazwa wyświetlana: `It`)

> WAŻNE: przed rozpoczęciem sprawdź rzeczywistą strukturę projektu. Jeśli w repozytorium istnieją już inne nazwy pakietów, pliki lub systemy, dostosuj implementację do aktualnego kodu. Nie zmieniaj Mod ID ani nazwy projektu bez zgody użytkownika.

---

# 1. GŁÓWNA KONCEPCJA

It Was Tall to rozbudowany horror mod do Minecrafta.

W świecie pojawia się tajemnicza, wysoka postać, która obserwuje graczy, poznaje ich bazy, pojawia się w kopalniach, puka w szyby i drzwi, organizuje zasadzki oraz czasami rozpoczyna pościg.

Gracz ma mieć wrażenie, że coś znajduje się w jego świecie, obserwuje jego działania i stopniowo poznaje jego otoczenie.

Najważniejsze elementy moda:

- własny model The Tall One;
- animacje GeckoLib;
- zaawansowane AI;
- sześć etapów eskalacji;
- istniejący system faz;
- maksymalnie jeden prawdziwy The Tall One na serwerze;
- mechanika obserwowania inspirowana Weeping Angel;
- fałszywe pojawienia się;
- wykrywanie i mapowanie baz;
- pukanie w okna i drzwi;
- zasadzki w kopalniach;
- książki z lore w skrzyniach;
- specjalne wydarzenie `IT JOINED`;
- zadania;
- własny dziennik i HUD;
- struktury świata;
- dynamiczne wydarzenia;
- obsługa multiplayer;
- konfiguracja i system debugowania.

## Najważniejsze zasady

1. Najpierw przeanalizuj istniejący kod.
2. Nie twórz drugich systemów, jeśli odpowiednie rozwiązania już istnieją.
3. Nie usuwaj działających funkcji.
4. Nie zmieniaj istniejącego systemu faz.
5. Nie zastępuj prawdziwej implementacji placeholderami.
6. Wszystkie ważne dane muszą być zapisywane.
7. Wszystkie ważne decyzje dotyczące prawdziwego The Tall One podejmuje serwer.
8. Dbaj o wydajność.
9. Implementuj projekt etapami i testuj każdą większą funkcjonalność.
10. Nie oznaczaj funkcji jako ukończonej, jeśli jest tylko częściowo zaimplementowana.

---

# 2. THE TALL ONE — GŁÓWNA POSTAĆ

## 2.1 Model i wygląd

Stwórz własny model postaci GeckoLib.

Wygląd:
- bardzo wysoka i szczupła sylwetka;
- długi tułów;
- długie ręce sięgające kolan;
- wydłużona głowa;
- ciemna, niemal czarna skóra;
- brak normalnej twarzy;
- dwa delikatnie świecące punkty zamiast oczu;
- niepokojąca sylwetka widoczna z daleka.

Nie używaj stockowych modeli ani przypadkowych tekstur.

## 2.2 Hitbox i atrybuty

Początkowe wartości:

- szerokość: 0.7 bloku;
- wysokość: 3.4 bloku;
- zdrowie: 80 HP;
- obrażenia: 7;
- prędkość pościgu: 0.42.

Wartości powinny być konfigurowalne.

Postać nie może despawnować się samoczynnie, kiedy jest aktywna.

## 2.3 Animacje

Wymagane animacje GeckoLib:

- `idle_stare` — stanie i obserwowanie;
- `walk` — powolny chód;
- `run` — szybki, pochylony bieg;
- `sink` — zapadanie się pod ziemię;
- `dig` — kopanie;
- `knock` — pukanie;
- `attack` — atak.

Animacja `idle_stare` powinna mieć subtelne kołysanie ciała i ruch głowy śledzącej gracza.

## 2.4 Wykorzystywanie przedmiotów

Podczas kopania The Tall One może trzymać żelazny kilof.

Przedmiot powinien być renderowany jako trzymany przedmiot, a animacja `dig` powinna uwzględniać ruch ręki.

---

# 3. SYSTEM AI THE TALL ONE

Zaimplementuj maszynę stanów AI.

Proponowane stany:

- `IDLE` — brak aktywności;
- `STARE` — obserwowanie gracza;
- `STALK` — śledzenie z dystansu;
- `WATCHED` — zatrzymanie, gdy gracz patrzy;
- `MOVE_UNSEEN` — poruszanie się poza polem widzenia;
- `CHASE` — pościg;
- `SINK` — zapadanie się;
- `DIG` — kopanie;
- `KNOCK` — pukanie;
- `ATTACK` — atak;
- `DISAPPEAR` — zniknięcie;
- `SEARCH_BASE` — przeszukiwanie bazy.

Każdy stan musi mieć:
- warunki rozpoczęcia;
- warunki zakończenia;
- czas trwania lub warunek zakończenia;
- zabezpieczenie przed nieprawidłowym przechodzeniem między stanami;
- odpowiednią synchronizację klient-serwer.

## 3.1 Mechanika obserwowania

Mechanika inspirowana Weeping Angel.

Działanie:

1. The Tall One pojawia się w pobliżu gracza.
2. Gdy gracz patrzy bezpośrednio na postać, ta się zatrzymuje.
3. Kiedy gracz odwraca wzrok, postać może się poruszać.
4. The Tall One może stopniowo podchodzić bliżej.
5. Gdy gracz ponownie spojrzy, postać zastyga.

Sprawdzaj:
- kierunek patrzenia;
- pole widzenia;
- odległość;
- raycast;
- przeszkody między graczem a postacią.

Nie pozwalaj postaci na widoczne teleportowanie się przed graczem.

## 3.2 Losowe zachowania

Nie każde spotkanie ma kończyć się atakiem.

Możliwe zachowania:
- stoi i obserwuje;
- powoli podchodzi;
- znika za drzewem;
- stoi na końcu tunelu;
- pojawia się za szybą;
- stoi za graczem;
- rozpoczyna pościg;
- wykonuje fałszywy atak;
- wydaje dźwięki bez pojawiania się;
- znika bez żadnego wyjaśnienia.

Zachowania powinny być losowane z uwzględnieniem aktualnego etapu eskalacji.

---

# 4. JEDEN PRAWDZIWY THE TALL ONE NA SERWERZE

Na całym serwerze może istnieć maksymalnie **jeden prawdziwy The Tall One**.

Wymagania:

- serwer zarządza istnieniem postaci;
- przed utworzeniem encji sprawdza, czy już istnieje;
- stan jest zapisywany w danych świata;
- nie ma duplikatów po restarcie;
- prawidłowo obsługuje usunięcie encji;
- uwzględnia zmianę wymiaru;
- obsługuje niezaładowane chunki;
- synchronizuje stan z klientami.

Fałszywe pojawienia się opisane w dalszej części nie są prawdziwymi encjami i nie podlegają temu ograniczeniu.

---

# 5. SZEŚĆ ETAPÓW ESKALACJI

Dodaj `Director` i `Stage`.

System posiada dokładnie sześć etapów eskalacji: od 0 do 5.

**Nie są to fazy Minecrafta.** To osobny system stopniowania intensywności wydarzeń.

Napięcie rośnie w zależności od:
- czasu gry;
- nocy;
- przebywania w ciemności;
- wydarzeń;
- niewykonanych zadań;
- spotkań z The Tall One.

Napięcie może spadać po wykonaniu zadań i zakończeniu niektórych wydarzeń.

Próg napięcia może powodować przejście na kolejny etap.

## ETAP 0 — CISZA

<badge>0 — CISZA</badge>

Pierwsze niepokojące wydarzenia.

Możliwe efekty:
- kroki za plecami;
- szepty;
- oddech;
- szelesty;
- dziwne dźwięki;
- krótkie odgłosy dochodzące z pobliskich pomieszczeń.

Na tym etapie The Tall One nie musi być widoczny.

Dźwięki powinny być odtwarzane tylko dla odpowiedniego gracza.

## ETAP 1 — OBSERWATOR

<badge>1 — OBSERWATOR</badge>

The Tall One zaczyna pojawiać się w oddali.

Zachowanie:
- pojawia się około 40–60 bloków od gracza;
- stoi nieruchomo;
- obserwuje gracza;
- może znikać po dłuższym spojrzeniu lub podejściu;
- nie rozpoczyna regularnych pościgów.

Głównym celem jest stworzenie wrażenia, że gracz jest obserwowany.

## ETAP 2 — ZBLIŻENIE

<badge>2 — ZBLIŻENIE</badge>

The Tall One zaczyna pojawiać się bliżej.

Zachowanie:
- pojawia się około 20–30 bloków od gracza;
- może stać na skraju lasu;
- może pojawiać się za oknem;
- zaczyna się skanowanie bazy;
- pojawiają się pierwsze puknięcia w szyby;
- może pojawić się pierwsza fałszywa obecność.

**Uwaga:** aktywacja The Tall One przez istniejący system `Phase 2` jest osobnym warunkiem. Nie utożsamiaj `Stage 2` z `Phase 2`.

## ETAP 3 — KONTAKT

<badge>3 — KONTAKT</badge>

The Tall One zaczyna ingerować w otoczenie.

Możliwe wydarzenia:
- gaszenie pochodni;
- otwieranie drzwi;
- manipulowanie wybranymi skrzyniami;
- pojawianie się tajemniczych tabliczek;
- dźwięki kopania w kopalniach;
- zadanie `Nie wychodź na zewnątrz`;
- zadanie `Zgaś światła`.

Zasadzki w kopalni mogą na tym etapie występować jako wydarzenia dźwiękowe.

## ETAP 4 — ŁOWCA

<badge>4 — ŁOWCA</badge>

To etap głównej sceny horrorowej.

W tym etapie:
- The Tall One może stać i obserwować gracza;
- gdy gracz podejdzie, może rozpocząć pościg;
- może wystąpić scena zapadania się pod ziemię;
- po chwili może nastąpić jumpscare;
- zasadzki w kopalni mogą być fizyczne;
- pojawia się zadanie `Nie patrz`.

Główna scena została opisana w sekcji 6.

## ETAP 5 — POLOWANIE

<badge>5 — POLOWANIE</badge>

Najbardziej intensywny etap eskalacji.

Możliwe zachowania:
- The Tall One może rozbijać szyby;
- może wchodzić do bazy;
- może przeszukiwać pomieszczenia;
- może walczyć z graczem;
- może rozpocząć zadanie `Chowany`;
- może pojawić się czarna kopia bazy w pobliżu.

Nie oznacza to stworzenia nowej fazy świata.

## Zasady systemu etapów

- Etapy mają identyfikatory `0–5`.
- Aktualny etap jest zapisywany w danych świata.
- System powinien działać w multiplayerze.
- Komenda testowa: `/itwastall stage <0-5>`.
- Nie resetuj etapu po ponownym uruchomieniu serwera.
- Zmiana etapu nie powinna automatycznie niszczyć lub usuwać aktywnych wydarzeń.

---

# 6. GŁÓWNA SCENA HORROROWA — STARE → CHASE → SINK → JUMPSCARE

Jedna z najważniejszych scen moda.

## 6.1 STARE

The Tall One pojawia się około 25–40 bloków od gracza.

Warunki:
- gracz znajduje się poza bazą;
- miejsce umożliwia obserwowanie;
- postać ma odpowiednią przestrzeń;
- spełnione są warunki wydarzenia.

Postać:
- stoi nieruchomo;
- obserwuje gracza;
- śledzi go głową;
- wydaje cichy, niepokojący dźwięk.

## 6.2 CHASE

Pościg może rozpocząć się, gdy:
- gracz znajduje się bliżej niż 12 bloków;
- lub patrzy na postać przez ponad 3 sekundy i jednocześnie się do niej zbliża.

The Tall One:
- wydaje charakterystyczny dźwięk;
- rozpoczyna bieg;
- wykorzystuje pathfinding;
- może otwierać odpowiednie drzwi;
- próbuje dogonić gracza.

## 6.3 SINK

Jeśli gracz dotrze do bazy:

1. The Tall One zatrzymuje się.
2. Uruchamia animację `sink`.
3. Postać stopniowo zapada się pod ziemię.
4. Mogą pojawić się cząsteczki bloku znajdującego się pod postacią.
5. Odtwarzany jest dźwięk zapadania.
6. Encja zostaje usunięta.

Animacja trwa około 1 sekundy.

## 6.4 JUMPSCARE

Po losowym czasie 10–40 ticków serwer wysyła `JumpscarePayload` do konkretnego gracza.

Klient:
- wyświetla pełnoekranową teksturę;
- odtwarza dźwięk;
- wykonuje krótki efekt kamery;
- kończy efekt po 100 ms.

Czas 100 ms powinien być mierzony precyzyjnym zegarem klienta, np. `System.nanoTime`, a nie zwykłym licznikiem ticków.

Konfiguracja:
- możliwość wyłączenia jumpscare;
- możliwość pozostawienia samego dźwięku;
- możliwość wyłączenia efektu kamery.

Jeśli gracz nie dotrze do bazy, The Tall One może go dogonić i zaatakować.

Na etapie 4 po trafieniu może zniknąć. Na etapie 5 może kontynuować walkę.

---

# 7. FALSE PRESENCE — FAŁSZYWE POJAWIENIA

Dodaj system fałszywych obecności.

To jedna z najważniejszych mechanik psychologicznego horroru.

## 7.1 Wygląd

Gracz może zobaczyć fałszywego The Tall One:
- między drzewami;
- na końcu tunelu;
- za oknem;
- w ciemnym pomieszczeniu;
- na skraju ekranu;
- na wzgórzu;
- kątem oka;
- za chwilę w miejscu, w którym wcześniej stała postać.

## 7.2 Zachowanie

- postać pojawia się na krótko;
- po odwróceniu kamery może zniknąć;
- może znikać po bezpośrednim spojrzeniu;
- może stać nieruchomo;
- może wyglądać jak prawdziwy The Tall One;
- nie może zadawać obrażeń.

## 7.3 Dźwięki

Dodaj:
- fałszywe kroki;
- oddech;
- odgłosy kopania;
- pukanie;
- szelesty;
- krótkie dźwięki dochodzące z różnych kierunków.

## 7.4 Implementacja

Fałszywa obecność:
- jest renderowana wyłącznie po stronie klienta;
- nie ma hitboxa;
- nie zadaje obrażeń;
- nie jest prawdziwą encją;
- nie pojawia się na liście graczy;
- nie wpływa na limit jednej prawdziwej postaci;
- może być przypisana do konkretnego gracza;
- ma cooldown i konfigurowalne prawdopodobieństwo.

---

# 8. WYKRYWANIE I MAPOWANIE BAZY

The Tall One powinien stopniowo poznawać bazę gracza.

Najpierw sprawdź, czy istniejący system wykrywania baz może zostać wykorzystany. Rozbuduj go, zamiast tworzyć drugi.

## 8.1 BaseTracker

Śledzi:
- bloki postawione przez gracza;
- czas przebywania gracza w chunkach;
- łóżka i spawn;
- skrzynie;
- piece;
- stoły rzemieślnicze;
- drzwi;
- szkło;
- źródła światła.

Można wykorzystać `UseBlockCallback` lub odpowiedni mixin do wykrywania stawiania bloków.

Każdy chunk otrzymuje wynik określający prawdopodobieństwo, że znajduje się w nim baza.

## 8.2 BaseScanner

Kiedy chunk przekroczy określony próg, rozpoczyna się skanowanie.

Wymagania:
- skanowanie rozłożone na ticki;
- limit bloków na tick;
- obsługa klastrów sąsiednich chunków;
- brak skanowania całego świata;
- brak nadmiernego obciążenia serwera.

## 8.3 BaseMap

Zapisuj:
- wnętrze;
- okna;
- drzwi;
- wejścia;
- światła;
- skrzynie;
- łóżka;
- ciemne kąty;
- możliwe miejsca ukrycia.

Wnętrze można wykrywać przez flood-fill przestrzeni powietrznych z punktów przy łóżku lub stole, z ograniczeniem objętości.

Komórki pod dachem można określać przez skylight i bloki nad nimi.

Dane powinny być zapisywane w `PersistentState` i NBT.

## 8.4 Aktualizowanie mapy

Po zmianach bloków:
- oznacz odpowiedni fragment jako wymagający aktualizacji;
- wykonaj ponowne skanowanie fragmentu;
- zaktualizuj okna, drzwi, światła i skrzynie;
- nie przeliczaj całej bazy bez potrzeby.

## 8.5 Debug 3D

Komenda:

`/itwastall debug base`

Wyświetla kolorowe ramki:
- okna — niebieskie;
- drzwi — zielone;
- światła — żółte;
- skrzynie — pomarańczowe;
- wnętrze — półprzezroczyste.

Druga komenda:

`/itwastall debug view`

Otwiera `BaseViewerScreen`.

Funkcje:
- obracanie myszą;
- zoom kółkiem;
- przełączanie warstw;
- renderowanie bloków;
- panel informacji o bazie;
- aktualny etap;
- napięcie;
- ostatnie wydarzenia;
- cooldowny.

---

# 9. KSIĄŻKI I LORE

Dodaj system tajemniczych książek pojawiających się w skrzyniach graczy.

Wykorzystaj istniejący system wykrywania baz i skrzyń.

## 9.1 Rozmieszczanie książek

Książki mają wyglądać, jakby ktoś potajemnie umieszczał je w skrzyniach.

Zasady:
- nigdy nie usuwaj przedmiotów gracza;
- nigdy nie zastępuj istniejących przedmiotów;
- wykorzystuj wyłącznie wolne sloty;
- obsługuj skrzynie podwójne;
- zapamiętuj już rozdane książki;
- unikaj duplikatów;
- wykorzystuj losowe szanse i cooldowny;
- nie skanuj całego świata co tick;
- zapisuj postęp w danych świata.

Każda książka powinna mieć unikalny identyfikator w danych przedmiotu.

## 9.2 Książki

Wszystkie poniższe teksty są po angielsku.

### 1. SOMEONE WAS HERE

"I found this book in my chest.

I don't remember putting it there.

I live alone.

I think.

There was something outside yesterday.

I didn't see its face.

But it was tall.

Very tall."

### 2. THE WINDOW

"I saw something outside my house.

It was standing near the trees.

I looked away for a second.

It was closer.

I closed the curtains.

I haven't opened them since."

### 3. THE MINE

"I heard someone mining.

There was nobody else online.

I followed the sound.

I found a tunnel.

At the end of it...

Something was standing there.

It was looking at me."

### 4. DON'T OPEN

"It knocked three times.

I checked the door.

Nothing.

Then it knocked again.

I checked the window.

Nothing.

Then I heard another knock.

From inside."

### 5. THE PLAYER LIST

"I checked the player list.

There was another name.

I didn't recognize it.

Nobody invited him.

Nobody could find him.

But he was still there."

### 6. IT JOINED

"Something joined this world.

I saw it in the distance.

It didn't move.

I checked the player list.

There was nobody new.

So what did I see?"

### 7. OBSERVATION

"It doesn't always follow me.

Sometimes it just stands there.

I can see it watching.

When I turn around...

It's gone.

I don't think it leaves."

### 8. THE CHEST

"I locked my storage.

I checked every item.

Everything was normal.

Then I found another book.

I never put it there."

### 9. COORDINATES

"I found coordinates written on a piece of paper.

I went there.

There was nothing.

I came back the next day.

Something was standing there."

### 10. THE LAST ENTRY

"I don't think it wants to kill me.

I think it wants me to know it's here.

I stopped sleeping.

I stopped leaving my house.

It still finds me.

I can hear it outside."

### 11. THE LIST

Książka zawiera listę nazw graczy z serwera.

Ma sprawiać wrażenie, że ktoś obserwuje osoby obecne w świecie.

### 12. IT KNOWS

"You found all the books.

You know about the knocking.

You know about the mines.

You know about the thing outside your window.

But there is something you still don't understand.

It was never outside."

**Ważne:** `IT KNOWS` jest wyłącznie treścią książki. Nie twórz wydarzenia `IT KNOWS`, nie przypisuj jej do Phase 5 i nie dodawaj osobnej fazy związanej z tą książką.

## 9.3 Dodatkowe warianty lore

Możliwe późniejsze dodatki:
- książki z brakującymi stronami;
- zmieniające się teksty;
- nieznani autorzy;
- współrzędne;
- odniesienia między książkami;
- podpisy poprzednich graczy;
- specjalne wskazówki;
- książki powiązane z wydarzeniami.

---

# 10. ISTNIEJĄCY SYSTEM FAZ — PHASE 2

Wykorzystaj istniejący system faz w projekcie.

**The Tall One aktywuje się w Phase 2.**

Nie twórz nowego systemu faz i nie zmieniaj istniejącej logiki przechodzenia między fazami.

Po aktywacji Phase 2:

1. Wyświetl na środku ekranu napis `IT JOINED`.
2. Pod nim wyświetl `Something has entered your world.`
3. Odtwórz specjalny dźwięk.
4. Na krótko ogranicz dźwięki otoczenia.
5. Dodaj delikatny efekt wizualny.
6. Umożliw pojawianie się The Tall One.
7. Odblokuj odpowiednie wydarzenia i książki.

Zdarzenie:
- uruchamia się tylko raz na świat;
- jest zarządzane przez serwer;
- jest zapisywane w danych świata;
- nie powtarza się po restarcie;
- działa poprawnie w multiplayerze.

**Nie dodawaj Phase 5 ani wydarzenia `IT KNOWS`.**

---

# 11. ROZPOCZĘCIE GŁÓWNEJ AKTYWNOŚCI

Główna aktywność moda rozpoczyna się po jednym pełnym dniu Minecrafta.

Jeden dzień Minecrafta to 24 000 ticków.

Wymagania:
- sprawdzaj czas świata;
- zapisuj informację o rozpoczęciu aktywności;
- nie resetuj licznika po ponownym uruchomieniu;
- nie uruchamiaj głównej aktywności przed upływem wymaganego czasu;
- uwzględnij istniejący system faz.

Nie twórz dodatkowego systemu faz.

---

# 12. PUKANIE W OKNA I DRZWI

Dodaj `WindowKnockEvent`.

## Warunki

- przede wszystkim noc;
- gracz znajduje się w bazie;
- istnieje wykryte okno;
- na zewnątrz jest odpowiednie miejsce;
- spełnione są warunki aktualnego etapu.

## Przebieg

1. The Tall One staje za szybą.
2. Wykonuje trzy puknięcia.
3. Gracz słyszy charakterystyczny dźwięk.
4. Postać może zniknąć po spojrzeniu.
5. Na najwyższym etapie eskalacji może rozbić szybę.

Dodatkowe warianty:
- pukanie w drzwi;
- pukanie w szybę bez widocznej postaci;
- cień pod drzwiami;
- sylwetka za oknem;
- pukanie dochodzące z wnętrza domu;
- samoczynne otwieranie drzwi;
- dźwięki dochodzące z różnych stron bazy.

Wykorzystuj `BaseMap` i animację `knock`.

---

# 13. ZASADZKI W KOPALNI

Dodaj `MineAmbushEvent`.

## Warunki

- gracz znajduje się pod ziemią;
- brak światła słonecznego;
- wysokość Y poniżej 50;
- odpowiednie otoczenie z kamienia;
- gracz nie znajduje się w bazie;
- spełnione są warunki losowania.

## Wydarzenia

The Tall One może:
- wydawać dźwięki kopania;
- pojawić się na końcu tunelu;
- stać nieruchomo;
- gasić pochodnie;
- rozpocząć zasadzkę;
- pojawić się na krótko i zniknąć.

Na etapie 3 mogą występować głównie dźwięki. Od etapu 4 mogą występować fizyczne zasadzki.

## DigPathfinder

Zaprojektuj algorytm A* uwzględniający koszt kopania bloków.

Wymagania:
- koszt zależny od twardości bloku;
- obsługa narzędzi;
- bedrock i płyny jako przeszkody;
- bloki wymagające diamentowego kilofa jako nieprzechodnie dla obecnego wariantu;
- limit przeszukiwanych węzłów;
- ograniczenie zasięgu poszukiwania.

`DigToTargetGoal` powinien:
- poruszać postać w kierunku celu;
- odtwarzać animację kopania;
- odtwarzać dźwięki;
- pokazywać odpowiednie efekty niszczenia bloku;
- respektować `mobGriefing`;
- respektować konfigurację moda.

Jeżeli niszczenie bloków jest wyłączone, używaj dźwięków i efektów zbliżającego się kopania.

Warianty wydarzenia:
- kopanie;
- krótkie pojawienie się i scare;
- walka;
- gaszenie pochodni za graczem.

---

# 14. POZOSTAŁE WYDARZENIA

Dodaj system niezależnych wydarzeń.

Proponowane klasy:

- `WindowKnockEvent`;
- `DoorEvent`;
- `DouseLightsEvent`;
- `ChestShuffleEvent`;
- `SignEvent`;
- `MineAmbushEvent`;
- `StareEvent`;
- `FalsePresenceEvent`.

Każde wydarzenie powinno mieć:
- warunki uruchomienia;
- cooldown;
- prawdopodobieństwo;
- limit częstotliwości;
- obsługę multiplayer;
- poprawne anulowanie;
- zapis ważnych danych.

Przykłady:
- gaszenie pochodni;
- otwieranie drzwi;
- przestawianie wybranych przedmiotów;
- tajemnicze napisy;
- kroki za graczem;
- postać widoczna na końcu korytarza.

Nie modyfikuj ekwipunku gracza bez wyraźnego powodu i zabezpieczeń.

---

# 15. ZADANIA GRACZA

Dodaj `TaskManager`.

Zadania są przypisane do konkretnych graczy i synchronizowane z HUD-em oraz dziennikiem.

## 15.1 NIE WYCHODŹ NA ZEWNĄTRZ

Gracz musi pozostać w bazie przez określony czas w nocy.

Wyjście może uruchomić atak.

## 15.2 CHOWANY

1. Rozpoczyna się odliczanie 30 sekund.
2. Gracz musi schować się w bazie.
3. The Tall One przeszukuje pomieszczenia.
4. Wykorzystuje `BaseMap`.
5. Sprawdza pokoje i ciemne kąty.
6. Może odnaleźć gracza i rozpocząć pościg.

## 15.3 ZGAŚ ŚWIATŁA

Gracz musi wyłączyć wskazane źródła światła.

## 15.4 NIE PATRZ

The Tall One może pojawić się za graczem i obserwować go przez określony czas, np. 30 sekund.

## 15.5 OFIARA

Gracz musi umieścić wymagany przedmiot w wyznaczonej skrzyni.

## Nagrody i kary

Nagroda:
- spadek napięcia;
- czasowy spokój;
- zakończenie wydarzenia.

Kara:
- wzrost napięcia;
- możliwość uruchomienia kolejnego wydarzenia;
- większe ryzyko spotkania.

Zadania muszą być zapisywane i synchronizowane.

---

# 16. GUI I HUD

## JournalScreen

Otwierany klawiszem J.

Zawiera:
- aktualne zadania;
- znalezione notatki;
- książki z lore;
- odkryte informacje o bazie;
- historię niektórych wydarzeń;
- informacje o tym, co The Tall One już poznał.

## NoteScreen

Wyświetla notatki znalezione w strukturach.

## TaskToast

Powiadomienia o:
- nowych zadaniach;
- odliczaniu;
- wykonaniu zadania;
- niepowodzeniu.

## FearHud

- delikatna winieta;
- szum;
- efekty zależne od napięcia;
- możliwość wyłączenia.

## JumpscareOverlay

Pełnoekranowy efekt jumpscare.

Wszystkie intensywne efekty powinny mieć odpowiednie ustawienia dostępności.

---

# 17. STRUKTURY ŚWIATA

Dodaj własne struktury generowane w świecie.

Proponowane struktury:

- opuszczona chata z notatkami;
- kapliczka;
- tunel z pochodniami;
- pokój z tajemniczymi tabliczkami.

Wykorzystaj worldgen Minecrafta, pliki NBT i jigsaw tam, gdzie będzie to odpowiednie.

Struktury:
- pojawiają się rzadko;
- nie generują się w nieodpowiednich miejscach;
- zawierają elementy lore;
- nie powinny nadmiernie obciążać generowania świata.

## 17.1 Dynamiczne struktury

Dodaj `DynamicStructures`.

Możliwe obiekty:
- czarna kopia bazy gracza;
- krzyże;
- ścieżki z pochodni;
- tajemnicze konstrukcje.

## Czarna kopia bazy

- korzysta z `BaseMap`;
- może być wykonana z blackstone i deepslate;
- pojawia się około 60–100 bloków od oryginalnej bazy;
- powinna powstawać poza polem widzenia gracza;
- nie może niszczyć oryginalnej bazy;
- ma ograniczenia wielkości i częstotliwości;
- wykorzystuje zapisane dane bazy.

---

# 18. DŹWIĘKI I ATMOSFERA

Dodaj system własnych dźwięków.

Kategorie:
- kroki;
- oddech;
- szepty;
- pukanie;
- kopanie;
- pojawienie się;
- zniknięcie;
- pościg;
- atak;
- jumpscare;
- `IT JOINED`.

Dźwięki powinny:
- uwzględniać odległość;
- mieć odpowiednią głośność;
- mieć możliwość odtwarzania tylko dla wybranego gracza;
- nie powtarzać się zbyt często;
- nie nakładać się bez kontroli.

Na początku można wykorzystać tymczasowe dźwięki zastępcze, ale docelowo należy przygotować własne pliki `.ogg`.

---

# 19. KONFIGURACJA

Utwórz konfigurację JSON.

Przykładowe ustawienia:

- `griefing` — czy The Tall One może niszczyć bloki;
- `jumpscares` — czy jumpscare jest aktywny;
- `photosensitiveMode` — ograniczenie intensywnych efektów;
- `escalationSpeed` — tempo wzrostu napięcia;
- `minEventCooldown` — minimalny cooldown wydarzeń;
- `scanBlocksPerTick` — budżet skanowania bazy;
- `debugAllowedForNonOps` — dostęp do debugowania;
- prawdopodobieństwo False Presence;
- częstotliwość pukania;
- głośność efektów;
- maksymalny zasięg wydarzeń.

Nie dodawaj ustawień, które tworzą nowy system faz.

---

# 20. MULTIPLAYER I ZAPIS DANYCH

Wszystkie istotne systemy muszą działać na serwerze dedykowanym.

Wymagania:
- maksymalnie jeden prawdziwy The Tall One;
- serwer zarządza prawdziwą encją;
- fałszywe pojawienia są lokalne;
- zadania mogą być przypisane do konkretnych graczy;
- wydarzenia mogą być globalne lub lokalne;
- książki są zapisywane;
- postęp lore jest zapisywany;
- stan etapów jest zapisywany;
- stan wydarzenia `IT JOINED` jest zapisywany;
- późno dołączający gracze otrzymują aktualny stan świata;
- synchronizacja GUI i HUD;
- brak duplikatów po restarcie.

Wykorzystuj `PersistentState`, NBT i odpowiednie mechanizmy Fabric.

---

# 21. KOMENDY

Dodaj komendy administracyjne i debugowe.

Przykładowe:

```text
/itwastall stage
/itwastall stage 0
/itwastall stage 1
/itwastall stage 2
/itwastall stage 3
/itwastall stage 4
/itwastall stage 5

/itwastall debug base
/itwastall debug view
/itwastall debug entity
/itwastall debug events

/itwastall event stare
/itwastall event knock
/itwastall event mine
/itwastall event falsepresence
```

Komendy:
- powinny mieć odpowiednie uprawnienia;
- powinny umożliwiać testowanie funkcji;
- nie powinny przypadkowo niszczyć świata;
- powinny zwracać czytelne komunikaty.

---

# 22. ARCHITEKTURA PROJEKTU

Najpierw przeanalizuj istniejący projekt. Poniższa struktura jest propozycją docelową.

```text
it-was-tall/
  build.gradle
  gradle.properties
  settings.gradle

  src/main/java/com/itwastall/
    ItWasTall.java

    registry/
      ModEntities.java
      ModSounds.java
      ModItems.java
      ModStructures.java

    entity/
      TallOneEntity.java
      ai/
        StareGoal.java
        ChaseGoal.java
        SinkGoal.java
        KnockWindowGoal.java
        DouseLightsGoal.java
        DigToTargetGoal.java
        SearchBaseGoal.java
        DigPathfinder.java

    base/
      BaseTracker.java
      BaseScanner.java
      BaseMap.java

    director/
      Director.java
      Stage.java
      events/

    lore/
      LoreManager.java
      LoreBook.java
      BookDistributor.java

    task/
      TaskManager.java
      tasks/

    world/
      ItWasTallState.java
      DynamicStructures.java

    net/
      Payloads.java

    command/
      ItWasTallCommand.java

    config/
      ItWasTallConfig.java

  src/client/java/com/itwastall/client/
    ItWasTallClient.java

    render/
      TallOneRenderer.java
      TallOneModel.java
      BaseDebugRenderer.java

    screen/
      BaseViewerScreen.java
      JournalScreen.java
      NoteScreen.java

    hud/
      JumpscareOverlay.java
      FearHud.java
      TaskToast.java
      FalsePresenceRenderer.java

  src/main/resources/
    fabric.mod.json
    itwastall.mixins.json

    assets/itwastall/
      geo/
      animations/
      textures/
      sounds/
      lang/

    data/itwastall/
      structure/
      worldgen/
      tags/

  tools/
```

Nie twórz na siłę całej struktury, jeśli część klas już istnieje. Dopasuj ją do rzeczywistego projektu.

---

# 23. WYDAJNOŚĆ

Mod nie może nadmiernie obciążać serwera.

Unikaj:
- skanowania całego świata co tick;
- tworzenia wielu niepotrzebnych encji;
- wysyłania dużych payloadów co tick;
- ciągłego przeliczania całej bazy;
- nieograniczonego przeszukiwania A*;
- kosztownych obliczeń AI bez limitów.

Wykorzystuj:
- cooldowny;
- limity operacji na tick;
- cache;
- aktualizowanie danych tylko po zmianach;
- ograniczenia zasięgu;
- kontrolę chunków;
- zapisywanie danych tylko wtedy, kiedy jest to potrzebne.

---

# 24. TESTY I WERYFIKACJA

## 24.1 Build

Sprawdź:

```bash
./gradlew build
```

## 24.2 Fabric GameTest

Przygotuj testy:

**BaseMap**
- testowy domek;
- `isInside` zwraca true wewnątrz;
- `isInside` zwraca false na zewnątrz;
- wykrywanie okien;
- wykrywanie drzwi;
- wykrywanie świateł.

**Maszyna stanów**
- gracz w odległości 10 bloków powoduje przejście do CHASE;
- gracz wewnątrz bazy powoduje SINK;
- encja zostaje poprawnie usunięta;
- zostaje wysłany JumpscarePayload.

**DigPathfinder**
- ścieżka przez odpowiednie bloki jest możliwa;
- obsydian i bedrock są przeszkodami w odpowiednim wariancie.

**Książki**
- nie usuwają przedmiotów gracza;
- nie duplikują się;
- zapisują swoje identyfikatory.

**Multiplayer**
- istnieje maksymalnie jedna prawdziwa postać;
- lokalne False Presence nie tworzy prawdziwej encji;
- wydarzenia synchronizują się poprawnie.

## 24.3 Test serwera

```bash
./gradlew runServer
```

Przetestuj:
- `/itwastall stage 4`;
- `/itwastall event stare`;
- `/itwastall event knock`;
- `/itwastall debug base`.

Sprawdź logi pod kątem wyjątków i błędów.

## 24.4 Test klienta

```bash
./gradlew runClient
```

Sprawdź:
- model;
- animacje;
- obserwowanie;
- pościg;
- zapadanie;
- jumpscare 100 ms;
- pukanie;
- fałszywe pojawienia;
- dziennik;
- HUD;
- podgląd 3D bazy.

---

# 25. KOLEJNOŚĆ REALIZACJI CAŁEGO MODA

Realizuj projekt w tej kolejności.

## ETAP A — MODEL I WYGLĄD
1. Przeanalizuj repozytorium.
2. Przygotuj model GeckoLib.
3. Przygotuj tekstury.
4. Przygotuj animacje.
5. Przygotuj interaktywny podgląd 3D.
6. Pokaż użytkownikowi model do akceptacji.

## ETAP B — PODSTAWY MODA
1. Sprawdź Fabric.
2. Sprawdź GeckoLib.
3. Zarejestruj encję.
4. Dodaj renderer.
5. Dodaj atrybuty.
6. Dodaj konfigurację.
7. Dodaj komendy.
8. Sprawdź build.

## ETAP C — SYSTEM ETAPÓW
1. Dodaj `ItWasTallState`.
2. Dodaj `Director`.
3. Dodaj sześć etapów eskalacji.
4. Dodaj system napięcia.
5. Dodaj zapisywanie postępu.
6. Dodaj komendy testowe.

## ETAP D — AI
1. Dodaj maszynę stanów.
2. Dodaj STARE.
3. Dodaj obserwowanie.
4. Dodaj zatrzymywanie przy patrzeniu.
5. Dodaj poruszanie się poza polem widzenia.
6. Dodaj STALK.
7. Dodaj CHASE.
8. Dodaj SINK.
9. Dodaj ATTACK.
10. Dodaj limit jednej prawdziwej encji.

## ETAP E — GŁÓWNA SCENA
1. Dodaj scenę obserwowania.
2. Dodaj pościg.
3. Dodaj wykrywanie wejścia do bazy.
4. Dodaj zapadanie pod ziemię.
5. Dodaj jumpscare 100 ms.
6. Dodaj dźwięki.
7. Przetestuj całą scenę.

## ETAP F — BAZY
1. Rozbuduj BaseTracker.
2. Rozbuduj BaseScanner.
3. Rozbuduj BaseMap.
4. Dodaj wykrywanie okien i drzwi.
5. Dodaj wykrywanie skrzyń i świateł.
6. Dodaj zapisywanie mapy.
7. Dodaj debugowanie 3D.
8. Dodaj BaseViewerScreen.

## ETAP G — FALSE PRESENCE
1. Dodaj lokalny renderer.
2. Dodaj fałszywe pojawienia.
3. Dodaj znikanie po spojrzeniu.
4. Dodaj fałszywe dźwięki.
5. Dodaj cooldowny.
6. Przetestuj multiplayer.

## ETAP H — ZDARZENIA
1. Pukanie w okna.
2. Pukanie w drzwi.
3. Gaszenie świateł.
4. Zdarzenia ze skrzyniami.
5. Tajemnicze napisy.
6. Zdarzenia w kopalniach.
7. Zasadzki.

## ETAP I — LORE
1. Dodaj system książek.
2. Dodaj wszystkie opisane książki.
3. Dodaj rozmieszczanie w skrzyniach.
4. Dodaj zabezpieczenia przed duplikatami.
5. Dodaj zapis postępu.
6. Dodaj wydarzenie `IT JOINED` przy aktywacji istniejącego Phase 2.
7. Przetestuj multiplayer.

## ETAP J — ZADANIA I GUI
1. Dodaj TaskManager.
2. Dodaj wszystkie zadania.
3. Dodaj HUD.
4. Dodaj JournalScreen.
5. Dodaj NoteScreen.
6. Dodaj TaskToast.
7. Dodaj FearHud.
8. Przetestuj zapisywanie postępu.

## ETAP K — STRUKTURY
1. Dodaj opuszczoną chatę.
2. Dodaj kapliczkę.
3. Dodaj tunel z pochodniami.
4. Dodaj pokój tabliczek.
5. Dodaj dynamiczne struktury.
6. Dodaj czarną kopię bazy.
7. Przetestuj generowanie świata.

## ETAP L — DŹWIĘKI I BALANS
1. Dodaj własne dźwięki.
2. Dostosuj głośność.
3. Dostosuj cooldowny.
4. Dostosuj prawdopodobieństwo wydarzeń.
5. Sprawdź częstotliwość spotkań.
6. Sprawdź wydajność.

## ETAP M — TESTY KOŃCOWE
1. Test lokalnego świata.
2. Test serwera dedykowanego.
3. Test multiplayer.
4. Test zapisywania i wczytywania.
5. Test restartu serwera.
6. Test niezaładowanych chunków.
7. Test wydajności.
8. Napraw błędy.
9. Zbuduj finalny JAR.

---

# 26. KRYTERIA UKOŃCZENIA

Mod jest gotowy, kiedy:

- The Tall One ma własny model;
- animacje działają;
- AI działa poprawnie;
- mechanika obserwowania działa;
- istnieje maksymalnie jedna prawdziwa postać na serwer;
- działa sześć etapów eskalacji;
- istnieje integracja z istniejącym Phase 2;
- działa wydarzenie `IT JOINED`;
- główna aktywność respektuje 24 000 ticków;
- działa scena STARE → CHASE → SINK → JUMPSCARE;
- jumpscare trwa około 100 ms;
- działają fałszywe pojawienia;
- baza jest wykrywana i mapowana;
- działa debugowanie 3D;
- The Tall One puka w szyby i drzwi;
- działają zasadzki w kopalniach;
- książki pojawiają się w skrzyniach bez usuwania przedmiotów;
- działa lore;
- działają zadania;
- działa dziennik;
- działają struktury;
- działa multiplayer;
- świat zapisuje potrzebne dane;
- projekt buduje się bez błędów.

---

# 27. INSTRUKCJE KOŃCOWE DLA CLAUDE CODE

Pracujesz w istniejącym projekcie:

`/home/user/it-was-tall`

Najpierw przeanalizuj wszystkie pliki i ustal:
- co już istnieje;
- co działa;
- co wymaga poprawy;
- co należy dopiero stworzyć;
- które systemy są już połączone.

Następnie przedstaw użytkownikowi listę brakujących elementów i proponowany plan wdrożenia.

Nie zakładaj, że wszystko z tego dokumentu jest już zaimplementowane.

Po każdym większym etapie:
1. Zbuduj projekt.
2. Uruchom odpowiednie testy.
3. Sprawdź logi.
4. Napraw błędy.
5. Wypisz zmienione pliki.
6. Opisz, co faktycznie działa.
7. Wypisz, co jeszcze pozostało.

Nie oznaczaj funkcji jako gotowej, jeśli istnieje tylko placeholder.

Nie zastępuj prawdziwej implementacji komentarzem TODO.

Nie usuwaj istniejących funkcji ani danych.

Nie twórz drugiego systemu baz, faz ani etapów, jeśli istniejące rozwiązania można rozbudować.

**Cel końcowy:** stworzyć kompletny, oryginalny horror mod Minecrafta, w którym The Tall One obserwuje gracza, poznaje jego bazę, pojawia się w kopalniach, zostawia tajemnicze ślady i może pojawić się w najmniej oczekiwanym momencie.
