import {Children, cloneElement, isValidElement, useEffect, useId, useRef, useState} from 'react'
import {ArrowUpRight, Plus, Search, ChevronLeft, ChevronRight, ArrowDownUp, Users, BookOpen, GraduationCap, CalendarDays, CheckCircle2, Upload, FileSpreadsheet} from 'lucide-react'
import {api} from './api'
import {Confirm, DialogDismiss, Modal, useToast} from './Feedback'

const roles = {ADMIN: 'Ban đào tạo', GIANG_VIEN: 'Giảng viên', SINH_VIEN: 'Sinh viên'}
const marks = {CO_MAT: 'Có mặt', DI_MUON: 'Đi muộn', VANG: 'Vắng', CO_PHEP: 'Có phép'}
const options = values => Object.entries(values).map(([value, label]) => ({value, label}))
const adminSections = {
  terms: {title:'Học kỳ', headings:['Học kỳ','Mở đăng ký','Đóng đăng ký','Ngưỡng chuyên cần'],
    cells:row => [row.code,row.opensOn,row.closesOn,row.threshold + '%']},
  users: {title: 'Tài khoản', headings: ['Mã tài khoản', 'Họ tên', 'Email', 'Vai trò', 'Trạng thái'],
    cells: row => [row.code, row.name, row.email ?? '–', roles[row.role],
      <span className={'status-badge '+(row.locked?'warning':'positive')}>{row.locked?'Bị khóa':'Hoạt động'}</span>]},
  students: {title: 'Sinh viên', headings: ['Mã sinh viên', 'Họ tên', 'Email', 'Số lớp', 'Trạng thái'],
    cells: row => [row.code, row.name, row.email ?? '–', row.classCount,
      <span className={'status-badge '+(row.locked?'warning':'positive')}>{row.locked?'Bị khóa':'Hoạt động'}</span>]},
  subjects: {title: 'Môn học', headings: ['Mã môn', 'Tên môn học', 'Tín chỉ'],
    cells: row => [row.code, row.name, row.credits]},
  classes: {title: 'Lớp học phần', headings: ['Mã lớp', 'Môn học', 'Giảng viên', 'Học kỳ', 'Sĩ số'],
    cells: row => [row.code, row.subject, row.teacher, row.term, row.enrollment + '/' + row.capacity]},
}

function adminFields(section, catalog, editing) {
  const code = (label) => ({name: 'code', label, maxLength: 20, pattern: '[A-Za-z0-9._\\-]+'})
  switch (section) {
    case 'terms':
      return [code('Học kỳ'), {name:'opensOn',label:'Mở đăng ký',type:'date'},
        {name:'closesOn',label:'Đóng đăng ký',type:'date'},
        {name:'threshold',label:'Ngưỡng chuyên cần (%)',type:'number',min:0,max:100}]
    case 'users':
      return [code('Mã tài khoản'), {name:'name', label:'Họ tên', maxLength:100},
        {name:'email', label:'Email', type:'email', maxLength:100, optional:true},
        {name:'role', label:'Vai trò', options:options(roles)},
        {name:'locked',label:'Trạng thái tài khoản',options:[{value:0,label:'Hoạt động'},{value:1,label:'Bị khóa'}]},
        {name:'password', label:editing ? 'Mật khẩu mới' : 'Mật khẩu', type:'password', minLength:8,
          optional:editing, hint:editing ? 'Để trống để giữ mật khẩu hiện tại' : undefined}]
    case 'students':
      return [code('Mã sinh viên'), {name:'name', label:'Họ tên', maxLength:100},
        {name:'email', label:'Email', type:'email', maxLength:100, optional:true},
        {name:'groupId', label:'Lớp hành chính', type:'number', optional:editing,
          options:catalog.groups.map(row=>({value:row.id,label:row.code + ' · ' + row.name}))},
        {name:'birthDate', label:'Ngày sinh', type:'date', optional:true},
        {name:'gender', label:'Giới tính', optional:true, options:options({NAM:'Nam',NU:'Nữ',KHAC:'Khác'})},
        {name:'phone', label:'Điện thoại', maxLength:25, optional:true},
        {name:'status', label:'Trạng thái sinh viên', optional:true,
          options:options({DANG_HOC:'Đang học',BAO_LUU:'Bảo lưu',THOI_HOC:'Thôi học',TOT_NGHIEP:'Tốt nghiệp'})},
        {name:'programId', label:'Chương trình tốt nghiệp', type:'number', optional:true,
          options:catalog.programs.map(row=>({value:row.id,label:row.code + ' · ' + row.name}))},
        {name:'locked',label:'Trạng thái tài khoản',options:[{value:0,label:'Hoạt động'},{value:1,label:'Bị khóa'}]},
        {name:'password', label:editing ? 'Mật khẩu mới' : 'Mật khẩu', type:'password', minLength:8,
          optional:editing, hint:editing ? 'Để trống để giữ mật khẩu hiện tại' : undefined}]
    case 'subjects':
      return [code('Mã môn'), {name:'name', label:'Tên môn học', maxLength:100},
        {name:'credits', label:'Số tín chỉ', type:'number', min:1, max:30},
        {name:'prerequisiteId',label:'Môn tiên quyết',type:'number',optional:true,
          options:catalog.subjects.map(row=>({value:row.id,label:row.name}))},
        ...['attendance','coursework','exam'].map((name,i)=>({name,label:['Trọng số chuyên cần (%)','Trọng số quá trình (%)','Trọng số thi (%)'][i],
          type:'number',min:0,max:100}))]
    default:
      return [code('Mã lớp'), {name:'subjectId', label:'Môn học', type:'number',
        options:catalog.subjects.map(row => ({value:row.id, label:row.code + ' · ' + row.name}))},
      {name:'teacherId', label:'Giảng viên', type:'number',
        options:catalog.users.filter(row => row.role === 'GIANG_VIEN').map(row => ({value:row.id, label:row.name}))},
      {name:'term', label:'Học kỳ', pattern:'[0-9]{4}\\-[1-3]', placeholder:'2026-1'}, {name:'capacity',label:'Sĩ số tối đa',type:'number',min:1,max:500}]
  }
}

function Editor({title, fields, record, onSave, onCancel, saveLabel = 'Lưu thay đổi'}) {
  const formId = useId()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  return <Modal title={title} busy={busy} onClose={onCancel}><form className="management-form dialog-body" onSubmit={async event => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const body = Object.fromEntries(fields.map(field => {
      const value = data.get(field.name)
      return [field.name, field.name === 'locked' ? value === '1' : field.type === 'number' ? (value === '' ? null : Number(value)) : value]
    }))
    setBusy(true)
    setError('')
    try { await onSave(body) }
    catch (err) { setError(err.message) }
    finally { setBusy(false) }
  }}>
    <div className="form-fields">
      {fields.map(({name, label, options, optional, hint, ...attributes}) =>
        <div key={name}>
          <label htmlFor={formId + name}>{label}</label>
            {options ? <select id={formId + name} name={name} defaultValue={record?.[name] ?? ''} required={!optional}>
              <option value="" disabled={!optional}>Chọn {label.toLowerCase()}</option>
              {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select> : <input {...attributes} id={formId + name} name={name} defaultValue={record?.[name] ?? ''} required={!optional}
              aria-describedby={hint ? formId + name + '-hint' : undefined}
              autoComplete={attributes.type === 'password' ? 'new-password' : undefined} />}
          {hint && <small id={formId + name + '-hint'}>{hint}</small>}
        </div>)}
    </div>
    {error && <p role="alert" className="error">{error}</p>}
    <div className="management-actions">
      <DialogDismiss disabled={busy} />
      <button className="primary" disabled={busy}>{busy ? 'Đang lưu…' : saveLabel}</button>
    </div>
  </form></Modal>
}

function elementText(node) {
  if (typeof node === 'string' || typeof node === 'number') return String(node)
  if (isValidElement(node)) return elementText(node.props.children)
  if (Array.isArray(node)) return node.map(elementText).join(' ')
  return ''
}

function DataTable({headings, children, pageSize=8, label='Bảng dữ liệu', numeric=[]}) {
  const [query,setQuery]=useState('')
  const [page,setPage]=useState(1)
  const [sort,setSort]=useState({index:null,ascending:true})
  const rows=Children.toArray(children).filter(row=>elementText(row).toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi')))
  const sorted=sort.index===null?rows:[...rows].sort((a,b)=>{
    const left=elementText(Children.toArray(a.props.children)[sort.index])
    const right=elementText(Children.toArray(b.props.children)[sort.index])
    return left.localeCompare(right,'vi',{numeric:true})*(sort.ascending?1:-1)
  })
  const pageCount=Math.max(1,Math.ceil(rows.length/pageSize))
  const currentPage=Math.min(page,pageCount)
  const id=useId()
  const decorate=row=>numeric.length===0?row:cloneElement(row,{},
    Children.map(row.props.children,(cell,index)=>isValidElement(cell)&&numeric.includes(index)
      ? cloneElement(cell,{className:[cell.props.className,'num'].filter(Boolean).join(' ')})
      : cell))
  return <div className="data-table">
    <div className="table-tools"><label className="table-search" htmlFor={id}><Search aria-hidden="true" />
      <input id={id} aria-label="Tìm trong bảng" value={query} onChange={event=>{setQuery(event.target.value);setPage(1)}} placeholder="Tìm trong danh sách" /></label>
      <span aria-live="polite">{rows.length} kết quả</span></div>
    <div className="table-wrap management-table" role="region" aria-label={label} tabIndex={0}>
      <table><caption className="sr-only">{label}</caption><thead><tr>{headings.map((heading,index)=><th key={heading} scope="col"
        className={numeric.includes(index)?'num':undefined}
        aria-sort={sort.index===index?(sort.ascending?'ascending':'descending'):undefined}>
        {heading==='Thao tác'?heading:<button className="column-sort" onClick={()=>setSort({index,ascending:sort.index===index?!sort.ascending:true})}>{heading}<ArrowDownUp aria-hidden="true" /></button>}
      </th>)}</tr></thead><tbody>{sorted.slice((currentPage-1)*pageSize,currentPage*pageSize).map(decorate)}</tbody></table>
      {rows.length===0 && <div className="empty-state table-empty">
        <Search aria-hidden="true" />
        <h3>{query?'Không có kết quả cho “'+query+'”':'Chưa có dữ liệu để hiển thị'}</h3>
        <p>{query?'Thử từ khóa khác hoặc xóa bộ lọc để xem toàn bộ danh sách.':'Danh mục này sẽ hiển thị khi có bản ghi đầu tiên.'}</p>
        {query&&<button type="button" onClick={()=>{setQuery('');setPage(1)}}>Xóa bộ lọc</button>}
      </div>}
    </div>
    <div className="table-pagination"><span>Trang {currentPage} / {pageCount}</span>
      <button aria-label="Trang trước" disabled={currentPage===1} onClick={()=>setPage(currentPage-1)}><ChevronLeft aria-hidden="true" /></button>
      <button aria-label="Trang sau" disabled={currentPage===pageCount} onClick={()=>setPage(currentPage+1)}><ChevronRight aria-hidden="true" /></button></div>
  </div>
}

function useList(path) {
  const [items,setItems]=useState(null)
  const [error,setError]=useState('')
  const [busy,setBusy]=useState(false)
  const notify=useToast()
  const version=useRef(0)
  const mutating=useRef(false)
  const alive=useRef(true)

  useEffect(()=>{
    alive.current=true
    const controller=new AbortController()
    let timer
    async function load() {
      if(mutating.current){
        timer=setTimeout(load,4000)
        return
      }
      const request=++version.current
      try {
        const result=await api(path,{signal:controller.signal})
        if(!controller.signal.aborted && request===version.current){
          setItems(result)
          setError('')
        }
      } catch(err) {
        if(!controller.signal.aborted && request===version.current)setError(err.message)
      } finally {
        if(!controller.signal.aborted && path.includes('/session-management/'))timer=setTimeout(load,4000)
      }
    }
    load()
    return ()=>{
      alive.current=false
      version.current++
      controller.abort()
      clearTimeout(timer)
    }
  },[path])

  async function change(target,method,body,message) {
    if(mutating.current)return
    mutating.current=true
    version.current++
    setBusy(true)
    setError('')
    try {
      await api(target,{method,body})
      notify(message)
      try {
        const result=await api(path)
        if(alive.current)setItems(result)
      } catch(err) {
        if(alive.current)setError('Thao tác đã lưu nhưng chưa tải lại được danh sách. '+err.message)
      }
    } finally {
      mutating.current=false
      if(alive.current)setBusy(false)
    }
  }

  async function reload() {
    try {
      const result = await api(path)
      if (alive.current) setItems(result)
    } catch (err) {
      if (alive.current) setError(err.message)
    }
  }

  return {items,error,change,busy,reload}
}

function Feedback({state}) {
  return <>
    {state.error && <p className="error panel-feedback" role="alert">{state.error}</p>}
    {!state.items && !state.error && <div className="skeleton" role="status" aria-label="Đang tải dữ liệu">
      <div className="skeleton-line" />
      <div className="skeleton-line" style={{width:'54%'}} />
      <div className="skeleton-row" />
      <div className="skeleton-row" />
      <div className="skeleton-row" />
    </div>}
  </>
}

function RosterDialog({classRow, students, onClose, onChanged}) {
  const path = '/admin/classes/' + classRow.id + '/roster'
  const state = useList(path)
  const notify = useToast()
  const [newStudent, setNewStudent] = useState('')
  const [adding, setAdding] = useState(false)
  const enrolled = new Set((state.items ?? []).map(row => row.id))
  const available = students.filter(row => !row.locked && !enrolled.has(row.id))

  async function addStudent(event) {
    event.preventDefault()
    if (!newStudent) return
    setAdding(true)
    try {
      await state.change(path, 'POST', {studentId:Number(newStudent)}, 'Đã thêm sinh viên vào lớp')
      setNewStudent('')
      await onChanged()
    } catch (err) { notify(err.message, 'error') }
    finally { setAdding(false) }
  }

  async function removeStudent(row) {
    try {
      await state.change(path + '/' + row.id, 'DELETE', undefined, 'Đã gỡ ' + row.name + ' khỏi lớp')
      await onChanged()
    } catch (err) { notify(err.message, 'error') }
  }

  return <Modal title={'Danh sách sinh viên · ' + classRow.code} onClose={onClose} busy={adding}>
    <div className="dialog-body">
      <p className="roster-summary">{classRow.subject} · {classRow.teacher} · {classRow.term}
        · {state.items ? state.items.length : '…'}/{classRow.capacity} sinh viên</p>
      <Feedback state={state} />
      {state.items && <form className="roster-add" onSubmit={addStudent}>
        <label htmlFor="roster-student">Thêm sinh viên</label>
        <select id="roster-student" value={newStudent} onChange={event => setNewStudent(event.target.value)}
          disabled={adding || available.length === 0}>
          <option value="">{available.length ? 'Chọn sinh viên' : 'Không còn sinh viên để thêm'}</option>
          {available.map(row => <option key={row.id} value={row.id}>{row.code} · {row.name}</option>)}
        </select>
        <button className="primary" disabled={adding || !newStudent}>Thêm</button>
      </form>}
      {state.items?.length > 0 && <DataTable label={'Sinh viên lớp ' + classRow.code} headings={['Mã SV', 'Họ tên', 'Đăng ký', 'Thao tác']}>
        {state.items.map(row => <tr key={row.id}>
          <td>{row.code}</td><td>{row.name}</td><td>{row.enrolledOn ?? '–'}</td>
          <td><button className="danger" disabled={adding || state.busy} onClick={() => removeStudent(row)}>Gỡ khỏi lớp</button></td>
        </tr>)}
      </DataTable>}
      {state.items?.length === 0 && !state.error && <p className="empty">Lớp chưa có sinh viên. Chọn sinh viên ở trên để thêm vào lớp.</p>}
      <div className="management-actions"><DialogDismiss disabled={adding}>Đóng</DialogDismiss></div>
    </div>
  </Modal>
}

const csvHeaders = {ma_so:'code',code:'code','mã sinh viên':'code',ho_ten:'name',name:'name','họ tên':'name',
  email:'email',mat_khau:'password',password:'password',lop_hanh_chinh:'group',group:'group',
  ngay_sinh:'birthDate',birthdate:'birthDate',gioi_tinh:'gender',gender:'gender',
  dien_thoai:'phone',phone:'phone',trang_thai:'status',status:'status',
  chuong_trinh:'program',program:'program'}
const genderCodes = {nam:'NAM',nữ:'NU',nu:'NU',khác:'KHAC',khac:'KHAC'}
const statusCodes = {'đang học':'DANG_HOC','dang hoc':'DANG_HOC',dang_hoc:'DANG_HOC','bảo lưu':'BAO_LUU',
  'bao luu':'BAO_LUU',bao_luu:'BAO_LUU','thôi học':'THOI_HOC','thoi hoc':'THOI_HOC',thoi_hoc:'THOI_HOC',
  'tốt nghiệp':'TOT_NGHIEP','tot nghiep':'TOT_NGHIEP',tot_nghiep:'TOT_NGHIEP'}

function parseCsv(text) {
  const rows=[]; let field='', row=[], quoted=false
  for (let i=0;i<text.length;i++) {
    const ch=text[i]
    if (quoted) {
      if (ch==='"' && text[i+1]==='"') { field+='"'; i++ }
      else if (ch==='"') quoted=false
      else field+=ch
    } else if (ch==='"') quoted=true
    else if (ch===',') { row.push(field); field='' }
    else if (ch==='\n') { row.push(field); rows.push(row); row=[]; field='' }
    else if (ch!=='\r') field+=ch
  }
  if (field || row.length) { row.push(field); rows.push(row) }
  return rows.filter(cells=>cells.some(cell=>cell.trim()!==''))
}

function parseStudentCsv(text, catalog) {
  const lines=parseCsv(text.replace(/^\uFEFF/,''))
  if (!lines.length) return []
  const keys=lines[0].map(header=>csvHeaders[header.trim().toLowerCase()] ?? header.trim().toLowerCase())
  const groupBy = value => catalog.groups.find(item=>item.code===value || String(item.id)===value)
  const programBy = value => catalog.programs.find(item=>item.code===value || String(item.id)===value)
  return lines.slice(1).map(cells => {
    const raw=Object.fromEntries(keys.map((key,i)=>[key,(cells[i]??'').trim()]))
    const gender=(raw.gender??'').toLowerCase(), status=(raw.status??'').toLowerCase()
    return {
      code:raw.code, name:raw.name, email:raw.email || null, password:raw.password,
      groupId:groupBy(raw.group)?.id ?? null, birthDate:raw.birthDate || null,
      gender:genderCodes[gender] ?? (raw.gender || null), phone:raw.phone || null,
      status:statusCodes[status] ?? (raw.status || null),
      programId:programBy(raw.program)?.id ?? null, locked:false,
    }
  })
}

function ImportStudentsDialog({catalog, onClose, onImported}) {
  const fileInput=useRef(null)
  const [rows,setRows]=useState([])
  const [fileName,setFileName]=useState('')
  const [error,setError]=useState('')
  const [result,setResult]=useState(null)
  const [busy,setBusy]=useState(false)

  function downloadTemplate() {
    const sample=catalog.groups[0]?.code ?? ''
    const csv='ma_so,ho_ten,email,mat_khau,lop_hanh_chinh,ngay_sinh,gioi_tinh,dien_thoai,trang_thai,chuong_trinh\n'
      +'sv100,Nguyen Van A,sv100@example.com,matkhau123,'+sample+',2004-05-06,Nam,0905123456,Dang hoc,\n'
    const url=URL.createObjectURL(new Blob(['\uFEFF'+csv],{type:'text/csv;charset=utf-8'}))
    const link=document.createElement('a')
    link.href=url; link.download='danh-sach-sinh-vien-mau.csv'; link.click()
    URL.revokeObjectURL(url)
  }

  async function chooseFile(event) {
    const file=event.target.files?.[0]
    if (!file) return
    setFileName(file.name); setError(''); setResult(null)
    try {
      const parsed=parseStudentCsv(await file.text(), catalog)
      setRows(parsed)
      if (!parsed.length) setError('Tệp không có dòng sinh viên nào.')
    } catch { setRows([]); setError('Không đọc được tệp CSV.') }
  }

  async function submit(event) {
    event.preventDefault()
    if (!rows.length) return
    setBusy(true); setError('')
    try {
      const response=await api('/admin/students/import',{method:'POST',body:{rows}})
      setResult(response)
      if (response.created) await onImported()
    } catch (err) { setError(err.message) }
    finally { setBusy(false) }
  }

  return <Modal title="Nhập sinh viên từ CSV" busy={busy} onClose={onClose}>
    <form className="management-form dialog-body" onSubmit={submit}>
      <p className="page-description">Mỗi dòng là một sinh viên. Bắt buộc: mã, họ tên, mật khẩu và lớp hành chính. Tối đa 500 dòng; nếu có dòng lỗi thì toàn bộ tệp không được lưu.</p>
      <div className="management-actions">
        <button type="button" onClick={downloadTemplate}><FileSpreadsheet aria-hidden="true" />Tải file mẫu</button>
        <button type="button" onClick={()=>fileInput.current?.click()}><Upload aria-hidden="true" />Chọn tệp CSV</button>
        <input ref={fileInput} type="file" accept=".csv,text/csv" hidden onChange={chooseFile} aria-label="Chọn tệp CSV" />
      </div>
      {fileName && <p className="page-description">Tệp: {fileName} · {rows.length} sinh viên</p>}
      {rows.length > 0 && <div className="table-wrap management-table" role="region" aria-label="Xem trước sinh viên" tabIndex={0}>
        <table><thead><tr><th>Mã</th><th>Họ tên</th><th>Lớp hành chính</th><th>Mật khẩu</th></tr></thead>
          <tbody>{rows.slice(0,5).map((row,index)=><tr key={index}>
            <td>{row.code || '–'}</td><td>{row.name || '–'}</td><td>{row.groupId ?? '–'}</td>
            <td>{row.password ? '••••••••' : '–'}</td>
          </tr>)}</tbody></table>
      </div>}
      {rows.length > 5 && <p className="page-description">…và {rows.length-5} sinh viên khác.</p>}
      {result && <p className={result.created ? 'status-badge positive' : 'error'} role="status">
        {result.created ? 'Đã tạo '+result.created+' sinh viên thành công.' : 'Không có sinh viên nào được tạo.'}
      </p>}
      {result?.errors?.map((item,index)=><p key={index} className="error" role="alert">Dòng {item.row} ({item.code || '–'}): {item.message}</p>)}
      {error && <p className="error" role="alert">{error}</p>}
      <div className="management-actions"><DialogDismiss disabled={busy} />
        <button className="primary" disabled={busy || !rows.length}>{busy?'Đang nhập…':'Nhập sinh viên'}</button>
      </div>
    </form>
  </Modal>
}

export function AdminPanel({refreshClasses, account}) {
  const state = useList('/admin')
  const [section, setSection] = useState('overview')
  const [editor, setEditor] = useState(null)
  const [removing, setRemoving] = useState(null)
  const [rosterClass, setRosterClass] = useState(null)
  const [importing, setImporting] = useState(false)
  const {title, headings, cells} = adminSections[section] ?? {}
  const path = '/admin/' + (section === 'students' ? 'users' : section)
  const rows = section === 'students'
    ? (state.items?.users ?? []).filter(row => row.role === 'SINH_VIEN')
    : (section === 'overview' ? [] : state.items?.[section] ?? [])
  const students = (state.items?.users ?? []).filter(row => row.role === 'SINH_VIEN')

  function navigate(key, create = false) {
    setSection(key)
    setEditor(create ? {} : null)
    setRosterClass(null)
  }

  function closeRoster() {
    const row = rosterClass
    setRosterClass(null)
    if (row) refreshClasses().catch(() => {})
  }

  return <div className="management admin-management">
    <header className="course-header dashboard-heading">
      <div><p className="page-eyebrow">Điều hành học vụ</p><h1>{section === 'overview' ? 'Tổng quan đào tạo' : title}</h1>
        <p className="page-description">{section==='overview'?'Theo dõi lớp học và những sinh viên cần được hỗ trợ.':'Quản lý thông tin, cập nhật và tra cứu hồ sơ đào tạo.'}</p></div>
      {section === 'overview' && state.items && <div className="header-actions">
        <button type="button" onClick={() => setImporting(true)}><Upload aria-hidden="true" />Nhập sinh viên</button>
        <button type="button" onClick={() => navigate('students', true)}><GraduationCap aria-hidden="true" />Tạo sinh viên</button>
        <button className="primary" onClick={() => navigate('classes', true)}><Plus aria-hidden="true" />Tạo lớp học phần</button>
      </div>}
    </header>
    <div className="tabs management-tabs" aria-label="Danh mục quản lý">
      {[['overview', {title:'Tổng quan'}], ...Object.entries(adminSections)].map(([key, value]) =>
        <button key={key} aria-pressed={section === key} disabled={state.busy} onClick={() => navigate(key)}>
          {value.title}
        </button>)}
    </div>
    <Feedback state={state} />
    {section === 'overview' ? state.items && <AdminOverview catalog={state.items} navigate={navigate} /> :
    <section className="report">
      <div className="section-head"><h2>Danh sách {title.toLowerCase()}</h2>
        <div className="header-actions">
          {section === 'students' && state.items && <button type="button" disabled={state.busy} onClick={() => setImporting(true)}>
            <Upload aria-hidden="true" />Nhập CSV
          </button>}
          {state.items && rows.length > 0 && <button className="primary" disabled={state.busy} onClick={() => setEditor({})}>
            <Plus aria-hidden="true" />Tạo mới
          </button>}
        </div>
      </div>
      {editor && <Editor key={section + '/' + (editor.id ?? 'new')}
        title={(editor.id ? 'Sửa ' : 'Tạo ') + title.toLowerCase()}
        fields={adminFields(section, state.items, Boolean(editor.id))}
        record={{attendance:10,coursework:30,exam:60,capacity:60,locked:0,threshold:80,...editor}} onCancel={() => setEditor(null)} saveLabel={editor.id ? 'Lưu thay đổi' : 'Tạo mới'}
        onSave={async body => {
          const payload = section === 'students' ? {...body, role:'SINH_VIEN'} : body
          await state.change(section === 'terms' ? path + '/' + body.code : editor.id ? path + '/' + editor.id : path,
            section === 'terms' || editor.id ? 'PUT' : 'POST', payload, 'Đã lưu ' + body.code)
          setEditor(null)
          await refreshClasses()
        }} />}
      {removing && <Confirm title={'Xóa ' + title.toLowerCase()} description={'Xóa “' + removing.code + '” khỏi danh mục?'}
        confirmLabel="Xác nhận xóa" destructive onClose={() => setRemoving(null)}
        onConfirm={async () => {
          await state.change(path + '/' + removing.id, 'DELETE', undefined, 'Đã xóa ' + removing.code)
          await refreshClasses()
        }} />}
      {rosterClass && <RosterDialog classRow={rosterClass} students={students} onClose={closeRoster}
        onChanged={refreshClasses} />}
      {importing && state.items && <ImportStudentsDialog catalog={state.items} onClose={() => setImporting(false)}
        onImported={async () => { await state.reload(); await refreshClasses?.() }} />}
      {state.items && (rows.length === 0 ? <div className="empty-state">
        <BookOpen aria-hidden="true" />
        <h3>Chưa có {title.toLowerCase()}</h3>
        <p>Danh mục này chưa có bản ghi nào. Tạo bản ghi đầu tiên để bắt đầu quản lý.</p>
        <button className="primary" type="button" disabled={state.busy} onClick={() => setEditor({})}><Plus aria-hidden="true" />Tạo mới</button>
      </div> :
        <DataTable key={section} label={'Danh sách ' + title.toLowerCase()}
          numeric={section === 'terms' ? [3] : section === 'subjects' || section === 'students' ? [section === 'subjects' ? 2 : 3] : []}
          headings={[...headings, 'Thao tác']}>
          {rows.map(row => <tr key={row.id ?? row.code}>
            {cells(row).map((cell, index) => <td key={index}>{cell}</td>)}
            <td><div className="row-actions">
              {section === 'classes' && <button disabled={state.busy} aria-label={'Danh sách sinh viên lớp ' + row.code}
                onClick={() => setRosterClass(row)}><Users aria-hidden="true" />Danh sách sinh viên</button>}
              <button disabled={state.busy} aria-label={'Sửa ' + row.code} onClick={() => setEditor(section === 'terms' ? {...row,id:row.code} : row)}>Sửa</button>
              {section !== 'terms' && !(section === 'users' && row.code === account.code) &&
                <button disabled={state.busy} className="danger" aria-label={'Xóa ' + row.code} onClick={() => setRemoving(row)}>Xóa</button>}
            </div></td>
          </tr>)}
        </DataTable>)}
    </section>}
  </div>
}

function AdminOverview({catalog, navigate}) {
  const [term, setTerm] = useState('')
  const terms = [...new Set(catalog.classes.map(row => row.term))].sort().reverse()
  const currentTerm = terms.includes(term) ? term : terms[0]
  const [metrics,setMetrics]=useState(null)
  const [metricError,setMetricError]=useState('')
  const [ringValue,setRingValue]=useState(0)
  const attendanceRate=metrics?.total?Math.round(metrics.attended*100/metrics.total):0
  useEffect(()=>{
    const frame=requestAnimationFrame(()=>setRingValue(attendanceRate))
    return ()=>cancelAnimationFrame(frame)
  },[attendanceRate])
  useEffect(()=>{
    let active=true
    if (currentTerm) api('/admin/attendance?term='+encodeURIComponent(currentTerm))
      .then(value=>{if(active){setMetrics(value);setMetricError('')}})
      .catch(err=>{if(active)setMetricError(err.message)})
    return ()=>{active=false}
  },[currentTerm])
  const classes = catalog.classes.filter(row => row.term === currentTerm)
  const distribution = Object.entries(roles).map(([role, label]) =>
    ({role, label, count:catalog.users.filter(user => user.role === role).length}))
  const stats = [
    ['classes', 'Lớp học phần', catalog.classes.length],
    ['subjects', 'Môn học', catalog.subjects.length],
    ['users', 'Tài khoản', catalog.users.length],
  ]

  return <>
    <section className="dashboard-stats" aria-label="Số liệu danh mục">
      {stats.map(([key, label, total]) =>
        <button type="button" key={key} onClick={() => navigate(key)}>
          <span>{label}</span><strong>{total}</strong>
        </button>
      )}
    </section>
    {metricError && <p role="alert" className="error">{metricError}</p>}
    {!metrics && !metricError && <div className="skeleton skeleton-dashboard" role="status" aria-label="Đang tải số liệu chuyên cần">
      <div className="skeleton-line" />
      <div className="skeleton-line" style={{width:'54%'}} />
      <div className="skeleton-row" />
      <div className="skeleton-row" />
    </div>}
    {metrics && <div className="attendance-overview">
      <section className="attendance-visual">
        <div className="section-head"><div><h2>Nhịp học tập</h2><p>Chuyên cần học kỳ {currentTerm}</p></div><CalendarDays aria-hidden="true" /></div>
        <div className="attendance-figure">
          <div className="attendance-ring">
            <svg viewBox="0 0 120 120" aria-hidden="true" focusable="false">
              <circle className="ring-track" cx="60" cy="60" r="52" pathLength="100" />
              <circle className="ring-value" cx="60" cy="60" r="52" pathLength="100"
                style={{strokeDasharray:'100', strokeDashoffset:100-ringValue}} />
            </svg>
            <div><strong>{metrics.total?attendanceRate+'%':'–'}</strong><span>Tỷ lệ tham dự</span></div>
          </div>
          <dl><div><dt><i className="legend-dot attended" />Lượt tham dự</dt><dd>{metrics.attended}</dd></div>
            <div><dt><i className="legend-dot" />Lượt chưa tham dự</dt><dd>{Math.max(0,metrics.total-metrics.attended)}</dd></div></dl>
        </div>
        <p className="chart-note">Tổng hợp các buổi đã đóng, loại trừ vắng có phép</p>
      </section>
      <section className="report attention-panel">
      <div className="section-head"><div><h2>Cần chú ý</h2><p>Sinh viên dưới ngưỡng chuyên cần</p></div>
        <span className={'status-badge ' + (metrics.alertCount === 0 ? 'positive' : 'warning')}>{metrics.alertCount} trường hợp</span></div>
      {metrics.alerts.length===0 && <div className="empty-state">
        <CheckCircle2 aria-hidden="true" />
        <h3>Chưa có cảnh báo trong học kỳ này</h3>
        <p>Không có sinh viên nào dưới ngưỡng chuyên cần của lớp đang theo dõi.</p>
      </div>}
      {metrics.alerts.length>0 && <DataTable pageSize={5} label="Sinh viên cần chú ý" numeric={[2,3]} headings={['Sinh viên','Lớp','Tham dự','Ngưỡng']}>
        {metrics.alerts.map(row=><tr key={row.code+row.classCode}><td>{row.name}</td><td>{row.classCode}</td>
          <td>{row.attended}/{row.total}</td><td>{row.threshold}%</td></tr>)}
      </DataTable>}
    </section></div>}
    <div className="dashboard-grid">
      <section className="report">
        <div className="section-head"><div><h2>Lớp học phần</h2><p>Phân công giảng dạy và học kỳ</p></div>
          {terms.length > 0 && <select aria-label="Học kỳ" value={currentTerm} onChange={event => setTerm(event.target.value)}>
            {terms.map(value => <option key={value} value={value}>{value}</option>)}
          </select>}
        </div>
        {classes.length === 0 ? <div className="empty-state">
          <BookOpen aria-hidden="true" />
          <h3>Chưa có lớp học phần</h3>
          <p>Học kỳ {currentTerm} chưa có lớp nào được phân công giảng dạy.</p>
          <button className="primary" type="button" onClick={() => navigate('classes', true)}><Plus aria-hidden="true" />Tạo lớp học phần</button>
        </div> :
          <DataTable headings={['Mã lớp', 'Môn học', 'Giảng viên']}>
            {classes.slice(0, 6).map(row => <tr key={row.id}>
              <td>{row.code}</td><td>{row.subject}</td><td>{row.teacher}</td>
            </tr>)}
          </DataTable>}
        <div className="dashboard-footer"><span>{classes.length} lớp{classes.length > 6 && ' · Hiển thị 6 lớp'}</span>
          <button type="button" onClick={() => navigate('classes')}>Quản lý lớp<ArrowUpRight aria-hidden="true" /></button>
        </div>
      </section>
      <section className="role-summary" aria-labelledby="role-title">
        <h2 id="role-title">Phân bổ tài khoản</h2><p>Số tài khoản theo vai trò trong hệ thống</p>
        <dl>{distribution.map(({role, label, count}) => <div key={role}>
          <dt>{label}</dt><dd>{count}</dd>
          <span className="role-track" role="presentation" aria-hidden="true">
            <span style={{transform:'scaleX('+(catalog.users.length ? count / catalog.users.length : 0)+')'}} />
          </span>
        </div>)}</dl>
      </section>
    </div>
  </>
}

export function EnrollmentPanel({refreshClasses}) {
  const state = useList('/enrollment')
  const notify = useToast()
  const [withdrawing, setWithdrawing] = useState(null)

  async function toggle(row) {
    await state.change('/enrollment/' + row.id, row.enrolled ? 'DELETE' : 'POST', undefined,
      (row.enrolled ? 'Đã hủy đăng ký ' : 'Đã đăng ký ') + row.code)
    await refreshClasses()
  }

  return <div className="management">
    <header className="course-header"><h1>Đăng ký lớp</h1></header>
    <section className="report">
      <div className="section-head"><h2>Danh sách lớp học phần</h2></div>
      {withdrawing && <Confirm title="Hủy đăng ký lớp" description={'Rời lớp “' + withdrawing.code + '”?'}
        confirmLabel="Hủy đăng ký" onClose={() => setWithdrawing(null)} onConfirm={() => toggle(withdrawing)} />}
      <Feedback state={state} />
      {state.items?.length === 0 && <p className="empty">Chưa mở lớp học phần</p>}
      {state.items?.length > 0 && <DataTable label="Danh sách lớp học phần" numeric={[4,5]} headings={['Mã lớp', 'Môn học', 'Giảng viên', 'Học kỳ', 'Tín chỉ', 'Sĩ số', 'Tình trạng', 'Thao tác']}>
        {state.items.map(row => <tr key={row.id}>
          <td>{row.code}</td><td>{row.subject}</td><td>{row.teacher}</td><td>{row.term}</td><td>{row.credits}</td><td>{row.enrollment}/{row.capacity}</td>
          <td>{row.enrolled?'Đã đăng ký':row.studentActive===0?'Hồ sơ chưa hoạt động':!row.registrationOpen?'Đóng đăng ký':row.sameSubject?'Đã chọn lớp cùng môn':row.missingPrerequisite?'Chưa đạt môn tiên quyết':row.scheduleConflict?'Trùng lịch':row.enrollment>=row.capacity?'Đã đủ sĩ số':'Đang mở'}</td>
          <td><button className={row.enrolled ? '' : 'primary'} disabled={state.busy || !row.registrationOpen || (!row.enrolled && (row.studentActive===0 || Boolean(row.scheduleConflict) || Boolean(row.sameSubject) || Boolean(row.missingPrerequisite) || row.enrollment>=row.capacity))} onClick={() => {
            if (row.enrolled) setWithdrawing(row)
            else toggle(row).catch(err => notify(err.message, 'error'))
          }}>
            {row.enrolled ? 'Hủy đăng ký' : 'Đăng ký'}</button></td>
        </tr>)}
      </DataTable>}
    </section>
  </div>
}

export function GradesPanel({classId, canEdit, canReopen=false, onStatusChange}) {
  const state = useList('/classes/' + classId + '/grades')
  const [editor, setEditor] = useState(null)
  const notify = useToast()
  const [finalizing,setFinalizing] = useState(false)
  const [reopening,setReopening] = useState(false)
  const [history,setHistory]=useState([])
  const [historyError,setHistoryError]=useState('')
  const finalized=Boolean(state.items?.[0]?.finalized)
  async function changeStatus(path,body,message) {
    await state.change(path,'POST',body,message)
    try { await onStatusChange?.() } catch(err){notify('Đã lưu trạng thái nhưng chưa tải lại được lớp. '+err.message,'error')}
  }
  useEffect(()=>{
    if(!canEdit && !canReopen)return
    let active=true
    api('/classes/'+classId+'/grade-history').then(rows=>{if(active){setHistory(rows);setHistoryError('')}})
      .catch(err=>{if(active)setHistoryError(err.message)})
    return ()=>{active=false}
  },[classId,canEdit,canReopen,state.items])
  return <section className="report management">
    <div className="section-head"><h2>Bảng điểm</h2>
      <div className="section-actions">
      {canEdit && state.items?.length>0 && !finalized && <label className="file-action">Nhập điểm Excel<input type="file" accept=".xlsx" disabled={state.busy} onChange={async event=>{
        const file=event.target.files[0]; event.target.value=''
        if(!file)return
        try{
          const body=new FormData();body.append('file',file)
          await state.change('/classes/'+classId+'/grades-import','POST',body,'Đã nhập bảng điểm Excel')
        }catch(err){notify(err.message,'error')}
      }} /></label>}
      {canEdit && state.items?.length>0 && !finalized && <button disabled={state.busy} onClick={()=>setFinalizing(true)}>Chốt bảng điểm</button>}
      {finalized && <span className="status-badge positive">Đã chốt</span>}
      {canReopen && finalized && <button disabled={state.busy} onClick={()=>setReopening(true)}>Mở lại bảng điểm</button>}
      </div>
    </div>
    <p className="detail-note">Điều kiện dự thi theo ngưỡng chuyên cần của học kỳ. Vắng có phép được loại khỏi số buổi tính tỷ lệ.
      Khi chốt, sinh viên không đủ điều kiện nhận tổng kết 0 / F; các sinh viên còn lại phải có đủ điểm thành phần.</p>
    {finalizing && <Confirm title="Chốt bảng điểm" description="Khóa điểm và chuyên cần của lớp. Sinh viên dưới ngưỡng chuyên cần sẽ bị cấm thi và nhận 0 / F. Kiểm tra kỹ trước khi chốt." confirmLabel="Chốt điểm"
      onClose={()=>setFinalizing(false)} onConfirm={()=>changeStatus('/classes/'+classId+'/finalize',undefined,'Đã chốt bảng điểm')} />}
    {reopening && <Editor title="Mở lại bảng điểm" fields={[{name:'reason',label:'Lý do mở lại',maxLength:500}]}
      onCancel={()=>setReopening(false)} onSave={async body=>{
        await changeStatus('/admin/classes/'+classId+'/reopen',body,'Đã mở lại bảng điểm; giảng viên cần chốt lại sau khi điều chỉnh')
        setReopening(false)
      }} />}
    {editor && <Editor title={'Sửa điểm của ' + editor.name} key={editor.id} record={editor} fields={[
      {name:'coursework', label:'Điểm quá trình', type:'number', min:0, max:10, step:'0.01', optional:true},
      {name:'exam', label:'Điểm thi', type:'number', min:0, max:10, step:'0.01', optional:true,disabled:!editor.eligible,
        hint:!editor.eligible?'Chưa đủ chuyên cần. Chỉ lưu điểm quá trình.':undefined},
    ]} onCancel={() => setEditor(null)} onSave={async body => {
      await state.change('/registrations/' + editor.id + '/grades', 'PUT', {...body,exam:editor.eligible?body.exam:null}, 'Đã lưu điểm của ' + editor.name)
      setEditor(null)
    }} />}
    <Feedback state={state} />
    {state.items?.length === 0 && <p className="empty">Chưa có dữ liệu điểm</p>}
    {state.items?.length > 0 && <DataTable label="Bảng điểm lớp" numeric={[3,4,5]} headings={['Mã SV', 'Họ tên', 'Dự thi', 'Quá trình', 'Điểm thi', 'Tổng kết', 'Điểm chữ', ...(canEdit ? ['Thao tác'] : [])]}>
      {state.items.map(row => <tr key={row.id}>
        <td>{row.code}</td><td>{row.name}</td><td><span className={'status-badge '+(row.eligible?'positive':'warning')}>{row.eligible?'Đủ điều kiện':row.finalized?'Cấm thi':'Chưa đủ chuyên cần'}</span></td>
        <td>{row.coursework ?? '–'}</td><td>{row.exam ?? '–'}</td><td>{row.total ?? '–'}</td><td>{row.letter ?? '–'}</td>
        {canEdit && <td><button disabled={state.busy || Boolean(row.finalized)} onClick={() => setEditor(row)} aria-label={'Sửa điểm ' + row.code}>Sửa điểm</button></td>}
      </tr>)}
    </DataTable>}
    {(canEdit || canReopen) && <details className="grade-history"><summary>Lịch sử chốt và mở lại bảng điểm ({history.length})</summary>
      {historyError && <p role="alert" className="error">{historyError}</p>}
      {history.length===0?<p className="empty">Chưa có lần chốt điểm</p>:<ol>{history.map(row=><li key={row.id}>
        <strong>{row.action==='CHOT_DIEM'?'Chốt bảng điểm':'Mở lại bảng điểm'} · {row.actor}</strong>
        <time dateTime={new Date(row.time).toISOString()}>{new Date(row.time).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'})}</time><p>{row.reason}</p>
      </li>)}</ol>}
    </details>}
  </section>
}

export function AttendancePanel({sessionId, onSaved}) {
  const path = '/session-management/' + sessionId + '/attendance'
  const state = useList(path)
  const [editor, setEditor] = useState(null)
  const notify = useToast()
  return <section className="report management">
    <div className="section-head"><h2>Kết quả điểm danh</h2></div>
    {editor && <Editor title="Chỉnh điểm danh" key={editor.id} record={editor} fields={[
      {name:'status', label:'Điểm danh của ' + editor.name, options:options(marks)},
      {name:'note', label:'Ghi chú', optional:true, maxLength:255},
    ]} onCancel={() => setEditor(null)} onSave={async body => {
      await state.change(path + '/' + editor.id, 'PUT', body, 'Đã lưu kết quả của ' + editor.name)
      setEditor(null)
      await onSaved()
    }} />}
    <Feedback state={state} />
    {state.items?.length === 0 && <p className="empty">Chưa có sinh viên đăng ký</p>}
    {state.items?.length > 0 && <DataTable label="Kết quả điểm danh buổi học" headings={['Mã SV', 'Họ tên', 'Trạng thái', 'Ghi chú', 'Thao tác']}>
      {state.items.map(row => <tr key={row.id}>
        <td>{row.code}</td><td>{row.name}</td><td>{marks[row.status] ?? 'Chưa ghi nhận'}</td><td>{row.note ?? '–'}{row.requestStatus && <div><small>{{CHO_DUYET:'Chờ duyệt',DA_DUYET:'Đã duyệt',TU_CHOI:'Từ chối'}[row.requestStatus]}</small>
          <p>{row.reason}</p>{row.evidence && <a href={row.evidence} target="_blank" rel="noreferrer">Minh chứng</a>}
          {row.requestStatus==='CHO_DUYET' && [true,false].map(approve=><button key={String(approve)} disabled={state.busy || Boolean(row.finalized)} onClick={async()=>{
            try {await state.change('/leave/'+row.requestId+'/decision','PUT',{approve},approve?'Đã duyệt đơn':'Đã từ chối đơn');await onSaved()}
            catch(err){notify(err.message,'error')}
          }}>{approve?'Duyệt đơn':'Từ chối'}</button>)}</div>}</td>
        <td><button disabled={state.busy || Boolean(row.finalized)} onClick={() => setEditor(row)} aria-label={'Chỉnh điểm danh ' + row.code}>Chỉnh điểm danh</button></td>
      </tr>)}
    </DataTable>}
  </section>
}