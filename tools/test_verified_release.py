"""Offline publication provenance contracts; no network, tags, or uploads."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import verified_release as vr

class ProvenanceTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.source=Path(self.tmp.name)
        (self.source/'gradle.properties').write_text('mod_version=0.1.1\nminecraft_version=1.21.1\n')
        self.sha='a'*40
        self.run={'status':'completed','conclusion':'success','event':'push','head_sha':self.sha,'head_branch':'1.21.1','path':'.github/workflows/build.yml','head_repository':{'full_name':vr.REPO},'run_attempt':1}
        self.job={'name':'integration','conclusion':'success'}
        self.art={'id':9,'name':'applied-singulo-development','expired':False,'digest':'sha256:'+'b'*64}
    def api(self,p):
        if p.startswith('git/ref/'): return {'object':{'type':'commit','sha':self.sha}}
        if p.startswith('compare/'): return {'status':'identical'}
        if '/jobs?' in p: return {'jobs':[self.job]}
        if '/artifacts?' in p: return {'total_count':1,'artifacts':[self.art]}
        return self.run
    def git(self,s,*args): return '' if args[0]=='status' else self.sha
    def inspect(self):
        with patch.object(vr,'api',self.api),patch.object(vr,'git',self.git): return vr.inspect(5,'v0.1.1',self.source)
    def test_exact_success_is_accepted(self): self.assertEqual(self.inspect()['commit'],self.sha)
    def test_wrong_sha_branch_event_workflow_or_attempt_rejected(self):
        for k,v in [('head_sha','c'*40),('head_branch','fix/x'),('event','pull_request'),('path','bad.yml'),('run_attempt',2),('conclusion','failure')]:
            with self.subTest(k=k):
                old=self.run[k];self.run[k]=v
                with self.assertRaises(RuntimeError): self.inspect()
                self.run[k]=old
    def test_expired_or_undigested_artifact_rejected(self):
        self.art['expired']=True
        with self.assertRaises(RuntimeError): self.inspect()
        self.art['expired']=False;self.art['digest']=''
        with self.assertRaises(RuntimeError): self.inspect()
    def test_failed_job_rejected(self):
        self.job['conclusion']='failure'
        with self.assertRaises(RuntimeError): self.inspect()
    def test_modified_download_rejected_before_zip_parse(self):
        with patch.object(vr.subprocess,'check_output',return_value=b'changed'):
            with self.assertRaisesRegex(RuntimeError,'digest mismatch'):
                vr.promote({'artifact':self.art},self.source)

class PromotionTests(unittest.TestCase):
    def fixture(self, root, mutate=None):
        import hashlib, io, zipfile
        source=root/'source'; source.mkdir()
        (source/'tools').mkdir()
        (source/'gradle.properties').write_text('singulo_version_range=[0.20.0,0.21.0)\n')
        pins={'Singulo.jar':{'url':'https://example.invalid/singulo.jar','sha256':'a'*64}}
        (source/'tools/dependencies.json').write_text(json.dumps(pins))
        name='Applied-Singulo-0.1.1-1.21.1N.jar'
        toml='[[mods]]\nmodId="applied_singulo"\nversion="0.1.1"\n[[dependencies.applied_singulo]]\nmodId="singulo"\nversionRange="[0.20.0,0.21.0)"\n[[dependencies.applied_singulo]]\nmodId="minecraft"\nversionRange="[1.21.1]"\n'
        if mutate=='metadata': toml=toml.replace('version="0.1.1"','version="9.0.0"')
        buf=io.BytesIO()
        with zipfile.ZipFile(buf,'w') as z: z.writestr('META-INF/neoforge.mods.toml',toml)
        jar=buf.getvalue(); log=b'47 GAME TESTS COMPLETE\nAll 47 required tests passed\n'
        if mutate=='log': log=b'0 GAME TESTS COMPLETE'
        receipt={'filename':name,'git_commit':'a'*40,'ci_run_id':'5','published':False,'release_type':'beta','sha256':hashlib.sha256(jar).hexdigest(),'sha512':hashlib.sha512(jar).hexdigest(),'gametest_log_sha256':hashlib.sha256(log).hexdigest(),'dependencies':pins}
        if mutate=='commit': receipt['git_commit']='c'*40
        if mutate=='dependencies': receipt['dependencies']={}
        buf=io.BytesIO()
        with zipfile.ZipFile(buf,'w') as z:
            z.writestr('release-manifest.json',json.dumps(receipt)); z.writestr('libs/'+name,jar); z.writestr('integration-gametest.log',log)
        archive=buf.getvalue(); metadata={'artifact':{'id':9,'digest':'sha256:'+hashlib.sha256(archive).hexdigest()},'version':'0.1.1','commit':'a'*40,'run_id':5}
        return source,metadata,archive,name
    def run_fixture(self, mutate=None):
        import os
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); source,metadata,archive,name=self.fixture(root,mutate); old=Path.cwd()
            try:
                os.chdir(root)
                with patch.object(vr.subprocess,'check_output',return_value=archive): vr.promote(metadata,source)
                self.assertTrue((root/'release-artifacts'/name).is_file())
            finally: os.chdir(old)
    def test_exact_archive_promotes(self): self.run_fixture()
    def test_receipt_or_payload_mutations_rejected(self):
        for mutation in ('commit','dependencies','log','metadata'):
            with self.subTest(mutation=mutation),self.assertRaises(RuntimeError): self.run_fixture(mutation)

if __name__=='__main__': unittest.main()
