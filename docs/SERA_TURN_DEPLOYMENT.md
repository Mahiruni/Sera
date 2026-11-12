# SERA production TURN relay deployment checklist

The SERA clients already consume short-lived coturn REST credentials. This setup is for a dedicated public Linux VPS, not Vercel serverless or an HTTP-only proxy. Do not run this on an existing server without checking port availability, firewall rules and cost.

1. Obtain a public IPv4 address, DNS hostname and trusted TLS certificate for the relay. Confirm UDP/TCP 3478, TLS TCP 5349 and a dedicated UDP relay port range (49160–49200 for a small pilot) are permitted by the provider firewall.
2. Install a supported coturn release. Configure a randomly generated 256-bit shared secret using a root-owned configuration file (mode 0600). Never store it in Git or paste it into chat.
3. Configure realm, listening/relay addresses and external IP mapping where NAT is used. Require authenticated REST credentials, TLS certificate/private key, explicit relay port range, per-user and global quotas. Deny loopback, link-local, multicast, RFC1918, carrier-grade NAT and cloud metadata destinations. Do not enable allow-loopback-peers outside the isolated CI test.
4. Configure the SERA private settings row through the authenticated administrator dashboard using the public turns: hostname and matching secret, keeping enabled=false until the relay is verified.
5. Enroll only two consenting dedicated QA users, then enable the pilot. Test Android↔Android, web↔web and Android↔web across different networks. Check real microphone audio, mute, speaker, interruption, call end and notification expiry. Remove test access after verification if required.

The workflow SERA Voice TURN Transport QA validates synthetic bidirectional RTP on a disposable local relay. It does not validate the production relay, the real application sign-in flow, physical-device audio or background calling. No production relay or billing resource is created by the workflow.
