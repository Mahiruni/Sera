# SERA production TURN relay deployment checklist

The SERA clients already consume short-lived coturn REST credentials. This setup is for a dedicated public Linux VPS, not Vercel serverless or an HTTP-only proxy. Do not run this on an existing server without checking port availability, firewall rules and cost.

1. Obtain a public IPv4 address, DNS hostname and trusted TLS certificate for the relay. Confirm UDP/TCP 3478, TLS TCP 5349 and a dedicated UDP relay port range (49160–49200 for a small pilot) are permitted by the provider firewall.
2. Install a supported coturn release. Configure a randomly generated 256-bit shared secret using a root-owned configuration file (mode 0600). Never store it in Git or paste it into chat.
3. Configure realm, listening/relay addresses and external IP mapping where NAT is used. Require authenticated REST credentials, TLS certificate/private key, explicit relay port range, per-user and global quotas. Deny loopback, link-local, multicast, RFC1918, carrier-grade NAT and cloud metadata destinations. Do not enable allow-loopback-peers outside the isolated CI test.
4. Configure the SERA private settings row through the authenticated administrator dashboard using the public turns: hostname and matching secret, keeping enabled=false until the relay is verified.
5. Enroll only two consenting dedicated QA users, then enable the pilot. Test Android↔Android, web↔web and Android↔web across different networks. Check real microphone audio, mute, speaker, interruption, call end and notification expiry. Remove test access after verification if required.

The workflow SERA Voice TURN Transport QA validates synthetic bidirectional RTP on a disposable local relay. It does not validate the production relay, the real application sign-in flow, physical-device audio or background calling. No production relay or billing resource is created by the workflow.

## Deployment configuration generator
Use `infra/turn/generate_turn_config.py` on a dedicated Linux relay host after obtaining a public IPv4, DNS record and trusted TLS certificate. For example (replace all values):

```sh
sudo python3 infra/turn/generate_turn_config.py \\
  --hostname turn.example.com --local-ip 10.1.2.3 --public-ip YOUR_PUBLIC_IPV4 \\
  --cert /etc/ssl/sera-turn/fullchain.pem --key /etc/ssl/sera-turn/privkey.pem \\
  --output /etc/turnserver-sera.conf
```

The generator creates a mode-0600 config with a fresh 256-bit secret, quotas, a bounded pilot UDP relay range, TLS and private-address restrictions. It refuses to overwrite an existing file. Use the service account that runs coturn, granting only that account read access to the configuration and private key. Do not put the generated file in the repository. Review the interface/NAT mapping, egress firewall (including cloud metadata/private destinations), certificate renewal and bandwidth capacity before starting the service. The included unit tests validate config generation, not a production network deployment.

## Managed Cloudflare alternative (recommended without a VPS)
Create a TURN key in the Cloudflare Realtime dashboard. Add its key ID and API token as **server-only** Production environment variables on SERA's Vercel project: `SERA_CLOUDFLARE_TURN_KEY_ID` and `SERA_CLOUDFLARE_TURN_API_TOKEN`. Never use NEXT_PUBLIC variables. Deploy the backend first, then the new APK. The managed provider uses three-hour temporary credentials (call lifetime is capped at two hours), a private pilot allowlist and rolling issuance quotas: 20 per member and 100 globally per day. No credentials are issued merely by opening a conversation. The backend filters relay URLs and never exposes the long-lived key. These quotas are not a billing cap. Review Cloudflare's Realtime usage and billing settings before activation. Set relay_provider='cloudflare' and the two pilot member UUIDs in the private settings row, keeping enabled=false until deployment and credentials are verified. Only then enable the pilot. No provider account or billing has been created by this change.
