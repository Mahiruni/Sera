import test from "node:test";
import assert from "node:assert/strict";
import {defaultMessagePreferences,shouldSuppressMessage,parseClock} from "../lib/network/message-control-model";
test("overnight quiet hours follow account timezone",()=>{const p={...defaultMessagePreferences,quiet_enabled:true};assert.equal(shouldSuppressMessage(p,null,new Date("2026-11-13T20:00:00Z")),true);assert.equal(shouldSuppressMessage(p,null,new Date("2026-11-13T10:00:00Z")),false);});
test("disabled and muted alerts suppress push only",()=>{assert.equal(shouldSuppressMessage({...defaultMessagePreferences,notifications_enabled:false},null,new Date()),true);assert.equal(shouldSuppressMessage(null,"infinity",new Date()),true);assert.equal(shouldSuppressMessage(null,"2000-01-01T00:00:00Z",new Date()),false);});
test("clock parser rejects invalid hours",()=>{assert.equal(parseClock("22:30"),1350);assert.equal(parseClock("24:00"),null);});
