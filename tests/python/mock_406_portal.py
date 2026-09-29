# Insula DICOM Viewer
# Copyright (C) 2026 Jemish Mayani
# SPDX-License-Identifier: GPL-3.0-or-later
import http.server, json, threading
STUDY=[{"0020000D":{"vr":"UI","Value":["1.2.3"]},"00100010":{"vr":"PN","Value":[{"Alphabetic":"DOE^JANE"}]},"00080020":{"vr":"DA","Value":["20260928"]},"00080061":{"vr":"CS","Value":["MR"]}}]
def mk(port, route, only_plain_json=False, reject_extras=False):
    class H(http.server.BaseHTTPRequestHandler):
        def log_message(self,*a): pass
        def do_GET(self):
            path=self.path.split('?')[0]; acc=self.headers.get('Accept','')
            if path.startswith(route+'/studies'):
                if only_plain_json and 'application/dicom+json' in acc and 'application/json' not in acc:
                    self.send_response(406); self.end_headers(); return
                if reject_extras and ('includefield' in self.path or 'fuzzymatching' in self.path):
                    self.send_response(400); self.end_headers(); return
                b=json.dumps(STUDY).encode(); self.send_response(200); self.send_header('Content-Type','application/json'); self.end_headers(); self.wfile.write(b); return
            if path in ('/','/index.html'):
                b=b'<!DOCTYPE html><html><body>Ruby PACS portal login</body></html>'; self.send_response(200); self.send_header('Content-Type','text/html'); self.end_headers(); self.wfile.write(b); return
            self.send_response(406); self.end_headers()   # portal rejects API-style requests
    s=http.server.ThreadingHTTPServer(('127.0.0.1',port),H); threading.Thread(target=s.serve_forever,daemon=True).start()
mk(8701,'/dicom-web',only_plain_json=True)
mk(8702,'/dcm4chee-arc/aets/DCM4CHEE/rs')
mk(8703,'',reject_extras=True)
print('up',flush=True); threading.Event().wait()
