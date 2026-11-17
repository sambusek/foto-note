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

Gli aggiornamenti si installano sopra la versione precedente perché sono firmati sempre con la stessa chiave.

### Chiave di firma

La chiave che firma l'APK è privata e **non sta nel repository** (il repository è pubblico: chiunque
potrebbe usarla per firmare un'app falsa che si installa sopra Pastashoot). La build la riceve da due
secret del repository (*Settings › Secrets and variables › Actions*):

- `SIGNING_KEYSTORE_BASE64` — il file della chiave (`pastashoot.keystore`, alias `pastashoot`) codificato in base64
- `SIGNING_PASSWORD` — la sua password

Il file e la password vanno conservati in un posto sicuro (es. un gestore di password), fuori dal repository.
Se si perdono, si genera una chiave nuova: in quel caso l'app va disinstallata e reinstallata una volta.

## Struttura

- `MainActivity` — home (sopralluoghi) e impostazioni
- `SessionActivity` — sessione in corso (staging) e sopralluogo chiuso
- `EditorActivity` + `Ink.kt` — editor con S Pen, testi, passaggio tra le foto
- `LayoutEditor.kt` — layout dei campi di testo; `NoteBand.kt` — stampa dei testi nelle strisce
- `Session.kt`, `AnnotationIO.kt`, `Export.kt` — dati, salvataggio, ZIP e PDF
- `Theme.kt`, `Ui.kt`, `AppIcons.kt` — tema (chiaro/scuro/sistema, colore d'accento) e componenti Material 3
- `PhotoWatcherService`, `BatchActivity` — notifica dopo lo scatto, nota multipla fuori sessione

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

**Layout note…** (per sessione) definisce campi con nome e posizione: banda superiore o inferiore, oppure uno (testi sempre in due strisce aggiunte sopra e sotto la foto: angoli 20%, centro 60%)
dei quattro angoli della foto. Ogni foto ha poi i suoi valori; senza campi resta solo la nota libera, nella
banda inferiore. Ogni nuovo sopralluogo parte senza campi; con "Copia i campi da un sopralluogo…" si riusa lo schema di uno precedente. **Elimina…** in una sessione in corso la
abbandona senza salvare nulla.

**Campi automatici** nel layout: data e ora dello scatto, nome del sopralluogo, etichetta fissa. Compaiono
su tutte le foto della sessione senza doverli compilare.

**Tema**: chiaro, scuro o come il sistema; colore d'accento a scelta oppure quello di sistema (Material You).

**Tipi di campo**: testo libero, elenco di scelte (es. "Piano" con "P3, P4": nell'editor si sceglie dal menu ▾,
oppure si scrive; le voci si possono importare da un file .txt o .csv: una per riga, o separate da virgola/punto e virgola), data e ora dello scatto, nome del sopralluogo, etichetta fissa.

## Versioni e consolidamento

Durante il sopralluogo **Emetti versione** salva lo stato attuale (disegni e testi) in
`Immagini/Pastashoot/YYMMDD_NOME_v01`, `_v02`… senza chiudere la sessione: si può condividerne ZIP o PDF e
poi continuare a modificare. **Consolida definitivo** salva in `YYMMDD_NOME` e chiude le modifiche.
"Versioni emesse" elenca tutte le emissioni, ciascuna con il suo ZIP e PDF.

**Nomi nello ZIP**: prima di creare lo ZIP si sceglie uno schema con segnaposto, es. `{N}_{Piano}_{Stanza}`
(campi del layout, `{N}` progressivo, `{Originale}`, `{Sopralluogo}`, `{Utente}`, `{Data}`, `{Ora}`).
I valori sono quelli registrati al momento dell'emissione.

**Nome utente**: chiesto alla prima apertura; compare nel saluto, nei report PDF e nel campo automatico
"Nome utente".

## Template

Impostazioni › **Gestisci template…**: schemi di campi pronti, da scegliere quando si inizia un sopralluogo
(oppure da copiare nel layout delle note). Dal layout di una sessione: ⋮ › **Salva come template…**.
Ogni template si esporta come file `.pastashoot.json` (⋮ › Esporta e condividi); sull'altro telefono si
apre il file con Pastashoot o si usa **Importa da file**. Un template con lo stesso nome viene aggiornato.

**Pausa**: ⏸ nella sessione mette in pausa il sopralluogo (es. pranzo); le foto scattate in pausa non entrano.
**Nota generale**: ⋮ › Nota generale… — va nella copertina del report PDF e come `Nota_sopralluogo.txt` nello ZIP.
**Tela**: nell'editor l'icona "allarga" fa comparire una cornice con maniglie: trascinando i bordi si aggiunge
spazio bianco attorno alla foto, dove disegnare e annotare. Il file salvato include lo spazio aggiunto.

## Passare un sopralluogo in corso a un altro telefono

Nella sessione in corso: ⋮ › **Esporta per un altro telefono…** crea `YYMMDD_NOME.pastashoot.zip` con le foto
originali (GPS compreso), disegni, testi, campi, layout e nota generale (le versioni emesse non sono incluse).
Sull'altro telefono si apre il file con Pastashoot, oppure home › ⋮ › **Importa un sopralluogo…**: le foto vanno in
`Immagini/Pastashoot/Importati/CARTELLA` e il sopralluogo riparte come sessione in corso, a cui si aggiungono le
foto scattate da quel momento. Serve che sull'altro telefono non ci sia già una sessione in corso.

## Aggiornamenti

Impostazioni › **Aggiornamenti**: controlla la release "latest" su GitHub, scarica l'APK e avvia l'installazione
(la prima volta Android chiede di consentire le installazioni da Pastashoot). Con il controllo all'apertura attivo,
la home mostra un avviso quando c'è una build nuova. Finché il repository è privato serve un token GitHub
fine-grained di sola lettura (Contents: Read-only) sul solo repository `foto-note`.
