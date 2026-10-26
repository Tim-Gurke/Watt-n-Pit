const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),http=require('node:http');
(async()=>{
 const root=path.resolve(__dirname,'../app/src/main/assets'),server=http.createServer((q,r)=>{const file=path.join(root,new URL(q.url,'http://localhost').pathname==='/'?'index.html':new URL(q.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){r.writeHead(404);r.end();return;}r.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');r.end(fs.readFileSync(file));});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.WATTPIT_CHROMIUM?{executablePath:process.env.WATTPIT_CHROMIUM}:{})});
 try{for(const width of [360,832])for(const theme of ['dark','light']){
 const page=await browser.newPage({viewport:{width,height:width===360?800:390}}),errors=[];page.on('pageerror',e=>errors.push(e.message));await page.goto(`http://127.0.0.1:${server.address().port}`);
 await page.evaluate(theme=>{localStorage.clear();localStorage.setItem('wattpit-roller',JSON.stringify({mac:'AA:BB:CC:DD:EE:FF',name:'Testroller'}));if(theme==='light')document.getElementById('themeToggle').click();},theme);
 await page.evaluate(()=>poll());await page.locator('#rollerPanel').waitFor({state:'visible'});assert.equal(await page.locator('#rollerSession').isDisabled(),true);
 await page.locator('#rollerConfigure').click();await page.locator('#roller-capacityAh').fill('26');await page.locator('#roller-rideKm').fill('12');await page.locator('#roller-planCharge').check();await page.locator('#rollerSave').click();assert.equal(await page.locator('#modal').isVisible(),false);
 await page.evaluate(()=>{const state={fresh:true,status:'BMS verbunden',profile:{...WattRollerCore.defaults,mac:'AA:BB:CC:DD:EE:FF'},live:{soc:70,power:96,current:1.89,voltage:50.78,at:Date.now()},estimatedInputWatts:138,session:{active:false,batteryWh:10,inputWh:14.4,observedMillis:20000,gapMillis:4000,started:Date.now()-24000},journal:[]};roller.a.native={rollerSnapshot:()=>JSON.stringify(state),rollerSession:start=>{state.session.active=start;return '';}};roller.render();});
 assert.equal(await page.locator('#rollerSoc').textContent(),'70 %');assert.equal(await page.locator('#rollerPower').textContent(),'96 W');assert.equal(await page.locator('#rollerSession').isEnabled(),true);assert.match(await page.locator('#rollerLiveNote').textContent(),/138 W/);
 await page.locator('#rollerSession').click();await page.locator('#rollerConfirmSession').click();assert.match(await page.locator('#rollerSession').textContent(),/beenden/);await page.locator('#rollerSession').click();assert.match(await page.locator('#rollerSession').textContent(),/starten/);
 assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false);assert.deepEqual(errors,[]);
 if(process.env.WATTPIT_SCREENSHOT_DIR){fs.mkdirSync(process.env.WATTPIT_SCREENSHOT_DIR,{recursive:true});await page.locator('#rollerPanel').screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,`roller-${width}-${theme}.png`)});}
 await page.close();}
 console.log('PASS roller UI: settings, BMS values, session controls, 360/832px, dark/light');
 }finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exit(1);});
