const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const fs=require('node:fs');
const luminance=rgb=>rgb.slice(0,3).map(c=>c/255).map(c=>c<=.04045?c/12.92:((c+.055)/1.055)**2.4).reduce((n,c,i)=>n+c*[.2126,.7152,.0722][i],0);
const ratio=(a,b)=>(Math.max(luminance(a),luminance(b))+.05)/(Math.min(luminance(a),luminance(b))+.05);
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.WATTPIT_CHROMIUM,args:['--no-sandbox','--single-process','--no-zygote','--disable-gpu']});
 const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html')+'?demo=1');
 const check=async(label)=>{
  const rows=await page.evaluate(()=>[...document.querySelectorAll('.cockpit-tile')].flatMap(tile=>[...tile.querySelectorAll('.tile-number,.tile-unit,.tile-label,.tile-chart-range,.tile-hint,.gauge-min,.gauge-max')].filter(el=>el.textContent.trim()).map(el=>{
   const canvas=document.createElement('canvas');canvas.width=canvas.height=1;const ctx=canvas.getContext('2d');ctx.fillStyle=getComputedStyle(document.body).backgroundColor;ctx.fillRect(0,0,1,1);ctx.fillStyle=getComputedStyle(tile).backgroundColor;ctx.fillRect(0,0,1,1);const background=[...ctx.getImageData(0,0,1,1).data];ctx.clearRect(0,0,1,1);ctx.fillStyle=getComputedStyle(el).color;ctx.fillRect(0,0,1,1);return {text:el.textContent,className:el.className,foreground:[...ctx.getImageData(0,0,1,1).data],background};
  })));
  for(const row of rows)assert.ok(ratio(row.foreground,row.background)>=4.5,`${label}: ${row.className} ${row.text} contrast ${ratio(row.foreground,row.background)}`);
 };
 for(const theme of ['light','dark']){
  if(await page.locator('html').getAttribute('data-theme')!==theme)await page.locator('#themeToggle').click();
  for(const palette of ['classic','blue','green','turquoise','orange','neutral']){
   await page.locator('[data-page="settings"]').click();await page.locator(`[data-palette="${palette}"]`).last().click();await page.locator('[data-page="overview"]').click();await check(`${theme}/${palette}`);
  }
 }
 await page.locator('#themeToggle').click();
 // Saved custom backgrounds and disabled decorative adaptation cannot hide the numbers.
 await page.locator('[data-tile="default-solar"]').click();await page.locator('#tileActionSettings').click();await page.locator('#tileType').selectOption('chart');
 await page.locator('summary').filter({hasText:/^Farben & Schrift$/}).click();await page.locator('#tileAdaptiveColor').uncheck();
 await page.locator('#tileBackgroundMode').selectOption('custom');await page.locator('#tileBackground').fill('#18303b');await page.locator('#saveTile').click();await check('light/custom dark');
 await page.locator('[data-tile="default-solar"]').click();await page.locator('#tileActionSettings').click();await page.locator('summary').filter({hasText:/^Farben & Schrift$/}).click();await page.locator('#tileOpacity').fill('0');await page.locator('#saveTile').click();await check('light/transparent');
 if(process.env.WATTPIT_SCREENSHOT_DIR){fs.mkdirSync(process.env.WATTPIT_SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:path.join(process.env.WATTPIT_SCREENSHOT_DIR,'overview-light-contrast.png'),fullPage:true});}
 assert.equal(errors.length,0,errors.join('\n'));await browser.close();console.log('Rendered number, unit, label and axis contrast passed for all 12 palette/theme combinations and custom/transparent tiles.');
})().catch(error=>{console.error(error);process.exit(1);});
