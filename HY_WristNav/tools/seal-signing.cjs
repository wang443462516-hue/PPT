const fs=require('node:fs'),c=require('node:crypto');
const [input,pub,output]=process.argv.slice(2);
const key=c.randomBytes(32),iv=c.randomBytes(12);
const cipher=c.createCipheriv('aes-256-gcm',key,iv);
const data=Buffer.concat([cipher.update(fs.readFileSync(input)),cipher.final()]);
const wrapped=c.publicEncrypt({key:fs.readFileSync(pub),oaepHash:'sha256',padding:c.constants.RSA_PKCS1_OAEP_PADDING},key);
fs.writeFileSync(output,JSON.stringify({v:1,algorithm:'RSA-OAEP-SHA256/AES-256-GCM',key:wrapped.toString('base64'),iv:iv.toString('base64'),tag:cipher.getAuthTag().toString('base64'),data:data.toString('base64')}));
