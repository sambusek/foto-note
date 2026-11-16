# Pastashoot

App Android (pensata per Samsung Galaxy S23 Ultra) per annotare le foto subito dopo lo scatto:
disegno con S Pen e nota testuale associata alla foto.

## Come funziona

1. Scatti con la Fotocamera Samsung, come sempre.
2. Pastashoot se ne accorge (osserva `DCIM/Camera`) e ti manda una notifica con l'anteprima.
   Se attivi "Apri l'editor da solo" e il permesso "Comparsa sopra altre app", l'editor si apre direttamente.
3. Nell'editor:
   - **S Pen** disegna (lo spessore segue la pressione);
   - **tasto laterale dell'S Pen premuto** = gomma;
   - con **"Solo S Pen"** attivo le dita spostano e zoomano la foto (pizzica per lo zoom);
   - colori, spessori S/M/L, annulla/ripeti, "Cancella tutto";
   - **Nota**: campo di testo (puoi dettare con il microfono della tastiera).
4. **Salva** crea una copia in `Immagini/Pastashoot` (`<nome originale>_nota.jpg`).
   L'originale non viene mai toccato. **Condividi** salva e apre la condivisione (foto + nota come testo).

La nota compare **in bianco su una banda nera aggiunta sotto la foto** (la foto non viene coperta;
l'immagine salvata è più alta dell'originale). Nell'editor vedi l'anteprima mentre scrivi.
La nota viene scritta anche nei metadati EXIF della copia (descrizione e commento; senza accenti,
perché l'EXIF accetta solo ASCII). Data, modello e posizione GPS dell'originale vengono copiati.
Riaprendo la stessa foto ritrovi tratti e nota modificabili.

**Nota su più foto**: dalla schermata principale ("Aggiungi una nota a più foto") oppure dalla
Galleria selezionando più foto › *Condividi › Nota su più foto*. La stessa nota viene aggiunta a
tutte; i disegni già fatti restano, una nota precedente viene sostituita.

La posizione GPS viene conservata (serve il permesso "posizione nei contenuti multimediali", chiesto al primo avvio).

Puoi annotare anche foto vecchie: dalla Galleria, *Condividi › Annota con Pastashoot*.

## Installazione

Ogni modifica su `main` avvia una build su GitHub Actions che pubblica l'APK nella release
**latest** del repository.

1. Dal telefono, accedi a GitHub e apri *Releases › latest*, scarica `pastashoot.apk`.
2. Aprilo: la prima volta Android chiede di consentire l'installazione da quella app (Chrome / Archivio).
3. Apri Pastashoot e segui i 3 passaggi a schermo (permessi, monitoraggio, batteria).

Gli aggiornamenti si installano sopra la versione precedente (la chiave di firma è fissa, in `keystore/`).

## Struttura

- `PhotoWatcherService` — servizio in primo piano che osserva le nuove foto
- `EditorActivity` + `Ink.kt` — editor con S Pen, zoom, gomma, annulla
- `AnnotationIO.kt` — salvataggio della copia annotata, EXIF, archivio delle annotazioni
- `MainActivity` — impostazioni e permessi

## Crediti

Font: IBM Plex Mono, © IBM Corp., licenza SIL Open Font License 1.1 (vedi `FONT_LICENSE_OFL.txt`).

## Skin

Dalla schermata principale (riquadro "Aspetto") si sceglie tra tre stili, definiti in `Skin.kt`:

- **Platinum** — grigi e rilievi da desktop di fine anni '90, scrivania azzurro-viola.
- **Plan 9** — omaggio a rio/acme: grigio piatto, bordi viola-blu, tag azzurro pallido, testi su giallo pallido, minuscolo e monospazio.
- **Sistema 7** — bianco e nero a 1 bit: scacchiera retinata, barre a righe nere, controlli che si invertono.

Font aggiuntivo per i titoli di Sistema 7: Pixelify Sans Bold (SIL OFL 1.1).

## Sopralluoghi (sessioni)

La schermata principale è l'elenco dei sopralluoghi.

1. **Inizia sopralluogo…** e dagli un nome: da quel momento tutte le foto scattate con la fotocamera
   entrano nella sessione (area di staging).
2. Nella sessione tocchi una foto per annotarla (S Pen e nota): le annotazioni restano **bozze**
   sulla singola foto, modificabili quanto vuoi. Tenendo premuto escludi (o riammetti) una foto.
3. **Chiudi sessione…** consolida tutto: ogni foto viene salvata con tratti e nota in
   `Immagini/Pastashoot/YYMMDD_NOME`. Le foto senza annotazioni vengono copiate identiche all'originale.
4. Da un sopralluogo chiuso: **ZIP** (tutte le foto, da condividere) e **PDF** (report multipagina:
   copertina + una foto per pagina). **Elimina…** rimuove la cartella del sopralluogo, non gli originali.

Aspetto, permessi e strumenti per le foto singole sono in **Impostazioni…**.

Codice: `Session.kt` (modello e operazioni), `SessionActivity.kt` (schermate), `Export.kt` (ZIP e PDF).

Nella sessione in corso: **Aggiungi foto…** inserisce foto scattate prima dell'inizio; **Nota multipla**
dà la stessa nota (bozza) a più foto selezionate; nell'editor si passa alla foto precedente o successiva
con ◀ ▶ o scorrendo con un dito a foto intera (in modalità "solo penna"), salvando la bozza al passaggio.

**Layout note…** (per sessione) definisce campi con nome e posizione: banda superiore o inferiore, oppure uno
dei quattro angoli della foto. Ogni foto ha poi i suoi valori; senza campi resta solo la nota libera, nella
banda inferiore. Un nuovo sopralluogo parte dal layout dell'ultimo. **Elimina…** in una sessione in corso la
abbandona senza salvare nulla.
