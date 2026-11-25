import {useEffect, useId, useRef, useState} from 'react'
import {Bell, BookOpen, Check, ChevronLeft, ChevronRight, FileText, Plus, Search, Trash2} from 'lucide-react'
import {api} from './api'
import {Confirm, DialogDismiss, Modal, useToast} from './Feedback'

const base='/admin/institution'
const states={DANG_HOC:'Đang học',BAO_LUU:'Bảo lưu',THOI_HOC:'Thôi học',TOT_NGHIEP:'Tốt nghiệp'}
const genders={NAM:'Nam',NU:'Nữ',KHAC:'Khác'}
const dateTime=value=>new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'})
const entities={
  departments:{name:'Khoa',singular:'khoa',description:'Đơn vị phụ trách chuyên môn và các ngành đào tạo.',columns:[['code','Mã khoa'],['name','Tên khoa']]},
  majors:{name:'Ngành',singular:'ngành',description:'Ngành đào tạo thuộc từng khoa trong trường.',columns:[['code','Mã ngành'],['name','Tên ngành'],['department','Khoa']]},
  cohorts:{name:'Khóa học',singular:'khóa học',description:'Khóa tuyển sinh và khoảng thời gian đào tạo dự kiến.',columns:[['code','Mã khóa'],['name','Tên khóa'],['startYear','Năm bắt đầu'],['endYear','Năm kết thúc']],numeric:[2,3]},
  groups:{name:'Lớp hành chính',singular:'lớp hành chính',description:'Tổ chức sinh viên theo ngành và khóa tuyển sinh.',columns:[['code','Mã lớp'],['name','Tên lớp'],['major','Ngành'],['cohort','Khóa học']]},
  profiles:{name:'Hồ sơ sinh viên',singular:'hồ sơ sinh viên',description:'Hồ sơ cá nhân, lớp hành chính và trạng thái học tập.',columns:[['code','Mã sinh viên'],['name','Họ tên'],['group','Lớp hành chính'],['major','Ngành'],['status','Trạng thái']]},
  programs:{name:'Chương trình',singular:'chương trình',description:'Khung môn học và tín chỉ cần hoàn thành cho từng ngành.',columns:[['code','Mã chương trình'],['name','Tên chương trình'],['major','Ngành'],['credits','Tín chỉ yêu cầu']],numeric:[3]},
}

function usePage(path,size=20) {
  const [query,setQuery]=useState('')
  const [page,setPage]=useState(1)
  const [revision,setRevision]=useState(0)
  const [data,setData]=useState(null)
  const [error,setError]=useState('')
  const [loading,setLoading]=useState(true)
  useEffect(()=>{
    const controller=new AbortController()
    setLoading(true);setError('')
    const timer=setTimeout(()=>{
      api(path+'?page='+page+'&size='+size+'&q='+encodeURIComponent(query.trim()),{signal:controller.signal})
        .then(result=>{
          if(controller.signal.aborted)return
          const last=Math.max(1,Math.ceil(result.total/size))
          if(page>last){setPage(last);return}
          setData(result)
        })
        .catch(err=>{if(!controller.signal.aborted)setError(err.message)})
        .finally(()=>{if(!controller.signal.aborted)setLoading(false)})
    },query?250:0)
    return ()=>{clearTimeout(timer);controller.abort()}
  },[path,size,page,query,revision])
  return {data,error,loading,page,query,setPage,setQuery:value=>{setPage(1);setQuery(value)},reload:()=>setRevision(value=>value+1)}
}

function PageControls({state,size=20}) {
  const pages=Math.max(1,Math.ceil((state.data?.total??0)/size))
  return <div className="table-pagination"><span>{state.data?.total??0} kết quả · Trang {state.page}/{pages}</span>
    <div><button aria-label="Trang trước" disabled={state.loading || state.page<=1} onClick={()=>state.setPage(state.page-1)}><ChevronLeft aria-hidden="true" /></button>
      <button aria-label="Trang sau" disabled={state.loading || state.page>=pages} onClick={()=>state.setPage(state.page+1)}><ChevronRight aria-hidden="true" /></button></div>
  </div>
}

function SearchBar({state,label='Tìm trong danh sách'}) {
  return <div className="table-tools"><label className="table-search"><Search aria-hidden="true" /><input aria-label={label} placeholder={label} value={state.query} onChange={event=>state.setQuery(event.target.value)} /></label>
    {state.query&&<button type="button" className="table-clear" onClick={()=>state.setQuery('')}>Xóa bộ lọc</button>}
    {state.loading && <span role="status">Đang tải…</span>}</div>
}

function StateError({state}) {
  return state.error?<div className="institution-feedback"><p role="alert" className="error">{state.error}</p><button onClick={state.reload}>Thử lại</button></div>:null
}

const choices=(rows,label='name')=>rows.map(row=>({value:row.id,label:(row.code?row.code+' · ':'')+row[label]}))
function fieldsFor(entity,catalog,record) {
  const common=[{name:'code',label:'Mã',maxLength:20,pattern:'[A-Za-z0-9._\\-]+'},{name:'name',label:'Tên',maxLength:150}]
  const major={name:'majorId',label:'Ngành',type:'number',options:choices(catalog.majors)}
  switch(entity) {
    case 'departments':return common
    case 'majors':return [...common,{name:'departmentId',label:'Khoa',type:'number',options:choices(catalog.departments)}]
    case 'cohorts':return [...common,{name:'startYear',label:'Năm bắt đầu',type:'number',min:1900,max:2200},{name:'endYear',label:'Năm kết thúc',type:'number',min:1900,max:2200}]
    case 'groups':return [...common,major,{name:'cohortId',label:'Khóa học',type:'number',options:choices(catalog.cohorts)}]
    case 'programs':return [...common,major,{name:'credits',label:'Tín chỉ yêu cầu',type:'number',min:1,max:500}]
    default:return [
      {name:'studentId',label:'Sinh viên',type:'number',options:choices(catalog.students),disabled:Boolean(record?.id)},
      {name:'groupId',label:'Lớp hành chính',type:'number',options:choices(catalog.groups)},
      {name:'birthDate',label:'Ngày sinh',type:'date',max:new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date()),optional:true},
      {name:'gender',label:'Giới tính',options:Object.entries(genders).map(([value,label])=>({value,label}))},
      {name:'phone',label:'Số điện thoại',type:'tel',maxLength:20,optional:true},
      {name:'status',label:'Trạng thái học tập',options:Object.entries(states).map(([value,label])=>({value,label}))},
    ]
  }
}

function RecordForm({entity,catalog,record,onClose,onSaved}) {
  const id=useId()
  const [busy,setBusy]=useState(false)
  const [error,setError]=useState('')
  const notify=useToast()
  const fields=fieldsFor(entity,catalog,record)
  const current={gender:'KHAC',status:'DANG_HOC',credits:120,...record,studentId:record?.id}
  return <Modal title={(record?.id?'Sửa ':'Tạo ')+entities[entity].singular} busy={busy} onClose={onClose}>
    <form className="dialog-body management-form" onSubmit={async event=>{
      event.preventDefault()
      if(busy)return
      const form=new FormData(event.currentTarget)
      const body=Object.fromEntries(fields.map(field=>{
        const raw=field.disabled?current[field.name]:form.get(field.name)
        return [field.name,field.type==='number'?(raw===''||raw==null?null:Number(raw)):raw===''&&field.optional?null:raw]
      }))
      setBusy(true);setError('')
      try{
        await api(base+'/'+entity+(record?.id?'/'+record.id:''),{method:record?.id?'PUT':'POST',body})
        notify('Đã lưu '+entities[entity].singular)
        onSaved();onClose()
      }catch(err){setError(err.message)}finally{setBusy(false)}
    }}>
      {entity==='profiles' && <p className="detail-note">Tạo tài khoản sinh viên trước khi lập hồ sơ. Trạng thái học tập ảnh hưởng quyền đăng ký và điểm danh.</p>}
      <div className="form-fields">{fields.map(({name,label,options,type,optional,disabled,...attrs})=><div key={name}>
        <label htmlFor={id+name}>{label}{optional?' (không bắt buộc)':''}</label>
        {options?<select id={id+name} name={name} defaultValue={current[name]??''} required={!optional} disabled={busy||disabled}>
          <option value="" disabled={!optional}>Chọn {label.toLowerCase()}</option>
          {options.map(option=><option key={option.value} value={option.value}>{option.label}</option>)}
        </select>:<input {...attrs} type={type??'text'} id={id+name} name={name} defaultValue={current[name]??''} required={!optional} disabled={busy} />}
      </div>)}</div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="management-actions"><DialogDismiss disabled={busy}/><button className="primary" disabled={busy}>{busy?'Đang lưu…':'Lưu thay đổi'}</button></div>
    </form>
  </Modal>
}

export function Institution() {
  const [entity,setEntity]=useState('departments')
  return <div className="management institution-page"><header className="course-header"><div><p className="page-eyebrow">Hồ sơ đào tạo</p><h1>Cơ cấu đào tạo</h1><p className="page-description">Kết nối đơn vị, ngành, khóa học và hồ sơ sinh viên.</p></div></header>
    <div className="tabs institution-tabs" aria-label="Danh mục cơ cấu đào tạo">{Object.entries(entities).map(([key,item])=><button key={key} aria-pressed={key===entity} onClick={()=>setEntity(key)}>{item.name}</button>)}</div>
    <InstitutionList key={entity} entity={entity}/>
  </div>
}

function InstitutionList({entity}) {
  const state=usePage(base+'/'+entity)
  const [catalog,setCatalog]=useState(null)
  const [catalogError,setCatalogError]=useState('')
  const [catalogRevision,setCatalogRevision]=useState(0)
  const [editor,setEditor]=useState(null)
  const [removing,setRemoving]=useState(null)
  const [program,setProgram]=useState(null)
  const notify=useToast()
  const config=entities[entity]
  useEffect(()=>{
    const controller=new AbortController()
    setCatalogError('')
    api(base+'/catalog',{signal:controller.signal}).then(setCatalog).catch(err=>{if(!controller.signal.aborted)setCatalogError(err.message)})
    return ()=>controller.abort()
  },[catalogRevision])
  return <section className="report">
    <div className="section-head"><div><h2>{config.name}</h2><p>{config.description}</p></div><button className="primary" disabled={!catalog} onClick={()=>setEditor({})}><Plus aria-hidden="true" />Tạo {config.singular}</button></div>
    {catalogError && <div className="institution-feedback"><p role="alert" className="error">{catalogError}</p><button onClick={()=>setCatalogRevision(value=>value+1)}>Tải lại danh mục</button></div>}
    <SearchBar state={state}/><StateError state={state}/>
    {state.data && !state.error && <>{state.data.items.length>0 && <div className="table-wrap institution-table" role="region" tabIndex={0} aria-label={config.name}><table><caption className="sr-only">{config.name}</caption><thead><tr>{config.columns.map(([key,label],index)=><th key={key} scope="col" className={config.numeric?.includes(index)?'num':undefined}>{label}</th>)}<th scope="col">Thao tác</th></tr></thead>
      <tbody>{state.data.items.map(row=><tr key={row.id}>{config.columns.map(([key],index)=><td key={key} className={config.numeric?.includes(index)?'num':undefined}>{key==='status'?<span className={'status-badge '+(row.status==='DANG_HOC'?'positive':'warning')}>{states[row.status]??row.status}</span>:row[key]??'–'}</td>)}<td><div className="institution-row-actions">
        {entity==='programs' && <button onClick={()=>setProgram(row)} aria-label={'Khung môn học '+row.code}><BookOpen aria-hidden="true" />Môn học</button>}
        <button disabled={!catalog} onClick={()=>setEditor(row)} aria-label={'Sửa '+row.code}>Sửa</button><button onClick={()=>setRemoving(row)} aria-label={'Xóa '+row.code}>Xóa</button>
      </div></td></tr>)}</tbody></table></div>}
      {state.data.items.length===0 && <div className="institution-empty"><FileText aria-hidden="true" /><h3>{state.query?'Không tìm thấy kết quả':'Chưa có '+config.singular}</h3><p>{state.query?'Thử mã hoặc tên khác.':'Tạo hồ sơ đầu tiên để bắt đầu quản lý.'}</p></div>}
      <PageControls state={state}/></>}
    {editor && catalog && <RecordForm entity={entity} catalog={catalog} record={editor} onClose={()=>setEditor(null)} onSaved={state.reload}/>}
    {removing && <Confirm title={'Xóa '+config.singular} description={'Xóa “'+(removing.name??removing.code)+'”? Hồ sơ đang được sử dụng sẽ được hệ thống bảo vệ.'} destructive confirmLabel="Xóa hồ sơ" onClose={()=>setRemoving(null)} onConfirm={async()=>{
      await api(base+'/'+entity+'/'+removing.id,{method:'DELETE'});notify('Đã xóa '+config.singular);state.reload()
    }}/>}
    {program && <Curriculum program={program} onClose={()=>setProgram(null)}/>}
  </section>
}

function Curriculum({program,onClose}) {
  const formId=useId()
  const [rows,setRows]=useState(null)
  const [subjects,setSubjects]=useState([])
  const [error,setError]=useState('')
  const [busy,setBusy]=useState(false)
  const [removing,setRemoving]=useState(null)
  const active=useRef(true)
  const notify=useToast()
  const path=base+'/programs/'+program.id+'/courses'
  useEffect(()=>{
    active.current=true
    const controller=new AbortController()
    Promise.all([api(path,{signal:controller.signal}),api('/admin',{signal:controller.signal})]).then(([courses,catalog])=>{setRows(courses);setSubjects(catalog.subjects)})
      .catch(err=>{if(!controller.signal.aborted)setError(err.message)})
    return ()=>{active.current=false;controller.abort()}
  },[path])
  async function mutate(target,method,body) {
    setBusy(true);setError('')
    try{
      await api(target,{method,body})
      notify(method==='POST'?'Đã thêm môn vào chương trình':'Đã bỏ môn khỏi chương trình')
      setRemoving(null)
      try{const data=await api(path);if(active.current)setRows(data)}catch(err){if(active.current)setError('Thay đổi đã lưu nhưng chưa tải lại được khung môn học. '+err.message)}
    }catch(err){setError(err.message)}finally{if(active.current)setBusy(false)}
  }
  const total=(rows??[]).reduce((sum,row)=>sum+Number(row.credits),0)
  return <Modal title={'Khung môn học · '+program.code} busy={busy} onClose={onClose}><div className="dialog-body curriculum-body"><p>{program.name}</p>
    <div className="curriculum-credit"><strong>{total}<small> tín chỉ trong khung</small></strong><span>Yêu cầu hoàn thành {program.credits} tín chỉ</span></div>
    {error && <p className="error" role="alert">{error}</p>}
    {!rows && !error && <p role="status">Đang tải khung môn học…</p>}
    {rows && <><form className="curriculum-form" onSubmit={event=>{
      event.preventDefault();if(busy)return
      const data=new FormData(event.currentTarget)
      mutate(path,'POST',{subjectId:Number(data.get('subjectId')),semester:Number(data.get('semester')),required:data.get('required')==='true'})
    }}><div><label htmlFor={formId+'subject'}>Môn học</label><select id={formId+'subject'} name="subjectId" required disabled={busy}><option value="">Chọn môn học</option>{subjects.filter(subject=>!rows.some(row=>row.subjectId===subject.id)).map(subject=><option key={subject.id} value={subject.id}>{subject.code} · {subject.name}</option>)}</select></div>
      <div><label htmlFor={formId+'semester'}>Học kỳ dự kiến</label><input id={formId+'semester'} name="semester" type="number" min={1} max={20} defaultValue={1} required disabled={busy}/></div>
      <div><label htmlFor={formId+'required'}>Loại môn</label><select id={formId+'required'} name="required" disabled={busy}><option value="true">Bắt buộc</option><option value="false">Tự chọn</option></select></div><button className="primary" disabled={busy||!subjects.length}><Plus aria-hidden="true" />Thêm môn</button></form>
      <div className="table-wrap curriculum-table" role="region" tabIndex={0} aria-label={'Khung môn học ' + program.code}><table><caption className="sr-only">{'Khung môn học ' + program.code}</caption><thead><tr><th>Mã môn</th><th>Môn học</th><th className="num">TC</th><th className="num">Học kỳ</th><th>Loại</th><th>Thao tác</th></tr></thead><tbody>{rows.map(row=><tr key={row.id}><td>{row.code}</td><td>{row.subject}</td><td className="num">{row.credits}</td><td className="num">{row.semester}</td><td>{row.required?'Bắt buộc':'Tự chọn'}</td><td><button disabled={busy} onClick={()=>setRemoving(row)} aria-label={'Bỏ môn '+row.code}><Trash2 aria-hidden="true" /></button></td></tr>)}</tbody></table></div>
      {rows.length===0 && <p className="empty">Chưa có môn trong chương trình.</p>}
      {removing && <div className="curriculum-confirm" role="alert"><p>Bỏ môn {removing.code} khỏi chương trình?</p><div><button disabled={busy} onClick={()=>setRemoving(null)}>Giữ lại</button><button className="primary" disabled={busy} onClick={()=>mutate(path+'/'+removing.id,'DELETE')}>Bỏ môn</button></div></div>}
    </>}
  </div></Modal>
}

export function MyProgram() {
  const [data,setData]=useState(null)
  const [error,setError]=useState('')
  const [revision,setRevision]=useState(0)
  useEffect(()=>{
    const controller=new AbortController()
    setError('')
    api('/my-program',{signal:controller.signal}).then(setData).catch(err=>{if(!controller.signal.aborted)setError(err.message)})
    return ()=>controller.abort()
  },[revision])
  return <div className="management institution-page"><header className="course-header"><div><p className="page-eyebrow">Lộ trình cá nhân</p><h1>Chương trình học</h1><p className="page-description">Đối chiếu kết quả đã chốt với khung môn học của ngành.</p></div></header>
    {error && <div className="institution-feedback"><p className="error" role="alert">{error}</p><button onClick={()=>setRevision(value=>value+1)}>Thử lại</button></div>}
    {!data&&!error&&<section className="report" aria-label="Đang tải chương trình học"><div className="skeleton skeleton-study" role="status">
      <div className="skeleton-line" style={{width:'32%'}} />
      <div className="skeleton-line" style={{width:'54%'}} />
      <div className="skeleton-row" /><div className="skeleton-row" />
    </div></section>}
    {data && <>{data.profile?<section className="report profile-summary"><div><span>Hồ sơ sinh viên</span><h2>{data.profile.name}</h2><p>{data.profile.code} · {data.profile.group??'Chưa phân lớp'}</p></div><dl><div><dt>Ngành</dt><dd>{data.profile.major??'–'}</dd></div><div><dt>Khóa học</dt><dd>{data.profile.cohort??'–'}</dd></div><div><dt>Tín chỉ tích lũy</dt><dd>{data.completedCredits??0}</dd></div><div><dt>Trạng thái</dt><dd>{states[data.profile.status]??'–'}</dd></div></dl></section>:<section className="report institution-empty"><FileText aria-hidden="true"/><h2>Chưa có hồ sơ đào tạo</h2><p>Liên hệ Ban đào tạo để được phân lớp hành chính và ngành học.</p></section>}
      {data.profile && data.programs.length===0 && <section className="report institution-empty"><BookOpen aria-hidden="true"/><h2>Ngành chưa có chương trình học</h2><p>Khung môn học sẽ xuất hiện khi Ban đào tạo thiết lập chương trình.</p></section>}
      {data.programs.map(program=>{
        const completed=program.courses.filter(course=>course.passed).reduce((sum,course)=>sum+Number(course.credits),0)
        const rate=Math.min(100,Math.round(completed*100/program.credits))
        return <section className="report program-progress" key={program.id}><div className="section-head"><div><small>{program.code}</small><h2>{program.name}</h2><p>{completed}/{program.credits} tín chỉ hoàn thành trong chương trình</p></div><strong>{rate}%</strong></div>
          <progress max={100} value={rate} aria-label={'Tiến độ '+program.name}/>
          <div className="table-wrap" role="region" tabIndex={0} aria-label={'Khung môn ' + program.name}><table><caption className="sr-only">{'Khung môn ' + program.name}</caption><thead><tr><th>Mã môn</th><th>Môn học</th><th className="num">Tín chỉ</th><th className="num">Học kỳ</th><th>Loại</th><th>Kết quả</th></tr></thead><tbody>{program.courses.map(course=><tr key={course.id}><td>{course.code}</td><td>{course.subject}</td><td className="num">{course.credits}</td><td className="num">{course.semester}</td><td>{course.required?'Bắt buộc':'Tự chọn'}</td><td><span className={'status-badge '+(course.passed?'positive':'warning')}>{course.passed?'Đã đạt · '+course.letter:(course.letter?'Chưa đạt · '+course.letter:'Chưa hoàn thành')}</span></td></tr>)}</tbody></table></div>
          {program.courses.length===0&&<p className="empty">Chương trình chưa được bổ sung môn học.</p>}
        </section>
      })}
    </>}
  </div>
}

export function Operations() {
  const state=usePage('/admin/audit')
  return <div className="management institution-page"><header className="course-header"><div><p className="page-eyebrow">Theo dõi vận hành</p><h1>Nhật ký hệ thống</h1><p className="page-description">Tra cứu thao tác thay đổi dữ liệu và kết quả xử lý trên hệ thống.</p></div></header><section className="report"><SearchBar state={state} label="Tìm tài khoản hoặc đường dẫn"/><StateError state={state}/>
    {state.data&&!state.error&&<>{(state.data.items.length>0)&&<div className="table-wrap institution-table" tabIndex={0} role="region" aria-label="Nhật ký hệ thống"><table><caption className="sr-only">Nhật ký hệ thống</caption><thead><tr><th>Thời gian</th><th>Tài khoản</th><th>Thao tác</th><th>Đường dẫn</th><th className="num">Kết quả</th><th>Mã tra cứu</th></tr></thead><tbody>{state.data.items.map(row=><tr key={row.id}><td className="audit-time">{dateTime(row.time)}</td><td>{row.actor??'Khách'}</td><td>{row.method}</td><td className="audit-path">{row.path}</td><td className="num"><span className={'status-badge '+(row.status<400?'positive':'warning')}>{row.status}</span></td><td><code className="audit-request" title={row.requestId}>{row.requestId}</code></td></tr>)}</tbody></table></div>}
      {state.data.items.length===0&&<p className="empty">Chưa có thao tác phù hợp với bộ lọc.</p>}<PageControls state={state}/></>}
  </section></div>
}

export function Notifications() {
  const state=usePage('/notifications',10)
  const [busy,setBusy]=useState(false)
  const [error,setError]=useState('')
  const notify=useToast()
  async function read(id) {
    if(busy)return
    setBusy(true);setError('')
    try{await api('/notifications/'+(id?id+'/read':'read-all'),{method:'PUT'});state.reload();if(!id)notify('Đã đánh dấu tất cả là đã đọc')}
    catch(err){setError(err.message)}finally{setBusy(false)}
  }
  return <div className="management institution-page"><header className="course-header"><div><p className="page-eyebrow">Hộp thư học vụ</p><h1>Thông báo</h1><p className="page-description">Cập nhật dành riêng cho tài khoản của bạn.</p></div><button disabled={busy||!state.data?.unread} onClick={()=>read()}><Check aria-hidden="true"/>Đánh dấu tất cả đã đọc</button></header>
    <section className="report notification-report"><div className="section-head"><h2>Cập nhật mới</h2><span>{state.data?.unread??0} chưa đọc</span></div><StateError state={state}/>{error&&<p role="alert" className="error institution-feedback">{error}</p>}
      {state.loading&&!state.data&&<div className="skeleton" role="status" aria-label="Đang tải thông báo">
        <div className="skeleton-line" /><div className="skeleton-line" style={{width:'54%'}} />
        <div className="skeleton-row" /><div className="skeleton-row" /><div className="skeleton-row" />
      </div>}
      {state.data&&!state.error&&<>{state.data.items.length===0?<div className="institution-empty"><Bell aria-hidden="true"/><h2>Chưa có thông báo</h2><p>Các cập nhật về học tập và yêu cầu của bạn sẽ xuất hiện tại đây.</p></div>:<ol className="notification-list">{state.data.items.map(row=><li key={row.id} className={row.read?'':'is-unread'}><span className="notification-marker" aria-hidden="true"/><div><h3>{row.title}</h3><p>{row.message}</p><time dateTime={new Date(row.time).toISOString()}>{dateTime(row.time)}</time></div>{!row.read&&<button disabled={busy} onClick={()=>read(row.id)} aria-label={'Đánh dấu đã đọc: '+row.title}><Check aria-hidden="true"/>Đã đọc</button>}</li>)}</ol>}<PageControls state={state} size={10}/></>}
    </section>
  </div>
}
