import importlib.util
from pathlib import Path
import stat
import tempfile
import unittest

spec=importlib.util.spec_from_file_location("relay",Path(__file__).with_name("generate_turn_config.py"))
relay=importlib.util.module_from_spec(spec)
spec.loader.exec_module(relay)
class RelayConfigTest(unittest.TestCase):
    def build(self,host="turn.example.com",local="10.1.2.3",public="8.8.4.4",cert="/etc/ssl/cert.pem"):
        return relay.build_config(host,local,public,cert,"/etc/ssl/key.pem","a"*64)
    def test_auth_tls_and_private_peer_denials(self):
        c=self.build()
        for entry in ["use-auth-secret","no-tlsv1","no-tlsv1_1","no-cli","no-multicast-peers","total-quota=32","min-port=49160","max-port=49200","denied-peer-ip=169.254.0.0-169.254.255.255","denied-peer-ip=10.0.0.0-10.255.255.255"]:
            self.assertIn(entry,c.splitlines())
        self.assertNotIn("allow-loopback-peers",c)
    def test_invalid_inputs(self):
        for kwargs in [{"host":"example.com\nno-auth"},{"public":"10.0.0.1"},{"local":"127.0.0.1"},{"cert":"/tmp/key\nno-auth"}]:
            with self.assertRaises(ValueError): self.build(**kwargs)
    def test_private_config_never_overwrites(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"turn.conf"
            relay.write_private(p,self.build())
            self.assertEqual(stat.S_IMODE(p.stat().st_mode),0o600)
            before=p.read_bytes()
            with self.assertRaises(FileExistsError): relay.write_private(p,"bad")
            self.assertEqual(before,p.read_bytes())
if __name__=="__main__": unittest.main()
