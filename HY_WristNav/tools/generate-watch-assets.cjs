// Original geometric HUD assets, generated reproducibly without font glyphs.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const out = path.join(__dirname, '../watch/src/common/generated');
fs.mkdirSync(out, {recursive: true});
function crc32(bytes) {
  let c = 0xffffffff;
  for (const b of bytes) { c ^= b; for (let i=0;i<8;i++) c = (c>>>1)^((c&1)?0xedb88320:0); }
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const t=Buffer.from(type), n=Buffer.alloc(4), crc=Buffer.alloc(4);
  n.writeUInt32BE(data.length); crc.writeUInt32BE(crc32(Buffer.concat([t,data])));
  return Buffer.concat([n,t,data,crc]);
}
function asset(name, segments, color=[255,255,255]) {
  const size=160, raw=Buffer.alloc(size*(1+size*4));
  for (let y=0;y<size;y++) for (let x=0;x<size;x++) {
    let lit=false;
    for (const [ax,ay,bx,by] of segments) {
      const dx=bx-ax,dy=by-ay;
      const t=Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy||1)));
      if(Math.hypot(x-ax-t*dx,y-ay-t*dy)<8) lit=true;
    }
    const p=y*(1+size*4)+1+x*4;
    if(lit) {raw[p]=color[0];raw[p+1]=color[1];raw[p+2]=color[2];raw[p+3]=255;}
  }
  const h=Buffer.alloc(13);h.writeUInt32BE(size,0);h.writeUInt32BE(size,4);h[8]=8;h[9]=6;
  fs.writeFileSync(path.join(out,name+'.png'),Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',h),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]));
}
const straight=[[80,135,80,25],[80,25,48,58],[80,25,112,58]];
const left=[[112,135,112,65],[112,65,28,65],[28,65,60,33],[28,65,60,97]];
const slight=[[105,135,105,95],[105,95,45,35],[45,35,45,76],[45,35,86,35]];
const mirror=s=>s.map(([a,b,c,d])=>[160-a,b,160-c,d]);
asset('straight',straight);asset('left',left);asset('right',mirror(left));
asset('slight_left',slight);asset('slight_right',mirror(slight));
asset('uturn',[[115,135,115,50],[115,50,100,30],[100,30,60,30],[60,30,40,50],[40,50,40,105],[40,105,18,80],[40,105,63,80]]);
asset('arrive',[[35,85,65,115],[65,115,130,40]]);asset('none',[[55,80,105,80]]);
asset('logo',straight,[103,217,255]);
console.log('Generated original HUD PNG assets');
