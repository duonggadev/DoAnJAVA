import {useEffect, useRef, useState} from 'react'
import jsQR from 'jsqr'
import {parseCheckInLink} from './checkInLink'

export function readPin(image, width, height, canvas) {
  if (!width || !height) return null
  const scale = Math.min(1, 1600 / Math.max(width, height))
  canvas.width = Math.max(1, Math.round(width * scale))
  canvas.height = Math.max(1, Math.round(height * scale))
  const context = canvas.getContext('2d', {willReadFrequently: true})
  context.drawImage(image, 0, 0, canvas.width, canvas.height)
  const data = context.getImageData(0, 0, canvas.width, canvas.height)
  const result = jsQR(data.data, data.width, data.height)
  if (parseCheckInLink(result?.data ?? '')?.token) return result.data
  if (/^SOLOP:T:[A-Za-z0-9_-]{43}$/.test(result?.data ?? '')) return 'token:' + result.data.slice(8)
  return /^SOLOP:[0-9]{8}$/.test(result?.data ?? '') ? result.data.slice(6) : null
}

export default function QrCamera({onRead, onClose}) {
  const video = useRef(null)
  const [error, setError] = useState('')
  useEffect(() => {
    const player = video.current
    const canvas = document.createElement('canvas')
    let stream
    let interval
    let stopped = false
    const stop = () => {
      stopped = true
      clearInterval(interval)
      stream?.getTracks().forEach(track => track.stop())
      player.srcObject = null
    }
    async function start() {
      try {
        if (!navigator.mediaDevices?.getUserMedia) throw new Error('unsupported')
        stream = await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'}}, audio:false})
        if (stopped) {stop(); return}
        player.srcObject = stream
        await player.play()
        if (stopped) return
        interval = setInterval(() => {
          const pin = readPin(player, player.videoWidth, player.videoHeight, canvas)
          if (pin) {
            stop()
            onRead(pin)
          }
        }, 250)
      } catch {
        if (!stopped) {
          stop()
          setError(!window.isSecureContext
            ? 'Trình duyệt cần HTTPS để bật camera trong trang web. Bạn có thể dùng Camera/Zalo để quét trực tiếp màn hình giảng viên hoặc chọn Đọc QR từ ảnh.'
            : 'Không mở được camera. Kiểm tra quyền truy cập hoặc chọn Đọc QR từ ảnh.')
        }
      }
    }
    start()
    return stop
  }, [onRead])

  return <section className="camera-panel" aria-label="Camera quét mã QR">
    <video ref={video} muted playsInline hidden={Boolean(error)} />
    {error && <p role="alert" className="error">{error}</p>}
    <button type="button" onClick={onClose}>Tắt camera</button>
  </section>
}