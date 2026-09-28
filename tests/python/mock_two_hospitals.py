import http.server, json, sys, base64, threading, pydicom
from pydicom.data import get_testdata_file
CT=open(get_testdata_file('CT_small.dcm'),'rb').read()
MR=open(get_testdata_file('MR_small.dcm'),'rb').read()
ds_ct=pydicom.dcmread(get_testdata_file('CT_small.dcm')); ds_mr=pydicom.dcmread(get_testdata_file('MR_small.dcm'))
def mk(port, auth_ok, data, ds):
    st, se, sop = ds.StudyInstanceUID, ds.SeriesInstanceUID, ds.SOPInstanceUID
    class H(http.server.BaseHTTPRequestHandler):
        def log_message(self,*a): pass
        def do_GET(self):
            if not auth_ok(self.headers.get('Authorization','')):
                self.send_response(401); self.end_headers(); return
            p=self.path.split('?')[0]
            if p=='/dicom-web/studies':
                body=json.dumps([{"0020000D":{"vr":"UI","Value":[st]},"00100010":{"vr":"PN","Value":[{"Alphabetic":str(ds.PatientName)}]},
                  "00100020":{"vr":"LO","Value":[ds.PatientID]},"00080020":{"vr":"DA","Value":[ds.StudyDate]},"00080061":{"vr":"CS","Value":[ds.Modality]}}]).encode()
                ct='application/dicom+json'
            elif p==f'/dicom-web/studies/{st}/series':
                body=json.dumps([{"0020000E":{"vr":"UI","Value":[se]}}]).encode(); ct='application/dicom+json'
            elif p==f'/dicom-web/studies/{st}/series/{se}/instances':
                body=json.dumps([{"00080018":{"vr":"UI","Value":[sop]}}]).encode(); ct='application/dicom+json'
            elif p==f'/dicom-web/studies/{st}/series/{se}/instances/{sop}':
                b='BOUNDARY42'; body=(f'--{b}\r\nContent-Type: application/dicom\r\n\r\n').encode()+data+f'\r\n--{b}--\r\n'.encode()
                ct=f'multipart/related; type="application/dicom"; boundary={b}'
            else:
                self.send_response(404); self.end_headers(); return
            self.send_response(200); self.send_header('Content-Type',ct); self.send_header('Content-Length',str(len(body))); self.end_headers(); self.wfile.write(body)
    s=http.server.ThreadingHTTPServer(('127.0.0.1',port),H); threading.Thread(target=s.serve_forever,daemon=True).start()
mk(8601, lambda a: a=='Basic '+base64.b64encode(b'drsharma:s3cret').decode(), CT, ds_ct)   # "City Hospital" with password
mk(8602, lambda a: a=='Bearer tok-ABC123', MR, ds_mr)                                     # "Imaging Centre" with token
print('up',flush=True); threading.Event().wait()
