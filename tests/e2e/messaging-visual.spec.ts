import {test,expect} from "@playwright/test";
for(const width of [320,375,430,1280]){
 test("actual messaging component layout at "+width+"px",async({page},testInfo)=>{
  await page.setViewportSize({width,height:width<700?844:900});
  await page.goto("/qa-messaging-fixture?view=inbox");
  await expect(page.getByRole("heading",{name:"Messages",exact:true})).toBeVisible();
  await expect(page.getByText("Alex Morgan",{exact:true})).toBeVisible();
  await page.screenshot({path:testInfo.outputPath("inbox-"+width+".png")});
  await page.goto("/qa-messaging-fixture");
  const thread=page.getByRole("region",{name:"Conversation with Alex Morgan"});
  await expect(thread).toBeVisible();
  const composer=page.locator(".sera-chat-composer");
  await expect(composer).toBeVisible();
  const bounds=await composer.boundingBox();
  expect(bounds).not.toBeNull();
  expect(bounds!.y+bounds!.height).toBeLessThanOrEqual((width<700?844:900)+1);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width+1);
  if(width<700)await expect(page.locator(".site-header")).toBeHidden();
  await page.screenshot({path:testInfo.outputPath("thread-"+width+".png")});
  const row=page.locator(".sera-chat-message-row").filter({hasText:"Excellent. I will review it today."});
  await row.getByRole("button",{name:"Message actions"}).click();
  await expect(row.getByRole("button",{name:"Reply",exact:true})).toBeVisible();
  await row.getByRole("button",{name:"Reply",exact:true}).click();
  await expect(page.locator(".sera-chat-reply-preview")).toBeVisible();
  await page.screenshot({path:testInfo.outputPath("reply-"+width+".png")});
  if(width<700){
   await page.setViewportSize({width,height:480});
   await expect(composer).toBeVisible();
   const resized=await composer.boundingBox();expect(resized!.y+resized!.height).toBeLessThanOrEqual(481);
   await page.screenshot({path:testInfo.outputPath("resized-"+width+".png")});
  }
  await page.getByRole("button",{name:"Cancel reply"}).click();
  await row.getByRole("button",{name:"Message actions"}).click();
  await row.getByRole("button",{name:"React",exact:true}).click();
  await expect(page.locator(".sera-chat-reaction-picker")).toBeVisible();
  await page.screenshot({path:testInfo.outputPath("reactions-"+width+".png")});
  await page.keyboard.press("Escape");
  await row.getByRole("button",{name:"Message actions"}).click();
  await row.getByRole("button",{name:"Unsend",exact:true}).click();
  await expect(page.getByRole("dialog",{name:"Unsend message"})).toBeVisible();
  await page.screenshot({path:testInfo.outputPath("unsend-"+width+".png")});
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog",{name:"Unsend message"})).toBeHidden();
  await page.evaluate(()=>{document.documentElement.dataset.seraTheme="dark";});
  await page.screenshot({path:testInfo.outputPath("dark-"+width+".png")});
 });
}
