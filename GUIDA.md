# Pastashoot — Guida rapida

Pastashoot raccoglie le foto di un sopralluogo, le annota con pennino, dita e note di testo, e alla fine consegna una cartella ordinata con ZIP e report PDF. Funziona su telefoni e tablet Android 10 o successivi.

## Installazione e primo avvio

1. Scarica `pastashoot.apk` dalla pagina [Releases › latest](https://github.com/sambusek/foto-note/releases/tag/latest) e aprilo. La prima volta Android chiede di consentire l'installazione da Chrome o da Archivio.
2. Scrivi il tuo nome: comparirà nel saluto e potrà finire nei campi delle note e nei nomi dei file.
3. Concedi l'accesso alle foto e alla posizione GPS delle foto, poi le notifiche.
4. Per accorgersi subito dei nuovi scatti, lascia l'app libera di lavorare in background: Impostazioni del telefono › App › Pastashoot › Batteria › «Senza restrizioni».

Gli aggiornamenti si installano sopra la versione precedente, senza perdere nulla. Non disinstallare mai l'app: è l'unico modo per perdere sopralluoghi e annotazioni.

## Fare un sopralluogo

Un sopralluogo raccoglie da solo tutte le foto scattate con la fotocamera del telefono dal momento in cui lo inizi. Non serve scattare dall'app.

1. Nella Home tocca **Inizia sopralluogo…**, scrivi il nome (diventerà il nome della cartella) e, se vuoi, scegli un template.
2. Scatta con la fotocamera di sempre, o dal pulsante **Fotocamera** nella schermata del sopralluogo. Le foto compaiono nella griglia.
3. Se ti servono foto fatte prima, usa ⋮ › **Aggiungi foto già scattate…**.
4. Per escludere una foto finita dentro per sbaglio, tienila premuta. Tienila premuta di nuovo per riammetterla.
5. Per una pausa (pranzo, foto personali) tocca **Metti in pausa**: gli scatti fatti in pausa restano fuori. Riprendi quando torni in cantiere.

Puoi avere un solo sopralluogo in corso alla volta. Lo ritrovi nella Home sotto «Sessione in corso».

## Annotare le foto

Tocca una foto della griglia per aprirla nell'editor. Le modifiche si salvano quando passi a un'altra foto o chiudi, e restano modificabili finché il sopralluogo non è consolidato.

![La barra dell'editor: in alto i comandi, in basso gli strumenti](docs/barra-editor.svg)

- **Disegno**: si disegna con la S Pen, il tratto segue la pressione. Con le dita sposti e ingrandisci la foto. Tenendo premuto il tasto della S Pen cancelli al volo.
- **Strumenti** (barra in basso): colore, spessore e gomma. Annulla, ripeti e Salva sono nella barra in alto. Il pulsante con la mano permette di disegnare anche con il dito.
- **Note e campi**: il pulsante delle note apre il testo libero e i campi del layout (es. Piano, Locale). Su tablet in orizzontale il pannello sta di lato.
- **Foto dopo foto**: con le frecce colorate, o scorrendo, passi alla foto precedente o successiva senza tornare alla griglia.
- **Tela più grande**: ⋮ › **Allarga la tela** aggiunge spazio bianco intorno alla foto, per schizzi e quote. Trascina i bordi o le maniglie, poi torna al disegno.
- **Stessa nota su più foto**: nella griglia del sopralluogo usa il pulsante del testo, seleziona le foto e scrivi una volta sola.

Il testo e i campi finiscono in bianco su una fascia nera aggiunta sopra o sotto la foto: l'immagine originale non viene coperta né modificata.

## Layout delle note e template

Il layout decide quali campi compaiono sulle foto e dove. Lo imposti per ogni sopralluogo da ⋮ › **Layout delle note…**; un template lo prepara una volta per tutti i sopralluoghi simili.

| Tipo di campo | Cosa contiene | Esempio |
| --- | --- | --- |
| Testo | Scritto a mano foto per foto | Osservazione |
| Scelta | Una voce da una lista, importabile da TXT o CSV | Piano: P3, P4 |
| Data, Ora | Riempiti da soli con il momento dello scatto | 17/11/2026 |
| Sopralluogo, Utente | Riempiti da soli con il nome del sopralluogo e il tuo | 261118 SAL |
| Fisso | Stesso testo su tutte le foto | Committente |

Ogni campo va in una fascia, sopra o sotto la foto, e in una posizione: sinistra, centro o destra. L'anteprima nel layout mostra dove cade. Per ogni campo puoi mostrare o nascondere l'etichetta prima del testo.

I template si gestiscono da Impostazioni › **Template di sopralluogo**. Si possono condividere e importare su un altro telefono; con **Copia i campi da…** riprendi i campi di un altro sopralluogo o template.

## Versioni, chiusura e consegna

Le foto annotate non vengono salvate una per una: escono tutte insieme quando emetti una versione o consolidi. Gli originali restano sempre intatti.

- **Emetti versione** salva le foto annotate in `Immagini › Pastashoot › AAMMGG_NOME_v01`, poi `_v02` e così via. Il sopralluogo resta aperto e modificabile. Le versioni emesse sono in ⋮ › **Versioni emesse**.
- **Consolida definitivo** salva la versione finale in `Immagini › Pastashoot › AAMMGG_NOME` e chiude il sopralluogo: da quel momento disegni e note non si modificano più. Nella Home compare con il lucchetto.
- **Nota generale** (⋮ › **Nota generale…**): un testo sull'intero sopralluogo, che va in testa al PDF e come file di testo nello ZIP.

Da un sopralluogo chiuso puoi creare e condividere:

- **ZIP** delle foto, con i nomi dei file costruiti come vuoi da numero, nome originale, sopralluogo, utente, data, ora e campi (es. `01_P3_Bagno.jpg`);
- **Report PDF** con le foto annotate, le loro note e la nota generale.

### Passare un sopralluogo a un altro telefono

1. Sul primo telefono, a sopralluogo aperto: ⋮ › **Esporta per un altro telefono…** e invia il file (mail, Drive, WhatsApp).
2. Sull'altro telefono: Home › **Importa un sopralluogo…** e scegli il file. Arrivano foto, disegni, note e campi, e si continua da lì.

L'importazione funziona solo se sull'altro telefono non c'è già un sopralluogo in corso.

## Impostazioni e consigli

Le impostazioni si aprono dall'icona in alto a destra nella Home.

- **Profilo**: il tuo nome, usato nel saluto e nel campo Utente.
- **Aggiornamenti**: controlla e installa la nuova versione direttamente dall'app. Il campo del token può restare vuoto.
- **Tema**: chiaro, scuro o come il sistema, e il colore d'accento.
- **Dopo lo scatto**: notifica per annotare subito ed eventuale apertura automatica dell'editor.
- **Foto singole, fuori sessione**: per annotare una foto qualsiasi senza aprire un sopralluogo, anche dalla condivisione della Galleria.

Tre consigli pratici:

1. Inizia il sopralluogo prima di scattare: le foto precedenti vanno aggiunte a mano.
2. Emetti una versione a fine giornata, anche se il lavoro non è finito: è una copia di sicurezza nella Galleria.
3. Prima di cambiare telefono, esporta il sopralluogo aperto e condividi i template.
