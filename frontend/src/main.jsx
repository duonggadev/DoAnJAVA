import {lazy, Suspense, useCallback, useEffect, useLayoutEffect, useRef, useState} from 'react'
import {createRoot} from 'react-dom/client'
import {BookOpenCheck, CalendarDays, Check, ChevronRight, ClipboardList, ImagePlus, LogOut, Plus, ScanLine, X} from 'lucide-react'
import {api} from './api'
import CheckInLanding from './CheckInLanding'
import {makeCheckInLink,parseCheckInLink} from './checkInLink'
import {Study, ExportLinks} from './Study'
import {PageMotion} from './Motion'
import {Search, Menu, LayoutDashboard, GraduationCap, BookOpen, ArrowRight, Eye, EyeOff, KeyRound, Building2, FileClock, Bell, Users} from 'lucide-react'
import {Confirm, DialogDismiss, Modal, ToastProvider, useToast} from './Feedback'
import {AdminPanel, AttendancePanel, EnrollmentPanel, GradesPanel} from './Management'
import {Institution, MyProgram, Notifications, Operations} from './Institution'
import './style.css'

const QrCamera = lazy(() => import('./QrCamera'))

const publicOrigin = typeof __SOLOP_PUBLIC_ORIGIN__ === 'string' ? __SOLOP_PUBLIC_ORIGIN__ : ''
const timezone = 'Asia/Ho_Chi_Minh'
const today = () => new Intl.DateTimeFormat('sv-SE', {timeZone: timezone}).format(new Date())
const dateLabel = value => new Intl.DateTimeFormat('vi-VN', {dateStyle: 'full', timeZone: timezone})
  .format(new Date(value + 'T00:00:00+07:00'))
const timeLabel = value => value.slice(0, 5)
const roles = {ADMIN: 'Ban đào tạo', GIANG_VIEN: 'Giảng viên', SINH_VIEN: 'Sinh viên'}
const attendance = {CO_MAT: 'Có mặt', DI_MUON: 'Đi muộn', VANG: 'Vắng', CO_PHEP: 'Có phép'}

function Wordmark() {
  return <div className="wordmark"><span className="mark"><BookOpenCheck aria-hidden="true" /></span><span>Sổ Lớp<small>Quản lý đào tạo</small></span></div>
}

function Login({onLogin, initialError, pendingCheckIn, preparing=false, scanNotice='', initialUsername=''}) {
  const [busy, setBusy] = useState(false)
  const [showPassword,setShowPassword] = useState(false)
  const [error, setError] = useState(initialError)
  async function signIn(credentials) {
    if(busy || preparing)return
    setBusy(true)
    setError('')
    try {
      await api('/login', {method: 'POST', body: credentials})
      onLogin(await api('/me'))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }
  return <PageMotion identity="login"><div className="login-layout">
    <section className="login-cover">
      <Wordmark />
      <div className="cover-content">
        <p className="cover-title">Một cuốn sổ<br />cho toàn bộ học vụ.</p>
        <p className="cover-description">Điểm danh QR, chuyên cần theo buổi và kết quả học tập của từng sinh viên, cập nhật ngay khi lớp kết thúc.</p>
        <figure className="cover-register">
          <figcaption><span>IT204.01</span><span>Buổi 2 · Phòng B.203</span></figcaption>
          <p>Cơ sở dữ liệu</p>
          <div className="cover-table">
            <div className="sample-labels"><span>Sinh viên</span><span>Điểm danh</span></div>
            <div><span>Lê Gia Hân</span><span className="sample-present"><Check aria-hidden="true" />Có mặt</span></div>
            <div><span>Nguyễn Hoàng Nam</span><span className="sample-present"><Check aria-hidden="true" />Có mặt</span></div>
            <div><span>Bùi Nhật Minh</span><span className="sample-absent">Vắng</span></div>
          </div>
        </figure>
      </div>
    </section>
    <main className="login-main">
      <h1>Đăng nhập</h1>
      <p className="login-help">{pendingCheckIn?'Bạn đã mở mã điểm danh. Đăng nhập tài khoản sinh viên để tiếp tục.':'Đăng nhập bằng tài khoản do Ban đào tạo cấp.'}</p>
      <form onSubmit={event => {
        event.preventDefault()
        signIn(new URLSearchParams(new FormData(event.currentTarget)))
      }}>
        <label>Mã tài khoản<input name="username" autoComplete="username" defaultValue={initialUsername} required /></label>
        <label>Mật khẩu<span className="password-control"><input name="password" type={showPassword?'text':'password'} autoComplete="current-password" required />
          <button type="button" aria-label={showPassword?'Ẩn mật khẩu':'Hiện mật khẩu'} onClick={()=>setShowPassword(!showPassword)}>
            {showPassword?<EyeOff aria-hidden="true" />:<Eye aria-hidden="true" />}</button></span></label>
        {error && <p className="error" role="alert">{error}</p>}
        {scanNotice && <p className="detail-note" role="status">{scanNotice}</p>}
        <button className="primary" disabled={busy || preparing}>{preparing?'Đang kiểm tra QR…':busy ? 'Đang đăng nhập…' : 'Đăng nhập'}</button>
      </form>
      <p className="login-account-note">Tài khoản cá nhân do Ban đào tạo cấp. Họ tên và mã sinh viên của bạn được ghi vào kết quả điểm danh.</p>
    </main>
  </div></PageMotion>
}

function App() {
  const [account, setAccount] = useState(undefined)
  const [error, setError] = useState('')
  const [checkIn,setCheckIn]=useState(()=>parseCheckInLink(window.location.href))
  const scanKey=checkIn ? checkIn.session+'/'+(checkIn.token ?? '') : null
  const [authenticatedScanKey,setAuthenticatedScanKey]=useState(null)
  const [scanTicket,setScanTicket]=useState(null)
  const [scanPreparing,setScanPreparing]=useState(Boolean(checkIn?.token))
  const [scanNotice,setScanNotice]=useState('')
  useEffect(()=>{
    const controller=new AbortController()
    setAuthenticatedScanKey(null);setScanTicket(null);setScanNotice('')
    if(!checkIn?.token){setScanPreparing(false);return}
    setScanPreparing(true)
    api('/qr-tickets',{method:'POST',body:{session:checkIn.session,token:checkIn.token},signal:controller.signal})
      .then(result=>{if(!controller.signal.aborted)setScanTicket(result.ticket)})
      .catch(err=>{if(!controller.signal.aborted)setScanNotice(err.message+' Bạn vẫn có thể đăng nhập và nhập PIN.')})
      .finally(()=>{if(!controller.signal.aborted)setScanPreparing(false)})
    return ()=>controller.abort()
  },[checkIn?.session,checkIn?.token])
  useEffect(()=>{
    const changed=()=>setCheckIn(parseCheckInLink(window.location.href))
    window.addEventListener('hashchange',changed)
    return ()=>window.removeEventListener('hashchange',changed)
  },[])
  useEffect(() => {
    const expireSession = () => {
      setError('Phiên đăng nhập đã hết hiệu lực. Hãy đăng nhập lại.')
      setAccount(null)
    }
    window.addEventListener('solop:session-expired', expireSession)
    api('/me').then(setAccount).catch(err => {
      if (err.status !== 401) setError('Không thể kết nối với máy chủ. Kiểm tra Spring Boot đã chạy.')
      setAccount(null)
    })
    return () => window.removeEventListener('solop:session-expired', expireSession)
  }, [])
  if (account === undefined) return <main className="standalone" role="status">Đang tải sổ lớp…</main>
  if (!account || (checkIn && authenticatedScanKey!==scanKey)) return <Login
    onLogin={user=>{setAccount(user);setAuthenticatedScanKey(scanKey)}} initialError={error}
    pendingCheckIn={checkIn} preparing={scanPreparing} scanNotice={scanNotice} initialUsername={account?.code ?? ''} />
  if(checkIn)return <CheckInLanding key={checkIn.session+'/'+checkIn.token} link={checkIn} ticket={scanTicket} account={account}
    onBack={()=>{history.replaceState(null,'',window.location.pathname+window.location.search);setCheckIn(null)}}
    onLogout={async()=>{try{await api('/logout',{method:'POST'});setAccount(null)}catch(err){setError(err.message)}}} />
  return <Workspace account={account} onLogout={() => {setError(''); setAccount(null)}} />
}

function PasswordDialog({onClose,onChanged}) {
  const [busy,setBusy]=useState(false)
  const [error,setError]=useState('')
  const notify=useToast()
  return <Modal title="Đổi mật khẩu" busy={busy} onClose={onClose}>
    <form className="dialog-body management-form" onSubmit={async event=>{
      event.preventDefault()
      if(busy)return
      const fields=Object.fromEntries(new FormData(event.currentTarget))
      if(fields.newPassword!==fields.confirm){setError('Hai lần nhập mật khẩu mới chưa khớp.');return}
      setBusy(true);setError('')
      try{
        await api('/account/password',{method:'PUT',body:{currentPassword:fields.currentPassword,newPassword:fields.newPassword}})
        notify('Đã đổi mật khẩu. Đăng nhập lại bằng mật khẩu mới.')
        onChanged()
      }catch(err){setError(err.message)}
      finally{setBusy(false)}
    }}>
      <p className="detail-note">Sau khi đổi, các phiên đăng nhập cũ sẽ hết hiệu lực.</p>
      <label>Mật khẩu hiện tại<input name="currentPassword" type="password" autoComplete="current-password" required /></label>
      <label>Mật khẩu mới<input name="newPassword" type="password" minLength={8} autoComplete="new-password" required /></label>
      <label>Nhập lại mật khẩu mới<input name="confirm" type="password" minLength={8} autoComplete="new-password" required /></label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="management-actions"><DialogDismiss disabled={busy} /><button className="primary" disabled={busy}>{busy?'Đang lưu…':'Đổi mật khẩu'}</button></div>
    </form>
  </Modal>
}

function Workspace({account, onLogout}) {
  const [classes, setClasses] = useState(null)
  const [selected, setSelected] = useState(null)
  const [error, setError] = useState('')
  const [view, setView] = useState('schedule')
  const [screen, setScreen] = useState(account.role === 'ADMIN' ? 'admin' : 'course')
  async function refreshClasses() {
    const list = await api('/classes')
    setClasses(list)
    setSelected(previous => list.some(item => item.id === previous) ? previous : list[0]?.id ?? null)
  }
  useEffect(() => {
    refreshClasses().catch(err => setError(err.message))
  }, [])
  const [passwordOpen,setPasswordOpen]=useState(false)
  const [menuOpen,setMenuOpen]=useState(false)
  const [menuPresent,setMenuPresent]=useState(false)
  const menuTrigger=useRef(null)
  useEffect(()=>{
    if(menuOpen){setMenuPresent(true);return}
    const delay=window.matchMedia('(prefers-reduced-motion: reduce)').matches?0:200
    const timer=setTimeout(()=>setMenuPresent(false),delay)
    return ()=>clearTimeout(timer)
  },[menuOpen])
  const [searchOpen,setSearchOpen]=useState(false)
  const [searchQuery,setSearchQuery]=useState('')
  useEffect(()=>{
    const shortcut=event=>{
      if((event.ctrlKey || event.metaKey) && event.key.toLowerCase()==='k'){
        event.preventDefault();setMenuOpen(false);setSearchOpen(previous=>!previous)
      }
    }
    window.addEventListener('keydown',shortcut)
    return ()=>window.removeEventListener('keydown',shortcut)
  },[])
  const course = classes?.find(item => item.id === selected)
  const matchedClasses=(classes??[]).filter(item=>(item.subject+' '+item.code).toLocaleLowerCase('vi').includes(searchQuery.toLocaleLowerCase('vi')))
  useLayoutEffect(()=>{
    if(!menuPresent)return
    const sidebar=document.querySelector('.sidebar')
    const previousOverflow=document.body.style.overflow
    document.body.style.overflow='hidden'
    const controls=Array.from(sidebar.querySelectorAll('button:not([disabled])'))
    const focusFrame=requestAnimationFrame(()=>controls[0]?.focus())
    const keyboard=event=>{
      if(event.key==='Escape'){event.preventDefault();setMenuOpen(false)}
      if(event.key==='Tab'){
        const first=controls[0],last=controls.at(-1)
        if(event.shiftKey && document.activeElement===first){event.preventDefault();last?.focus()}
        if(!event.shiftKey && document.activeElement===last){event.preventDefault();first?.focus()}
      }
    }
    const resize=()=>{if(window.innerWidth>900)setMenuOpen(false)}
    document.addEventListener('keydown',keyboard)
    window.addEventListener('resize',resize)
    return ()=>{
      document.body.style.overflow=previousOverflow
      document.removeEventListener('keydown',keyboard)
      window.removeEventListener('resize',resize)
      cancelAnimationFrame(focusFrame)
      requestAnimationFrame(()=>{
        if(document.querySelector('.workspace')?.classList.contains('menu-open'))return
        menuTrigger.current?.focus()
      })
    }
  },[menuPresent])
  function openCourse(id){
    setSelected(id);setView('schedule');setScreen('course');setSearchOpen(false);setMenuOpen(false)
  }
  return <div className={'workspace'+(menuOpen?' menu-open':'')+(menuPresent?' menu-present':'')}>
    <a className="skip-link" href="#main-content">Bỏ qua điều hướng</a>
    <button className="nav-scrim" aria-label="Đóng điều hướng" onClick={()=>setMenuOpen(false)} />
    {passwordOpen && !menuPresent && <PasswordDialog onClose={()=>setPasswordOpen(false)} onChanged={onLogout} />}
    <aside className="sidebar" id="sidebar-nav" aria-label="Sổ lớp">
      <div className="sidebar-head">
        <Wordmark />
        <button className="sidebar-close" type="button" aria-label="Đóng điều hướng" onClick={()=>setMenuOpen(false)}><X aria-hidden="true" /></button>
      </div>
      <button className="sidebar-search" onClick={()=>{setMenuOpen(false);setSearchOpen(true)}}><Search aria-hidden="true" />Tìm lớp học phần<kbd>Ctrl K</kbd></button>
      <nav aria-label="Điều hướng sổ lớp">
        <button aria-current={screen==='notifications'?'page':undefined} onClick={()=>{setScreen('notifications');setMenuOpen(false)}}><Bell aria-hidden="true" />Thông báo</button>
        {account.role === 'SINH_VIEN' && <button aria-current={screen==='study'?'page':undefined} onClick={()=>{setScreen('study');setMenuOpen(false)}}><CalendarDays aria-hidden="true" />Lịch học và kết quả</button>}
        {account.role === 'SINH_VIEN' && <button aria-current={screen==='program'?'page':undefined} onClick={()=>{setScreen('program');setMenuOpen(false)}}><GraduationCap aria-hidden="true" />Chương trình học</button>}
        {account.role !== 'GIANG_VIEN' && <button className="workspace-entry" aria-current={screen === 'admin' || screen === 'enrollment' ? 'page' : undefined}
          onClick={() => {setScreen(account.role === 'ADMIN' ? 'admin' : 'enrollment');setMenuOpen(false)}}>
          <LayoutDashboard aria-hidden="true" /><span>{account.role === 'ADMIN' ? 'Quản lý đào tạo' : 'Đăng ký lớp'}</span><ChevronRight aria-hidden="true" />
        </button>}
        {account.role === 'ADMIN' && <>
          <button aria-current={screen==='institution'?'page':undefined} onClick={()=>{setScreen('institution');setMenuOpen(false)}}><Building2 aria-hidden="true" />Cơ cấu đào tạo</button>
          <button aria-current={screen==='operations'?'page':undefined} onClick={()=>{setScreen('operations');setMenuOpen(false)}}><FileClock aria-hidden="true" />Nhật ký hệ thống</button>
        </>}
        <h2>Lớp học phần</h2>
        {classes?.map(item => <button key={item.id} aria-current={screen === 'course' && selected === item.id ? 'page' : undefined}
          onClick={() => openCourse(item.id)}><BookOpen aria-hidden="true" /><span className="nav-course"><b>{item.subject}</b><small>{item.code}</small></span><ChevronRight aria-hidden="true" /></button>)}
      </nav>
      <button className="account-settings" onClick={()=>{setMenuOpen(false);setPasswordOpen(true)}}><KeyRound aria-hidden="true" />Đổi mật khẩu</button>
      <div className="account">
        <span className="avatar" aria-hidden="true">{account.name.split(' ').slice(-2).map(part => part[0]).join('')}</span>
        <div><b>{account.name}</b><small>{roles[account.role]}</small></div>
        <button aria-label="Đăng xuất" title="Đăng xuất" onClick={async () => {
          try {
            await api('/logout', {method: 'POST'})
            onLogout()
          } catch (err) {
            setError(err.message)
          }
        }}><LogOut aria-hidden="true" /></button>
      </div>
    </aside>
    <main className="content" id="main-content" tabIndex={-1} inert={menuPresent}>
      <div className="workspace-toolbar">
        <button ref={menuTrigger} className="mobile-menu" aria-label="Mở điều hướng" aria-expanded={menuOpen} aria-controls="sidebar-nav" onClick={()=>setMenuOpen(true)}><Menu aria-hidden="true" /></button>
        <span className="workspace-location"><GraduationCap aria-hidden="true" />Không gian học vụ</span>
        <button className="toolbar-search" onClick={()=>setSearchOpen(true)}><Search aria-hidden="true" /><span>Tìm lớp học phần</span><kbd>Ctrl K</kbd></button>
        <time className="toolbar-date" dateTime={today()}>{dateLabel(today())}</time>
        <span className="toolbar-role">{roles[account.role]}</span>
      </div>
      <PageMotion identity={screen}>
      {error && <p role="alert" className="error">{error}</p>}
      {!classes && !error && <div className="skeleton" role="status" aria-label="Đang tải lớp học phần">
        <div className="skeleton-line" />
        <div className="skeleton-line" style={{width:'54%'}} />
        <div className="skeleton-row" />
        <div className="skeleton-row" />
      </div>}
      {screen === 'study' && <Study />}
      {screen === 'program' && <MyProgram />}
      {screen === 'institution' && <Institution />}
      {screen === 'operations' && <Operations />}
      {screen === 'notifications' && <Notifications />}
      {screen === 'admin' && <AdminPanel account={account} refreshClasses={refreshClasses} />}
      {screen === 'enrollment' && <EnrollmentPanel refreshClasses={refreshClasses} />}
      {screen === 'course' && classes?.length === 0 && <p>Bạn chưa có lớp học phần.</p>}
      {screen === 'course' && course && <ClassBook key={course.id} course={course} account={account} view={view} setView={setView} />}
      </PageMotion>
      {searchOpen && !menuPresent && <Modal title="Tìm lớp học phần" onClose={()=>setSearchOpen(false)}>
        <div className="dialog-body"><label>Tên môn hoặc mã lớp<input value={searchQuery} onChange={event=>setSearchQuery(event.target.value)} placeholder="Ví dụ Cơ sở dữ liệu" /></label>
          <div className="search-results">{matchedClasses.length===0?<p className="empty">Không có lớp phù hợp</p>:matchedClasses.map(item=>
            <button key={item.id} onClick={()=>openCourse(item.id)}><BookOpen aria-hidden="true" /><span><b>{item.subject}</b><small>{item.code} · {item.term}</small></span><ArrowRight aria-hidden="true" /></button>)}</div>
        </div></Modal>}
    </main>
  </div>
}

function ClassBook({course, account, view, setView}) {
  const [book, setBook] = useState(null)
  const [sessionId, setSessionId] = useState(null)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState(null)
  const [attendanceSession, setAttendanceSession] = useState(null)
  const [error, setError] = useState('')
  const [now, setNow] = useState(Date.now())
  const url = '/classes/' + course.id
  const teacher = account.role === 'GIANG_VIEN'
  useEffect(() => {
    let current = true
    api(url).then(data => {
      if (current) setBook(data)
    }).catch(err => {
      if (current) setError(err.message)
    })
    const interval = setInterval(() => setNow(Date.now()), 1000)
    const poll = account.role === 'SINH_VIEN' ? setInterval(() => {
      api(url).then(data => {if (current) setBook(data)}).catch(() => {})
    }, 5000) : null
    return () => {current = false; clearInterval(interval); clearInterval(poll)}
  }, [url, account.role])

  async function refresh() {
    setBook(await api(url))
    setNow(Date.now())
  }

  const sessions = [...(book?.sessions ?? [])].sort((a, b) => (a.date + ' ' + a.start).localeCompare(b.date + ' ' + b.start))
  const active = sessions.find(item => item.id === sessionId)
    ?? sessions.find(item => item.date === today())
    ?? sessions.at(-1)
  return <>
    <header className="course-header">
      <div><p className="course-code">Học kỳ {course.term}</p><h1>{course.subject}</h1>
        <p className="metadata"><span>{course.teacher}</span><span>{course.credits} tín chỉ</span><span>{course.enrollment} sinh viên</span></p></div>
      {teacher && !book?.finalized && <button className="primary" aria-expanded={creating} onClick={() => {
        setView('schedule')
        setEditing(null)
        setCreating(!creating)
      }}>{creating ? <><X aria-hidden="true" />Đóng biểu mẫu</> : <><Plus aria-hidden="true" />Tạo buổi học</>}</button>}
    </header>
    {account.role !== 'SINH_VIEN' && <ExportLinks classId={course.id} />}
    <div className="tabs" aria-label="Nội dung lớp học">
      <button aria-pressed={view === 'schedule'} onClick={() => setView('schedule')}><CalendarDays aria-hidden="true" />Buổi học</button>
      <button aria-pressed={view === 'report'} onClick={() => {setView('report'); refresh().catch(err => setError(err.message))}}><ClipboardList aria-hidden="true" />Chuyên cần</button>
      <button aria-pressed={view === 'grades'} onClick={() => setView('grades')}><GraduationCap aria-hidden="true" />Bảng điểm</button>
    </div>
    {error && <p className="error" role="alert">{error}</p>}
    {!error && !book && view !== 'grades' && <section className="report" aria-label="Đang tải sổ điểm danh">
      <div className="skeleton skeleton-book" role="status">
        <div className="skeleton-line" style={{width:'32%'}} />
        <div className="skeleton-line" style={{width:'54%'}} />
        <div className="skeleton-row" /><div className="skeleton-row" /><div className="skeleton-row" /><div className="skeleton-row" />
      </div>
    </section>}
    {view === 'schedule' && (creating || editing) && <SessionForm key={editing?.id ?? 'new'} url={url} item={editing}
      onCancel={() => {setCreating(false); setEditing(null)}}
      onSaved={async () => {
        setCreating(false);setEditing(null)
        try{await refresh()}catch(err){setError('Buổi học đã lưu nhưng chưa tải lại được lịch. '+err.message)}
      }} />}
    {view === 'grades' && <GradesPanel classId={course.id} canEdit={teacher} canReopen={account.role==='ADMIN'} onStatusChange={refresh} />}
    {book && view === 'report' && <Report roster={book.roster} />}
    {book && view === 'schedule' && <>
      <div className="book-grid">
        <section className="schedule" aria-labelledby="schedule-title">
          <div className="section-head"><h2 id="schedule-title">Lịch buổi học</h2><span>{sessions.length} buổi</span></div>
          {sessions.length === 0 ? <p className="empty">Lớp chưa có buổi học.</p> :
            <div className="session-list">
              <div className="session-columns" aria-hidden="true"><span>Ngày</span><span>Buổi và phòng học</span><span>Trạng thái</span></div>
              {sessions.map((item, index) => {
              const live = item.expiresAt > now
              const ended = (item.expiresAt && !live) || (!item.expiresAt && new Date(item.date+'T'+item.end+'+07:00').getTime()<=now)
              return <button key={item.id} aria-pressed={active?.id === item.id} onClick={() => setSessionId(item.id)}>
                <time className="date-tile" dateTime={item.date}><b>{item.date.slice(8)}</b><small>thg {Number(item.date.slice(5, 7))}</small></time>
                <span className="session-text"><b>Buổi {index + 1}</b><small>{timeLabel(item.start)}–{timeLabel(item.end)}{item.room && ' · ' + item.room}</small></span>
                <span className={item.status ? 'state ' + (item.status === 'VANG' ? 'absent' : 'recorded') : live ? 'state live' : 'state'}>{attendance[item.status] || (live ? 'Đang mở' : ended ? 'Đã qua' : item.date === today() ? 'Hôm nay' : 'Sắp tới')}</span>
              </button>
            })}</div>}
        </section>
        {active && <SessionDetails key={active.id} item={active} now={now} role={account.role} refresh={refresh}
          finalized={Boolean(book.finalized)}
          onEdit={() => {setCreating(false); setEditing(active)}}
          showAttendance={attendanceSession === active.id}
          onAttendance={() => setAttendanceSession(attendanceSession === active.id ? null : active.id)} />}
      </div>
      {teacher && active && attendanceSession === active.id &&
        <AttendancePanel key={active.id} sessionId={active.id} onSaved={refresh} />}
    </>}
  </>
}

function SessionForm({url, item, onSaved, onCancel}) {
  const notify = useToast()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  return <Modal title={item ? 'Sửa buổi học' : 'Tạo buổi học'} busy={busy} onClose={onCancel}>
    <form className="dialog-body session-editor" onSubmit={async event => {
    event.preventDefault()
    const body = Object.fromEntries(new FormData(event.currentTarget))
    setBusy(true)
    setError('')
    try {
      await api(item ? '/session-management/' + item.id : url + '/sessions', {method: item ? 'PUT' : 'POST', body})
      await onSaved()
      notify(item ? 'Đã lưu thay đổi buổi học' : 'Đã tạo buổi học')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }}>
    <div className="form-fields">
    <label>Ngày học<input name="date" type="date" min={today()} defaultValue={item?.date ?? today()} required /></label>
    <label>Bắt đầu<input name="start" type="time" defaultValue={item && timeLabel(item.start)} required /></label>
    <label>Kết thúc<input name="end" type="time" defaultValue={item && timeLabel(item.end)} required /></label>
    <label>Nội dung<input name="topic" defaultValue={item?.topic ?? ''} maxLength={255} /></label>
    {!item && <label>Số tuần lặp<input name="weeks" type="number" min="1" max="20" defaultValue="1" /></label>}
    <label>Phòng học<input name="room" maxLength={20} defaultValue={item?.room ?? ''} required /></label>
    </div>
    {error && <p className="error" role="alert">{error}</p>}
    <div className="management-actions">
      <DialogDismiss disabled={busy} />
      <button className="primary" disabled={busy}>Lưu buổi học</button>
    </div>
    </form></Modal>
}

function SessionDetails({item, now, role, refresh, onEdit, showAttendance, onAttendance, finalized}) {
  const notify = useToast()
  const [confirmAction, setConfirmAction] = useState(null)
  const [pin, setPin] = useState('')
  const [qr, setQr] = useState('')
  const [qrToken, setQrToken] = useState(null)
  const [checkedIn,setCheckedIn]=useState(false)
  const [presenting,setPresenting]=useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const upload = useRef(null)
  const [camera, setCamera] = useState(false)
  const acceptPin = useCallback(value => {
    const link=parseCheckInLink(value)
    if(link && link.session!==item.id){
      setError('Mã QR thuộc buổi học khác. Hãy chọn đúng buổi hoặc mở liên kết QR trên điện thoại.')
      setCamera(false)
      return
    }
    const token=link?.token ?? (value.startsWith('token:') ? value.slice(6) : null)
    setQrToken(token)
    setPin(token ? '' : value)
    setCamera(false)
    setError('')
    setNotice('Đã đọc mã QR. Bấm xác nhận để điểm danh.')
  }, [item.id])
  const seconds = Math.max(0, Math.ceil((item.expiresAt - now) / 1000))
  const live = seconds > 0
  const canOpen = role === 'GIANG_VIEN' && !live && !finalized && item.date === today()

  useEffect(() => {
    let current = true
    let timer
    async function update() {
      if (!item.pin || !live || !current) return
      let delay=2000
      try {
        const data = await api('/sessions/' + item.id + '/qr')
        const refreshDeadline=Date.now()+Math.max(0,data.refreshAt-data.serverTime)
        const {default: generator} = await import('qrcode')
        const image = await generator.toDataURL(makeCheckInLink(publicOrigin || window.location.origin,item.id,data.token), {width: 420, margin: 4, errorCorrectionLevel:'M'})
        if (current) setQr(image)
        delay=Math.max(150,refreshDeadline-Date.now()+40)
      } catch (err) { if (current) setNotice(err.message) }
      finally {if(current)timer=setTimeout(update,delay)}
    }
    update()
    return () => {current = false; clearTimeout(timer)}
  }, [item.pin, item.id, live])

  async function send(action, body) {
    setBusy(true)
    setError('')
    try {
      await api('/sessions/' + item.id + '/' + action, {method: 'POST', body})
      setNotice('')
      if(action==='check-in')setCheckedIn(true)
      notify(action === 'check-in' ? 'Đã ghi nhận điểm danh.' : 'Đã mở điểm danh')
      try{await refresh()}catch(err){setNotice('Thao tác đã lưu nhưng chưa tải lại được buổi học. '+err.message)}
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function manage(action) {
    const deleting = action === 'delete'
    await api('/session-management/' + item.id + (deleting ? '' : '/close'), {method: deleting ? 'DELETE' : 'POST'})
    await refresh()
    notify(deleting ? 'Đã xóa buổi học' : 'Đã đóng điểm danh')
  }

  async function readQr(event) {
    const file = event.target.files[0]
    event.target.value = ''
    if (!file) return
    setError('')
    try {
      const {readPin} = await import('./QrCamera')
      const image = await createImageBitmap(file)
      try {
        const value = readPin(image, image.width, image.height, document.createElement('canvas'))
        if (!value) throw new Error('invalid QR')
        acceptPin(value)
      } finally {
        image.close()
      }
    } catch {
      setError('Không đọc được mã QR. Hãy chọn ảnh rõ hơn hoặc nhập PIN.')
    }
  }

  return <section className="session-detail" aria-labelledby="detail-title">
    <div className="detail-top"><h2 id="detail-title"><ScanLine aria-hidden="true" />Điểm danh</h2>{live && <span className="remaining">Còn {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}</span>}</div>
    <div className="detail-date"><p>{dateLabel(item.date)}</p><p className="detail-time">{timeLabel(item.start)}–{timeLabel(item.end)}{item.room && ' · Phòng ' + item.room}</p></div>
    {live && item.pin && <div className="code-box">
      {qr && <img src={qr} width="144" height="144" alt="Mã QR điểm danh" />}
      <div className="pin-code"><span>Mã PIN</span><b>{item.pin}</b></div>
      <p className="network-hint">Quét Camera/Zalo để mở điểm danh. Dùng cùng mạng Wi-Fi với máy tính.</p>
    </div>}
    {live && qr && <button onClick={()=>setPresenting(true)}>Trình chiếu QR</button>}
    {presenting && <Modal title="Điểm danh trực tiếp" onClose={()=>setPresenting(false)}>
      <div className="qr-presentation">{live ? <><img src={qr} alt="QR động điểm danh" />
        <p>PIN · {item.pin}</p><p>Còn {Math.floor(seconds/60)}:{String(seconds%60).padStart(2,'0')}</p>
        <small>Quét bằng Camera hoặc Zalo để mở trang điểm danh. QR đổi mỗi 20 giây.</small>
        <p className="network-hint">Điện thoại và máy tính cần cùng mạng Wi-Fi · {publicOrigin || window.location.origin}</p></> : <p>Điểm danh đã đóng</p>}</div>
    </Modal>}
    {canOpen && <><p className="detail-note">Mã PIN và QR dùng được trong 10 phút</p><button className="primary" disabled={busy} onClick={() => send('open')}>{item.expiresAt?'Mở lại điểm danh':'Mở điểm danh'}</button></>}
    {(item.status || checkedIn) && <p className={'confirmed ' + (item.status === 'VANG' ? 'absent' : '')}>{item.status !== 'VANG' && <Check aria-hidden="true" />}{attendance[item.status ?? 'CO_MAT']}</p>}
    {live && role === 'SINH_VIEN' && !item.status && !checkedIn &&
      <form onSubmit={event => {event.preventDefault(); send('check-in', qrToken ? {token:qrToken} : {pin})}}>
        <label>Mã PIN<input value={pin} onChange={event => {setQrToken(null); setPin(event.target.value.replace(/[^0-9]/g, '').slice(0, 8))}}
          inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{8}" placeholder={qrToken ? 'Đã đọc QR động' : '8 chữ số'} required={!qrToken} /></label>
        <button className="primary" disabled={busy}>Xác nhận điểm danh</button>
        <button type="button" className="upload" onClick={() => upload.current.click()}><ImagePlus aria-hidden="true" />Đọc QR từ ảnh</button>
        <input ref={upload} type="file" accept="image/*" hidden onChange={readQr} />
        {!camera && <button type="button" onClick={() => setCamera(true)}><ScanLine aria-hidden="true" />Quét bằng camera</button>}
        {camera && <Suspense fallback={<p role="status">Đang mở camera…</p>}>
          <QrCamera onRead={acceptPin} onClose={() => setCamera(false)} />
        </Suspense>}
      </form>}
    {!live && !canOpen && !item.status && <p className="detail-note">{item.expiresAt ? 'Điểm danh đã đóng' : 'Chưa mở điểm danh'}</p>}
    {role === 'GIANG_VIEN' && <div className="session-actions">
      {Boolean(item.editable) && <>
        <button disabled={busy} onClick={onEdit}>Sửa buổi</button>
        <button className="danger" disabled={busy} onClick={() => setConfirmAction('delete')}>Xóa buổi</button>
      </>}
      {live && <button disabled={busy} onClick={() => setConfirmAction('close')}>Đóng điểm danh</button>}
      <button aria-expanded={showAttendance} onClick={onAttendance}>{showAttendance ? 'Ẩn kết quả' : 'Xem kết quả'}</button>
    </div>}
    {confirmAction && <Confirm title={confirmAction === 'delete' ? 'Xóa buổi học' : 'Đóng điểm danh'}
      description={confirmAction === 'delete' ? 'Xóa buổi học ngày ' + dateLabel(item.date) + '?'
        : 'Mã QR và PIN của buổi học sẽ hết hiệu lực ngay.'}
      confirmLabel={confirmAction === 'delete' ? 'Xác nhận xóa' : 'Đóng ngay'}
      destructive={confirmAction === 'delete'} onClose={() => setConfirmAction(null)} onConfirm={() => manage(confirmAction)} />}
    {notice && <p role="status">{notice}</p>}
    {error && <p className="error" role="alert">{error}</p>}
  </section>
}

function Report({roster}) {
  return <section className="report" aria-labelledby="report-title">
    <div className="section-head"><h2 id="report-title">Bảng chuyên cần</h2><span>{roster.length} sinh viên</span></div>
    {roster.length === 0 ? <div className="empty-state">
      <Users aria-hidden="true" />
      <h3>Lớp chưa có sinh viên</h3>
      <p>Bảng chuyên cần sẽ xuất hiện khi sinh viên đăng ký lớp học phần này.</p>
    </div> :
      <div className="table-wrap" tabIndex={0} role="region" aria-label="Bảng chuyên cần của lớp"><table>
        <caption className="sr-only">Bảng chuyên cần của lớp</caption>
        <thead><tr><th scope="col">Mã SV</th><th scope="col">Họ tên</th><th scope="col" className="num">Tham dự</th><th scope="col" className="num">Vắng</th><th scope="col" className="num">Có phép</th><th scope="col" className="num">Tỷ lệ</th></tr></thead>
        <tbody>{roster.map(row => {
          const rate = row.total ? Math.round(row.attended * 100 / row.total) : null
          return <tr key={row.code}>
            <td>{row.code}</td><th scope="row">{row.name}</th><td className="num">{row.attended}/{row.total}</td><td className="num">{row.absent}</td><td className="num">{row.excused}</td>
            <td className="num"><span className="rate">{rate !== null && <span className="rate-line" aria-hidden="true"><span style={{transform:'scaleX(' + rate / 100 + ')'}} /></span>}<span>{rate === null ? '–' : rate + '%'}</span></span></td>
          </tr>
        })}</tbody>
      </table></div>}
    <p className="report-note">Tính các buổi đã đóng điểm danh hoặc kết thúc giờ học. Đi muộn tính là tham dự, vắng có phép được loại khỏi mẫu số.</p>
  </section>
}

createRoot(document.getElementById('root')).render(<ToastProvider><App /></ToastProvider>)
