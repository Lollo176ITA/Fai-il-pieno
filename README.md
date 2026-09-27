# Fai il pieno

App Android che dice dove e quando conviene fare rifornimento, usando i prezzi ufficiali
pubblicati ogni giorno dal Ministero delle Imprese e del Made in Italy (MIMIT).
Tutti i calcoli avvengono sul telefono: non c'è nessun backend.

## Requisiti

- JDK 17 o superiore (va bene il JBR di Android Studio)
- Android SDK con la piattaforma **API 37** (Android 17)
- Android Studio recente (AGP 9.4) oppure solo la riga di comando

## Build

```bash
./gradlew assembleDebug      # APK in app/build/outputs/apk/debug/
./gradlew test               # test unitari
./gradlew lintDebug          # analisi statica
```

Il percorso dell'SDK va in `local.properties` (Android Studio lo crea da solo):

```properties
sdk.dir=C\:/Users/<utente>/AppData/Local/Android/Sdk
```

### Chiave OpenRouteService

Percorsi abituali e ricerca degli indirizzi usano [OpenRouteService](https://openrouteservice.org/)
tramite l'host `api.heigit.org` (il vecchio `api.openrouteservice.org` è stato dismesso).
La chiave va **solo** in `local.properties`,
che è escluso da git:

```properties
ORS_API_KEY=la-tua-chiave
```

Non inserire mai chiavi nel codice o in file versionati. Senza chiave l'app funziona lo stesso,
ma percorsi e ricerca degli indirizzi mostrano un avviso.

## Struttura

| Modulo | Tipo | Contenuto |
|---|---|---|
| `:domain` | Kotlin/JVM puro | Modelli, parser CSV in streaming, normalizzazione dei marchi, classificazione dei carburanti, geometria (Haversine, bounding box, polyline, distanza punto-segmento, fascia attorno al percorso), risparmio netto, classifica dei distributori vicini e lungo i tragitti |
| `:data` | Libreria Android | Room, DataStore, download OkHttp, WorkManager, posizione (LocationManager, senza Google Play Services), client OpenRouteService/Pelias |
| `:app` | Applicazione | Jetpack Compose, Material 3 Expressive, navigazione, ViewModel |

La logica sta in `:domain`, quindi i test girano sulla JVM senza emulatore.

Stack: Kotlin, Compose, Material 3 Expressive (`material3` 1.5.0-alpha, l'unica versione in
cui le API Expressive sono pubbliche), Hilt, Room, DataStore, WorkManager, OkHttp,
kotlinx.serialization, MapLibre.

## Dati

### Fonte

- Pagina del dataset: <https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti>
- `anagrafica_impianti_attivi.csv` e `prezzo_alle_8.csv`, pubblicati ogni mattina verso le 9
  con i prezzi in vigore alle 8 del giorno indicato nella prima riga (`Estrazione del AAAA-MM-GG`).
- Licenza **IODL 2.0**: l'app mostra sempre "Fonte: Ministero delle Imprese e del Made in Italy"
  e la data dei prezzi.

### Formato gestito

- Separatore `|` (dal 10/02/2026) e nessun quoting. Il vecchio formato con la virgola viene rifiutato.
- Alcuni nomi di impianto contengono a loro volta `|`, per esempio `STOIL | gestori.prezzibenzina.it`.
  Il parser tratta come fissi i primi 4 e gli ultimi 5 campi e ricompone il nome con quelli
  in mezzo.
- I prezzi sono salvati in millesimi di euro interi (2,309 → 2309). Il metano è in €/kg.
- Le coordinate mancanti o fuori dall'Italia lasciano l'impianto nel DB, ma non in lista né in mappa.

### Regole di affidabilità

| Regola | Effetto |
|---|---|
| Prezzo fuori da un intervallo plausibile (es. benzina 0,80–4,00 €) | Scartato all'import |
| Prezzo comunicato più di 14 giorni prima dell'estrazione | Non mostrato in lista né usato nelle medie; visibile nel dettaglio come "non aggiornato" |
| Prezzo più basso del 20% rispetto alla mediana nazionale (stesso carburante e modalità) | Considerato sospetto: escluso da lista e medie; visibile nel dettaglio con un avviso |
| GPL e metano | Self e servito non vengono distinti (il self esiste in circa il 3% degli impianti) |
| Solo prodotti base (Benzina, Gasolio, GPL, Metano) | Usati per classifiche e medie. I prodotti speciali (Blue Diesel, HVO…) compaiono nel dettaglio |

### Risparmio netto

```
risparmio = (prezzo_medio_zona − prezzo) × litri − km_deviazione × consumo × prezzo
```

- **Litri:** un rifornimento tipico, pari all'80% del serbatoio.
- **Deviazione:** i km in più, andata e ritorno, rispetto al distributore più vicino.
- **Consiglio:** un distributore viene proposto solo se il risparmio è positivo.

### Percorsi abituali

- L'utente salva i luoghi (Casa, Lavoro, Università, altri) cercando l'indirizzo o usando la
  posizione attuale, poi i tragitti tra due luoghi con i giorni della settimana.
- Il percorso in auto si calcola **una volta** con OpenRouteService e si salva in locale come
  encoded polyline: le ricerche successive non usano la rete.
- Si cercano i distributori entro una fascia attorno al percorso (predefinita 500 m) con la
  distanza punto-segmento; i segmenti sono indicizzati in una griglia di circa 1 km.
- Risparmio netto con la stessa formula dei distributori vicini: la media è quella della fascia
  (la media nazionale se ci sono meno di 3 distributori) e la deviazione è andata e ritorno dal
  percorso al distributore.

### Mappa

Tile di [OpenFreeMap](https://openfreemap.org/) (gratuite, senza chiave), dati
© [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors.

## Licenza del codice

Da definire.
