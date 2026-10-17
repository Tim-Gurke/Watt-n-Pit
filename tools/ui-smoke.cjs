const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');

(async () => {
  const browser = await chromium.launch({headless:true,args:['--no-sandbox',...(process.env.WATTPIT_BROWSER_ARGS || '').split(' ').filter(Boolean)],...(process.env.WATTPIT_CHROMIUM?{executablePath:process.env.WATTPIT_CHROMIUM}:{})});
  let server;
  if (!process.env.WATTPIT_UI_URL) {
    const assets=path.resolve(__dirname,'../app/src/main/assets');
    server=http.createServer((request,response)=>{
      const route=new URL(request.url,'http://localhost').pathname;
      const file=path.join(assets,route==='/'?'index.html':route);
      if(!file.startsWith(assets+path.sep)||!fs.existsSync(file)){response.writeHead(404);response.end();return;}
      response.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':file.endsWith('.svg')?'image/svg+xml':'text/html');
      response.end(fs.readFileSync(file));
    });
    await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  }
  const base = process.env.WATTPIT_UI_URL || `http://127.0.0.1:${server.address().port}`;
  const output = process.env.WATTPIT_SCREENSHOT_DIR || null;
  if (output) fs.mkdirSync(output,{recursive:true});
  let cases = 0;
  const context=await browser.newContext();
  const shared=await context.newPage();
  for (const viewport of [{width:360,height:800},{width:832,height:390},{width:1100,height:900}]) {
    for (const theme of ['dark','light']) {
      const page = shared;
      await page.setViewportSize(viewport);
      const errors=[];
      page.on('pageerror',e=>errors.push(e.message));
      await page.goto(base);
      await page.evaluate(()=>localStorage.clear());await page.reload();
      if (theme==='light') await page.locator('#themeToggle').click();
      await page.locator('[data-tile="default-soc"] .tile-number').waitFor();
      assert.equal(await page.locator('[data-tile="default-soc"] .tile-number').textContent(),'—');
      assert.equal(await page.locator('#welcome').isVisible(),true);
      assert.equal(await page.evaluate(()=>document.body.scrollWidth>innerWidth),false,'horizontal overflow');
      await page.locator('#demoFirst').click();
      assert.equal(await page.locator('[data-tile="default-soc"] .tile-number').textContent(),'76,4');
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
      if(output && viewport.width===360) { await page.evaluate(()=>window.scrollTo(0,0));await page.waitForTimeout(200); await page.screenshot({path:path.join(output,`history-${theme}.png`)}); }
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
      await page.locator('#addTile').click();
      await page.locator('.metric-option[data-metric="remainingAh"]').click();
      assert.equal(await page.locator('.cockpit-tile').last().locator('.tile-number').textContent(),'114,6');
      await page.locator('#exitDemo').click();
      assert.equal(await page.locator('[data-tile="default-soc"] .tile-number').textContent(),'—');
      assert.equal(errors.length,0,errors.join('\n'));
      cases++;
    }
  }
  // Customize, overlap, reload and rotate without moving unrelated tiles.
  await shared.setViewportSize({width:390,height:844});
  await shared.goto(base);await shared.evaluate(()=>localStorage.clear());await shared.reload();
  await shared.locator('#demoFirst').click();
  assert.equal(await shared.locator('.cockpit-tile').count(),7);
  assert.equal(await shared.locator('.cockpit-tile').filter({hasText:'Entnommene Kapazität'}).count(),0);
  await shared.locator('[data-tile="default-remaining"]').click();
  await shared.locator('#tileUnit').selectOption('d');
  await shared.locator('#tileTitle').fill('Vorrat an Zeit');
  await shared.locator('#tileType').selectOption('gauge');
  await shared.locator('summary').filter({hasText:/^Skala$/}).click();
  await shared.locator('#tileMin').fill('0');await shared.locator('#tileMax').fill('2');
  await shared.locator('#tileMulticolor').check();
  await shared.locator('#saveTile').click();
  assert.equal(await shared.locator('[data-tile="default-remaining"] .tile-number').textContent(),'0,3');
  assert.equal(await shared.locator('[data-tile="default-remaining"] .tile-gauge svg path').count(),60);
  const before=await shared.evaluate(()=>JSON.parse(localStorage.getItem('wattpit-dashboard-v1')));
  await shared.locator('#editDashboard').click();
  await shared.locator('[data-tile="default-voltage"]').evaluate(el=>el.scrollIntoView({block:'center',behavior:'instant'}));
  const rect=await shared.locator('[data-tile="default-voltage"]').boundingBox();
  await shared.mouse.move(rect.x+25,rect.y+30);await shared.mouse.down();
  await shared.mouse.move(rect.x+65,rect.y+56,{steps:6});await shared.mouse.up();
  const after=await shared.evaluate(()=>JSON.parse(localStorage.getItem('wattpit-dashboard-v1')));
  assert.notDeepEqual(after.tiles.find(t=>t.id==='default-voltage').layouts.compact,before.tiles.find(t=>t.id==='default-voltage').layouts.compact);
  for(const t of before.tiles.filter(t=>t.id!=='default-voltage'))assert.deepEqual(after.tiles.find(x=>x.id===t.id).layouts,t.layouts,'drag moved an unrelated tile');
  await shared.reload();await shared.locator('#demoFirst').click();
  assert.equal(await shared.locator('[data-tile="default-remaining"] .tile-label').textContent(),'Vorrat an Zeit');
  assert.equal(await shared.locator('[data-tile="default-remaining"] .tile-number').textContent(),'0,3');
  const saved=await shared.evaluate(()=>localStorage.getItem('wattpit-dashboard-v1'));
  await shared.setViewportSize({width:832,height:390});await shared.waitForTimeout(100);
  assert.equal(await shared.locator('#layoutProfile').textContent(),'Breite Ansicht');
  assert.equal(await shared.evaluate(()=>localStorage.getItem('wattpit-dashboard-v1')),saved,'rotation rewrites the saved arrangement');
  await shared.locator('#addTile').click();await shared.locator('.metric-option[data-metric="solar"]').click();
  assert.equal(await shared.locator('.cockpit-tile').count(),8);
  await shared.waitForTimeout(350);await shared.locator('.cockpit-tile').last().click();
  await shared.locator('#tileType').selectOption('chart');await shared.locator('#saveTile').click();
  assert.equal(await shared.locator('.cockpit-tile').last().locator('.tile-sparkline').count(),1,'the historical tile does not display its series');
  await shared.locator('.cockpit-tile').last().click();await shared.locator('#removeTile').click();
  assert.equal(await shared.locator('.cockpit-tile').count(),7);
  const local=shared;await local.setViewportSize({width:390,height:844});
  await local.addInitScript(()=>{
    window.mock={running:true,status:'Bluetooth-Empfang aktiv',devices:[{kind:'shunt',mac:'AA:BB:CC:DD:EE:FF',name:'SmartShunt',error:'',seen:Date.now()}],found:[],readings:[{kind:'shunt',device:'AA:BB:CC:DD:EE:FF',t:Date.now(),soc:76.4,voltage:12.94,current:0,power:0,ttg:null,consumed:23.6}],stats:{count:0},settings:{theme:'dark',capacity:100,sampleSeconds:10}};
    window.WattPit={snapshot:()=>JSON.stringify(window.mock),history:()=>JSON.stringify({points:[]}),settings:()=>'',stop:()=>window.mock.running=false,start:()=>window.mock.running=true};
  });
  await local.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
  assert.equal(await local.locator('[data-tile="default-soc"] .tile-number').textContent(),'76,4','local asset page cannot access the bridge');
  assert.equal(await local.locator('[data-tile="default-current"] .tile-number').textContent(),'0,00','zero is displayed as missing');
  await local.evaluate(()=>window.mock.readings[0].t=Date.now()-46000);await local.waitForTimeout(2200);
  assert.equal(await local.locator('[data-tile="default-soc"] .tile-number').textContent(),'—','stale device readings remain live');
  await local.evaluate(()=>{window.mock.readings[0].t=Date.now();window.mock.running=false;});await local.waitForTimeout(2200);
  assert.equal(await local.locator('[data-tile="default-voltage"] .tile-number').textContent(),'—','paused readings remain live');

  await browser.close();if(server)server.close();console.log(`${cases} UI scenarios, customization/persistence/overlap/rotation and local-asset/bridge freshness checks passed.`);
})().catch(error=>{console.error(error);process.exit(1);});
