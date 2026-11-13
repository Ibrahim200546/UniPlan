"""Emit updater metadata from the actual APK build output, never from a handwritten version."""
import hashlib
import json
from pathlib import Path

metadata = json.loads(Path('app/build/outputs/apk/supabase/debug/output-metadata.json').read_text())
assert metadata['applicationId'] == 'kz.uniplan.app'
element, = metadata['elements']
apk = Path('dist/UniPlan.apk')
manifest = dict(applicationId=metadata['applicationId'], versionCode=element['versionCode'],
                versionName=element['versionName'], sha256=hashlib.sha256(apk.read_bytes()).hexdigest())
Path('dist/update.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
