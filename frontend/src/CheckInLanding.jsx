import {useEffect,useState} from 'react'
import {BookOpenCheck, CheckCircle2, ScanLine} from 'lucide-react'
import {api} from './api'
import {useToast} from './Feedback'

export default function CheckInLanding({link,ticket,account,onBack,onLogout}) {
  const [context,setContext]=useState(null)
  const [error,setError]=useState('')
  const [pin,setPin]=useState('')
  const [usePin,setUsePin]=useState(!ticket)
  const [busy,setBusy]=useState(false)
  const notify=useToast()
  useEffect(()=>{
    const controller=new AbortController()
    setContext(null);setError('');setUsePin(!ticket);setPin('')
    if(account.role==='SINH_VIEN'){
      api('/sessions/'+link.session+'/check-in-context',{signal:controller.signal})
        .then(setContext).catch(err=>{if(!controller.signal.aborted)setError(err.message)})
    }
    return ()=>controller.abort()
  },[link.session,link.token,ticket,account.role])

  async function confirm(event) {
    event.preventDefault()
    if(busy)return
    setBusy(true);setError('')
    try{
      await api('/sessions/'+link.session+'/check-in',{method:'POST',body:usePin?{pin}:{ticket}})
      setContext(previous=>({...previous,status:'CO_MAT'}))
      notify('Đã ghi nhận điểm danh')
      try{setContext(await api('/sessions/'+link.session+'/check-in-context'))}
      catch{notify('Điểm danh đã lưu. Chưa tải lại được thông tin buổi học.','error')}
    }catch(err){
      setError(err.status===409 && !usePin
        ? err.message+' QR có thể đã đổi khi bạn đăng nhập. Hãy quét lại hoặc nhập PIN đang hiện trên màn hình giảng viên.'
        : err.message)
    }
    finally{setBusy(false)}
  }

  return <main className="checkin-landing">
    <header><BookOpenCheck aria-hidden="true" /><b>Sổ Lớp</b></header>
    <section className="report checkin-card">
      <ScanLine aria-hidden="true" />
      <h1>Xác nhận điểm danh</h1>
      <p className="page-description">Đã đăng nhập · {account.name} · MSSV {account.code}</p>
      {account.role!=='SINH_VIEN'?<>
        <p>Liên kết này dành cho sinh viên. Hãy đăng nhập tài khoản sinh viên để điểm danh.</p>
        <button onClick={onLogout}>Đăng xuất và đổi tài khoản</button>
      </>:<>
        {!context && !error && <div className="skeleton" role="status" aria-label="Đang kiểm tra buổi học">
          <div className="skeleton-line" />
          <div className="skeleton-line" style={{width:'54%'}} />
        </div>}
        {context && <div className="checkin-context"><h2>{context.subject}</h2>
          <p>{context.code} · {context.date}</p><p>{context.start.slice(0,5)}–{context.end.slice(0,5)} · {context.room}</p></div>}
        {context?.status?<p className="confirmed"><CheckCircle2 aria-hidden="true" />Đã có kết quả · {{CO_MAT:'Có mặt',DI_MUON:'Đi muộn',VANG:'Vắng',CO_PHEP:'Có phép'}[context.status]}</p>:
          context && (context.open?<form onSubmit={confirm}>
            {usePin?<label>Mã PIN<input value={pin} onChange={event=>setPin(event.target.value.replace(/[^0-9]/g,'').slice(0,8))}
              inputMode="numeric" pattern="[0-9]{8}" required placeholder="8 chữ số trên màn hình giảng viên" /></label>:
              <p>Đã kiểm tra QR. Bấm xác nhận để điểm danh cho đúng tài khoản đang hiển thị phía trên.</p>}
            <button className="primary" disabled={busy}>{busy?'Đang ghi nhận…':'Xác nhận điểm danh'}</button>
            {!usePin && <button type="button" disabled={busy} onClick={()=>{setUsePin(true);setError('')}}>Nhập PIN thay cho QR</button>}
          </form>:<p>Buổi học chưa mở hoặc đã đóng điểm danh. Hãy liên hệ giảng viên.</p>)}
        {error && <p className="error" role="alert">{error}</p>}
      </>}
      <button onClick={onBack}>Về sổ lớp</button>
    </section>
  </main>
}