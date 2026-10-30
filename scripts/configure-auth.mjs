// Management credentials belong only in a shell environment or GitHub Actions secret.
const ref=process.env.SUPABASE_PROJECT_REF;
const token=process.env.SUPABASE_ACCESS_TOKEN;
if(ref!=='rrhuqwaytpeerirbnhlc' || !token) throw new Error('Expected the UniPlan project and SUPABASE_ACCESS_TOKEN');
const url=`https://api.supabase.com/v1/projects/${ref}/config/auth`;
const headers={Authorization:`Bearer ${token}`,'Content-Type':'application/json'};
const read=await fetch(url,{headers,signal:AbortSignal.timeout(20000)});
if(!read.ok) throw new Error('Cannot inspect Auth configuration: '+read.status);
const config=await read.json();
const redirects=new Set((config.uri_allow_list||'').split(',').map(s=>s.trim()).filter(Boolean));
redirects.add('kz.uniplan.app://auth/callback');
const update=await fetch(url,{method:'PATCH',headers,body:JSON.stringify({uri_allow_list:[...redirects].join(',')}),signal:AbortSignal.timeout(20000)});
if(!update.ok) throw new Error('Cannot add Android redirect: '+update.status);
const verify=await fetch(url,{headers,signal:AbortSignal.timeout(20000)});
if(!verify.ok || !(await verify.json()).uri_allow_list.split(',').map(s=>s.trim()).includes('kz.uniplan.app://auth/callback')) throw new Error('Redirect verification failed');
console.log('UniPlan Android callback is allowed; existing redirects and Auth security settings preserved.');
