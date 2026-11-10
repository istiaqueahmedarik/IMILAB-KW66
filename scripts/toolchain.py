#!/usr/bin/env python3
"""Project-local tools, verified downloads, and independent JDK provider fallback."""
import hashlib,json,os,pathlib,platform,re,shutil,sys,tarfile,tempfile,urllib.error,urllib.request,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
TOOLS=ROOT/'.tools'
USER_AGENT='KW66-Glass-Builder/0.1'
def request(url):
    return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':USER_AGENT}),timeout=60)
def read_url(url):
    with request(url) as src:return src.read()
def download(url,target,sha=None):
    print('Downloading',target.name,flush=True)
    part=target.with_suffix(target.suffix+'.partial')
    try:
        digest=hashlib.sha256()
        with request(url) as src,part.open('wb') as dst:
            while chunk:=src.read(1024*1024):dst.write(chunk);digest.update(chunk)
        if sha and digest.hexdigest().lower()!=sha.lower():raise RuntimeError('SHA-256 mismatch: '+target.name)
        part.replace(target)
    finally:part.unlink(missing_ok=True)
def adoptium():
    meta=json.loads(read_url('https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse'))[0]
    pkg=meta['binary']['package']
    return {'provider':'Eclipse Temurin','url':pkg['link'],'sha256':pkg['checksum']}
def corretto():
    filename='amazon-corretto-21-x64-linux-jdk.tar.gz'
    checksum=read_url('https://corretto.aws/downloads/latest_sha256/'+filename).decode().strip().split()[0]
    if not re.fullmatch(r'[0-9a-fA-F]{64}',checksum):raise RuntimeError('Invalid Corretto SHA-256 response')
    return {'provider':'Amazon Corretto','url':'https://corretto.aws/downloads/latest/'+filename,'sha256':checksum}
def install_jdk():
    errors=[]
    for name,provider in (("Temurin",adoptium),("Corretto",corretto)):
        try:
            info=provider();archive=TOOLS/'jdk.tar.gz'
            print('Trying JDK 21 from '+info['provider'],flush=True)
            download(info['url'],archive,info['sha256'])
        except (OSError,ValueError,KeyError,IndexError,RuntimeError) as error:
            errors.append(name+': '+str(error));print('JDK source unavailable: '+errors[-1],file=sys.stderr,flush=True);continue
        with tempfile.TemporaryDirectory(prefix='jdk-unpack-',dir=TOOLS) as tmp:
            unpack=pathlib.Path(tmp)
            with tarfile.open(archive) as source:source.extractall(unpack,filter='data')
            dirs=[p for p in unpack.iterdir() if p.is_dir()]
            if len(dirs)!=1 or not (dirs[0]/'bin/javac').is_file():raise RuntimeError('Unexpected JDK archive layout')
            dirs[0].rename(TOOLS/'jdk')
        (TOOLS/'jdk-download.json').write_text(json.dumps(info,indent=2)+'\n');archive.unlink();return
    raise RuntimeError('JDK 21 download failed from both providers.\n'+'\n'.join(errors)+'\nUse an installed JDK 17–23 by setting JAVA_HOME to its directory, then rerun scripts/build.sh.')
def main(kind):
    TOOLS.mkdir(exist_ok=True)
    if platform.system()!='Linux' or platform.machine() not in ('x86_64','AMD64'):
        raise RuntimeError('Bootstrap supports Linux x86_64. Use Android Studio / JDK 21 / Gradle 8.13 on other hosts.')
    if kind=='jdk':install_jdk()
    elif kind=='gradle':
        base='https://services.gradle.org/distributions/gradle-8.13-bin.zip'
        sha=read_url(base+'.sha256').decode().strip();archive=TOOLS/'gradle.zip';download(base,archive,sha)
        with zipfile.ZipFile(archive) as f:f.extractall(TOOLS)
        (TOOLS/'gradle-8.13/bin/gradle').chmod(0o755);archive.unlink()
    elif kind=='android':
        sdk=pathlib.Path(os.environ.get('ANDROID_HOME',TOOLS/'sdk'))
        archive=TOOLS/'android-cli.zip';download('https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip',archive)
        with tempfile.TemporaryDirectory(prefix='android-unpack-',dir=TOOLS) as tmp:
            unpack=pathlib.Path(tmp)
            with zipfile.ZipFile(archive) as f:f.extractall(unpack)
            dest=sdk/'cmdline-tools/latest';dest.parent.mkdir(parents=True,exist_ok=True);shutil.move(str(unpack/'cmdline-tools'),dest)
        for p in (dest/'bin').iterdir():p.chmod(0o755)
        archive.unlink()
    else:raise RuntimeError('Unknown toolchain component')
if __name__=='__main__':
    try:main(sys.argv[1])
    except (OSError,ValueError,KeyError,IndexError,RuntimeError,tarfile.TarError,zipfile.BadZipFile) as error:raise SystemExit('Toolchain setup failed: '+str(error))
