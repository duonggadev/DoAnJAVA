import {defineConfig,loadEnv} from 'vite'
import react from '@vitejs/plugin-react'
import {networkInterfaces} from 'node:os'

function lanAddress() {
  const physical=Object.entries(networkInterfaces()).filter(([name])=>
    !/vpn|radmin|vethernet|docker|virtual|tailscale|wsl|loopback/i.test(name))
  physical.sort(([left],[right])=>Number(/wi-?fi|wlan/i.test(right))-Number(/wi-?fi|wlan/i.test(left)))
  for(const [,addresses] of physical){
    const match=addresses?.find(address=>address.family==='IPv4' && !address.internal &&
      /^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)/.test(address.address))
    if(match)return match.address
  }
  return null
}

export default defineConfig(({command,mode})=>{
  const env=loadEnv(mode,process.cwd(),'SOLOP_')
  const address=lanAddress()
  const configured=env.SOLOP_PUBLIC_ORIGIN?.trim()
  const publicOrigin=configured || (command==='serve' && address ? 'http://'+address+':5173':'')
  if(publicOrigin && (!/^https?:$/.test(new URL(publicOrigin).protocol) || new URL(publicOrigin).origin!==publicOrigin)){
    throw new Error('SOLOP_PUBLIC_ORIGIN must be an HTTP or HTTPS origin without a path or trailing slash')
  }
  const proxy={'/api':'http://127.0.0.1:8098'}
  return {
    plugins:[react()],
    define:{__SOLOP_PUBLIC_ORIGIN__:JSON.stringify(publicOrigin)},
    server:{host:'0.0.0.0',port:5173,strictPort:true,proxy},
    preview:{host:'0.0.0.0',proxy},
  }
})
