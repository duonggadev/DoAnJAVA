import json
import os
import subprocess
import tempfile
import time
from pathlib import Path
from urllib.request import urlopen
from playwright.sync_api import sync_playwright, expect
from mysql_verify_db import create as create_verify_db, drop as drop_verify_db, app_args as mysql_app_args

root = Path(__file__).resolve().parents[1]
url = 'http://127.0.0.1:5184'
import shutil
java = str(Path(os.environ['JAVA_HOME'])/'bin/java.exe') if os.environ.get('JAVA_HOME') else shutil.which('java')

def wait(url):
    for attempt in range(60):
        try:
            with urlopen(url, timeout=2) as response:
                assert response.status == 200
            return
        except OSError:
            time.sleep(.5)
    raise RuntimeError('service did not start')

def mutate(page, path, method='POST', data=None):
    csrf = page.request.get(url + '/api/csrf').json()
    return page.request.fetch(url + '/api' + path, method=method,
                              headers={csrf['header']:csrf['token']}, data=data)

def log_in(browser, user, password=None, width=1366):
    page = browser.new_page(viewport={'width':width,'height':900})
    page.on('dialog', lambda dialog: dialog.accept())
    page.goto(url)
    if password:
        page.get_by_label('Mã tài khoản').fill(user)
        page.get_by_label('Mật khẩu', exact=True).fill(password)
        page.get_by_role('button', name='Đăng nhập', exact=True).click()
    else:
        page.get_by_role('button', name=user, exact=True).click()
    expect(page.get_by_role('button', name='Đăng xuất')).to_be_visible()
    return page

def set_field(page, label, value):
    page.locator('.management-form').get_by_label(label, exact=True).fill(value)



def run():
    import base64
    from urllib.parse import urlparse, parse_qs
    from datetime import datetime
    from zoneinfo import ZoneInfo
    with sync_playwright() as pw:
        browser=pw.chromium.launch(channel='chrome')
        errors=[]
        teacher=browser.new_page()
        teacher.on('pageerror',lambda error:errors.append(str(error)))
        teacher.goto(url)
        teacher.get_by_label('Mã tài khoản',exact=True).fill('gv01')
        teacher.get_by_label('Mật khẩu',exact=True).fill('DemoSoLop2026')
        teacher.get_by_role('button',name='Đăng nhập',exact=True).click()
        expect(teacher.get_by_role('heading',name='Lịch buổi học')).to_be_visible()
        teacher.get_by_role('button',name='Mở điểm danh',exact=True).click()
        qr=teacher.get_by_role('img',name='Mã QR điểm danh',exact=True)
        expect(qr).to_be_visible()
        payload=teacher.evaluate("""async () => {
            const source=document.querySelector('.code-box img').src;
            const image=new Image(); image.src=source; await image.decode();
            const {readPin}=await import('/src/QrCamera.jsx');
            return readPin(image,image.width,image.height,document.createElement('canvas'));
        }""")
        assert payload.startswith('http://127.0.0.1:5184/#/check-in?'),payload
        params=parse_qs(urlparse(payload).fragment.split('?')[1])
        session_id=int(params['session'][0])
        assert len(params['token'][0])==43
        pin=teacher.locator('.pin-code b').inner_text()
        def login_student(code, link=None, delay=0):
            page=browser.new_page(viewport={'width':390,'height':844},is_mobile=True,has_touch=True)
            page.on('pageerror',lambda error:errors.append(str(error)))
            page.goto(link or url)
            expect(page.get_by_role('heading',name='Đăng nhập',exact=True)).to_be_visible()
            expect(page.get_by_role('button',name='Đăng nhập',exact=True)).to_be_enabled()
            if delay: page.wait_for_timeout(delay)
            page.get_by_label('Mã tài khoản',exact=True).fill(code)
            page.get_by_label('Mật khẩu',exact=True).fill('DemoSoLop2026')
            page.get_by_role('button',name='Đăng nhập',exact=True).click()
            return page
        anonymous=browser.new_page()
        csrf=anonymous.request.get(url+'/api/csrf').json()
        denied=anonymous.request.post(url+f'/api/sessions/{session_id}/check-in',
            headers={csrf['header']:csrf['token']},data={'pin':pin})
        assert denied.status==401,denied.status
        assert anonymous.request.get(url+f'/api/sessions/{session_id}/check-in-context').status==401
        student=login_student('sv01',payload,delay=30000)
        expect(student.get_by_role('heading',name='Xác nhận điểm danh')).to_be_visible()
        expect(student.get_by_role('heading',name='Cơ sở dữ liệu')).to_be_visible()
        student.get_by_role('button',name='Xác nhận điểm danh',exact=True).click()
        expect(student.get_by_text('Đã có kết quả · Có mặt')).to_be_visible()
        student.reload()
        expect(student.get_by_role('heading',name='Đăng nhập',exact=True)).to_be_visible()
        student.get_by_label('Mật khẩu',exact=True).fill('DemoSoLop2026')
        student.get_by_role('button',name='Đăng nhập',exact=True).click()
        expect(student.get_by_text('Đã có kết quả · Có mặt')).to_be_visible()
        student.screenshot(path=str(Path(os.environ['TEMP'])/'solop-checkin-phone.png'),full_page=True)
        student2=login_student('sv02')
        expect(student2.get_by_role('button',name='Xác nhận điểm danh',exact=True)).to_be_visible(timeout=10000)
        image=base64.b64decode(qr.get_attribute('src').split(',',1)[1])
        student2.locator('input[type=file]').set_input_files({'name':'qr.png','mimeType':'image/png','buffer':image})
        expect(student2.get_by_placeholder('Đã đọc QR động')).to_be_visible()
        student2.get_by_role('button',name='Xác nhận điểm danh',exact=True).click()
        expect(student2.get_by_text('Đã ghi nhận điểm danh.',exact=True)).to_be_visible()

        student3=login_student('sv03')
        expect(student3.get_by_role('button',name='Quét bằng camera',exact=True)).to_be_visible()
        student3.evaluate("""src => {
            navigator.mediaDevices.getUserMedia=async()=>{
                const image=new Image(); image.src=src; await image.decode();
                const canvas=document.createElement('canvas');canvas.width=512;canvas.height=512;
                const ctx=canvas.getContext('2d');
                const draw=()=>{ctx.fillStyle='#fff';ctx.fillRect(0,0,512,512);ctx.drawImage(image,16,16,480,480)};
                draw();const timer=setInterval(draw,60);const stream=canvas.captureStream(15);
                const track=stream.getVideoTracks()[0],stop=track.stop.bind(track);
                track.stop=()=>{clearInterval(timer);stop()};return stream;
            };
        }""",qr.get_attribute('src'))
        student3.get_by_role('button',name='Quét bằng camera',exact=True).click()
        expect(student3.get_by_placeholder('Đã đọc QR động')).to_be_visible(timeout=10000)
        student3.get_by_role('button',name='Xác nhận điểm danh',exact=True).click()
        expect(student3.get_by_text('Đã ghi nhận điểm danh.',exact=True)).to_be_visible()

        student4=login_student('sv04')
        expect(student4.get_by_label('Mã PIN',exact=True)).to_be_visible()
        student4.get_by_label('Mã PIN',exact=True).fill(pin)
        student4.get_by_role('button',name='Xác nhận điểm danh',exact=True).click()
        expect(student4.get_by_text('Đã ghi nhận điểm danh.',exact=True)).to_be_visible()
        assert student.request.get('http://127.0.0.1:5184/api/sessions/'+str(session_id)+'/check-in-context').json()['status']=='CO_MAT'
        stale=payload.split('token=')[0]+'token='+'A'*43
        fifth=login_student('sv05',stale)
        expect(fifth.get_by_role('button',name='Xác nhận điểm danh',exact=True)).to_be_visible()
        expect(fifth.get_by_label('Mã PIN',exact=True)).to_be_visible()
        fifth.get_by_label('Mã PIN',exact=True).fill(pin)
        fifth.get_by_role('button',name='Xác nhận điểm danh',exact=True).click()
        expect(fifth.get_by_text('Đã có kết quả · Có mặt')).to_be_visible()
        assert not errors,errors
        print('PASS password required, anonymous API denied, QR survives 30-second login, fresh reauthentication on reload; QR actual image decoding -> web URL -> fresh phone login -> correct session -> attendance persisted. Image upload, camera stream, PIN and duplicate-link reload passed.')
db_name = 'solop_verify_qr'
create_verify_db(db_name)
try:
    with tempfile.TemporaryDirectory() as temp:
        tmp = Path(temp)
        cfg = tmp/'vite.mjs'
        cfg.write_text("export default {define:{__SOLOP_PUBLIC_ORIGIN__:JSON.stringify('http://127.0.0.1:5184')},esbuild:{jsx:'automatic'},server:{host:'0.0.0.0',port:5184,strictPort:true,proxy:{'/api':'http://127.0.0.1:8107'}}}")
        with (tmp/'backend.log').open('w+') as backend_log, (tmp/'frontend.log').open('w+') as frontend_log:
            backend = subprocess.Popen([java,'-jar',str(root/'backend/target/so-lop.jar'),'--server.port=8107']+mysql_app_args(db_name),
                                       cwd=root/'backend',stdout=backend_log,stderr=subprocess.STDOUT)
            frontend = subprocess.Popen(['node','node_modules/vite/bin/vite.js','--config',str(cfg)],
                                        cwd=root/'frontend',stdout=frontend_log,stderr=subprocess.STDOUT)
            try:
                wait(url+'/api/csrf')
                run()
            except Exception:
                for log in (frontend_log,backend_log):
                    log.seek(0)
                    print(log.read()[-2400:])
                raise
            finally:
                frontend.terminate()
                frontend.wait(timeout=10)
                backend.terminate()
                backend.wait(timeout=10)
finally:
    drop_verify_db(db_name)
