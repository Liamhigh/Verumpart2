I am the GitHub Copilot Chat Assistant.

Below are precise, actionable steps to build Verumpart2 locally, validate the core flows, and prepare a branch/PR. Follow them in order; copy/paste commands where shown. If a step references a file or directory that doesn’t exist in the repo yet, the instructions include what to create.

Prerequisites
- Git configured with your GitHub account.
- Node.js 20.x (use nvm: nvm install 20; nvm use 20)
- npm (bundled with Node) or yarn/pnpm
- Optional but recommended: Firebase CLI (npm i -g firebase-tools), Java JDK & Android SDK/Gradle (for APK work), jq for JSON parsing.
- A terminal with basic UNIX commands (macOS / Linux); PowerShell on Windows with adjustments where noted.

1) Clone the repo
- git clone https://github.com/Liamhigh/Verumpart2.git
- cd Verumpart2
- git checkout main
- git pull --ff-only origin main

2) Inspect repo to see what’s present
- ls -la
- find . -maxdepth 2 -type d -print
- find . -type f -name 'index.html' -o -name '*.ts' -o -name '*.js' -o -name 'app-release.apk' -print

Note files/dirs we expect for the build: web/, web/assets/, web/downloads/, functions/, functions/src/, functions/assets/, capacitor-app/android/.../app-release.apk, .github/workflows/.

If something is missing, keep going — steps below create safe stubs.

3) Create a feature branch for your work
- git checkout -b feat/bootstrap-verumpart2

4) Add/validate web UI scaffold
Paths to use:
- web/index.html
- web/assistant.html
- web/institutions.html
- web/assets/ (videos, logo)
- web/downloads/verum-omnis.apk (APK target)

Create directories:
- mkdir -p web/downloads web/assets

Minimal index.html (Tailwind CDN dark scaffold)
- Create web/index.html with:
  - a single valid HTML document (one DOCTYPE/HTML/HEAD/BODY)
  - a unified header with nav links: Home, Assistant (/assistant.html), Features, Institutions, Legal, Tax
  - dark theme classes (Tailwind CDN recommended)
  - CTA for APK: href="/downloads/verum-omnis.apk" (it can be a placeholder until APK exists)

Minimal assistant.html
- Add upload input, small JS to compute SHA‑512 locally:
  - Example (client JS):
    const buf = await file.arrayBuffer();
    const hash = await crypto.subtle.digest('SHA-512', buf);
    const hex = Array.from(new Uint8Array(hash)).map(b=>b.toString(16).padStart(2,'0')).join('');
    // display hex
- Add controls to call /v1/verify and /v1/seal via fetch (these will be no-op until functions implemented).

Institutions page
- web/institutions.html should be a single HTML file with a responsive grid:
  - For md+ show two videos side-by-side, for mobile stack vertically.
  - video src="/assets/institutions1.mp4" and "/assets/institutions2.mp4" (use placeholders if missing).

Commit these files:
- git add web/
- git commit -m "chore(web): add index, assistant, institutions scaffold"

5) Functions skeleton and required routes
If functions/ is missing, create it and a minimal Node project.

Create directories and basic package.json:
- mkdir -p functions/src/routes functions/src/constants functions/assets
- cd functions
- npm init -y
- echo "20" > .nvmrc
- npm install express cors multer helmet compression pino zod

Create functions/src/index.js (or index.ts)
- Minimal Express app that mounts /v1 routes and can be run locally.
- Example (index.js):
  const express = require('express');
  const app = express();
  app.use(express.json());
  const healthRouter = require('./routes/health');
  const logicRouter = require('./routes/logic');
  app.use('/v1/health', healthRouter);
  app.use('/v1/logic', logicRouter);
  const port = process.env.PORT || 3000;
  app.listen(port, ()=>console.log(`listening ${port}`));
  module.exports = app;

Create functions/src/routes/health.js
- Export a router that responds GET / → { ok: true, env, uptime, node: process.version }
  const express = require('express');
  const router = express.Router();
  router.get('/', (req,res) => res.json({ ok:true, env: process.env.NODE_ENV||'local', uptime: process.uptime(), node: process.version }));
  module.exports = router;

Create functions/src/constants/logic.js
- Export VERUM_LOGIC: version, sha512 (full 128-hex string — placeholder until you add the real sha), path to functions/assets/Verum_Omnis_Ideal_Logic_With_Brains_Sealed.pdf
  module.exports = { VERUM_LOGIC: { version: 'VO-LOGIC-PLACEHOLDER', sha512: '', path: require('path').join(__dirname,'../assets/Verum_Omnis_Ideal_Logic_With_Brains_Sealed.pdf') } };

Create functions/src/routes/logic.js
- GET / → if file exists, stream bytes and set headers:
  X-Verum-Logic-Version: VERUM_LOGIC.version
  X-Verum-Logic-SHA512: VERUM_LOGIC.sha512
  Content-Type: application/pdf
  If PDF missing, return 501 JSON { ok:false, error:'logic pdf missing' }.

Add start script to functions/package.json:
- "start": "node src/index.js"

Commit:
- cd ..
- git add functions/
- git commit -m "chore(functions): add skeleton, /v1/health and /v1/logic stubs"

6) Logic PDF SHA-512 (if you have the PDF)
If you have the canonical PDF, place it at:
- functions/assets/Verum_Omnis_Ideal_Logic_With_Brains_Sealed.pdf

Compute full SHA‑512 and place into constants:
- openssl method:
  openssl dgst -sha512 -binary functions/assets/Verum_Omnis_Ideal_Logic_With_Brains_Sealed.pdf | xxd -p -c 256
- Node method:
  node -e "const fs=require('fs'),crypto=require('crypto');console.log(crypto.createHash('sha512').update(fs.readFileSync('functions/assets/Verum_Omnis_Ideal_Logic_With_Brains_Sealed.pdf')).digest('hex'))"
- Paste resulting 128-hex string into functions/src/constants/logic.js as VERUM_LOGIC.sha512 and adjust version string.

Commit:
- git add functions/assets functions/src/constants/logic.js
- git commit -m "feat(logic): add canonical PDF and sha512"

7) Add logic-guard GitHub Actions workflow
Create .github/workflows/logic-guard.yml with a job that:
- checks out repo
- if functions/assets/Verum...pdf exists compute sha512:
  - sha=$(openssl dgst -sha512 -binary functions/assets/Verum_Omnis_Ideal_Logic_With_Brains_Sealed.pdf | xxd -p -c 256)
  - grep -q "$sha" functions/src/constants/logic.js || (echo "SHA mismatch" && exit 1)
- If file missing, skip job (echo "PDF missing — skipping logic guard").

A minimal workflow skeleton:
- name: Logic Guard
  on: [pull_request]
  jobs:
    check:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - name: Check PDF
          run: |
            if [ ! -f functions/assets/Verum_Omnis_Ideal_Logic_With_Brains_Sealed.pdf ]; then echo "PDF not present — skipping"; exit 0; fi
            sha=$(openssl dgst -sha512 -binary functions/assets/Verum_Omnis_Ideal_Logic_With_Brains_Sealed.pdf | xxd -p -c 256)
            if ! grep -q "$sha" functions/src/constants/logic.js; then echo "Logic PDF SHA mismatch"; exit 1; fi

Commit workflow:
- git add .github/workflows/logic-guard.yml
- git commit -m "ci: add logic-guard workflow"

8) APK wiring (optional)
Search for built APK:
- find . -type f -name 'app-release.apk'
If found, copy:
- mkdir -p web/downloads
- cp <path-to-apk> web/downloads/verum-omnis.apk
- Update web/index.html CTA to href="/downloads/verum-omnis.apk"

If APK not found, leave CTA pointing to placeholder and document build steps in README.

9) Local smoke validation
Start functions server locally (from functions/):
- cd functions
- npm install
- npm run start   # or node src/index.js

Verify /v1/health:
- curl http://localhost:3000/v1/health | jq
Expected: { "ok": true, "env": "local", ... }

Verify /v1/logic (if PDF present):
- curl -I http://localhost:3000/v1/logic
Expected headers: X-Verum-Logic-Version and X-Verum-Logic-SHA512 set to values in constants.

Serve the static web pages:
- From repo root:
  npx http-server web -p 8080
  OR
  python3 -m http.server --directory web 8080
Open in browser:
- http://localhost:8080/index.html
- http://localhost:8080/assistant.html
- http://localhost:8080/institutions.html
Check:
- header/footer present and consistent
- assistant page computes SHA‑512 locally for a chosen file
- institutions page shows two videos responsively
- APK download link works if APK present

10) Create PR
- git push origin feat/bootstrap-verumpart2
- Open a draft PR with title:
  chore: bootstrap Verumpart2 — web scaffold, health+logic routes, APK wiring
- PR body should include the acceptance checklist:
  - [ ] branch created from main
  - [ ] web pages share header/footer & dark theme
  - [ ] /v1/health returns { ok:true }
  - [ ] /v1/logic returns PDF with X-Verum-Logic-* headers (if PDF present)
  - [ ] logic-guard CI present
  - [ ] web/institutions.html shows two videos responsively
  - [ ] APK wired to /downloads/verum-omnis.apk (if present)
  - [ ] No regressions to /v1/verify, /v1/seal, /v1/anchor, /v1/contradict

Use GitHub UI or gh CLI:
- gh pr create --title "chore: bootstrap Verumpart2 — web scaffold, health+logic routes, APK wiring" --body "<paste checklist and smoke instructions>" --draft

11) CI & final notes before merging
- Add repository secrets if you plan to deploy (FIREBASE_TOKEN, ANCHOR_PRIVATE_KEY, RPC URL, AI keys).
- Ensure GitHub runners have openssl available (they do on ubuntu-latest).
- If you will run firebase emulators in CI add steps to install firebase-tools and authenticate appropriately.
- Don’t change sealing/legal wording (PDF protocol) per project constraints.

If anything blocks (missing PDF file you intended to use, a needed secret, or merge conflicts), stop and provide:
- exact file path and line numbers where the problem occurs, or
- the name of the missing secret(s) (e.g., FIREBASE_TOKEN, ANCHOR_PRIVATE_KEY).

If you want, I will:
- produce exact file contents for index.html, assistant.html, the Express files, and the GitHub action (copy-paste ready), or
- push these changes directly to a branch and open the draft PR for you (I am prepared to do that if you confirm). Which do you want next?