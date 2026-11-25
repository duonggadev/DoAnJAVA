import {useRef} from 'react'
import gsap from 'gsap'
import CustomEase from 'gsap/CustomEase'
import {useGSAP} from '@gsap/react'

gsap.registerPlugin(useGSAP, CustomEase)
CustomEase.create('solop', 'M0,0 C0.22,1 0.36,1 1,1')

export const motionTokens = {micro:0.12, ui:0.24, exit:0.16, scene:0.48, ease:'solop', stagger:0.06}

export function useEntrance(dependencies = []) {
  const scope = useRef(null)
  useGSAP(() => {
    const media = gsap.matchMedia()
    media.add('(prefers-reduced-motion: no-preference)', () => {
      gsap.fromTo(scope.current, {opacity:0, y:8}, {
        opacity:1, y:0, duration:motionTokens.ui, ease:motionTokens.ease,
        overwrite:'auto', clearProps:'transform,opacity',
      })
    })
    return () => media.revert()
  }, {scope, dependencies, revertOnUpdate:true})
  return scope
}

export function PageMotion({children, identity}) {
  const scope=useRef(null)
  useGSAP(()=>{
    const media=gsap.matchMedia()
    media.add('(prefers-reduced-motion: no-preference)',()=>{
      gsap.fromTo(scope.current,{opacity:0.86,y:8},{
        opacity:1,y:0,duration:motionTokens.ui,ease:motionTokens.ease,clearProps:'transform,opacity',overwrite:'auto',
      })
    })
    return ()=>media.revert()
  },{scope,dependencies:[identity],revertOnUpdate:true})
  return <div ref={scope} className="page-motion">{children}</div>
}
