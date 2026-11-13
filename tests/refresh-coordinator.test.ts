import test from "node:test";
import assert from "node:assert/strict";
import {refreshDelay} from "../lib/network/refresh-coordinator";
test("refresh requests are spaced and have a bounded coalescing delay",()=>{
  assert.equal(refreshDelay(10000,0),250);
  assert.equal(refreshDelay(10000,10000),1000);
  assert.equal(refreshDelay(10500,10000),500);
  assert.equal(refreshDelay(11000,10000),250);
});
