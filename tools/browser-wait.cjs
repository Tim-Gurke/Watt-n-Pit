/* Playwright's waitForFunction may require eval in the page utility world.
 * Poll through evaluate so the actual application's strict CSP stays enabled. */
module.exports=async function waitForPage(page,predicate,arg,timeout=10000){
 const deadline=Date.now()+timeout;
 do{if(await page.evaluate(predicate,arg))return;await page.waitForTimeout(50);}while(Date.now()<deadline);
 throw Error('Browser did not reach the expected state before the timeout');
};
