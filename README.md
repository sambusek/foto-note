# Foto Note

App Android (pensata per Samsung Galaxy S23 Ultra) per annotare le foto subito dopo lo scatto:
disegno con S Pen e nota testuale associata alla foto.

## Come funziona

1. Scatti con la Fotocamera Samsung, come sempre.
2. Foto Note se ne accorge (osserva `DCIM/Camera`) e ti manda una notifica con l'anteprima.
   Se attivi "Apri l'editor da solo" e il permesso "Comparsa sopra altre app", l'editor si apre direttamente.
3. Nell'editor:
   - **S Pen** disegna (lo spessore segue la pressione);
   - **tasto laterale dell'S Pen premuto** = gomma;
   - con **"Solo S Pen"** attivo le dita spostano e zoomano la foto (pizzica per lo zoom);
   - colori, spessori S/M/L, annulla/ripeti, "Cancella tutto";
   - **Nota**: campo di testo (puoi dettare con il microfono della tastiera).
4. **Salva** crea una copia in `Immagini/FotoNote` (`<nome originale>_nota.jpg`).
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

Puoi annotare anche foto vecchie: dalla Galleria, *Condividi › Annota con Foto Note*.

## Installazione

Ogni modifica su `main` avvia una build su GitHub Actions che pubblica l'APK nella release
**latest** del repository.

1. Dal telefono, accedi a GitHub e apri *Releases › latest*, scarica `foto-note.apk`.
2. Aprilo: la prima volta Android chiede di consentire l'installazione da quella app (Chrome / Archivio).
3. Apri Foto Note e segui i 3 passaggi a schermo (permessi, monitoraggio, batteria).

Gli aggiornamenti si installano sopra la versione precedente (la chiave di firma è fissa, in `keystore/`).

## Struttura

- `PhotoWatcherService` — servizio in primo piano che osserva le nuove foto
- `EditorActivity` + `Ink.kt` — editor con S Pen, zoom, gomma, annulla
- `AnnotationIO.kt` — salvataggio della copia annotata, EXIF, archivio delle annotazioni
- `MainActivity` — impostazioni e permessi
