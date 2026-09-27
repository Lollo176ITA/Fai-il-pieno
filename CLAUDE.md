# FaiIlPieno — contesto per Claude

App Android che dice all'utente DOVE e QUANDO conviene fare rifornimento, in base a posizione,
percorsi abituali, auto e prezzi ufficiali MIMIT. **Priorità assoluta: interfaccia chiara**:
l'utente deve capire in 2 secondi dove andare e quanto risparmia. Tutto il calcolo avviene sul
telefono, senza backend. Dettagli tecnici e regole sui dati sono in [README.md](README.md).

## Stato

- **Fase 1 (MVP): completata.**
- **Fase 2 (percorsi abituali): completata.**
- **Fase 3 (stima carburante e notifiche): completata.** Vedi `git log`.
- **Prossima: fase 4** (eventi e prezzo stimato). Specifica qui sotto.
- CI: `.github/workflows/build.yml` (test, lint, APK come artifact; Release con i tag `v*`).

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
- `:app` UI Compose, ViewModel, navigazione (3 schede: Oggi, Mappa, Percorsi; le Impostazioni,
  con auto, carburante, servizio, marchi e preferenze di viaggio, si aprono dall'ingranaggio in alto).

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
- Room è alla versione 4 (v2: `places` e `commutes`; v3: `refuels`, senza chiave esterna verso
  `stations`, che l'import MIMIT svuota ogni giorno; v4: `commutes.routeAvoidMask`). **Ogni modifica allo schema richiede di
  incrementare la versione e aggiungere una migrazione** (`AutoMigration` dove basta).
- OpenRouteService: host **`api.heigit.org`** (`api.openrouteservice.org` è dismesso dal 24/8/2026).
  Percorsi: `POST /openrouteservice/v2/directions/driving-car/json`; indirizzi: `GET /pelias/v1/search`
  (non `autocomplete`, che sbaglia gli indirizzi completi). Chiave nell'header `Authorization`,
  letta da `local.properties` in `BuildConfig.ORS_API_KEY` del modulo `:data`.
- Tragitti: percorso calcolato una volta e salvato come encoded polyline; si ricalcola solo se
  cambiano i luoghi, su richiesta o se cambiano le preferenze di viaggio (evita autostrade,
  pedaggi, traghetti → `avoid_features`, condivise da tutti i tragitti). Un percorso salvato con
  altre preferenze non si usa per i consigli. I tragitti mai calcolati o falliti non si
  ricalcolano da soli. Il ritorno si assume sulla stessa strada.
- Distributori sul tragitto: entro la fascia scelta (250 m / 500 m predefinito / 1 km / 2 km).
  Deviazione = 2 × distanza in linea d'aria dal percorso. Media di zona = media della fascia;
  con meno di 3 distributori si usa la media nazionale (e la UI lo dice). Il filtro marchi della
  scheda Oggi vale anche qui, ma non cambia la media.
- Card del tragitto in cima alla scheda Oggi (scelta dell'utente): solo per i tragitti del primo
  giorno in cui sono previsti (oggi, se c'è) e solo se c'è un risparmio netto positivo.
- Stima del serbatoio (`TankEstimator`): dall'ultimo pieno si scalano i km dei tragitti previsti
  in ciascun giorno; il giorno del pieno conta, oggi no. Parziali senza litri ignorati. Riserva =
  1/8 del serbatoio. Solo tragitti salvati: nessuna posizione in background.
- Calibrazione da pieno a pieno (litri ÷ km stimati), accettata tra ½ e 2× il consumo attuale e con
  almeno 50 km; aggiorna subito il profilo e lo dice (scelta dell'utente). Assorbe anche i km
  guidati fuori dai tragitti.
- UI della fase 3 (scelte dell'utente): card Serbatoio in cima alle Impostazioni con "Ho fatto il
  pieno"; avviso in Oggi quando la riserva è entro 3 giorni; "Ho fatto il pieno qui" nel dettaglio
  del distributore. Permesso notifiche chiesto dopo il primo pieno registrato, poi interruttore.
- Notifica: `ReserveCheckWorker` ogni giorno alle 18:30, se la riserva è entro 2 giorni (stima di
  domani mattina), al massimo una al giorno, con il distributore consigliato sul primo tragitto.

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
