using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Security.Cryptography;
using System.Threading;
using System.Web.Script.Serialization;

namespace AetherfallUpdate {
 public sealed class UpdateProgress {
  public int schema = 1;
  public string phase, unit, current;
  public long completed, total;
  public int item, items;
  public double bytesPerSecond;
 }

 // Shared by Setup self-update and the standalone repack updater. Counters are
 // Int64 throughout; progress is phase-local, never a weighted estimate of time.
 public static class UpdateTransfer {
  public const string Prefix = "AFUPDATE/1 ";
  public static event Action<UpdateProgress> ProgressChanged;
  static readonly JavaScriptSerializer Serializer = new JavaScriptSerializer();
  static long lastReport;

  public static void Report(string phase, string unit, long completed, long total,
                            int item, int items, string current, double rate, bool force) {
   long now = Stopwatch.GetTimestamp();
   if (!force && now - lastReport < Stopwatch.Frequency / 4) return;
   lastReport = now;
   var value = new UpdateProgress { phase = phase, unit = unit, completed = completed,
    total = total, item = item, items = items, current = current, bytesPerSecond = rate };
   Console.WriteLine(Prefix + Serializer.Serialize(value));
   Console.Out.Flush();
   var handler = ProgressChanged;
   if (handler != null) handler(value);
  }

  public static bool TryParse(string line, out UpdateProgress value) {
   value = null;
   if (line == null || !line.StartsWith(Prefix, StringComparison.Ordinal) || line.Length > 16384) return false;
   try {
    value = Serializer.Deserialize<UpdateProgress>(line.Substring(Prefix.Length));
    if (value == null || value.schema != 1 || String.IsNullOrEmpty(value.phase) ||
        value.completed < 0 || value.total < 0 || value.item < 0 || value.items < 0 ||
        Double.IsNaN(value.bytesPerSecond) || Double.IsInfinity(value.bytesPerSecond)) {
     value = null; return false;
    }
    return true;
   } catch { value = null; return false; }
  }

  public static string Hash(string path, string phase, long completed, long total,
                            int item, int items, string current) {
   using (var sha = SHA256.Create())
   using (var source = File.OpenRead(path)) {
    if (String.IsNullOrEmpty(phase))
     return BitConverter.ToString(sha.ComputeHash(source)).Replace("-", "").ToLowerInvariant();
    byte[] buffer = new byte[1024 * 1024];
    long read = 0; int count;
    while ((count = source.Read(buffer, 0, buffer.Length)) > 0) {
     sha.TransformBlock(buffer, 0, count, buffer, 0); read += count;
     Report(phase, "bytes", completed + read, total, item, items, current, 0, false);
    }
    sha.TransformFinalBlock(new byte[0], 0, 0);
    return BitConverter.ToString(sha.Hash).Replace("-", "").ToLowerInvariant();
   }
  }

  public static void Download(string url, string destination, long expectedBytes, string expectedHash,
                              string phase, long completed, long total, int item, int items) {
   Uri uri;
   if (!Uri.TryCreate(url, UriKind.Absolute, out uri) || uri.Scheme != "https" || !String.IsNullOrEmpty(uri.UserInfo))
    throw new InvalidDataException("Update downloads require HTTPS without credentials.");
   string partial = destination + ".partial";
   try {
    var request = (HttpWebRequest)WebRequest.Create(uri);
    request.UserAgent = "Aetherfall-Updater";
    request.Timeout = 30000; request.ReadWriteTimeout = 30000;
    Report(phase, "bytes", completed, total, item, items, Path.GetFileName(destination), 0, true);
    using (var response = request.GetResponse()) {
     if (response.ContentLength >= 0 && response.ContentLength != expectedBytes)
      throw new InvalidDataException("Downloaded update size differs from its manifest.");
     using (var input = response.GetResponseStream())
     using (var output = File.Create(partial)) {
      var clock = Stopwatch.StartNew();
      byte[] buffer = new byte[128 * 1024];
      long received = 0; int count;
      while ((count = input.Read(buffer, 0, buffer.Length)) > 0) {
       received += count;
       if (received > expectedBytes) throw new InvalidDataException("Update download exceeds its declared size.");
       output.Write(buffer, 0, count);
       double rate = received / Math.Max(clock.Elapsed.TotalSeconds, 0.001);
       Report(phase, "bytes", completed + received, total, item, items, Path.GetFileName(destination), rate, false);
      }
      if (received != expectedBytes) throw new InvalidDataException("Update download ended before all bytes arrived.");
      Report(phase, "bytes", completed + received, total, item, items, Path.GetFileName(destination),
             received / Math.Max(clock.Elapsed.TotalSeconds, 0.001), true);
     }
    }
    if (!String.Equals(Hash(partial, "", 0, 0, 0, 0, ""), expectedHash, StringComparison.OrdinalIgnoreCase))
     throw new InvalidDataException("Downloaded update checksum mismatch: " + Path.GetFileName(destination));
    if (File.Exists(destination)) File.Delete(destination);
    File.Move(partial, destination);
   } finally {
    if (File.Exists(partial)) File.Delete(partial);
   }
  }

  public static void Copy(string source, string destination, string phase, long completed, long total,
                          int item, int items, string current) {
   using (var input = File.OpenRead(source)) {
    // Match the existing updater's Copy-Item -Force behavior for shipped files.
    if (File.Exists(destination)) {
     var attributes = File.GetAttributes(destination);
     if ((attributes & FileAttributes.ReadOnly) != 0)
      File.SetAttributes(destination, attributes & ~FileAttributes.ReadOnly);
    }
    using (var output = File.Create(destination)) {
     byte[] buffer = new byte[1024 * 1024]; long copied = 0; int count;
     while ((count = input.Read(buffer, 0, buffer.Length)) > 0) {
      output.Write(buffer, 0, count); copied += count;
      Report(phase, "bytes", completed + copied, total, item, items, current, 0, false);
     }
    }
   }
  }

  // Optional components keep a bounded partial file across pauses/network failures.
  // The expected hash is part of the cache name chosen by the component owner.
  public static void DownloadResumable(string url,string destination,long expectedBytes,string expectedHash,CancellationToken cancel) {
   Uri uri;if(!Uri.TryCreate(url,UriKind.Absolute,out uri)||uri.Scheme!="https"||!String.IsNullOrEmpty(uri.UserInfo)||expectedBytes<=0)
    throw new InvalidDataException("Optional downloads require HTTPS and a declared size.");
   ServicePointManager.SecurityProtocol|=SecurityProtocolType.Tls12;
   if(File.Exists(destination)&&new FileInfo(destination).Length==expectedBytes&&String.Equals(Hash(destination,"checking",0,expectedBytes,1,1,Path.GetFileName(destination)),expectedHash,StringComparison.OrdinalIgnoreCase))return;
   string partial=destination+".partial";long offset=File.Exists(partial)?new FileInfo(partial).Length:0;
   if(offset>expectedBytes){File.Delete(partial);offset=0;}
   cancel.ThrowIfCancellationRequested();
   if(offset<expectedBytes) {
    var request=(HttpWebRequest)WebRequest.Create(uri);request.UserAgent="Aetherfall-Languages";request.Timeout=30000;request.ReadWriteTimeout=30000;
    if(offset>0)request.AddRange(offset);
    try {
     using(cancel.Register(()=>request.Abort()))using(var response=(HttpWebResponse)request.GetResponse()) {
      if(response.StatusCode==HttpStatusCode.PartialContent) {
       string range=response.Headers[HttpResponseHeader.ContentRange]??"";
       string prefix="bytes "+offset+"-";if(!range.StartsWith(prefix,StringComparison.Ordinal)||!range.EndsWith("/"+expectedBytes,StringComparison.Ordinal))throw new InvalidDataException("Optional download range differs from its manifest.");
      } else if(response.StatusCode==HttpStatusCode.OK)offset=0;
      else throw new InvalidDataException("Unsupported optional download response.");
      if(response.ContentLength>=0&&response.ContentLength!=expectedBytes-offset)throw new InvalidDataException("Optional download size differs from its manifest.");
      using(var input=response.GetResponseStream())using(var output=new FileStream(partial,offset==0?FileMode.Create:FileMode.Append,FileAccess.Write,FileShare.Read)) {
       byte[] buffer=new byte[128*1024];long received=offset;int count;var clock=Stopwatch.StartNew();
       Report("download","bytes",received,expectedBytes,1,1,Path.GetFileName(destination),0,true);
       while((count=input.Read(buffer,0,buffer.Length))>0) {
        cancel.ThrowIfCancellationRequested();received+=count;if(received>expectedBytes)throw new InvalidDataException("Optional download exceeds its declared size.");output.Write(buffer,0,count);
        Report("download","bytes",received,expectedBytes,1,1,Path.GetFileName(destination),(received-offset)/Math.Max(clock.Elapsed.TotalSeconds,.001),false);
       }
       if(received!=expectedBytes)throw new InvalidDataException("Optional download ended early. Retry to resume.");
       Report("download","bytes",received,expectedBytes,1,1,Path.GetFileName(destination),(received-offset)/Math.Max(clock.Elapsed.TotalSeconds,.001),true);
      }
     }
    }catch(WebException){cancel.ThrowIfCancellationRequested();throw;}
   }
   cancel.ThrowIfCancellationRequested();
   if(!String.Equals(Hash(partial,"verifying",0,expectedBytes,1,1,Path.GetFileName(destination)),expectedHash,StringComparison.OrdinalIgnoreCase)){File.Delete(partial);throw new InvalidDataException("Optional download checksum mismatch. Retry for a fresh copy.");}
   if(File.Exists(destination))File.Delete(destination);File.Move(partial,destination);
  }
 }
}
