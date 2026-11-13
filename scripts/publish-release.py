"""Publish locally verified dist/ assets using existing Git credentials, without logging tokens."""
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import urllib.parse

repo = 'Ibrahim200546/UniPlan'
manifest = json.loads(Path('dist/update.json').read_text())
tag = 'v' + manifest['versionName']
token = os.environ.get('GH_TOKEN') or os.environ.get('GITHUB_TOKEN')
if not token:
    result = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n\n',
                            text=True, capture_output=True, check=True)
    credentials = dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)
    token = credentials.get('password')
if not token:
    raise SystemExit('GitHub authentication is required')

def request(path, payload=None, method=None, content_type='application/json'):
    url = path if path.startswith('https://') else 'https://api.github.com/repos/' + repo + path
    data = payload if isinstance(payload, bytes) else (json.dumps(payload).encode() if payload is not None else None)
    req = urllib.request.Request(url, data=data, method=method,
        headers={'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json',
                 'Content-Type': content_type, 'User-Agent': 'UniPlan-release'})
    with urllib.request.urlopen(req, timeout=120) as response:
        return json.load(response)

commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
notes = Path('dist/RELEASE_NOTES.md').read_text(encoding='utf-8')
release = request('/releases', {'tag_name': tag, 'target_commitish': commit, 'name': 'UniPlan ' + manifest['versionName'],
                              'body': notes, 'draft': True, 'prerelease': False}, 'POST')
for name in ['UniPlan.apk', 'SHA256SUMS', 'update.json']:
    data = Path('dist', name).read_bytes()
    url = release['upload_url'].split('{')[0] + '?name=' + urllib.parse.quote(name)
    asset = request(url, data, 'POST', 'application/vnd.android.package-archive' if name.endswith('.apk') else 'application/octet-stream')
    assert asset['state'] == 'uploaded' and asset['size'] == len(data), 'Incomplete upload'
ready = request('/releases/' + str(release['id']))
assert {a['name'] for a in ready['assets']} >= {'UniPlan.apk', 'SHA256SUMS', 'update.json'}
published = request('/releases/' + str(release['id']), {'draft': False, 'make_latest': 'true'}, 'PATCH')
print(published['html_url'])
