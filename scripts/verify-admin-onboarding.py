import os
import shutil
import subprocess
import tempfile
import time
from pathlib import Path
from urllib.request import urlopen
from playwright.sync_api import sync_playwright, expect
from mysql_verify_db import create as create_verify_db, drop as drop_verify_db, app_args as mysql_app_args

root = Path(__file__).resolve().parents[1]
url = 'http://127.0.0.1:5185'
page_errors = []
java = str(Path(os.environ['JAVA_HOME'])/'bin/java.exe') if os.environ.get('JAVA_HOME') else shutil.which('java')


def wait(address):
    for _ in range(60):
        try:
            with urlopen(address, timeout=2) as response:
                assert response.status == 200
            return
        except OSError:
            time.sleep(.5)
    raise RuntimeError('service did not start')


def mutate(page, path, method='POST', data=None):
    csrf = page.request.get(url + '/api/csrf').json()
    return page.request.fetch(url + '/api' + path, method=method,
                              headers={csrf['header']: csrf['token']}, data=data)


def log_in(browser, code, width=1366, password='DemoSoLop2026'):
    page = browser.new_page(viewport={'width': width, 'height': 900})
    page.on('pageerror', lambda error: page_errors.append(str(error)))
    page.goto(url)
    page.get_by_label('Mã tài khoản', exact=True).fill(code)
    page.get_by_label('Mật khẩu', exact=True).fill(password)
    page.get_by_role('button', name='Đăng nhập', exact=True).click()
    expect(page.get_by_role('button', name='Đăng xuất')).to_be_visible()
    return page


def run():
    with sync_playwright() as pw:
        browser = pw.chromium.launch(channel='chrome')
        admin = log_in(browser, 'admin')

        # Seed the institution catalog the student form depends on.
        def create(entity, body):
            response = mutate(admin, '/admin/institution/' + entity, 'POST', body)
            assert response.status == 201, (entity, response.status, response.text())
            return response.json()['id']

        create('departments', {'code': 'K1', 'name': 'Khoa 1'})
        create('majors', {'code': 'N1', 'name': 'Nganh 1', 'departmentId': 1})
        create('cohorts', {'code': 'KH1', 'name': 'Khoa 1', 'startYear': 2024, 'endYear': 2028})
        create('groups', {'code': 'LHC1', 'name': 'Lop 1', 'majorId': 1, 'cohortId': 1})
        create('programs', {'code': 'CT1', 'name': 'Chuong trinh 1', 'majorId': 1, 'credits': 120})

        # The catalog is fetched once when the panel mounts, so reload to pick up the new entries.
        admin.reload()
        expect(admin.get_by_role('button', name='Đăng xuất')).to_be_visible()

        # Create a student with a full profile through the dialog.
        admin.get_by_label('Danh mục quản lý').get_by_role('button', name='Sinh viên', exact=True).click()
        admin.get_by_role('button', name='Tạo mới', exact=True).click()
        form = admin.locator('.management-form')
        form.get_by_label('Mã sinh viên', exact=True).fill('sv50')
        form.get_by_label('Họ tên', exact=True).fill('Nguyen Van Onboard')
        form.get_by_label('Email', exact=True).fill('sv50@example.com')
        form.get_by_label('Lớp hành chính', exact=True).select_option(label='LHC1 · Lop 1')
        form.get_by_label('Ngày sinh', exact=True).fill('2004-05-06')
        form.get_by_label('Giới tính', exact=True).select_option(label='Nam')
        form.get_by_label('Điện thoại', exact=True).fill('0905123456')
        form.get_by_label('Chương trình tốt nghiệp', exact=True).select_option(label='CT1 · Chuong trinh 1')
        form.get_by_label('Mật khẩu', exact=True).fill('matkhau123')
        form.get_by_role('button', name='Tạo mới', exact=True).click()
        expect(admin.get_by_text('sv50', exact=True)).to_be_visible()

        catalog = admin.request.get(url + '/api/admin').json()
        created = next(row for row in catalog['users'] if row['code'] == 'sv50')
        assert created['groupId'] == 1 and created['status'] == 'DANG_HOC', created
        assert created['birthDate'] == '2004-05-06', created

        # CSV import: template columns, one valid batch then one bad batch.
        admin.get_by_role('button', name='Nhập CSV', exact=True).click()
        dialog = admin.get_by_role('dialog')
        csv = ('ma_so,ho_ten,email,mat_khau,lop_hanh_chinh,ngay_sinh,gioi_tinh,dien_thoai,trang_thai,chuong_trinh\n'
               'sv51,Nguyen Van CSV 1,sv51@example.com,matkhau123,LHC1,2004-05-06,Nam,0905123456,Dang hoc,CT1\n'
               'sv52,Nguyen Van CSV 2,sv52@example.com,matkhau123,LHC1,2004-05-06,Nu,0905123457,Dang hoc,CT1\n')
        dialog.locator('input[type=file]').set_input_files({'name': 'sv.csv', 'mimeType': 'text/csv', 'buffer': csv.encode()})
        expect(dialog.get_by_text('sv51', exact=True)).to_be_visible()
        dialog.get_by_role('button', name='Nhập sinh viên', exact=True).click()
        expect(dialog.get_by_text('Đã tạo 2 sinh viên thành công.')).to_be_visible()

        csv_bad = ('ma_so,ho_ten,email,mat_khau,lop_hanh_chinh\n'
                   'sv53,Nguyen Bad,sv53@example.com,matkhau123,LHC1\n'
                   'sv53,Nguyen Bad 2,sv54@example.com,matkhau123,LHC1\n')
        dialog.locator('input[type=file]').set_input_files({'name': 'bad.csv', 'mimeType': 'text/csv', 'buffer': csv_bad.encode()})
        dialog.get_by_role('button', name='Nhập sinh viên', exact=True).click()
        expect(dialog.get_by_text('Dòng 2', exact=False)).to_be_visible()
        catalog = admin.request.get(url + '/api/admin').json()
        assert not any(row['code'] == 'sv53' for row in catalog['users'])

        # The new student receives a notification and can log in immediately.
        student = log_in(browser, 'sv50', password='matkhau123')
        notices = student.request.get(url + '/api/notifications').json()
        assert any('Tài khoản sinh viên' in item['title'] for item in notices['items']), notices

        # Screenshots at three widths plus an overflow check on the phone viewport.
        shots = root/'verification'
        shots.mkdir(exist_ok=True)
        for width in (1440, 768, 375):
            admin.set_viewport_size({'width': width, 'height': 900})
            admin.wait_for_timeout(250)
            assert admin.evaluate('document.documentElement.scrollWidth<=window.innerWidth+1'), width
            admin.screenshot(path=str(shots/f'admin-students-{width}.png'), full_page=True)
        assert not page_errors, page_errors
        print('PASS admin onboarding: profile creation, CSV import with row errors, notification, responsive 375/768/1440')


db_name = 'solop_verify_onboarding'
create_verify_db(db_name)
try:
    with tempfile.TemporaryDirectory() as temp:
        tmp = Path(temp)
        cfg = tmp/'vite.mjs'
        cfg.write_text("export default {esbuild:{jsx:'automatic'},server:{host:'127.0.0.1',port:5185,strictPort:true,proxy:{'/api':'http://127.0.0.1:8109'}}}")
        with (tmp/'backend.log').open('w+') as backend_log, (tmp/'frontend.log').open('w+') as frontend_log:
            backend = subprocess.Popen([java, '-jar', str(root/'backend/target/so-lop.jar'), '--server.port=8109']+mysql_app_args(db_name),
                                       cwd=root/'backend', stdout=backend_log, stderr=subprocess.STDOUT)
            frontend = subprocess.Popen(['node', 'node_modules/vite/bin/vite.js', '--config', str(cfg)],
                                        cwd=root/'frontend', stdout=frontend_log, stderr=subprocess.STDOUT)
            try:
                wait(url + '/api/csrf')
                run()
            except Exception:
                for log in (frontend_log, backend_log):
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
