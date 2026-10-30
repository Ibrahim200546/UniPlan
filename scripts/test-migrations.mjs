import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';

// Runs actual PostgreSQL policies/RPCs locally; does not claim hosted Auth/API coverage.
test('migration enforces owner isolation, grants, payload limits and stale-write rejection', async () => {
  const db = new PGlite();
  try {
    await db.exec(`create role anon; create role authenticated;
      create schema auth; create table auth.users(id uuid primary key);
      create function auth.uid() returns uuid language sql stable as
      $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
      grant usage on schema auth,public to anon,authenticated;
      grant execute on function auth.uid() to anon,authenticated;`);
    for (const file of (await readdir(new URL('../supabase/migrations/', import.meta.url))).sort()) {
      if (file.endsWith('.sql')) await db.exec(await readFile(new URL('../supabase/migrations/'+file,import.meta.url),'utf8'));
    }
    const a='11111111-1111-4111-8111-111111111111', b='22222222-2222-4222-8222-222222222222';
    await db.query('insert into auth.users values ($1),($2)',[a,b]);
    const snapshot={schemaVersion:1,profile:{displayName:'Student',studyGroup:''},subjects:[],tasks:[],lessons:[],notes:[]};
    const asUser=async uid=>{ await db.exec('reset role; set role authenticated'); await db.query("select set_config('request.jwt.claim.sub',$1,false)",[uid]); };
    const save=async (revision,data=snapshot)=>(await db.query('select public.save_uniplan_state($1,$2::jsonb) as result',[revision,JSON.stringify(data)])).rows[0].result;
    await asUser(a);
    assert.deepEqual(await save(0),{ok:true,revision:1});
    assert.deepEqual(await save(0),{ok:false,conflict:true,revision:1});
    assert.deepEqual(await save(1),{ok:true,revision:2});
    assert.deepEqual(await save(1),{ok:false,conflict:true,revision:2});
    await assert.rejects(()=>save(-1));
    await assert.rejects(()=>save(2,{}));
    await assert.rejects(()=>save(2,{...snapshot,schemaVersion:'1'}));
    await assert.rejects(()=>save(2,{...snapshot,notes:['x'.repeat(2100000)]}));
    await assert.rejects(()=>db.query('update public.uniplan_state set user_id=$1',[b]));
    await asUser(b);
    assert.equal((await db.query('select * from public.uniplan_state')).rows.length,0);
    assert.equal((await db.query('update public.uniplan_state set revision=99 returning *')).rows.length,0);
    await assert.rejects(()=>db.query('insert into public.uniplan_state(user_id,payload) values ($1,$2)',[a,JSON.stringify(snapshot)]));
    await assert.rejects(()=>db.query('insert into public.uniplan_state(user_id,payload) values ($1,$2)',[b,'{}']));
    assert.deepEqual(await save(0),{ok:true,revision:1});
    await asUser(a);
    assert.equal((await db.query('select revision from public.uniplan_state')).rows[0].revision,2);
    await db.exec('reset role; set role anon');
    await assert.rejects(()=>db.query('select * from public.uniplan_state'));
    await assert.rejects(()=>save(0));
    await asUser('');
    assert.equal((await db.query('select * from public.uniplan_state')).rows.length,0);
    await assert.rejects(()=>save(0));
  } finally { await db.close(); }
});
