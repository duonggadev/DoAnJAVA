import {useEffect,useState} from 'react'
import {CalendarDays, ChevronLeft, ChevronRight, Clock3, MapPin, Download, FileSpreadsheet} from 'lucide-react'
import {api} from './api'
import {Modal,useToast} from './Feedback'

const schoolToday=()=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date())
function shiftDate(date,days) {
  const value=new Date(date+'T12:00:00Z')
  value.setUTCDate(value.getUTCDate()+days)
  return value.toISOString().slice(0,10)
}
const startOfWeek=date=>{
  const day=new Date(date+'T12:00:00Z').getUTCDay()
  return shiftDate(date,-((day+6)%7))
}
const labels={CO_MAT:'Có mặt',DI_MUON:'Đi muộn',VANG:'Vắng',CO_PHEP:'Có phép'}

export function Study() {
  const [week,setWeek]=useState(startOfWeek(schoolToday()))
  const [data,setData]=useState(null)
  const [error,setError]=useState('')
  const [session,setSession]=useState(null)
  const [busy,setBusy]=useState(false)
  const notify=useToast()
  useEffect(()=>{
    let active=true
    setData(null)
    api('/my-study?week='+week).then(result=>{if(active){setData(result);setError('')}}).catch(err=>{if(active)setError(err.message)})
    return ()=>{active=false}
  },[week])

  return <div className="management study-page">
    <header className="course-header"><div><p className="page-eyebrow">Không gian sinh viên</p><h1>Lịch học và kết quả</h1>
      <p className="page-description">Theo dõi từng buổi học và tiến độ học tập của bạn.</p></div>
      <button onClick={()=>setWeek(startOfWeek(schoolToday()))}><CalendarDays aria-hidden="true" />Tuần này</button>
    </header>
    {error && <p role="alert" className="error">{error}</p>}
    {!data && !error && <section className="report" aria-label="Đang tải lịch học">
      <div className="skeleton skeleton-study" role="status">
        <div className="skeleton-line" style={{width:'32%'}} />
        <div className="skeleton-line" style={{width:'54%'}} />
        <div className="skeleton-row" /><div className="skeleton-row" /><div className="skeleton-row" /><div className="skeleton-row" />
      </div>
    </section>}
    {data && <>
      <section className="study-stats" aria-label="Kết quả học tập">
        {[['GPA hệ 10',data.gpa10],['GPA hệ 4',data.gpa4],['Tín chỉ đã chốt',data.credits]].map(([label,value])=>
          <div key={label}><span>{label}</span><strong className={value == null || value === '–' ? 'is-empty' : undefined}>{value == null || value === '–' ? '–' : value}</strong>
            {(value == null || value === '–') && <small>Chưa có kết quả</small>}</div>)}
      </section>
      {data.alerts.map(row=><p className="study-alert" key={row.code}>Chuyên cần {row.code} đang dưới {row.threshold}% · {row.attended}/{row.total} buổi tham dự</p>)}
      <section className="report week-calendar">
        <div className="section-head"><div><h2>Lịch trong tuần</h2><p>{week} đến {shiftDate(week,6)}</p></div>
          <div className="week-navigation">
            <button aria-label="Tuần trước" onClick={()=>setWeek(shiftDate(week,-7))}><ChevronLeft aria-hidden="true" /></button>
            <label><span className="sr-only">Tuần bắt đầu</span><input aria-label="Tuần bắt đầu" type="date" value={week} onChange={e=>{if(e.target.value)setWeek(startOfWeek(e.target.value))}} /></label>
            <button aria-label="Tuần sau" onClick={()=>setWeek(shiftDate(week,7))}><ChevronRight aria-hidden="true" /></button>
          </div>
        </div>
        {data.sessions.length===0 ? <div className="calendar-empty"><CalendarDays aria-hidden="true" /><h2>Tuần này chưa có buổi học</h2><p>Chọn tuần khác để xem lịch đã được giảng viên tạo.</p>
          {week!==startOfWeek(schoolToday())&&<button type="button" onClick={()=>setWeek(startOfWeek(schoolToday()))}>Về tuần này</button>}</div> :
          <div className="week-days">{Array.from({length:7},(_,index)=>{
            const date=shiftDate(week,index)
            const sessions=data.sessions.filter(row=>row.date===date)
            if(sessions.length===0)return null
            return <div className={'calendar-day'+(date===schoolToday()?' is-today':'')} key={date}>
              <div className="day-heading"><strong>{date.slice(8)}</strong><span>{['Thứ hai','Thứ ba','Thứ tư','Thứ năm','Thứ sáu','Thứ bảy','Chủ nhật'][index]}</span>
                {date===schoolToday() && <small>Hôm nay</small>}</div>
              <div className="day-sessions">{sessions.map(row=><article className="calendar-session" key={row.id}>
                <div><small>{row.code}</small><h3>{row.subject}</h3><p><Clock3 aria-hidden="true" />{row.start.slice(0,5)}–{row.end.slice(0,5)}<MapPin aria-hidden="true" />{row.room??'Chưa có phòng'}</p>
                  {row.topic && <p>{row.topic}</p>}</div>
                <div className="calendar-session-actions">
                  {row.status && <span className="status-badge positive">{labels[row.status]}</span>}
                  {row.requestStatus && <small>{{CHO_DUYET:'Đơn đang chờ duyệt',DA_DUYET:'Đơn đã được duyệt',TU_CHOI:'Đơn bị từ chối'}[row.requestStatus]}</small>}
                  {!row.requestStatus && new Date(row.date+'T'+row.start+'+07:00')>new Date() && <button onClick={()=>setSession(row)}>Xin phép nghỉ</button>}
                </div>
              </article>)}</div>
            </div>
          })}</div>}
      </section>
      <section className="report study-results"><div className="section-head"><h2>Kết quả đã chốt</h2><span>{data.grades.length} lớp học phần</span></div>
        {data.grades.length===0?<div className="empty-state">
          <FileSpreadsheet aria-hidden="true" />
          <h3>Chưa có kết quả đã chốt</h3>
          <p>Bảng điểm sẽ xuất hiện sau khi giảng viên chốt kết quả lớp học phần.</p>
        </div>:<div className="table-wrap" role="region" tabIndex={0} aria-label="Kết quả đã chốt"><table>
          <caption className="sr-only">Kết quả đã chốt</caption>
          <thead><tr><th scope="col">Mã lớp</th><th scope="col">Môn học</th><th scope="col">Học kỳ</th><th scope="col" className="num">Tín chỉ</th><th scope="col" className="num">Tổng kết</th><th scope="col">Điểm chữ</th></tr></thead>
          <tbody>{data.grades.map(row=><tr key={row.code}><td>{row.code}</td><td>{row.subject}</td><td>{row.term}</td><td className="num">{row.credits}</td><td className="num">{row.total}</td><td>{row.letter}{Boolean(row.barred) && ' · Cấm thi'}</td></tr>)}</tbody>
        </table></div>}
      </section>
    </>}
    {session && <Modal title="Xin phép nghỉ học" busy={busy} onClose={()=>setSession(null)}><form className="dialog-body" onSubmit={async e=>{
      e.preventDefault()
      if(busy)return
      const form=new FormData(e.currentTarget)
      const file=form.get('file')
      const withFile=file instanceof File && file.size>0
      if(withFile && file.size>2000000){notify('Tệp minh chứng cần nhỏ hơn 2 MB.','error');return}
      const body=withFile?form:{reason:form.get('reason'),evidence:form.get('evidence')}
      setBusy(true)
      try {
        await api('/sessions/'+session.id+(withFile?'/leave-file':'/leave'),{method:'POST',body})
        setSession(null);notify('Đã gửi đơn xin phép')
        setData(await api('/my-study?week='+week))
      } catch(err){notify(err.message,'error')}
      finally {setBusy(false)}
    }}><p>{session.subject} · {session.date}</p>
      <label>Lý do<input name="reason" maxLength={500} required /></label>
      <label>Tải minh chứng (PDF, PNG, JPEG · tối đa 2 MB)<input name="file" type="file" accept=".pdf,.png,.jpg,.jpeg" /></label>
      <label>Hoặc liên kết minh chứng HTTPS<input name="evidence" type="url" /></label>
      <p className="detail-note">Nếu tải tệp, hệ thống sẽ dùng tệp làm minh chứng. Chỉ bạn, giảng viên phụ trách và Ban đào tạo được tải tệp.</p>
      <button className="primary" disabled={busy}>{busy?'Đang gửi…':'Gửi đơn'}</button></form></Modal>}
  </div>
}

export function ExportLinks({classId}) {
  return <div className="export-links">
    <a href={'/api/classes/'+classId+'/grades.xlsx'} download><FileSpreadsheet aria-hidden="true" />Bảng điểm Excel</a>
    {[['roster','Danh sách lớp'],['grades','Kết quả chốt'],['attendance','Chuyên cần']].map(([kind,label])=>
      <span className="export-group" key={kind}><span>{label}</span>
        <a aria-label={label+' CSV'} href={'/api/classes/'+classId+'/exports/'+kind} download><Download aria-hidden="true" />CSV</a>
        <a aria-label={label+' Excel'} href={'/api/classes/'+classId+'/exports/'+kind+'?format=xlsx'} download><FileSpreadsheet aria-hidden="true" />Excel</a>
      </span>)}
  </div>
}