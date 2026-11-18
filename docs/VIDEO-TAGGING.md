# Free offline video-to-keywords

InstaBazaar can now inspect a seller's **uploaded MP4** and suggest fashion keywords from its visual content. Inference runs in the Java application on the local CPU. It does not call an AI API, need an API key, train a new model, scrape Instagram, transcribe audio or require a Python/Node server.

## First-time setup

From the repository root on Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-video.ps1 -Samples
```

This installs checksum-verified, pinned assets under ignored `data/`:

- CLIP ViT-B/32 quantized vision/text ONNX models and tokenizer, approximately 156 MB combined.
- FFmpeg/FFprobe 9.0.2 Windows essentials distribution, approximately 115 MB compressed.
- With `-Samples`, two licensed test photos and generated three-second MP4 clips in `data/video-demo/`.

The model files, native executables, videos and photos are **not committed to Git**. Re-running the script verifies and reuses existing downloads. Initial Maven dependencies must also be downloaded once. Processing then needs no hosted service or internet connection; normal CPU, disk and electricity usage still apply.

Restart the app after setup. For the existing local H2 demonstration:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run.ps1 -Demo
```

The normal MySQL run path remains `mvn spring-boot:run` with the database configuration from README.md.

On macOS/Linux, manually download the same pinned model assets into the configured directory, install FFmpeg/FFprobe using your OS package manager, and set the tool paths if necessary. The Windows setup script's `-ModelsOnly` switch skips the bundled FFmpeg download. Only the Windows setup/runtime has been verified in this build.

## Seller workflow

1. Register or sign in as a **SELLER**. A buyer cannot submit analyses.
2. Open **Seller studio → Video → keywords**, or `/seller/videos/new`.
3. Upload an MP4 and confirm both usage permission and that it shows one product.
4. The background worker extracts four frames and runs the local visual model. The status page refreshes while processing.
5. Select only the suggested tags that look correct. No tag is automatically published.
6. Continue to the product form. A new listing can receive an approved type/colour-based title and category; fill in price, sizes, description and stock yourself.
7. Correct the editable tags/details and save. The saved keywords are now searched by the existing catalogue search.

For an existing product, use its editor's video link. Approved tags are merged with its seller tags; existing title, category and colour are not silently overwritten. Check those fields yourself before saving. Opening the separate analyser does not preserve unsaved form edits; upload first or save a draft.

## Input limits and privacy

- Standard MP4 container, not just a renamed extension; exactly one video stream.
- 30 MB maximum, 0.2–60 seconds, at least 64 pixels per dimension, at most 2,073,600 pixels per frame and aspect ratio at most 6:1. Standard 1080p landscape/portrait fits; 4K does not.
- One product per clip. Multi-product detection, tracking, product segmentation and automatic creation of several listings are not supported.
- Four frames, sampled at approximately 10%, 35%, 60% and 85% of the clip; audio is ignored.
- One pending analysis per seller, ten analyses in a rolling 24 hours, one local worker and at most two waiting jobs.
- Decoders run with explicit arguments, disabled stdin, restricted protocols, one decoding thread and per-process timeouts. They do not receive seller-controlled shell commands or paths.
- Native decoding is not an operating-system sandbox. Before public deployment, isolate decoder processes, review upstream security updates, monitor resources and add operational rate limiting; this is a loopback-bound local MVP.
- Original filenames do not determine storage paths. Uploads live in generated UUID directories under a **dedicated private work directory**, never the public media directory.
- Source videos and extracted frames are deleted after either success or failure. At startup, interrupted jobs become retryable failures and abandoned UUID work directories are cleaned using a fixed file allowlist. Do not configure the work path to a shared document directory.
- Only the analysis status, suggested/approved labels, relative scores, duration, frame count and ownership/product references remain in the database. Videos are not published or retained as product media.

## What the model does—and does not do

Java ONNX Runtime runs the two CLIP encoders. A local tokenizer embeds fixed fashion prompts. RGB frames use the model's 224×224 centre-crop/normalisation. Frame embeddings are averaged and compared with text embeddings for:

- Product type: dress, kurti, saree, shirt, T-shirt, top, jacket, trousers or skirt.
- Colour: twelve common colour labels, evaluated in the context of the suggested product type.
- Appearance: plain, floral, striped, checked, polka dot or embroidered, with a stricter acceptance threshold.

The system abstains when a winner is insufficiently distinct. Group softmax scores are **relative matches, not calibrated probabilities or accuracy estimates**. Category/colour suggestions require a relative score of at least 0.35, appearance at least 0.60, a minimum cosine similarity of 0.18 and a top-two score margin of 0.08. These are conservative heuristics, not a validated accuracy guarantee.

Background scenery, people, accessories, lighting, video cuts, unusual garments and the fixed vocabulary can confuse the model. A centre crop can miss details near the edge of the frame. Indian garment categories may be less reliable than common categories. Do not infer price, stock, size, brand authenticity, fabric composition or seller identity from these results. Seller approval is mandatory, and optional appearance tags can be omitted entirely.

This is genuine visual inference, not extraction of a supplied filename/title/caption. It is also **not** OCR, unrestricted image captioning, semantic buyer search, screenshot search, or access to everything a buyer watched on Instagram. It indexes only approved attributes of products sellers add to this catalogue.

## Configuration

| Variable | Default / meaning |
| --- | --- |
| VIDEO_MODEL_DIR | ./data/models/clip-vit-base-patch32 |
| VIDEO_WORK_DIR | ./data/video-work; dedicated private temporary directory |
| VIDEO_FFMPEG | Bundled Windows tool when present; otherwise ffmpeg on PATH |
| VIDEO_FFPROBE | Bundled Windows tool when present; otherwise ffprobe on PATH |

The Windows launch script loads these keys from `.env`. Direct Maven runs need exported environment variables. Missing tools/models produce an actionable message and do not prevent manual catalogue use or application startup. The first analysis loads models and embeds vocabulary; later analyses reuse the sessions and prompt embeddings. CPU use is intentionally bounded to two inference threads.

## Tests

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-video.ps1 -Samples
.\mvnw.cmd verify '-Dvideo.model.tests=true'
```

The opt-in model tests use real local FFmpeg and CLIP, not mocked inference: two sample videos must produce dress/blue and dress/red keywords, and a fake MP4 container must fail decoding. The opt-in real-HTTP test uploads an anonymously named MP4, waits for actual local inference, approves dress/red, saves a product and checks that a buyer's `red dress` search retrieves it. Unapproved appearance guesses are not indexed.

The normal default test run does not download models or samples; external-asset tests are explicitly skipped unless the flag is set. Core tests still cover permissions, CSRF, job ownership, review gating, edited tags, background job transitions, upload validation, quota limits, interruption recovery and temporary-file cleanup. Worker orchestration tests mock inference; these are separate from the opt-in real-model tests.

## Sources and licences

- Model: [Xenova/clip-vit-base-patch32](https://huggingface.co/Xenova/clip-vit-base-patch32), pinned revision `6b7c523703ae26e39be4b03774966f8571c7f523`, quantized ONNX export of [OpenAI CLIP](https://github.com/openai/CLIP). The model card declares MIT; retain the original project's [MIT licence](https://github.com/openai/CLIP/blob/main/LICENSE) when redistributing its assets. SHA-256 values are pinned in the setup script.
- Java inference: Microsoft ONNX Runtime 1.32.0 (MIT) and DJL Hugging Face tokenizers 0.40.0 (Apache-2.0), obtained through Maven. The tokenizer's Windows native library is packaged in its Maven artifact.
- FFmpeg: GyanD/codexffmpeg essentials 9.0.2, pinned release asset/SHA-256. Its archive retains the distribution's GPL licence. Binaries are downloaded locally, not bundled in this repository; review its redistribution obligations if packaging a deployment.
- Optional sample photos: Unsplash image sources `photo-1539008835657-9e8e9680c956` and `photo-1595777457583-95e059d581b8`, downloaded under the [Unsplash License](https://unsplash.com/license). They are test inputs, not seller inventory or evidence of verified purchases, and are not committed.
