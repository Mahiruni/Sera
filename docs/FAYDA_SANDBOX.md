# Milestone 13.5.1A — local Fayda sandbox
This isolated local harness is NOT a production verification provider. It cannot access Supabase or issue SERA badges. It uses official published test Client 1 and its exact localhost:3000/callback redirect. Do not deploy it publicly or use real FANs.

1. Open https://partner.fayda.et/docs and obtain the full test client configuration. Set FAYDA_CLIENT_ID and FAYDA_PRIVATE_KEY (base64 RSA JWK) in your local environment. Do not commit keys.
2. Confirm the exact sandbox issuer and JWKS URI from official provider metadata or Fayda support; set FAYDA_ISSUER and FAYDA_JWKS_URI. Both must be HTTPS on esignet.ida.fayda.et. The harness deliberately refuses guessed metadata.
3. Run `node --env-file=.env.fayda.local scripts/fayda-sandbox/server.mjs` with Node 22. Open http://localhost:3000. Stop any other server using port 3000.
4. Use only the official published test personas and OTP. The callback verifies RS256 signatures, issuer, audience, expiry, nonce, subject and single-use state; it requests signed userinfo. No tokens or identity claims are retained. If provider metadata or signed userinfo differ, do not relax checks; confirm the official contract.
5. Run npm test. The unit tests use generated RSA keys, not real identities.

Production/hosted integration is a separate gate: obtain approved organization, provider credentials, registered HTTPS callback, confirmed issuer/JWKS and userinfo contract, data protection review, and provider-specific assurance policy. Android can use a system-browser handoff only after a registered hosted callback and authenticated account binding are implemented. Sandbox success must never be written to the production identity badge tables. Existing manual review remains unchanged.
