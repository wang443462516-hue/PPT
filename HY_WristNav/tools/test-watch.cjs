const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const ux=fs.readFileSync(require('node:path').join(__dirname,'../watch/src/pages/index/index.ux'),'utf8');
const script=ux.match(/<script>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/mg,'').replace('export default','page =');
const sent=[];
let now=100000, timerCount=0;
const connection={send:({data})=>sent.push(data),getReadyState:({success})=>success({status:1})};
const box={Date:{now:()=>now},console:{log:()=>{}},isFinite,JSON,Math,
  interconnect:{instance:()=>connection},brightness:{setKeepScreenOn:opts=>assert.equal(opts.keepScreenOn,false)},
  vibrator:{vibrate:()=>{}},setInterval:()=>++timerCount,clearInterval:()=>--timerCount};
vm.createContext(box);vm.runInContext(script,box);
const page=box.page;Object.assign(page,page.private);page.onInit();page.onShow();
const snapshot=(seq,patch={})=>({v:1,t:'nav_snapshot',seq,ts:now,live:true,state:'navigating',maneuver:'left',metersToTurn:90,road:'示例路',...patch});
page.receive(JSON.stringify(snapshot(1)));assert.equal(page.distanceText,'90 m');assert.equal(page.liveLabel,'LIVE');
assert(sent.some(x=>x.t==='ack'&&x.seq===1));
now+=31000;page.render();assert.equal(page.statusText,'数据过期');assert.equal(page.distanceText,'--');assert(page.arrowPath.endsWith('none.png'));
page.receive(snapshot(2,{live:false}));assert.equal(page.liveLabel,'DEMO');
connection.onclose();assert.equal(page.statusText,'手机断开');assert.equal(page.distanceText,'--');
page.receive(snapshot(3,{state:'stop'}));assert.equal(page.statusText,'导航已停止');
page.receive(snapshot(2));assert.equal(page.statusText,'导航已停止');
page.receive(snapshot(4,{ts:undefined}));assert.equal(page.lastSeq,3);
page.receive(snapshot(4,{ts:now+9000}));assert.equal(page.statusText,'请同步手机与手表时间');
page.receive(snapshot(5,{state:'no_data'}));assert.equal(page.statusText,'无可用导航数据');
page.onHide();assert.equal(timerCount,0);page.onShow();assert.equal(timerCount,1);page.onDestroy();assert.equal(timerCount,0);
console.log('Watch regression checks passed: handshake, ACK, stale, disconnected, stop ordering, invalid timestamp, DEMO and lifecycle');
