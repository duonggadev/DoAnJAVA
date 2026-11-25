import {createContext, useCallback, useContext, useEffect, useId, useLayoutEffect, useRef, useState} from 'react'
import {Check, CircleAlert, X} from 'lucide-react'
import {useEntrance, motionTokens} from './Motion'
import gsap from 'gsap'

const ToastContext = createContext(null)
const CloseDialogContext = createContext(null)

export function DialogDismiss({children='Hủy', ...props}) {
  const close=useContext(CloseDialogContext)
  return <button {...props} type="button" onClick={close}>{children}</button>
}

export function ToastProvider({children}) {
  const [toasts, setToasts] = useState([])
  const sequence = useRef(0)
  const dismiss = useCallback(id => setToasts(list => list.filter(toast => toast.id !== id)), [])
  const notify = useCallback((message, tone = 'success') => {
    const id = ++sequence.current
    setToasts(list => [...list.slice(-2), {id, message, tone}])
  }, [])

  return <ToastContext.Provider value={notify}>
    {children}
    <div className="toast-stack" aria-label="Thông báo" aria-live="polite">
      {toasts.map(toast => <Toast key={toast.id} toast={toast} dismiss={dismiss} />)}
    </div>
  </ToastContext.Provider>
}

function Toast({toast, dismiss}) {
  const scope=useEntrance()
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  useEffect(() => {
    if (toast.tone === 'error' || hovered || focused) return
    const timer = setTimeout(() => dismiss(toast.id), 5500)
    return () => clearTimeout(timer)
  }, [dismiss, toast, hovered, focused])
  const Icon = toast.tone === 'error' ? CircleAlert : Check
  return <div ref={scope} className={'toast ' + toast.tone}
    onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}
    onFocus={() => setFocused(true)} onBlur={() => setFocused(false)}>
    <Icon aria-hidden="true" />
    <p role={toast.tone === 'error' ? 'alert' : undefined}>{toast.message}</p>
    <button type="button" aria-label="Đóng thông báo" onClick={() => dismiss(toast.id)}><X aria-hidden="true" /></button>
  </div>
}

export function useToast() {
  return useContext(ToastContext)
}

export function Modal({title, children, onClose, busy = false}) {
  const dialog = useRef(null)
  const heading = useId()
  const closeTween=useRef(null)
  const onCloseRef=useRef(onClose)
  onCloseRef.current=onClose
  const busyRef=useRef(busy)
  busyRef.current=busy
  const closing=useRef(false)

  const requestClose=useCallback(()=>{
    if(busyRef.current || closing.current)return
    closing.current=true
    const element=dialog.current
    gsap.killTweensOf(element)
    if(window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      onCloseRef.current()
      return
    }
    closeTween.current=gsap.to(element,{
      opacity:0,duration:motionTokens.exit,ease:motionTokens.ease,overwrite:'auto',
      onComplete:()=>onCloseRef.current(),
    })
  },[])
  useLayoutEffect(() => {
    const element = dialog.current
    const trigger = document.activeElement
    element.showModal()
    const previousOverflow=document.body.style.overflow
    document.body.style.overflow='hidden'
    const context=gsap.context(()=>{
      if(!window.matchMedia('(prefers-reduced-motion: reduce)').matches){
        gsap.fromTo(element,{opacity:0,y:8,scale:.98},{opacity:1,y:0,scale:1,duration:motionTokens.ui,ease:motionTokens.ease,clearProps:'transform,opacity'})
      }
    },element)
    const target = element.querySelector('[data-initial-focus]') ?? element.querySelector('input, select, textarea')
    target?.focus()
    return () => {
      closeTween.current?.kill()
      context.revert()
      element.close()
      document.body.style.overflow=previousOverflow
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus()
    }
  }, [])
  return <dialog ref={dialog} className="app-dialog" aria-labelledby={heading} onCancel={event => {
    event.preventDefault()
    requestClose()
  }}>
    <header className="dialog-header">
      <h2 id={heading}>{title}</h2>
      <button type="button" aria-label="Đóng hộp thoại" disabled={busy} onClick={requestClose}><X aria-hidden="true" /></button>
    </header>
    <CloseDialogContext.Provider value={requestClose}>{children}</CloseDialogContext.Provider>
  </dialog>
}

export function Confirm({title, description, confirmLabel, onClose, onConfirm, destructive = false}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  return <Modal title={title} onClose={onClose} busy={busy}>
    <form className="dialog-body" onSubmit={async event => {
      event.preventDefault()
      setBusy(true)
      setError('')
      try {
        await onConfirm()
        onClose()
      } catch (err) {
        setError(err.message)
      } finally {
        setBusy(false)
      }
    }}>
      <p>{description}</p>
      {error && <p role="alert" className="error">{error}</p>}
      <div className="management-actions">
        <DialogDismiss data-initial-focus disabled={busy} />
        <button className={destructive ? 'primary destructive' : 'primary'} disabled={busy}>{busy ? 'Đang xử lý…' : confirmLabel}</button>
      </div>
    </form>
  </Modal>
}