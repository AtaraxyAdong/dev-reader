const assert=require('node:assert/strict');
const {before,after,test}=require('node:test');
const fs=require('node:fs'),path=require('node:path');
const {chromium}=require('playwright');
const resources=path.join(__dirname,'../../main/resources/reader');
const origin='https://reader-fixture.invalid';let browser;
const svg=(w,h)=>`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}"><rect width="100%" height="100%" fill="#9ec9e8"/><text x="30" y="70" font-size="48">Image preview fixture</text></svg>`;
function template(file,script,content='',token='one') {
 return fs.readFileSync(path.join(resources,file),'utf8').replaceAll('{{NONCE}}','fixture-nonce').replaceAll('{{TOKEN}}',token).replace('{{CSS}}',fs.readFileSync(path.join(resources,'reader.css'),'utf8')).replace('{{JS}}',script).replace('{{CONTENT}}',content);
}
function readerHtml() {
 const content=`<article><h1>Question</h1><p><a id="plain" href="https://example.invalid/article">Normal link</a></p><p><img id="large" src="${origin}/large.svg" alt="Large diagram"></p><p><a href="https://example.invalid/image-link"><img id="linked" src="${origin}/tall.svg" alt="Linked diagram"></a></p><p><img id="broken" src="${origin}/broken.png"></p></article>`;
 const script=fs.readFileSync(path.join(resources,'reader.js'),'utf8')+`\nwindow.__messages=[];window.readerBridge=m=>window.__messages.push(JSON.parse(m));window.readerStart({token:'one',theme:{},imageFailure:'图片加载失败，点击查看原图',sourceUri:'https://www.mianshiya.com/question/12'});`;
 return template('reader.html',script,content);
}
function previewHtml(image) {
 const script=fs.readFileSync(path.join(resources,'image-preview.js'),'utf8')+`\nwindow.__closed=0;window.readerPreviewClosed=()=>window.__closed++;window.readerShowImagePreview(${JSON.stringify(origin+'/'+image)},'Diagram');`;
 return template('image-preview.html',script);
}
before(async()=>{browser=await chromium.launch({executablePath:process.env.READER_TEST_BROWSER||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});});
after(async()=>{if(browser)await browser.close();});
async function fixture(kind,fn,{image='large.svg',viewport={width:1200,height:800}}={}) {
 const page=await browser.newPage({viewport});page.setDefaultTimeout(3000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/*',async route=>{
  const url=new URL(route.request().url());if(url.origin!==origin){await route.abort();return;}
  if(url.pathname==='/large.svg'||url.pathname==='/tall.svg')await route.fulfill({contentType:'image/svg+xml',body:url.pathname==='/large.svg'?svg(3000,2000):svg(600,4000)});
  else if(url.pathname==='/broken.png')await route.fulfill({status:404,body:''});
  else await route.fulfill({contentType:'text/html; charset=utf-8',body:url.pathname==='/reader'?readerHtml():previewHtml(image)});
 });
 try {await page.goto(origin+'/'+kind);if(kind==='reader')await page.waitForFunction(()=>window.__messages.some(m=>m.kind==='ready'));else if(image!=='broken.png')await loaded(page);await fn(page);assert.deepEqual(errors,[]);}finally{await page.close();}
}
async function loaded(page){await page.waitForFunction(()=>document.querySelector('#image-preview-image')?.naturalWidth>0&&!document.querySelector('#image-preview-image').hidden);}
async function closed(page){assert.equal(await page.locator('#image-preview').evaluate(el=>el.open),false);assert.equal(await page.locator('#image-preview-image').getAttribute('src'),null);}
async function reopen(page,url=origin+'/large.svg'){await page.evaluate(url=>window.readerShowImagePreview(url,'Diagram'),url);await loaded(page);}

test('native-reader image click requests a separate host window without an inline modal',()=>fixture('reader',async page=>{
 await page.locator('#large').click();const image=await page.evaluate(()=>window.__messages.find(m=>m.kind==='image'));
 assert.equal(image?.url,origin+'/large.svg');assert.equal(image?.alt,'Large diagram');assert.equal(await page.locator('#image-preview').count(),0);
 assert.equal(await page.evaluate(()=>window.__messages.some(m=>m.kind==='link')),false);
},{viewport:{width:280,height:600}}));
test('native-reader linked and keyboard images use the preview bridge while ordinary links stay unchanged',()=>fixture('reader',async page=>{
 await page.locator('#linked').click();assert.equal(await page.evaluate(()=>window.__messages.at(-1).kind),'image');
 assert.equal(await page.evaluate(()=>window.__messages.at(-1).url),origin+'/tall.svg');
 await page.locator('#large').focus();await page.keyboard.press('Enter');await page.keyboard.press('Space');
 assert.equal(await page.evaluate(()=>window.__messages.filter(m=>m.kind==='image').length),3);
 await page.locator('#plain').click();assert.equal(await page.evaluate(()=>window.__messages.filter(m=>m.kind==='link').at(-1).url),'https://example.invalid/article');
}));
test('native-reader failure link stays internal and unsafe URLs never reach the host',()=>fixture('reader',async page=>{
 await page.locator('a.image-failure').click();assert.equal(await page.evaluate(()=>window.__messages.filter(m=>m.kind==='image').at(-1).url),origin+'/broken.png');
 const before=await page.evaluate(()=>window.__messages.length);
 await page.locator('#large').evaluate(img=>{img.src='data:image/png,invalid';img.dispatchEvent(new Event('error'));});
 await page.locator('a.image-failure[href^="data:"]').click();assert.equal(await page.evaluate(()=>window.__messages.length),before);
}));
test('separate viewer fits the larger viewport and keeps the image aspect ratio',()=>fixture('preview',async page=>{
 const size=await page.locator('#image-preview-image').boundingBox();assert.ok(size.width>360);assert.ok(size.width<=1200&&size.height<=800);assert.ok(Math.abs(size.width/size.height-1.5)<.01);
 assert.equal(await page.locator('#image-preview-close').evaluate(el=>el===document.activeElement),true);
 if(process.env.READER_PREVIEW_SCREENSHOTS){fs.mkdirSync(process.env.READER_PREVIEW_SCREENSHOTS,{recursive:true});await page.screenshot({path:path.join(process.env.READER_PREVIEW_SCREENSHOTS,'native-viewer-1200x800.png')});}
}));
test('viewer close button, Esc and mask notify the native host once and clear image resources',()=>fixture('preview',async page=>{
 for(const close of [()=>page.locator('#image-preview-close').click(),()=>page.keyboard.press('Escape'),()=>page.mouse.click(2,2)]){
  const before=await page.evaluate(()=>window.__closed);await close();await closed(page);assert.equal(await page.evaluate(()=>window.__closed),before+1);await reopen(page);
 }
 await page.locator('#image-preview-image').click();assert.equal(await page.locator('#image-preview').evaluate(el=>el.open),true);
}));
test('viewer repeated open and close does not accumulate active images',()=>fixture('preview',async page=>{
 for(let i=0;i<8;i++){await page.locator('#image-preview-close').click();await closed(page);await reopen(page);}
 assert.equal(await page.locator('#image-preview-image').count(),1);assert.equal(await page.evaluate(()=>window.__closed),8);
}));
test('viewer preserves original-size scrolling, zoom, fitting and modal focus when resized',()=>fixture('preview',async page=>{
 await page.locator('#image-preview-actual').click();let box=await page.locator('#image-preview-image').boundingBox();assert.equal(Math.round(box.width),3000);assert.equal(Math.round(box.height),2000);
 assert.equal(await page.locator('#image-preview-viewport').evaluate(el=>el.scrollWidth>el.clientWidth&&el.scrollHeight>el.clientHeight),true);
 await page.locator('#image-preview-in').click();assert.ok((await page.locator('#image-preview-image').boundingBox()).width>3000);await page.locator('#image-preview-out').click();assert.equal(Math.round((await page.locator('#image-preview-image').boundingBox()).width),3000);
 await page.locator('#image-preview-fit').click();await page.setViewportSize({width:280,height:180});await page.waitForTimeout(100);box=await page.locator('#image-preview-image').boundingBox();assert.ok(box.width<=280&&box.height<=180&&box.width>0);
 for(let i=0;i<12;i++){await page.keyboard.press('Tab');assert.equal(await page.evaluate(()=>!!document.activeElement.closest('#image-preview')),true);}
}));
test('viewer failed image remains closable and next valid image resets the error',()=>fixture('preview',async page=>{
 await page.waitForFunction(()=>document.querySelector('#image-preview-status').textContent.includes('失败'));
 await page.keyboard.press('Escape');await closed(page);await reopen(page);assert.equal(await page.locator('#image-preview-status').textContent(),'');
},{image:'broken.png'}));
test('viewer close during pending image response ignores late callbacks and pagehide does not reopen',()=>fixture('preview',async page=>{
 await page.route(origin+'/tall.svg',async route=>{await new Promise(resolve=>setTimeout(resolve,350));try{await route.fulfill({contentType:'image/svg+xml',body:svg(600,4000)});}catch{}});
 await page.evaluate(url=>window.readerShowImagePreview(url,'Delayed'),origin+'/tall.svg');await page.keyboard.press('Escape');await closed(page);await page.waitForTimeout(500);await closed(page);
 assert.equal(await page.evaluate(()=>window.__closed),1);await reopen(page);
 await page.evaluate(()=>window.dispatchEvent(new PageTransitionEvent('pagehide')));await closed(page);assert.equal(await page.evaluate(()=>window.__closed),1);
}));
