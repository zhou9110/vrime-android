const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync('app/src/main/assets/cross-screen.html', 'utf8');
const elements = Object.fromEntries(['code','text','status','connect','disconnect','backspace'].map(id => [id, {
  value:'', selectionStart:0, selectionEnd:0, disabled:false, events:{},
  addEventListener(name, callback){this.events[name]=callback},
  setSelectionRange(start,end){this.selectionStart=start;this.selectionEnd=end}
}]));
let current = {available:true,text:'Quest',selectionStart:5,selectionEnd:5,revision:1,message:'connected'};
let syncs=0, requests=0, nextTick, delayedReply=null, oneWayText="";
const document = {querySelector:s=>elements[s.slice(1)],addEventListener(){},activeElement:null};
const context=vm.createContext({document,AbortSignal,console,setTimeout:f=>{nextTick=f},fetch:async(path,options)=>{
  requests++;
  if(path==='/sync'){
    syncs++;
    const edit=JSON.parse(options.body);
    assert.equal(edit.revision,current.revision);
    if(edit.oneWay){
      oneWayText=edit.backspace?Array.from(oneWayText).slice(0,-1).join(''):oneWayText+edit.text;
    }else current={...current,...edit,revision:current.revision+1};
  }
  if(delayedReply)await delayedReply;
  return {ok:true,status:200,json:async()=>({...current})};
}});
vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1],context);
(async()=>{
  elements.code.value='1234';
  await elements.connect.onclick();
  assert.equal(elements.text.value,'Quest');
  elements.text.value='中文😀';elements.text.selectionStart=4;elements.text.selectionEnd=4;
  elements.text.events.input();await nextTick();
  assert.equal(current.text,'中文😀');assert.equal(syncs,1);
  elements.text.selectionStart=0;elements.text.selectionEnd=2;
  elements.text.events.select();await nextTick();assert.equal(current.selectionEnd,2);
  // A device-side clear and field switch are pulled without another pairing action.
  current={...current,text:'',selectionStart:0,selectionEnd:0,revision:8};
  await nextTick();assert.equal(elements.text.value,'');
  current={...current,text:'新输入框',selectionStart:4,selectionEnd:4,revision:9};
  await nextTick();assert.equal(elements.text.value,'新输入框');
  elements.text.events.compositionstart();elements.text.value='nihao';elements.text.events.input();
  const before=requests;await nextTick();assert.equal(requests,before);
  elements.text.value='你好';elements.text.selectionStart=2;elements.text.selectionEnd=2;
  elements.text.events.compositionend();await nextTick();assert.equal(current.text,'你好');
  elements.text.value='';elements.text.selectionStart=0;elements.text.selectionEnd=0;
  elements.text.events.input();await nextTick();assert.equal(current.text,'');
  elements.disconnect.onclick();
  assert.equal(elements.text.disabled,true);
  const stopped=requests;await nextTick();assert.equal(requests,stopped);
  await elements.connect.onclick();assert.equal(elements.text.disabled,false);
  let release;
  delayedReply=new Promise(resolve=>{release=resolve});
  const pending=nextTick();
  elements.disconnect.onclick();release();await pending;
  assert.equal(elements.text.disabled,true);
  assert.equal(elements.status.textContent,'已断开连接');
  assert.equal(elements.disconnect.disabled,true);
  delayedReply=null;
  current={available:true,readable:false,text:'',selectionStart:0,selectionEnd:0,revision:20,message:'单向输入'};
  await elements.connect.onclick();
  assert.equal(elements.text.disabled,false);assert.equal(elements.backspace.hidden,false);
  elements.text.events.compositionstart();elements.text.value='nihao';elements.text.events.input();
  const beforeOneWay=requests;await nextTick();assert.equal(requests,beforeOneWay);
  elements.text.value='你好';elements.text.events.compositionend();
  assert.equal(elements.text.value,'');await nextTick();assert.equal(oneWayText,'你好');
  elements.text.value='😀';elements.text.events.input();await nextTick();assert.equal(oneWayText,'你好😀');
  elements.backspace.onclick();await nextTick();assert.equal(oneWayText,'你好');
  assert.equal(elements.text.value,'');
  console.log('PASS: one-way input, composition, backspace; realtime typing, selection, device clear/switch, composition, browser clear');
})().catch(error=>{console.error(error);process.exitCode=1});
