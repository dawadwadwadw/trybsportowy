# MASTER BLUEPRINT — provenance note

> **Read-only.** This document captures the original intent of the app and is canonical for
> what the app *was* (see CLAUDE.md §1.12). It was provided verbatim by the project owner.
> Do not edit it. If the algorithm changes, that is a `v2` event handled per CLAUDE.md
> §1.12 / §4.1 — not an edit to this file. Reproduced from the owner's pasted text; if any
> line differs from the owner's master copy, the owner's copy wins — flag and replace.

---

# 🧬 MASTER BLUEPRINT: HYBRID ATHLETE READINESS APP

**Rola:** Jesteś Principal Android Architect. Twoim zadaniem jest napisanie kompletnej aplikacji od zera (Jetpack Compose, Kotlin, MVVM, Room, Coroutines). Aplikacja służy do śledzenia gotowości (Readiness) sportowca hybrydowego z zaawansowanym algorytmem zanikania zmęczenia (Fatigue Decay) i karą za obciążenie układu nerwowego (CNS Overload).

Poniżej znajduje się absolutna specyfikacja techniczna. Zaimplementuj kod zgodnie z tymi wytycznymi.

## 📦 1. STOS TECHNOLOGICZNY

* **UI:** Jetpack Compose (Material Design 3)
* **Architektura:** MVVM (Model-View-ViewModel) z Clean Architecture (Data -> Domain -> Presentation)
* **Baza Danych:** Room Database (reaktywna, zwracająca dane jako `Flow<List<T>>`)
* **Asynchroniczność:** Kotlin Coroutines (`Dispatchers.IO`)
* **Zadania w tle:** `AlarmManager` + `BroadcastReceiver` (Powiadomienia)

---

## 💾 2. WARSTWA DANYCH (ROOM DATABASE)

Utwórz tabelę `DailyReadinessEntity`. To jest serce aplikacji.

**Pola tabeli:**

* `@PrimaryKey val dateTimestamp: Long` (Znormalizowana data do północy)
* `val sleepCode: String` (Domyślnie "S2")
* `val hrvCode: String` (Domyślnie "H2")
* `val physicalLoadCode: String` (Domyślnie "P0")
* `val workCode: String` (Domyślnie "W0")
* `val alcoholCode: String` (Domyślnie "A0")
* `val nutritionCode: String` (Domyślnie "N1")
* `val cnsDrain: Int` (Suma punktów z tagów CNS dla danego dnia, domyślnie 0)
* `val bodyDrain: Int` (Suma punktów z tagów Body dla danego dnia, domyślnie 0)
* `val drainTags: String` (JSON / String z listą zaznaczonych tagów)

**DAO (`ReadinessDao`):**

Musi zawierać metody:

* `@Insert(onConflict = OnConflictStrategy.REPLACE)`
* `@Query("SELECT * FROM DailyReadinessEntity ORDER BY dateTimestamp DESC")` zwracające `Flow<List<DailyReadinessEntity>>` (wymusza to natychmiastowe odświeżanie UI).

---

## 🧮 3. CORE ALGORITHM (`CalculateReadinessUseCase`)

To najważniejszy plik w aplikacji. Zaimplementuj logikę matematyczną wyliczającą wynik na podstawie 4 ostatnich dni.

**Słownik Wartości (Base Points):**

* **S (Sen):** S0 (0), S1 (+5), S2 (+20), S3 (+25), S4 (+35)
* **H (HRV):** H0 (-35), H1 (-15), H2 (0), H3 (+15)
* **P (Trening):** P0 (0), P1 (-10), P2 (-30), P3 (-55), P4 (-85)
* **W (Praca):** W0 (0), W1 (-5), W2 (-15), W3 (-30), W4 (-55)
* **A (Alkohol):** A0 (0), A1 (-5), A2 (-15), A3 (-35)
* **N (Dieta):** N0 (+5), N1 (0), N2 (-8), N3 (-15)

**Słownik Tagów (Micro-Tags):**

* **🧠 CNS:** Silny Stres (+4), Kac po siłowni (+3), Deadline (+3), Nauka (+2), Ekran (+2), Relaks (-2)
* **💪 Body:** Ból ścięgien (+4), Choroba (+5), DOMS (+2), Odwodnienie (+2), Siedzenie (+2), Aktywna regeneracja (-2)

**Wzór na "Fatigue Decay" (4-dniowe zanikanie):**

Algorytm iteruje po dzisiejszym dniu oraz 3 dniach wstecz. Każdy dzień ma przypisany modyfikator (wagę):

1. **Dzisiaj (Day 0):** Mnożnik 1.0
2. **Wczoraj (Day -1):** Mnożnik 0.75 (Wyjątek: Sen z wczoraj ma mnożnik 0.8)
3. **Przedwczoraj (Day -2):** Mnożnik 0.5
4. **3 Dni temu (Day -3):** Mnożnik 0.2

*Obliczenie Base Score:* Zsumuj (S + H + P + W + A + N) z uwzględnieniem powyższych mnożników dla historii. Traktuj brakujące dni w bazie jako 0.

**System Overload Penalty (KARA ZA CNS):**

1. Oblicz `total_cns` = suma `cnsDrain` z 4 dni (z użyciem mnożników decay 1.0, 0.75, 0.5, 0.2).
2. Oblicz `total_body` = suma `bodyDrain` z 4 dni (z użyciem tych samych mnożników).
3. Jeśli `(total_cns + total_body) > 6.0`, system ulega przepaleniu.
4. Zastosuj karę do wyniku bazowego: `Final_Score = Base_Score * 0.85f`.

---

## 📱 4. WARSTWA WIZUALNA (UI COMPONENTS)

**A. QuickEntryScreen (Wprowadzanie Danych)**

* Użyj `HorizontalPager` (2 strony, zablokowany swipe, przejście przyciskiem "Dalej").
* **Strona 1:** Wybór kodów (S, H, P, W, A, N).
* **Strona 2:** Chipy (Tagi) podzielone na sekcje "🧠 CNS" i "💪 Ciało". Kliknięcie chipa dodaje punkty do "Live Status" widocznego pod każdą sekcją.
* Dodaj przycisk `[?]` otwierający `LegendBottomSheet`.

**B. ProDashboardScreen (Panel Główny)**

* Lista historycznych dni. W każdym wierszu idealnie wyśrodkowane kody (użyj `Arrangement.SpaceEvenly` na Row).
* **SYSTEMS CHECK:** Komponent pod głównym wykresem. Dwa paski `LinearProgressIndicator` (Górny: CNS, Dolny: Body). Paski reagują na skumulowane wartości (`total_cns`, `total_body`):
  * 0-3 pkt: Kolor Zielony
  * 4-6 pkt: Kolor Żółty
  * 7+ pkt: Kolor Czerwony

**C. DayDetailScreen (Szczegóły Dnia)**

* Karta z podsumowaniem (Kod, Emoji, Nazwa).
* Szczegółowa lista zaznaczonych tagów z ich wagą (+/-).
* Przyciski: "Edytuj ten dzień" (odpala QuickEntry w trybie Update) oraz "Usuń wpis".

**D. LegendBottomSheet (Ściągawka)**

* `LazyColumn` z kartami opisującymi kody S, H, P, W, A, N.
* Trzecia zakładka (Tab) "Fizjologia", tłumacząca karę 15% za przepalenie układu nerwowego i 4-dniowe zanikanie.

---

## ⏰ 5. ZADANIA W TLE

**Powiadomienia (Reminders):**

* Stwórz `ReminderReceiver: BroadcastReceiver`, który wywołuje lokalne powiadomienie z `PendingIntent` otwierającym aplikację.
* W `MainActivity` zaimplementuj `AlarmManager`, który konfiguruje codzienne wywołanie powiadomienia o 20:00. Pamiętaj o uprawnieniu `POST_NOTIFICATIONS` dla Android 13+.
