using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;

namespace AetherfallSetup {
 public sealed class RepackVerificationResult {
  public int Checked;
  public List<string> Missing=new List<string>(),Changed=new List<string>(),HdTextures=new List<string>();
  public IEnumerable<string> Repairable { get { return Missing.Concat(Changed).Where(x=>!HdTextures.Contains(x)); } }
  public int Problems { get { return Missing.Count+Changed.Count; } }
 }
 // Checks the shipped files of FILES.json; player settings and runtime data are not part of it.
 public static class RepackVerification {
  // The Hub writes its own updater into Control and replaces itself, so these can lead the index.
  static readonly HashSet<string> OwnFiles=new HashSet<string>(StringComparer.OrdinalIgnoreCase) {
   "Aetherfall Hub.exe","Control/Update-Repack.ps1","Control/UpdateTransfer.cs","Control/RepackInstallation.cs" };
  static readonly JavaScriptSerializer Json=new JavaScriptSerializer { MaxJsonLength=int.MaxValue };
  static string At(string package,string relative) { return Path.Combine(package,relative.Replace('/',Path.DirectorySeparatorChar)); }
  static Dictionary<string,object> Read(string path) { return (Dictionary<string,object>)Json.DeserializeObject(File.ReadAllText(path)); }
  static IEnumerable<Dictionary<string,object>> Rows(Dictionary<string,object> value,string key) {
   object rows;return value.TryGetValue(key,out rows)&&rows is object[]?((object[])rows).Cast<Dictionary<string,object>>():Enumerable.Empty<Dictionary<string,object>>();
  }
  static string Hash(string path,Action<long> read,CancellationToken cancel) {
   byte[] buffer=new byte[1024*1024];
   using(var hash=new SHA256Cng())using(var file=new FileStream(path,FileMode.Open,FileAccess.Read,FileShare.ReadWrite|FileShare.Delete,1,FileOptions.SequentialScan)) {
    int count;while((count=file.Read(buffer,0,buffer.Length))>0) { cancel.ThrowIfCancellationRequested();hash.TransformBlock(buffer,0,count,null,0);read(count); }
    hash.TransformFinalBlock(buffer,0,0);return BitConverter.ToString(hash.Hash).Replace("-","").ToLowerInvariant();
   }
  }
  // The selected HD profile replaces standard hashes; its files come from the HD pack, not updates.
  static Dictionary<string,string> HdHashes(string package) {
   var hashes=new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);
   string root=At(package,"Client/Mods/HDTextures"),state=Path.Combine(root,"installed.json");
   if(!File.Exists(state))return hashes;
   var installed=Read(state);object enabled;
   if(!installed.TryGetValue("enabled",out enabled)||!(enabled is bool)||!(bool)enabled)return hashes;
   var component=Read(Path.Combine(root,"component.json"));string version=installed["assetVersion"] as string;
   if(version!=component["assetVersion"] as string) {
    var record=Rows(component,"compatibleComponents").Where(x=>x["assetVersion"] as string==version).ToList();
    if(record.Count!=1||!Regex.IsMatch(record[0]["path"] as string??"",@"^compatibility/[0-9.]+\.json$"))throw new InvalidDataException("Unsupported HD component version.");
    component=Read(At(root,(string)record[0]["path"]));
   }
   foreach(var entry in Rows(component,"files"))hashes["Client/"+entry["path"]]=(string)entry["hdSha256"];
   foreach(var shared in Rows(component,"shared")) { string hash;if(hashes.TryGetValue("Client/"+shared["path"],out hash))hashes["Client/"+shared["packagePath"]]=hash; }
   return hashes;
  }
  // only limits the check to those paths, for confirming a repair.
  public static RepackVerificationResult Run(string package,ICollection<string> only,Action<long,long> progress,CancellationToken cancel) {
   var result=new RepackVerificationResult();var hd=HdHashes(package);
   var entries=Rows(Read(At(package,"FILES.json")),"files").Where(x=>{ object mutable;return !(x.TryGetValue("mutable",out mutable)&&mutable is bool&&(bool)mutable)&&!OwnFiles.Contains((string)x["path"]); }).ToList();
   if(only!=null) { var wanted=new HashSet<string>(only,StringComparer.OrdinalIgnoreCase);entries=entries.Where(x=>wanted.Contains((string)x["path"])).ToList(); }
   long total=entries.Sum(x=>Convert.ToInt64(x["bytes"])),done=0;
   foreach(var entry in entries) {
    cancel.ThrowIfCancellationRequested();
    string relative=(string)entry["path"],path=At(package,relative),expected;long bytes=Convert.ToInt64(entry["bytes"]);
    bool textured=hd.TryGetValue(relative,out expected);if(!textured)expected=(string)entry["sha256"];
    result.Checked++;
    if(!File.Exists(path)) { result.Missing.Add(relative);if(textured)result.HdTextures.Add(relative);done+=bytes;continue; }
    long start=done;bool same=textured||new FileInfo(path).Length==bytes;
    if(same) {
     try { same=String.Equals(Hash(path,count=>{done+=count;progress(done,total);},cancel),expected,StringComparison.OrdinalIgnoreCase); }
     catch(IOException) { same=false; } catch(UnauthorizedAccessException) { same=false; }
    }
    done=start+bytes;progress(done,total);
    if(!same) { result.Changed.Add(relative);if(textured)result.HdTextures.Add(relative); }
   }
   return result;
  }
 }
}
