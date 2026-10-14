const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');

(async () => {
  const browser = await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.WATTPIT_CHROMIUM?{executablePath:process.env.WATTPIT_CHROMIUM}:{})});
  let server;
  if (!process.env.WATTPIT_UI_URL) {
    const assets=path.resolve(__dirname,'../app/src/main/assets');
    server=http.createServer((request,response)=>{
      const route=new URL(request.url,'http://localhost').pathname;
      const file=path.join(assets,route==='/'?'index.html':route);
      if(!file.startsWith(assets+path.sep)||!fs.existsSync(file)){response.writeHead(404);response.end();return;}
      response.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');
      response.end(fs.readFileSync(file));
    });
    await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  }
  const base = process.env.WATTPIT_UI_URL || `http://127.0.0.1:${server.address().port}`;
  const output = process.env.WATTPIT_SCREENSHOT_DIR || null;
  if (output) fs.mkdirSync(output,{recursive:true});
  let cases = 0;
  for (const viewport of [{width:360,height:800},{width:832,height:390},{width:1100,height:900}]) {
    for (const theme of ['dark','light']) {
      const page = await browser.newPage({viewport});
      const errors=[];
      page.on('pageerror',e=>errors.push(e.message));
      await page.goto(base);
      if (theme==='light') await page.locator('#themeToggle').click();
      await page.locator('#socValue').waitFor();
      assert.equal(await page.locator('#socValue').textContent(),'—');
      assert.equal(await page.locator('#welcome').isVisible(),true);
      assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false,'horizontal overflow');
      await page.locator('#demoFirst').click();
      assert.equal(await page.locator('#socValue').textContent(),'76,4');
      assert.equal(await page.locator('#demoBar').isVisible(),true);
      assert.equal(await page.locator('#overviewRecord').isDisabled(),true);
      if(output && viewport.width===360) { await page.waitForTimeout(600); await page.screenshot({path:path.join(output,`overview-${theme}.png`)}); }
      await page.locator('[data-page="history"]').click();
      assert.equal(await page.locator('#chartLegend span').count(),2);
      await page.locator('[data-range="week"]').click();
      const windowBefore=await page.locator('#chartWindow').textContent();
      await page.locator('#zoomIn').click();
      await page.waitForTimeout(400);
      assert.notEqual(await page.locator('#chartWindow').textContent(),windowBefore,'zoom does not change time window');
      await page.locator('#openScale').click();
      await page.locator('#scaleMin').fill('-100');await page.locator('#scaleMax').fill('200');
      await page.locator('#applyScale').click();
      assert.equal(await page.locator('#modal').isVisible(),false);
      await page.locator('[data-metric="soc"]').click();
      assert.equal(await page.locator('#chartTitle').textContent(),'Ladezustand');
      assert.equal(await page.locator('#chartLegend span').count(),1);
      await page.locator('#historySource').selectOption('csv');
      assert.equal(await page.locator('#chartLegend span').count(),0);
      await page.locator('#historySource').selectOption('all');
      await page.locator('[data-metric="power"]').click();
      await page.locator('[data-range="today"]').click();
      assert.equal(await page.locator('#rangeButtons .selected').count(),1);
      if(output && viewport.width===360) { await page.waitForTimeout(200); await page.screenshot({path:path.join(output,`history-${theme}.png`)}); }
      await page.locator('[data-page="devices"]').click();
      await page.locator('#addDevice').click();
      assert.equal(await page.locator('#deviceKey').getAttribute('type'),'password');
      await page.locator('#closeModal').click();
      await page.evaluate(()=>window.onImportPreview({headers:['timestamp','Battery Voltage (V)','<script>bad()</script>'],rows:2,map:{timestamp:0,voltage:1},preview:[['2026-10-14T09:00:00Z','13.2','']],name:'<svg onload="bad()">.csv'}));
      assert.equal(await page.locator('#map-voltage').inputValue(),'1');
      assert.equal(await page.locator('#modalBody svg').count(),0,'import preview interprets an untrusted filename as HTML');
      await page.locator('#cancelCsv').click();
      await page.locator('[data-page="settings"]').click();
      await page.locator('#capacitySetting').fill('150');await page.waitForTimeout(2200);
      assert.equal(await page.locator('#capacitySetting').inputValue(),'150','poll discards unsaved input');
      await page.locator('#saveSettings').click();
      await page.locator('[data-page="overview"]').click();
      assert.equal(await page.locator('#remainingAh').textContent(),'114,6 Ah');
      await page.locator('#exitDemo').click();
      assert.equal(await page.locator('#socValue').textContent(),'—');
      assert.equal(errors.length,0,errors.join('\n'));
      await page.close();cases++;
    }
  }
  const local=await browser.newPage({viewport:{width:390,height:844}});
  await local.addInitScript(()=>{
    window.mock={running:true,status:'Bluetooth-Empfang aktiv',devices:[{kind:'shunt',mac:'AA:BB:CC:DD:EE:FF',name:'SmartShunt',error:'',seen:Date.now()}],found:[],readings:[{kind:'shunt',device:'AA:BB:CC:DD:EE:FF',t:Date.now(),soc:76.4,voltage:12.94,current:0,power:0,ttg:null,consumed:23.6}],stats:{count:0},settings:{theme:'dark',capacity:100,sampleSeconds:10}};
    window.WattPit={snapshot:()=>JSON.stringify(window.mock),history:()=>JSON.stringify({points:[]}),settings:()=>'',stop:()=>window.mock.running=false,start:()=>window.mock.running=true};
  });
  await local.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
  assert.equal(await local.locator('#socValue').textContent(),'76,4','local asset page cannot access the bridge');
  assert.equal(await local.locator('#currentValue').textContent(),'0,00 A','zero is displayed as missing');
  await local.evaluate(()=>window.mock.readings[0].t=Date.now()-46000);await local.waitForTimeout(2200);
  assert.equal(await local.locator('#socValue').textContent(),'—','stale device readings remain live');
  await local.evaluate(()=>{window.mock.readings[0].t=Date.now();window.mock.running=false;});await local.waitForTimeout(2200);
  assert.equal(await local.locator('#voltageValue').textContent(),'— V','paused readings remain live');
  await local.close();
  await browser.close();if(server)server.close();console.log(`${cases} UI scenarios plus local-asset/bridge freshness checks passed.`);
})().catch(error=>{console.error(error);process.exit(1);});
