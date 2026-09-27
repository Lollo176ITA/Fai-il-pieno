# FaiIlPieno — contesto per Claude

App Android che dice all'utente DOVE e QUANDO conviene fare rifornimento, in base a posizione,
percorsi abituali, auto e prezzi ufficiali MIMIT. **Priorità assoluta: interfaccia chiara**:
l'utente deve capire in 2 secondi dove andare e quanto risparmia. Tutto il calcolo avviene sul
telefono, senza backend. Dettagli tecnici e regole sui dati sono in [README.md](README.md).

## Stato

- **Fase 1 (MVP): completata.** Vedi `git log`.
- **Prossima: fase 2** (percorsi abituali). Specifica delle fasi mancanti qui sotto.

## Regole di lavoro (dall'utente)

1. Una fase alla volta. A fine fase: build funzionante, test verdi, commit con messaggio chiaro,
   breve riepilogo di cosa è stato fatto e cosa resta.
2. **Prima di aggiungere dipendenze non già presenti in `gradle/libs.versions.toml`, chiedere.**
3. Se una scelta di interfaccia non è ovvia, proporre 2 alternative con pro e contro invece di
   decidere da soli.
4. **Nei commit mai crediti a Claude** (niente `Co-Authored-By`, niente "Generated with").
5. Tutte le stringhe in `strings.xml`, in italiano. Chiavi API solo in `local.properties`
   (es. `ORS_API_KEY`), mai nel codice o nel repository.
6. API di Material 3 Expressive: verificarle nei sorgenti prima di usarle, non inventarle.

## Stack e struttura

Kotlin, Compose, Material 3 Expressive (`material3` **1.5.0-alpha**, fissato sopra la BOM: nella
1.4.0 stabile le API Expressive sono `internal`), Hilt, Room, DataStore, WorkManager, OkHttp,
kotlinx.serialization, MapLibre + OpenFreeMap. AGP 9 con Kotlin integrato (niente plugin
`kotlin-android` nei moduli), KSP, minSdk 26, compile/targetSdk 37.

- `:domain` Kotlin/JVM puro: logica e calcoli, **ogni nuovo calcolo va qui, con i test**.
- `:data` Room, DataStore, rete, worker, posizione.
- `:app` UI Compose, ViewModel, navigazione (3 schede: Oggi, Mappa, La mia auto).

## Decisioni già prese (non rimetterle in discussione senza motivo)

- Prezzi in millesimi di euro interi (`priceMilli`). Il metano è in €/kg, il consumo in kg/100 km.
- Prezzi comunicati più di 14 giorni prima dell'estrazione: esclusi da lista e medie, visibili
  nel dettaglio come "non aggiornato".
- Prezzi più bassi del 20% rispetto alla mediana nazionale (stesso carburante e modalità):
  **nascosti** da lista e medie (scelta A dell'utente), visibili nel dettaglio con un avviso.
- GPL e metano: self e servito non si distinguono (`FuelCategory.hasServiceModes`).
- Solo i prodotti base (Benzina, Gasolio, GPL, Metano) entrano in classifiche e medie.
- Posizione con il `LocationManager` di sistema, niente Google Play Services. Nessuna
  localizzazione in background.
- Distributore consigliato solo se il risparmio netto è positivo. Rifornimento tipico = 80% del
  serbatoio. Deviazione = km in più, andata e ritorno, rispetto al distributore più vicino.
- Room è alla versione 1. **Da ora ogni modifica allo schema richiede di incrementare la versione e
  aggiungere una migrazione** (`AutoMigration` dove basta).

## Comandi

```bash
./gradlew assembleDebug test lintDebug
```

Emulatore (AVD `Pixel_8`): **usare sempre `adb -s emulator-5554`**. In Git Bash va impostato
`MSYS_NO_PATHCONV=1` per i percorsi `/sdcard`. `adb emu geo fix` non funziona: per simulare una
posizione usare i provider di test:

```bash
adb -s emulator-5554 shell appops set 2000 android:mock_location allow
adb -s emulator-5554 shell cmd location providers add-test-provider gps
adb -s emulator-5554 shell cmd location providers set-test-provider-enabled gps true
adb -s emulator-5554 shell cmd location providers set-test-provider-location gps --location 41.8902,12.4922
```

(ripetere per `fused` e `network`).

## Fasi da fare

### Fase 2 — Percorsi abituali

1. L'utente salva luoghi (Casa, Lavoro, Università, personalizzati) e i giorni della settimana in
   cui fa il tragitto.
2. Il percorso si calcola UNA volta con OpenRouteService (chiave `ORS_API_KEY` in
   `local.properties`) e la polyline si salva in locale.
3. Si cercano i distributori entro una distanza configurabile dalla polyline (default 500 m),
   con la distanza punto-segmento.
4. Convenienza della deviazione:
   `risparmio netto = (prezzo_medio_zona − prezzo_distributore) × litri_da_fare − (km_deviazione × consumo × prezzo_distributore)`.
   Si suggerisce un distributore solo se il risparmio netto è positivo.

Tabelle previste: `places` (id, label, kind, lat, lon, address?) e `commutes` (id, fromPlaceId,
toPlaceId, daysMask, roundTrip, distanceM, durationS, polyline encoded, computedAt).

### Fase 3 — Stima carburante e notifiche

1. Pulsante "Ho fatto il pieno", con litri e importo opzionali.
2. Stima dei km rimanenti: ogni giorno si scalano i km del tragitto abituale, nei giorni impostati.
3. Calibrazione: al pieno successivo, se l'utente inserisce i litri, si ricalcola il consumo reale
   e si aggiorna il profilo.
4. Notifica del tipo: "Tra 2 giorni sei in riserva. Domani passi davanti a [distributore] a
   2,17 €/l, risparmi 4 €."
5. Niente localizzazione in background: solo percorsi salvati e posizione in primo piano.

Tabella prevista: `refuels` (id, at, liters?, amountCents?, isFull, stationId?).

### Fase 4 — Eventi e prezzo stimato

File JSON remoto (URL configurabile, es. GitHub raw), letto all'avvio e poi ogni giorno. Schema
di esempio:

```json
{
  "aggiornato": "2026-09-27",
  "eventi": [
    {
      "id": "sconto-accise-gasolio-2026-09",
      "tipo": "variazione_accise",
      "carburante": "gasolio",
      "variazione_eur_litro": 0.061,
      "dal": "2026-09-26",
      "al": "2026-10-05",
      "titolo": "Lo sconto sul gasolio scende a 6,1 cent",
      "testo": "Dal 6 ottobre lo sconto termina.",
      "fonte_url": "https://..."
    },
    {
      "id": "tetto-eni-2026-09",
      "tipo": "tetto_prezzo",
      "bandiera": "Eni",
      "prezzi_max": { "gasolio": 2.19, "benzina": 1.99 },
      "dal": "2026-09-28",
      "al": "2026-10-27",
      "titolo": "Tetto Eni: gasolio max 2,19 €/l"
    }
  ]
}
```

Usi:
- **Card in cima alla home** quando un evento è attivo o imminente (es. "Sabato lo sconto
  scende: conviene fare il pieno venerdì").
- **"Prezzo stimato oggi"** = prezzo di ieri corretto con le variazioni note avvenute dopo la data
  di estrazione. Per i tetti si usa `min(prezzo, prezzo_max)`. Va mostrato SEMPRE etichettato
  come stima, accanto al prezzo ufficiale.
- **Evidenziare i distributori del marchio** che espongono un prezzo sopra il tetto dichiarato.
- **JSON da validare:** se è malformato o irraggiungibile, l'app funziona comunque senza eventi.
  Da salvare come file validato in `filesDir`, non in Room.

### Requisiti trasversali (valgono per ogni fase)

- Verde sotto la media e rosso sopra, ma MAI solo il colore: sempre anche testo o icona.
- `contentDescription` su icone e card, contrasto WCAG AA, target tattili di almeno 48 dp, testo
  ingrandito supportato (verificare anche con `font_scale 2.0`).
- Stati vuoti, di caricamento e di errore curati.
- Test unitari per ogni calcolo nuovo: distanza punto-segmento, stima carburante, applicazione
  degli eventi.
