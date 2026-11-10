#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
mkdir -p tests/build
python3 - <<'PY'
import json,pathlib,xml.etree.ElementTree as ET,os
root=pathlib.Path.cwd()
capture=pathlib.Path(os.environ.get('KW66_REPLAY_CAPTURE',root/'tests/private/capture.jsonl'))
target=root/'tests/build/capture.tsv'
target.unlink(missing_ok=True)
if capture.is_file():
    with capture.open() as src,target.open('w') as out:
        for line in src:
            r=json.loads(line)
            if r.get('type')=='notification':
                out.write(r['characteristic'][4:8]+'\t'+r['hex']+'\n')
    print('Optional private capture found; replay enabled (input remains local).')
else: print('No private capture supplied; running self-contained protocol tests.')
for xml in (root/'app/src/main').rglob('*.xml'): ET.parse(xml)
print('Manifest/resource XML parsed.')
PY
javac --release 17 -d tests/build app/src/main/java/dev/kw66/glass/Protocol.java app/src/main/java/dev/kw66/glass/SchedulePolicy.java app/src/main/java/dev/kw66/glass/CallPolicy.java app/src/main/java/dev/kw66/glass/AiEngine.java app/src/core/java/dev/kw66/glass/LocalAi.java tests/ProtocolTest.java
if [[ -f tests/build/capture.tsv ]]; then java -cp tests/build ProtocolTest tests/build/capture.tsv;
else java -cp tests/build ProtocolTest; fi
python3 tests/storage_test.py
python3 tests/toolchain_test.py
