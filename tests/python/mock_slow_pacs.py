import http.server, json, threading, time, io, pydicom, sys
from pydicom.data import get_testdata_file
from pydicom.uid import generate_uid
base=pydicom.dcmread(get_testdata_file('CT_small.dcm'))
ST='1.2.3.4.5'; SERIES={}
for s in range(3):
    se=f'1.2.3.4.5.{s+1}'; lst=[]
    for i in range(60):
        d=base.copy(); d.StudyInstanceUID=ST; d.SeriesInstanceUID=se; d.SOPInstanceUID=generate_uid(); d.InstanceNumber=i+1
        b=io.BytesIO(); d.save_as(b); lst.append((d.SOPInstanceUID,b.getvalue()))
    SERIES[se]=lst
LAT=0.12
def mk(port, series_ok):
    class H(http.server.BaseHTTPRequestHandler):
        protocol_version='HTTP/1.1'
        def log_message(self,*a): pass
        def send(self,code,ct,body):
            self.send_response(code); self.send_header('Content-Type',ct); self.send_header('Content-Length',str(len(body))); self.end_headers(); self.wfile.write(body)
        def do_GET(self):
            time.sleep(LAT)
            p=self.path.split('?')[0].split('/')
            # /dicom-web/studies/ST/series[/SE[/instances[/SOP]]]
            if p[-1]=='series': return self.send(200,'application/dicom+json',json.dumps([{"0020000E":{"vr":"UI","Value":[s]}} for s in SERIES]).encode())
            if p[-1]=='instances': return self.send(200,'application/dicom+json',json.dumps([{"00080018":{"vr":"UI","Value":[u]}} for u,_ in SERIES[p[-2]]]).encode())
            B='bnd_7a3f'
            if len(p)>=2 and p[-2]=='series':
                if not series_ok: return self.send(404,'text/plain',b'nope')
                body=b''.join(b'--'+B.encode()+b'\r\nContent-Type: application/dicom\r\n\r\n'+d+b'\r\n' for _,d in SERIES[p[-1]])+b'--'+B.encode()+b'--\r\n'
                return self.send(200,f'multipart/related; type="application/dicom"; boundary={B}',body)
            if len(p)>=2 and p[-2]=='instances':
                d=dict(SERIES[p[-3]])[p[-1]]
                return self.send(200,f'multipart/related; type="application/dicom"; boundary={B}',b'--'+B.encode()+b'\r\nContent-Type: application/dicom\r\n\r\n'+d+b'\r\n--'+B.encode()+b'--')
            self.send(404,'text/plain',b'x')
    s=http.server.ThreadingHTTPServer(('127.0.0.1',port),H); threading.Thread(target=s.serve_forever,daemon=True).start()
mk(8801,True); mk(8802,False)
print('up',flush=True); threading.Event().wait()
