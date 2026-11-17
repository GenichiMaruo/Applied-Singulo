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

if __name__=='__main__': unittest.main()
