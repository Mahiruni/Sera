import test from "node:test";
import assert from "node:assert/strict";
import {meetingProposalText,parseMeetingProposal} from "../lib/network/meeting-proposal";
test("meeting proposal keeps a UTC instant and duration",()=>{
 const text=meetingProposalText("Project discussion","2099-03-12T10:00:00",30);
 assert.ok(text);
 const result=parseMeetingProposal(text);
 assert.equal(result?.title,"Project discussion");
 assert.equal(result?.minutes,30);
 assert.equal(result?.at,new Date("2099-03-12T10:00:00").toISOString());
});
test("invalid meeting data is never promoted to a card",()=>{
 assert.equal(meetingProposalText("","2099-03-12T10:00:00",30),null);
 assert.equal(meetingProposalText("Past","2000-01-01T10:00:00",30),null);
 assert.equal(parseMeetingProposal("Meeting proposal: Test\nWhen: invalid\nDuration: 30 minutes"),null);
 assert.equal(parseMeetingProposal("Meeting proposal: Test\nWhen: 2099-03-12T10:00:00Z\nDuration: 999 minutes"),null);
});
