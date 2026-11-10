"""Offline regression tests for the reported provider-403 bootstrap failure."""
import hashlib,importlib.util,io,json,pathlib,tarfile,tempfile,unittest,urllib.error
from unittest.mock import patch
from contextlib import redirect_stdout,redirect_stderr
source=pathlib.Path(__file__).resolve().parents[1]/'scripts/toolchain.py'
spec=importlib.util.spec_from_file_location('kw66_toolchain',source)
toolchain=importlib.util.module_from_spec(spec);spec.loader.exec_module(toolchain)

class ToolchainTest(unittest.TestCase):
    def setUp(self):
        self.enterContext(redirect_stdout(io.StringIO()));self.enterContext(redirect_stderr(io.StringIO()))
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.tools=pathlib.Path(self.temp.name);self.location=patch.object(toolchain,'TOOLS',self.tools);self.location.start();self.addCleanup(self.location.stop)
    def test_403_falls_back_to_verified_corretto(self):
        buffer=io.BytesIO()
        with tarfile.open(fileobj=buffer,mode='w:gz') as archive:
            member=tarfile.TarInfo('corretto-21/bin/javac');member.size=3;archive.addfile(member,io.BytesIO(b'jdk'))
        data=buffer.getvalue();checksum=hashlib.sha256(data).hexdigest()
        blocked=urllib.error.HTTPError('https://api.adoptium.net',403,'Forbidden',None,None)
        info={'provider':'Amazon Corretto','url':'https://corretto.aws/test.tar.gz','sha256':checksum}
        with patch.object(toolchain,'adoptium',side_effect=blocked),patch.object(toolchain,'corretto',return_value=info),patch.object(toolchain,'request',return_value=io.BytesIO(data)):
            toolchain.install_jdk()
        self.assertTrue((self.tools/'jdk/bin/javac').is_file())
        self.assertEqual(json.loads((self.tools/'jdk-download.json').read_text())['provider'],'Amazon Corretto')
        self.assertFalse((self.tools/'jdk.tar.gz').exists())
    def test_checksum_mismatch_does_not_install_or_leave_partial_file(self):
        with patch.object(toolchain,'request',return_value=io.BytesIO(b'wrong download')):
            with self.assertRaisesRegex(RuntimeError,'SHA-256 mismatch'):toolchain.download('https://example.invalid',self.tools/'jdk.tar.gz','0'*64)
        self.assertEqual(list(self.tools.iterdir()),[])
    def test_both_providers_failed_gives_java_home_instruction(self):
        with patch.object(toolchain,'adoptium',side_effect=OSError('403 Forbidden')),patch.object(toolchain,'corretto',side_effect=OSError('Network unavailable')):
            with self.assertRaisesRegex(RuntimeError,'JAVA_HOME'):toolchain.install_jdk()
    def test_corretto_checksums_are_validated(self):
        with patch.object(toolchain,'read_url',return_value=b'<html>Forbidden</html>'):
            with self.assertRaisesRegex(RuntimeError,'Invalid Corretto SHA-256'):toolchain.corretto()

if __name__=='__main__':unittest.main()
