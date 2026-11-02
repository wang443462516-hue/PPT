// Restore into a private directory, never under a committed path.
const fs=require('node:fs'),c=require('node:crypto');
const [input,privateKey,output]=process.argv.slice(2);
const e=JSON.parse(fs.readFileSync(input,'utf8'));
if(e.v!==1||e.algorithm!=='RSA-OAEP-SHA256/AES-256-GCM')throw Error('Unsupported envelope');
const key=c.privateDecrypt({key:fs.readFileSync(privateKey),oaepHash:'sha256',padding:c.constants.RSA_PKCS1_OAEP_PADDING},Buffer.from(e.key,'base64'));
const cipher=c.createDecipheriv('aes-256-gcm',key,Buffer.from(e.iv,'base64'));
cipher.setAuthTag(Buffer.from(e.tag,'base64'));
const plaintext=Buffer.concat([cipher.update(Buffer.from(e.data,'base64')),cipher.final()]);
fs.writeFileSync(output,plaintext,{flag:'wx',mode:0o600});
console.log('Signing backup restored into the requested private archive');
