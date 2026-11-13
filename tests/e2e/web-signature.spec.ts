import {test,expect} from "@playwright/test";
const widths=[320,390,768,1024,1440,1920];
for(const width of widths){
 test("responsive auth and composer at "+width+"px",async({page},testInfo)=>{
  await page.setViewportSize({width,height:width<768?844:900});
  await page.goto("/sign-in");
  await expect(page.locator(".sera-web-auth .ax-head h1")).toBeVisible();
  await expect(page.locator(".sera-web-header .sera-brand-mark")).toBeVisible();
  await expect(page.locator(".ax-form input[name=email]")).toBeVisible();
  await expect(page.locator(".ax-primary")).toBeVisible();
  if(width>=900)await expect(page.locator(".ax-brand-panel")).toBeVisible();
  else await expect(page.locator(".ax-brand-panel")).toBeHidden();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width+1);
  await page.screenshot({path:testInfo.outputPath("signin-"+width+".png"),fullPage:true});
  await page.goto("/sign-up");
  await expect(page.locator(".ax-form input[name=email]")).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width+1);
  await page.screenshot({path:testInfo.outputPath("signup-"+width+".png"),fullPage:true});
  await page.goto("/qa-web-signature");
  await page.getByRole("button",{name:"Start a post",exact:false}).click();
  const dialog=page.getByRole("dialog",{name:"Create post"});
  await expect(dialog).toBeVisible();
  const editor=dialog.locator("textarea[name=body]");
  await editor.fill("An example professional update.\nSecond line.");
  await expect(editor).toHaveValue("An example professional update.\nSecond line.");
  await expect(dialog.locator(".sera-premium-publish")).toBeEnabled();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width+1);
  await page.screenshot({path:testInfo.outputPath("composer-"+width+".png")});
  await page.evaluate(()=>{document.documentElement.dataset.seraTheme="dark"});
  await page.screenshot({path:testInfo.outputPath("composer-dark-"+width+".png")});
 });
}
for(const width of [320,390,1440]){
 test("responsive message conversation at "+width+"px",async({page},testInfo)=>{
  await page.setViewportSize({width,height:844});
  await page.goto("/qa-messaging-fixture");
  await expect(page.getByRole("region",{name:"Conversation with Alex Morgan"})).toBeVisible();
  const composer=page.locator(".sera-chat-composer");
  await expect(composer).toBeVisible();
  const bounds=await composer.boundingBox();
  expect(bounds).not.toBeNull();
  expect(bounds!.y+bounds!.height).toBeLessThanOrEqual(845);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width+1);
  await page.screenshot({path:testInfo.outputPath("messages-"+width+".png")});
 });
}
