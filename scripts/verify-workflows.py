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
page_errors=[]
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
    page.on('pageerror',lambda error:page_errors.append(str(error)))
    page.goto(url)
    if password:
        page.get_by_label('Mã tài khoản').fill(user)
        page.get_by_label('Mật khẩu', exact=True).fill(password)
        page.get_by_role('button', name='Đăng nhập', exact=True).click()
    else:
        code={'Ban đào tạo':'admin','Giảng viên':'gv01','Sinh viên':'sv01'}[user]
        page.get_by_label('Mã tài khoản',exact=True).fill(code)
        page.get_by_label('Mật khẩu',exact=True).fill('DemoSoLop2026')
        page.get_by_role('button',name='Đăng nhập',exact=True).click()
    expect(page.get_by_role('button', name='Đăng xuất')).to_be_visible()
    return page

def set_field(page, label, value):
    page.locator('.management-form').get_by_label(label, exact=True).fill(value)


def run():
    from datetime import datetime, timedelta
    from zoneinfo import ZoneInfo
    with sync_playwright() as pw:
        browser=pw.chromium.launch(channel='chrome')
        admin=log_in(browser,'Ban đào tạo')
        teacher=log_in(browser,'Giảng viên')
        student=log_in(browser,'Sinh viên')
        today=datetime.now(ZoneInfo('Asia/Ho_Chi_Minh')).date()
        def put(page,path,body,method='PUT',status=204):
            response=mutate(page,path,method,body)
            assert response.status==status,(path,response.status,response.text())
            return response
        catalog=admin.request.get(url+'/api/admin').json()
        assert 'terms' in catalog
        put(admin,'/admin/terms/2026-1',{'opensOn':str(today-timedelta(days=1)),'closesOn':str(today+timedelta(days=30)),'threshold':80})
        put(teacher,'/classes/1/sessions',{'date':str(today+timedelta(days=1)),'start':'13:00','end':'14:00','room':'QA.9','weeks':2,'topic':'QA'},'POST',201)
        book=teacher.request.get(url+'/api/classes/1').json()
        future=next(x for x in book['sessions'] if x['room']=='QA.9')
        put(student,f"/sessions/{future['id']}/leave",{'reason':'Khám bệnh','evidence':'https://example.org/file'},'POST',201)
        roster=teacher.request.get(url+f"/api/session-management/{future['id']}/attendance").json()
        request=next(x for x in roster if x['code']=='sv01')
        put(teacher,f"/leave/{request['requestId']}/decision",{'approve':True})
        put(teacher,f"/leave/{request['requestId']}/decision",{'approve':True},status=409)
        active=next(x for x in book['sessions'] if x['date']==str(today))
        put(teacher,f"/sessions/{active['id']}/open",None,'POST',200)
        qr=teacher.request.get(url+f"/api/sessions/{active['id']}/qr").json()
        assert len(qr['token'])==43
        put(student,f"/sessions/{active['id']}/check-in",{'token':qr['token']},'POST')
        assert teacher.request.get(url+'/api/classes/1/exports/attendance').status==200
        assert student.request.get(url+'/api/classes/1/exports/grades').status==404
        assert student.request.get(url+'/api/my-study?week='+str(today)).status==200
        put(admin,'/admin/subjects',{'code':'QA','name':'QA','credits':3},'POST',201)
        catalog=admin.request.get(url+'/api/admin').json()
        sub=next(x['id'] for x in catalog['subjects'] if x['code']=='QA')
        put(admin,'/admin/classes',{'code':'QA.1','subjectId':sub,'teacherId':2,'term':'2026-1','capacity':1},'POST',201)
        catalog=admin.request.get(url+'/api/admin').json()
        cl=next(x['id'] for x in catalog['classes'] if x['code']=='QA.1')
        put(student,f'/enrollment/{cl}',None,'POST')
        grades=teacher.request.get(url+f'/api/classes/{cl}/grades').json()
        put(teacher,f"/registrations/{grades[0]['id']}/grades",{'coursework':8,'exam':9})
        sheet=teacher.request.get(url+f'/api/classes/{cl}/grades.xlsx')
        assert sheet.status==200
        csrf=teacher.request.get(url+'/api/csrf').json()
        uploaded=teacher.request.post(url+f'/api/classes/{cl}/grades-import',headers={csrf['header']:csrf['token']},
            multipart={'file':{'name':'grades.xlsx','mimeType':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','buffer':sheet.body()}})
        assert uploaded.status==204,uploaded.text()
        put(teacher,f'/classes/{cl}/finalize',None,'POST')
        put(teacher,f"/registrations/{grades[0]['id']}/grades",{'coursework':1,'exam':2},status=409)
        row=teacher.request.get(url+f'/api/classes/{cl}/grades').json()[0]
        assert row['letter']=='A' and row['total']==8.8,row
        user=next(x for x in catalog['users'] if x['code']=='sv02')
        put(admin,f"/admin/users/{user['id']}",{**user,'locked':True},status=200)
        other=browser.new_page()
        other.goto(url)
        for i in range(9):
            csrf=other.request.get(url+'/api/csrf').json()
            response=other.request.post(url+'/api/login',headers={csrf['header']:csrf['token']},form={'username':'sv02','password':'wrongpass'})
        assert response.status==429,response.status
        sheet=teacher.request.get(url+f'/api/classes/{cl}/grades.xlsx')
        assert sheet.status==200
        assert sheet.body()[:2]==b'PK'
        csrf=teacher.request.get(url+'/api/csrf').json()
        uploaded=teacher.request.post(url+f'/api/classes/{cl}/grades-import',headers={csrf['header']:csrf['token']},
            multipart={'file':{'name':'grades.xlsx','mimeType':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','buffer':sheet.body()}})
        assert uploaded.status==409,uploaded.text()
        assert admin.request.get(url+'/api/admin/attendance?term=2026-1').status==200
        # Reopen through the real admin modal and re-finalize through the lecturer UI.
        admin.reload()
        admin.locator('.sidebar nav button').filter(has_text='QA.1').click()
        admin.get_by_role('button',name='Bảng điểm',exact=True).click()
        admin.get_by_role('button',name='Mở lại bảng điểm',exact=True).click()
        dialog=admin.get_by_role('dialog')
        dialog.get_by_label('Lý do mở lại',exact=True).fill('Đối soát bảng điểm')
        dialog.get_by_role('button',name='Lưu thay đổi',exact=True).click()
        expect(dialog).not_to_be_visible()
        assert teacher.request.get(url+f'/api/classes/{cl}/grades').json()[0]['total'] is None
        admin.get_by_text('Lịch sử chốt và mở lại bảng điểm (2)').click()
        expect(admin.get_by_text('Đối soát bảng điểm',exact=True)).to_be_visible()
        admin.screenshot(path=str(Path(tempfile.gettempdir())/'solop-final-grades.png'),full_page=True)
        teacher.reload()
        teacher.locator('.sidebar nav button').filter(has_text='QA.1').click()
        teacher.get_by_role('button',name='Bảng điểm',exact=True).click()
        teacher.get_by_role('button',name='Chốt bảng điểm',exact=True).click()
        teacher.get_by_role('dialog').get_by_role('button',name='Chốt điểm',exact=True).click()
        expect(teacher.get_by_role('dialog')).not_to_be_visible()
        expect(teacher.get_by_text('Đã chốt',exact=True)).to_be_visible()
        assert teacher.request.get(url+f'/api/classes/{cl}/grades').json()[0]['total']==8.8
        for kind in ('attendance','roster','grades'):
            export=admin.request.get(url+f'/api/classes/{cl}/exports/{kind}?format=xlsx')
            assert export.status==200 and export.body()[:2]==b'PK'
        # Submit a protected evidence file from the student's calendar.
        book=teacher.request.get(url+'/api/classes/1').json()
        future2=max((x for x in book['sessions'] if x['room']=='QA.9'),key=lambda x:x['date'])
        student.get_by_role('button',name='Lịch học và kết quả',exact=True).click()
        student.get_by_label('Tuần bắt đầu',exact=True).fill(future2['date'])
        student.locator('.calendar-session').filter(has_text='QA.9').get_by_role('button',name='Xin phép nghỉ',exact=True).click()
        dialog=student.get_by_role('dialog')
        dialog.get_by_label('Lý do',exact=True).fill('Khám bệnh có minh chứng')
        dialog.locator('input[type=file]').set_input_files({'name':'giay-hen.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.7 QA evidence'})
        dialog.get_by_role('button',name='Gửi đơn',exact=True).click()
        expect(dialog).not_to_be_visible()
        rows=teacher.request.get(url+f"/api/session-management/{future2['id']}/attendance").json()
        evidence=next(x['evidence'] for x in rows if x['code']=='sv01')
        assert evidence.startswith('/api/leave-evidence/')
        assert student.request.get(url+evidence).status==200
        assert admin.request.get(url+evidence).status==200
        outsider=log_in(browser,'sv03','DemoSoLop2026')
        assert outsider.request.get(url+evidence).status==404
        # Password change requires the old password and invalidates a second session.
        observer=log_in(browser,'Sinh viên')
        student.get_by_role('button',name='Đổi mật khẩu',exact=True).click()
        dialog=student.get_by_role('dialog')
        dialog.get_by_label('Mật khẩu hiện tại',exact=True).fill('wrong-password')
        dialog.get_by_label('Mật khẩu mới',exact=True).fill('NewPassword2026')
        dialog.get_by_label('Nhập lại mật khẩu mới',exact=True).fill('NewPassword2026')
        dialog.get_by_role('button',name='Đổi mật khẩu',exact=True).click()
        expect(dialog.get_by_role('alert')).to_have_text('Mật khẩu hiện tại chưa đúng.')
        dialog.get_by_label('Mật khẩu hiện tại',exact=True).fill('DemoSoLop2026')
        dialog.get_by_role('button',name='Đổi mật khẩu',exact=True).click()
        expect(student.get_by_role('heading',name='Đăng nhập',exact=True)).to_be_visible()
        assert observer.request.get(url+'/api/me').status==401
        student.get_by_label('Mã tài khoản',exact=True).fill('sv01')
        student.get_by_label('Mật khẩu',exact=True).fill('NewPassword2026')
        student.get_by_role('button',name='Đăng nhập',exact=True).click()
        expect(student.get_by_role('button',name='Đăng xuất')).to_be_visible()
        errors=[]
        student.on('pageerror',lambda error:errors.append(str(error)))
        student.set_viewport_size({'width':390,'height':844})
        student.get_by_role('button',name='Mở điều hướng',exact=True).click()
        expect(student.get_by_role('button',name='Đổi mật khẩu',exact=True)).to_be_visible()
        student.get_by_role('button',name='Đổi mật khẩu',exact=True).click()
        expect(student.get_by_role('dialog')).to_be_visible()
        student.wait_for_timeout(400)
        student.screenshot(path=str(Path(tempfile.gettempdir())/'solop-final-mobile.png'),full_page=True)
        student.get_by_role('button',name='Đóng hộp thoại',exact=True).click()
        expect(student.get_by_role('dialog')).not_to_be_visible()
        assert student.evaluate('document.documentElement.scrollWidth<=window.innerWidth')
        assert student.evaluate('document.body.style.overflow!==\"hidden\"')
        assert not errors,errors
        assert not page_errors,page_errors
        print('PASS new workflows: admin reopen modal, audit history, re-finalize, Excel reports, evidence upload/access, password change/session revocation, mobile modal')
        print('PASS upgrade: recurring sessions, leave approval, rotating QR, CSV, enrollment capacity, finalization, lock and login throttle')
db_name = 'solop_verify_workflows'
create_verify_db(db_name)
try:
    with tempfile.TemporaryDirectory() as temp:
        tmp = Path(temp)
        cfg = tmp/'vite.mjs'
        cfg.write_text("export default {esbuild:{jsx:'automatic'},server:{host:'127.0.0.1',port:5184,strictPort:true,proxy:{'/api':'http://127.0.0.1:8107'}}}")
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
