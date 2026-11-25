export async function api(path, options = {}) {
  const {body, method = 'GET', signal} = options
  const headers = new Headers()
  if (method !== 'GET') {
    const csrf = await api('/csrf', {signal})
    headers.set(csrf.header, csrf.token)
  }
  if (body && !(body instanceof URLSearchParams) && !(body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch('/api' + path, {
    method, headers, signal, body: body instanceof URLSearchParams || body instanceof FormData ? body : JSON.stringify(body),
  })
  if (!response.ok) {
    if (response.status === 401 && path !== '/login' && path !== '/me') {
      window.dispatchEvent(new Event('solop:session-expired'))
    }
    const error = await response.json().catch(() => ({}))
    throw Object.assign(new Error(error.message || ({
      401: path === '/login' ? 'Kiểm tra mã tài khoản và mật khẩu.' : 'Phiên đăng nhập đã hết hiệu lực.',
      403: 'Thao tác này không được phép. Hãy tải lại trang và thử lại.',
      400: 'Dữ liệu gửi lên chưa hợp lệ.',
      429: 'Bạn thao tác quá nhanh. Hãy chờ một phút rồi thử lại.',
    }[response.status] ?? 'Có lỗi khi kết nối với hệ thống.')), {status: response.status})
  }
  if (response.status !== 204 && response.headers.get('content-type')?.includes('application/json')) {
    return response.json()
  }
}
