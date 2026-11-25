import test from 'node:test'
import assert from 'node:assert/strict'
import {makeCheckInLink,parseCheckInLink} from '../src/checkInLink.js'

const origin='http://192.168.2.8:5173'
const token='a'.repeat(43)

test('QR carries a web URL and preserves the session across login navigation',()=>{
  const link=makeCheckInLink(origin,17,token)
  assert.equal(new URL(link).origin,origin)
  assert.deepEqual(parseCheckInLink(link,'http://localhost:5173'),{session:17,token})
  assert.equal(new URL(link).search,'')
})

test('same site hash navigation supports PIN fallback without a token',()=>{
  assert.deepEqual(parseCheckInLink('/#/check-in?session=3',origin),{session:3,token:null})
})

test('invalid QR inputs cannot select a session',()=>{
  for(const raw of [
    '/#/check-in?session=0&token='+token,
    '/#/check-in?session=-1&token='+token,
    '/#/check-in?session=abc&token='+token,
    '/#/check-in?session=9999999999999999&token='+token,
    '/#/check-in?session=3&token=bad',
    'SOLOP:T:'+token,
    '/?session=3&token='+token,
  ])assert.equal(parseCheckInLink(raw,origin),null,raw)
})
