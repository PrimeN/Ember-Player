#!/usr/bin/env python3
"""Run the shipped JNI wrapper and real IAMF decoder on a Linux host.

Arguments: IAMF_TOOLS_SOURCE HOST_STATIC_ARCHIVE BUILD_DIRECTORY
Build the archive with Bazel's iamf_decoder_static target, AAC disabled and -fPIC.
Requires JDK 17, g++ and the source tree's PCM/Opus/FLAC fixtures. No Android device.
"""
import os, pathlib, re, subprocess, sys

root = pathlib.Path(__file__).resolve().parent.parent
source, archive, build = [pathlib.Path(p).resolve() for p in sys.argv[1:]]
build.mkdir(parents=True, exist_ok=True)
jdk = pathlib.Path(os.environ['JAVA_HOME'])
subprocess.run(['g++','-std=c++20','-O2','-shared','-fPIC','-fvisibility=hidden',
    '-I'+str(jdk/'include'),'-I'+str(jdk/'include/linux'),'-I'+str(source/'iamf/include'),
    '-I'+str(source/('bazel-'+source.name)/'external/abseil-cpp+'),
    str(root/'app/native/iamf/jni/iamf_jni.cc'),str(archive),'-pthread','-lm',
    '-Wl,--exclude-libs,ALL','-o',str(build/'libiamfJNI.so')],check=True)
declarations = (root/'app/src/main/java/androidx/media3/decoder/iamf/IamfDecoder.java').read_text()
methods = '\n'.join(re.sub(r'@\w+\s*','',m) for m in re.findall(r'private native.*?;',declarations,re.S))
java = '''package androidx.media3.decoder.iamf;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
public final class IamfDecoder {
METHODS
static void require(boolean value) {if(!value)throw new AssertionError();}
public static void main(String[] args)throws Exception {
 System.load(args[0]);
 IamfDecoder d=new IamfDecoder();
 long closed=d.iamfOpen();d.iamfClose(closed);d.iamfClose(closed);
 require(d.iamfReset(closed)==-1);
 require(d.iamfDecode(ByteBuffer.allocateDirect(4),4,closed)==-1);
 for(int i=1;i<args.length;i++) {
  long configured=d.iamfOpen();
  require(d.iamfCreateFromDescriptors(null,0,-1,1,1,configured)==-1);
  require(d.iamfCreateFromDescriptors(new byte[0],0,-1,1,1,configured)==-1);
  byte[] descriptors=Files.readAllBytes(Path.of(args[i]+".descriptors"));
  require(d.iamfCreateFromDescriptors(descriptors,0,-1,1,1,configured)==0);
  byte[] unit=Files.readAllBytes(Path.of(args[i]+".unit"));
  ByteBuffer sample=ByteBuffer.allocateDirect(unit.length);sample.put(unit);
  require(d.iamfDecode(sample,unit.length,configured)==0);
  d.iamfClose(configured);
  long h=d.iamfOpen();
  require(d.iamfCreate(0,-1,1,1,h)==0);
  require(d.iamfDecode(ByteBuffer.allocateDirect(4),5,h)==-1);
  require(d.iamfDecode(ByteBuffer.allocateDirect(4),-1,h)==-1);
  require(d.iamfDecode(ByteBuffer.allocate(4),4,h)==-1);
  byte[] bytes=Files.readAllBytes(Path.of(args[i]));
  ByteBuffer in=ByteBuffer.allocateDirect(bytes.length);in.put(bytes);
  require(d.iamfDecode(in,bytes.length,h)==0);
  ByteBuffer out=ByteBuffer.allocateDirect(32*1024*1024);long total=0;
  // Decode buffers temporal units. Drain and allow the parser to advance with empty input.
  for(int n=0;n<100000;n++) {
   if(d.iamfIsTemporalUnitAvailable(h)) {
    require(d.iamfGetOutputTemporalUnit(out,out.capacity()+1,h)==-1);
    int written=d.iamfGetOutputTemporalUnit(out,out.capacity(),h);
    require(written>0);total+=written;
   } else {
    require(d.iamfSignalEndOfDecoding(h)==0);
    if(!d.iamfIsTemporalUnitAvailable(h))break;
   }
  }
  require(total>0);require(d.iamfGetNumberOfOutputChannels(h)==2);
  require(d.iamfGetSampleRate(h)>0);d.iamfClose(h);
  System.out.println(Path.of(args[i]).getFileName()+": "+total+" PCM bytes; JNI bounds and repeated close passed");
 }
}
}'''.replace('METHODS',methods)
file=build/'IamfDecoder.java';file.write_text(java)
subprocess.run([str(jdk/'bin/javac'),'-d',str(build),str(file)],check=True)
fixtures=source/'iamf/cli/testdata/iamf'
# The legacy PCM fixture leaves the samples/frame field at zero. Current IAMF
# validation requires its actual 256-sample frame length; audio bytes are unchanged.
pcm=(fixtures/'tones_256samp_5p1_pcm.iamf').read_bytes()
assert pcm[8:17] == bytes.fromhex('00 0e 00 69 70 63 6d 00 00')
pcm=pcm[:9]+bytes([15])+pcm[10:15]+bytes([0x80,0x02])+pcm[16:]
pcm_file=build/'pcm.iamf';pcm_file.write_bytes(pcm)
inputs=[pcm_file]
for name in ['noise_1024samp_stereo_flac.iamf','noise_3s_stereo_opus.iamf']:
    file=build/name;file.write_bytes((fixtures/name).read_bytes());inputs.append(file)
def obu_ranges(data):
    at=0
    while at<len(data):
        start=at;kind=data[at]>>3;at+=1;size=0
        for shift in range(0,56,7):
            byte=data[at];at+=1;size|=(byte&127)<<shift
            if byte<128: break
        end=at+size
        assert end<=len(data)
        yield start,end,kind
        at=end
for file in inputs:
    data=file.read_bytes();ranges=list(obu_ranges(data))
    first=next(i for i,(_,_,kind) in enumerate(ranges) if kind not in {0,1,2,31})
    start=ranges[first][0];seen=set();end=len(data)
    for begin,finish,kind in ranges[first:]:
        if kind==4 and seen or 5<=kind<=23 and kind in seen:
            end=begin;break
        if 5<=kind<=23:seen.add(kind)
    pathlib.Path(str(file)+'.descriptors').write_bytes(data[:start])
    pathlib.Path(str(file)+'.unit').write_bytes(data[start:end])
subprocess.run([str(jdk/'bin/java'),'-cp',str(build),'androidx.media3.decoder.iamf.IamfDecoder',str(build/'libiamfJNI.so'),
    *map(str,inputs)],check=True)
