export function parseCheckInLink(raw,base=window.location.origin) {
  try {
    const url=new URL(raw,base)
    const hash=url.hash.slice(1)
    const [route,query='']=hash.split('?')
    if(route!=='/check-in')return null
    const values=new URLSearchParams(query)
    const session=values.get('session')
    const token=values.get('token')
    if(!/^[1-9][0-9]{0,14}$/.test(session??''))return null
    if(token!==null && !/^[A-Za-z0-9_-]{43}$/.test(token))return null
    return {session:Number(session),token}
  } catch {return null}
}

export function makeCheckInLink(origin,session,token) {
  const link=new URL(origin)
  link.pathname='/'
  link.search=''
  link.hash='/check-in?'+new URLSearchParams({session:String(session),token})
  return link.toString()
}
