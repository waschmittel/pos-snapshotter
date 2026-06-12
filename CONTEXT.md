# PosSnapshotter

Desktop app that captures images (webcam, file, or rich text) and prints them on Epson multi-tone ESC/POS thermal printers, with an error-diffusion dithering pipeline tuned to the printer's thermal head.

## Language

### Dithering

**Dither Pipeline**:
The single public entry to dithering (`DitherPipeline`): render an image into printable chunks, or preview it as one dithered image. Stage order and chunking live behind it.
_Avoid_: dither engine, image processor

**Dither Params**:
The seven user-tunable dithering parameters (diffusion matrix, gamma, sharpness, contrast, gray levels, CLAHE tiles/clip). Always clamped on write.
_Avoid_: settings (that's the Settings Store), config

**Diffusion Matrix**:
An error-diffusion kernel (Floyd-Steinberg, Jarvis-Judice-Ninke, Sierra Lite, Flubba) distributing quantization error to neighbouring pixels.

**Chunk**:
A horizontal slice of the dithered image sent to the printer as one ESC/POS `GS ( L` graphics command.

### Printing

**Printer**:
The seam in front of the physical device (`Printer` interface): list available printers, print chunks. Production adapter is `EscPosPrinter`; tests use a recording fake.
_Avoid_: printer service

**Print Workflow**:
The orchestration from image to paper: scale to printer width, dither via the Dither Pipeline, send chunks to the Printer.

### Capture & preview

**Camera**:
The module owning the webcam: device discovery, the frame-grab loop, and device switching. Pushes frames to a consumer; all grabber access is serialized internally.
_Avoid_: grabber (that's the JavaCV internal), webcam panel

**Live Preview**:
The module that turns a source image into a dithered on-screen preview — continuous (polling) or debounced (poked after edits). Hides threading, null guards and EDT handoff.
_Avoid_: preview loop, dithering loop

### Documents & settings

**Document Store**:
Persistence for the text editor's HTML documents: explicit load/save plus one auto-save slot at a fixed path.
_Avoid_: auto-saver, file manager

**Settings Store**:
The single source of truth for user preferences (Dither Params, printer name, camera index, UI state). One instance per process; reads are live in-memory values, persistence is internal.
_Avoid_: preferences (the Java API is an implementation detail), config store
