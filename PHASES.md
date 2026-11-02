# IT WAS TALL — PLAN FAZ I ETAPÓW ESKALACJI

> Dokument wykonawczy do MODPLAN.md (§5, §10, §11). Definiuje system FAZ (start moda)
> i system ETAPÓW 0–5 (eskalacja) oraz co dokładnie dzieje się w każdym.
> Odpowiada Poziomom 1–3 planu wdrożenia (ItWasTallState → Director/Stage → podpięcie systemów).

---

## 0. Dwa osobne systemy

| System | Kieruje czym? | Zapis | Steruje |
|---|---|---|---|
| **FAZA** (Phase) | cyklem życia moda | `ItWasTallState` (NBT) | czy The Tall One **może w ogóle istnieć** |
| **ETAP** (Stage 0–5) | intensywnością | `ItWasTallState` (NBT) | częstotliwością wydarzeń, dystansami, cooldownami |

Zasady niezmienne:
- FAZA przechodzi raz: PRE → AKTYWNA. Nie wraca (plan §10: „tylko raz na świat").
- ETAP idzie tylko do góry (0→5). Nie schodzi po śmierci gracza ani restarcie.
- Zmiana ETAPU nie usuwa aktywnych wydarzeń — stare dobiegają końca naturalnie.
- `tension` (0–100) decyduje o awansach; `escalationSpeed` (config) skaluje tempo.

**TWARDE WYMAGANIE: zmiany komendą działają natychmiast (runtime).**
`/itwastall stage <n>`, `/itwastall phase activate`, `/itwastall tension ±` zmieniają zachowanie
od kolejnego ticka — BEZ restartu serwera. Warunek: wszystkie systemy odpytują
`Director.currentStage()` przy każdym ticku (żadnego snapshotowania etapu przy starcie).
Komenda modyfikuje stan w pamięci + zapis do `ItWasTallState` w tle. Trwające akcje (animacje,
biegający zdarzenia) dobiegają końca naturalnie, ale nowe decyzje systemów od razu korzystają
z nowego etapu.

---

## 1. FAZA PRE (cicha) — od vytvoření světa

**Czas trwania:** pierwszy pełny dzień Minecrafta (licznik 24 000 ticków, zapisywany — restart nie resetuje).

**Co się dzieje:**
- WYŁĄCZNIE dźwięki z trem ETAPU 0 (kroki, szepty, oddech, szelesty) — nigdy postaci, nigdy blokad.
- `AmbientSounds` i `ScareSounds` działają normalnie, bez kategorii „close".
- `MineEvents`: tylko SOUNDS (dźwięki kopania). Nigdy DIG / TORCHES / PEEK.
- `HauntDirector` NIE działa. The Tall One nie może się spawnąć (guard w directorze).
- Spawn egg działa (testy), ale encja w fazie PRE po 30 s cicho znika (DISAPPEAR bez efektu).

**Przejście do FAZY AKTYWNEJ (Phase 2):**

Warunek:
1. upłynęło 24 000 ticków świata **ORAZ** jest noc (time > 13000), **albo**
2. komenda `/itwastall phase activate` (test).

Sekwencja `IT JOINED` (jednorazowa, ~4 s):
1. napis na środku ekranu: **IT JOINED**;
2. pod nim: *Something has entered your world.*;
3. specjalny dźwięk `ui.it_joined` (nowy event do ModSounds);
4. 3 s serwisowe wyciszenie ambientu (80%);
5. delikatny efekt wizualny (winieta, jedna migająca klatka);
6. `itJoined = true` zapisane w state — nie powtórzy się po restarcie;
7. od tej chwili `HauntDirector` uruchamia zegar nawiedzenia (pierwszy spacerek po 3–5 min).

---

## 2. ETAPY ESKALACJI 0–5

### Mechanika napięcia (tension 0–100)

| Zdarzenie | Δ tension | Limit |
|---|---|---|
| gra w nocy | +0.03/s | ciągłe |
| ciemność wokół gracza (light < 4) | +0.10/s | tylko noc |
| przebywanie pod ziemią | +0.12/s | ciągłe |
| gracz patrzy na It ≥ 2 s | +1.0 | 1× na 30 min |
| rozpoczęcie pościgu | +4 | 1× na pościg |
| jumpscare | +6 | 1× na zdarzenie |
| inne wydarzenie (kopalnia, straszak) | +0.5 | 1× na zdarzenie |
| niewykonane zadanie po terminie | +5 | 1× |
| wykonane zadanie | −8 | 1× |
| noc przespana przy świetle ≥ 8 w bazie | −4/dzień | |
| udana ucieczka do bazy w pościgu | −3 | 1× na pościg |

Wartości są startowe, definiowane w configu (`tension.*`).
Progi awansów: Stage 1 = 20, 2 = 40, 3 = 55, 4 = 70, 5 = 85.

---

## ETAP 0 — „CISZA"

**Cel:** nieco niespokojna atmosfera; gracz nie wie jeszcze, że coś jest.

| System | Zachowanie |
|---|---|
| HauntDirector | WYŁĄCZONY — encja się nie pojawia |
| ScareSounds | interwał 90–150 s; powszechne konteksty OUTSIDE_NIGHT / DAY; NIGDY „close" (1.5–3 bloki) |
| AmbientSounds | normalne, bez heartbeat (It nie istnieje) |
| MineEvents | tylko SOUNDS, szansa połowa bazowa |
| ScareText | WYŁĄCZONY |
| Jumpscare / PEEK | NIGDY |

**Awans na 1:** tension ≥ 20.

---

## ETAP 1 — „OBSERWATOR"

**Cel:** czuję, że ktoś patrzy. It stoi daleko, nie goni.

| System | Zachowanie |
|---|---|
| HauntDirector | ON w noc; dystans 40–60 bloków; cooldown nawiedzenia 8–16 min; pierwszy raz 3–5 min |
| TallOneEntity | tylko STARE i DISAPPEAR; `chase` **zablokowany**: bliżenie się lub długie patrzenie → It ZNIKA |
| ScareSounds | interwał 40–80 s; bez minibandi „close"; bez ScareText |
| AmbientSounds | heartbeat zablokowany (It możliwe, ale brak sygnału bliskości) |
| MineEvents | SOUNDS normalna szansa; DIG/TORCHES/PEEK ZABLOKOWANE |

**Awans na 2:** tension ≥ 40 LUB gracz 2 razy podszedł na ≤20 bloków.

---

## ETAP 2 — „ZBLIŻENIE"

**Cel:** It staje BLISKO. Zaczyna działać w okolicy bazy. Pierwsze pukanie.

| System | Zachowanie |
|---|---|
| HauntDirector | dystans 20–30 bloków; cooldown 6–12 min; możliwe spawn za oknem (poziom 6 → pełny zakres), wierzę w szybę |
| TallOneEntity | STARE + DISAPPEAR; przy każdym metastinte kolejnym — znikam przy spojrzeniu; chase wciąż zablokowany |
| WindowKnock (wersja dźwiękowa, przed Poziomem 5) | 1× na noc, szansa 35%; TYLKO dźwięk, bez postaci przy oknie; wersje sylwetki/cienia dochodzą w Poziomach 5–6 |
| MineEvents | DIG nadal ZABLOKOWANE; SOUNDS podbity (+40%); TORCHES/PEEK ZABLOKOWANE |
| FalsePresence (z Poziomu 4) | szansa 1.5%; zniknięcie po spojrzeniu; maksymalnie 1 na noc per gracz |
| ScareSounds | interwał 8–14 s pomiędzy małymi scares |

**Awans na 3:** tension ≥ 55 LORAZ pierwsze skuteczne pukanie nocą.

---

## ETAP 3 — „KONTAKT"

**Cel:** It zaczyna ingerować w świat —gaszę światła, kopie, otwiera drzwi, rzuca pierwsze zasunięcia.

| System | Zachowanie |
|---|---|
| MineEvents | DIG ON (pełny system kopalni z tuneli); TORCHES ON; PEEK nadal OFF; szanse +50% |
| Gaszenie pochodni na powierzchni | dopiero z DouseLightsEvent (Poziom 5); na ten moment tylko w kopalni |
| HauntDirector | dystans 20–30; po spojrzeniu znika w park mode (DISAPPEAR); chase pozostaje zablokowany |
| FalsePresence | 3%; dźwiękowe i wizualne, bez fizycznej encji |
| Zadania (z Poziomu 8) | odblokowane: `Nie wychodź na zewnątrz` i `Zgaś światła` |
| ScareSounds | „close" na 40% popularności; szept przy uchu maks 1× na noc |
| DoorEvent / ChestShuffle / Sign | dochodzą z Poziomu 5 |

**Awans na 4:** tension ≥ 70 LORAZ gracz 3 noce przespał w bazie.

---

## ETAP 4 — „ŁOWCA"

**Cel:** główna scena horroru o pełnej mocy. It goni, zapada się, jumpscare.

| System | Zachowanie |
|---|---|
| TallOneEntity | pełny zestaw: STARE → CHASE (główna scena), SINK, DIG, ATTACK; chase trigger dist 12 (config) |
| Jumpscare | ON — 100 ms, tekstura + dźwięk (config: włącz / tylko dźwięk / wyłącz) |
| PEEK (za plecami) | ON — spawn 2–4 bloki za plecami; obrót = jumpscare; maks 1× na 20 min |
| ScareText | ON — „don't hide. just run." (po 180 s ukrycia nocą, szansa 40%) |
| MineEvents | wszystkie 4 rodzaje ON; pełne fizyczne zasadzki |
| Gaszenie pochodni | ON, sekwencje od najdalszej do najbliższej (także przy bazie) |
| Zadanie | `Nie patrz` odblokowane (It stoi za graczem 30 s) |

**Awans na 5:** tension ≥ 85.

---

## ETAP 5 — „POLOWANIE"

**Cel:** maksimum intensywności; It rozbija szyby, wchodzi do bazy, poluje.

| System | Zachowanie |
|---|---|
| TallOneEntity | chaseSpeed 1.6 (zamiast 1.45); po uderzeniu nie znika od razu — kontynuuje walkę 3–10 s, potem SINK |
| Szyby | It może ROBIć szyby zdane do bazy gracza (tylko Stage 5) |
| Wchodzenie do bazy | SearchBaseGoal (po Poziomie 6, gdy BaseMap działa): It przeszukuje pomieszczenia |
| Zadanie | `Chowany`: odliczanie 30 s, It przeszukuje bazę wg BaseMap, znalezisko → pościg |
| FalsePresence | 5%; różnice per gracz |
| ScareSounds | częstotliwość +50% względem Stage 4 |

Stage 5 nie kończy się sam — działa aż do lipca tension (spadek naturalny po wykonaniu zadań może zawiesić intensywność, ale nie cofa etapu).

---

## 3. Parametry per ETAP (tabela balansowa)

| Parametr | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| Haunt dystans | — | 40–60 | 20–30 | 20–30 | 16–28 | 12–24 |
| Cooldown nawiedzenia | — | 8–16 min | 6–12 | 5–10 | 4–8 | 3–6 |
| CHASE | ✗ | ✗ | ✗ | opcjonalnie 35% | ✓ | ✓ |
| SINK | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ |
| DIG (kopalnia) | ✗ | ✗ | ✗ | ✓ | ✓ | ✓ |
| PEEK | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ częściej |
| WindowKnock | ✗ | ✗ | dźwięk | dźwięk | + cień/drzwi | rozbicie szyby |
| ScareText | ✗ | ✗ | ✗ | rzadko | ✓ | ✓ częściej |
| Jumpscare | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ |
| FalsePresence % | ✗ | 0.5% | 1.5% | 3% | 4% | 5% |
| Gaszenie pochodni | ✗ | ✗ | ✗ | kopalnia | baza + kopalnia | + światła w bazie |
| Zadania | ✗ | ✗ | ✗ | 2 pierwsze | + Nie patrz | + Chowany |

---

## 4. Mapowanie na istniejący kod (Poziom 3)

| System | Co czyta z etapu |
|---|---|
| `HauntDirector` | dystans min/max i cooldown z tabeli etapów (config `stages[]`) |
| `TallOneEntity` | guardy w `tickStare/tickChase` — które tryby są odblokowane na etapie |
| `MineEvents` | bitmapa rodzajów DIG/SOUNDS/TORCHES/PEEK wg etapu |
| `ScareSounds` | interwały i dozwolone kategorie wg etapu |
| `AmbientSounds` | heartbeat od Stage 3 (jak zmienia intensywność z bliskością) |
| `ScareText` | szansa i typ treści wg etapu |
| `JumpscarePayload` | showImage / durationMs wg etapu i configu |
| config | nowe sekcje: `stages[]`, `tension.*`, `activation.*` |

---

## 5. Komendy testowe (Poziom 2)

```
/itwastall stage            // pokazuje: FAZA, ETAP, tension, aktywne cooldowny
/itwastall stage <0-5>      // ustawia etap (test)
/itwastall tension +10
/itwastall phase activate   // wymusza IT JOINED
```
Permisja: lvl 2. Istniejące `/itwastall status` zachowane.
