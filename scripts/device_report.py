#!/usr/bin/env python3
"""Explicit-device, read-only app compatibility snapshot; no notification or health-content dump."""
import argparse,datetime,json,pathlib,re,subprocess
p=argparse.ArgumentParser();p.add_argument('serial');p.add_argument('--label',default='snapshot');a=p.parse_args()
def adb(*args):
    r=subprocess.run(['adb','-s',a.serial,*args],text=True,capture_output=True,timeout=30)
    if r.returncode:raise RuntimeError(r.stderr.strip() or r.stdout.strip())
    return r.stdout.strip()
try:
    report={'captured_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'label':a.label,'device':{}}
    for key in ['ro.product.manufacturer','ro.product.model','ro.build.version.release','ro.build.version.sdk','ro.product.cpu.abilist','ro.soc.model']:
        report['device'][key]=adb('shell','getprop',key)
    mem=adb('shell','cat','/proc/meminfo')
    report['ram_kib']={k:int(v) for k,v in re.findall(r'^(MemTotal|MemAvailable):\s+(\d+)',mem,re.M)}
    battery=adb('shell','dumpsys','battery');report['battery']={k:int(v) for k,v in re.findall(r'^\s*(level|scale|temperature|voltage|status):\s*(-?\d+)',battery,re.M)}
    report['app_meminfo']=adb('shell','dumpsys','meminfo','dev.kw66.glass')
    out=pathlib.Path(__file__).resolve().parents[1]/'device-reports';out.mkdir(exist_ok=True)
    label=re.sub(r'[^A-Za-z0-9_-]','_',a.label)[:60];name=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+label+'.json'
    target=out/name;target.write_text(json.dumps(report,indent=2));target.chmod(0o600);print(target)
except (RuntimeError,subprocess.TimeoutExpired) as e:raise SystemExit(str(e))
