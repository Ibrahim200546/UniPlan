import { readFileSync } from 'node:fs';
const config=Object.fromEntries(readFileSync(new URL('../config/supabase.properties',import.meta.url),'utf8').split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).map(l=>{const i=l.indexOf('=');return [l.slice(0,i),l.slice(i+1)];}));
const url=process.env.SUPABASE_URL || config.url;
const key=process.env.SUPABASE_PUBLISHABLE_KEY || config.publishableKey;
if(!/^https:\/\/[a-z0-9.-]+(?::\d+)?\/?$/i.test(url) || !/^sb_publishable_[A-Za-z0-9_-]+$/.test(key)) throw new Error('Invalid public Supabase configuration');
const headers={apikey:key,'Content-Type':'application/json'};
const settings=await fetch(url+'/auth/v1/settings',{headers,signal:AbortSignal.timeout(20000)});
if(!settings.ok) throw new Error('Supabase Auth is unavailable: '+settings.status);
const auth=await settings.json();
if(auth.disable_signup || !auth.external?.email) throw new Error('Email registration is disabled');
for(const [path,method,body] of [
  ['/rest/v1/uniplan_state?select=revision&limit=0','GET',undefined],
  ['/rest/v1/rpc/save_uniplan_state','POST',JSON.stringify({expected_revision:0,data:{}})],
]) {
  const reply=await fetch(url+path,{method,headers,body,signal:AbortSignal.timeout(20000)});
  const data=await reply.json();
  if(![401,403].includes(reply.status) || data.code!=='42501') throw new Error('Expected deployed schema to deny anonymous access: '+path+' ('+reply.status+', '+data.code+')');
}
console.log('Auth reachable; email registration enabled; table and RPC exist and deny anonymous access.');
console.log('This check does not replace authenticated account/device testing.');
